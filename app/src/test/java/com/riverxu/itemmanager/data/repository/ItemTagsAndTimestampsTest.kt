package com.riverxu.itemmanager.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.riverxu.itemmanager.data.local.database.AppDatabase
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.data.model.ItemInput
import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.PresetTags
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
 * 0.2.0 新增能力的测试：多选标签、录入时间、最近更新位置时间。
 */
@RunWith(RobolectricTestRunner::class)
class ItemTagsAndTimestampsTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SQLiteItemRepository

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SQLiteItemRepository(db, PhotoStore(context))
        repository.ensureSeeded()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun residenceId(): Long = repository.getResidences().first().id

    private suspend fun newItem(
        name: String,
        tags: List<String> = emptyList(),
        locationId: Long? = null,
    ): Long {
        val residence = residenceId()
        return repository.addItem(
            ItemInput(
                residenceId = residence,
                name = name,
                tags = tags,
                locationId = locationId ?: repository.getUnsetLocationId(residence),
            ),
        )
    }

    // ---------------- 预置标签 ----------------

    @Test
    fun `初始化会写入两个预置标签`() = runTest {
        val tags = repository.getTags()
        assertTrue(PresetTags.TO_REPLACE in tags.map { it.name })
        assertTrue(PresetTags.TO_SELL in tags.map { it.name })
        assertTrue(
            "预置标签应带 isPreset 标记",
            tags.filter { it.name in PresetTags.ALL }.all { it.isPreset },
        )
    }

    @Test
    fun `预置标签不可删除`() = runTest {
        val preset = repository.getTags().first { it.name == PresetTags.TO_SELL }
        repository.deleteTag(preset.id)
        assertNotNull(
            "预置标签删除后应仍然存在",
            repository.getTags().firstOrNull { it.name == PresetTags.TO_SELL },
        )
    }

    // ---------------- 标签多选 ----------------

    @Test
    fun `新建物品默认不带标签`() = runTest {
        val id = newItem("默认无标签")
        assertTrue(repository.getItemDetail(id)!!.tags.isEmpty())
    }

    @Test
    fun `物品可同时拥有多个标签`() = runTest {
        val id = newItem("多标签物品", tags = listOf(PresetTags.TO_SELL, "厨房", "小工具"))
        val tags = repository.getItemDetail(id)!!.tags
        assertEquals(listOf("小工具", "厨房", PresetTags.TO_SELL).sorted(), tags.sorted())
    }

    @Test
    fun `使用不存在的标签名会自动创建`() = runTest {
        newItem("自动建标签", tags = listOf("冷门标签"))
        assertNotNull(
            "使用过的标签应被创建",
            repository.getTags().firstOrNull { it.name == "冷门标签" },
        )
    }

    @Test
    fun `重复标签名不会重复创建`() = runTest {
        newItem("物品甲", tags = listOf("共用标签"))
        newItem("物品乙", tags = listOf("共用标签"))
        assertEquals(1, repository.getTags().count { it.name == "共用标签" })
    }

    @Test
    fun `同一物品重复的标签只保留一个`() = runTest {
        val id = newItem("重复标签", tags = listOf("去重", "去重", " 去重 "))
        assertEquals(listOf("去重"), repository.getItemDetail(id)!!.tags)
    }

    @Test
    fun `更新物品时整体替换标签集合`() = runTest {
        val id = newItem("先有标签", tags = listOf("旧标签A", "旧标签B"))
        val existing = repository.getItem(id)!!
        repository.updateItem(
            ItemInput(
                id = id,
                residenceId = existing.residenceId,
                name = existing.name,
                tags = listOf("新标签"),
                locationId = existing.locationId,
            ),
        )
        assertEquals(listOf("新标签"), repository.getItemDetail(id)!!.tags)
        // 旧标签本身保留在标签库里（只是不再关联），与「类型」的处置一致
        assertNotNull(repository.getTags().firstOrNull { it.name == "旧标签A" })
    }

    @Test
    fun `清空标签`() = runTest {
        val id = newItem("要清空", tags = listOf("A", "B"))
        val existing = repository.getItem(id)!!
        repository.updateItem(
            ItemInput(
                id = id,
                residenceId = existing.residenceId,
                name = existing.name,
                tags = emptyList(),
                locationId = existing.locationId,
            ),
        )
        assertTrue(repository.getItemDetail(id)!!.tags.isEmpty())
    }

    // ---------------- 标签管理 ----------------

    @Test
    fun `重命名标签后物品上的标签名同步变化`() = runTest {
        val id = newItem("待改名", tags = listOf("旧名"))
        val tag = repository.getTags().first { it.name == "旧名" }
        repository.renameTag(tag.id, "新名")
        assertEquals(listOf("新名"), repository.getItemDetail(id)!!.tags)
    }

    @Test
    fun `重命名到已存在的标签名时合并两者`() = runTest {
        val itemA = newItem("物品A", tags = listOf("甲标签"))
        val itemB = newItem("物品B", tags = listOf("乙标签"))
        val tagB = repository.getTags().first { it.name == "乙标签" }
        repository.renameTag(tagB.id, "甲标签")

        // 两个标签合并成一个，两件物品都指向它
        assertEquals(1, repository.getTags().count { it.name == "甲标签" })
        assertNull(repository.getTags().firstOrNull { it.name == "乙标签" })
        assertEquals(listOf("甲标签"), repository.getItemDetail(itemA)!!.tags)
        assertEquals(listOf("甲标签"), repository.getItemDetail(itemB)!!.tags)
    }

    @Test
    fun `删除标签只解除关联不影响物品本身`() = runTest {
        val id = newItem("物品还在", tags = listOf("待删除标签"))
        val tag = repository.getTags().first { it.name == "待删除标签" }

        repository.deleteTag(tag.id)

        val item = repository.getItem(id)
        assertNotNull("物品不应随标签删除而消失", item)
        assertEquals("物品还在", item!!.name)
        assertTrue(repository.getItemDetail(id)!!.tags.isEmpty())
    }

    @Test
    fun `标签名会剔除控制字符与分隔符`() = runTest {
        val id = newItem("奇怪标签", tags = listOf("正\u001F常"))
        assertEquals(listOf("正常"), repository.getItemDetail(id)!!.tags)
    }

    // ---------------- 标签筛选 ----------------

    @Test
    fun `按单个标签筛选`() = runTest {
        newItem("要卖的", tags = listOf(PresetTags.TO_SELL))
        newItem("要换的", tags = listOf(PresetTags.TO_REPLACE))
        newItem("普通物品")

        val rows = repository.observeItems(
            FilterSpec(tags = setOf(PresetTags.TO_SELL)),
            SortField.defaultSort(),
        ).first()
        assertEquals(listOf("要卖的"), rows.map { it.name })
    }

    @Test
    fun `多个标签之间是或的关系`() = runTest {
        newItem("要卖的", tags = listOf(PresetTags.TO_SELL))
        newItem("要换的", tags = listOf(PresetTags.TO_REPLACE))
        newItem("普通物品")

        val rows = repository.observeItems(
            FilterSpec(tags = setOf(PresetTags.TO_SELL, PresetTags.TO_REPLACE)),
            SortField.defaultSort(),
        ).first()
        assertEquals(listOf("要换的", "要卖的").sorted(), rows.map { it.name }.sorted())
    }

    @Test
    fun `标签筛选可与其它条件叠加`() = runTest {
        val residence = residenceId()
        val shelf = repository.addLocation(residence, "货架")
        newItem("货架上的待售品", tags = listOf(PresetTags.TO_SELL), locationId = shelf)
        newItem("低下的待售品", tags = listOf(PresetTags.TO_SELL))

        val rows = repository.observeItems(
            FilterSpec(tags = setOf(PresetTags.TO_SELL), locationId = shelf),
            SortField.defaultSort(),
        ).first()
        assertEquals(listOf("货架上的待售品"), rows.map { it.name })
    }

    @Test
    fun `列表行携带标签用于展示`() = runTest {
        newItem("带标签的行", tags = listOf("乙", "甲"))
        val row = repository.observeItems(FilterSpec.EMPTY, SortField.defaultSort()).first().first()
        assertEquals("标签应按名称排序返回", listOf("乙", "甲").sorted(), row.tags)
    }

    @Test
    fun `不含标签的物品在标签筛选下不出现`() = runTest {
        newItem("没有标签")
        val rows = repository.observeItems(
            FilterSpec(tags = setOf(PresetTags.TO_SELL)),
            SortField.defaultSort(),
        ).first()
        assertTrue(rows.isEmpty())
    }

    // ---------------- 录入时间 / 最近更新位置时间 ----------------

    @Test
    fun `录入时间在创建时写入且之后不再变化`() = runTest {
        val id = newItem("计时物品")
        val created = repository.getItem(id)!!.createdAt
        assertTrue("录入时间应被写入", created > 0)

        Thread.sleep(5)
        val existing = repository.getItem(id)!!
        repository.updateItem(
            ItemInput(
                id = id,
                residenceId = existing.residenceId,
                name = "改个名字",
                note = "加个注释",
                locationId = existing.locationId,
            ),
        )
        assertEquals("录入时间不可被修改", created, repository.getItem(id)!!.createdAt)
    }

    @Test
    fun `修改名称注释照片不会推进最近更新位置时间`() = runTest {
        val id = newItem("位置不动")
        val before = repository.getItem(id)!!.locationUpdatedAt

        Thread.sleep(5)
        val existing = repository.getItem(id)!!
        repository.updateItem(
            ItemInput(
                id = id,
                residenceId = existing.residenceId,
                name = "改名了",
                note = "改注释了",
                photoPath = "1_new.jpg",
                locationId = existing.locationId,
            ),
        )

        assertEquals(
            "位置没变，最近更新位置时间不应改变",
            before,
            repository.getItem(id)!!.locationUpdatedAt,
        )
        val updated = repository.getItem(id)!!
        assertTrue("但修改时间应推进", updated.updatedAt > before)
    }

    @Test
    fun `更换位置会推进最近更新位置时间`() = runTest {
        val residence = residenceId()
        val shelf = repository.addLocation(residence, "新位置")
        val id = newItem("要挪窝")
        val before = repository.getItem(id)!!.locationUpdatedAt

        Thread.sleep(5)
        val existing = repository.getItem(id)!!
        repository.updateItem(
            ItemInput(
                id = id,
                residenceId = existing.residenceId,
                name = existing.name,
                locationId = shelf,
            ),
        )

        val after = repository.getItem(id)!!.locationUpdatedAt
        assertTrue("换位置应推进最近更新位置时间", after > before)
    }

    @Test
    fun `搬家迁移会推进最近更新位置时间`() = runTest {
        val old = residenceId()
        val new = repository.addResidence("新住处")
        val id = newItem("随家搬迁")
        val before = repository.getItem(id)!!.locationUpdatedAt

        Thread.sleep(5)
        repository.migrateItems(
            itemIds = listOf(id),
            targetResidenceId = new,
            target = com.riverxu.itemmanager.data.model.MigrationTarget.ToUnset,
        )

        assertTrue(
            "搬家也算位置变化",
            repository.getItem(id)!!.locationUpdatedAt > before,
        )
    }

    @Test
    fun `录入时间早于等于最近更新位置时间`() = runTest {
        val id = newItem("新建即定位")
        val item = repository.getItem(id)!!
        assertEquals(
            "新建时录入时间与最近更新位置时间同源",
            item.createdAt,
            item.locationUpdatedAt,
        )
    }

    // ---------------- 统计与排序 ----------------

    @Test
    fun `统计包含标签数量`() = runTest {
        newItem("统计用", tags = listOf("额外标签"))
        val stats = repository.stats()
        assertEquals(PresetTags.ALL.size + 1, stats.tagCount)
    }

    @Test
    fun `可按最近更新位置时间排序`() = runTest {
        val residence = residenceId()
        val shelf = repository.addLocation(residence, "架子")
        val first = newItem("先挪的")
        Thread.sleep(5)
        val second = newItem("后挪的")
        // 把 first 挪到新位置，使它成为最近更新位置的物品
        Thread.sleep(5)
        val firstEntity = repository.getItem(first)!!
        repository.updateItem(
            ItemInput(
                id = first,
                residenceId = firstEntity.residenceId,
                name = firstEntity.name,
                locationId = shelf,
            ),
        )

        val rows = repository.observeItems(
            FilterSpec.EMPTY,
            CompositeSort(listOf(SortSpec(SortField.LOCATION_UPDATED_AT, ascending = false))),
        ).first()
        assertEquals(listOf("先挪的", "后挪的"), rows.map { it.name })
        assertTrue(repository.getItem(second)!!.locationUpdatedAt > 0)
    }

    @Test
    fun `标签筛选不会被快捷入口的编解码丢掉`() = runTest {
        val filter = FilterSpec(tags = setOf("甲", "乙"))
        repository.addShortcut("带标签的快捷入口", filter, SortField.defaultSort())
        val shortcut = repository.observeShortcuts().first().first()
        assertEquals(setOf("甲", "乙"), shortcut.filter.tags)
        assertFalse(shortcut.filter.isEmpty)
    }
}
