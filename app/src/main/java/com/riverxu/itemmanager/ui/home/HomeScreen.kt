package com.riverxu.itemmanager.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riverxu.itemmanager.data.local.database.ItemListRow
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.LayoutMode
import com.riverxu.itemmanager.ui.LocalPreferences
import com.riverxu.itemmanager.ui.TestTags
import com.riverxu.itemmanager.ui.components.ConfirmDialog
import com.riverxu.itemmanager.ui.components.EmptyState
import com.riverxu.itemmanager.ui.components.TextInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onKeywordChange: (String) -> Unit,
    onFilterChange: ((FilterSpec) -> FilterSpec) -> Unit,
    onClearFilter: () -> Unit,
    onToggleExpiration: (com.riverxu.itemmanager.domain.model.ExpirationFilter) -> Unit,
    onToggleSize: (com.riverxu.itemmanager.domain.model.SizeCategory) -> Unit,
    onToggleSortField: (com.riverxu.itemmanager.domain.model.SortField) -> Unit,
    onSortChange: (com.riverxu.itemmanager.domain.model.CompositeSort) -> Unit,
    onResetSort: () -> Unit,
    onToggleLayout: () -> Unit,
    onApplyShortcut: (com.riverxu.itemmanager.domain.model.FilterShortcut) -> Unit,
    onSaveShortcut: (String) -> Unit,
    onDeleteShortcut: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onToggleSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteItems: (Collection<Long>) -> Unit,
    onItemClick: (ItemListRow) -> Unit,
    onAddItem: () -> Unit,
    onOpenMoveMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onConsumeMessage: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val preferences = LocalPreferences.current
    var searchActive by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showSaveShortcut by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<Long>?>(null) }
    var pendingShortcutDelete by remember { mutableStateOf<Long?>(null) }
    var skipConfirmThisSession by remember { mutableStateOf(false) }

    val selectionMode = state.selectedIds.isNotEmpty()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onConsumeMessage()
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("已选 ${state.selectedIds.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Outlined.Close, contentDescription = "退出多选")
                        }
                    },
                    actions = {
                        TextButton(onClick = onToggleSelectAll) { Text("全选") }
                        IconButton(onClick = {
                            if (state.confirmDelete && !preferences.skipDeleteConfirmThisSession) {
                                pendingDelete = state.selectedIds.toList()
                            } else {
                                onDeleteItems(state.selectedIds)
                            }
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除所选")
                        }
                    },
                )
            } else {
                Column {
                    TopAppBar(
                        title = {
                            if (searchActive) {
                                OutlinedTextField(
                                    value = state.filter.keyword,
                                    onValueChange = onKeywordChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(TestTags.SEARCH_FIELD),
                                    placeholder = { Text("搜索名称或注释") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { onKeywordChange(""); searchActive = false }) {
                                            Icon(Icons.Outlined.Close, contentDescription = "关闭搜索")
                                        }
                                    },
                                )
                            } else {
                                Column {
                                    Text(
                                        text = "物品管家Free",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (state.filter.isEmpty) {
                                            "共 ${state.totalCount} 件物品"
                                        } else {
                                            "筛选出 ${state.filteredCount} / ${state.totalCount} 件"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        },
                        actions = {
                            if (!searchActive) {
                                IconButton(
                                    onClick = { searchActive = true },
                                    modifier = Modifier.testTag(TestTags.HOME_ACTION_SEARCH),
                                ) {
                                    Icon(Icons.Outlined.Search, contentDescription = "搜索")
                                }
                            }
                            IconButton(
                                onClick = { showFilterSheet = true },
                                modifier = Modifier.testTag(TestTags.HOME_ACTION_FILTER),
                            ) {
                                Icon(Icons.Outlined.FilterList, contentDescription = "筛选")
                            }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.testTag(TestTags.HOME_ACTION_MORE),
                                ) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("排序") },
                                        leadingIcon = { Icon(Icons.Outlined.Sort, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            showSortSheet = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (state.layoutMode == LayoutMode.LIST) "切换为网格视图" else "切换为列表视图",
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (state.layoutMode == LayoutMode.LIST) {
                                                    Icons.Outlined.GridView
                                                } else {
                                                    Icons.Outlined.ViewList
                                                },
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onToggleLayout()
                                        },
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("搬家模式") },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenMoveMode()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("设置") },
                                        onClick = {
                                            menuExpanded = false
                                            onOpenSettings()
                                        },
                                    )
                                    if (!state.filter.isEmpty) {
                                        DropdownMenuItem(
                                            text = { Text("清除全部筛选") },
                                            onClick = {
                                                menuExpanded = false
                                                onClearFilter()
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    QuickFilterRow(
                        state = state,
                        onApplyShortcut = onApplyShortcut,
                        onToggleExpiration = onToggleExpiration,
                        onToggleSize = onToggleSize,
                        onClearFilter = onClearFilter,
                        onSaveShortcut = { showSaveShortcut = true },
                        onDeleteShortcut = { pendingShortcutDelete = it },
                    )
                }
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onAddItem,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("添加物品") },
                    modifier = Modifier.testTag(TestTags.FAB_ADD_ITEM),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.items.isEmpty() -> EmptyState(
                    title = if (state.filter.isEmpty) "还没有物品" else "没有符合条件的物品",
                    description = if (state.filter.isEmpty) {
                        "点击右下角「添加物品」记录第一件物品吧"
                    } else {
                        "试着调整筛选条件或清除筛选"
                    },
                    action = if (state.filter.isEmpty) {
                        null
                    } else {
                        { TextButton(onClick = onClearFilter) { Text("清除筛选") } }
                    },
                )

                state.layoutMode == LayoutMode.LIST -> ItemList(
                    items = state.items,
                    selectedIds = state.selectedIds,
                    selectionMode = selectionMode,
                    confirmDelete = state.confirmDelete && !preferences.skipDeleteConfirmThisSession,
                    onClick = onItemClick,
                    onLongClick = onToggleSelection,
                    onDeleteRequest = { pendingDelete = listOf(it) },
                    onDeleteImmediately = { onDeleteItems(listOf(it)) },
                )

                else -> ItemGrid(
                    items = state.items,
                    selectedIds = state.selectedIds,
                    selectionMode = selectionMode,
                    onClick = onItemClick,
                    onLongClick = onToggleSelection,
                )
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            state = state,
            onDismiss = { showFilterSheet = false },
            onFilterChange = onFilterChange,
            onClearFilter = onClearFilter,
        )
    }

    if (showSortSheet) {
        SortSheet(
            state = state,
            onDismiss = { showSortSheet = false },
            onToggleSortField = onToggleSortField,
            onSortChange = onSortChange,
            onResetSort = onResetSort,
        )
    }

    if (showSaveShortcut) {
        TextInputDialog(
            title = "保存为快捷入口",
            label = "快捷入口名称",
            supportingText = "例如：快过期物品",
            maxLength = com.riverxu.itemmanager.domain.model.FieldLimits.SHORTCUT_NAME_MAX,
            onConfirm = {
                onSaveShortcut(it)
                showSaveShortcut = false
            },
            onDismiss = { showSaveShortcut = false },
        )
    }

    pendingDelete?.let { ids ->
        ConfirmDialog(
            title = "删除物品",
            message = if (ids.size == 1) {
                "确定要删除「${state.items.firstOrNull { it.id == ids.first() }?.name ?: "该物品"}」吗？此操作不可恢复。"
            } else {
                "确定要删除选中的 ${ids.size} 件物品吗？此操作不可恢复。"
            },
            confirmLabel = "删除",
            destructive = true,
            checkBoxLabel = "本次启动不再确认",
            checkBoxChecked = skipConfirmThisSession,
            onCheckBoxChange = { skipConfirmThisSession = it },
            onConfirm = {
                if (skipConfirmThisSession) {
                    preferences.skipDeleteConfirmThisSession = true
                }
                onDeleteItems(ids)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingShortcutDelete?.let { id ->
        ConfirmDialog(
            title = "删除快捷入口",
            message = "确定要删除这个快捷入口吗？",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                onDeleteShortcut(id)
                pendingShortcutDelete = null
            },
            onDismiss = { pendingShortcutDelete = null },
        )
    }
}

@Composable
private fun QuickFilterRow(
    state: HomeUiState,
    onApplyShortcut: (com.riverxu.itemmanager.domain.model.FilterShortcut) -> Unit,
    onToggleExpiration: (com.riverxu.itemmanager.domain.model.ExpirationFilter) -> Unit,
    onToggleSize: (com.riverxu.itemmanager.domain.model.SizeCategory) -> Unit,
    onClearFilter: () -> Unit,
    onSaveShortcut: () -> Unit,
    onDeleteShortcut: (Long) -> Unit,
) {
    var expandedShortcut by remember { mutableStateOf<Long?>(null) }
    val hasShortcuts = state.shortcuts.isNotEmpty()
    val hasFilter = !state.filter.isEmpty
    if (!hasShortcuts && !hasFilter) return

    Surface(color = MaterialTheme.colorScheme.surface) {
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasFilter) {
                item {
                    androidx.compose.material3.AssistChip(
                        onClick = onClearFilter,
                        label = { Text("清除筛选") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
                item {
                    androidx.compose.material3.AssistChip(
                        onClick = onSaveShortcut,
                        label = { Text("保存为快捷入口") },
                    )
                }
            }

            state.shortcuts.forEach { shortcut ->
                item(key = "shortcut-${shortcut.id}") {
                    Box {
                        androidx.compose.material3.SuggestionChip(
                            onClick = { onApplyShortcut(shortcut) },
                            label = { Text(shortcut.name) },
                            icon = {
                                Icon(
                                    Icons.Outlined.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                        // 长按弹出菜单，用于删除快捷入口
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { expandedShortcut = shortcut.id },
                                ),
                        )
                        DropdownMenu(
                            expanded = expandedShortcut == shortcut.id,
                            onDismissRequest = { expandedShortcut = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text("应用") },
                                onClick = {
                                    expandedShortcut = null
                                    onApplyShortcut(shortcut)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    expandedShortcut = null
                                    onDeleteShortcut(shortcut.id)
                                },
                            )
                        }
                    }
                }
            }

            if (state.filter.expiration != null) {
                item {
                    com.riverxu.itemmanager.ui.components.MetaChip(
                        text = state.filter.expiration.label,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            state.filter.sizeCategory?.let { category ->
                item {
                    com.riverxu.itemmanager.ui.components.MetaChip(text = category.label)
                }
            }
            if (state.residences.size > 1) {
                state.filter.residenceId?.let { id ->
                    val name = state.residences.firstOrNull { it.id == id }?.name
                    if (name != null) {
                        item { com.riverxu.itemmanager.ui.components.MetaChip(text = name) }
                    }
                }
            }
            state.filter.locationId?.let { id ->
                val name = state.locations.firstOrNull { it.id == id }?.name
                if (name != null) {
                    item { com.riverxu.itemmanager.ui.components.MetaChip(text = name) }
                }
            }
            state.filter.typeName?.let { type ->
                item { com.riverxu.itemmanager.ui.components.MetaChip(text = type) }
            }
            if (state.filter.keyword.isNotBlank()) {
                item {
                    com.riverxu.itemmanager.ui.components.MetaChip(text = "“${state.filter.keyword}”")
                }
            }
        }
    }
}
