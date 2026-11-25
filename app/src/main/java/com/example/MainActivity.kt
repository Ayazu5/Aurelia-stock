package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.auth.signOut
import com.example.data.firebase.configureAppCheck
import com.example.data.model.ToastTone
import com.example.data.model.UiState
import com.example.data.preferences.UserPreferencesRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.components.ExecutiveToastHost
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.drawer.ProfileSettingsDrawer
import com.example.ui.modals.EditItemDialog
import com.example.ui.modals.HistoryLogbookSheet
import com.example.ui.scan.ScanVisionSheet
import com.example.ui.theme.AureliaStockTheme
import com.example.viewmodel.InventoryViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent
        // BEFORE any Compose UI, ViewModel, or Firebase operations.
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge display
        enableEdgeToEdge()

        setContent {
            AureliaRootApp(
                onSyncSystemBars = { isDark ->
                    enableEdgeToEdge(
                        statusBarStyle = if (isDark) {
                            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT
                            )
                        },
                        navigationBarStyle = if (isDark) {
                            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT
                            )
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun AureliaRootApp(
    onSyncSystemBars: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember(context) { UserPreferencesRepository(context.applicationContext) }
    val globalDarkMode by prefsRepo.globalDarkMode.collectAsStateWithLifecycle(initialValue = true)

    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        SideEffect { onSyncSystemBars(globalDarkMode) }
        AureliaStockTheme(darkTheme = globalDarkMode) {
            AuthScreen(
                darkMode = globalDarkMode,
                onToggleDarkMode = {
                    scope.launch { prefsRepo.setGlobalDarkMode(!globalDarkMode) }
                },
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                }
            )
        }
    } else {
        // Key by UID so switching Google accounts cleanly resets ViewModel & Firestore listeners
        key(user.uid) {
            AuthenticatedWorkspace(
                user = user,
                onSyncSystemBars = onSyncSystemBars
            )
        }
    }
}

@Composable
private fun AuthenticatedWorkspace(
    user: FirebaseUser,
    onSyncSystemBars: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: InventoryViewModel = viewModel(
        key = user.uid,
        factory = InventoryViewModel.Factory(user.uid, context)
    )

    val inventoryState by viewModel.inventoryState.collectAsStateWithLifecycle()
    val historyState by viewModel.historyState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val offlineQueue by viewModel.offlineQueue.collectAsStateWithLifecycle()
    val isEffectivelyOffline by viewModel.isEffectivelyOffline.collectAsStateWithLifecycle()
    val isSyncingQueue by viewModel.isSyncingQueue.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val editingItem by viewModel.editingItem.collectAsStateWithLifecycle()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsStateWithLifecycle()
    val isHistoryOpen by viewModel.isHistoryOpen.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()

    SideEffect { onSyncSystemBars(preferences.darkMode) }

    AureliaStockTheme(
        darkTheme = preferences.darkMode,
        uiScalePercent = preferences.uiScalePercent
    ) {
        val inventoryItems = (inventoryState as? UiState.Success)?.data.orEmpty()
        val historyCount = (historyState as? UiState.Success)?.data?.size ?: 0

        Box(modifier = Modifier.fillMaxSize()) {
            DashboardScreen(
                userDisplayName = user.displayName.orEmpty(),
                userEmail = user.email.orEmpty(),
                inventoryState = inventoryState,
                preferences = preferences,
                isEffectivelyOffline = isEffectivelyOffline,
                offlineQueueCount = offlineQueue.size,
                isSyncingQueue = isSyncingQueue,
                searchQuery = searchQuery,
                activeFilter = activeFilter,
                onSearchQueryChange = viewModel::setSearchQuery,
                onFilterSelect = viewModel::setFilter,
                onToggleDensity = viewModel::toggleViewDensity,
                onOpenDrawer = { viewModel.setDrawerOpen(true) },
                onOpenHistory = { viewModel.setHistoryOpen(true) },
                onOpenScan = viewModel::openScanSheet,
                onQuickAdjust = viewModel::quickAdjustItem,
                onEditItem = viewModel::openEditItem
            )

            // Mirrored Slide-Over Profile & Ergonomic Settings Drawer
            ProfileSettingsDrawer(
                isOpen = isDrawerOpen,
                uid = user.uid,
                displayName = user.displayName.orEmpty(),
                email = user.email.orEmpty(),
                preferences = preferences,
                historyCount = historyCount,
                onDismiss = { viewModel.setDrawerOpen(false) },
                onHandSideChange = viewModel::setHandSide,
                onDarkModeChange = viewModel::setDarkMode,
                onUiScaleChange = viewModel::setUiScale,
                onPhotoQualityChange = viewModel::setPhotoQuality,
                onSimulatedOfflineChange = viewModel::setSimulatedOffline,
                onOpenHistory = { viewModel.setHistoryOpen(true) },
                onSignOut = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )

            // Stock IN / Stock OUT Vision AI Modal Sheet
            ScanVisionSheet(
                state = scanState,
                inventoryItems = inventoryItems,
                isEffectivelyOffline = isEffectivelyOffline,
                onDismiss = viewModel::closeScanSheet,
                onSetCameraActive = viewModel::setCameraActive,
                onPhotoCaptured = viewModel::onPhotoCapturedFromCamera,
                onPhotoPicked = viewModel::onPhotoPickedFromGallery,
                onRetake = viewModel::resetCapturedPhoto,
                onCameraError = { msg -> viewModel.showToast(msg, ToastTone.ERROR) },
                onSelectCandidate = viewModel::selectDisambiguationCandidate,
                onConfirmUseSelectedCandidate = viewModel::confirmDisambiguationUseExisting,
                onConfirmCreateNewSku = viewModel::confirmDisambiguationCreateNew,
                onSelectManualOutItem = viewModel::selectManualItemForStockOut,
                onUpdateForm = viewModel::updateScanForm,
                onSubmit = viewModel::submitScanTransaction
            )

            // Direct Variant Edit & Delete Modal
            EditItemDialog(
                item = editingItem,
                onDismiss = viewModel::closeEditItem,
                onSave = viewModel::saveEditedItem,
                onDelete = viewModel::deleteItem
            )

            // Immutable Stock History Logbook Sheet
            HistoryLogbookSheet(
                isOpen = isHistoryOpen,
                historyState = historyState,
                onDismiss = { viewModel.setHistoryOpen(false) }
            )

            // Top Executive Toast Notification Overlay
            ExecutiveToastHost(toast = toast)
        }
    }
}
