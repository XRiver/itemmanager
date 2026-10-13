package com.riverxu.itemmanager.data.local.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * 照片压缩与存储测试。
 */
@RunWith(RobolectricTestRunner::class)
class PhotoCompressorTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun createBitmap(width: Int, height: Int, noisy: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (noisy) {
            // 随机噪点让 JPEG 无法被极度压缩，便于验证容量上限逻辑
            val paint = Paint()
            val random = java.util.Random(42)
            for (i in 0 until 4000) {
                paint.color = 0xFF000000.toInt() or random.nextInt(0xFFFFFF)
                val x = random.nextInt(width).toFloat()
                val y = random.nextInt(height).toFloat()
                canvas.drawCircle(x, y, 6f, paint)
            }
        } else {
            canvas.drawColor(0xFF3366CC.toInt())
        }
        return bitmap
    }

    private fun writeTempBitmap(bitmap: Bitmap, name: String, quality: Int = 95): File {
        val dir = File(context.cacheDir, "photo_test").apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
        return file
    }

    @Test
    fun `压缩后不超过目标容量`() {
        val source = writeTempBitmap(createBitmap(1200, 1200), "big.jpg")
        val target = File(context.cacheDir, "photo_test/out.jpg")
        val result = PhotoCompressor().compress(source, target, maxBytes = 200L * 1024L)

        assertTrue("压缩应成功", result is PhotoCompressor.Result.Success)
        val success = result as PhotoCompressor.Result.Success
        assertTrue("输出文件应存在", target.exists())
        assertEquals("返回的字节数应与文件大小一致", target.length().toInt(), success.bytes)
        assertTrue("质量应不低于下限", success.quality >= PhotoCompressor.MIN_QUALITY)
    }

    @Test
    fun `最长边限制为 1920 像素`() {
        val source = writeTempBitmap(createBitmap(3000, 1500), "wide.jpg")
        val target = File(context.cacheDir, "photo_test/wide_out.jpg")
        val result = PhotoCompressor().compress(source, target, maxBytes = 2L * 1024 * 1024)

        val success = result as PhotoCompressor.Result.Success
        assertEquals("等比缩放后最长边应为 1920", 1920, maxOf(success.width, success.height))
        assertEquals("宽高比应保持 2:1", 960, minOf(success.width, success.height))
    }

    @Test
    fun `小图不会被放大`() {
        val source = writeTempBitmap(createBitmap(400, 300, noisy = false), "small.jpg")
        val target = File(context.cacheDir, "photo_test/small_out.jpg")
        val result = PhotoCompressor().compress(source, target, maxBytes = 1024L * 1024L)

        val success = result as PhotoCompressor.Result.Success
        assertEquals(400, success.width)
        assertEquals(300, success.height)
    }

    @Test
    fun `纯色大图可被压到很小`() {
        val source = writeTempBitmap(createBitmap(2000, 2000, noisy = false), "flat.jpg")
        val target = File(context.cacheDir, "photo_test/flat_out.jpg")
        val result = PhotoCompressor().compress(source, target, maxBytes = 100L * 1024L)

        val success = result as PhotoCompressor.Result.Success
        assertTrue("纯色图应远小于 100KB，实际 ${success.bytes}", success.bytes < 100 * 1024)
    }

    /**
     * 说明：Robolectric 的 BitmapFactory 影子实现会把任意输入伪造成 100x100 图片，
     * 因此"损坏/缺失文件"这一分支无法在 JVM 单测中覆盖，只能在真机上验证。
     * 这里显式记录该限制，避免误以为已经覆盖。
     */
    @Test
    fun `解码失败分支在 Robolectric 下不可构造`() {
        val dir = File(context.cacheDir, "photo_test").apply { mkdirs() }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(File(dir, "definitely-missing.jpg").absolutePath, bounds)
        assertTrue(
            "Robolectric 影子实现会伪造尺寸，真机上此处应为 <= 0",
            bounds.outWidth > 0,
        )
    }

    @Test
    fun `质量档位为 32KB 到 1MB 的倍增序列`() {
        val expected = listOf(
            PhotoCompressor.Quality.KB32 to 32L * 1024L,
            PhotoCompressor.Quality.KB64 to 64L * 1024L,
            PhotoCompressor.Quality.KB128 to 128L * 1024L,
            PhotoCompressor.Quality.KB256 to 256L * 1024L,
            PhotoCompressor.Quality.KB512 to 512L * 1024L,
            PhotoCompressor.Quality.MB1 to 1024L * 1024L,
        )
        // 档位数量与顺序都要稳定，UI 直接按 entries 渲染
        assertEquals(expected.map { it.first }, PhotoCompressor.Quality.entries.toList())
        expected.forEach { (quality, maxBytes) ->
            assertEquals("${quality.label} 的上限", maxBytes, quality.maxBytes)
        }
        // 每档是上一档的两倍
        PhotoCompressor.Quality.entries.zipWithNext().forEach { (a, b) ->
            assertEquals("${b.label} 应为 ${a.label} 的两倍", a.maxBytes * 2, b.maxBytes)
        }
    }

    @Test
    fun `默认档位与名称解析`() {
        assertEquals(PhotoCompressor.Quality.KB128, PhotoCompressor.Quality.default)
        assertEquals(
            PhotoCompressor.Quality.KB512,
            PhotoCompressor.Quality.fromNameOrNull("KB512"),
        )
        // 旧版本存下的档位名（SMALL/MEDIUM/LARGE）应解析失败，由调用方回退到默认档
        assertEquals(null, PhotoCompressor.Quality.fromNameOrNull("MEDIUM"))
        assertEquals(null, PhotoCompressor.Quality.fromNameOrNull("HUGE"))
        assertEquals(null, PhotoCompressor.Quality.fromNameOrNull(null))
    }

    @Test
    fun `每档都能达到目标容量`() {
        // 用同一张噪点图依次跑全部档位，验证小目标（32KB）也真能达到
        val source = writeTempBitmap(createBitmap(1600, 1200), "all_qualities.jpg")
        val target = File(context.cacheDir, "photo_test/all_q_out.jpg")
        PhotoCompressor.Quality.entries.forEach { quality ->
            val result = PhotoCompressor().compress(source, target, quality.maxBytes)
            val success = result as PhotoCompressor.Result.Success
            assertTrue(
                "${quality.label} 档：实际 ${success.bytes / 1024} KB 应不超过上限",
                success.bytes <= quality.maxBytes,
            )
            assertTrue(
                "${quality.label} 档：质量应不低于下限",
                success.quality >= PhotoCompressor.MIN_QUALITY,
            )
        }
    }

    @Test
    fun `二分查找会选满足上限的最高质量`() {
        val source = writeTempBitmap(createBitmap(1600, 1200), "mono.jpg")
        val target = File(context.cacheDir, "photo_test/mono_out.jpg")
        val qualities = PhotoCompressor.Quality.entries.map { quality ->
            (PhotoCompressor().compress(source, target, quality.maxBytes)
                as PhotoCompressor.Result.Success).quality
        }

        // 容量上限越大，允许的质量应单调不减
        qualities.zipWithNext().forEach { (lower, higher) ->
            assertTrue(
                "质量应随容量上限提高而不下降，实际各档质量 = $qualities",
                higher >= lower,
            )
        }
        // 若只是"一降到下限就交差"，最大档也会是下限；二分应能在 1MB 档用上明显更高的质量
        assertTrue(
            "1MB 档不应退回最低质量，实际 ${qualities.last()}（下限 ${PhotoCompressor.MIN_QUALITY}）",
            qualities.last() > PhotoCompressor.MIN_QUALITY,
        )
    }

    @Test
    fun `解码展示图按尺寸采样`() {
        val source = writeTempBitmap(createBitmap(2400, 1800), "display.jpg")
        val bitmap = PhotoCompressor().decodeForDisplay(source.absolutePath, maxSize = 600)
        assertNotNull(bitmap)
        assertTrue("采样后最长边应接近 600 且不超过 1200", maxOf(bitmap!!.width, bitmap.height) <= 1200)
        assertTrue("采样后应明显小于原图", maxOf(bitmap.width, bitmap.height) < 2400)
    }

    // ---------------- PhotoStore ----------------

    @Test
    fun `照片按物品编号命名并落盘`() {
        val store = PhotoStore(context)
        store.clearAll()
        val source = writeTempBitmap(createBitmap(1000, 1000), "store.jpg")

        val outcome = store.saveCompressedPhoto(source, itemId = 7L, maxBytes = 512L * 1024L)

        assertTrue(outcome is PhotoStore.SaveOutcome.Success)
        val fileName = (outcome as PhotoStore.SaveOutcome.Success).fileName
        assertTrue("文件名应以物品 id 开头", fileName.startsWith("7_"))
        assertTrue(fileName.endsWith(".jpg"))
        assertNotNull(store.resolve(fileName))
        assertEquals(1, store.listPhotoFiles().size)
        assertTrue(store.totalBytes() > 0)
    }

    @Test
    fun `删除物品时清理其照片`() {
        val store = PhotoStore(context)
        store.clearAll()
        val source = writeTempBitmap(createBitmap(800, 800), "cleanup.jpg")
        val outcome = store.saveCompressedPhoto(source, itemId = 11L, maxBytes = 512L * 1024L)
        val fileName = (outcome as PhotoStore.SaveOutcome.Success).fileName

        store.deleteItemPhotos(11L, fileName)

        assertTrue("照片文件应被删除", store.resolve(fileName) == null)
        assertTrue(store.listPhotoFiles().isEmpty())
    }

    @Test
    fun `替换照片后清理旧文件`() {
        val store = PhotoStore(context)
        store.clearAll()
        val source = writeTempBitmap(createBitmap(700, 700), "prune.jpg")
        val first = store.saveCompressedPhoto(source, itemId = 3L, maxBytes = 512L * 1024L)
        val firstName = (first as PhotoStore.SaveOutcome.Success).fileName
        Thread.sleep(5)
        val second = store.saveCompressedPhoto(source, itemId = 3L, maxBytes = 512L * 1024L)
        val secondName = (second as PhotoStore.SaveOutcome.Success).fileName

        store.pruneOrphansFor(3L, keepPath = secondName)

        assertEquals("只应保留最新照片", listOf(secondName), store.listPhotoFiles().map { it.name })
        assertTrue(store.resolve(firstName) == null)
    }

    @Test
    fun `缓存命中返回同一对象`() {
        val store = PhotoStore(context)
        store.clearAll()
        val source = writeTempBitmap(createBitmap(600, 600), "cache.jpg")
        val outcome = store.saveCompressedPhoto(source, itemId = 5L, maxBytes = 512L * 1024L)
        val fileName = (outcome as PhotoStore.SaveOutcome.Success).fileName

        val first = store.loadBitmap(fileName, maxSize = 256)
        val second = store.loadBitmap(fileName, maxSize = 256)
        assertNotNull(first)
        assertTrue("第二次读取应命中内存缓存", first === second)
    }

    @Test
    fun `照片不存在时返回 null 而不崩溃`() {
        val store = PhotoStore(context)
        store.photosDirectory().mkdirs()
        assertEquals(null, store.loadBitmap("不存在的照片.jpg", maxSize = 256))
        assertEquals(null, store.loadBitmap(null, maxSize = 256))
        assertEquals(false, store.exists("不存在的照片.jpg"))
    }
}
