package com.riverxu.itemmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riverxu.itemmanager.data.local.database.ItemListRow
import com.riverxu.itemmanager.data.local.database.LocationEntity
import com.riverxu.itemmanager.data.local.database.ResidenceEntity
import com.riverxu.itemmanager.data.local.database.TagEntity
import com.riverxu.itemmanager.data.local.database.TypeEntity
import com.riverxu.itemmanager.data.local.prefs.PreferencesStore
import com.riverxu.itemmanager.data.repository.ItemRepository
import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.ExpirationFilter
import com.riverxu.itemmanager.domain.model.FieldLimits
import com.riverxu.itemmanager.domain.model.FilterShortcut
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.LayoutMode
import com.riverxu.itemmanager.domain.model.SizeCategory
import com.riverxu.itemmanager.domain.model.SortField
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 首页状态。
 */
data class HomeUiState(
    val items: List<ItemListRow> = emptyList(),
    val totalCount: Int = 0,
    val filteredCount: Int = 0,
    val residences: List<ResidenceEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val types: List<TypeEntity> = emptyList(),
    /** 全部可选标签，供筛选面板展示 */
    val tags: List<TagEntity> = emptyList(),
    val shortcuts: List<FilterShortcut> = emptyList(),
    val filter: FilterSpec = FilterSpec.EMPTY,
    val sort: CompositeSort = SortField.defaultSort(),
    val layoutMode: LayoutMode = LayoutMode.LIST,
    val confirmDelete: Boolean = true,
    val loading: Boolean = true,
    val selectedIds: Set<Long> = emptySet(),
    val message: String? = null,
)

