package com.riverxu.itemmanager.data.local.database

import androidx.room.ColumnInfo
import androidx.room.TypeConverter
import com.riverxu.itemmanager.domain.model.SizeCategory
import com.riverxu.itemmanager.domain.model.SortField
import com.riverxu.itemmanager.domain.model.SortSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room 类型转换器：枚举与复合排序结构以文本方式持久化。
 */
class Converters {

    @TypeConverter
    fun sizeCategoryToString(value: SizeCategory?): String? = value?.name

    @TypeConverter
    fun stringToSizeCategory(value: String?): SizeCategory? =
        value?.let { SizeCategory.fromNameOrNull(it) }

    @TypeConverter
    fun sortSpecsToJson(value: List<SortSpec>?): String {
        val array = JSONArray()
        value.orEmpty().forEach { spec ->
            array.put(
                JSONObject()
                    .put("field", spec.field.name)
                    .put("ascending", spec.ascending)
                    .put("nullsLast", spec.nullsLast),
            )
        }
        return array.toString()
    }

    @TypeConverter
    fun jsonToSortSpecs(value: String?): List<SortSpec> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(value)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val field = SortField.fromNameOrNull(obj.optString("field")) ?: continue
                    add(
                        SortSpec(
                            field = field,
                            ascending = obj.optBoolean("ascending", true),
                            nullsLast = obj.optBoolean("nullsLast", true),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * 住处。
 */
@androidx.room.Entity(
    tableName = "residence",
    indices = [androidx.room.Index(value = ["name"])],
)
data class ResidenceEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * 位置。每个住处创建时自动生成"灵活"和"未设置"两个预置位置。
 */
@androidx.room.Entity(
    tableName = "location",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = ResidenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["residenceId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [
        androidx.room.Index(value = ["residenceId"]),
        androidx.room.Index(value = ["residenceId", "name"], unique = true),
        androidx.room.Index(value = ["lastUsedAt", "usageCount"]),
    ],
)
data class LocationEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val residenceId: Long,
    val name: String,
    val isPreset: Boolean = false,
    val usageCount: Int = 0,
    val lastUsedAt: Long = 0L,
) {
    val isFlexible: Boolean get() = name == LOCATION_FLEXIBLE
    val isUnset: Boolean get() = name == LOCATION_UNSET

    companion object {
        const val LOCATION_FLEXIBLE = "灵活"
        const val LOCATION_UNSET = "未设置"
    }
}

/**
 * 类型。预置类型不可删除。
 */
@androidx.room.Entity(
    tableName = "item_type",
    indices = [
        androidx.room.Index(value = ["name"], unique = true),
        androidx.room.Index(value = ["lastUsedAt", "usageCount"]),
    ],
)
data class TypeEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val isPreset: Boolean = false,
    val usageCount: Int = 0,
    val lastUsedAt: Long = 0L,
)

/**
 * 标签。与「类型」类似但一件物品可同时拥有多个标签，且由用户手动创建。
 * 预置标签（待汰换 / 待出售）不可删除。
 */
@androidx.room.Entity(
    tableName = "tag",
    indices = [
        androidx.room.Index(value = ["name"], unique = true),
        androidx.room.Index(value = ["lastUsedAt", "usageCount"]),
    ],
)
data class TagEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val isPreset: Boolean = false,
    val usageCount: Int = 0,
    val lastUsedAt: Long = 0L,
)

/**
 * 物品 ↔ 标签 多对多关联。
 */
@androidx.room.Entity(
    tableName = "item_tag",
    primaryKeys = ["itemId", "tagId"],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
        androidx.room.ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [androidx.room.Index(value = ["tagId"])],
)
data class ItemTagCrossRef(
    val itemId: Long,
    val tagId: Long,
)

/**
 * 标签名在 SQL `GROUP_CONCAT` 中使用的分隔符。
 *
 * 用 ASCII 单元分隔符而不是逗号：标签允许用户自由输入，逗号是常见字符，
 * 用它做分隔会导致标签名被切断。
 */
const val TAG_SEPARATOR = '\u001F'

/**
 * 物品。
 *
 * [purchaseDate] 为 null 表示"很久"。
 */
@androidx.room.Entity(
    tableName = "item",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = ResidenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["residenceId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
        androidx.room.ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [
        androidx.room.Index(value = ["residenceId"]),
        androidx.room.Index(value = ["locationId"]),
        androidx.room.Index(value = ["expiryDate"]),
        androidx.room.Index(value = ["name"]),
        androidx.room.Index(value = ["type"]),
    ],
)
data class ItemEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val residenceId: Long,
    val name: String,
    val note: String? = null,
    val sizeCategory: SizeCategory = SizeCategory.SMALL,
    val type: String? = null,
    val photoPath: String? = null,
    /** null 表示"很久" */
    val purchaseDate: Long? = null,
    val expiryDate: Long? = null,
    val locationId: Long,
    /** 录入时间：物品创建时由系统写入，用户不可修改 */
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * 最近一次「位置发生变化」的时间，由系统维护，用户不可修改。
     *
     * 注意与 [updatedAt] 的区别：改名称、改注释、换照片都不会更新它，
     * 只有位置（或所属住处）变化时才更新。
     */
    val locationUpdatedAt: Long = System.currentTimeMillis(),
)

/**
 * 物品列表查询结果（含关联住处/位置名称与标签）。
 */
data class ItemListRow(
    val id: Long,
    val name: String,
    val note: String?,
    val sizeCategory: SizeCategory,
    val type: String?,
    val photoPath: String?,
    val purchaseDate: Long?,
    val expiryDate: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val locationUpdatedAt: Long,
    val residenceId: Long,
    @ColumnInfo(name = "residenceName") val residenceName: String,
    val locationId: Long,
    @ColumnInfo(name = "locationName") val locationName: String,
    /** 由 SQL `GROUP_CONCAT` 拼出的标签名，分隔符见 [TAG_SEPARATOR] */
    @ColumnInfo(name = "tagNames") val tagNames: String? = null,
) {
    /** 标签列表（已排序、去空） */
    val tags: List<String>
        get() = tagNames
            ?.split(TAG_SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun toEntity(): ItemEntity = ItemEntity(
        id = id,
        residenceId = residenceId,
        name = name,
        note = note,
        sizeCategory = sizeCategory,
        type = type,
        photoPath = photoPath,
        purchaseDate = purchaseDate,
        expiryDate = expiryDate,
        locationId = locationId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        locationUpdatedAt = locationUpdatedAt,
    )
}

/**
 * 筛选快捷入口。
 */
@androidx.room.Entity(tableName = "filter_shortcut")
data class FilterShortcutEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** FilterSpec 的 JSON 序列化结果 */
    val filterConfig: String,
    /** 可选的排序快照 JSON */
    val sortConfig: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
