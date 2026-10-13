package com.riverxu.itemmanager.data.local.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 照片压缩：先限制最长边尺寸，再从高质量起逐步降低 JPEG 质量，直到满足目标大小。
 *
 * 对应设计文档「照片处理 - 压缩策略」：
 *  1. 最长边不超过 [MAX_DIMENSION]（1920px）
 *  2. JPEG 质量从 90 递减
 *  3. 直到文件不大于用户配置的最大容量
 */
class PhotoCompressor(
    private val maxDimension: Int = MAX_DIMENSION,
    private val startQuality: Int = START_QUALITY,
    private val minQuality: Int = MIN_QUALITY,
    private val qualityStep: Int = QUALITY_STEP,
) {

    sealed interface Result {
        /** 压缩成功，[bytes] 为最终 JPEG 数据大小 */
        data class Success(val bytes: Int, val width: Int, val height: Int, val quality: Int) : Result

        data object DecodeFailed : Result

        data object OutOfMemory : Result
    }

    sealed interface ByteResult {
        class Success(val data: ByteArray, val quality: Int, val width: Int, val height: Int) : ByteResult

        data object DecodeFailed : ByteResult

        data object OutOfMemory : ByteResult
    }

    /**
     * 将 [source] 压缩为不超过 [maxBytes] 的 JPEG 数据。
     *
     * 策略：
     *  1. 限制最长边到 [maxDimension]
     *  2. 在该尺寸下用**二分查找**找出满足容量上限的最高 JPEG 质量
     *     （编码体积随质量单调递增，二分比"从 90 逐步递减"少编码约一半次数，
     *      对 32 KB 这类小目标尤其明显）
     *  3. 若连质量下限都达不到，则按 [RESIZE_FACTOR] 逐级缩小尺寸重试；
     *     已经到最小尺寸仍超标时，返回该尺寸下最低质量的结果（调用方可据此提示实际大小）
     */
    fun compressToBytes(source: File, maxBytes: Long): ByteResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ByteResult.DecodeFailed

        var targetDimension = maxDimension
        var round = 0
        while (round < MAX_RESIZE_ATTEMPTS) {
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetDimension)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = try {
                BitmapFactory.decodeFile(source.absolutePath, options)
            } catch (_: OutOfMemoryError) {
                return ByteResult.OutOfMemory
            } ?: return ByteResult.DecodeFailed

            val scaled = scaleDown(decoded, targetDimension)
            if (scaled !== decoded) decoded.recycle()

            val oriented = applyExifOrientation(scaled, source)
            if (oriented !== scaled) scaled.recycle()

            val attempt = findBestQuality(oriented, maxBytes)
            val width = oriented.width
            val height = oriented.height
            oriented.recycle()

            if (attempt.fits) {
                return ByteResult.Success(attempt.bytes, attempt.quality, width, height)
            }

            // 连质量下限都超标：缩小尺寸重试；已到最小尺寸则接受当前结果
            val nextDimension = (targetDimension * RESIZE_FACTOR).roundToInt()
            if (nextDimension <= MIN_DIMENSION) {
                return ByteResult.Success(attempt.bytes, attempt.quality, width, height)
            }
            targetDimension = nextDimension
            round++
        }
        return ByteResult.DecodeFailed
    }

    /**
     * 单次编码尝试。
     *
     * @param fits 体积是否已满足容量上限
     */
    private class Attempt(val bytes: ByteArray, val quality: Int, val fits: Boolean)

    /**
     * 在 [bitmap] 上二分查找满足 [maxBytes] 的最高 JPEG 质量。
     *
     * 质量候选为 [[minQuality], [startQuality]] 内以 [qualityStep] 递增的离散值；
     * 由于编码体积随质量单调递增，二分即可。
     *
     * @return 命中时返回该质量的结果（`fits = true`）；全部超标时返回体积最小的那次（`fits = false`）
     */
    private fun findBestQuality(bitmap: Bitmap, maxBytes: Long): Attempt {
        val candidates = generateSequence(minQuality) { previous ->
            (previous + qualityStep).takeIf { it <= startQuality }
        }.toList()
        var low = 0
        var high = candidates.lastIndex
        var best: Attempt? = null
        var smallest: Attempt? = null
        while (low <= high) {
            val mid = (low + high) / 2
            val quality = candidates[mid]
            val bytes = encodeJpeg(bitmap, quality)
            if (bytes.size <= maxBytes) {
                best = Attempt(bytes, quality, fits = true)
                low = mid + 1 // 还有余量，尝试更高质量
            } else {
                if (smallest == null || bytes.size < smallest.bytes.size) {
                    smallest = Attempt(bytes, quality, fits = false)
                }
                high = mid - 1
            }
        }
        // best 为空说明全部超标，此时 smallest 必然非空（候选非空且至少探测过一次）
        return best ?: smallest ?: Attempt(ByteArray(0), minQuality, fits = false)
    }

    /** 压缩并把结果写入 [target]，返回写出的字节数。 */
    fun compress(source: File, target: File, maxBytes: Long): Result {
        val compressed = compressToBytes(source, maxBytes)
        return when (compressed) {
            ByteResult.DecodeFailed -> Result.DecodeFailed
            ByteResult.OutOfMemory -> Result.OutOfMemory
            is ByteResult.Success -> try {
                target.outputStream().use { it.write(compressed.data) }
                Result.Success(compressed.data.size, compressed.width, compressed.height, compressed.quality)
            } catch (_: Exception) {
                Result.DecodeFailed
            }
        }
    }

    /** 解码为用于显示的位图（按 [maxSize] 采样，避免大图占内存）。 */
    fun decodeForDisplay(path: String, maxSize: Int): Bitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(path, options)
        }.getOrNull()

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream(BITMAP_BUFFER_BYTES)
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun scaleDown(bitmap: Bitmap, targetDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= targetDimension) return bitmap
        val ratio = targetDimension.toFloat() / longest.toFloat()
        val width = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun applyExifOrientation(bitmap: Bitmap, source: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetDimension: Int): Int {
        var sampleSize = 1
        var longest = max(width, height)
        while (longest / 2 >= targetDimension) {
            longest /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    /** 压缩质量档位（每图最大容量），按倍增序列从 32 KB 到 1 MB。 */
    enum class Quality(val label: String, val maxBytes: Long) {
        KB32("32 KB", 32L * 1024L),
        KB64("64 KB", 64L * 1024L),
        KB128("128 KB", 128L * 1024L),
        KB256("256 KB", 256L * 1024L),
        KB512("512 KB", 512L * 1024L),
        MB1("1 MB", 1024L * 1024L);

        companion object {
            /** 默认档位：物品照片主要用作辨认参照，128 KB 已足够清晰。 */
            val default: Quality = KB128

            fun fromNameOrNull(name: String?): Quality? =
                entries.firstOrNull { it.name == name }
        }
    }

    companion object {
        const val MAX_DIMENSION = 1920
        const val START_QUALITY = 90

        /**
         * JPEG 质量下限。设为 30 是为了让 32 KB / 64 KB 这类小目标也能达到，
         * 一味保持画质会导致小目标永远无法满足。
         */
        const val MIN_QUALITY = 30
        const val QUALITY_STEP = 10

        private const val RESIZE_FACTOR = 0.75f
        private const val MIN_DIMENSION = 480

        /** 尺寸回退轮次：1920 → 1440 → 1080 → 810 → 607，给极小目标留足空间。 */
        private const val MAX_RESIZE_ATTEMPTS = 5
        private const val BITMAP_BUFFER_BYTES = 256 * 1024
    }
}

/** 便于测试：把位图直接写入文件。 */
internal fun writeBitmapToFile(bitmap: Bitmap, target: File, quality: Int = 90) {
    FileOutputStream(target).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
    }
}
