package com.riverxu.itemmanager.di

import android.content.Context
import com.riverxu.itemmanager.data.local.database.AppDatabase
import com.riverxu.itemmanager.data.local.export.ExportManager
import com.riverxu.itemmanager.data.local.photo.PhotoCompressor
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.data.local.prefs.PreferencesStore
import com.riverxu.itemmanager.data.repository.ItemRepository
import com.riverxu.itemmanager.data.repository.SQLiteItemRepository

/**
 * 轻量级依赖容器。
 *
 * 设计文档原计划使用 Hilt；考虑到 AGP 9 内置 Kotlin 与 Hilt 注解处理器的版本兼容风险，
 * 这里以显式的手写容器达到同样的"依赖注入集中管理"目的：单一实例、按需懒加载、便于测试替换。
 */
class AppContainer(private val context: Context) {

    val preferences: PreferencesStore by lazy { PreferencesStore(context) }

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val photoStore: PhotoStore by lazy { PhotoStore(context) }

    val photoCompressor: PhotoCompressor by lazy { PhotoCompressor() }

    val itemRepository: ItemRepository by lazy { SQLiteItemRepository(database, photoStore) }

    val exportManager: ExportManager by lazy {
        ExportManager(context, itemRepository, photoStore)
    }

    /** 应用启动时的初始化：写入预置数据。 */
    suspend fun initialize() {
        itemRepository.ensureSeeded()
        preferences.markSeeded()
    }
}
