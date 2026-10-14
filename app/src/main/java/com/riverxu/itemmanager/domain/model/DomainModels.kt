package com.riverxu.itemmanager.domain.model

/**
 * 物品大小分类（预置，不可增删）。
 */
enum class SizeCategory(val label: String) {
    FURNITURE("家具"),
    LARGE("大件"),
    SMALL("小件");

    companion object {
        val default: SizeCategory = SMALL

        fun fromNameOrNull(value: String?): SizeCategory? =
            entries.firstOrNull { it.name == value }

        fun fromNameOrDefault(value: String?): SizeCategory =
            fromNameOrNull(value) ?: default
    }
}

/**
 * 排序字段。
 */
enum class SortField(val label: String) {
    LOCATION("位置"),
    NAME("名称"),
    PURCHASE_DATE("购入时间"),
    EXPIRY_DATE("过期时间"),
    /** 录入时间（系统自动写入，用户不可修改） */
    CREATED_AT("录入时间"),
    UPDATED_AT("修改时间"),
    /** 最近更新位置时间（系统自动写入，用户不可修改） */
    LOCATION_UPDATED_AT("最近更新位置"),
    SIZE_CATEGORY("大小分类"),
    TYPE("类型"),
    RESIDENCE("住处");

    companion object {
        /** 首页默认排序：位置 → 购入时间 → 名称（对应设计文档示例） */
        fun defaultSort(): CompositeSort = CompositeSort(
            listOf(
                SortSpec(SortField.LOCATION, ascending = true),
                SortSpec(SortField.PURCHASE_DATE, ascending = false, nullsLast = true),
                SortSpec(SortField.NAME, ascending = true),
            ),
        )

        fun fromNameOrNull(value: String?): SortField? = entries.firstOrNull { it.name == value }
    }
}

/**
 * 单条排序规则。
 *
 * @param ascending true 为升序
 * @param nullsLast 空值是否排在最后（仅对可空字段有意义，默认排最后）
 */
data class SortSpec(
    val field: SortField,
    val ascending: Boolean = true,
    val nullsLast: Boolean = true,
)

/**
 * 复合排序，最多 3 个字段（设计文档要求）。
 */
data class CompositeSort(val specs: List<SortSpec> = SortField.defaultSort().specs) {

    fun normalized(): CompositeSort = copy(specs = specs.take(MAX_FIELDS))

    fun withSpec(spec: SortSpec): CompositeSort {
        val existing = specs.indexOfFirst { it.field == spec.field }
        val next = if (existing >= 0) {
            specs.toMutableList().also { it[existing] = spec }
        } else {
            (specs + spec)
        }
        return CompositeSort(next).normalized()
    }

    fun toggled(field: SortField): CompositeSort {
        val current = specs.firstOrNull { it.field == field }
        return if (current == null) {
            withSpec(SortSpec(field))
        } else {
            withSpec(current.copy(ascending = !current.ascending))
        }
    }

    companion object {
        const val MAX_FIELDS = 3
    }
}

/**
 * 列表展示样式。
 */
enum class LayoutMode { LIST, GRID }

/**
 * 筛选条件。[null] 表示该条件不参与筛选。
 *
 * @param expiration 过期状态筛选，见 [ExpirationFilter]
 * @param keyword 全文搜索关键字（匹配名称与注释）
 */
data class FilterSpec(
    val keyword: String = "",
    val residenceId: Long? = null,
    val locationId: Long? = null,
    val typeName: String? = null,
    val sizeCategory: SizeCategory? = null,
    val expiration: ExpirationFilter? = null,
    val purchaseFrom: Long? = null,
    val purchaseTo: Long? = null,
    /** 标签筛选：命中**任意一个**所选标签即算符合 */
    val tags: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = keyword.isBlank() &&
            residenceId == null &&
            locationId == null &&
            typeName == null &&
            sizeCategory == null &&
            expiration == null &&
            purchaseFrom == null &&
            purchaseTo == null &&
            tags.isEmpty()

    fun activeCount(): Int {
        var n = 0
        if (keyword.isNotBlank()) n++
        if (residenceId != null) n++
        if (locationId != null) n++
        if (typeName != null) n++
        if (sizeCategory != null) n++
        if (expiration != null) n++
        if (purchaseFrom != null || purchaseTo != null) n++
        if (tags.isNotEmpty()) n++
        return n
    }

    companion object {
        val EMPTY = FilterSpec()
    }
}

/**
 * 过期状态筛选。
 */
enum class ExpirationFilter(val label: String) {
    EXPIRED("已过期"),
    WITHIN_7_DAYS("7天内过期"),
    WITHIN_30_DAYS("30天内过期"),
    WITHIN_90_DAYS("90天内过期"),
    HAS_EXPIRY("设置了过期时间"),
    NO_EXPIRY("未设置过期时间"),
    ANY("全部时间");
}

/**
 * 保存的筛选快捷入口。
 */
data class FilterShortcut(
    val id: Long = 0L,
    val name: String,
    val filter: FilterSpec,
    val sort: CompositeSort? = null,
)
