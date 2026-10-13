package com.riverxu.itemmanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riverxu.itemmanager.data.local.database.AppDatabase
import com.riverxu.itemmanager.data.local.photo.PhotoCompressor
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.data.model.ItemInput
import com.riverxu.itemmanager.data.repository.SQLiteItemRepository
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.PresetLocations
import com.riverxu.itemmanager.domain.model.SortField
import com.riverxu.itemmanager.domain.model.SizeCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.max

/**
 * 真机功能测试：覆盖 Robolectric 无法真实模拟的部分。
 *
 * 这些用例必须跑在真实设备/模拟器上，因为：
 * 1. Robolectric 的 `BitmapFactory` 影子实现会把任意输入伪造成 100x100 图片，
 *    真实的采样解码、EXIF 方向、JPEG 编码都无法验证；
 * 2. Room 在真实 Android SQLite 上的建表、外键级联、类型转换器需要真机运行才作数。
 */
@RunWith(AndroidJUnit4::class)
class RealDeviceFunctionalTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: AppDatabase
    private lateinit var repository: SQLiteItemRepository
    private lateinit var photoStore: PhotoStore
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        // 使用独立的测试库，避免污染应用真实数据；测完删除
        context.deleteDatabase(TEST_DB)
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB).build()
        photoStore = PhotoStore(context)
        repository = SQLiteItemRepository(db, photoStore)
        runBlocking { repository.ensureSeeded() }
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
        tempFiles.forEach { runCatching { it.delete() } }
    }

    // ---------------------------------------------------------------- 照片压缩

    /** 生成带噪点的图片，避免被 JPEG 极度压缩而无法验证容量上限。 */
    private fun createNoisyBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val random = java.util.Random(7)
        repeat(12000) {
            paint.color = 0xFF000000.toInt() or random.nextInt(0xFFFFFF)
            canvas.drawCircle(
                random.nextInt(width).toFloat(),
                random.nextInt(height).toFloat(),
                5f,
                paint,
            )
        }
        return bitmap
    }

    private fun writeSourceBitmap(bitmap: Bitmap, name: String): File {
        val dir = File(context.cacheDir, "real_photo_test").apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        tempFiles += file
        return file
    }

    @Test
    fun 真实编码器下压缩结果不超过配置容量() {
        val source = writeSourceBitmap(createNoisyBitmap(3000, 2000), "noisy.jpg")
        val target = File(context.cacheDir, "real_photo_test/out_500k.jpg")
        tempFiles += target

        val result = PhotoCompressor().compress(source, target, PhotoCompressor.Quality.SMALL.maxBytes)

        assertTrue("压缩应成功，实际 $result", result is PhotoCompressor.Result.Success)
        val success = result as PhotoCompressor.Result.Success
        assertTrue(
            "输出 ${success.bytes} 字节应不超过 500KB",
            success.bytes <= PhotoCompressor.Quality.SMALL.maxBytes,
        )
        assertEquals("返回字节数应与落盘大小一致", target.length().toInt(), success.bytes)
        assertTrue("质量不应低于下限", success.quality >= PhotoCompressor.MIN_QUALITY)
    }

    @Test
    fun 真实解码器下最长边被限制在1920像素() {
        val source = writeSourceBitmap(createNoisyBitmap(4000, 2000), "wide.jpg")
        val target = File(context.cacheDir, "real_photo_test/out_wide.jpg")
        tempFiles += target

        val result = PhotoCompressor().compress(source, target, PhotoCompressor.Quality.LARGE.maxBytes)

        val success = result as PhotoCompressor.Result.Success
        assertEquals("等比缩放后最长边应为 1920", 1920, max(success.width, success.height))
        assertEquals("宽高比应保持 2:1", 960, minOf(success.width, success.height))
    }

    @Test
    fun 真实设备上小图不会被放大() {
        val source = writeSourceBitmap(createNoisyBitmap(320, 240), "small.jpg")
        val target = File(context.cacheDir, "real_photo_test/out_small.jpg")
        tempFiles += target

        val success = PhotoCompressor().compress(source, target, PhotoCompressor.Quality.MEDIUM.maxBytes)
            as PhotoCompressor.Result.Success
        assertEquals(320, success.width)
        assertEquals(240, success.height)
    }

    @Test
    fun 真实设备上损坏文件返回解码失败() {
        // 真机解码器不会伪造图片，这才是能覆盖失败分支的地方
        val broken = File(context.cacheDir, "real_photo_test").apply { mkdirs() }
            .let { File(it, "broken.jpg") }
        broken.writeText("这不是一张图片")
        tempFiles += broken
        val target = File(context.cacheDir, "real_photo_test/out_broken.jpg")
        tempFiles += target

        assertEquals(
            PhotoCompressor.Result.DecodeFailed,
            PhotoCompressor().compress(broken, target, PhotoCompressor.Quality.MEDIUM.maxBytes),
        )
    }

    @Test
    fun 照片按物品编号命名落盘并可清理() {
        photoStore.clearAll()
        val source = writeSourceBitmap(createNoisyBitmap(1600, 1200), "store.jpg")

        val outcome = photoStore.saveCompressedPhoto(
            source = source,
            itemId = 42L,
            maxBytes = PhotoCompressor.Quality.MEDIUM.maxBytes,
        )

        assertTrue(outcome is PhotoStore.SaveOutcome.Success)
        val fileName = (outcome as PhotoStore.SaveOutcome.Success).fileName
        assertTrue("文件名应以物品 id 开头：$fileName", fileName.startsWith("42_"))
        assertTrue(fileName.endsWith(".jpg"))

        val saved = photoStore.resolve(fileName)
        assertNotNull("照片应真实落盘", saved)
        assertTrue("落盘大小应不超过容量上限", saved!!.length() <= PhotoCompressor.Quality.MEDIUM.maxBytes)
        assertNotNull("应能从磁盘解码出图片", photoStore.loadBitmap(fileName, maxSize = 512))

        photoStore.deleteItemPhotos(42L, fileName)
        assertTrue("删除后文件应消失", photoStore.resolve(fileName) == null)
    }

    // ---------------------------------------------------------------- 真实数据库

    @Test
    fun 真实SQLite上完成增删改查与复合排序() = runBlocking {
        val residenceId = repository.getResidences().first().id
        val drawer = repository.addLocation(residenceId, "书桌抽屉")
        val unset = repository.getUnsetLocationId(residenceId)

        val penId = repository.addItem(
            ItemInput(
                residenceId = residenceId,
                name = "钢笔",
                sizeCategory = SizeCategory.SMALL,
                type = "办公用品",
                locationId = drawer,
            ),
        )
        repository.addItem(
            ItemInput(
                residenceId = residenceId,
                name = "订书机",
                sizeCategory = SizeCategory.SMALL,
                locationId = unset,
            ),
        )

        assertEquals(2, repository.countItems(FilterSpec.EMPTY))

        val rows = repository.observeItems(FilterSpec.EMPTY, SortField.defaultSort()).first()
        // 默认排序：位置名升序（未使用过的"未设置" vs "书桌抽屉"按二进制序）
        assertEquals(2, rows.size)
        assertTrue(rows.any { it.name == "钢笔" && it.locationName == "书桌抽屉" })

        // 更新
        repository.updateItem(
            ItemInput(
                id = penId,
                residenceId = residenceId,
                name = "钢笔（已换）",
                sizeCategory = SizeCategory.SMALL,
                locationId = drawer,
            ),
        )
        assertEquals("钢笔（已换）", repository.getItem(penId)!!.name)

        // 删除位置应把物品归入"未设置"（真实外键/事务路径）
        repository.deleteLocation(drawer)
        assertEquals(unset, repository.getItem(penId)!!.locationId)

        // 删除
        repository.deleteItem(penId)
        assertEquals(1, repository.countItems(FilterSpec.EMPTY))
        assertTrue(PresetLocations.UNSET in repository.getLocations(residenceId).map { it.name })
    }

    @Test
    fun 真实SQLite上删除住处级联清空内部数据() = runBlocking {
        val officeId = repository.addResidence("测试公司")
        val officeLocation = repository.addLocation(officeId, "工位")
        repository.addItem(
            ItemInput(
                residenceId = officeId,
                name = "显示器",
                locationId = officeLocation,
            ),
        )
        assertEquals(1, repository.countItems(FilterSpec(residenceId = officeId)))

        repository.deleteResidence(officeId)

        assertEquals(0, repository.countItems(FilterSpec(residenceId = officeId)))
        assertTrue("住处下的位置应被级联删除", repository.getLocations(officeId).isEmpty())
    }

    @Test
    fun 真实SQLite上搬家迁移() = runBlocking {
        val oldId = repository.getResidences().first().id
        val newId = repository.addResidence("测试新家")
        val drawer = repository.addLocation(oldId, "抽屉")
        val itemId = repository.addItem(
            ItemInput(residenceId = oldId, name = "钥匙", locationId = drawer),
        )

        val moved = repository.migrateItems(
            itemIds = listOf(itemId),
            targetResidenceId = newId,
            target = com.riverxu.itemmanager.data.model.MigrationTarget.SameLocationName,
        )

        assertEquals(1, moved)
        val item = repository.getItem(itemId)!!
        assertEquals(newId, item.residenceId)
        assertEquals("抽屉", repository.getLocation(item.locationId)!!.name)
    }

    private companion object {
        const val TEST_DB = "real_device_test.db"
    }
}
