package com.riverxu.itemmanager.ui.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.riverxu.itemmanager.data.local.database.AppDatabase
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.data.local.prefs.PreferencesStore
import com.riverxu.itemmanager.data.repository.SQLiteItemRepository
import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.FilterShortcut
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.PresetTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 筛选快捷入口的切换规则（0.3.0 起为短按切换）。
 *
 * 行为约定：
 *  - 未应用时短按 → 应用该快捷入口的筛选（与其排序）
 *  - 已应用时短按 → 取消，并**还原到应用之前**的筛选与排序（不是清空）
 *  - 手动改动筛选后，该快捷入口不再算作"已应用"
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeShortcutToggleTest {

    private lateinit var db: AppDatabase
    private lateinit var viewModel: HomeViewModel

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = SQLiteItemRepository(db, PhotoStore(context))
        runBlocking { repository.ensureSeeded() }
        viewModel = HomeViewModel(repository, PreferencesStore(context))
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun shortcut(
        id: Long = 1L,
        name: String = "待出售",
        filter: FilterSpec = FilterSpec(tags = setOf(PresetTags.TO_SELL)),
        sort: CompositeSort? = null,
    ) = FilterShortcut(id = id, name = name, filter = filter, sort = sort)

    @Test
    fun `短按未应用的快捷入口会应用其筛选`() {
        val target = shortcut()

        viewModel.toggleShortcut(target)

        assertEquals(target.filter, viewModel.currentFilter)
        assertTrue("应用后应视为已应用", viewModel.isShortcutApplied(target))
    }

    @Test
    fun `再短按一次取消并还原到应用前的筛选`() {
        viewModel.onFilterChange { it.copy(typeName = "电子产品") }
        val before = viewModel.currentFilter
        val target = shortcut()

        viewModel.toggleShortcut(target)
        assertEquals(target.filter, viewModel.currentFilter)

        viewModel.toggleShortcut(target)
        assertEquals("取消应还原原筛选，而不是清空", before, viewModel.currentFilter)
        assertFalse(viewModel.isShortcutApplied(target))
    }

    @Test
    fun `原本没有任何筛选时取消后回到空筛选`() {
        val target = shortcut()

        viewModel.toggleShortcut(target)
        viewModel.toggleShortcut(target)

        assertTrue(viewModel.currentFilter.isEmpty)
    }

    @Test
    fun `应用快捷入口会套用其排序，取消时排序一并还原`() {
        viewModel.setSortField(com.riverxu.itemmanager.domain.model.SortField.NAME, ascending = false)
        val sortBefore = viewModel.currentSort
        val target = shortcut(
            filter = FilterSpec(typeName = "书籍"),
            sort = com.riverxu.itemmanager.domain.model.SortField.defaultSort(),
        )

        viewModel.toggleShortcut(target)
        assertEquals("应套用快捷入口保存的排序", target.sort, viewModel.currentSort)

        viewModel.toggleShortcut(target)
        assertEquals("取消后排序也应还原", sortBefore, viewModel.currentSort)
    }

    @Test
    fun `快捷入口未保存排序时不影响当前排序`() {
        viewModel.setSortField(com.riverxu.itemmanager.domain.model.SortField.NAME, ascending = false)
        val sortBefore = viewModel.currentSort

        viewModel.toggleShortcut(shortcut(sort = null))

        assertEquals(sortBefore, viewModel.currentSort)
    }

    @Test
    fun `手动改动筛选后该快捷入口不再算作已应用`() {
        val target = shortcut()
        viewModel.toggleShortcut(target)
        assertTrue(viewModel.isShortcutApplied(target))

        // 用户在被应用的筛选上又手动调整了条件
        viewModel.onFilterChange { it.copy(sizeCategory = com.riverxu.itemmanager.domain.model.SizeCategory.SMALL) }

        assertFalse("筛选已被改动，不应再显示为选中", viewModel.isShortcutApplied(target))
    }

    @Test
    fun `切换不同快捷入口不会互相干扰`() {
        val first = shortcut(id = 1, name = "待出售", filter = FilterSpec(tags = setOf(PresetTags.TO_SELL)))
        val second = shortcut(id = 2, name = "待汰换", filter = FilterSpec(tags = setOf(PresetTags.TO_REPLACE)))

        viewModel.toggleShortcut(first)
        viewModel.toggleShortcut(second)

        assertEquals(second.filter, viewModel.currentFilter)
        assertTrue(viewModel.isShortcutApplied(second))
        assertFalse(viewModel.isShortcutApplied(first))

        // 取消第二个应还原到应用它之前的状态（即第一个快捷入口）
        viewModel.toggleShortcut(second)
        assertEquals(first.filter, viewModel.currentFilter)
    }

    @Test
    fun `清除筛选后快捷入口恢复为未选中`() {
        val target = shortcut()
        viewModel.toggleShortcut(target)

        viewModel.clearFilter()

        assertTrue(viewModel.currentFilter.isEmpty)
        assertFalse(viewModel.isShortcutApplied(target))
    }
}
