package com.riverxu.itemmanager.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.data.repository.SQLiteItemRepository
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.PresetTags
import com.riverxu.itemmanager.domain.model.SortField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 数据库迁移测试：v1 → v2。
 *
 * 手工按 v1 的真实建表语句造一个旧库，再用 Room(v2) + [MIGRATION_1_2] 打开，
 * 验证两件事：
 *  1. 既有数据一条不丢（这是升级最怕出问题的地方）
 *  2. 新结构可用：`locationUpdatedAt` 被回填、预置标签已就位、标签关联表能写
 *
 * Room 在迁移后会严格校验表结构与期望 schema 是否一致，不一致会抛
 * IllegalStateException，因此本用例同时也在校验迁移 SQL 的正确性。
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dbName = "migration_test.db"
    private lateinit var opened: AppDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        if (::opened.isInitialized && opened.isOpen) opened.close()
        context.deleteDatabase(dbName)
    }

    /** 按 1.json 里的建表语句手工造一个 v1 库，并写入一条代表性数据。 */
    private fun createLegacyV1Database() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        legacy.execSQL(
            "CREATE TABLE IF NOT EXISTS `residence` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        legacy.execSQL(
            "CREATE TABLE IF NOT EXISTS `location` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `residenceId` INTEGER NOT NULL, `name` TEXT NOT NULL, `isPreset` INTEGER NOT NULL, `usageCount` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, FOREIGN KEY(`residenceId`) REFERENCES `residence`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        legacy.execSQL(
            "CREATE TABLE IF NOT EXISTS `item_type` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isPreset` INTEGER NOT NULL, `usageCount` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL)",
        )
        legacy.execSQL(
            "CREATE TABLE IF NOT EXISTS `item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `residenceId` INTEGER NOT NULL, `name` TEXT NOT NULL, `note` TEXT, `sizeCategory` TEXT NOT NULL, `type` TEXT, `photoPath` TEXT, `purchaseDate` INTEGER, `expiryDate` INTEGER, `locationId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`residenceId`) REFERENCES `residence`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`locationId`) REFERENCES `location`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        legacy.execSQL(
            "CREATE TABLE IF NOT EXISTS `filter_shortcut` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `filterConfig` TEXT NOT NULL, `sortConfig` TEXT, `createdAt` INTEGER NOT NULL)",
        )

        // 索引也必须照搬：Room 迁移后会把索引一并纳入校验
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_residence_name` ON `residence` (`name`)")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_location_residenceId` ON `location` (`residenceId`)")
        legacy.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_location_residenceId_name` ON `location` (`residenceId`, `name`)")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_location_lastUsedAt_usageCount` ON `location` (`lastUsedAt`, `usageCount`)")
        legacy.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_item_type_name` ON `item_type` (`name`)")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_item_type_lastUsedAt_usageCount` ON `item_type` (`lastUsedAt`, `usageCount`)")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_item_residenceId` ON `item` (`residenceId`)")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_item_locationId` ON `item` (`locationId`)")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_item_expiryDate` ON `item` (`expiryDate`)")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_item_name` ON `item` (`name`)")
        legacy.execSQL("CREATE INDEX IF NOT EXISTS `index_item_type` ON `item` (`type`)")

        // 旧版特征数据：物品/位置/类型/住处/筛选快捷入口各一条
        legacy.execSQL("INSERT INTO residence (id, name, createdAt) VALUES (1, '旧住处', 100)")
        legacy.execSQL(
            "INSERT INTO location (id, residenceId, name, isPreset, usageCount, lastUsedAt) " +
                "VALUES (1, 1, '未设置', 1, 0, 0)",
        )
        legacy.execSQL(
            "INSERT INTO item_type (id, name, isPreset, usageCount, lastUsedAt) " +
                "VALUES (1, '办公用品', 1, 3, 500)",
        )
        legacy.execSQL(
            "INSERT INTO item (id, residenceId, name, note, sizeCategory, type, photoPath, " +
                "purchaseDate, expiryDate, locationId, createdAt, updatedAt) " +
                "VALUES (1, 1, '旧螺丝刀', '迁移前就存在', 'SMALL', '办公用品', " +
                "'1_photo.jpg', 111, 222, 1, 1000, 2000)",
        )
        legacy.execSQL(
            "INSERT INTO filter_shortcut (id, name, filterConfig, sortConfig, createdAt) " +
                "VALUES (1, '旧快捷入口', '{\"keyword\":\"\"}', NULL, 3000)",
        )
        legacy.version = 1
        legacy.close()
    }

    private fun openWithRoomV2(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
            .also { opened = it }

    @Test
    fun `v1 升 v2 后既有数据全部保留`() = runTest {
        createLegacyV1Database()
        val db = openWithRoomV2()
        val repository = SQLiteItemRepository(db, PhotoStore(context))

        // 物品：字段逐个核对
        val item = repository.getItem(1L)
        assertNotNull("迁移后物品不应丢失", item)
        assertEquals("旧螺丝刀", item!!.name)
        assertEquals("迁移前就存在", item.note)
        assertEquals("办公用品", item.type)
        assertEquals("1_photo.jpg", item.photoPath)
        assertEquals(111L, item.purchaseDate)
        assertEquals(222L, item.expiryDate)
        assertEquals(1L, item.residenceId)
        assertEquals(1L, item.locationId)

        // 关联数据
        assertEquals(1, repository.getResidences().size)
        assertEquals("旧住处", repository.getResidences().first().name)
        assertEquals(1, repository.getLocations(1L).size)
        assertTrue(repository.getShortcutsOrEmpty(db).isNotEmpty())
    }

    @Test
    fun `v1 升 v2 后最近更新位置时间被回填为录入时间`() = runTest {
        createLegacyV1Database()
        val db = openWithRoomV2()
        val repository = SQLiteItemRepository(db, PhotoStore(context))

        val item = repository.getItem(1L)!!
        assertEquals("录入时间应保持原值", 1000L, item.createdAt)
        assertEquals("新增列应回填为 createdAt，而不是 0", 1000L, item.locationUpdatedAt)
    }

    @Test
    fun `v1 升 v2 后预置标签已就位`() = runTest {
        createLegacyV1Database()
        val db = openWithRoomV2()
        val repository = SQLiteItemRepository(db, PhotoStore(context))

        val names = repository.getTags().map { it.name }
        assertTrue("应包含预置标签「${PresetTags.TO_REPLACE}」，实际 $names", PresetTags.TO_REPLACE in names)
        assertTrue("应包含预置标签「${PresetTags.TO_SELL}」，实际 $names", PresetTags.TO_SELL in names)
        assertTrue(
            "预置标签应标记为 isPreset",
            repository.getTags().filter { it.name in PresetTags.ALL }.all { it.isPreset },
        )
    }

    @Test
    fun `升级后新结构可正常使用（标签与位置时间）`() = runTest {
        createLegacyV1Database()
        val db = openWithRoomV2()
        val repository = SQLiteItemRepository(db, PhotoStore(context))

        // 给迁移过来的旧物品打标签
        val existing = repository.getItem(1L)!!
        repository.updateItem(
            com.riverxu.itemmanager.data.model.ItemInput(
                id = existing.id,
                residenceId = existing.residenceId,
                name = existing.name,
                note = existing.note,
                sizeCategory = existing.sizeCategory,
                type = existing.type,
                tags = listOf(PresetTags.TO_SELL, "厨房"),
                photoPath = existing.photoPath,
                purchaseDate = existing.purchaseDate,
                expiryDate = existing.expiryDate,
                locationId = existing.locationId,
            ),
        )

        val detail = repository.getItemDetail(1L)!!
        assertEquals(listOf("厨房", PresetTags.TO_SELL).sorted(), detail.tags.sorted())

        // 标签筛选可用
        val byTag = repository.observeItems(
            FilterSpec(tags = setOf(PresetTags.TO_SELL)),
            SortField.defaultSort(),
        ).first()
        assertEquals(listOf("旧螺丝刀"), byTag.map { it.name })
        assertEquals(listOf("厨房", PresetTags.TO_SELL).sorted(), byTag.first().tags.sorted())

        // 改名称不会推进「最近更新位置时间」
        repository.updateItem(
            com.riverxu.itemmanager.data.model.ItemInput(
                id = existing.id,
                residenceId = existing.residenceId,
                name = "改名后的螺丝刀",
                photoPath = existing.photoPath,
                purchaseDate = existing.purchaseDate,
                expiryDate = existing.expiryDate,
                locationId = existing.locationId,
                tags = listOf(PresetTags.TO_SELL, "厨房"),
            ),
        )
        assertEquals("位置未变，最近更新位置时间不应改变", 1000L, repository.getItem(1L)!!.locationUpdatedAt)
    }
}

/** 测试辅助：判断筛选快捷入口是否迁移过来。 */
private suspend fun SQLiteItemRepository.getShortcutsOrEmpty(db: AppDatabase) =
    db.filterShortcutDao().observeAll().first()
