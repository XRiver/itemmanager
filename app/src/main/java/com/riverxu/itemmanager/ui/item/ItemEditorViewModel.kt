package com.riverxu.itemmanager.ui.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riverxu.itemmanager.data.local.database.LocationEntity
import com.riverxu.itemmanager.data.local.database.ResidenceEntity
import com.riverxu.itemmanager.data.local.database.TagEntity
import com.riverxu.itemmanager.data.local.database.TypeEntity
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.data.local.prefs.PreferencesStore
import com.riverxu.itemmanager.data.model.ItemInput
import com.riverxu.itemmanager.data.model.NameValidation
import com.riverxu.itemmanager.data.repository.ItemRepository
import com.riverxu.itemmanager.domain.model.FieldLimits
import com.riverxu.itemmanager.domain.model.PresetLocations
import com.riverxu.itemmanager.domain.model.SizeCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * 编辑表单状态。
 */
data class ItemEditorState(
    val itemId: Long = 0L,
    val isEditing: Boolean = false,
    val name: String = "",
    val note: String = "",
    val sizeCategory: SizeCategory = SizeCategory.default,
    val typeName: String? = null,
    val photoPath: String? = null,
    val pendingPhotoFile: File? = null,
    val purchaseDate: Long? = null,
    val purchaseDateIsNever: Boolean = false,
    val expiryDate: Long? = null,
    val residenceId: Long = 0L,
    val locationId: Long = 0L,
    val residences: List<ResidenceEntity> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val types: List<TypeEntity> = emptyList(),
    /** 全部可选标签（LRU 排序） */
    val availableTags: List<TagEntity> = emptyList(),
    /** 当前已选标签；新建物品时默认为空 */
    val selectedTags: Set<String> = emptySet(),
    /** 录入时间（编辑既有物品时才有值，只读展示） */
    val createdAt: Long? = null,
    val sameNameCount: Int = 0,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
) {
    val canSave: Boolean
        get() = name.isNotBlank() &&
            name.length <= FieldLimits.ITEM_NAME_MAX &&
            residenceId > 0 &&
            locationId > 0 &&
            !saving
}

