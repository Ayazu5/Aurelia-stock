package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val extractedText: String = "",
    val designDetails: String = "",
    val category: String = "General",
    val quantity: Long = 0L,
    val threshold: Long = 5L,
    val thumbnailBase64: String? = null,
    val fpHash: String? = null,
    val fpAvgRgb: List<Long>? = null,
    val fpRadial: List<Long>? = null,
    val fpEdgeDensity: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isLowStock(): Boolean = quantity <= threshold

    fun toImageFingerprint(): ImageFingerprint? {
        val hash = fpHash ?: return null
        val rgb = fpAvgRgb?.takeIf { it.size == 3 } ?: return null
        val radial = fpRadial?.takeIf { it.size == 3 } ?: return null
        val edge = fpEdgeDensity ?: return null
        return ImageFingerprint(
            hash = hash,
            avgRgb = rgb,
            radial = radial,
            edgeDensity = edge
        )
    }
}

@Keep
@IgnoreExtraProperties
data class StockHistoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val itemId: String = "",
    val type: String = "IN",
    val itemName: String = "",
    val quantity: Long = 1L,
    val resultingStock: Long = 0L,
    val thumbnailBase64: String? = null,
    val offlineQueued: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class ImageFingerprint(
    val hash: String,
    val avgRgb: List<Long>,
    val radial: List<Long>,
    val edgeDensity: Long
)

data class SimilarCandidate(
    val id: String,
    val similarity: Double,
    val reason: String
)

data class AiVisionAnalysis(
    val suggestedName: String,
    val extractedText: String,
    val designDetails: String,
    val category: String,
    val suggestedThreshold: Int,
    val confidence: Double,
    val confidenceNote: String,
    val exactMatchId: String?,
    val ambiguous: Boolean,
    val similarCandidates: List<SimilarCandidate>
)

enum class HandSide(val key: String) {
    LEFT("left"),
    RIGHT("right");

    companion object {
        fun fromKey(raw: String?): HandSide =
            entries.firstOrNull { it.key == raw } ?: LEFT
    }
}

enum class ViewDensity(val key: String) {
    LIST("list"),
    GRID("grid");

    companion object {
        fun fromKey(raw: String?): ViewDensity =
            entries.firstOrNull { it.key == raw } ?: LIST
    }
}

enum class PhotoQuality(
    val key: String,
    val label: String,
    val maxDimension: Int,
    val jpegQuality: Int
) {
    LOW("low", "Low", 360, 55),
    MEDIUM("medium", "Medium", 640, 72),
    HIGH("high", "High", 1024, 88);

    companion object {
        fun fromKey(raw: String?): PhotoQuality =
            entries.firstOrNull { it.key == raw } ?: MEDIUM
    }
}

enum class ScanMode {
    IN,
    OUT
}

sealed interface StockFilter {
    data object All : StockFilter
    data object LowStock : StockFilter
    data object InStock : StockFilter
    data class Category(val name: String) : StockFilter
}

data class UserPreferences(
    val handSide: HandSide = HandSide.LEFT,
    val darkMode: Boolean = true,
    val uiScalePercent: Int = 100,
    val photoQuality: PhotoQuality = PhotoQuality.MEDIUM,
    val viewDensity: ViewDensity = ViewDensity.LIST,
    val simulatedOffline: Boolean = false
)

/**
 * Local queued stock mutation captured when offline or in Simulated Offline Mode.
 * Automatically flushed to Firestore upon reconnection.
 */
data class QueuedOfflineOp(
    val opId: String,
    val mode: ScanMode,
    val targetItemId: String?,
    val name: String,
    val extractedText: String,
    val designDetails: String,
    val category: String,
    val quantityDelta: Long,
    val threshold: Long,
    val thumbnailBase64: String?,
    val fpHash: String?,
    val fpAvgRgb: List<Long>?,
    val fpRadial: List<Long>?,
    val fpEdgeDensity: Long?,
    val queuedAtMillis: Long
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ToastTone {
    SUCCESS,
    INFO,
    WARNING,
    ERROR
}

data class ToastMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val tone: ToastTone = ToastTone.INFO
)
