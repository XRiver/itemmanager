package com.riverxu.itemmanager.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.riverxu.itemmanager.data.local.database.LocationEntity
import com.riverxu.itemmanager.data.local.database.ResidenceEntity
import com.riverxu.itemmanager.data.local.database.TypeEntity
import com.riverxu.itemmanager.data.local.export.ExportManager
import com.riverxu.itemmanager.data.local.photo.PhotoCompressor
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.data.local.prefs.PreferencesStore
import com.riverxu.itemmanager.data.model.DataStats
import com.riverxu.itemmanager.data.repository.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val stats: DataStats = DataStats(0, 0, 0, 0, 0, 0),
    val residences: List<ResidenceEntity> = emptyList(),
    val activeResidenceId: Long = 0L,
    val locations: List<LocationEntity> = emptyList(),
    val types: List<TypeEntity> = emptyList(),
    val photoQuality: PhotoCompressor.Quality = PhotoCompressor.Quality.default,
    val themeMode: PreferencesStore.ThemeMode = PreferencesStore.ThemeMode.SYSTEM,
    val confirmDelete: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * 设置页 ViewModel：压缩质量、主题、删除确认、数据统计与导入导出。
 */
class SettingsViewModel(
    private val repository: ItemRepository,
    private val preferences: PreferencesStore,
    private val photoStore: PhotoStore,
    private val exportManager: ExportManager,
) : ViewModel() {

    private val statsState = MutableStateFlow(DataStats(0, 0, 0, 0, 0, 0))
    private val busyState = MutableStateFlow(false)
    private val messageState = MutableStateFlow<String?>(null)
    private val activeResidenceId = MutableStateFlow(0L)

    private val residencesFlow = repository.observeResidences()

    private val activeResidenceFlow = combine(residencesFlow, activeResidenceId) { residences, requested ->
        val valid = residences.any { it.id == requested }
        if (valid) requested else residences.firstOrNull()?.id ?: 0L
    }

    private val locationsFlow = activeResidenceFlow.flatMapLatest { residenceId ->
        if (residenceId > 0) repository.observeLocations(residenceId) else flowOf(emptyList())
    }

    /** 设置项快照。 */
    private val prefsFlow = combine(
        preferences.photoQuality,
        preferences.themeMode,
        preferences.confirmDelete,
    ) { quality, theme, confirm -> Triple(quality, theme, confirm) }

    /** 管理数据快照（住处 / 位置 / 类型）。 */
    private val managementFlow = combine(
        residencesFlow,
        locationsFlow,
        repository.observeTypes(),
        activeResidenceFlow,
    ) { residences, locations, types, activeId ->
        ManagementSnapshot(residences, locations, types, activeId)
    }

    /** 运行状态快照。 */
    private val statusFlow = combine(busyState, statsState) { busy, stats -> busy to stats }

    val uiState: StateFlow<SettingsUiState> = combine(
        managementFlow,
        prefsFlow,
        combine(statusFlow, messageState) { status, message -> Triple(status.first, status.second, message) },
    ) { management, prefs, (busy, stats, message) ->
        SettingsUiState(
            stats = stats,
            residences = management.residences,
            activeResidenceId = management.activeResidenceId,
            locations = management.locations,
            types = management.types,
            photoQuality = prefs.first,
            themeMode = prefs.second,
            confirmDelete = prefs.third,
            busy = busy,
            message = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    private data class ManagementSnapshot(
        val residences: List<ResidenceEntity>,
        val locations: List<LocationEntity>,
        val types: List<TypeEntity>,
        val activeResidenceId: Long,
    )

    init {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch { statsState.value = repository.stats() }
    }

    fun setActiveResidence(residenceId: Long) {
        activeResidenceId.value = residenceId
    }

    // ---------------- 设置项 ----------------

    fun setPhotoQuality(quality: PhotoCompressor.Quality) {
        preferences.setPhotoQuality(quality)
    }

    fun setThemeMode(mode: PreferencesStore.ThemeMode) = preferences.setThemeMode(mode)

    fun setConfirmDelete(value: Boolean) = preferences.setConfirmDelete(value)

    // ---------------- 数据维护 ----------------

    fun optimizeDatabase() {
        viewModelScope.launch {
            busyState.value = true
            messageState.value = try {
                repository.optimize()
                "数据库已优化"
            } catch (e: Exception) {
                "优化失败：${e.message ?: "未知错误"}"
            }
            busyState.value = false
            refreshStats()
        }
    }

    /** Context 由界面层传入，避免 ViewModel 长期持有 Context 造成泄漏。 */
    fun exportTo(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            busyState.value = true
            when (val result = ExportManager.exportToUri(context, repository, photoStore, uri)) {
                is ExportManager.Result.Success -> {
                    messageState.value = "导出成功：${result.value.itemCount} 件物品、" +
                        "${result.value.photoCount} 张照片（${result.value.fileName}）"
                }

                is ExportManager.Result.Failure -> messageState.value = result.message
            }
            busyState.value = false
        }
    }

    fun importFrom(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            busyState.value = true
            when (val result = ExportManager.importFromUri(context, repository, photoStore, uri)) {
                is ExportManager.Result.Success -> {
                    val renamed = result.value.renamedResidences
                    messageState.value = buildString {
                        append("导入成功：${result.value.itemCount} 件物品、")
                        append("${result.value.residenceCount} 个住处")
                        if (renamed.isNotEmpty()) {
                            append("；同名住处已改名（")
                            append(renamed.joinToString("、"))
                            append("）")
                        }
                    }
                }

                is ExportManager.Result.Failure -> messageState.value = result.message
            }
            busyState.value = false
            refreshStats()
        }
    }

    fun consumeMessage() {
        messageState.value = null
    }

    // ---------------- 住处 / 位置 / 类型管理 ----------------

    fun addResidence(name: String) {
        viewModelScope.launch {
            repository.addResidence(name)
            statsState.value = repository.stats()
            messageState.value = "已添加住处「$name」"
        }
    }

    fun renameResidence(id: Long, name: String) {
        viewModelScope.launch {
            repository.renameResidence(id, name)
            messageState.value = "已重命名为「$name」"
        }
    }

    fun deleteResidence(id: Long) {
        viewModelScope.launch {
            repository.deleteResidence(id)
            if (activeResidenceId.value == id) activeResidenceId.value = 0L
            statsState.value = repository.stats()
            messageState.value = "住处及其内部数据已删除"
        }
    }

    fun addLocation(residenceId: Long, name: String) {
        viewModelScope.launch {
            repository.addLocation(residenceId, name)
            statsState.value = repository.stats()
        }
    }

    fun renameLocation(id: Long, name: String) {
        viewModelScope.launch {
            repository.renameLocation(id, name)
            messageState.value = "位置已重命名"
        }
    }

    fun deleteLocation(id: Long) {
        viewModelScope.launch {
            repository.deleteLocation(id)
            statsState.value = repository.stats()
            messageState.value = "位置已删除，相关物品已归入「未设置」"
        }
    }

    fun addType(name: String) {
        viewModelScope.launch {
            repository.addType(name)
            statsState.value = repository.stats()
        }
    }

    fun renameType(id: Long, name: String) {
        viewModelScope.launch {
            repository.renameType(id, name)
            messageState.value = "类型已重命名"
        }
    }

    fun deleteType(id: Long) {
        viewModelScope.launch {
            repository.deleteType(id)
            statsState.value = repository.stats()
            messageState.value = "类型已删除，相关物品的类型已清空"
        }
    }

    companion object {
        fun factory(
            repository: ItemRepository,
            preferences: PreferencesStore,
            photoStore: PhotoStore,
            exportManager: ExportManager,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(
                    repository = repository,
                    preferences = preferences,
                    photoStore = photoStore,
                    exportManager = exportManager,
                ) as T
        }
    }
}
