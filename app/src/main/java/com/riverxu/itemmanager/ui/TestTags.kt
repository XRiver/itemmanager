package com.riverxu.itemmanager.ui

/**
 * UI 测试用的稳定标识。
 *
 * 为什么不直接用文字查找：可点击容器（如 ExtendedFloatingActionButton）的内部文字
 * 不一定会合并进它的语义节点，导致 `onNodeWithText` 在合并树中找不到该控件；
 * 用 testTag 直接标识控件本身，既稳定又不依赖文案。
 */
object TestTags {
    const val FAB_ADD_ITEM = "fab_add_item"
    const val FIELD_ITEM_NAME = "field_item_name"
    const val FIELD_NOTE = "field_note"
    const val ACTION_SAVE = "action_save"
    const val HOME_ACTION_SEARCH = "home_action_search"
    const val HOME_ACTION_MORE = "home_action_more"
    const val HOME_ACTION_FILTER = "home_action_filter"
    const val SEARCH_FIELD = "search_field"
    const val SETTINGS_LIST = "settings_list"
    const val ITEM_ROW_PREFIX = "item_row_"
}
