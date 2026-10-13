package com.riverxu.itemmanager.ui.item

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.riverxu.itemmanager.data.local.photo.PhotoCompressor
import com.riverxu.itemmanager.data.local.photo.PhotoStore
import com.riverxu.itemmanager.domain.model.FieldLimits
import com.riverxu.itemmanager.domain.model.SizeCategory
import com.riverxu.itemmanager.ui.LocalPhotoCompressor
import com.riverxu.itemmanager.ui.LocalPhotoStore
import com.riverxu.itemmanager.ui.TestTags
import com.riverxu.itemmanager.ui.components.AppDatePickerDialog
import com.riverxu.itemmanager.ui.components.ChoiceChips
import com.riverxu.itemmanager.ui.components.PhotoBox
import com.riverxu.itemmanager.ui.components.PickerBottomSheet
import com.riverxu.itemmanager.util.DateUtils
import java.io.File

/**
 * 添加 / 编辑物品表单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditorScreen(
    state: ItemEditorState,
    photoQuality: PhotoCompressor.Quality,
    onNameChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSizeCategoryChange: (SizeCategory) -> Unit,
    onTypeChange: (String?) -> Unit,
    onCreateType: (String) -> Unit,
    onResidenceChange: (Long) -> Unit,
    onLocationChange: (Long) -> Unit,
    onCreateLocation: (String) -> Unit,
    onPurchaseDateChange: (Long?, Boolean) -> Unit,
    onExpiryDateChange: (Long?) -> Unit,
    onPhotoPicked: (File) -> Unit,
    onPhotoRemoved: () -> Unit,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    onConsumeMessage: () -> Unit,
) {
    val context = LocalContext.current
    val photoStore = LocalPhotoStore.current
    val compressor = LocalPhotoCompressor.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showTypePicker by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showResidencePicker by remember { mutableStateOf(false) }
    var showPurchasePicker by remember { mutableStateOf(false) }
    var showExpiryPicker by remember { mutableStateOf(false) }

    var cameraTempFile by remember { mutableStateOf<File?>(null) }

    // 拍照：写入 FileProvider 提供的临时文件
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = cameraTempFile
        if (success && file != null && file.exists() && file.length() > 0) {
            onPhotoPicked(file)
        } else {
            file?.delete()
        }
        cameraTempFile = null
    }

    // 从相册选择
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            val copied = PhotoStore.copyToTemp(context, uri, "gallery_${System.currentTimeMillis()}.jpg")
            if (copied != null) onPhotoPicked(copied)
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateBack()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onConsumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "编辑物品" else "添加物品") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.Close, contentDescription = "取消")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = state.canSave,
                        modifier = Modifier.testTag(TestTags.ACTION_SAVE),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("保存", fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ---------------- 照片 ----------------
            val previewPath = state.pendingPhotoFile?.absolutePath ?: state.photoPath
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PhotoBox(
                    photoPath = previewPath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    targetSize = 768,
                    cornerRadius = 16.dp,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val file = runCatching {
                            val dir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
                            File(dir, "capture_${System.currentTimeMillis()}.jpg")
                        }.getOrNull()
                        if (file != null) {
                            cameraTempFile = file
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            takePictureLauncher.launch(uri)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("拍照")
                }
                OutlinedButton(
                    onClick = {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("相册")
                }
                if (previewPath != null) {
                    OutlinedButton(onClick = onPhotoRemoved) {
                        Icon(Icons.Outlined.Delete, contentDescription = "移除照片", modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(
                text = "保存照片时会自动压缩，每张不超过 ${photoQuality.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // ---------------- 名称 ----------------
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("名称 *") },
                placeholder = { Text("例如：螺丝刀套装") },
                singleLine = true,
                isError = state.name.isBlank() && state.name.isNotEmpty(),
                supportingText = {
                    val warning = if (state.sameNameCount > 0) "已有 ${state.sameNameCount} 个同名物品" else null
                    Text(
                        text = warning ?: "${state.name.length}/${FieldLimits.ITEM_NAME_MAX}",
                        color = if (warning != null) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.FIELD_ITEM_NAME),
            )

            // ---------------- 注释 ----------------
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text("注释") },
                placeholder = { Text("补充说明，可留空") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.FIELD_NOTE),
            )

            // ---------------- 大小分类 ----------------
            FieldLabel("大小分类 *")
            ChoiceChips(
                options = SizeCategory.entries,
                selected = state.sizeCategory,
                label = { it.label },
                onSelect = onSizeCategoryChange,
            )

            HorizontalDivider()

            // ---------------- 住处 ----------------
            if (state.residences.size > 1) {
                FieldLabel("住处 *")
                SelectorRow(
                    text = state.residences.firstOrNull { it.id == state.residenceId }?.name ?: "请选择住处",
                    onClick = { showResidencePicker = true },
                )
            }

            // ---------------- 位置 ----------------
            FieldLabel("位置 *")
            SelectorRow(
                text = state.locations.firstOrNull { it.id == state.locationId }?.name ?: "请选择位置",
                onClick = { showLocationPicker = true },
            )

            // ---------------- 类型 ----------------
            FieldLabel("类型")
            SelectorRow(
                text = state.typeName ?: "未设置",
                onClick = { showTypePicker = true },
                onClear = if (state.typeName != null) {
                    { onTypeChange(null) }
                } else {
                    null
                },
            )

            HorizontalDivider()

            // ---------------- 购入时间 ----------------
            FieldLabel("购入时间")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectorRow(
                    text = if (state.purchaseDateIsNever) {
                        "很久"
                    } else {
                        DateUtils.formatDate(state.purchaseDate)
                    },
                    onClick = { showPurchasePicker = true },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onPurchaseDateChange(null, !state.purchaseDateIsNever)
                    },
                ) {
                    Text(if (state.purchaseDateIsNever) "填写日期" else "设为很久")
                }
            }

            // ---------------- 过期日期 ----------------
            FieldLabel("过期日期")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectorRow(
                    text = state.expiryDate?.let { DateUtils.formatDate(it) } ?: "未设置",
                    onClick = { showExpiryPicker = true },
                    modifier = Modifier.weight(1f),
                )
                if (state.expiryDate != null) {
                    TextButton(onClick = { onExpiryDateChange(null) }) { Text("清除") }
                }
            }
            if (state.expiryDate != null) {
                Text(
                    text = DateUtils.expiryLabel(state.expiryDate).orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(if (state.isEditing) "保存修改" else "添加物品")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // ---------------- 选择面板 ----------------

    if (showResidencePicker) {
        PickerBottomSheet(
            title = "选择住处",
            items = state.residences,
            key = { it.id },
            label = { it.name },
            selectedKey = state.residenceId,
            onDismiss = { showResidencePicker = false },
            onSelect = {
                onResidenceChange(it.id)
                showResidencePicker = false
            },
        )
    }

    if (showLocationPicker) {
        PickerBottomSheet(
            title = "选择位置",
            items = state.locations,
            key = { it.id },
            label = { it.name },
            secondaryLabel = { if (it.isPreset) "预置" else null },
            selectedKey = state.locationId,
            allowCreate = true,
            createHint = "新建位置",
            onDismiss = { showLocationPicker = false },
            onSelect = {
                onLocationChange(it.id)
                showLocationPicker = false
            },
            onCreate = { name ->
                onCreateLocation(name)
                showLocationPicker = false
            },
        )
    }

    if (showTypePicker) {
        PickerBottomSheet(
            title = "选择类型",
            items = state.types,
            key = { it.id },
            label = { it.name },
            secondaryLabel = { if (it.isPreset) "预置" else null },
            selectedKey = state.types.firstOrNull { it.name == state.typeName }?.id,
            allowCreate = true,
            createHint = "新建类型",
            onDismiss = { showTypePicker = false },
            onSelect = {
                onTypeChange(it.name)
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
            initialMillis = state.purchaseDate,
            title = "选择购入日期",
            onConfirm = { millis ->
                onPurchaseDateChange(millis, millis == null)
                showPurchasePicker = false
            },
            onDismiss = { showPurchasePicker = false },
        )
    }

    if (showExpiryPicker) {
        AppDatePickerDialog(
            initialMillis = state.expiryDate,
            title = "选择过期日期",
            onConfirm = { millis ->
                onExpiryDateChange(millis)
                showExpiryPicker = false
            },
            onDismiss = { showExpiryPicker = false },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SelectorRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    androidx.compose.material3.OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (onClear != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "清除",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
