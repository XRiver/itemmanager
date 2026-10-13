package com.riverxu.itemmanager.ui.move

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riverxu.itemmanager.data.local.database.ItemListRow
import com.riverxu.itemmanager.data.local.database.LocationEntity
import com.riverxu.itemmanager.data.local.database.ResidenceEntity
import com.riverxu.itemmanager.data.model.MigrationTarget
import com.riverxu.itemmanager.data.repository.ItemRepository
import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.SortField
import com.riverxu.itemmanager.domain.model.SortSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 搬家模式状态。
 */
data class MoveUiState(
    val residences: List<ResidenceEntity> = emptyList(),
    val fromResidenceId: Long = 0L,
    val toResidenceId: Long = 0L,
    val fromLocations: List<LocationEntity> = emptyList(),
    val toLocations: List<LocationEntity> = emptyList(),
    val filter: FilterSpec = FilterSpec.EMPTY,
    val items: List<ItemListRow> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val target: MigrationTarget = MigrationTarget.ToUnset,
    val targetLocationId: Long = 0L,
    val busy: Boolean = false,
    val finished: Boolean = false,
    val message: String? = null,
) {
    val canMigrate: Boolean
        get() = selectedIds.isNotEmpty() &&
            toResidenceId > 0 &&
            toResidenceId != fromResidenceId &&
            !busy
}

/**
 * 搬家模式 ViewModel：
 * 选择老住处与目标住处 → 按位置排序浏览 → 多选 → 选择迁移方式 → 执行迁移。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoveViewModel(
    private val repository: ItemRepository,
) : ViewModel() {

    /** 默认按位置排序（设计文档要求）。 */
    private val moveSort = CompositeSort(
        listOf(
            SortSpec(SortField.LOCATION, ascending = true),
            SortSpec(SortField.NAME, ascending = true),
        ),
    )

    private val stateInternal = MutableStateFlow(MoveUiState())

    private val itemFilterFlow = stateInternal
        .map { state ->
            FilterSpec(
                residenceId = state.fromResidenceId,
                locationId = state.filter.locationId,
                typeName = state.filter.typeName,
                sizeCategory = state.filter.sizeCategory,
            )
        }
        .distinctUntilChanged()

    private val itemsFlow = itemFilterFlow.flatMapLatest { filter ->
        repository.observeItems(filter, moveSort)
    }

    val uiState: StateFlow<MoveUiState> = combine(stateInternal, itemsFlow) { state, items ->
        state.copy(items = items)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MoveUiState(),
    )

    init {
        viewModelScope.launch {
            val residences = repository.getResidences()
            val from = residences.firstOrNull()?.id ?: 0L
            val to = residences.getOrNull(1)?.id ?: 0L
            stateInternal.value = stateInternal.value.copy(
                residences = residences,
                fromResidenceId = from,
                toResidenceId = to,
                fromLocations = repository.getLocations(from),
                toLocations = if (to > 0) repository.getLocations(to) else emptyList(),
                target = MigrationTarget.ToUnset,
            )
        }
        // 住处列表变化（例如新增住处）时同步
        viewModelScope.launch {
            repository.observeResidences().collect { residences ->
                stateInternal.value = stateInternal.value.copy(residences = residences)
            }
        }
    }

    fun onFromResidenceChange(residenceId: Long) {
        viewModelScope.launch {
            val locations = repository.getLocations(residenceId)
            stateInternal.value = stateInternal.value.copy(
                fromResidenceId = residenceId,
                fromLocations = locations,
                selectedIds = emptySet(),
            )
        }
    }

    fun onToResidenceChange(residenceId: Long) {
        viewModelScope.launch {
            val locations = repository.getLocations(residenceId)
            stateInternal.value = stateInternal.value.copy(
                toResidenceId = residenceId,
                toLocations = locations,
                targetLocationId = locations.firstOrNull()?.id ?: 0L,
            )
        }
    }

    fun onTargetChange(target: MigrationTarget) {
        stateInternal.value = stateInternal.value.copy(
            target = target,
            targetLocationId = when (target) {
                is MigrationTarget.SpecificLocation -> target.locationId
                else -> 0L
            },
        )
    }

    fun onTargetLocationChange(locationId: Long) {
        stateInternal.value = stateInternal.value.copy(
            target = MigrationTarget.SpecificLocation(locationId),
            targetLocationId = locationId,
        )
    }

    fun onFilterChange(transform: (FilterSpec) -> FilterSpec) {
        stateInternal.value = stateInternal.value.copy(
            filter = transform(stateInternal.value.filter),
            selectedIds = emptySet(),
        )
    }

    fun toggleSelection(id: Long) {
        stateInternal.value = stateInternal.value.let { state ->
            state.copy(
                selectedIds = if (id in state.selectedIds) {
                    state.selectedIds - id
                } else {
                    state.selectedIds + id
                },
            )
        }
    }

    fun selectAll() {
        stateInternal.value = stateInternal.value.let { state ->
            state.copy(selectedIds = state.items.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        stateInternal.value = stateInternal.value.copy(selectedIds = emptySet())
    }

    fun migrate() {
        val state = stateInternal.value
        if (!state.canMigrate) return
        stateInternal.value = state.copy(busy = true)
        viewModelScope.launch {
            try {
                val target = when (val t = state.target) {
                    is MigrationTarget.SpecificLocation -> t
                    MigrationTarget.SameLocationName -> MigrationTarget.SameLocationName
                    MigrationTarget.ToUnset -> MigrationTarget.ToUnset
                }
                val moved = repository.migrateItems(
                    itemIds = state.selectedIds.toList(),
                    targetResidenceId = state.toResidenceId,
                    target = target,
                )
                val targetName = state.residences.firstOrNull { it.id == state.toResidenceId }?.name.orEmpty()
                stateInternal.value = stateInternal.value.copy(
                    busy = false,
                    finished = true,
                    selectedIds = emptySet(),
                    message = "已迁移 $moved 件物品到「$targetName」",
                )
            } catch (e: Exception) {
                stateInternal.value = stateInternal.value.copy(
                    busy = false,
                    message = "迁移失败：${e.message ?: "未知错误"}",
                )
            }
        }
    }

    fun consumeMessage() {
        stateInternal.value = stateInternal.value.copy(message = null, finished = false)
    }

    companion object {
        fun factory(repository: ItemRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MoveViewModel(repository) as T
            }
    }
}
