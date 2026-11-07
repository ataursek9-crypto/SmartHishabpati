package com.example.data.backend

import kotlinx.coroutines.flow.StateFlow

/**
 * Provider-Independent Authentication Contract
 * Allows switching between Firebase, Supabase, or Custom API without modifying UI or business logic.
 */
interface IAuthProvider {
    val providerType: AuthProviderType
    val currentUserState: StateFlow<BackendUser?>

    fun getCurrentUser(): BackendUser?

    /**
     * Authenticates using Email and Password (Firebase Auth Free Tier)
     */
    suspend fun signInWithEmail(email: String, password: String): BackendAuthResult

    /**
     * Registers a new user with Email and Password
     */
    suspend fun signUpWithEmail(email: String, password: String): BackendAuthResult

    /**
     * Authenticates using Phone and Password/PIN
     * In Supabase: verifies password server-side via Bcrypt without exposing PIN hash to Android client.
     */
    suspend fun signInWithPhone(phone: String, passwordOrPin: String): BackendAuthResult

    /**
     * Registers a user with Phone and Password/PIN
     */
    suspend fun signUpWithPhone(phone: String, passwordOrPin: String): BackendAuthResult

    /**
     * Sends password reset request
     */
    suspend fun sendPasswordReset(email: String): BackendAuthResult

    /**
     * Safely links an existing Firebase account with Supabase credentials after verifying ownership
     */
    suspend fun linkAccounts(
        primaryUser: BackendUser,
        secondaryCredentials: Map<String, String>
    ): BackendAuthResult

    /**
     * Terminates the active session
     */
    suspend fun signOut()
}

/**
 * Multi-Device Session Management Contract
 * Tracks all devices logged into a shop and allows Admin to remotely revoke unauthorized sessions.
 */
interface ISessionManager {
    /**
     * Registers the current device's active session in the Cloud Backend
     */
    suspend fun registerCurrentDevice(
        shopId: String,
        userId: String,
        userRole: String
    ): DeviceSession

    /**
     * Fetches all active logged-in devices for the given shop
     */
    suspend fun getActiveSessions(shopId: String): List<DeviceSession>

    /**
     * Remotely revokes/invalidates a device's session
     */
    suspend fun revokeSession(shopId: String, sessionId: String): Boolean

    /**
     * Verifies if the local device's session is still valid or has been revoked by the Admin
     */
    suspend fun isCurrentSessionValid(shopId: String, currentSessionId: String): Boolean
}
