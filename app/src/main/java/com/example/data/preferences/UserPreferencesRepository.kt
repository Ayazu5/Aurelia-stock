package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.HandSide
import com.example.data.model.PhotoQuality
import com.example.data.model.QueuedOfflineOp
import com.example.data.model.ScanMode
import com.example.data.model.UserPreferences
import com.example.data.model.ViewDensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.aureliaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aurelia_stock_prefs"
)

/**
 * Persists client-side ergonomic preferences (hand orientation, dark/light theme,
 * UI resolution scale, photo quality, list/grid density, simulated offline flag)
 * and user-isolated offline sync queue entries in DataStore Preferences.
 */
class UserPreferencesRepository(private val context: Context) {

    private val dataStore = context.aureliaDataStore

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("global_dark_mode")
        fun handSide(uid: String) = stringPreferencesKey("u_${uid}_hand_side")
        fun darkMode(uid: String) = booleanPreferencesKey("u_${uid}_dark_mode")
        fun uiScale(uid: String) = intPreferencesKey("u_${uid}_ui_scale")
        fun photoQuality(uid: String) = stringPreferencesKey("u_${uid}_photo_quality")
        fun viewDensity(uid: String) = stringPreferencesKey("u_${uid}_view_density")
        fun simOffline(uid: String) = booleanPreferencesKey("u_${uid}_sim_offline")
        fun offlineQueue(uid: String) = stringPreferencesKey("u_${uid}_offline_queue")
    }

    val globalDarkMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: true
    }

    suspend fun setGlobalDarkMode(dark: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.DARK_MODE] = dark }
    }

    fun observePreferences(uid: String): Flow<UserPreferences> = dataStore.data.map { prefs ->
        val fallbackDark = prefs[Keys.DARK_MODE] ?: true
        UserPreferences(
            handSide = HandSide.fromKey(prefs[Keys.handSide(uid)]),
            darkMode = prefs[Keys.darkMode(uid)] ?: fallbackDark,
            uiScalePercent = (prefs[Keys.uiScale(uid)] ?: 100).coerceIn(90, 120),
            photoQuality = PhotoQuality.fromKey(prefs[Keys.photoQuality(uid)]),
            viewDensity = ViewDensity.fromKey(prefs[Keys.viewDensity(uid)]),
            simulatedOffline = prefs[Keys.simOffline(uid)] ?: false
        )
    }

    suspend fun setHandSide(uid: String, handSide: HandSide) {
        dataStore.edit { it[Keys.handSide(uid)] = handSide.key }
    }

    suspend fun setDarkMode(uid: String, dark: Boolean) {
        dataStore.edit {
            it[Keys.darkMode(uid)] = dark
            it[Keys.DARK_MODE] = dark
        }
    }

    suspend fun setUiScale(uid: String, scalePercent: Int) {
        dataStore.edit { it[Keys.uiScale(uid)] = scalePercent.coerceIn(90, 120) }
    }

    suspend fun setPhotoQuality(uid: String, quality: PhotoQuality) {
        dataStore.edit { it[Keys.photoQuality(uid)] = quality.key }
    }

    suspend fun setViewDensity(uid: String, density: ViewDensity) {
        dataStore.edit { it[Keys.viewDensity(uid)] = density.key }
    }

    suspend fun setSimulatedOffline(uid: String, offline: Boolean) {
        dataStore.edit { it[Keys.simOffline(uid)] = offline }
    }

    fun observeOfflineQueue(uid: String): Flow<List<QueuedOfflineOp>> = dataStore.data.map { prefs ->
        deserializeQueue(prefs[Keys.offlineQueue(uid)])
    }

    suspend fun enqueueOfflineOp(uid: String, op: QueuedOfflineOp) {
        dataStore.edit { prefs ->
            val current = deserializeQueue(prefs[Keys.offlineQueue(uid)]).toMutableList()
            current.add(op)
            prefs[Keys.offlineQueue(uid)] = serializeQueue(current)
        }
    }

    suspend fun removeOfflineOps(uid: String, opIds: Set<String>) {
        if (opIds.isEmpty()) return
        dataStore.edit { prefs ->
            val remaining = deserializeQueue(prefs[Keys.offlineQueue(uid)])
                .filterNot { it.opId in opIds }
            prefs[Keys.offlineQueue(uid)] = serializeQueue(remaining)
        }
    }

    private fun serializeQueue(list: List<QueuedOfflineOp>): String {
        val arr = JSONArray()
        list.forEach { op ->
            arr.put(
                JSONObject().apply {
                    put("opId", op.opId)
                    put("mode", op.mode.name)
                    put("targetItemId", op.targetItemId ?: JSONObject.NULL)
                    put("name", op.name)
                    put("extractedText", op.extractedText)
                    put("designDetails", op.designDetails)
                    put("category", op.category)
                    put("quantityDelta", op.quantityDelta)
                    put("threshold", op.threshold)
                    put("thumbnailBase64", op.thumbnailBase64 ?: JSONObject.NULL)
                    put("fpHash", op.fpHash ?: JSONObject.NULL)
                    if (op.fpAvgRgb != null) put("fpAvgRgb", JSONArray(op.fpAvgRgb))
                    if (op.fpRadial != null) put("fpRadial", JSONArray(op.fpRadial))
                    if (op.fpEdgeDensity != null) put("fpEdgeDensity", op.fpEdgeDensity)
                    put("queuedAtMillis", op.queuedAtMillis)
                }
            )
        }
        return arr.toString()
    }

    private fun deserializeQueue(raw: String?): List<QueuedOfflineOp> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val modeStr = obj.optString("mode", "IN")
                val mode = ScanMode.entries.firstOrNull { it.name == modeStr } ?: ScanMode.IN
                QueuedOfflineOp(
                    opId = obj.optString("opId", ""),
                    mode = mode,
                    targetItemId = obj.optString("targetItemId", "").takeIf { it.isNotEmpty() && it != "null" },
                    name = obj.optString("name", ""),
                    extractedText = obj.optString("extractedText", ""),
                    designDetails = obj.optString("designDetails", ""),
                    category = obj.optString("category", "General"),
                    quantityDelta = obj.optLong("quantityDelta", 1L),
                    threshold = obj.optLong("threshold", 5L),
                    thumbnailBase64 = obj.optString("thumbnailBase64", "").takeIf { it.isNotEmpty() && it != "null" },
                    fpHash = obj.optString("fpHash", "").takeIf { it.isNotEmpty() && it != "null" },
                    fpAvgRgb = obj.optJSONArray("fpAvgRgb")?.let { a ->
                        if (a.length() == 3) listOf(a.optLong(0), a.optLong(1), a.optLong(2)) else null
                    },
                    fpRadial = obj.optJSONArray("fpRadial")?.let { a ->
                        if (a.length() == 3) listOf(a.optLong(0), a.optLong(1), a.optLong(2)) else null
                    },
                    fpEdgeDensity = if (obj.has("fpEdgeDensity") && !obj.isNull("fpEdgeDensity")) {
                        obj.optLong("fpEdgeDensity")
                    } else null,
                    queuedAtMillis = obj.optLong("queuedAtMillis", System.currentTimeMillis())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
