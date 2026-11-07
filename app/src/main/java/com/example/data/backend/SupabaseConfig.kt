package com.example.data.backend

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Supabase Client Configuration & Security Policy Enforcement
 *
 * CRITICAL SECURITY CONSTRAINTS:
 * 1. ONLY public anonymous keys ('anon' public key) are permitted in Android client applications.
 * 2. Service Role Secret Key (service_role) MUST NEVER be embedded in client APK or source code.
 * 3. Direct Database Master Password MUST NEVER be accessible to the mobile client.
 * 4. Data access must be governed strictly by PostgreSQL Row Level Security (RLS) policies.
 */
object SupabaseConfig {
    private const val TAG = "SupabaseConfig"

    // Default prototype configuration placeholders for Supabase Auth
    // In production, these can be overridden via BuildConfig or Secrets panel
    private var customProjectUrl: String? = null
    private var customAnonKey: String? = null

    // Fallback sandbox endpoint for testing if not yet provisioned
    const val DEFAULT_SUPABASE_URL = "https://smarthishabpati.supabase.co"
    const val DEFAULT_ANON_PUBLIC_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.prototype_anon_key"

    fun getProjectUrl(): String {
        return customProjectUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_SUPABASE_URL
    }

    fun getAnonPublicKey(): String {
        return customAnonKey?.takeIf { it.isNotBlank() } ?: DEFAULT_ANON_PUBLIC_KEY
    }

    fun isConfigured(): Boolean {
        val url = getProjectUrl()
        val key = getAnonPublicKey()
        return url.isNotBlank() && !url.contains("prototype") && key.isNotBlank() && !key.contains("prototype")
    }

    fun setCredentials(url: String, anonKey: String) {
        // Enforce that a secret service_role key is never accepted
        if (anonKey.contains("service_role", ignoreCase = true)) {
            Log.e(TAG, "SECURITY VIOLATION: Attempted to inject service_role secret key into Android client! Blocked.")
            throw SecurityException("Service Role Key must never be configured in client applications!")
        }
        customProjectUrl = url.trim()
        customAnonKey = anonKey.trim()
    }

    /**
     * Formats phone numbers to standard E.164 (+880...) required by Supabase Auth
     */
    fun formatToE164(phone: String): String {
        val digits = phone.replace(Regex("[^0-9+]"), "").trim()
        return when {
            digits.startsWith("+880") -> digits
            digits.startsWith("880") -> "+$digits"
            digits.startsWith("01") -> "+880${digits.substring(1)}"
            digits.startsWith("1") && digits.length == 10 -> "+880$digits"
            else -> if (digits.startsWith("+")) digits else "+$digits"
        }
    }
}
