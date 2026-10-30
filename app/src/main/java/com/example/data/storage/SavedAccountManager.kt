package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Metadata for a saved phone number on this device.
 * Never stores PIN or password hashes.
 */
data class SavedAccount(
    val phone: String,
    val savedAt: Long = System.currentTimeMillis()
) {
    /**
     * Partially masked phone representation for privacy and security.
     * E.g., "01812345678" -> "018****5678"
     */
    val maskedPhone: String
        get() {
            val clean = phone.trim()
            return if (clean.length >= 8) {
                val prefix = clean.take(3)
                val suffix = clean.takeLast(4)
                val stars = "*".repeat((clean.length - 7).coerceAtLeast(3))
                "$prefix$stars$suffix"
            } else {
                clean
            }
        }
}

/**
 * Manages device-local storage of remembered mobile numbers.
 * Security principles:
 * - Only mobile numbers and timestamp metadata are retained.
 * - PIN or PIN hash is NEVER stored.
 * - User consent controls whether a number is remembered.
 * - User can remove any number individually or clear all.
 * - User can choose whether logout clears remembered numbers or keeps them.
 */
class SavedAccountManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _savedAccounts = MutableStateFlow<List<SavedAccount>>(loadAccounts())
    val savedAccounts: StateFlow<List<SavedAccount>> = _savedAccounts.asStateFlow()

    private val _rememberOnLogout = MutableStateFlow(
        prefs.getBoolean(KEY_KEEP_ON_LOGOUT, true)
    )
    val keepOnLogout: StateFlow<Boolean> = _rememberOnLogout.asStateFlow()

    private fun loadAccounts(): List<SavedAccount> {
        val rawJson = prefs.getString(KEY_SAVED_ACCOUNTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(rawJson)
            val list = mutableListOf<SavedAccount>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val phone = obj.optString("phone", "").trim()
                val savedAt = obj.optLong("savedAt", System.currentTimeMillis())
                if (phone.isNotBlank()) {
                    list.add(SavedAccount(phone, savedAt))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistAccounts(list: List<SavedAccount>) {
        val array = JSONArray()
        list.forEach { account ->
            val obj = JSONObject()
            obj.put("phone", account.phone.trim())
            obj.put("savedAt", account.savedAt)
            array.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_ACCOUNTS, array.toString()).apply()
        _savedAccounts.value = list
    }

    /**
     * Save or update a phone number in the remembered accounts list.
     */
    fun saveAccount(phone: String) {
        val clean = phone.trim()
        if (clean.isBlank()) return
        val current = _savedAccounts.value.toMutableList()
        current.removeAll { it.phone == clean }
        // Prepend so latest used account appears first
        current.add(0, SavedAccount(phone = clean, savedAt = System.currentTimeMillis()))
        persistAccounts(current)
    }

    /**
     * Remove a single phone number from remembered accounts.
     */
    fun removeAccount(phone: String) {
        val clean = phone.trim()
        val updated = _savedAccounts.value.filterNot { it.phone == clean }
        persistAccounts(updated)
    }

    /**
     * Clear all remembered phone numbers.
     */
    fun clearAll() {
        persistAccounts(emptyList())
    }

    /**
     * Set user preference on whether to keep remembered numbers after logout.
     */
    fun setKeepOnLogout(keep: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_ON_LOGOUT, keep).apply()
        _rememberOnLogout.value = keep
    }

    /**
     * Called on user logout. If user chose not to keep remembered numbers on logout,
     * they are cleared.
     */
    fun handleLogout() {
        if (!_rememberOnLogout.value) {
            clearAll()
        }
    }

    companion object {
        private const val PREFS_NAME = "smart_biz_saved_accounts_prefs"
        private const val KEY_SAVED_ACCOUNTS = "saved_accounts_json"
        private const val KEY_KEEP_ON_LOGOUT = "keep_accounts_on_logout"

        @Volatile
        private var INSTANCE: SavedAccountManager? = null

        fun getInstance(context: Context): SavedAccountManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SavedAccountManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
