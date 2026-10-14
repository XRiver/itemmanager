package com.riverxu.itemmanager.ui.item

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riverxu.itemmanager.data.model.ItemDetail
import com.riverxu.itemmanager.data.repository.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ItemDetailState(
    val detail: ItemDetail? = null,
    val loading: Boolean = true,
    val deleted: Boolean = false,
    val message: String? = null,
)

/**
 * 物品详情 ViewModel：支持单字段就地编辑（名称、注释、位置、类型、日期）。
 */
class ItemDetailViewModel(
    private val repository: ItemRepository,
    private val itemId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ItemDetailState())
    val state: StateFlow<ItemDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        val detail = repository.getItemDetail(itemId)
        _state.value = _state.value.copy(detail = detail, loading = false)
    }

    /** 局部更新：读出实体 → 改字段 → 写回，保持其他字段不变。 */
    private fun patch(
        tagsOverride: List<String>? = null,
        transform: (com.riverxu.itemmanager.data.local.database.ItemEntity) -> com.riverxu.itemmanager.data.local.database.ItemEntity,
    ) {
        viewModelScope.launch { applyPatch(tagsOverride, transform) }
    }

    /**
     * @param tagsOverride 新的标签集合；为 null 时沿用库中现有标签。
     *
     * 注意：必须显式带上标签，否则每次局部编辑都会把物品的标签清空
     * （`ItemInput.tags` 默认是空列表，而 updateItem 是整体替换标签）。
     */
    private suspend fun applyPatch(
        tagsOverride: List<String>?,
        transform: (com.riverxu.itemmanager.data.local.database.ItemEntity) -> com.riverxu.itemmanager.data.local.database.ItemEntity,
    ) {
        val detail = repository.getItemDetail(itemId) ?: return
        val updated = transform(detail.item).copy(updatedAt = System.currentTimeMillis())
        repository.updateItem(
            com.riverxu.itemmanager.data.model.ItemInput(
                id = updated.id,
                residenceId = updated.residenceId,
                name = updated.name,
                note = updated.note,
                sizeCategory = updated.sizeCategory,
                type = updated.type,
                tags = tagsOverride ?: detail.tags,
                photoPath = updated.photoPath,
                purchaseDate = updated.purchaseDate,
                expiryDate = updated.expiryDate,
                locationId = updated.locationId,
            ),
        )
        load()
    }

    /** 整体替换标签集合。 */
    fun updateTags(tags: List<String>) = patch(tagsOverride = tags) { it }

    fun updateName(name: String) = patch { it.copy(name = name.trim()) }

    fun updateNote(note: String?) = patch { it.copy(note = note?.trim()?.ifBlank { null }) }

    fun updateSizeCategory(category: com.riverxu.itemmanager.domain.model.SizeCategory) =
        patch { it.copy(sizeCategory = category) }

    fun updateType(type: String?) = patch { it.copy(type = type?.trim()?.ifBlank { null }) }

    fun updateLocation(locationId: Long) = patch { it.copy(locationId = locationId) }

    fun updateResidence(residenceId: Long) {
        viewModelScope.launch {
            val unsetId = repository.getUnsetLocationId(residenceId)
            applyPatch(null) { it.copy(residenceId = residenceId, locationId = unsetId) }
        }
    }

    fun updatePurchaseDate(millis: Long?) = patch { it.copy(purchaseDate = millis) }

    fun updateExpiryDate(millis: Long?) = patch { it.copy(expiryDate = millis) }

    fun delete() {
        viewModelScope.launch {
            repository.deleteItem(itemId)
            _state.value = _state.value.copy(deleted = true)
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    companion object {
        fun factory(repository: ItemRepository, itemId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ItemDetailViewModel(repository, itemId) as T
            }
    }
}
