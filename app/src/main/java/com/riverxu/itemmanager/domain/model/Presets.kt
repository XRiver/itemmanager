package com.riverxu.itemmanager.domain.model

/**
 * 预置类型（设计文档指定，不允许删除）。
 */
object PresetTypes {
    const val ELECTRONICS = "电子产品"
    const val BOOKS = "书籍"
    const val FOOD = "食物"
    const val OFFICE = "办公用品"

    val ALL = listOf(ELECTRONICS, BOOKS, FOOD, OFFICE)
}

/**
 * 预置位置：每个住处创建时自动生成，不允许删除。
 */
object PresetLocations {
    const val FLEXIBLE = "灵活"
    const val UNSET = "未设置"

    val ALL = listOf(FLEXIBLE, UNSET)
}

/**
 * 预置标签：与「类型」不同，标签由用户手动创建、一件物品可多选。
 * 这两个是预置项，不允许删除。
 */
object PresetTags {
    const val TO_REPLACE = "待汰换"
    const val TO_SELL = "待出售"

    val ALL = listOf(TO_REPLACE, TO_SELL)
}

/**
 * 各字段长度上限（设计文档「边界情况」）。
 */
object FieldLimits {
    const val ITEM_NAME_MAX = 100
    const val LOCATION_NAME_MAX = 50
    const val RESIDENCE_NAME_MAX = 50
    const val TYPE_NAME_MAX = 50
    const val TAG_NAME_MAX = 20
    const val NOTE_MAX = 2000
    const val SHORTCUT_NAME_MAX = 30
}
