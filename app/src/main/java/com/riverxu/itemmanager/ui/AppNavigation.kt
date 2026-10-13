package com.riverxu.itemmanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.riverxu.itemmanager.data.local.database.LocationEntity
import com.riverxu.itemmanager.data.local.database.ResidenceEntity
import com.riverxu.itemmanager.data.local.database.TypeEntity
import com.riverxu.itemmanager.data.local.prefs.PreferencesStore
import com.riverxu.itemmanager.di.AppContainer
import com.riverxu.itemmanager.ui.home.HomeScreen
import com.riverxu.itemmanager.ui.home.HomeViewModel
import com.riverxu.itemmanager.ui.item.ItemDetailSheet
import com.riverxu.itemmanager.ui.item.ItemDetailViewModel
import com.riverxu.itemmanager.ui.item.ItemEditorScreen
import com.riverxu.itemmanager.ui.item.ItemEditorViewModel
import com.riverxu.itemmanager.ui.move.MoveScreen
import com.riverxu.itemmanager.ui.move.MoveViewModel
import com.riverxu.itemmanager.ui.settings.SettingsScreen
import com.riverxu.itemmanager.ui.settings.SettingsViewModel
import com.riverxu.itemmanager.ui.theme.OpenItemManagerTheme
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val EDITOR = "editor"
    const val MOVE = "move"
    const val SETTINGS = "settings"

    fun editor(itemId: Long = 0L): String = if (itemId > 0) "$EDITOR?itemId=$itemId" else EDITOR
}

/**
 * 应用根组件：提供依赖、主题与导航。
 */
@Composable
fun OpenItemManagerApp(container: AppContainer) {
    val preferences = container.preferences
    val themeMode by preferences.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        PreferencesStore.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        PreferencesStore.ThemeMode.LIGHT -> false
        PreferencesStore.ThemeMode.DARK -> true
    }

    OpenItemManagerTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(
            LocalItemRepository provides container.itemRepository,
            LocalPhotoStore provides container.photoStore,
            LocalPhotoCompressor provides container.photoCompressor,
            LocalPreferences provides preferences,
            LocalExportManager provides container.exportManager,
        ) {
            AppNavHost(container)
        }
    }
}

@Composable
private fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val repository = container.itemRepository
    val preferences = container.preferences
    val photoStore = container.photoStore

    // 详情底部抽屉以浮层形式呈现，避免打断列表状态
    var detailItemId by rememberSaveable { mutableLongStateOf(0L) }

    val photoQuality by preferences.photoQuality.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        // ---------------- 首页 ----------------
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(repository, preferences),
            )
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()

            HomeScreen(
                state = state,
                onKeywordChange = homeViewModel::onKeywordChange,
                onFilterChange = homeViewModel::onFilterChange,
                onClearFilter = homeViewModel::clearFilter,
                onToggleExpiration = homeViewModel::toggleExpirationFilter,
                onToggleSize = homeViewModel::toggleSizeCategory,
                onToggleSortField = homeViewModel::toggleSortField,
                onSortChange = homeViewModel::onSortChange,
                onResetSort = homeViewModel::resetSort,
                onToggleLayout = homeViewModel::toggleLayoutMode,
                onApplyShortcut = homeViewModel::applyShortcut,
                onSaveShortcut = homeViewModel::saveShortcut,
                onDeleteShortcut = homeViewModel::deleteShortcut,
                onToggleSelection = homeViewModel::toggleSelection,
                onToggleSelectAll = homeViewModel::toggleSelectAll,
                onClearSelection = homeViewModel::clearSelection,
                onDeleteItems = homeViewModel::deleteItems,
                onItemClick = { detailItemId = it.id },
                onAddItem = { navController.navigate(Routes.editor()) },
                onOpenMoveMode = { navController.navigate(Routes.MOVE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onConsumeMessage = homeViewModel::consumeMessage,
            )
        }

        // ---------------- 添加 / 编辑物品 ----------------
        composable(
            route = "${Routes.EDITOR}?itemId={itemId}",
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
            ),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0L
            val editorViewModel: ItemEditorViewModel = viewModel(
                key = "editor-$itemId",
                factory = ItemEditorViewModel.factory(repository, preferences, photoStore, itemId),
            )
            val state by editorViewModel.state.collectAsStateWithLifecycle()

            ItemEditorScreen(
                state = state,
                photoQuality = photoQuality,
                onNameChange = editorViewModel::onNameChange,
                onNoteChange = editorViewModel::onNoteChange,
                onSizeCategoryChange = editorViewModel::onSizeCategoryChange,
                onTypeChange = editorViewModel::onTypeChange,
                onCreateType = editorViewModel::onCreateType,
                onResidenceChange = editorViewModel::onResidenceChange,
                onLocationChange = editorViewModel::onLocationChange,
                onCreateLocation = editorViewModel::onCreateLocation,
                onPurchaseDateChange = editorViewModel::onPurchaseDateChange,
                onExpiryDateChange = editorViewModel::onExpiryDateChange,
                onPhotoPicked = editorViewModel::onPhotoPicked,
                onPhotoRemoved = editorViewModel::onPhotoRemoved,
                onSave = { editorViewModel.save(photoQuality.maxBytes) },
                onNavigateBack = { navController.popBackStack() },
                onConsumeMessage = editorViewModel::consumeMessage,
            )
        }

        // ---------------- 搬家模式 ----------------
        composable(Routes.MOVE) {
            val moveViewModel: MoveViewModel = viewModel(
                factory = MoveViewModel.factory(repository),
            )
            val state by moveViewModel.uiState.collectAsStateWithLifecycle()

            MoveScreen(
                state = state,
                onFromResidenceChange = moveViewModel::onFromResidenceChange,
                onToResidenceChange = moveViewModel::onToResidenceChange,
                onTargetChange = moveViewModel::onTargetChange,
                onTargetLocationChange = moveViewModel::onTargetLocationChange,
                onFilterChange = moveViewModel::onFilterChange,
                onToggleSelection = moveViewModel::toggleSelection,
                onSelectAll = moveViewModel::selectAll,
                onClearSelection = moveViewModel::clearSelection,
                onMigrate = moveViewModel::migrate,
                onConsumeMessage = moveViewModel::consumeMessage,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // ---------------- 设置 ----------------
        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(
                    repository = repository,
                    preferences = preferences,
                    photoStore = photoStore,
                    exportManager = container.exportManager,
                ),
            )
            val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current

            SettingsScreen(
                state = state,
                onSetPhotoQuality = settingsViewModel::setPhotoQuality,
                onSetThemeMode = settingsViewModel::setThemeMode,
                onSetConfirmDelete = settingsViewModel::setConfirmDelete,
                onOptimize = settingsViewModel::optimizeDatabase,
                onExport = { uri -> settingsViewModel.exportTo(context, uri) },
                onImport = { uri -> settingsViewModel.importFrom(context, uri) },
                onSetActiveResidence = settingsViewModel::setActiveResidence,
                onAddResidence = settingsViewModel::addResidence,
                onRenameResidence = settingsViewModel::renameResidence,
                onDeleteResidence = settingsViewModel::deleteResidence,
                onAddLocation = settingsViewModel::addLocation,
                onRenameLocation = settingsViewModel::renameLocation,
                onDeleteLocation = settingsViewModel::deleteLocation,
                onAddType = settingsViewModel::addType,
                onRenameType = settingsViewModel::renameType,
                onDeleteType = settingsViewModel::deleteType,
                onConsumeMessage = settingsViewModel::consumeMessage,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }

    // ---------------- 物品详情浮层 ----------------
    if (detailItemId > 0) {
        DetailLayer(
            container = container,
            itemId = detailItemId,
            onOpenFullEditor = { id ->
                detailItemId = 0L
                navController.navigate(Routes.editor(id))
            },
            onDismiss = { detailItemId = 0L },
        )
    }
}

