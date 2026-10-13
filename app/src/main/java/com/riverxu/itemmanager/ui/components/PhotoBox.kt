package com.riverxu.itemmanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riverxu.itemmanager.ui.LocalPhotoCompressor
import com.riverxu.itemmanager.ui.LocalPhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 轻量照片加载：按目标尺寸采样解码 + 内存缓存（由 PhotoStore 提供）。
 *
 * 自行实现以避免引入额外图片库，同时满足"内存不足时使用采样压缩"的要求。
 */
@Composable
fun PhotoBox(
    photoPath: String?,
    modifier: Modifier = Modifier,
    targetSize: Int = 512,
    cornerRadius: Dp = 12.dp,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val photoStore = LocalPhotoStore.current
    val compressor = LocalPhotoCompressor.current

    val bitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        photoPath,
        targetSize,
    ) {
        value = if (photoPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                photoStore.loadBitmap(photoPath, targetSize, compressor)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null && !current.isRecycled) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Icon(
                imageVector = if (photoPath.isNullOrBlank()) Icons.Outlined.Inventory2 else Icons.Outlined.BrokenImage,
                contentDescription = null,
                modifier = Modifier.size(if (targetSize > 512) 64.dp else 28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}
