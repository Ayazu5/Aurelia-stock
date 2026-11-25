package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.ImageFingerprint
import com.example.data.model.InventoryItem
import com.example.data.model.StockHistoryEntry
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Authenticated Firestore repository targeting the dedicated named database
 * (R.string.firestore_database_id). Every payload strictly aligns with firestore.rules.
 */
class InventoryRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun inventoryCollection(uid: String) =
        db.collection("users").document(uid).collection("inventory")

    private fun historyCollection(uid: String) =
        db.collection("users").document(uid).collection("history")

    /**
     * Real-time stream of user's isolated inventory sorted by most recently updated.
     */
    fun observeInventory(uid: String): Flow<List<InventoryItem>> = flow {
        require(uid.isNotBlank()) { "User ID must not be blank." }
        val path = "users/$uid/inventory"
        emitAll(
            inventoryCollection(uid)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        InventoryItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Real-time stream of user's immutable Stock IN/OUT audit logbook.
     */
    fun observeHistory(uid: String): Flow<List<StockHistoryEntry>> = flow {
        require(uid.isNotBlank()) { "User ID must not be blank." }
        val path = "users/$uid/history"
        emitAll(
            historyCollection(uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(120)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StockHistoryEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Creates a new SKU inventory document and appends an audit log entry.
     */
    suspend fun createInventoryItem(
        name: String,
        extractedText: String,
        designDetails: String,
        category: String,
        quantity: Long,
        threshold: Long,
        thumbnailBase64: String?,
        fingerprint: ImageFingerprint?,
        offlineQueued: Boolean = false
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/inventory"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(120)
            require(cleanName.isNotEmpty()) { "Variant name cannot be blank." }
            val cleanCategory = category.trim().ifBlank { "General" }.take(60)
            val safeQty = quantity.coerceIn(0L, 10_000_000L)
            val safeThreshold = threshold.coerceIn(0L, 1_000_000L)

            val docRef = inventoryCollection(uid).document()
            docPath = docRef.path

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "name" to cleanName,
                "extractedText" to extractedText.trim().take(200),
                "designDetails" to designDetails.trim().take(300),
                "category" to cleanCategory,
                "quantity" to safeQty,
                "threshold" to safeThreshold,
                "thumbnailBase64" to thumbnailBase64?.takeIf { it.isNotBlank() }?.take(180_000),
                "fpHash" to fingerprint?.hash?.take(256),
                "fpAvgRgb" to fingerprint?.avgRgb?.takeIf { it.size == 3 },
                "fpRadial" to fingerprint?.radial?.takeIf { it.size == 3 },
                "fpEdgeDensity" to fingerprint?.edgeDensity?.coerceIn(0L, 512L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()

            if (safeQty > 0L) {
                recordHistoryEntryInternal(
                    uid = uid,
                    itemId = docRef.id,
                    type = "IN",
                    itemName = cleanName,
                    quantity = safeQty,
                    resultingStock = safeQty,
                    thumbnailBase64 = thumbnailBase64,
                    offlineQueued = offlineQueued
                )
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Inline quick +1 / -1 stock adjustment matching Action 1 in firestore.rules
     * (only touches quantity and updatedAt) plus audit log entry.
     */
    suspend fun quickAdjustQuantity(
        item: InventoryItem,
        delta: Long,
        offlineQueued: Boolean = false
    ): Result<Long> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/inventory/${item.id}"
        return try {
            val uid = requireUserId()
            require(item.id.isNotBlank()) { "Item ID must not be blank." }
            val nextQty = (item.quantity + delta).coerceIn(0L, 10_000_000L)
            if (nextQty == item.quantity) return Result.success(nextQty)

            val docRef = inventoryCollection(uid).document(item.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "quantity" to nextQty,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val appliedDelta = kotlin.math.abs(nextQty - item.quantity)
            if (appliedDelta >= 1L) {
                recordHistoryEntryInternal(
                    uid = uid,
                    itemId = item.id,
                    type = if (delta > 0L) "IN" else "OUT",
                    itemName = item.name,
                    quantity = appliedDelta,
                    resultingStock = nextQty,
                    thumbnailBase64 = item.thumbnailBase64,
                    offlineQueued = offlineQueued
                )
            }
            Result.success(nextQty)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Updates an existing SKU variant during Stock IN/OUT or direct Edit modal
     * matching Action 2 in firestore.rules (preserves immutable userId & createdAt).
     */
    suspend fun updateInventoryItem(
        itemId: String,
        name: String,
        extractedText: String,
        designDetails: String,
        category: String,
        newQuantity: Long,
        threshold: Long,
        thumbnailBase64: String? = null,
        fingerprint: ImageFingerprint? = null,
        historyType: String? = null,
        historyDelta: Long = 0L,
        offlineQueued: Boolean = false
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/inventory/$itemId"
        return try {
            val uid = requireUserId()
            require(itemId.isNotBlank()) { "Item ID must not be blank for update." }
            val cleanName = name.trim().take(120)
            require(cleanName.isNotEmpty()) { "Variant name cannot be blank." }
            val cleanCategory = category.trim().ifBlank { "General" }.take(60)
            val safeQty = newQuantity.coerceIn(0L, 10_000_000L)
            val safeThreshold = threshold.coerceIn(0L, 1_000_000L)

            val docRef = inventoryCollection(uid).document(itemId)
            docPath = docRef.path

            val updates = mutableMapOf<String, Any>(
                "name" to cleanName,
                "extractedText" to extractedText.trim().take(200),
                "designDetails" to designDetails.trim().take(300),
                "category" to cleanCategory,
                "quantity" to safeQty,
                "threshold" to safeThreshold,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!thumbnailBase64.isNullOrBlank()) {
                updates["thumbnailBase64"] = thumbnailBase64.take(180_000)
            }
            if (fingerprint != null) {
                updates["fpHash"] = fingerprint.hash.take(256)
                if (fingerprint.avgRgb.size == 3) updates["fpAvgRgb"] = fingerprint.avgRgb
                if (fingerprint.radial.size == 3) updates["fpRadial"] = fingerprint.radial
                updates["fpEdgeDensity"] = fingerprint.edgeDensity.coerceIn(0L, 512L)
            }

            docRef.update(updates).await()

            if (historyType != null && (historyType == "IN" || historyType == "OUT") && historyDelta >= 1L) {
                recordHistoryEntryInternal(
                    uid = uid,
                    itemId = itemId,
                    type = historyType,
                    itemName = cleanName,
                    quantity = historyDelta.coerceIn(1L, 10_000_000L),
                    resultingStock = safeQty,
                    thumbnailBase64 = thumbnailBase64,
                    offlineQueued = offlineQueued
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteInventoryItem(itemId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/inventory/$itemId"
        return try {
            val uid = requireUserId()
            require(itemId.isNotBlank()) { "Item ID must not be blank for delete." }
            val docRef = inventoryCollection(uid).document(itemId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun recordHistoryEntryInternal(
        uid: String,
        itemId: String,
        type: String,
        itemName: String,
        quantity: Long,
        resultingStock: Long,
        thumbnailBase64: String?,
        offlineQueued: Boolean
    ) {
        val docRef = historyCollection(uid).document()
        val payload = mapOf<String, Any?>(
            "userId" to uid,
            "itemId" to itemId,
            "type" to if (type == "OUT") "OUT" else "IN",
            "itemName" to itemName.trim().ifBlank { "Unnamed SKU" }.take(200),
            "quantity" to quantity.coerceIn(1L, 10_000_000L),
            "resultingStock" to resultingStock.coerceIn(0L, 10_000_000L),
            "thumbnailBase64" to thumbnailBase64?.takeIf { it.isNotBlank() }?.take(180_000),
            "offlineQueued" to offlineQueued,
            "createdAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }
}
