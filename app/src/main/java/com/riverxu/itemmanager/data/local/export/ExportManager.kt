package com.riverxu.itemmanager.data.local.export

import android.content.Context
import com.riverxu.itemmanager.data.local.database.AppDatabase
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.data.model.ExportSummary
import com.riverxu.itemmanager.data.model.ImportSummary
import com.riverxu.itemmanager.data.repository.ItemRepository
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数据备份与恢复。
 *
 * 导出产物为 ZIP：
 *  - `database.db`：SQLite 数据库快照（已执行 WAL checkpoint）
 *  - `photos` 目录：全部照片
 *  - `manifest.json`：导出元信息
 */
class ExportManager(
    private val context: Context,
    private val repository: ItemRepository,
    private val photoStore: PhotoStore,
) {

    sealed interface Result<out T> {
        data class Success<T>(val value: T) : Result<T>

        data class Failure(val message: String) : Result<Nothing>
    }

    // ------------------------------------------------------------------ 导出

    suspend fun exportTo(output: OutputStream, timestamp: Long = System.currentTimeMillis()): Result<ExportSummary> =
        withContext(Dispatchers.IO) {
            try {
                repository.checkpointForExport()
                val dbFile = repository.databaseFile()
                if (!dbFile.exists()) return@withContext Result.Failure("数据库文件不存在，无法导出")

                val photoFiles = photoStore.listPhotoFiles()
                val itemCount = countItems()
                val residenceCount = repository.getResidences().size

                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    zip.setLevel(6)

                    zip.putNextEntry(ZipEntry(ENTRY_DB))
                    dbFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()

                    photoFiles.forEach { photo ->
                        zip.putNextEntry(ZipEntry("$ENTRY_PHOTOS_DIR/${photo.name}"))
                        photo.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }

                    zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                    manifestJson(
                        version = AppDatabase.DB_NAME,
                        itemCount = itemCount,
                        residenceCount = residenceCount,
                        photoCount = photoFiles.size,
                        exportedAt = timestamp,
                    ).toByteArray(Charsets.UTF_8).let { zip.write(it) }
                    zip.closeEntry()
                }

                val fileName = "ItemManagerFree_backup_${DateUtils.backupTimestamp(timestamp)}.zip"
                Result.Success(
                    ExportSummary(
                        itemCount = itemCount,
                        residenceCount = residenceCount,
                        photoCount = photoFiles.size,
                        totalBytes = dbFile.length() + photoFiles.sumOf { it.length() },
                        fileName = fileName,
                    ),
                )
            } catch (e: Exception) {
                Result.Failure("导出失败：${e.message ?: "未知错误"}")
            }
        }

    // ------------------------------------------------------------------ 导入

    /**
     * 从 ZIP 备份恢复。
     *
     * 流程：解包 → 校验 database.db 完整性 → 恢复照片 → 用备份库内容替换当前数据 →
     * 处理同名住处（重命名为「{原名}新导入」）。
     */
    suspend fun importFrom(input: InputStream): Result<ImportSummary> = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "import_staging")
        try {
            staging.deleteRecursively()
            if (!staging.mkdirs()) return@withContext Result.Failure("无法创建临时目录，请检查存储空间")

            val extract = extractZip(input, staging)
                ?: return@withContext Result.Failure("备份包已损坏，无法解压")
            if (!extract.hasDatabase) {
                return@withContext Result.Failure("备份包不完整：缺少 database.db")
            }

            // 校验备份数据库结构
            val validation = validateDatabase(extract.databaseFile)
            if (validation != null) {
                return@withContext Result.Failure(validation)
            }

            // 恢复照片
            var photoCount = 0
            extract.photoFiles.forEach { photo ->
                if (photoStore.writeRawPhoto(photo.name, photo.inputStream())) {
                    photoCount++
                }
            }

            // 用备份数据替换现有数据
            repository.replaceDatabaseFrom(extract.databaseFile)
            repository.ensureSeeded()

            // 处理同名住处
            val renamed = resolveResidenceNameConflicts()

            Result.Success(
                ImportSummary(
                    itemCount = countItems(),
                    residenceCount = repository.getResidences().size,
                    photoCount = photoCount,
                    renamedResidences = renamed,
                ),
            )
        } catch (e: Exception) {
            Result.Failure("导入失败：${e.message ?: "未知错误"}")
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun countItems(): Int = repository.countItems(FilterSpec.EMPTY)

    /**
     * 导入后若出现重名住处，自动把新导入的住处改名为「{原名}新导入」，必要时追加序号。
     */
    private suspend fun resolveResidenceNameConflicts(): List<String> {
        val residences = repository.getResidences()
        val renamed = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        // 保留创建时间最早的作为原名，其余改名
        residences.sortedBy { it.createdAt }.forEach { residence ->
            if (seen.add(residence.name)) return@forEach
            var candidate = "${residence.name}新导入"
            var index = 2
            while (candidate in seen) {
                candidate = "${residence.name}新导入$index"
                index++
            }
            repository.renameResidence(residence.id, candidate)
            seen.add(candidate)
            renamed += "${residence.name} → $candidate"
        }
        return renamed
    }

    // ------------------------------------------------------------------ ZIP 工具

    private class ExtractResult(
        val databaseFile: File,
        val hasDatabase: Boolean,
        val photoFiles: List<File>,
    )

    private fun extractZip(input: InputStream, target: File): ExtractResult? {
        var hasDatabase = false
        val photos = mutableListOf<File>()
        val photosDir = File(target, ENTRY_PHOTOS_DIR).apply { mkdirs() }

        return try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == ENTRY_DB -> {
                            hasDatabase = true
                            File(target, DB_TEMP_NAME).outputStream().use { zip.copyTo(it) }
                        }

                        name.startsWith("$ENTRY_PHOTOS_DIR/") && !entry.isDirectory -> {
                            val fileName = File(name).name
                            if (fileName.isNotBlank()) {
                                val out = File(photosDir, fileName)
                                out.outputStream().use { zip.copyTo(it) }
                                photos += out
                            }
                        }

                        else -> Unit // 忽略其他条目
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            ExtractResult(
                databaseFile = File(target, DB_TEMP_NAME),
                hasDatabase = hasDatabase,
                photoFiles = photos,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 备份包基础校验：SQLite 文件头 + 关键表名。
     * 表结构与完整性由导入时的跨库复制事务再次校验。
     *
     * @return 错误信息；结构正确时返回 null
     */
    private fun validateDatabase(dbFile: File): String? {
        if (!dbFile.exists() || dbFile.length() == 0L) return "备份包中的数据库为空"
        val header = ByteArray(16)
        val read = dbFile.inputStream().use { it.read(header) }
        if (read < 16 || String(header, Charsets.US_ASCII) != SQLITE_MAGIC) {
            return "数据库文件格式不正确，可能已损坏"
        }
        // 在文件前部扫描表名字符串，确认是本应用导出的库
        val probeSize = minOf(dbFile.length(), 256L * 1024L).toInt()
        val probe = ByteArray(probeSize)
        dbFile.inputStream().use { it.read(probe) }
        val text = String(probe, Charsets.ISO_8859_1)
        val missing = REQUIRED_TABLES.filterNot { text.contains("CREATE TABLE $it") || text.contains("\"$it\"") }
        return if (missing.isEmpty()) null else "备份文件缺少数据表：${missing.joinToString()}"
    }

    private fun manifestJson(
        version: String,
        itemCount: Int,
        residenceCount: Int,
        photoCount: Int,
        exportedAt: Long,
    ): String = """
        {
          "app": "物品管家Free",
          "appEn": "ItemManagerFree",
          "appVersion": "${com.riverxu.itemmanager.BuildConfig.VERSION_NAME}",
          "package": "com.riverxu.itemmanager",
          "database": "$version",
          "itemCount": $itemCount,
          "residenceCount": $residenceCount,
          "photoCount": $photoCount,
          "exportedAt": $exportedAt,
          "exportedAtReadable": "${DateUtils.formatDate(exportedAt)}"
        }
    """.trimIndent()

    companion object {
        const val ENTRY_DB = "database.db"
        const val ENTRY_PHOTOS_DIR = "photos"
        const val ENTRY_MANIFEST = "manifest.json"
        private const val DB_TEMP_NAME = "imported.db"
        private const val SQLITE_MAGIC = "SQLite format 3\u0000"
        private val REQUIRED_TABLES = listOf("residence", "location", "item_type", "item")

        /** 便捷方法：导出到 SAF 文档。 */
        suspend fun exportToUri(
            context: Context,
            repository: ItemRepository,
            photoStore: PhotoStore,
            uri: android.net.Uri,
        ): Result<ExportSummary> {
            val manager = ExportManager(context, repository, photoStore)
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: return Result.Failure("无法写入所选位置，请重试")
            return output.use { manager.exportTo(it) }
        }

        suspend fun importFromUri(
            context: Context,
            repository: ItemRepository,
            photoStore: PhotoStore,
            uri: android.net.Uri,
        ): Result<ImportSummary> {
            val manager = ExportManager(context, repository, photoStore)
            val input = context.contentResolver.openInputStream(uri)
                ?: return Result.Failure("无法读取所选文件，请重试")
            return input.use { manager.importFrom(it) }
        }
    }
}
