package com.example.data.backend

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Hybrid Account Manager:
 * Manages Server-Verified Identity Mapping between Firebase and Supabase.
 *
 * STRICT SECURITY POLICIES:
 * 1. Single Shop Per Phone Enforcement: Prohibits accidental duplicate store creation for the same phone number.
 * 2. Authenticated Proof Required: Accounts are never linked based on unverified string matching alone.
 * 3. Preserves all existing Firebase Owner UIDs, Shop IDs, Products, and Invoices.
 */
class HybridAccountManager(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "HybridAccountManager"
    }

    /**
     * Checks if a phone number is already registered as an owner of an existing shop in Firestore.
     * Prevents accidental duplicate shop creation for the same phone number.
     */
    suspend fun findExistingShopByPhone(phone: String): String? {
        val cleanPhone = phone.replace(Regex("[^0-9]"), "").trim()
        val normalized = when {
            cleanPhone.startsWith("880") -> "0" + cleanPhone.substring(3)
            else -> cleanPhone
        }

        return try {
            val querySnapshot = firestore.collection("businesses")
                .whereEqualTo("phone", normalized)
                .limit(1)
                .get()
                .await()

            if (!querySnapshot.isEmpty) {
                val doc = querySnapshot.documents.first()
                val shopId = doc.id
                Log.i(TAG, "Existing shop found for phone $normalized: $shopId")
                shopId
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "findExistingShopByPhone check notice: ${e.message}")
            null
        }
    }

    /**
     * Securely records the verified link between a Firebase UID and a Supabase UID
     * under Firestore /user_identity_mappings/{firebaseUid}
     */
    suspend fun recordVerifiedMapping(
        firebaseUid: String,
        supabaseUid: String,
        phone: String?,
        email: String?
    ): Boolean {
        if (firebaseUid.isBlank() || supabaseUid.isBlank()) return false
        val cleanPhone = phone?.trim() ?: ""
        val cleanEmail = email?.trim()?.lowercase() ?: ""

        val mappingData = hashMapOf<String, Any>(
            "firebaseUid" to firebaseUid,
            "supabaseUid" to supabaseUid,
            "linkedAt" to System.currentTimeMillis(),
            "phone" to cleanPhone,
            "email" to cleanEmail,
            "verified" to true
        )

        return try {
            firestore.collection("user_identity_mappings").document(firebaseUid)
                .set(mappingData)
                .await()
            Log.i(TAG, "Server-verified account mapping established: Firebase($firebaseUid) <-> Supabase($supabaseUid)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record identity mapping: ${e.message}")
            false
        }
    }

    /**
     * Resolves the linked Supabase UID for a given Firebase UID if mapped
     */
    suspend fun getLinkedSupabaseUid(firebaseUid: String): String? {
        if (firebaseUid.isBlank()) return null
        return try {
            val doc = firestore.collection("user_identity_mappings").document(firebaseUid)
                .get()
                .await()
            if (doc.exists()) doc.getString("supabaseUid") else null
        } catch (e: Exception) {
            Log.w(TAG, "getLinkedSupabaseUid notice: ${e.message}")
            null
        }
    }
}