/**
 * 添加 / 编辑物品 ViewModel。
 *
 * 负责默认值带入（上次选择的住处、位置、类型、大小分类、购入时间）与校验提示。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemEditorViewModel(
    private val repository: ItemRepository,
    private val preferences: PreferencesStore,
    private val photoStore: PhotoStore,
    private val editingItemId: Long,
) : ViewModel() {

    private val formState = MutableStateFlow(ItemEditorState(isEditing = editingItemId > 0, itemId = editingItemId))
    val state: StateFlow<ItemEditorState> = formState.asStateFlow()

    private val residenceIdFlow = formState.map { it.residenceId }.distinctUntilChanged()

    private val locationsFlow = residenceIdFlow.flatMapLatest { residenceId ->
        if (residenceId <= 0) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            repository.observeLocationsLru(residenceId)
        }
    }

    private val typesFlow = repository.observeTypesLru()
    private val tagsFlow = repository.observeTagsLru()
    private val residencesFlow = repository.observeResidences()

    init {
        viewModelScope.launch {
            val residences = repository.getResidences()
            val types = repository.getTypes()
            val existing = if (editingItemId > 0) repository.getItem(editingItemId) else null

            if (existing != null) {
                val locations = repository.getLocations(existing.residenceId)
                formState.value = formState.value.copy(
                    itemId = existing.id,
                    isEditing = true,
                    name = existing.name,
                    note = existing.note.orEmpty(),
                    sizeCategory = existing.sizeCategory,
                    typeName = existing.type,
                    photoPath = existing.photoPath,
                    purchaseDate = existing.purchaseDate,
                    purchaseDateIsNever = existing.purchaseDate == null,
                    expiryDate = existing.expiryDate,
                    residenceId = existing.residenceId,
                    locationId = existing.locationId,
                    residences = residences,
                    locations = locations,
                    types = types,
                    // 编辑时带入既有标签
                    selectedTags = repository.getItemDetail(existing.id)?.tags?.toSet().orEmpty(),
                    createdAt = existing.createdAt,
                )
            } else {
                // 默认值带入：上次的住处 / 位置 / 类型 / 大小分类 / 购入时间
                // 注意：标签是刻意「不带入默认值」的，新建物品时始终为空
                val residenceId = resolveDefaultResidenceId(residences)
                val locationId = resolveDefaultLocationId(residenceId)
                formState.value = formState.value.copy(
                    name = "",
                    sizeCategory = preferences.lastSizeCategory() ?: SizeCategory.default,
                    typeName = preferences.lastTypeName(),
                    purchaseDate = preferences.lastPurchaseDate(),
                    purchaseDateIsNever = preferences.lastPurchaseDate() == null,
                    residenceId = residenceId,
                    locationId = locationId,
                    residences = residences,
                    locations = repository.getLocations(residenceId),
                    types = types,
                    selectedTags = emptySet(),
                )
            }
        }

        viewModelScope.launch {
            tagsFlow.collect { tags -> formState.value = formState.value.copy(availableTags = tags) }
        }

        viewModelScope.launch {
            residencesFlow.collect { residences ->
                formState.value = formState.value.copy(residences = residences)
                // 住处被删除时回退到第一个住处
                if (residences.none { it.id == formState.value.residenceId } && residences.isNotEmpty()) {
                    onResidenceChange(residences.first().id)
                }
            }
        }

        viewModelScope.launch {
            locationsFlow.collect { locations ->
                formState.value = formState.value.copy(locations = locations)
                if (locations.isNotEmpty() && locations.none { it.id == formState.value.locationId }) {
                    val fallback = locations.firstOrNull { it.name == PresetLocations.UNSET } ?: locations.first()
                    formState.value = formState.value.copy(locationId = fallback.id)
                }
            }
        }

        viewModelScope.launch {
            typesFlow.collect { types -> formState.value = formState.value.copy(types = types) }
        }
    }

    private suspend fun resolveDefaultResidenceId(residences: List<ResidenceEntity>): Long {
        val last = preferences.lastResidenceId()
        if (last > 0 && residences.any { it.id == last }) return last
        return residences.firstOrNull()?.id ?: 0L
    }

    private suspend fun resolveDefaultLocationId(residenceId: Long): Long {
        if (residenceId <= 0) return 0L
        val last = preferences.lastLocationId(residenceId)
        if (last > 0 && repository.getLocation(last) != null) return last
        return repository.getUnsetLocationId(residenceId)
    }

    // ---------------- 表单操作 ----------------

    fun onNameChange(value: String) {
        if (value.length > FieldLimits.ITEM_NAME_MAX) return
        formState.value = formState.value.copy(name = value)
        checkSameName(value)
    }

    private fun checkSameName(value: String) {
        viewModelScope.launch {
            val result = repository.validateItemName(value, editingItemId)
            val count = (result as? NameValidation.Duplicated)?.count ?: 0
            formState.value = formState.value.copy(sameNameCount = count)
        }
    }

    fun onNoteChange(value: String) {
        if (value.length > FieldLimits.NOTE_MAX) return
        formState.value = formState.value.copy(note = value)
    }

    fun onSizeCategoryChange(category: SizeCategory) {
        formState.value = formState.value.copy(sizeCategory = category)
    }

    fun onTypeChange(name: String?) {
        formState.value = formState.value.copy(typeName = name)
    }

    fun onCreateType(name: String) {
        viewModelScope.launch {
            repository.addType(name)
            formState.value = formState.value.copy(typeName = name.trim())
        }
    }

    // ---------------- 标签（多选） ----------------

    /** 点选 / 取消某个标签。 */
    fun onToggleTag(name: String) {
        val current = formState.value.selectedTags
        val next = if (name in current) current - name else current + name
        formState.value = formState.value.copy(selectedTags = next)
    }

    /** 新建标签并直接选中它。 */
    fun onCreateTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addTag(trimmed)
            formState.value = formState.value.copy(
                selectedTags = formState.value.selectedTags + trimmed,
                availableTags = repository.getTags(),
            )
        }
    }

    fun onResidenceChange(residenceId: Long) {
        viewModelScope.launch {
            val locationId = resolveDefaultLocationId(residenceId)
            formState.value = formState.value.copy(
                residenceId = residenceId,
                locationId = locationId,
                locations = repository.getLocations(residenceId),
            )
        }
    }

    fun onLocationChange(locationId: Long) {
        formState.value = formState.value.copy(locationId = locationId)
    }

    fun onCreateLocation(name: String) {
        val residenceId = formState.value.residenceId
        if (residenceId <= 0) return
        viewModelScope.launch {
            val id = repository.addLocation(residenceId, name)
            formState.value = formState.value.copy(
                locationId = id,
                locations = repository.getLocations(residenceId),
            )
        }
    }

    fun onPurchaseDateChange(millis: Long?, never: Boolean) {
        formState.value = formState.value.copy(
            purchaseDate = millis,
            purchaseDateIsNever = never || millis == null,
        )
    }

    fun onExpiryDateChange(millis: Long?) {
        formState.value = formState.value.copy(expiryDate = millis)
    }

    /**
     * 选定照片：先记入待处理文件，保存时再按配置质量压缩入库。
     */
    fun onPhotoPicked(file: File) {
        formState.value = formState.value.copy(pendingPhotoFile = file)
    }

    fun onPhotoRemoved() {
        formState.value = formState.value.copy(photoPath = null, pendingPhotoFile = null)
    }

    // ---------------- 保存 ----------------

    fun save(maxBytes: Long) {
        val current = formState.value
        if (!current.canSave) {
            formState.value = current.copy(
                message = when {
                    current.name.isBlank() -> "请填写物品名称"
                    current.locationId <= 0 -> "请选择存放位置"
                    current.residenceId <= 0 -> "请选择住处"
                    else -> "请检查表单内容"
                },
            )
            return
        }
        formState.value = current.copy(saving = true)
        viewModelScope.launch {
            try {
                val input = ItemInput(
                    id = current.itemId,
                    residenceId = current.residenceId,
                    name = current.name.trim(),
                    note = current.note,
                    sizeCategory = current.sizeCategory,
                    type = current.typeName,
                    tags = current.selectedTags.toList(),
                    photoPath = current.photoPath,
                    purchaseDate = if (current.purchaseDateIsNever) null else current.purchaseDate,
                    expiryDate = current.expiryDate,
                    locationId = current.locationId,
                )

                val itemId = if (current.isEditing) {
                    repository.updateItem(input)
                    current.itemId
                } else {
                    repository.addItem(input)
                }

                // 处理待保存照片
                val pending = current.pendingPhotoFile
                // 极小目标（如 32 KB）下压缩器可能已到尺寸/质量下限仍达不到，
                // 这时如实告知实际大小，而不是假装"不超过目标"
                var photoNote: String? = null
                if (pending != null) {
                    val outcome = photoStore.saveCompressedPhoto(pending, itemId, maxBytes)
                    when (outcome) {
                        is PhotoStore.SaveOutcome.Success -> {
                            repository.updateItem(input.copy(id = itemId, photoPath = outcome.fileName))
                            if (outcome.bytes > maxBytes) {
                                photoNote = "照片已压到最小，实际 ${outcome.bytes / 1024} KB" +
                                    "（超过目标 ${maxBytes / 1024} KB）"
                            } else {
                                photoNote = "照片 ${outcome.bytes / 1024} KB"
                            }
                        }

                        is PhotoStore.SaveOutcome.Failure -> {
                            formState.value = formState.value.copy(
                                saving = false,
                                saved = true,
                                message = "物品已保存，但${outcome.message}",
                            )
                            persistDefaults()
                            return@launch
                        }
                    }
                    pending.delete()
                }

                persistDefaults()
                val base = if (current.isEditing) "已保存修改" else "已添加「${current.name.trim()}」"
                formState.value = formState.value.copy(
                    saving = false,
                    saved = true,
                    pendingPhotoFile = null,
                    message = if (photoNote != null) "$base · $photoNote" else base,
                )
            } catch (e: Exception) {
                formState.value = formState.value.copy(
                    saving = false,
                    message = "保存失败：${e.message ?: "未知错误"}",
                )
            }
        }
    }

    private suspend fun persistDefaults() {
        val current = formState.value
        preferences.setLastResidenceId(current.residenceId)
        preferences.setLastLocationId(current.residenceId, current.locationId)
        preferences.setLastTypeName(current.typeName)
        preferences.setLastSizeCategory(current.sizeCategory)
        preferences.setLastPurchaseDate(current.purchaseDate)
    }

    fun consumeMessage() {
        formState.value = formState.value.copy(message = null)
    }

    fun duplicateWarning(): String? {
        val count = formState.value.sameNameCount
        return if (count > 0) "已有 $count 个同名物品" else null
    }

    companion object {
        fun factory(
            repository: ItemRepository,
            preferences: PreferencesStore,
            photoStore: PhotoStore,
            itemId: Long,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ItemEditorViewModel(repository, preferences, photoStore, itemId) as T
        }

        /** 供 SavedStateHandle 场景使用（当前未启用）。 */
        @Suppress("unused")
        fun factoryWithHandle(
            repository: ItemRepository,
            preferences: PreferencesStore,
            photoStore: PhotoStore,
            handle: SavedStateHandle,
        ): ViewModelProvider.Factory = factory(
            repository,
            preferences,
            photoStore,
            handle.get<Long>("itemId") ?: 0L,
        )
    }
}
