package com.riverxu.itemmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 通用底部选择面板：用于位置、类型等选项。
 *
 * 支持关键词过滤与"快速新建"，切换住处时列表随住处变化。
 */
/**
 * 多选底部面板：用于「标签」这类一件物品可同时拥有多个的字段。
 *
 * 与 [PickerBottomSheet] 的区别：每行是复选框、点击切换选中状态，面板不因选择而关闭，
 * 用户点右上角关闭或返回键退出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MultiSelectPickerSheet(
    title: String,
    items: List<T>,
    key: (T) -> String,
    label: (T) -> String,
    secondaryLabel: (T) -> String? = { null },
    selectedKeys: Set<String>,
    allowCreate: Boolean = false,
    createHint: String = "新建",
    emptyHint: String = "暂无选项",
    onDismiss: () -> Unit,
    onToggle: (T) -> Unit,
    onCreate: ((String) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val filtered = remember(items, query) {
        if (query.isBlank()) items else items.filter { label(it).contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "已选 ${selectedKeys.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }
            }

            if (items.size > 8) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("搜索…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (filtered.isEmpty() && !creating) {
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(filtered, key = { key(it) }) { item ->
                    val checked = key(item) in selectedKeys
                    ListItem(
                        headlineContent = { Text(label(item)) },
                        supportingContent = secondaryLabel(item)?.let { { Text(it) } },
                        leadingContent = {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggle(item) },
                            )
                        },
                        colors = if (checked) {
                            ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            )
                        } else {
                            ListItemDefaults.colors()
                        },
                        modifier = Modifier.clickable { onToggle(item) },
                    )
                }
            }

            if (allowCreate && onCreate != null) {
                HorizontalDivider()
                if (creating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(createHint) },
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            enabled = newName.isNotBlank(),
                            onClick = {
                                onCreate(newName.trim())
                                newName = ""
                                creating = false
                            },
                        ) { Text("创建") }
                    }
                } else {
                    ListItem(
                        headlineContent = { Text(createHint) },
                        leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        modifier = Modifier.clickable { creating = true },
                    )
                }
            }
        }
    }
}

/**
 * 标签展示与入口：已选标签以可删除的小胶囊列出，右侧按钮打开多选面板。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSelectionField(
    selected: Set<String>,
    available: List<String>,
    onOpenPicker: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (selected.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                selected.sorted().forEach { tag ->
                    InputChip(
                        selected = true,
                        enabled = enabled,
                        onClick = { onRemove(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "移除标签 $tag",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onOpenPicker,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (selected.isEmpty()) {
                    "选择标签"
                } else {
                    "选择标签（已选 ${selected.size}，可选 ${available.size} 个）"
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PickerBottomSheet(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    label: (T) -> String,
    secondaryLabel: (T) -> String? = { null },
    selectedKey: Any?,
    allowCreate: Boolean = false,
    createHint: String = "新建",
    emptyHint: String = "暂无选项",
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    onCreate: ((String) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val filtered = remember(items, query) {
        if (query.isBlank()) items else items.filter { label(it).contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }
            }

            if (items.size > 8) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("搜索…") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (filtered.isEmpty()) {
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                items(filtered, key = { key(it).toString() }) { item ->
                    val selected = selectedKey != null && key(item).toString() == selectedKey.toString()
                    ListItem(
                        headlineContent = { Text(label(item)) },
                        supportingContent = secondaryLabel(item)?.let { { Text(it) } },
                        leadingContent = if (selected) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        colors = if (selected) {
                            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        } else {
                            ListItemDefaults.colors()
                        },
                        modifier = Modifier.clickable { onSelect(item) },
                    )
                }
            }

            if (allowCreate && onCreate != null) {
                HorizontalDivider()
                if (creating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(createHint) },
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            enabled = newName.isNotBlank(),
                            onClick = {
                                onCreate(newName.trim())
                                newName = ""
                                creating = false
                            },
                        ) { Text("创建") }
                    }
                } else {
                    ListItem(
                        headlineContent = { Text(createHint) },
                        leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        modifier = Modifier.clickable { creating = true },
                    )
                }
            }
        }
    }
}

/** 单选分组（大小分类等少量互斥选项）。 */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 日期选择对话框，null 表示清除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initialMillis: Long?,
    title: String,
    allowClear: Boolean = true,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(state.selectedDateMillis)
                },
            ) { Text("确定") }
        },
        dismissButton = {
            Row {
                if (allowClear) {
                    TextButton(onClick = { onConfirm(null) }) { Text("清除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    ) {
        DatePicker(state = state)
    }
}

/** 简单文本输入对话框（新建/重命名位置、类型、住处）。 */
@Composable
fun TextInputDialog(
    title: String,
    initialValue: String = "",
    label: String,
    supportingText: String? = null,
    confirmLabel: String = "确定",
    maxLength: Int = 50,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    val tooLong = value.length > maxLength
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = true,
                    isError = tooLong,
                    modifier = Modifier.fillMaxWidth(),
                )
                val hint = when {
                    tooLong -> "最多 $maxLength 个字符"
                    supportingText != null -> supportingText
                    else -> null
                }
                if (hint != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tooLong) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank() && !tooLong,
                onClick = { onConfirm(value.trim()) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 带颜色点的可选中行（用于筛选面板中的住所/位置列表）。 */
@Composable
fun SelectableRow(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (accent != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
