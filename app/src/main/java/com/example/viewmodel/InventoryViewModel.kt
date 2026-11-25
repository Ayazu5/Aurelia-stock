package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiVisionAnalysis
import com.example.data.model.HandSide
import com.example.data.model.ImageFingerprint
import com.example.data.model.InventoryItem
import com.example.data.model.PhotoQuality
import com.example.data.model.QueuedOfflineOp
import com.example.data.model.ScanMode
import com.example.data.model.StockFilter
import com.example.data.model.StockHistoryEntry
import com.example.data.model.ToastMessage
import com.example.data.model.ToastTone
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.data.model.ViewDensity
import com.example.data.network.NetworkMonitor
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.InventoryRepository
import com.example.data.vision.GeminiVisionRepository
import com.example.data.vision.ImageProcessor
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "InventoryViewModel"

data class ScanSheetState(
    val isOpen: Boolean = false,
    val mode: ScanMode = ScanMode.IN,
    val isCameraActive: Boolean = false,
    val capturedBitmap: Bitmap? = null,
    val thumbnailBase64: String? = null,
    val fingerprint: ImageFingerprint? = null,
    val isAnalyzing: Boolean = false,
    val aiError: String? = null,
    val analysis: AiVisionAnalysis? = null,
    val showDisambiguation: Boolean = false,
    val selectedCandidateId: String? = null,
    val matchedItemId: String? = null,
    val isSubmitting: Boolean = false,
    // Editable SKU form fields
    val name: String = "",
    val extractedText: String = "",
    val designDetails: String = "",
    val category: String = "General",
    val quantity: Long = 1L,
    val threshold: Long = 5L
)

