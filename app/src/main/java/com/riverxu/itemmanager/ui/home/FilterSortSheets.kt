package com.riverxu.itemmanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riverxu.itemmanager.domain.model.CompositeSort
import com.riverxu.itemmanager.domain.model.ExpirationFilter
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.SizeCategory
import com.riverxu.itemmanager.domain.model.SortField
import com.riverxu.itemmanager.ui.components.AppDatePickerDialog
import com.riverxu.itemmanager.ui.components.SelectableRow
import com.riverxu.itemmanager.ui.components.SortDirectionIcon
import com.riverxu.itemmanager.util.DateUtils

/**
 * 筛选面板：住处、位置、类型、大小分类、过期时间、购入时间区间。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onFilterChange: ((FilterSpec) -> FilterSpec) -> Unit,
    onClearFilter: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filter = state.filter
    val scroll = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(scroll)
                .padding(bottom = 28.dp),
        ) {
            SheetHeader(title = "筛选", onDismiss = onDismiss) {
                if (!filter.isEmpty) {
                    TextButton(onClick = onClearFilter) { Text("全部清除") }
                }
            }

            // ---------------- 住处 ----------------
            if (state.residences.size > 1) {
                SectionTitle("住处")
                SelectableRow(
                    text = "全部住处",
                    selected = filter.residenceId == null,
                    onClick = {
                        onFilterChange { it.copy(residenceId = null, locationId = null) }
                    },
                )
                state.residences.forEach { residence ->
                    SelectableRow(
                        text = residence.name,
                        selected = filter.residenceId == residence.id,
                        onClick = {
                            onFilterChange { current ->
                                if (current.residenceId == residence.id) {
                                    current.copy(residenceId = null, locationId = null)
                                } else {
                                    current.copy(residenceId = residence.id, locationId = null)
                                }
                            }
                        },
                    )
                }
                HorizontalDivider()
            }

            // ---------------- 位置 ----------------
            if (filter.residenceId != null && state.locations.isNotEmpty()) {
                SectionTitle("位置")
                FilterChipRow(
                    options = state.locations.map { it.id to it.name },
                    selectedKey = filter.locationId,
                    onSelect = { id ->
                        onFilterChange { current ->
                            current.copy(locationId = if (current.locationId == id) null else id)
                        }
                    },
                )
                HorizontalDivider()
            }

            // ---------------- 类型 ----------------
            if (state.types.isNotEmpty()) {
                SectionTitle("类型")
                FilterChipRow(
                    options = state.types.map { it.name to it.name },
                    selectedKey = filter.typeName,
                    onSelect = { name ->
                        onFilterChange { current ->
                            current.copy(typeName = if (current.typeName == name) null else name)
                        }
                    },
                )
                HorizontalDivider()
            }

            // ---------------- 标签（多选，符合任一） ----------------
            if (state.tags.isNotEmpty()) {
                SectionTitle("标签（符合任一）")
                FilterChipRow(
                    options = state.tags.map { it.name },
                    selectedKey = null,
                    selectedKeys = filter.tags,
                    onSelect = { name ->
                        onFilterChange { current ->
                            current.copy(
                                tags = if (name in current.tags) {
                                    current.tags - name
                                } else {
                                    current.tags + name
                                },
                            )
                        }
                    },
                )
                HorizontalDivider()
            }

            // ---------------- 大小分类 ----------------
            SectionTitle("大小分类")
            FilterChipRow(
                options = SizeCategory.entries.map { it.name to it.label },
                selectedKey = filter.sizeCategory?.name,
                onSelect = { name ->
                    val category = SizeCategory.fromNameOrNull(name)
                    onFilterChange { current ->
                        current.copy(sizeCategory = if (current.sizeCategory == category) null else category)
                    }
                },
            )
            HorizontalDivider()

            // ---------------- 过期时间 ----------------
            SectionTitle("过期时间")
            FilterChipRow(
                options = ExpirationFilter.entries
                    .filter { it != ExpirationFilter.ANY }
                    .map { it.name to it.label },
                selectedKey = filter.expiration?.name,
                onSelect = { name ->
                    val value = ExpirationFilter.entries.firstOrNull { it.name == name }
                    onFilterChange { current ->
                        current.copy(expiration = if (current.expiration == value) null else value)
                    }
                },
            )
            HorizontalDivider()

            // ---------------- 购入时间 ----------------
            SectionTitle("购入时间")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DateBoundChip(
                    label = "起始",
                    value = filter.purchaseFrom,
                    onPick = { millis ->
                        onFilterChange { it.copy(purchaseFrom = millis) }
                    },
                    modifier = Modifier.weight(1f),
                )
                DateBoundChip(
                    label = "结束",
                    value = filter.purchaseTo,
                    onPick = { millis ->
                        onFilterChange { it.copy(purchaseTo = millis) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val oneYearAgo = DateUtils.startOfToday() - 365L * 24 * 60 * 60 * 1000
                FilterChip(
                    selected = filter.purchaseFrom == oneYearAgo,
                    onClick = {
                        onFilterChange { it.copy(purchaseFrom = oneYearAgo, purchaseTo = null) }
                    },
                    label = { Text("近一年购入") },
                )
                FilterChip(
                    selected = filter.purchaseFrom == null && filter.purchaseTo == null,
                    onClick = {
                        onFilterChange { it.copy(purchaseFrom = null, purchaseTo = null) }
                    },
                    label = { Text("不限时间") },
                )
            }
        }
    }
}

@Composable
private fun DateBoundChip(
    label: String,
    value: Long?,
    onPick: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    FilterChip(
        selected = value != null,
        onClick = { showPicker = true },
        label = {
            Text(if (value != null) "$label：${DateUtils.formatDate(value)}" else "$label：不限")
        },
        modifier = modifier,
    )
    if (showPicker) {
        AppDatePickerDialog(
            initialMillis = value,
            title = "选择${label}日期",
            onConfirm = {
                onPick(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <K> FilterChipRow(
    options: List<Pair<K, String>>,
    selectedKey: K?,
    onSelect: (K) -> Unit,
    /** 多选模式：命中集合内的项即为选中；为 null 时退化为按 [selectedKey] 单选 */
    selectedKeys: Set<K>? = null,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val selected = selectedKeys?.contains(key) ?: (selectedKey == key)
            FilterChip(
                selected = selected,
                onClick = { onSelect(key) },
                label = { Text(label) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** 纯字符串选项的多选重载（标签筛选用）。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterChipRow(
    options: List<String>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    selectedKeys: Set<String>,
) {
    FilterChipRow<String>(
        options = options.map { it to it },
        selectedKey = selectedKey,
        onSelect = onSelect,
        selectedKeys = selectedKeys,
    )
}

/**
 * 排序面板：最多 3 个字段，可切换升/降序与空值位置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    state: HomeUiState,
    onDismiss: () -> Unit,
    onToggleSortField: (SortField) -> Unit,
    onSortChange: (CompositeSort) -> Unit,
    onResetSort: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sort = state.sort
    val scroll = rememberScrollState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(scroll)
                .padding(bottom = 28.dp),
        ) {
            SheetHeader(title = "排序", onDismiss = onDismiss) {
                TextButton(onClick = onResetSort) { Text("恢复默认") }
            }

            Text(
                text = "最多可同时按 ${CompositeSort.MAX_FIELDS} 个字段排序，序号即优先级",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            val orderedSpecs = sort.normalized().specs
            SortField.entries.forEach { field ->
                val spec = orderedSpecs.firstOrNull { it.field == field }
                val index = orderedSpecs.indexOfFirst { it.field == field }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectableRow(
                        text = field.label,
                        selected = spec != null,
                        trailing = if (index >= 0) "#${index + 1}" else null,
                        modifier = Modifier.weight(1f),
                        onClick = { onToggleSortField(field) },
                    )
                    if (spec != null) {
                        IconButton(
                            onClick = {
                                onSortChange(sort.toggled(field))
                            },
                        ) {
                            SortDirectionIcon(ascending = spec.ascending)
                        }
                        IconButton(
                            onClick = {
                                onSortChange(
                                    CompositeSort(orderedSpecs.filterNot { it.field == field }),
                                )
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "移除该排序字段",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (spec != null && (field == SortField.PURCHASE_DATE || field == SortField.EXPIRY_DATE || field == SortField.TYPE)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "空值",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilterChip(
                            selected = spec.nullsLast,
                            onClick = { onSortChange(sort.withSpec(spec.copy(nullsLast = true))) },
                            label = { Text("排最后") },
                        )
                        FilterChip(
                            selected = !spec.nullsLast,
                            onClick = { onSortChange(sort.withSpec(spec.copy(nullsLast = false))) },
                            label = { Text("排最前") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SheetHeader(
    title: String,
    onDismiss: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
        IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭")
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}
