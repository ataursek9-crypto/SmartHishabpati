package com.example.data.backend

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lightweight, zero-dependency Supabase Auth REST Client
 * Interacts with Supabase GoTrue Auth API (/auth/v1/...) over secure TLS.
 *
 * ZERO-TRUST PRINCIPLES:
 * 1. PIN/Password is verified server-side on Supabase via bcrypt; client NEVER sees or stores PIN hash.
 * 2. If phone is not verified via official SMS OTP, phone_confirmed_at is NULL, and account is strictly marked UNVERIFIED.
 * 3. Uses only the public ANON API key.
 */
class SupabaseAuthClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "SupabaseAuthClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Registers a user via Supabase Auth using Phone and Password/PIN
     */
    suspend fun signUpWithPhone(phone: String, passwordOrPin: String): BackendAuthResult = withContext(Dispatchers.IO) {
        val baseUrl = SupabaseConfig.getProjectUrl()
        val anonKey = SupabaseConfig.getAnonPublicKey()
        val e164Phone = SupabaseConfig.formatToE164(phone)

        if (!SupabaseConfig.isConfigured()) {
            // Safe Prototype fallback when live Supabase project is not yet provisioned
            Log.w(TAG, "Live Supabase backend is not configured. Running in prototype validation mode.")
            return@withContext BackendAuthResult.UnverifiedPhone(
                phone = e164Phone,
                message = "সতর্কতা: Supabase লাইভ ব্যাকএন্ড এখনো কনফিগার করা হয়নি। SMS ভেরিফিকেশন ছাড়া ফোন নম্বর যাচাইকৃত হিসেবে গণ্য হবে না।"
            )
        }

        try {
            val endpoint = "$baseUrl/auth/v1/signup"
            val requestBodyJson = JSONObject().apply {
                put("phone", e164Phone)
                put("password", passwordOrPin.trim())
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "Supabase signUp response code: ${response.code}")

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val userId = json.optString("id").ifBlank { json.optJSONObject("user")?.optString("id") ?: "" }
                val phoneConfirmedAt = json.optString("phone_confirmed_at")
                    .ifBlank { json.optJSONObject("user")?.optString("phone_confirmed_at") ?: "" }

                val isPhoneVerified = phoneConfirmedAt.isNotBlank() && phoneConfirmedAt != "null"

                val backendUser = BackendUser(
                    id = userId,
                    phone = e164Phone,
                    isPhoneVerified = isPhoneVerified,
                    primaryProvider = AuthProviderType.SUPABASE,
                    linkedSupabaseUid = userId
                )

                if (!isPhoneVerified) {
                    // Strict Rule 4: Phone without SMS confirmation MUST NOT be reported as verified!
                    return@withContext BackendAuthResult.UnverifiedPhone(
                        phone = e164Phone,
                        message = "অ্যাকাউন্ট তৈরি হয়েছে, তবে এসএমএস ভেরিফিকেশন ছাড়া ফোন নম্বরটি অনির্ধারিত/আনভেরিফাইড থাকবে।"
                    )
                }

                return@withContext BackendAuthResult.Success(
                    user = backendUser,
                    message = "Supabase-এ ফোন রেজিস্ট্রেশন সম্পন্ন হয়েছে।"
                )
            } else {
                val errJson = try { JSONObject(responseBody) } catch (_: Exception) { null }
                val errMsg = errJson?.optString("msg") ?: errJson?.optString("error_description") ?: "রেজিস্ট্রেশন ব্যর্থ হয়েছে"
                val errCode = errJson?.optString("error_code") ?: "SIGNUP_FAILED"

                if (errMsg.contains("already registered", ignoreCase = true) || errCode.contains("user_already_exists", ignoreCase = true)) {
                    return@withContext BackendAuthResult.DuplicateAccountDetected(
                        existingShopId = "",
                        message = "এই ফোন নম্বর দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে। অনুগ্রহ করে সরাসরি লগইন করুন।"
                    )
                }

                return@withContext BackendAuthResult.Error(
                    errorCode = errCode,
                    messageBn = "Supabase রেজিস্ট্রেশন ব্যর্থ: $errMsg",
                    messageEn = errMsg
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase signUp exception", e)
            return@withContext BackendAuthResult.Error(
                errorCode = "NETWORK_ERROR",
                messageBn = "সার্ভার সংযোগ ব্যর্থ হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক বিচ্ছিন্ন"}",
                messageEn = e.message ?: "Network error"
            )
        }
    }

    /**
     * Authenticates a user via Supabase Auth using Phone and Password/PIN
     */
    suspend fun signInWithPhone(phone: String, passwordOrPin: String): BackendAuthResult = withContext(Dispatchers.IO) {
        val baseUrl = SupabaseConfig.getProjectUrl()
        val anonKey = SupabaseConfig.getAnonPublicKey()
        val e164Phone = SupabaseConfig.formatToE164(phone)

        if (!SupabaseConfig.isConfigured()) {
            return@withContext BackendAuthResult.Error(
                errorCode = "NOT_CONFIGURED",
                messageBn = "Supabase লাইভ প্রজেক্ট ইউআরএল সেটআপ করা হয়নি। Firebase জিমেইল লগইন ব্যবহার করুন।",
                messageEn = "Supabase credentials unconfigured"
            )
        }

        try {
            val endpoint = "$baseUrl/auth/v1/token?grant_type=password"
            val requestBodyJson = JSONObject().apply {
                put("phone", e164Phone)
                put("password", passwordOrPin.trim())
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val accessToken = json.optString("access_token")
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id") ?: ""
                val phoneConfirmedAt = userObj?.optString("phone_confirmed_at") ?: ""
                val isPhoneVerified = phoneConfirmedAt.isNotBlank() && phoneConfirmedAt != "null"

                val backendUser = BackendUser(
                    id = userId,
                    phone = e164Phone,
                    isPhoneVerified = isPhoneVerified,
                    primaryProvider = AuthProviderType.SUPABASE,
                    linkedSupabaseUid = userId
                )

                return@withContext BackendAuthResult.Success(
                    user = backendUser,
                    token = accessToken,
                    message = "লগইন সফল হয়েছে।"
                )
            } else {
                val errJson = try { JSONObject(responseBody) } catch (_: Exception) { null }
                val errMsg = errJson?.optString("error_description") ?: errJson?.optString("msg") ?: "লগইন ব্যর্থ হয়েছে"
                val errCode = errJson?.optString("error") ?: "INVALID_CREDENTIALS"

                val userFriendlyBn = if (errMsg.contains("invalid login", ignoreCase = true) || errMsg.contains("invalid grant", ignoreCase = true)) {
                    "ভুল ফোন নম্বর অথবা পাসওয়ার্ড/পিন কোড! পুনরায় সঠিক তথ্য দিয়ে চেষ্টা করুন।"
                } else {
                    "লগইন ত্রুটি: $errMsg"
                }

                return@withContext BackendAuthResult.Error(
                    errorCode = errCode,
                    messageBn = userFriendlyBn,
                    messageEn = errMsg
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase signIn exception", e)
            return@withContext BackendAuthResult.Error(
                errorCode = "NETWORK_ERROR",
                messageBn = "সার্ভার সংযোগ বিচ্ছিন্ন বা নেটওয়ার্ক অনুপলব্ধ।",
                messageEn = e.message ?: "Network error"
            )
        }
    }
}