/**
 * 首页 ViewModel：搜索、筛选、排序、快捷入口与批量选择。
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HomeViewModel(
    private val repository: ItemRepository,
    private val preferences: PreferencesStore,
) : ViewModel() {

    private val filterState = MutableStateFlow(FilterSpec.EMPTY)
    private val sortState = MutableStateFlow(SortField.defaultSort())
    private val selectionState = MutableStateFlow<Set<Long>>(emptySet())
    private val messageState = MutableStateFlow<String?>(null)
    private val loadingState = MutableStateFlow(true)

    private val residencesFlow = repository.observeResidences()
    private val typesFlow = repository.observeTypes()
    private val shortcutsFlow = repository.observeShortcuts()
    private val totalCountFlow = repository.observeTotalItemCount()

    /** 筛选中选择住处后，位置下拉需要跟随该住处。 */
    private val filterResidenceFlow = filterState
        .map { it.residenceId }
        .distinctUntilChanged()

    private val filterLocationsFlow = filterResidenceFlow.flatMapLatest { residenceId ->
        if (residenceId == null) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            repository.observeLocations(residenceId)
        }
    }

    private val itemsFlow = combine(filterState, sortState) { filter, sort -> filter to sort }
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { (filter, sort) ->
            repository.observeItems(filter, sort)
        }

    /** 过滤条件与排序的快照流，避免 combine 超过 5 个参数。 */
    private val filterAndSortFlow = combine(filterState, sortState) { filter, sort -> filter to sort }

    /** 总数与选中集合的快照流。 */
    private val countAndSelectionFlow = combine(totalCountFlow, selectionState) { total, selection ->
        total to selection
    }

    val uiState: StateFlow<HomeUiState> = combine(
        itemsFlow,
        residencesFlow,
        filterLocationsFlow,
        typesFlow,
        shortcutsFlow,
        filterAndSortFlow,
        countAndSelectionFlow,
        combine(
            preferences.layoutMode,
            combine(loadingState, messageState) { l, m -> l to m },
        ) { layout, lm -> Triple(layout, lm.first, lm.second) },
        preferences.confirmDelete,
        repository.observeTags(),
    ) { args: Array<Any?> ->
        val items = args[0] as List<ItemListRow>
        val filterAndSort = args[5] as Pair<FilterSpec, CompositeSort>
        val countAndSelection = args[6] as Pair<Int, Set<Long>>
        val layoutAndStatus = args[7] as Triple<LayoutMode, Boolean, String?>
        HomeUiState(
            items = items,
            totalCount = countAndSelection.first,
            filteredCount = items.size,
            residences = args[1] as List<ResidenceEntity>,
            locations = args[2] as List<LocationEntity>,
            types = args[3] as List<TypeEntity>,
            shortcuts = args[4] as List<FilterShortcut>,
            filter = filterAndSort.first,
            sort = filterAndSort.second,
            selectedIds = countAndSelection.second,
            layoutMode = layoutAndStatus.first,
            loading = layoutAndStatus.second,
            message = layoutAndStatus.third,
            confirmDelete = args[8] as Boolean,
            tags = args[9] as List<TagEntity>,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        viewModelScope.launch {
            // 恢复上次筛选的住处（若已删除则忽略）
            val last = preferences.lastFilterResidenceId()
            if (last != null) {
                val exists = repository.getResidence(last) != null
                if (exists) filterState.value = filterState.value.copy(residenceId = last)
            }
            loadingState.value = false
        }
    }

    // ---------------- 搜索与筛选 ----------------

    fun onKeywordChange(keyword: String) {
        filterState.value = filterState.value.copy(keyword = keyword.take(FieldLimits.ITEM_NAME_MAX))
    }

    fun onFilterChange(transform: (FilterSpec) -> FilterSpec) {
        val next = transform(filterState.value)
        filterState.value = next
        preferences.setLastFilterResidenceId(next.residenceId)
    }

    fun clearFilter() {
        filterState.value = FilterSpec.EMPTY
        preferences.setLastFilterResidenceId(null)
    }

    fun clearKeyword() {
        filterState.value = filterState.value.copy(keyword = "")
    }

    fun toggleExpirationFilter(value: ExpirationFilter) {
        onFilterChange { current ->
            current.copy(expiration = if (current.expiration == value) null else value)
        }
    }

    /** 标签筛选：多选，命中任意一个即符合。 */
    fun toggleTagFilter(tag: String) {
        onFilterChange { current ->
            val next = if (tag in current.tags) current.tags - tag else current.tags + tag
            current.copy(tags = next)
        }
    }

    fun clearTagFilter() {
        onFilterChange { it.copy(tags = emptySet()) }
    }

    fun toggleSizeCategory(value: SizeCategory) {
        onFilterChange { current ->
            current.copy(sizeCategory = if (current.sizeCategory == value) null else value)
        }
    }

    // ---------------- 排序 ----------------

    fun onSortChange(sort: CompositeSort) {
        sortState.value = sort.normalized()
    }

    fun toggleSortField(field: SortField) {
        sortState.value = sortState.value.toggled(field)
    }

    fun setSortField(field: SortField, ascending: Boolean) {
        sortState.value = sortState.value.withSpec(
            com.riverxu.itemmanager.domain.model.SortSpec(field = field, ascending = ascending),
        )
    }

    fun removeSortField(field: SortField) {
        sortState.value = CompositeSort(sortState.value.specs.filterNot { it.field == field })
    }

    fun resetSort() {
        sortState.value = SortField.defaultSort()
    }

    // ---------------- 布局与选择 ----------------

    fun toggleLayoutMode() {
        val next = if (uiState.value.layoutMode == LayoutMode.LIST) LayoutMode.GRID else LayoutMode.LIST
        preferences.setLayoutMode(next)
    }

    fun toggleSelection(id: Long) {
        selectionState.value = selectionState.value.let { current ->
            if (id in current) current - id else current + id
        }
    }

    fun selectAll() {
        selectionState.value = uiState.value.items.map { it.id }.toSet()
    }

    fun clearSelection() {
        selectionState.value = emptySet()
    }

    fun toggleSelectAll() {
        val all = uiState.value.items.map { it.id }.toSet()
        selectionState.value = if (selectionState.value.containsAll(all) && all.isNotEmpty()) {
            emptySet()
        } else {
            all
        }
    }

    // ---------------- 删除 ----------------

    fun deleteItems(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteItems(ids.toList())
            selectionState.value = selectionState.value - ids.toSet()
            messageState.value = "已删除 ${ids.size} 件物品"
        }
    }

    // ---------------- 快捷入口 ----------------

    fun applyShortcut(shortcut: FilterShortcut) {
        filterState.value = shortcut.filter
        shortcut.sort?.let { sortState.value = it }
        preferences.setLastFilterResidenceId(shortcut.filter.residenceId)
    }

    fun saveShortcut(name: String) {
        viewModelScope.launch {
            repository.addShortcut(name.trim(), filterState.value, sortState.value)
            messageState.value = "已保存快捷入口「${name.trim()}」"
        }
    }

    fun deleteShortcut(id: Long) {
        viewModelScope.launch { repository.deleteShortcut(id) }
    }

    // ---------------- 消息 ----------------

    fun consumeMessage() {
        messageState.value = null
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L

        fun factory(
            repository: ItemRepository,
            preferences: PreferencesStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(repository, preferences) as T
        }
    }
}
