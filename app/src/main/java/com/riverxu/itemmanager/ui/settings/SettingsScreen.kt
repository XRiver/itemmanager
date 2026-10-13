package com.riverxu.itemmanager.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.riverxu.itemmanager.data.local.database.LocationEntity
import com.riverxu.itemmanager.data.local.database.ResidenceEntity
import com.riverxu.itemmanager.data.local.database.TypeEntity
import com.riverxu.itemmanager.data.local.photo.PhotoCompressor
import com.riverxu.itemmanager.data.local.prefs.PreferencesStore
import com.riverxu.itemmanager.ui.components.ConfirmDialog
import com.riverxu.itemmanager.ui.components.TextInputDialog
import com.riverxu.itemmanager.util.DateUtils

/**
 * 设置页面。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSetPhotoQuality: (PhotoCompressor.Quality) -> Unit,
    onSetThemeMode: (PreferencesStore.ThemeMode) -> Unit,
    onSetConfirmDelete: (Boolean) -> Unit,
    onOptimize: () -> Unit,
    onExport: (android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onSetActiveResidence: (Long) -> Unit,
    onAddResidence: (String) -> Unit,
    onRenameResidence: (Long, String) -> Unit,
    onDeleteResidence: (Long) -> Unit,
    onAddLocation: (Long, String) -> Unit,
    onRenameLocation: (Long, String) -> Unit,
    onDeleteLocation: (Long) -> Unit,
    onAddType: (String) -> Unit,
    onRenameType: (Long, String) -> Unit,
    onDeleteType: (Long) -> Unit,
    onConsumeMessage: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showResidenceManager by remember { mutableStateOf(false) }
    var showLocationManager by remember { mutableStateOf(false) }
    var showTypeManager by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // 导出：选择目标文件
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) onExport(uri)
    }

    // 导入：选择备份文件
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
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
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.testTag(com.riverxu.itemmanager.ui.TestTags.SETTINGS_LIST),
            ) {
                // ---------------- 照片 ----------------
                item { SettingsSectionTitle("照片") }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "每张照片最大容量",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        // 6 个档位在窄屏上一行放不下，用 FlowRow 自动换行
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            PhotoCompressor.Quality.entries.forEach { quality ->
                                FilterChip(
                                    selected = state.photoQuality == quality,
                                    onClick = { onSetPhotoQuality(quality) },
                                    label = { Text(quality.label) },
                                    leadingIcon = if (state.photoQuality == quality) {
                                        {
                                            Icon(
                                                Icons.Outlined.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "压缩方式：最长边限制 1920px，JPEG 质量由 90 递减（下限 30）；" +
                                "若仍超过上限会继续按 75% 逐级缩小尺寸，最小缩到 480px。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ---------------- 外观 ----------------
                item { SettingsSectionTitle("外观") }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(text = "主题", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreferencesStore.ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = state.themeMode == mode,
                                    onClick = { onSetThemeMode(mode) },
                                    label = { Text(mode.label) },
                                )
                            }
                        }
                    }
                }

                // ---------------- 交互 ----------------
                item { SettingsSectionTitle("交互") }
                item {
                    ListItem(
                        headlineContent = { Text("删除前二次确认") },
                        supportingContent = { Text("关闭后左滑或点击删除将直接生效") },
                        trailingContent = {
                            Switch(
                                checked = state.confirmDelete,
                                onCheckedChange = onSetConfirmDelete,
                            )
                        },
                    )
                }

                // ---------------- 数据 ----------------
                item { SettingsSectionTitle("数据") }
                item {
                    ListItem(
                        headlineContent = { Text("物品数量") },
                        supportingContent = { Text("${state.stats.itemCount} 件") },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("照片占用") },
                        supportingContent = {
                            Text("${state.stats.photoCount} 张 · ${DateUtils.formatBytes(state.stats.photoBytes)}")
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("导出数据") },
                        supportingContent = { Text("导出 SQLite 数据库与全部照片为 ZIP 备份包") },
                        leadingContent = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                        modifier = Modifier.clickable {
                            exportLauncher.launch("ItemManagerFree_backup_${DateUtils.backupTimestamp()}.zip")
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("导入数据") },
                        supportingContent = { Text("从 ZIP 备份包恢复，同名住处会自动改名") },
                        leadingContent = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                        modifier = Modifier.clickable {
                            importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("优化数据库") },
                        supportingContent = { Text("执行 VACUUM 压缩数据库体积") },
                        leadingContent = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
                        modifier = Modifier.clickable(enabled = !state.busy) { onOptimize() },
                    )
                }

                // ---------------- 管理 ----------------
                item { SettingsSectionTitle("管理") }
                item {
                    ListItem(
                        headlineContent = { Text("住处管理") },
                        supportingContent = { Text("共 ${state.stats.residenceCount} 个住处") },
                        leadingContent = { Icon(Icons.Outlined.Home, contentDescription = null) },
                        modifier = Modifier.clickable { showResidenceManager = true },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("位置管理") },
                        supportingContent = {
                            val current = state.residences.firstOrNull { it.id == state.activeResidenceId }
                            Text("${current?.name ?: "—"} · 共 ${state.locations.size} 个位置")
                        },
                        leadingContent = { Icon(Icons.Outlined.Place, contentDescription = null) },
                        modifier = Modifier.clickable { showLocationManager = true },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("类型管理") },
                        supportingContent = { Text("共 ${state.types.size} 个类型") },
                        leadingContent = { Icon(Icons.Outlined.Category, contentDescription = null) },
                        modifier = Modifier.clickable { showTypeManager = true },
                    )
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = "物品管家Free v0.1.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "包名 com.riverxu.itemmanager · 最低支持 Android 10",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showResidenceManager) {
        ResidenceManagerDialog(
            residences = state.residences,
            onAdd = onAddResidence,
            onRename = onRenameResidence,
            onDelete = onDeleteResidence,
            onDismiss = { showResidenceManager = false },
        )
    }

    if (showLocationManager) {
        LocationManagerDialog(
            residences = state.residences,
            activeResidenceId = state.activeResidenceId,
            locations = state.locations,
            onSetActiveResidence = onSetActiveResidence,
            onAdd = onAddLocation,
            onRename = onRenameLocation,
            onDelete = onDeleteLocation,
            onDismiss = { showLocationManager = false },
        )
    }

    if (showTypeManager) {
        TypeManagerDialog(
            types = state.types,
            onAdd = onAddType,
            onRename = onRenameType,
            onDelete = onDeleteType,
            onDismiss = { showTypeManager = false },
        )
    }

    if (showImportConfirm) {
        ConfirmDialog(
            title = "导入数据",
            message = "导入会覆盖当前全部数据（物品、位置、类型、住处）。建议先导出当前数据作为备份。确定继续吗？",
            confirmLabel = "导入并覆盖",
            destructive = true,
            onConfirm = {
                showImportConfirm = false
                pendingImportUri?.let(onImport)
                pendingImportUri = null
            },
            onDismiss = {
                showImportConfirm = false
                pendingImportUri = null
            },
        )
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

// ------------------------------------------------------------------ 住处管理

@Composable
private fun ResidenceManagerDialog(
    residences: List<ResidenceEntity>,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ResidenceEntity?>(null) }
    var deleting by remember { mutableStateOf<ResidenceEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("住处管理") },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                items(residences, key = { it.id }) { residence ->
                    ListItem(
                        headlineContent = { Text(residence.name) },
                        supportingContent = {
                            Text("创建于 ${DateUtils.formatDate(residence.createdAt)}")
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renaming = residence }) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = "改名",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { deleting = residence },
                                    enabled = residences.size > 1,
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showAdd = true }) { Text("添加住处") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )

    if (showAdd) {
        TextInputDialog(
            title = "添加住处",
            label = "住处名称",
            supportingText = "例如：北京的家",
            maxLength = com.riverxu.itemmanager.domain.model.FieldLimits.RESIDENCE_NAME_MAX,
            onConfirm = {
                onAdd(it)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    renaming?.let { residence ->
        TextInputDialog(
            title = "重命名住处",
            initialValue = residence.name,
            label = "住处名称",
            maxLength = com.riverxu.itemmanager.domain.model.FieldLimits.RESIDENCE_NAME_MAX,
            onConfirm = {
                onRename(residence.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { residence ->
        ConfirmDialog(
            title = "删除住处",
            message = "删除「${residence.name}」会一并删除其内部所有物品、位置数据，且不可恢复。确定继续吗？",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                onDelete(residence.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

// ------------------------------------------------------------------ 位置管理

@Composable
private fun LocationManagerDialog(
    residences: List<ResidenceEntity>,
    activeResidenceId: Long,
    locations: List<LocationEntity>,
    onSetActiveResidence: (Long) -> Unit,
    onAdd: (Long, String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LocationEntity?>(null) }
    var deleting by remember { mutableStateOf<LocationEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("位置管理") },
        text = {
            Column {
                if (residences.size > 1) {
                    Text(
                        text = "选择住处",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(modifier = Modifier.height(120.dp)) {
                        items(residences, key = { it.id }) { residence ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSetActiveResidence(residence.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = residence.id == activeResidenceId,
                                    onClick = { onSetActiveResidence(residence.id) },
                                )
                                Text(residence.name)
                            }
                        }
                    }
                    HorizontalDivider()
                }
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(locations, key = { it.id }) { location ->
                        ListItem(
                            headlineContent = { Text(location.name) },
                            supportingContent = {
                                Text(
                                    if (location.isPreset) {
                                        "预置位置（不可删除）"
                                    } else {
                                        "使用 ${location.usageCount} 次"
                                    },
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = { renaming = location },
                                        enabled = !location.isPreset,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "重命名",
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { deleting = location },
                                        enabled = !location.isPreset,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(18.dp),
                                            tint = if (location.isPreset) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            },
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { showAdd = true },
                enabled = activeResidenceId > 0,
            ) { Text("添加位置") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )

    if (showAdd) {
        TextInputDialog(
            title = "添加位置",
            label = "位置名称",
            supportingText = "例如：书桌抽屉",
            maxLength = com.riverxu.itemmanager.domain.model.FieldLimits.LOCATION_NAME_MAX,
            onConfirm = {
                onAdd(activeResidenceId, it)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    renaming?.let { location ->
        TextInputDialog(
            title = "重命名位置",
            initialValue = location.name,
            label = "位置名称",
            maxLength = com.riverxu.itemmanager.domain.model.FieldLimits.LOCATION_NAME_MAX,
            onConfirm = {
                onRename(location.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { location ->
        ConfirmDialog(
            title = "删除位置",
            message = "删除「${location.name}」后，使用该位置的物品会自动归入「未设置」。确定继续吗？",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                onDelete(location.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

// ------------------------------------------------------------------ 类型管理

@Composable
private fun TypeManagerDialog(
    types: List<TypeEntity>,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<TypeEntity?>(null) }
    var deleting by remember { mutableStateOf<TypeEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("类型管理") },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                items(types, key = { it.id }) { type ->
                    ListItem(
                        headlineContent = { Text(type.name) },
                        supportingContent = {
                            Text(
                                if (type.isPreset) {
                                    "预置类型（不可删除）"
                                } else {
                                    "使用 ${type.usageCount} 次"
                                },
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = { renaming = type },
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = "重命名",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { deleting = type },
                                    enabled = !type.isPreset,
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (type.isPreset) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showAdd = true }) { Text("添加类型") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )

    if (showAdd) {
        TextInputDialog(
            title = "添加类型",
            label = "类型名称",
            supportingText = "例如：电子产品",
            maxLength = com.riverxu.itemmanager.domain.model.FieldLimits.TYPE_NAME_MAX,
            onConfirm = {
                onAdd(it)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    renaming?.let { type ->
        TextInputDialog(
            title = "重命名类型",
            initialValue = type.name,
            label = "类型名称",
            maxLength = com.riverxu.itemmanager.domain.model.FieldLimits.TYPE_NAME_MAX,
            onConfirm = {
                onRename(type.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { type ->
        ConfirmDialog(
            title = "删除类型",
            message = "删除「${type.name}」后，使用该类型的物品类型将变为未设置。确定继续吗？",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                onDelete(type.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Suppress("unused")
private val settingsIcons = listOf(Icons.Outlined.Build, Icons.Outlined.Image, Icons.Outlined.Palette, Icons.Outlined.Add)
