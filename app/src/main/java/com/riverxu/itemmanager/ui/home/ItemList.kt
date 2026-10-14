package com.riverxu.itemmanager.ui.home

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riverxu.itemmanager.data.local.database.ItemListRow
import com.riverxu.itemmanager.ui.components.ExpiryChip
import com.riverxu.itemmanager.ui.components.MetaChip
import com.riverxu.itemmanager.ui.components.PhotoBox
import com.riverxu.itemmanager.ui.components.SizeCategoryChip

/**
 * 物品列表（列表视图）：支持左滑删除与长按多选。
 *
 * [confirmDelete] 关闭时左滑立即删除，否则弹出确认对话框。
 */
@Composable
fun ItemList(
    items: List<ItemListRow>,
    selectedIds: Set<Long>,
    selectionMode: Boolean,
    confirmDelete: Boolean,
    onClick: (ItemListRow) -> Unit,
    onLongClick: (Long) -> Unit,
    onDeleteRequest: (Long) -> Unit,
    onDeleteImmediately: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 4.dp,
            bottom = 108.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            SwipeableItemRow(
                item = item,
                selected = item.id in selectedIds,
                selectionMode = selectionMode,
                confirmDelete = confirmDelete,
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item.id) },
                onDeleteRequest = { onDeleteRequest(item.id) },
                onDeleteImmediately = { onDeleteImmediately(item.id) },
            )
        }
    }
}

@Composable
private fun SwipeableItemRow(
    item: ItemListRow,
    selected: Boolean,
    selectionMode: Boolean,
    confirmDelete: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteImmediately: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                if (confirmDelete) {
                    onDeleteRequest()
                    false // 交由确认对话框决定，本行不自行消失
                } else {
                    onDeleteImmediately()
                    true
                }
            } else {
                false
            }
        },
    )

    // 删除确认完成后重置滑动状态
    LaunchedEffect(item.id) { dismissState.reset() }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        ItemRowCard(
            item = item,
            selected = selected,
            selectionMode = selectionMode,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRowCard(
    item: ItemListRow,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            // 必须使用不透明颜色：卡片下面是左滑删除的红色背景，
            // 半透明会让垃圾桶图标一直透出来
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            PhotoBox(
                photoPath = item.photoPath,
                modifier = Modifier.size(56.dp),
                targetSize = 192,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    SizeCategoryChip(item.sizeCategory)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = item.locationName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.type?.let {
                        Spacer(Modifier.width(6.dp))
                        MetaChip(text = it)
                    }
                }
                if (item.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 空间有限，最多显示 2 个标签，其余折叠成 +N
                        item.tags.take(2).forEach { tag ->
                            TagChip(text = tag)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (item.tags.size > 2) {
                            MetaChip(text = "+${item.tags.size - 2}")
                        }
                    }
                }
                if (item.expiryDate != null) {
                    Spacer(Modifier.height(4.dp))
                    ExpiryChip(expiryMillis = item.expiryDate)
                }
            }
        }
    }
}

/** 标签胶囊：与大小分类/类型用的 MetaChip 区分开，用主题色描边感更强 */
@Composable
private fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "#$text",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
        )
    }
}

/**
 * 物品网格视图。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemGrid(
    items: List<ItemListRow>,
    selectedIds: Set<Long>,
    selectionMode: Boolean,
    onClick: (ItemListRow) -> Unit,
    onLongClick: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 108.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            val selected = item.id in selectedIds
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onClick(item) },
                        onLongClick = { onLongClick(item.id) },
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column {
                    Box {
                        PhotoBox(
                            photoPath = item.photoPath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            targetSize = 384,
                            cornerRadius = 14.dp,
                        )
                        if (selectionMode) {
                            androidx.compose.material3.Checkbox(
                                checked = selected,
                                onCheckedChange = { onClick(item) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = item.locationName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            SizeCategoryChip(item.sizeCategory)
                        }
                        if (item.tags.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TagChip(text = item.tags.first())
                                if (item.tags.size > 1) {
                                    Spacer(Modifier.width(4.dp))
                                    MetaChip(text = "+${item.tags.size - 1}")
                                }
                            }
                        }
                        if (item.expiryDate != null) {
                            Spacer(Modifier.height(6.dp))
                            ExpiryChip(expiryMillis = item.expiryDate)
                        }
                    }
                }
            }
        }
    }
}
