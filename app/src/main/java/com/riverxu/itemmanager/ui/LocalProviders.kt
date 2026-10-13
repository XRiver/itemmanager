package com.riverxu.itemmanager.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.riverxu.itemmanager.data.local.export.ExportManager
import com.riverxu.itemmanager.data.local.photo.PhotoCompressor
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.data.local.prefs.PreferencesStore
import com.riverxu.itemmanager.data.repository.ItemRepository

/** 供 Compose 树访问依赖容器中的服务，避免层层透传参数。 */
val LocalItemRepository = staticCompositionLocalOf<ItemRepository> {
    error("ItemRepository 未提供")
}

val LocalPhotoStore = staticCompositionLocalOf<PhotoStore> {
    error("PhotoStore 未提供")
}

val LocalPhotoCompressor = staticCompositionLocalOf<PhotoCompressor> {
    error("PhotoCompressor 未提供")
}

val LocalPreferences = staticCompositionLocalOf<PreferencesStore> {
    error("PreferencesStore 未提供")
}

val LocalExportManager = staticCompositionLocalOf<ExportManager> {
    error("ExportManager 未提供")
}
