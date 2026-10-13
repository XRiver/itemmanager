package com.riverxu.itemmanager.ui.move

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riverxu.itemmanager.data.model.MigrationTarget
import com.riverxu.itemmanager.ui.components.EmptyState
import com.riverxu.itemmanager.ui.components.MetaChip
import com.riverxu.itemmanager.ui.components.PhotoBox
import com.riverxu.itemmanager.ui.components.PickerBottomSheet

/**
 * 搬家模式界面。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoveScreen(
    state: MoveUiState,
    onFromResidenceChange: (Long) -> Unit,
    onToResidenceChange: (Long) -> Unit,
    onTargetChange: (MigrationTarget) -> Unit,
    onTargetLocationChange: (Long) -> Unit,
    onFilterChange: ((com.riverxu.itemmanager.domain.model.FilterSpec) -> com.riverxu.itemmanager.domain.model.FilterSpec) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMigrate: () -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var showTargetLocationPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onConsumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搬家模式") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    if (state.selectedIds.isNotEmpty()) {
                        TextButton(onClick = onClearSelection) { Text("取消选择") }
                    }
                    TextButton(onClick = onSelectAll, enabled = state.items.isNotEmpty()) { Text("全选") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.selectedIds.isNotEmpty()) {
                BottomMigrationBar(
                    state = state,
                    onTargetChange = onTargetChange,
                    onPickTargetLocation = { showTargetLocationPicker = true },
                    onMigrate = onMigrate,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ---------------- 住处选择 ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ResidenceBox(
                    label = "从",
                    name = state.residences.firstOrNull { it.id == state.fromResidenceId }?.name ?: "选择",
                    modifier = Modifier.weight(1f),
                    onClick = { showFromPicker = true },
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                ResidenceBox(
                    label = "到",
                    name = state.residences.firstOrNull { it.id == state.toResidenceId }?.name ?: "选择",
                    modifier = Modifier.weight(1f),
                    onClick = { showToPicker = true },
                )
            }

            if (state.residences.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "搬家模式需要至少两个住处，请先在设置中添加住处。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (state.toResidenceId == state.fromResidenceId && state.toResidenceId > 0) {
                Text(
                    text = "目标住处与当前住处相同，请重新选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // ---------------- 临时筛选 ----------------
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.filter.locationId == null,
                        onClick = { onFilterChange { it.copy(locationId = null) } },
                        label = { Text("全部位置") },
                    )
                }
                state.fromLocations.forEach { location ->
                    item(key = "loc-${location.id}") {
                        FilterChip(
                            selected = state.filter.locationId == location.id,
                            onClick = {
                                onFilterChange { current ->
                                    current.copy(
                                        locationId = if (current.locationId == location.id) null else location.id,
                                    )
                                }
                            },
                            label = { Text(location.name) },
                        )
                    }
                }
            }

            Text(
                text = "已选 ${state.selectedIds.size} / ${state.items.size} 件（默认按位置排序，可临时筛选）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            HorizontalDivider()

            if (state.items.isEmpty()) {
                EmptyState(
                    title = "该住处下没有物品",
                    description = "可切换住处或调整筛选条件",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = if (state.selectedIds.isEmpty()) 24.dp else 180.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        val selected = item.id in state.selectedIds
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onToggleSelection(item.id) },
                                    onLongClick = { onToggleSelection(item.id) },
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { onToggleSelection(item.id) },
                                )
                                PhotoBox(
                                    photoPath = item.photoPath,
                                    modifier = Modifier.size(44.dp),
                                    targetSize = 160,
                                    cornerRadius = 10.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.locationName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        item.type?.let {
                                            Spacer(Modifier.width(6.dp))
                                            MetaChip(text = it)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFromPicker) {
        PickerBottomSheet(
            title = "选择原住处",
            items = state.residences,
            key = { it.id },
            label = { it.name },
            selectedKey = state.fromResidenceId,
            onDismiss = { showFromPicker = false },
            onSelect = {
                onFromResidenceChange(it.id)
                showFromPicker = false
            },
        )
    }

    if (showToPicker) {
        PickerBottomSheet(
            title = "选择新住处",
            items = state.residences,
            key = { it.id },
            label = { it.name },
            selectedKey = state.toResidenceId,
            onDismiss = { showToPicker = false },
            onSelect = {
                onToResidenceChange(it.id)
                showToPicker = false
            },
        )
    }

    if (showTargetLocationPicker) {
        PickerBottomSheet(
            title = "选择新位置",
            items = state.toLocations,
            key = { it.id },
            label = { it.name },
            secondaryLabel = { if (it.isPreset) "预置" else null },
            selectedKey = state.targetLocationId,
            onDismiss = { showTargetLocationPicker = false },
            onSelect = {
                onTargetLocationChange(it.id)
                showTargetLocationPicker = false
            },
        )
    }
}

@Composable
private fun ResidenceBox(
    label: String,
    name: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 底部迁移方式选择栏。
 */
@Composable
private fun BottomMigrationBar(
    state: MoveUiState,
    onTargetChange: (MigrationTarget) -> Unit,
    onPickTargetLocation: () -> Unit,
    onMigrate: () -> Unit,
) {
    androidx.compose.material3.Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "迁移方式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))

            MigrationOption(
                selected = state.target is MigrationTarget.SpecificLocation,
                title = "指定新位置",
                subtitle = state.targetLocationId.takeIf { it > 0 }?.let { id ->
                    state.toLocations.firstOrNull { it.id == id }?.name
                } ?: "选择新住处的某个位置",
                onClick = onPickTargetLocation,
            )
            MigrationOption(
                selected = state.target == MigrationTarget.SameLocationName,
                title = "同位迁移",
                subtitle = "在新住处自动创建同名位置",
                onClick = { onTargetChange(MigrationTarget.SameLocationName) },
            )
            MigrationOption(
                selected = state.target == MigrationTarget.ToUnset,
                title = "灵活迁移",
                subtitle = "统一放入新住处的「未设置」",
                onClick = { onTargetChange(MigrationTarget.ToUnset) },
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onMigrate,
                enabled = state.canMigrate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("迁移 ${state.selectedIds.size} 件物品")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MigrationOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
