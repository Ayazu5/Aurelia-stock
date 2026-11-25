package com.example.data.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

/**
 * Logs structured JSON diagnostics for Firestore operation errors so permission or schema
 * issues are immediately visible in logcat with auth context.
 */
fun handleFirestoreError(
    error: Exception,
    operationType: OperationType,
    path: String?
) {
    val auth = Firebase.auth
    val user = auth.currentUser
    val providers = JSONArray().apply {
        user?.providerData?.forEach { info ->
            put(
                JSONObject().apply {
                    put("providerId", info.providerId)
                    put("displayName", info.displayName ?: JSONObject.NULL)
                    put("email", info.email ?: JSONObject.NULL)
                }
            )
        }
    }

    val payload = JSONObject().apply {
        put("error", error.message ?: error.javaClass.simpleName)
        put("operationType", operationType.value)
        put("path", path ?: JSONObject.NULL)
        put(
            "authInfo",
            JSONObject().apply {
                put("userId", user?.uid ?: JSONObject.NULL)
                put("email", user?.email ?: JSONObject.NULL)
                put("emailVerified", user?.isEmailVerified ?: false)
                put("isAnonymous", user?.isAnonymous ?: false)
                put("providerInfo", providers)
            }
        )
    }

    Log.e("FirestoreError", "Firestore Operation Failed: $payload", error)
}
