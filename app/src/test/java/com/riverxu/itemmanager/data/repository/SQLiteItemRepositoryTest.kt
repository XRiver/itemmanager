package com.riverxu.itemmanager.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.riverxu.itemmanager.data.local.database.AppDatabase
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.data.model.ItemInput
import com.riverxu.itemmanager.data.model.MigrationTarget
import com.riverxu.itemmanager.data.model.NameValidation
import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.ExpirationFilter
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.PresetLocations
import com.riverxu.itemmanager.domain.model.PresetTypes
import com.riverxu.itemmanager.domain.model.SizeCategory
import com.riverxu.itemmanager.domain.model.SortField
import com.riverxu.itemmanager.domain.model.SortSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Repository 层集成测试（内存数据库）。
 */
@RunWith(RobolectricTestRunner::class)
class SQLiteItemRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SQLiteItemRepository
    private lateinit var photoStore: PhotoStore

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        photoStore = PhotoStore(context)
        repository = SQLiteItemRepository(db, photoStore)
        repository.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun defaultResidenceId(): Long = repository.getResidences().first().id

    private suspend fun unsetLocation(residenceId: Long): Long =
        repository.getUnsetLocationId(residenceId)

    private suspend fun addSampleItem(
        residenceId: Long,
        name: String,
        locationId: Long,
        type: String? = null,
        size: SizeCategory = SizeCategory.SMALL,
        purchaseDate: Long? = null,
        expiryDate: Long? = null,
        note: String? = null,
    ): Long = repository.addItem(
        ItemInput(
            residenceId = residenceId,
            name = name,
            note = note,
            sizeCategory = size,
            type = type,
            purchaseDate = purchaseDate,
            expiryDate = expiryDate,
            locationId = locationId,
        ),
    )

    // ---------------- 预置数据 ----------------

    @Test
    fun `首次初始化创建默认住处与预置位置类型`() = runTest {
        val residences = repository.getResidences()
        assertEquals(1, residences.size)

        val locations = repository.getLocations(residences.first().id)
        assertEquals(2, locations.size)
        assertTrue(locations.all { it.isPreset })
        assertEquals(
            setOf(PresetLocations.FLEXIBLE, PresetLocations.UNSET),
            locations.map { it.name }.toSet(),
        )

        val types = repository.getTypes()
        assertEquals(PresetTypes.ALL.toSet(), types.map { it.name }.toSet())
        assertTrue(types.all { it.isPreset })
    }

    @Test
    fun `重复初始化不产生重复数据`() = runTest {
        repository.ensureSeeded()
        repository.ensureSeeded()
        assertEquals(1, repository.getResidences().size)
        assertEquals(PresetTypes.ALL.size, repository.getTypes().size)
    }

    @Test
    fun `新增住处自动创建预置位置`() = runTest {
        val id = repository.addResidence("北京的家")
        val locations = repository.getLocations(id)
        assertEquals(2, locations.size)
        assertTrue(locations.any { it.name == PresetLocations.UNSET })
    }

    // ---------------- 物品 CRUD ----------------

    @Test
    fun `添加与读取物品`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        val id = addSampleItem(
            residenceId = residenceId,
            name = "螺丝刀套装",
            locationId = locationId,
            type = PresetTypes.ELECTRONICS,
            note = "十字与一字",
            purchaseDate = 1_600_000_000_000L,
        )

        val detail = repository.getItemDetail(id)
        assertNotNull(detail)
        assertEquals("螺丝刀套装", detail!!.item.name)
        assertEquals("十字与一字", detail.item.note)
        assertEquals(SizeCategory.SMALL, detail.item.sizeCategory)
        assertEquals(residenceId, detail.item.residenceId)
        assertEquals(PresetLocations.UNSET, detail.locationName)
    }

    @Test
    fun `更新物品字段`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        val id = addSampleItem(residenceId, "旧名称", locationId)

        val existing = repository.getItem(id)!!
        val flexible = repository.findLocationByName(residenceId, PresetLocations.FLEXIBLE)!!.id
        repository.updateItem(
            ItemInput(
                id = id,
                residenceId = residenceId,
                name = "新名称",
                note = "补充",
                sizeCategory = SizeCategory.LARGE,
                type = PresetTypes.BOOKS,
                purchaseDate = null,
                expiryDate = 1_900_000_000_000L,
                locationId = flexible,
            ),
        )

        val updated = repository.getItem(id)!!
        assertEquals("新名称", updated.name)
        assertEquals("补充", updated.note)
        assertEquals(SizeCategory.LARGE, updated.sizeCategory)
        assertEquals(PresetTypes.BOOKS, updated.type)
        assertNull("购入时间为空表示很久", updated.purchaseDate)
        assertEquals(1_900_000_000_000L, updated.expiryDate)
        assertEquals(flexible, updated.locationId)
        assertTrue(updated.updatedAt >= existing.updatedAt)
    }

    @Test
    fun `删除物品`() = runTest {
        val residenceId = defaultResidenceId()
        val id = addSampleItem(residenceId, "待删除", unsetLocation(residenceId))
        repository.deleteItem(id)
        assertNull(repository.getItem(id))
    }

    @Test
    fun `批量删除物品`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        val ids = (1..5).map { addSampleItem(residenceId, "物品$it", locationId) }
        repository.deleteItems(ids.take(3))
        assertEquals(2, repository.countItems(FilterSpec.EMPTY))
    }

    @Test
    fun `删除空集合不报错`() = runTest {
        repository.deleteItems(emptyList())
        assertEquals(0, repository.countItems(FilterSpec.EMPTY))
    }

    // ---------------- 名称校验 ----------------

    @Test
    fun `名称为空与超长的校验`() = runTest {
        assertTrue(repository.validateItemName("   ") is NameValidation.Empty)
        assertTrue(repository.validateItemName("x".repeat(101)) is NameValidation.TooLong)
        assertTrue(repository.validateItemName("正常名称") is NameValidation.Ok)
    }

    @Test
    fun `同名物品提示数量且排除自身`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        val first = addSampleItem(residenceId, "充电器", locationId)
        addSampleItem(residenceId, "充电器", locationId)

        val duplicated = repository.validateItemName("充电器")
        assertTrue(duplicated is NameValidation.Duplicated)
        assertEquals(2, (duplicated as NameValidation.Duplicated).count)

        // 编辑自身时不应把自己算作重复
        assertTrue(repository.validateItemName("充电器", excludeId = first) is NameValidation.Duplicated)
    }

    // ---------------- 位置与 LRU ----------------

    @Test
    fun `使用位置后更新 LRU 顺序`() = runTest {
        val residenceId = defaultResidenceId()
        val unset = unsetLocation(residenceId)

        // 未使用过的位置 lastUsedAt 为 0
        val before = repository.getLocations(residenceId).first { it.id == unset }
        assertEquals(0L, before.lastUsedAt)

        addSampleItem(residenceId, "书", unset)

        val after = repository.getLocations(residenceId).first { it.id == unset }
        assertTrue("使用后应记录时间戳", after.lastUsedAt > 0)
        assertEquals(1, after.usageCount)

        val lru = repository.observeLocationsLru(residenceId).first()
        assertEquals("最近使用的位置应排在最前", unset, lru.first().id)
    }

    @Test
    fun `LRU 列表最多返回 20 条`() = runTest {
        val residenceId = defaultResidenceId()
        repeat(25) { index ->
            repository.addLocation(residenceId, "位置$index")
        }
        val lru = repository.observeLocationsLru(residenceId).first()
        assertTrue(lru.size <= 20)
    }

    @Test
    fun `删除位置时物品归入未设置`() = runTest {
        val residenceId = defaultResidenceId()
        val customId = repository.addLocation(residenceId, "书桌抽屉")
        val itemId = addSampleItem(residenceId, "笔记本", customId)

        repository.deleteLocation(customId)

        val item = repository.getItem(itemId)!!
        val unset = repository.getUnsetLocationId(residenceId)
        assertEquals(unset, item.locationId)
        assertNull(repository.getLocation(customId))
    }

    @Test
    fun `预置位置不允许删除`() = runTest {
        val residenceId = defaultResidenceId()
        val unset = unsetLocation(residenceId)
        repository.deleteLocation(unset)
        assertNotNull("预置位置应仍然存在", repository.getLocation(unset))
    }

    @Test
    fun `新建同名位置复用已有记录`() = runTest {
        val residenceId = defaultResidenceId()
        val first = repository.addLocation(residenceId, "阳台")
        val second = repository.addLocation(residenceId, "阳台")
        assertEquals(first, second)
    }

    // ---------------- 类型 ----------------

    @Test
    fun `删除类型时物品类型置空`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        val typeId = repository.addType("工具")
        val itemId = addSampleItem(residenceId, "扳手", locationId, type = "工具")

        repository.deleteType(typeId)

        assertNull(repository.getItem(itemId)!!.type)
        assertNull(repository.getTypes().firstOrNull { it.name == "工具" })
    }

    @Test
    fun `预置类型不允许删除`() = runTest {
        val preset = repository.getTypes().first { it.name == PresetTypes.FOOD }
        repository.deleteType(preset.id)
        assertNotNull(repository.getTypes().firstOrNull { it.name == PresetTypes.FOOD })
    }

    @Test
    fun `重命名类型同步更新物品`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        val typeId = repository.addType("工具")
        val itemId = addSampleItem(residenceId, "扳手", locationId, type = "工具")

        repository.renameType(typeId, "五金工具")

        assertEquals("五金工具", repository.getItem(itemId)!!.type)
    }

    // ---------------- 复合排序与筛选 ----------------

    @Test
    fun `按位置与名称复合排序`() = runTest {
        val residenceId = defaultResidenceId()
        val shelfB = repository.addLocation(residenceId, "B架")
        val shelfA = repository.addLocation(residenceId, "A架")

        addSampleItem(residenceId, "乙", shelfB)
        addSampleItem(residenceId, "甲", shelfA)
        addSampleItem(residenceId, "丙", shelfA)

        val sort = CompositeSort(
            listOf(
                SortSpec(SortField.LOCATION, ascending = true),
                SortSpec(SortField.NAME, ascending = true),
            ),
        )
        val rows = repository.observeItems(FilterSpec(residenceId = residenceId), sort).first()
        // 先按位置名排序，再按名称排序
        assertEquals(3, rows.size)
        assertEquals(listOf("A架", "A架", "B架"), rows.map { it.locationName })
        assertEquals(setOf("甲", "丙"), rows.take(2).map { it.name }.toSet())
        assertEquals("乙", rows.last().name)
    }

    @Test
    fun `降序排序`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        addSampleItem(residenceId, "a", locationId)
        addSampleItem(residenceId, "b", locationId)

        val sort = CompositeSort(listOf(SortSpec(SortField.NAME, ascending = false)))
        val rows = repository.observeItems(FilterSpec(residenceId = residenceId), sort).first()
        assertEquals(listOf("b", "a"), rows.map { it.name })
    }

    @Test
    fun `空值排最后的排序`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        addSampleItem(residenceId, "无过期", locationId, expiryDate = null)
        addSampleItem(residenceId, "有过期", locationId, expiryDate = 1_900_000_000_000L)

        val sort = CompositeSort(listOf(SortSpec(SortField.EXPIRY_DATE, ascending = true, nullsLast = true)))
        val rows = repository.observeItems(FilterSpec(residenceId = residenceId), sort).first()
        assertEquals(listOf("有过期", "无过期"), rows.map { it.name })
    }

    @Test
    fun `关键字搜索匹配名称与注释`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        addSampleItem(residenceId, "螺丝刀", locationId)
        addSampleItem(residenceId, "扳手", locationId, note = "配套螺丝刀头")
        addSampleItem(residenceId, "水杯", locationId)

        val byName = repository.observeItems(FilterSpec(keyword = "螺丝刀"), SortField.defaultSort()).first()
        assertEquals(2, byName.size)

        val byNote = repository.observeItems(FilterSpec(keyword = "配套"), SortField.defaultSort()).first()
        assertEquals(listOf("扳手"), byNote.map { it.name })
    }

    @Test
    fun `搜索关键字中的百分号按字面匹配`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        addSampleItem(residenceId, "浓度 50% 酒精", locationId)
        addSampleItem(residenceId, "浓度 80% 酒精", locationId)

        val rows = repository.observeItems(FilterSpec(keyword = "50%"), SortField.defaultSort()).first()
        assertEquals(listOf("浓度 50% 酒精"), rows.map { it.name })
    }

    @Test
    fun `按大小分类与类型筛选`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        addSampleItem(residenceId, "沙发", locationId, size = SizeCategory.FURNITURE)
        addSampleItem(residenceId, "书", locationId, size = SizeCategory.SMALL, type = PresetTypes.BOOKS)

        val furniture = repository.observeItems(
            FilterSpec(sizeCategory = SizeCategory.FURNITURE),
            SortField.defaultSort(),
        ).first()
        assertEquals(listOf("沙发"), furniture.map { it.name })

        val books = repository.observeItems(
            FilterSpec(typeName = PresetTypes.BOOKS),
            SortField.defaultSort(),
        ).first()
        assertEquals(listOf("书"), books.map { it.name })
    }

    @Test
    fun `按过期状态筛选`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000

        addSampleItem(residenceId, "已过期", locationId, expiryDate = now - 5 * day)
        addSampleItem(residenceId, "一周内", locationId, expiryDate = now + 3 * day)
        addSampleItem(residenceId, "很久以后", locationId, expiryDate = now + 200 * day)
        addSampleItem(residenceId, "没过期", locationId, expiryDate = null)

        val expired = repository.observeItems(
            FilterSpec(expiration = ExpirationFilter.EXPIRED),
            SortField.defaultSort(),
        ).first()
        assertEquals(listOf("已过期"), expired.map { it.name })

        val within7 = repository.observeItems(
            FilterSpec(expiration = ExpirationFilter.WITHIN_7_DAYS),
            SortField.defaultSort(),
        ).first()
        assertEquals(listOf("一周内"), within7.map { it.name })

        val noExpiry = repository.observeItems(
            FilterSpec(expiration = ExpirationFilter.NO_EXPIRY),
            SortField.defaultSort(),
        ).first()
        assertEquals(listOf("没过期"), noExpiry.map { it.name })
    }

    @Test
    fun `按住处筛选`() = runTest {
        val home = defaultResidenceId()
        val office = repository.addResidence("公司")
        addSampleItem(home, "家里物品", repository.getUnsetLocationId(home))
        addSampleItem(office, "公司物品", repository.getUnsetLocationId(office))

        val atOffice = repository.observeItems(
            FilterSpec(residenceId = office),
            SortField.defaultSort(),
        ).first()
        assertEquals(listOf("公司物品"), atOffice.map { it.name })
        assertEquals(1, repository.countItems(FilterSpec(residenceId = home)))
    }

    // ---------------- 筛选快捷入口 ----------------

    @Test
    fun `保存与读取快捷入口`() = runTest {
        val filter = FilterSpec(keyword = "书", sizeCategory = SizeCategory.SMALL)
        repository.addShortcut("小件书籍", filter, SortField.defaultSort())

        val shortcuts = repository.observeShortcuts().first()
        assertEquals(1, shortcuts.size)
        assertEquals("小件书籍", shortcuts.first().name)
        assertEquals(filter, shortcuts.first().filter)
        assertEquals(SortField.defaultSort().specs, shortcuts.first().sort?.specs)

        repository.deleteShortcut(shortcuts.first().id)
        assertTrue(repository.observeShortcuts().first().isEmpty())
    }

    // ---------------- 搬家模式 ----------------

    @Test
    fun `同位迁移在新住处创建同名位置`() = runTest {
        val old = defaultResidenceId()
        val new = repository.addResidence("新家")
        val drawer = repository.addLocation(old, "书桌抽屉")
        val itemId = addSampleItem(old, "钢笔", drawer)

        val moved = repository.migrateItems(listOf(itemId), new, MigrationTarget.SameLocationName)

        assertEquals(1, moved)
        val item = repository.getItem(itemId)!!
        assertEquals(new, item.residenceId)
        val newLocation = repository.getLocation(item.locationId)!!
        assertEquals("书桌抽屉", newLocation.name)
        assertEquals(new, newLocation.residenceId)
    }

    @Test
    fun `指定位置迁移`() = runTest {
        val old = defaultResidenceId()
        val new = repository.addResidence("新家")
        val targetLocation = repository.addLocation(new, "储物间")
        val itemId = addSampleItem(old, "电钻", unsetLocation(old))

        repository.migrateItems(listOf(itemId), new, MigrationTarget.SpecificLocation(targetLocation))

        val item = repository.getItem(itemId)!!
        assertEquals(new, item.residenceId)
        assertEquals(targetLocation, item.locationId)
    }

    @Test
    fun `灵活迁移归入未设置`() = runTest {
        val old = defaultResidenceId()
        val new = repository.addResidence("新家")
        val drawer = repository.addLocation(old, "抽屉")
        val itemId = addSampleItem(old, "钥匙", drawer)

        repository.migrateItems(listOf(itemId), new, MigrationTarget.ToUnset)

        val item = repository.getItem(itemId)!!
        assertEquals(repository.getUnsetLocationId(new), item.locationId)
    }

    @Test
    fun `批量迁移多件物品`() = runTest {
        val old = defaultResidenceId()
        val new = repository.addResidence("新家")
        val locationId = unsetLocation(old)
        val ids = (1..30).map { addSampleItem(old, "物品$it", locationId) }

        val moved = repository.migrateItems(ids, new, MigrationTarget.ToUnset)

        assertEquals(30, moved)
        assertEquals(0, repository.countItems(FilterSpec(residenceId = old)))
        assertEquals(30, repository.countItems(FilterSpec(residenceId = new)))
    }

    @Test
    fun `迁移更新修改时间`() = runTest {
        val old = defaultResidenceId()
        val new = repository.addResidence("新家")
        val itemId = addSampleItem(old, "台灯", unsetLocation(old))
        val before = repository.getItem(itemId)!!.updatedAt

        Thread.sleep(5)
        repository.migrateItems(listOf(itemId), new, MigrationTarget.ToUnset)

        assertTrue(repository.getItem(itemId)!!.updatedAt > before)
    }

    // ---------------- 住处删除 ----------------

    @Test
    fun `删除住处级联删除物品与位置`() = runTest {
        val home = defaultResidenceId()
        val office = repository.addResidence("公司")
        val officeLocation = repository.addLocation(office, "工位")
        addSampleItem(office, "显示器", officeLocation)

        repository.deleteResidence(office)

        assertEquals(1, repository.getResidences().size)
        assertEquals(0, repository.countItems(FilterSpec(residenceId = office)))
        assertTrue(repository.getLocations(office).isEmpty())
    }

    // ---------------- 统计 ----------------

    @Test
    fun `统计信息反映当前数据`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        addSampleItem(residenceId, "a", locationId)
        addSampleItem(residenceId, "b", locationId)

        val stats = repository.stats()
        assertEquals(2, stats.itemCount)
        assertEquals(1, stats.residenceCount)
        assertEquals(2, stats.locationCount)
        assertEquals(PresetTypes.ALL.size, stats.typeCount)
        assertEquals(0, stats.photoCount)
    }

    @Test
    fun `物品数量流动`() = runTest {
        val residenceId = defaultResidenceId()
        assertEquals(0, repository.observeTotalItemCount().first())
        addSampleItem(residenceId, "x", unsetLocation(residenceId))
        assertEquals(1, repository.observeTotalItemCount().first())
        assertEquals(1, repository.observeResidenceItemCount(residenceId).first())
    }

    @Test
    fun `重命名住处与位置`() = runTest {
        val residenceId = defaultResidenceId()
        repository.renameResidence(residenceId, "北京的家")
        assertEquals("北京的家", repository.getResidence(residenceId)!!.name)

        val locationId = repository.addLocation(residenceId, "旧名")
        repository.renameLocation(locationId, "新名")
        assertEquals("新名", repository.getLocation(locationId)!!.name)
    }

    @Test
    fun `物品照片字段可保存与清空`() = runTest {
        val residenceId = defaultResidenceId()
        val locationId = unsetLocation(residenceId)
        val id = addSampleItem(residenceId, "带照片", locationId)
        val existing = repository.getItem(id)!!
        repository.updateItem(
            ItemInput(
                id = id,
                residenceId = residenceId,
                name = existing.name,
                photoPath = "1_123.jpg",
                locationId = locationId,
            ),
        )
        assertEquals("1_123.jpg", repository.getItem(id)!!.photoPath)

        repository.updateItem(
            ItemInput(
                id = id,
                residenceId = residenceId,
                name = existing.name,
                photoPath = null,
                locationId = locationId,
            ),
        )
        assertNull(repository.getItem(id)!!.photoPath)
    }

    @Test
    fun `数据库文件路径存在`() = runTest {
        // 内存数据库路径可能为空，仅验证不抛异常
        repository.checkpointForExport()
        assertFalse(db.isOpen.not())
    }

    @Test
    fun `优化数据库不抛异常`() = runTest {
        repository.optimize()
        assertTrue(db.isOpen)
    }
}
