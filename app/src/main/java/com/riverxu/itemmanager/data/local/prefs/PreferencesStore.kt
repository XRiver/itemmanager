package com.riverxu.itemmanager.data.local.prefs

import android.content.Context
import androidx.core.content.edit
import com.riverxu.itemmanager.data.local.photo.PhotoCompressor
import com.riverxu.itemmanager.domain.model.LayoutMode
import com.riverxu.itemmanager.domain.model.SizeCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用设置与"上次输入"记忆。
 *
 * 使用 SharedPreferences 保存，并以 [StateFlow] 暴露可变设置，供 Compose 直接观察。
 */
class PreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.fromNameOrDefault(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _photoQuality = MutableStateFlow(
        PhotoCompressor.Quality.fromNameOrNull(prefs.getString(KEY_PHOTO_QUALITY, null))
            ?: PhotoCompressor.Quality.default,
    )
    val photoQuality: StateFlow<PhotoCompressor.Quality> = _photoQuality.asStateFlow()

    private val _confirmDelete = MutableStateFlow(prefs.getBoolean(KEY_CONFIRM_DELETE, true))
    val confirmDelete: StateFlow<Boolean> = _confirmDelete.asStateFlow()

    private val _layoutMode = MutableStateFlow(
        if (prefs.getString(KEY_LAYOUT, null) == LayoutMode.GRID.name) LayoutMode.GRID else LayoutMode.LIST,
    )
    val layoutMode: StateFlow<LayoutMode> = _layoutMode.asStateFlow()

    // ---------- 设置项 ----------

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }

    fun setPhotoQuality(quality: PhotoCompressor.Quality) {
        prefs.edit { putString(KEY_PHOTO_QUALITY, quality.name) }
        _photoQuality.value = quality
    }

    fun setConfirmDelete(value: Boolean) {
        prefs.edit { putBoolean(KEY_CONFIRM_DELETE, value) }
        _confirmDelete.value = value
    }

    fun setLayoutMode(mode: LayoutMode) {
        prefs.edit { putString(KEY_LAYOUT, mode.name) }
        _layoutMode.value = mode
    }

    /**
     * 本次启动内不再确认删除（设计文档：左滑删除可设置本次启动不再确认）。
     */
    var skipDeleteConfirmThisSession: Boolean = false

    // ---------- 上次输入记忆（默认值带入） ----------

    fun lastResidenceId(): Long = prefs.getLong(KEY_LAST_RESIDENCE, 0L).takeIf { it > 0 } ?: 0L

    fun setLastResidenceId(id: Long) = prefs.edit { putLong(KEY_LAST_RESIDENCE, id) }

    fun lastLocationId(residenceId: Long): Long =
        prefs.getLong("${KEY_LAST_LOCATION_PREFIX}$residenceId", 0L)

    fun setLastLocationId(residenceId: Long, locationId: Long) =
        prefs.edit { putLong("${KEY_LAST_LOCATION_PREFIX}$residenceId", locationId) }

    fun lastTypeName(): String? = prefs.getString(KEY_LAST_TYPE, null)?.takeIf { it.isNotBlank() }

    fun setLastTypeName(name: String?) =
        prefs.edit { putString(KEY_LAST_TYPE, name?.takeIf { it.isNotBlank() }) }

    fun lastSizeCategory(): SizeCategory? =
        SizeCategory.fromNameOrNull(prefs.getString(KEY_LAST_SIZE, null))

    fun setLastSizeCategory(category: SizeCategory) =
        prefs.edit { putString(KEY_LAST_SIZE, category.name) }

    fun lastPurchaseDate(): Long? =
        prefs.getLong(KEY_LAST_PURCHASE, NEVER).takeIf { it != NEVER }

    /** 记录"很久"（null）与具体日期两种状态。 */
    fun setLastPurchaseDate(millis: Long?) =
        prefs.edit { putLong(KEY_LAST_PURCHASE, millis ?: NEVER) }

    /** 首页最近使用的筛选住处。 */
    fun lastFilterResidenceId(): Long? =
        prefs.getLong(KEY_LAST_FILTER_RESIDENCE, 0L).takeIf { it > 0 }

    fun setLastFilterResidenceId(id: Long?) =
        prefs.edit { putLong(KEY_LAST_FILTER_RESIDENCE, id ?: 0L) }

    /** 数据库是否完成过首次预置。 */
    fun isSeeded(): Boolean = prefs.getBoolean(KEY_SEEDED, false)

    fun markSeeded() = prefs.edit { putBoolean(KEY_SEEDED, true) }

    enum class ThemeMode(val label: String) {
        SYSTEM("跟随系统"),
        LIGHT("浅色"),
        DARK("深色");

        companion object {
            fun fromNameOrDefault(name: String?): ThemeMode =
                entries.firstOrNull { it.name == name } ?: SYSTEM
        }
    }

    companion object {
        private const val PREFS_NAME = "open_item_manager_prefs"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PHOTO_QUALITY = "photo_quality"
        private const val KEY_CONFIRM_DELETE = "confirm_delete"
        private const val KEY_LAYOUT = "layout_mode"
        private const val KEY_LAST_RESIDENCE = "last_residence_id"
        private const val KEY_LAST_LOCATION_PREFIX = "last_location_id_"
        private const val KEY_LAST_TYPE = "last_type_name"
        private const val KEY_LAST_SIZE = "last_size_category"
        private const val KEY_LAST_PURCHASE = "last_purchase_date"
        private const val KEY_LAST_FILTER_RESIDENCE = "last_filter_residence"
        private const val KEY_SEEDED = "seeded"
        private const val NEVER = Long.MIN_VALUE
    }
}