class InventoryViewModel(
    private val uid: String,
    private val appContext: Context,
    private val inventoryRepository: InventoryRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val visionRepository: GeminiVisionRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    // Real-time Firestore inventory with two-tier CancellationException unwrapping
    val inventoryState: StateFlow<UiState<List<InventoryItem>>> =
        inventoryRepository.observeInventory(uid)
            .map<List<InventoryItem>, UiState<List<InventoryItem>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(UiState.Error(rootError.message ?: "Unable to sync inventory from cloud."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = UiState.Loading
            )

    // Real-time Firestore immutable history logbook
    val historyState: StateFlow<UiState<List<StockHistoryEntry>>> =
        inventoryRepository.observeHistory(uid)
            .map<List<StockHistoryEntry>, UiState<List<StockHistoryEntry>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(UiState.Error(rootError.message ?: "Unable to load stock audit history."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = UiState.Loading
            )

    val preferences: StateFlow<UserPreferences> =
        preferencesRepository.observePreferences(uid)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = UserPreferences()
            )

    val offlineQueue: StateFlow<List<QueuedOfflineOp>> =
        preferencesRepository.observeOfflineQueue(uid)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList()
            )

    val isDeviceOnline: StateFlow<Boolean> =
        networkMonitor.isOnline
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = networkMonitor.isCurrentlyOnline()
            )

    val isEffectivelyOffline: StateFlow<Boolean> =
        combine(isDeviceOnline, preferences) { online, prefs ->
            !online || prefs.simulatedOffline
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow<StockFilter>(StockFilter.All)
    val activeFilter: StateFlow<StockFilter> = _activeFilter.asStateFlow()

    private val _scanState = MutableStateFlow(ScanSheetState())
    val scanState: StateFlow<ScanSheetState> = _scanState.asStateFlow()

    private val _editingItem = MutableStateFlow<InventoryItem?>(null)
    val editingItem: StateFlow<InventoryItem?> = _editingItem.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    private val _isHistoryOpen = MutableStateFlow(false)
    val isHistoryOpen: StateFlow<Boolean> = _isHistoryOpen.asStateFlow()

    private val _isSyncingQueue = MutableStateFlow(false)
    val isSyncingQueue: StateFlow<Boolean> = _isSyncingQueue.asStateFlow()

    private val _toast = MutableStateFlow<ToastMessage?>(null)
    val toast: StateFlow<ToastMessage?> = _toast.asStateFlow()

    private var toastJob: Job? = null
    private var visionJob: Job? = null

    init {
        // Automatically flush queued offline operations whenever connectivity returns
        viewModelScope.launch {
            combine(isEffectivelyOffline, offlineQueue) { offline, queue ->
                Pair(offline, queue)
            }.collect { (offline, queue) ->
                if (!offline && queue.isNotEmpty() && !_isSyncingQueue.value) {
                    flushOfflineQueue(queue)
                }
            }
        }
    }

    fun showToast(text: String, tone: ToastTone = ToastTone.INFO) {
        toastJob?.cancel()
        _toast.value = ToastMessage(text = text, tone = tone)
        toastJob = viewModelScope.launch {
            delay(3_200L)
            _toast.value = null
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _toast.value = null
    }

    // --- Dashboard filters & overlays ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: StockFilter) {
        _activeFilter.value = filter
    }

    fun setDrawerOpen(open: Boolean) {
        _isDrawerOpen.value = open
    }

    fun setHistoryOpen(open: Boolean) {
        _isHistoryOpen.value = open
    }

    fun openEditItem(item: InventoryItem) {
        _editingItem.value = item
    }

    fun closeEditItem() {
        _editingItem.value = null
    }

    // --- Ergonomic & theme preferences ---
    fun setHandSide(handSide: HandSide) {
        viewModelScope.launch {
            preferencesRepository.setHandSide(uid, handSide)
            showToast(
                if (handSide == HandSide.LEFT) "Switched to Left-Handed ergonomics"
                else "Switched to Right-Handed ergonomics",
                ToastTone.INFO
            )
        }
    }

    fun setDarkMode(dark: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDarkMode(uid, dark)
        }
    }

    fun setUiScale(scalePercent: Int) {
        viewModelScope.launch {
            preferencesRepository.setUiScale(uid, scalePercent)
            showToast("UI resolution scale set to $scalePercent%", ToastTone.INFO)
        }
    }

    fun setPhotoQuality(quality: PhotoQuality) {
        viewModelScope.launch {
            preferencesRepository.setPhotoQuality(uid, quality)
            showToast("Photo capture quality set to ${quality.label}", ToastTone.INFO)
        }
    }

    fun toggleViewDensity() {
        val next = if (preferences.value.viewDensity == ViewDensity.LIST) {
            ViewDensity.GRID
        } else {
            ViewDensity.LIST
        }
        viewModelScope.launch {
            preferencesRepository.setViewDensity(uid, next)
        }
    }

    fun setSimulatedOffline(offline: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSimulatedOffline(uid, offline)
            if (offline) {
                showToast("Simulated Offline Mode active — AI paused, local queue enabled", ToastTone.WARNING)
            } else {
                showToast("Back online — Gemini Vision AI ready", ToastTone.SUCCESS)
            }
        }
    }

    // --- Stock IN / Stock OUT Vision AI Flow ---
    fun openScanSheet(mode: ScanMode) {
        visionJob?.cancel()
        _scanState.value = ScanSheetState(
            isOpen = true,
            mode = mode,
            quantity = 1L,
            threshold = 5L
        )
    }

    fun closeScanSheet() {
        visionJob?.cancel()
        _scanState.value = ScanSheetState(isOpen = false)
    }

    fun setCameraActive(active: Boolean) {
        _scanState.update { it.copy(isCameraActive = active) }
    }

    fun resetCapturedPhoto() {
        visionJob?.cancel()
        _scanState.update {
            it.copy(
                capturedBitmap = null,
                thumbnailBase64 = null,
                fingerprint = null,
                isAnalyzing = false,
                aiError = null,
                analysis = null,
                showDisambiguation = false,
                selectedCandidateId = null
            )
        }
    }

    fun onPhotoPickedFromGallery(uri: Uri) {
        val quality = preferences.value.photoQuality
        viewModelScope.launch {
            _scanState.update { it.copy(isCameraActive = false, isAnalyzing = true, aiError = null) }
            val processed = withContext(Dispatchers.IO) {
                ImageProcessor.decodeAndProcessUri(appContext, uri, quality)
            }
            if (processed == null) {
                _scanState.update { it.copy(isAnalyzing = false, aiError = "Could not read selected image.") }
                showToast("Could not decode the selected photo", ToastTone.ERROR)
                return@launch
            }
            handleProcessedPhoto(processed)
        }
    }

    fun onPhotoCapturedFromCamera(bitmap: Bitmap) {
        val quality = preferences.value.photoQuality
        viewModelScope.launch {
            _scanState.update { it.copy(isCameraActive = false, isAnalyzing = true, aiError = null) }
            val processed = withContext(Dispatchers.IO) {
                ImageProcessor.processBitmap(bitmap, quality)
            }
            handleProcessedPhoto(processed)
        }
    }

    private fun handleProcessedPhoto(processed: ImageProcessor.ProcessedPhoto) {
        val currentItems = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val offline = isEffectivelyOffline.value
        val mode = _scanState.value.mode

        _scanState.update {
            it.copy(
                capturedBitmap = processed.aiBitmap,
                thumbnailBase64 = processed.thumbnailBase64,
                fingerprint = processed.fingerprint,
                isAnalyzing = !offline,
                aiError = null,
                analysis = null,
                showDisambiguation = false,
                selectedCandidateId = null
            )
        }

        if (offline) {
            // Offline mode: use local perceptual fingerprint matching only, pause Gemini
            val localMatches = ImageProcessor.findLocalCandidates(processed.fingerprint, currentItems, 0.72)
            if (localMatches.isNotEmpty()) {
                val top = localMatches.first()
                _scanState.update {
                    it.copy(
                        analysis = AiVisionAnalysis(
                            suggestedName = it.name,
                            extractedText = it.extractedText,
                            designDetails = it.designDetails,
                            category = it.category,
                            suggestedThreshold = it.threshold.toInt(),
                            confidence = top.similarity,
                            confidenceNote = "Offline perceptual match found — confirm SKU below.",
                            exactMatchId = null,
                            ambiguous = true,
                            similarCandidates = localMatches
                        ),
                        showDisambiguation = true,
                        selectedCandidateId = top.id
                    )
                }
                showToast("Offline · Visual fingerprint matched saved SKU", ToastTone.WARNING)
            } else {
                showToast("Offline · AI paused. Fill fields manually to queue.", ToastTone.WARNING)
            }
            return
        }

        visionJob?.cancel()
        visionJob = viewModelScope.launch {
            val result = visionRepository.analyzeProductPhoto(
                bitmap = processed.aiBitmap,
                fingerprint = processed.fingerprint,
                existingItems = currentItems,
                mode = mode
            )
            result.onSuccess { analysis ->
                applyAiAnalysis(analysis, currentItems)
            }.onFailure { err ->
                Log.e(TAG, "Vision AI error", err)
                _scanState.update {
                    it.copy(
                        isAnalyzing = false,
                        aiError = err.message ?: "Vision AI failed. You can edit fields manually."
                    )
                }
                showToast(err.message ?: "Vision AI error", ToastTone.ERROR)
            }
        }
    }

    private fun applyAiAnalysis(analysis: AiVisionAnalysis, items: List<InventoryItem>) {
        val exactItem = analysis.exactMatchId?.let { id -> items.firstOrNull { it.id == id } }
        val needsConfirm = analysis.ambiguous && analysis.similarCandidates.isNotEmpty()

        _scanState.update { state ->
            when {
                exactItem != null -> state.copy(
                    isAnalyzing = false,
                    analysis = analysis,
                    showDisambiguation = false,
                    matchedItemId = exactItem.id,
                    name = exactItem.name,
                    extractedText = analysis.extractedText.ifBlank { exactItem.extractedText },
                    designDetails = analysis.designDetails.ifBlank { exactItem.designDetails },
                    category = exactItem.category,
                    threshold = exactItem.threshold
                )
                needsConfirm -> state.copy(
                    isAnalyzing = false,
                    analysis = analysis,
                    showDisambiguation = true,
                    selectedCandidateId = analysis.similarCandidates.firstOrNull()?.id,
                    matchedItemId = null,
                    name = analysis.suggestedName,
                    extractedText = analysis.extractedText,
                    designDetails = analysis.designDetails,
                    category = analysis.category,
                    threshold = analysis.suggestedThreshold.toLong()
                )
                else -> state.copy(
                    isAnalyzing = false,
                    analysis = analysis,
                    showDisambiguation = false,
                    matchedItemId = null,
                    name = analysis.suggestedName,
                    extractedText = analysis.extractedText,
                    designDetails = analysis.designDetails,
                    category = analysis.category,
                    threshold = analysis.suggestedThreshold.toLong()
                )
            }
        }
    }

    fun selectDisambiguationCandidate(candidateId: String) {
        _scanState.update { it.copy(selectedCandidateId = candidateId) }
    }

    fun confirmDisambiguationUseExisting() {
        val candidateId = _scanState.value.selectedCandidateId ?: return
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val chosen = items.firstOrNull { it.id == candidateId } ?: return
        _scanState.update { state ->
            state.copy(
                showDisambiguation = false,
                matchedItemId = chosen.id,
                name = chosen.name,
                extractedText = chosen.extractedText.ifBlank { state.extractedText },
                designDetails = chosen.designDetails.ifBlank { state.designDetails },
                category = chosen.category,
                threshold = chosen.threshold
            )
        }
        showToast("Linked to saved variant: ${chosen.name}", ToastTone.SUCCESS)
    }

    fun confirmDisambiguationCreateNew() {
        _scanState.update { state ->
            state.copy(
                showDisambiguation = false,
                matchedItemId = null,
                selectedCandidateId = null
            )
        }
        showToast("Listing as distinct new SKU variant", ToastTone.INFO)
    }

    fun selectManualItemForStockOut(item: InventoryItem) {
        _scanState.update { state ->
            state.copy(
                matchedItemId = item.id,
                showDisambiguation = false,
                name = item.name,
                extractedText = item.extractedText,
                designDetails = item.designDetails,
                category = item.category,
                threshold = item.threshold,
                thumbnailBase64 = state.thumbnailBase64 ?: item.thumbnailBase64
            )
        }
    }

    fun updateScanForm(
        name: String? = null,
        extractedText: String? = null,
        designDetails: String? = null,
        category: String? = null,
        quantity: Long? = null,
        threshold: Long? = null
    ) {
        _scanState.update { state ->
            state.copy(
                name = name ?: state.name,
                extractedText = extractedText ?: state.extractedText,
                designDetails = designDetails ?: state.designDetails,
                category = category ?: state.category,
                quantity = (quantity ?: state.quantity).coerceIn(1L, 10_000_000L),
                threshold = (threshold ?: state.threshold).coerceIn(0L, 1_000_000L)
            )
        }
    }

    fun submitScanTransaction() {
        val state = _scanState.value
        val cleanName = state.name.trim()
        if (cleanName.isEmpty()) {
            showToast("Please enter a variant or product name", ToastTone.WARNING)
            return
        }
        val qty = state.quantity.coerceAtLeast(1L)
        val currentItems = (inventoryState.value as? UiState.Success)?.data.orEmpty()
        val matched = state.matchedItemId?.let { id -> currentItems.firstOrNull { it.id == id } }

        if (state.mode == ScanMode.OUT && matched == null) {
            showToast("Select an existing inventory variant to Stock OUT", ToastTone.WARNING)
            return
        }

        if (state.mode == ScanMode.OUT && matched != null && qty > matched.quantity) {
            showToast("Only ${matched.quantity} unit(s) available in stock", ToastTone.WARNING)
            return
        }

        // If offline (real or simulated), queue locally in DataStore for automatic sync
        if (isEffectivelyOffline.value) {
            viewModelScope.launch {
                val queuedOp = QueuedOfflineOp(
                    opId = UUID.randomUUID().toString(),
                    mode = state.mode,
                    targetItemId = matched?.id,
                    name = cleanName,
                    extractedText = state.extractedText.trim(),
                    designDetails = state.designDetails.trim(),
                    category = state.category.trim().ifBlank { "General" },
                    quantityDelta = qty,
                    threshold = state.threshold,
                    thumbnailBase64 = state.thumbnailBase64,
                    fpHash = state.fingerprint?.hash,
                    fpAvgRgb = state.fingerprint?.avgRgb,
                    fpRadial = state.fingerprint?.radial,
                    fpEdgeDensity = state.fingerprint?.edgeDensity,
                    queuedAtMillis = System.currentTimeMillis()
                )
                preferencesRepository.enqueueOfflineOp(uid, queuedOp)
                closeScanSheet()
                showToast("Saved to Offline Queue — will auto-sync when online", ToastTone.WARNING)
            }
            return
        }

        viewModelScope.launch {
            _scanState.update { it.copy(isSubmitting = true) }
            val result = if (state.mode == ScanMode.IN) {
                if (matched != null) {
                    val newQty = (matched.quantity + qty).coerceAtMost(10_000_000L)
                    inventoryRepository.updateInventoryItem(
                        itemId = matched.id,
                        name = cleanName,
                        extractedText = state.extractedText.trim(),
                        designDetails = state.designDetails.trim(),
                        category = state.category.trim().ifBlank { matched.category },
                        newQuantity = newQty,
                        threshold = state.threshold,
                        thumbnailBase64 = state.thumbnailBase64 ?: matched.thumbnailBase64,
                        fingerprint = state.fingerprint ?: matched.toImageFingerprint(),
                        historyType = "IN",
                        historyDelta = qty
                    )
                } else {
                    inventoryRepository.createInventoryItem(
                        name = cleanName,
                        extractedText = state.extractedText.trim(),
                        designDetails = state.designDetails.trim(),
                        category = state.category.trim().ifBlank { "General" },
                        quantity = qty,
                        threshold = state.threshold,
                        thumbnailBase64 = state.thumbnailBase64,
                        fingerprint = state.fingerprint
                    ).map { Unit }
                }
            } else {
                val target = matched!!
                val newQty = (target.quantity - qty).coerceAtLeast(0L)
                inventoryRepository.updateInventoryItem(
                    itemId = target.id,
                    name = target.name,
                    extractedText = target.extractedText,
                    designDetails = target.designDetails,
                    category = target.category,
                    newQuantity = newQty,
                    threshold = target.threshold,
                    thumbnailBase64 = target.thumbnailBase64,
                    fingerprint = target.toImageFingerprint(),
                    historyType = "OUT",
                    historyDelta = qty
                )
            }

            result.onSuccess {
                closeScanSheet()
                val verb = if (state.mode == ScanMode.IN) "+$qty Stock IN" else "-$qty Stock OUT"
                showToast("$verb logged for $cleanName", ToastTone.SUCCESS)
            }.onFailure { err ->
                _scanState.update { it.copy(isSubmitting = false) }
                showToast(err.message ?: "Failed to save transaction", ToastTone.ERROR)
            }
        }
    }

    // --- Inline quick +/- & direct Edit / Delete ---
    fun quickAdjustItem(item: InventoryItem, delta: Long) {
        if (delta < 0L && item.quantity <= 0L) {
            showToast("${item.name} is already at 0 units", ToastTone.WARNING)
            return
        }
        if (isEffectivelyOffline.value) {
            viewModelScope.launch {
                val op = QueuedOfflineOp(
                    opId = UUID.randomUUID().toString(),
                    mode = if (delta > 0) ScanMode.IN else ScanMode.OUT,
                    targetItemId = item.id,
                    name = item.name,
                    extractedText = item.extractedText,
                    designDetails = item.designDetails,
                    category = item.category,
                    quantityDelta = kotlin.math.abs(delta),
                    threshold = item.threshold,
                    thumbnailBase64 = item.thumbnailBase64,
                    fpHash = item.fpHash,
                    fpAvgRgb = item.fpAvgRgb,
                    fpRadial = item.fpRadial,
                    fpEdgeDensity = item.fpEdgeDensity,
                    queuedAtMillis = System.currentTimeMillis()
                )
                preferencesRepository.enqueueOfflineOp(uid, op)
                showToast("Queued ${if (delta > 0) "+1" else "-1"} for ${item.name} (Offline)", ToastTone.WARNING)
            }
            return
        }

        viewModelScope.launch {
            inventoryRepository.quickAdjustQuantity(item, delta)
                .onSuccess { nextQty ->
                    val label = if (delta > 0) "+$delta" else "$delta"
                    showToast("${item.name}: $label → $nextQty units", ToastTone.SUCCESS)
                }
                .onFailure { err ->
                    showToast(err.message ?: "Could not adjust stock", ToastTone.ERROR)
                }
        }
    }

    fun saveEditedItem(
        item: InventoryItem,
        name: String,
        extractedText: String,
        designDetails: String,
        category: String,
        quantity: Long,
        threshold: Long
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            showToast("Variant name cannot be blank", ToastTone.WARNING)
            return
        }
        val diff = quantity - item.quantity
        val historyType = when {
            diff > 0L -> "IN"
            diff < 0L -> "OUT"
            else -> null
        }

        viewModelScope.launch {
            inventoryRepository.updateInventoryItem(
                itemId = item.id,
                name = cleanName,
                extractedText = extractedText.trim(),
                designDetails = designDetails.trim(),
                category = category.trim().ifBlank { "General" },
                newQuantity = quantity.coerceAtLeast(0L),
                threshold = threshold.coerceAtLeast(0L),
                thumbnailBase64 = item.thumbnailBase64,
                fingerprint = item.toImageFingerprint(),
                historyType = historyType,
                historyDelta = kotlin.math.abs(diff)
            ).onSuccess {
                closeEditItem()
                showToast("Updated $cleanName", ToastTone.SUCCESS)
            }.onFailure { err ->
                showToast(err.message ?: "Failed to update variant", ToastTone.ERROR)
            }
        }
    }

    fun deleteItem(item: InventoryItem) {
        viewModelScope.launch {
            inventoryRepository.deleteInventoryItem(item.id)
                .onSuccess {
                    closeEditItem()
                    showToast("Deleted ${item.name}", ToastTone.INFO)
                }
                .onFailure { err ->
                    showToast(err.message ?: "Failed to delete variant", ToastTone.ERROR)
                }
        }
    }

    // --- Offline Queue Auto-Flush ---
    private suspend fun flushOfflineQueue(queue: List<QueuedOfflineOp>) {
        _isSyncingQueue.value = true
        val syncedIds = mutableSetOf<String>()
        val items = (inventoryState.value as? UiState.Success)?.data.orEmpty().associateBy { it.id }

        try {
            for (op in queue) {
                val fp = if (op.fpHash != null && op.fpAvgRgb != null && op.fpRadial != null && op.fpEdgeDensity != null) {
                    ImageFingerprint(op.fpHash, op.fpAvgRgb, op.fpRadial, op.fpEdgeDensity)
                } else null

                val res = if (op.targetItemId != null && items.containsKey(op.targetItemId)) {
                    val target = items[op.targetItemId]!!
                    val nextQty = if (op.mode == ScanMode.IN) {
                        (target.quantity + op.quantityDelta).coerceAtMost(10_000_000L)
                    } else {
                        (target.quantity - op.quantityDelta).coerceAtLeast(0L)
                    }
                    inventoryRepository.updateInventoryItem(
                        itemId = target.id,
                        name = op.name.ifBlank { target.name },
                        extractedText = op.extractedText.ifBlank { target.extractedText },
                        designDetails = op.designDetails.ifBlank { target.designDetails },
                        category = op.category.ifBlank { target.category },
                        newQuantity = nextQty,
                        threshold = op.threshold,
                        thumbnailBase64 = op.thumbnailBase64 ?: target.thumbnailBase64,
                        fingerprint = fp ?: target.toImageFingerprint(),
                        historyType = op.mode.name,
                        historyDelta = op.quantityDelta,
                        offlineQueued = true
                    )
                } else if (op.mode == ScanMode.IN) {
                    inventoryRepository.createInventoryItem(
                        name = op.name,
                        extractedText = op.extractedText,
                        designDetails = op.designDetails,
                        category = op.category,
                        quantity = op.quantityDelta,
                        threshold = op.threshold,
                        thumbnailBase64 = op.thumbnailBase64,
                        fingerprint = fp,
                        offlineQueued = true
                    ).map { Unit }
                } else {
                    // Target item no longer exists for Stock OUT; drop gracefully
                    Result.success(Unit)
                }

                if (res.isSuccess) {
                    syncedIds.add(op.opId)
                }
            }

            if (syncedIds.isNotEmpty()) {
                preferencesRepository.removeOfflineOps(uid, syncedIds)
                showToast("Synced ${syncedIds.size} queued offline update(s) to cloud", ToastTone.SUCCESS)
            }
        } finally {
            _isSyncingQueue.value = false
        }
    }

    class Factory(
        private val uid: String,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            return InventoryViewModel(
                uid = uid,
                appContext = appCtx,
                inventoryRepository = InventoryRepository(appCtx),
                preferencesRepository = UserPreferencesRepository(appCtx),
                visionRepository = GeminiVisionRepository(),
                networkMonitor = NetworkMonitor(appCtx)
            ) as T
        }
    }
}
