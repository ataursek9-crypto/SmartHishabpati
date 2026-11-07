package com.example.data.backend

/**
 * Supported Authentication Providers in SmartHishabpati Hybrid Architecture
 */
enum class AuthProviderType {
    FIREBASE,
    SUPABASE,
    HYBRID,
    LOCAL_OFFLINE
}

/**
 * Provider-Independent Authenticated User Model
 */
data class BackendUser(
    val id: String,
    val email: String? = null,
    val phone: String? = null,
    val displayName: String? = null,
    val isEmailVerified: Boolean = false,
    val isPhoneVerified: Boolean = false,
    val primaryProvider: AuthProviderType = AuthProviderType.FIREBASE,
    val linkedProviders: List<AuthProviderType> = listOf(primaryProvider),
    val linkedFirebaseUid: String? = null,
    val linkedSupabaseUid: String? = null,
    val shopId: String? = null,
    val role: String = "ADMIN",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Standardized Authentication Result for all providers
 */
sealed class BackendAuthResult {
    data class Success(
        val user: BackendUser,
        val token: String? = null,
        val message: String = "প্রমাণীকরণ সফল হয়েছে"
    ) : BackendAuthResult()

    data class UnverifiedPhone(
        val phone: String,
        val message: String = "ফোন নম্বরের মালিকানা এসএমএস কোড দ্বারা যাচাই করা হয়নি। নিরাপত্তা রক্ষার্থে এই অ্যাকাউন্টটি এখনও ভেরিফাইড নয়।"
    ) : BackendAuthResult()

    data class AccountLinkingRequired(
        val existingUid: String,
        val existingEmail: String?,
        val existingPhone: String?,
        val targetProvider: AuthProviderType,
        val message: String
    ) : BackendAuthResult()

    data class DuplicateAccountDetected(
        val existingShopId: String,
        val message: String = "এই ফোন নম্বর বা ইমেইলের অধীনে ইতিমধ্যে একটি নিবন্ধিত দোকান রয়েছে। নতুন দোকান তৈরি না করে আগের দোকানে প্রবেশ করুন।"
    ) : BackendAuthResult()

    data class Error(
        val errorCode: String,
        val messageBn: String,
        val messageEn: String
    ) : BackendAuthResult()
}

/**
 * Active Device Session Model for Multi-Device Management & Revocation
 */
data class DeviceSession(
    val sessionId: String,
    val deviceId: String,
    val deviceName: String,
    val manufacturer: String,
    val osVersion: String,
    val appVersion: String,
    val loggedInAt: Long,
    val lastActiveAt: Long,
    val userId: String,
    val userRole: String,
    val shopId: String,
    val isCurrentDevice: Boolean = false,
    val isRevoked: Boolean = false,
    val ipAddressOrNetwork: String = "Online"
)
