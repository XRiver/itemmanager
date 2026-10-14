package com.riverxu.itemmanager.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riverxu.itemmanager.data.local.database.LocationEntity
import com.riverxu.itemmanager.data.local.database.ResidenceEntity
import com.riverxu.itemmanager.data.local.database.TypeEntity
import com.riverxu.itemmanager.domain.model.SizeCategory
import com.riverxu.itemmanager.ui.components.AppDatePickerDialog
import com.riverxu.itemmanager.ui.components.ChoiceChips
import com.riverxu.itemmanager.ui.components.ConfirmDialog
import com.riverxu.itemmanager.ui.components.DetailRow
import com.riverxu.itemmanager.ui.components.ExpiryChip
import com.riverxu.itemmanager.ui.components.MultiSelectPickerSheet
import com.riverxu.itemmanager.ui.components.PhotoBox
import com.riverxu.itemmanager.ui.components.PickerBottomSheet
import com.riverxu.itemmanager.ui.components.TextInputDialog
import com.riverxu.itemmanager.util.DateUtils

/**
 * 物品详情底部抽屉。
 *
 * 每个字段可点击复制，右侧编辑按钮进入就地编辑；底部提供删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailSheet(
    state: ItemDetailState,
    residences: List<ResidenceEntity>,
    locations: List<LocationEntity>,
    types: List<TypeEntity>,
    availableTags: List<String>,
    onUpdateName: (String) -> Unit,
    onUpdateNote: (String?) -> Unit,
    onUpdateSizeCategory: (SizeCategory) -> Unit,
    onUpdateType: (String?) -> Unit,
    onUpdateTags: (List<String>) -> Unit,
    onUpdateLocation: (Long) -> Unit,
    onUpdateResidence: (Long) -> Unit,
    onUpdatePurchaseDate: (Long?) -> Unit,
    onUpdateExpiryDate: (Long?) -> Unit,
    onCreateLocation: (String) -> Unit,
    onCreateType: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenFullEditor: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    var editingField by remember { mutableStateOf<DetailField?>(null) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showResidencePicker by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showExpiryPicker by remember { mutableStateOf(false) }
    var showPurchasePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSizeEditor by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val detail = state.detail
        if (detail == null || state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@ModalBottomSheet
        }

        val item = detail.item

        // 外层不滚动：字段区可滚动 + 底部操作栏固定。
        // 这样字段再多（0.2.0 加了标签与两个系统时间）也不会把「完整编辑 / 删除」挤出屏幕。
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
            PhotoBox(
                photoPath = item.photoPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 16.dp),
                targetSize = 1024,
                cornerRadius = 16.dp,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { editingField = DetailField.NAME }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑名称", modifier = Modifier.size(20.dp))
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                HorizontalDivider()

                DetailRow(
                    label = "注释",
                    value = item.note.orEmpty().ifBlank { "无" },
                    onCopy = item.note?.let { note -> { clipboard.setText(AnnotatedString(note)) } },
                    onEdit = { editingField = DetailField.NOTE },
                )

                DetailRow(
                    label = "大小分类",
                    value = item.sizeCategory.label,
                    onEdit = { showSizeEditor = true },
                )

                DetailRow(
                    label = "类型",
                    value = item.type ?: "未设置",
                    onCopy = item.type?.let { type -> { clipboard.setText(AnnotatedString(type)) } },
                    onEdit = { showTypePicker = true },
                )

                // 标签：多选，点右侧编辑按钮进入多选面板
                Column {
                    DetailRow(
                        label = "标签",
                        value = if (detail.tags.isEmpty()) "无" else detail.tags.joinToString("、"),
                        onCopy = detail.tags.takeIf { it.isNotEmpty() }?.let { tags ->
                            { clipboard.setText(AnnotatedString(tags.joinToString("、"))) }
                        },
                        onEdit = { showTagPicker = true },
                    )
                    if (detail.tags.isNotEmpty()) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            detail.tags.take(4).forEach { tag ->
                                com.riverxu.itemmanager.ui.components.MetaChip(text = tag)
                            }
                            if (detail.tags.size > 4) {
                                com.riverxu.itemmanager.ui.components.MetaChip(
                                    text = "+${detail.tags.size - 4}",
                                )
                            }
                        }
                    }
                }

                DetailRow(
                    label = "购入时间",
                    value = DateUtils.formatPurchaseDate(item.purchaseDate),
                    onEdit = { showPurchasePicker = true },
                )

                Column {
                    DetailRow(
                        label = "过期日期",
                        value = item.expiryDate?.let { DateUtils.formatDate(it) } ?: "未设置",
                        onEdit = { showExpiryPicker = true },
                    )
                    if (item.expiryDate != null) {
                        ExpiryChip(
                            expiryMillis = item.expiryDate,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }

                DetailRow(
                    label = "位置",
                    value = detail.locationName,
                    onCopy = { clipboard.setText(AnnotatedString(detail.locationName)) },
                    onEdit = { showLocationPicker = true },
                )

                DetailRow(
                    label = "住处",
                    value = detail.residenceName,
                    onCopy = { clipboard.setText(AnnotatedString(detail.residenceName)) },
                    onEdit = {
                        if (residences.size > 1) showResidencePicker = true
                    },
                )

                Text(
                    text = "提示：点击字段内容可复制到剪贴板",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // 系统自动维护的只读字段
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                DetailRow(
                    label = "录入时间",
                    value = DateUtils.formatDateTime(item.createdAt),
                    onCopy = {
                        clipboard.setText(AnnotatedString(DateUtils.formatDateTime(item.createdAt)))
                    },
                )
                DetailRow(
                    label = "最近更新位置",
                    value = DateUtils.formatDateTime(item.locationUpdatedAt),
                    onCopy = {
                        clipboard.setText(
                            AnnotatedString(DateUtils.formatDateTime(item.locationUpdatedAt)),
                        )
                    },
                )
                Text(
                    text = "「录入时间」与「最近更新位置」由系统自动记录，不可手动修改",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                }
            }

            // ---------------- 固定底部操作栏（不随字段区滚动） ----------------
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onOpenFullEditor(item.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("完整编辑")
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // ---------------- 就地编辑对话框 ----------------

    when (editingField) {
        DetailField.NAME -> {
            val current = state.detail?.item?.name.orEmpty()
            TextInputDialog(
                title = "修改名称",
                initialValue = current,
                label = "物品名称",
                maxLength = com.riverxu.itemmanager.domain.model.FieldLimits.ITEM_NAME_MAX,
                onConfirm = {
                    onUpdateName(it)
                    editingField = null
                },
                onDismiss = { editingField = null },
            )
        }

        DetailField.NOTE -> {
            val current = state.detail?.item?.note.orEmpty()
            TextInputDialog(
                title = "修改注释",
                initialValue = current,
                label = "注释",
                maxLength = com.riverxu.itemmanager.domain.model.FieldLimits.NOTE_MAX,
                onConfirm = {
                    onUpdateNote(it)
                    editingField = null
                },
                onDismiss = { editingField = null },
            )
        }

        null -> Unit
    }

    if (showSizeEditor) {
        AlertDialog(
            onDismissRequest = { showSizeEditor = false },
            title = { Text("大小分类") },
            text = {
                ChoiceChips(
                    options = SizeCategory.entries,
                    selected = state.detail?.item?.sizeCategory,
                    label = { it.label },
                    onSelect = {
                        onUpdateSizeCategory(it)
                        showSizeEditor = false
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showSizeEditor = false }) { Text("关闭") }
            },
        )
    }

    if (showLocationPicker) {
        PickerBottomSheet(
            title = "选择位置",
            items = locations,
            key = { it.id },
            label = { it.name },
            secondaryLabel = { if (it.isPreset) "预置" else null },
            selectedKey = state.detail?.item?.locationId,
            allowCreate = true,
            createHint = "新建位置",
            onDismiss = { showLocationPicker = false },
            onSelect = {
                onUpdateLocation(it.id)
                showLocationPicker = false
            },
            onCreate = { name ->
                onCreateLocation(name)
                showLocationPicker = false
            },
        )
    }

    if (showResidencePicker) {
        PickerBottomSheet(
            title = "选择住处",
            items = residences,
            key = { it.id },
            label = { it.name },
            secondaryLabel = { "迁移后位置变为「未设置」" },
            selectedKey = state.detail?.item?.residenceId,
            onDismiss = { showResidencePicker = false },
            onSelect = {
                onUpdateResidence(it.id)
                showResidencePicker = false
            },
        )
    }

    if (showTagPicker) {
        val currentTags = state.detail?.tags.orEmpty()
        MultiSelectPickerSheet(
            title = "选择标签",
            items = (availableTags + currentTags).distinct().sorted(),
            key = { it },
            label = { it },
            selectedKeys = currentTags.toSet(),
            allowCreate = true,
            createHint = "新建标签",
            onDismiss = { showTagPicker = false },
            onToggle = { name ->
                val next = if (name in currentTags) currentTags - name else currentTags + name
                onUpdateTags(next)
            },
            onCreate = { name ->
                onCreateTag(name)
                onUpdateTags((currentTags + name).distinct())
            },
        )
    }

    if (showTypePicker) {
        PickerBottomSheet(
            title = "选择类型",
            items = types,
            key = { it.id },
            label = { it.name },
            secondaryLabel = { if (it.isPreset) "预置" else null },
            selectedKey = types.firstOrNull { it.name == state.detail?.item?.type }?.id,
            allowCreate = true,
            createHint = "新建类型",
            onDismiss = { showTypePicker = false },
            onSelect = {
                onUpdateType(it.name)
                showTypePicker = false
            },
            onCreate = { name ->
                onCreateType(name)
                showTypePicker = false
            },
        )
    }

    if (showPurchasePicker) {
        AppDatePickerDialog(
            initialMillis = state.detail?.item?.purchaseDate,
            title = "选择购入日期",
            onConfirm = {
                onUpdatePurchaseDate(it)
                showPurchasePicker = false
            },
            onDismiss = { showPurchasePicker = false },
        )
    }

    if (showExpiryPicker) {
        AppDatePickerDialog(
            initialMillis = state.detail?.item?.expiryDate,
            title = "选择过期日期",
            onConfirm = {
                onUpdateExpiryDate(it)
                showExpiryPicker = false
            },
            onDismiss = { showExpiryPicker = false },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "删除物品",
            message = "确定要删除「${state.detail?.item?.name.orEmpty()}」吗？此操作不可恢复。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

private enum class DetailField { NAME, NOTE }
