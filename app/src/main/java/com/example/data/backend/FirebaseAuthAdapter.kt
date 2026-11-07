package com.example.data.backend

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Authentication Implementation of IAuthProvider
 * Preserves 100% of existing Gmail, Google Sign-In, and Password accounts on Firebase Free Tier.
 */
class FirebaseAuthAdapter(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : IAuthProvider {

    override val providerType: AuthProviderType = AuthProviderType.FIREBASE

    private val _currentUserState = MutableStateFlow<BackendUser?>(mapFirebaseUser(auth.currentUser))
    override val currentUserState: StateFlow<BackendUser?> = _currentUserState.asStateFlow()

    init {
        auth.addAuthStateListener { fAuth ->
            _currentUserState.value = mapFirebaseUser(fAuth.currentUser)
        }
    }

    override fun getCurrentUser(): BackendUser? {
        return mapFirebaseUser(auth.currentUser)
    }

    override suspend fun signInWithEmail(email: String, password: String): BackendAuthResult {
        return try {
            val res = auth.signInWithEmailAndPassword(email.trim().lowercase(), password.trim()).await()
            val user = res.user ?: return BackendAuthResult.Error(
                errorCode = "NO_USER",
                messageBn = "লগইন সেশন তৈরি সম্ভব হয়নি।",
                messageEn = "Null user from Firebase Auth"
            )
            BackendAuthResult.Success(
                user = mapFirebaseUser(user)!!,
                token = user.getIdToken(false).await()?.token,
                message = "জিমেইল লগইন সফল হয়েছে।"
            )
        } catch (e: Exception) {
            BackendAuthResult.Error(
                errorCode = "FIREBASE_AUTH_FAILED",
                messageBn = "ইমেইল বা পাসওয়ার্ড ভুল হয়েছে!",
                messageEn = e.message ?: "Authentication failed"
            )
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): BackendAuthResult {
        return try {
            val res = auth.createUserWithEmailAndPassword(email.trim().lowercase(), password.trim()).await()
            val user = res.user ?: return BackendAuthResult.Error(
                errorCode = "SIGNUP_FAILED",
                messageBn = "অ্যাকাউন্ট তৈরি সম্ভব হয়নি।",
                messageEn = "Null user on create"
            )
            BackendAuthResult.Success(
                user = mapFirebaseUser(user)!!,
                token = user.getIdToken(false).await()?.token,
                message = "রেজিস্ট্রেশন সফল হয়েছে।"
            )
        } catch (e: Exception) {
            BackendAuthResult.Error(
                errorCode = "SIGNUP_FAILED",
                messageBn = "রেজিস্ট্রেশন ত্রুটি: ${e.localizedMessage ?: "ব্যর্থ হয়েছে"}",
                messageEn = e.message ?: "Signup error"
            )
        }
    }

    override suspend fun signInWithPhone(phone: String, passwordOrPin: String): BackendAuthResult {
        return BackendAuthResult.Error(
            errorCode = "NOT_SUPPORTED_ON_FIREBASE_FREE",
            messageBn = "ফায়ারবেস ফ্রি প্ল্যানে পাসওয়ার্ড-ভিত্তিক ফোন লগইন সমর্থিত নয়। জিমেইল বা Supabase ব্যবহার করুন।",
            messageEn = "Phone+Password is not supported natively on Firebase Spark"
        )
    }

    override suspend fun signUpWithPhone(phone: String, passwordOrPin: String): BackendAuthResult {
        return BackendAuthResult.Error(
            errorCode = "NOT_SUPPORTED_ON_FIREBASE_FREE",
            messageBn = "ফায়ারবেস ফ্রি প্ল্যানে এসএমএস ওটিপি ছাড়া সরাসরি ফোন রেজিস্ট্রেশন অনুপলব্ধ।",
            messageEn = "Phone signup requires SMS or external backend"
        )
    }

    override suspend fun sendPasswordReset(email: String): BackendAuthResult {
        return try {
            auth.sendPasswordResetEmail(email.trim().lowercase()).await()
            BackendAuthResult.Success(
                user = BackendUser(id = "anonymous", email = email),
                message = "পাসওয়ার্ড রিসেট লিংক আপনার ইমেইলে পাঠানো হয়েছে।"
            )
        } catch (e: Exception) {
            BackendAuthResult.Error(
                errorCode = "RESET_FAILED",
                messageBn = "পাসওয়ার্ড রিসেট ব্যর্থ হয়েছে: ${e.localizedMessage ?: "সার্ভার ত্রুটি"}",
                messageEn = e.message ?: "Reset error"
            )
        }
    }

    override suspend fun linkAccounts(
        primaryUser: BackendUser,
        secondaryCredentials: Map<String, String>
    ): BackendAuthResult {
        // Safe mapping handled via HybridAccountManager
        return BackendAuthResult.Success(primaryUser, message = "অ্যাকাউন্ট ম্যাপিং সমর্থিত")
    }

    override suspend fun signOut() {
        auth.signOut()
        _currentUserState.value = null
    }

    private fun mapFirebaseUser(user: com.google.firebase.auth.FirebaseUser?): BackendUser? {
        if (user == null) return null
        return BackendUser(
            id = user.uid,
            email = user.email,
            phone = user.phoneNumber,
            displayName = user.displayName,
            isEmailVerified = user.isEmailVerified,
            isPhoneVerified = !user.phoneNumber.isNullOrBlank(),
            primaryProvider = AuthProviderType.FIREBASE,
            linkedFirebaseUid = user.uid
        )
    }
}