/**
 * 物品详情浮层：独立获取所需的下拉数据并绑定 ViewModel。
 */
@Composable
private fun DetailLayer(
    container: AppContainer,
    itemId: Long,
    onOpenFullEditor: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val repository = container.itemRepository
    val detailViewModel: ItemDetailViewModel = viewModel(
        key = "detail-$itemId",
        factory = ItemDetailViewModel.factory(repository, itemId),
    )
    val state by detailViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var residences by remember { mutableStateOf<List<ResidenceEntity>>(emptyList()) }
    var locations by remember { mutableStateOf<List<LocationEntity>>(emptyList()) }
    var types by remember { mutableStateOf<List<TypeEntity>>(emptyList()) }

    LaunchedEffect(state.detail?.item?.residenceId) {
        residences = repository.getResidences()
        types = repository.getTypes()
        val residenceId = state.detail?.item?.residenceId
        locations = if (residenceId != null && residenceId > 0) {
            repository.getLocations(residenceId)
        } else {
            emptyList()
        }
    }

    ItemDetailSheet(
        state = state,
        residences = residences,
        locations = locations,
        types = types,
        onUpdateName = detailViewModel::updateName,
        onUpdateNote = detailViewModel::updateNote,
        onUpdateSizeCategory = detailViewModel::updateSizeCategory,
        onUpdateType = detailViewModel::updateType,
        onUpdateLocation = detailViewModel::updateLocation,
        onUpdateResidence = detailViewModel::updateResidence,
        onUpdatePurchaseDate = detailViewModel::updatePurchaseDate,
        onUpdateExpiryDate = detailViewModel::updateExpiryDate,
        onCreateLocation = { name ->
            val residenceId = state.detail?.item?.residenceId
            if (residenceId != null && residenceId > 0) {
                scope.launch {
                    val id = repository.addLocation(residenceId, name)
                    locations = repository.getLocations(residenceId)
                    detailViewModel.updateLocation(id)
                }
            }
        },
        onCreateType = { name ->
            scope.launch {
                repository.addType(name)
                types = repository.getTypes()
                detailViewModel.updateType(name)
            }
        },
        onDelete = detailViewModel::delete,
        onOpenFullEditor = onOpenFullEditor,
        onDismiss = onDismiss,
    )
}
