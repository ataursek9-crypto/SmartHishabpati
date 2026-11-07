package com.example.data.backend

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Multi-Device Active Sessions Tracker & Remote Session Revocation
 * Stores device metadata under Firestore /businesses/{shopId}/active_devices/{deviceId}.
 * Allows Store Admin to view all active phones/tablets and remotely revoke any session.
 */
class FirestoreSessionManager(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ISessionManager {

    companion object {
        private const val TAG = "SessionManager"
        private const val PREF_SESSION = "smart_hishabpati_session_store"
        private const val KEY_DEVICE_ID = "local_device_unique_id"
    }

    /**
     * Gets or generates a stable unique device identifier for this app installation
     */
    fun getLocalDeviceId(): String {
        val sp = context.getSharedPreferences(PREF_SESSION, Context.MODE_PRIVATE)
        var devId = sp.getString(KEY_DEVICE_ID, null)
        if (devId.isNullOrBlank()) {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (_: Exception) { null }
            devId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                "dev_$androidId"
            } else {
                "dev_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            }
            sp.edit().putString(KEY_DEVICE_ID, devId).apply()
        }
        return devId
    }

    override suspend fun registerCurrentDevice(
        shopId: String,
        userId: String,
        userRole: String
    ): DeviceSession {
        val devId = getLocalDeviceId()
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val appVersion = "v1.1"
        val now = System.currentTimeMillis()

        val sessionData = hashMapOf<String, Any>(
            "deviceId" to devId,
            "deviceName" to deviceName,
            "manufacturer" to manufacturer,
            "osVersion" to osVersion,
            "appVersion" to appVersion,
            "loggedInAt" to now,
            "lastActiveAt" to now,
            "userId" to userId,
            "userRole" to userRole,
            "shopId" to shopId,
            "isRevoked" to false,
            "ipAddressOrNetwork" to "Active Device"
        )

        try {
            firestore.collection("businesses").document(shopId)
                .collection("active_devices").document(devId)
                .set(sessionData, SetOptions.merge())
                .await()
            Log.d(TAG, "Device session registered for deviceId: $devId in shop: $shopId")
        } catch (e: Exception) {
            Log.w(TAG, "Device session registration notice: ${e.message}")
        }

        return DeviceSession(
            sessionId = devId,
            deviceId = devId,
            deviceName = deviceName,
            manufacturer = manufacturer,
            osVersion = osVersion,
            appVersion = appVersion,
            loggedInAt = now,
            lastActiveAt = now,
            userId = userId,
            userRole = userRole,
            shopId = shopId,
            isCurrentDevice = true,
            isRevoked = false
        )
    }

    override suspend fun getActiveSessions(shopId: String): List<DeviceSession> {
        val localDevId = getLocalDeviceId()
        return try {
            val snapshot = firestore.collection("businesses").document(shopId)
                .collection("active_devices")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val devId = doc.getString("deviceId") ?: doc.id
                val isRevoked = doc.getBoolean("isRevoked") ?: false
                if (isRevoked) return@mapNotNull null // Filter out revoked sessions

                DeviceSession(
                    sessionId = doc.id,
                    deviceId = devId,
                    deviceName = doc.getString("deviceName") ?: "স্মার্টফোন",
                    manufacturer = doc.getString("manufacturer") ?: "",
                    osVersion = doc.getString("osVersion") ?: "",
                    appVersion = doc.getString("appVersion") ?: "v1.1",
                    loggedInAt = doc.getLong("loggedInAt") ?: 0L,
                    lastActiveAt = doc.getLong("lastActiveAt") ?: 0L,
                    userId = doc.getString("userId") ?: "",
                    userRole = doc.getString("userRole") ?: "STAFF",
                    shopId = shopId,
                    isCurrentDevice = (devId == localDevId),
                    isRevoked = isRevoked,
                    ipAddressOrNetwork = doc.getString("ipAddressOrNetwork") ?: "Online"
                )
            }.sortedByDescending { it.isCurrentDevice }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch active sessions notice: ${e.message}")
            emptyList()
        }
    }

    override suspend fun revokeSession(shopId: String, sessionId: String): Boolean {
        return try {
            firestore.collection("businesses").document(shopId)
                .collection("active_devices").document(sessionId)
                .update(
                    mapOf(
                        "isRevoked" to true,
                        "revokedAt" to System.currentTimeMillis()
                    )
                ).await()
            Log.i(TAG, "Device session $sessionId revoked successfully by admin")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Revoke session failed: ${e.message}")
            false
        }
    }

    override suspend fun isCurrentSessionValid(shopId: String, currentSessionId: String): Boolean {
        val devId = currentSessionId.ifBlank { getLocalDeviceId() }
        return try {
            val doc = firestore.collection("businesses").document(shopId)
                .collection("active_devices").document(devId)
                .get()
                .await()
            if (!doc.exists()) return true // Not recorded yet, assume valid
            val isRevoked = doc.getBoolean("isRevoked") ?: false
            !isRevoked
        } catch (e: Exception) {
            Log.w(TAG, "Session validity check notice: ${e.message}")
            true // Network failure shouldn't immediately lock user out
        }
    }
}
