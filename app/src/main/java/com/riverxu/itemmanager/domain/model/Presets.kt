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
 * 各字段长度上限（设计文档「边界情况」）。
 */
object FieldLimits {
    const val ITEM_NAME_MAX = 100
    const val LOCATION_NAME_MAX = 50
    const val RESIDENCE_NAME_MAX = 50
    const val TYPE_NAME_MAX = 50
    const val NOTE_MAX = 2000
    const val SHORTCUT_NAME_MAX = 30
}
