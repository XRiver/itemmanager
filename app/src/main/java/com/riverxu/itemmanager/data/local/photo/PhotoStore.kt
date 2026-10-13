package com.riverxu.itemmanager.data.local.photo

import android.content.Context
import android.net.Uri
import android.util.LruCache
import java.io.File

/**
 * 照片文件存储。
 *
 * 存储路径：`/data/data/<包名>/files/photos/`，文件命名 `{itemId}_{timestamp}.jpg`。
 * 同时提供解码后的位图内存缓存，避免列表滚动时重复解码。
 */
class PhotoStore(
    private val context: Context,
) {

    private val photosDir: File
        get() = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private val bitmapCache = LruCache<String, android.graphics.Bitmap>(CACHE_ENTRIES)

    fun photosDirectory(): File = photosDir

    /**
     * 把 [source] 压缩并按 `{itemId}_{timestamp}.jpg` 落盘。
     */
    fun saveCompressedPhoto(
        source: File,
        itemId: Long,
        maxBytes: Long,
        compressor: PhotoCompressor = PhotoCompressor(),
    ): SaveOutcome {
        if (!photosDir.exists() && !photosDir.mkdirs()) {
            return SaveOutcome.Failure("存储空间不足，请清理后重试")
        }
        val compressed = when (val result = compressor.compressToBytes(source, maxBytes)) {
            PhotoCompressor.ByteResult.DecodeFailed -> return SaveOutcome.Failure("照片损坏，无法读取")
            PhotoCompressor.ByteResult.OutOfMemory -> return SaveOutcome.Failure("内存不足，请重试")
            is PhotoCompressor.ByteResult.Success -> result
        }

        val fileName = "${itemId}_${System.currentTimeMillis()}.jpg"
        val target = File(photosDir, fileName)
        return try {
            target.outputStream().use { it.write(compressed.data) }
            SaveOutcome.Success(fileName, compressed.data.size, compressed.quality)
        } catch (_: Exception) {
            runCatching { target.delete() }
            SaveOutcome.Failure("照片保存失败，请重试")
        }
    }

    /** 直接把数据写入照片目录（导入流程使用）。 */
    fun writeRawPhoto(fileName: String, data: java.io.InputStream): Boolean = try {
        File(photosDir, fileName).outputStream().use { out -> data.copyTo(out) }
        true
    } catch (_: Exception) {
        false
    }

    fun resolve(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val file = if (path.startsWith("/")) File(path) else File(photosDir, path)
        return file.takeIf { it.exists() }
    }

    fun exists(path: String?): Boolean = resolve(path) != null

    fun deleteQuietly(path: String?) {
        val file = resolve(path) ?: return
        invalidateCache(file.absolutePath)
        runCatching { file.delete() }
    }

    /** 删除某物品的全部照片（按显式路径与 id 前缀）。 */
    fun deleteItemPhotos(itemId: Long, explicitPath: String?) {
        deleteQuietly(explicitPath)
        val prefix = "${itemId}_"
        listPhotoFiles()
            .filter { it.name.startsWith(prefix) }
            .forEach {
                invalidateCache(it.absolutePath)
                runCatching { it.delete() }
            }
    }

    /** 更新照片后清理该物品遗留的旧文件，仅保留 [keepPath]。 */
    fun pruneOrphansFor(itemId: Long, keepPath: String?) {
        val keepName = keepPath?.let { File(it).name }
        val prefix = "${itemId}_"
        listPhotoFiles()
            .filter { it.name.startsWith(prefix) && it.name != keepName }
            .forEach {
                invalidateCache(it.absolutePath)
                runCatching { it.delete() }
            }
    }

    fun listPhotoFiles(): List<File> = photosDir.listFiles()?.toList().orEmpty()

    fun totalBytes(): Long = listPhotoFiles().sumOf { it.length() }

    fun clearAll() {
        listPhotoFiles().forEach { runCatching { it.delete() } }
        bitmapCache.evictAll()
    }

    /** 取得解码后的缩略图/大图，命中内存缓存则直接返回。 */
    fun loadBitmap(
        path: String?,
        maxSize: Int,
        compressor: PhotoCompressor = PhotoCompressor(),
    ): android.graphics.Bitmap? {
        val file = resolve(path) ?: return null
        val key = "${file.absolutePath}@$maxSize"
        bitmapCache.get(key)?.let { return it }
        val bitmap = compressor.decodeForDisplay(file.absolutePath, maxSize) ?: return null
        bitmapCache.put(key, bitmap)
        return bitmap
    }

    fun invalidateCache(absolutePath: String) {
        val snapshot = bitmapCache.snapshot()
        snapshot.keys.filter { it.startsWith(absolutePath) }.forEach { bitmapCache.remove(it) }
    }

    sealed interface SaveOutcome {
        data class Success(val fileName: String, val bytes: Int, val quality: Int) : SaveOutcome

        data class Failure(val message: String) : SaveOutcome
    }

    companion object {
        const val DIR_NAME = "photos"
        private const val CACHE_ENTRIES = 60

        /** 由外部（相机/相册）Uri 复制到缓存目录，得到可压缩的临时文件。 */
        fun copyToTemp(
            context: Context,
            uri: Uri,
            name: String = "incoming.jpg",
        ): File? = runCatching {
            val dir = File(context.cacheDir, "photo_inbox").apply { if (!exists()) mkdirs() }
            val target = File(dir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target
        }.getOrNull()
    }
}
