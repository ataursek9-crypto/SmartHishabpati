package com.example.util

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class AppError(
    val problemBn: String,
    val whatToDoBn: String,
    val problemEn: String,
    val whatToDoEn: String,
    val rawErrorCode: String? = null
) {
    fun getProblem(): String = if (AppLanguageManager.isEnglish()) problemEn else problemBn
    fun getWhatToDo(): String = if (AppLanguageManager.isEnglish()) whatToDoEn else whatToDoBn

    fun toUserMessage(): String {
        val problem = getProblem()
        val whatToDo = getWhatToDo()
        return if (whatToDo.isNotBlank()) {
            val pPrefix = if (AppLanguageManager.isEnglish()) "Problem: " else "সমস্যা: "
            val aPrefix = if (AppLanguageManager.isEnglish()) "What to do: " else "করণীয়: "
            "$pPrefix$problem\n$aPrefix$whatToDo"
        } else {
            problem
        }
    }
}

/**
 * Centralized, Language-Aware Error Handling and Diagnostics Engine.
 * Translates technical exceptions into polite, clear Problem + Action guidance
 * for both Bengali and English, while logging sanitized technical details for debugging.
 */
object AppErrorHandler {
    private const val TAG = "AppErrorHandler"

    /**
     * Maps any throwable or error string into an actionable bilingual AppError.
     */
    fun map(error: Throwable?): AppError {
        if (error == null) {
            return AppError(
                problemBn = "একটি অনির্দিষ্ট ত্রুটি ঘটেছে।",
                whatToDoBn = "অনুগ্রহ করে কিছুক্ষণ পর আবার চেষ্টা করুন।",
                problemEn = "An unexpected error occurred.",
                whatToDoEn = "Please try again in a moment."
            )
        }

        // Secure technical log (sanitized)
        val sanitizedMsg = sanitize(error.message ?: error.toString())
        try {
            Log.e(TAG, "[TechnicalLog] Exception: ${error.javaClass.simpleName}, Message: $sanitizedMsg")
        } catch (_: Throwable) {
            // Safe fallback when running on host JVM test without android.util.Log stub
        }

        val msg = error.message.orEmpty()
        val errCode = if (error is FirebaseAuthException) error.errorCode else ""

        // 1. Firebase Initialization Error
        if (msg.contains("Default FirebaseApp is not initialized", ignoreCase = true) ||
            msg.contains("FirebaseApp.initializeApp", ignoreCase = true) ||
            (error is IllegalStateException && msg.contains("FirebaseApp", ignoreCase = true))
        ) {
            return AppError(
                problemBn = "অ্যাপের সার্ভার সংযোগ চালু করা যায়নি।",
                whatToDoBn = "অনুগ্রহ করে অ্যাপটি আপডেট করুন অথবা সাপোর্টে যোগাযোগ করুন।",
                problemEn = "The app could not initialize its server connection.",
                whatToDoEn = "Please update the app or contact support.",
                rawErrorCode = "FIREBASE_NOT_INITIALIZED"
            )
        }

        // 2. Network & Internet Unavailable
        if (error is UnknownHostException || error is FirebaseNetworkException ||
            msg.contains("network error", ignoreCase = true) ||
            msg.contains("network-request-failed", ignoreCase = true) ||
            msg.contains("failed to connect", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না।",
                whatToDoBn = "মোবাইল ডাটা অথবা Wi-Fi চালু করে আবার চেষ্টা করুন।",
                problemEn = "No internet connection.",
                whatToDoEn = "Turn on mobile data or Wi-Fi and try again.",
                rawErrorCode = "NETWORK_UNAVAILABLE"
            )
        }

        // 3. Network Socket Timeout
        if (error is SocketTimeoutException || msg.contains("timeout", ignoreCase = true)) {
            return AppError(
                problemBn = "সার্ভারের সঙ্গে সংযোগের সময় শেষ হয়ে গেছে।",
                whatToDoBn = "দুর্বল ইন্টারনেটের কারণে এটি হতে পারে। ইন্টারনেট সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন।",
                problemEn = "Connection to server timed out.",
                whatToDoEn = "This may be due to a slow connection. Check your network and retry.",
                rawErrorCode = "TIMEOUT"
            )
        }

        // 4a. Firebase Authentication: Account exists with different credential (e.g. Google user attempting password login)
        if (errCode == "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ||
            msg.contains("account-exists-with-different-credential", ignoreCase = true) ||
            msg.contains("different credential", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "এই ইমেইলটি অন্য একটি সাইন-ইন পদ্ধতির (যেমন Google) সাথে যুক্ত।",
                whatToDoBn = "অনুগ্রহ করে নিচে 'Google দিয়ে প্রবেশ করুন' বোতাম ব্যবহার করে লগইন করুন।",
                problemEn = "An account already exists with the same email address but different sign-in credentials.",
                whatToDoEn = "Please sign in using the 'Continue with Google' button below.",
                rawErrorCode = "ACCOUNT_EXISTS_DIFFERENT_CREDENTIAL"
            )
        }

        // 4b. Firebase Authentication: Invalid Email Address Format (checked before generic credentials)
        if (errCode == "ERROR_INVALID_EMAIL" ||
            msg.contains("badly formatted", ignoreCase = true) ||
            msg.contains("invalid email", ignoreCase = true) ||
            msg.contains("invalid-email", ignoreCase = true) ||
            msg.contains("invalid_email", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "ইমেইল ঠিকানাটির ফরম্যাট সঠিক নয়।",
                whatToDoBn = "অনুগ্রহ করে সঠিক ফরম্যাটের জিমেইল লিখুন (যেমন: user@gmail.com)।",
                problemEn = "The email address is badly formatted.",
                whatToDoEn = "Please enter a valid Gmail address (e.g. user@gmail.com).",
                rawErrorCode = "INVALID_EMAIL"
            )
        }

        // 4c. Firebase Authentication: User Not Found
        if (error is FirebaseAuthInvalidUserException ||
            errCode == "ERROR_USER_NOT_FOUND" ||
            msg.contains("user not found", ignoreCase = true) ||
            msg.contains("user-not-found", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "এই ইমেইল বা নম্বরে কোনো অ্যাকাউন্ট খুঁজে পাওয়া যায়নি।",
                whatToDoBn = "আপনার ইমেইল বা নম্বরটি সঠিক কিনা যাচাই করুন অথবা নতুন দোকান নিবন্ধন করুন।",
                problemEn = "No account found for this email or phone number.",
                whatToDoEn = "Verify your credentials or create a new shop account.",
                rawErrorCode = "USER_NOT_FOUND"
            )
        }

        // 4d. Firebase Authentication: Specific Wrong Password
        if (errCode == "ERROR_WRONG_PASSWORD" ||
            msg.contains("wrong-password", ignoreCase = true) ||
            msg.contains("password is invalid", ignoreCase = true) ||
            msg.contains("wrong password", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "পাসওয়ার্ড সঠিক নয়।",
                whatToDoBn = "পাসওয়ার্ড যাচাই করে আবার চেষ্টা করুন, অথবা পাসওয়ার্ড ভুলে গেলে নিচে 'পাসওয়ার্ড ভুলে গেছেন?' ব্যবহার করুন।",
                problemEn = "The password is incorrect.",
                whatToDoEn = "Please try again or use Forgot Password to reset.",
                rawErrorCode = "WRONG_PASSWORD"
            )
        }

        // 4e. Firebase Authentication: Invalid Credentials (covers wrong password OR Google-only account without password)
        if (error is FirebaseAuthInvalidCredentialsException ||
            errCode == "ERROR_INVALID_CREDENTIAL" ||
            msg.contains("invalid-credential", ignoreCase = true) ||
            msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "প্রদত্ত পাসওয়ার্ড সঠিক নয়, অথবা অ্যাকাউন্টটি Google দিয়ে তৈরি।",
                whatToDoBn = "পাসওয়ার্ড যাচাই করে পুনরায় চেষ্টা করুন, অথবা নিচে 'Google দিয়ে প্রবেশ করুন' বোতামে চাপ দিন। পাসওয়ার্ড মনে না থাকলে 'পাসওয়ার্ড ভুলে গেছেন?' করুন।",
                problemEn = "Invalid password entered, or this account was created using Google Sign-In.",
                whatToDoEn = "Verify your password, sign in directly with Google, or use Forgot Password to reset.",
                rawErrorCode = "INVALID_CREDENTIAL"
            )
        }

        // 5. User Collision (Email or Phone already in use)
        if (error is FirebaseAuthUserCollisionException ||
            errCode == "ERROR_EMAIL_ALREADY_IN_USE" ||
            msg.contains("email-already-in-use", ignoreCase = true) ||
            msg.contains("EMAIL_EXISTS", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "এই ইমেইল বা নম্বরটি দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট নিবন্ধিত আছে।",
                whatToDoBn = "আপনার অ্যাকাউন্টে লগইন করুন, অথবা Google দিয়ে যাচাই করে এই অ্যাকাউন্টে নতুন দোকান যুক্ত করুন।",
                problemEn = "An account already exists with this email or phone number.",
                whatToDoEn = "Log in to your account, or verify with Google to add a new shop to this account.",
                rawErrorCode = "ACCOUNT_EXISTS"
            )
        }

        // 7. Rate Limit / Too Many Requests
        if (error is FirebaseTooManyRequestsException ||
            errCode == "ERROR_TOO_MANY_REQUESTS" ||
            msg.contains("too-many-requests", ignoreCase = true) ||
            msg.contains("blocked all requests", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "অতিরিক্ত অনুরোধের কারণে সাময়িকভাবে সেবা স্থগিত রাখা হয়েছে।",
                whatToDoBn = "অনুগ্রহ করে কিছুক্ষণ অপেক্ষা করে আবার চেষ্টা করুন অথবা Google সাইন-ইন ব্যবহার করুন।",
                problemEn = "Temporarily blocked due to too many failed attempts or SMS quota reached.",
                whatToDoEn = "Please wait a few moments before trying again or use Google Sign-In.",
                rawErrorCode = "TOO_MANY_REQUESTS"
            )
        }

        // 7b. Phone Provider Disabled or SMS Region Policy Blocked (+880 Bangladesh)
        if (errCode == "ERROR_OPERATION_NOT_ALLOWED" ||
            msg.contains("operation is not allowed", ignoreCase = true) ||
            msg.contains("sign-in provider is disabled", ignoreCase = true) ||
            msg.contains("SMS unable to be sent", ignoreCase = true) ||
            msg.contains("region enabled", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "Firebase Phone Authentication বন্ধ রয়েছে অথবা বাংলাদেশে (+880) SMS পাঠানোর অনুমতি (SMS Region Policy) নেই।",
                whatToDoBn = "১. SMS ঝামেলা ছাড়া 'Google দিয়ে প্রবেশ করুন' ব্যবহার করুন (প্রস্তাবিত)।\n২. অথবা Firebase Console > Authentication > Settings > 'SMS Region Policy'-তে Bangladesh (+880) Allow করুন এবং Blaze বিলিং সক্রিয় করুন।",
                problemEn = "Firebase Phone Auth is disabled or SMS Region Policy blocks Bangladesh (+880).",
                whatToDoEn = "1. Use 'Sign in with Google' for instant access without SMS dependencies.\n2. In Firebase Console > Authentication > Settings > SMS Region Policy, enable Bangladesh (+880) and check billing.",
                rawErrorCode = "OPERATION_NOT_ALLOWED"
            )
        }

        // 7c. App Verification Failed (SafetyNet / Play Integrity / SHA Fingerprint)
        if (msg.contains("app not authorized", ignoreCase = true) ||
            msg.contains("SHA-1", ignoreCase = true) ||
            msg.contains("app verification failed", ignoreCase = true) ||
            msg.contains("reCAPTCHA", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "ডিভাইসের নিরাপত্তা যাচাইকরণ (Play Integrity / SHA-1 / reCAPTCHA) ব্যর্থ হয়েছে।",
                whatToDoBn = "Firebase কনসোলে আপনার Android অ্যাপের বর্তমান debug.keystore SHA-1 (AE:D3:D1:F1:5F:C6:78:C4:E0:23:4D:2A:25:7F:8B:4E:2C:97:75:9E) এবং SHA-256 ফিঙ্গারপ্রিন্ট যুক্ত করুন অথবা Google সাইন-ইন ব্যবহার করুন।",
                problemEn = "Device security check (Play Integrity / SHA-1 / reCAPTCHA) failed.",
                whatToDoEn = "Add current debug.keystore SHA-1 (AE:D3:D1:F1:5F:C6:78:C4:E0:23:4D:2A:25:7F:8B:4E:2C:97:75:9E) in Firebase Console or use Google Sign-In.",
                rawErrorCode = "APP_NOT_VERIFIED"
            )
        }

        // 8. Firestore Permission Denied
        if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            msg.contains("PERMISSION_DENIED", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "এই কাজটি করার অনুমতি আপনার নেই।",
                whatToDoBn = "প্রয়োজনে দোকানের প্রধান মালিকের সঙ্গে যোগাযোগ করুন।",
                problemEn = "You do not have permission to perform this action.",
                whatToDoEn = "Please contact the shop owner if needed.",
                rawErrorCode = "PERMISSION_DENIED"
            )
        }

        // 9. Phone OTP Invalid or Expired
        if (msg.contains("OTP", ignoreCase = true) && (msg.contains("invalid", ignoreCase = true) || msg.contains("expired", ignoreCase = true))) {
            return AppError(
                problemBn = "প্রদত্ত ওটিপি (OTP) কোডটি সঠিক নয় অথবা এর মেয়াদ শেষ হয়ে গেছে।",
                whatToDoBn = "অনুগ্রহ করে মোবাইলে আসা ৬ সংখ্যার সঠিক কোড দিন অথবা আবার কোড পাঠান।",
                problemEn = "The OTP code entered is incorrect or expired.",
                whatToDoEn = "Please enter the correct 6-digit code or request a new OTP.",
                rawErrorCode = "INVALID_OTP"
            )
        }

        // 10. General Fallback
        return AppError(
            problemBn = "সার্ভারের সঙ্গে সংযোগ করা যাচ্ছে না।",
            whatToDoBn = "কিছুক্ষণ পরে আবার চেষ্টা করুন অথবা ইন্টারনেট পরীক্ষা করুন।",
            problemEn = "Unable to connect to the server.",
            whatToDoEn = "Please try again later or check your internet connection.",
            rawErrorCode = "GENERAL_ERROR"
        )
    }

    enum class EmailAuthProviderType {
        GOOGLE_ONLY,
        PASSWORD_ONLY,
        GOOGLE_AND_PASSWORD,
        NOT_FOUND,
        UNKNOWN
    }

    /**
     * Maps Login errors with audited provider context so Google-only accounts are never
     * misled with "Wrong Password" errors.
     */
    fun mapLoginError(error: Throwable?, auditedProviderType: EmailAuthProviderType): AppError {
        val baseError = map(error)
        if (baseError.rawErrorCode == "NETWORK_UNAVAILABLE" ||
            baseError.rawErrorCode == "TIMEOUT" ||
            baseError.rawErrorCode == "TOO_MANY_REQUESTS" ||
            baseError.rawErrorCode == "INVALID_EMAIL" ||
            baseError.rawErrorCode == "ACCOUNT_EXISTS_DIFFERENT_CREDENTIAL"
        ) {
            return baseError
        }

        return when (auditedProviderType) {
            EmailAuthProviderType.GOOGLE_ONLY -> AppError(
                problemBn = "এই Gmail অ্যাকাউন্টটি Google Sign-In দিয়ে তৈরি—এতে কোনো পাসওয়ার্ড যুক্ত নেই।",
                whatToDoBn = "পাসওয়ার্ড দিয়ে লগইন করার চেষ্টা না করে নিচের 'Google দিয়ে প্রবেশ করুন' বোতামে ট্যাপ করুন।",
                problemEn = "This Gmail account was created with Google Sign-In and has no password.",
                whatToDoEn = "Please tap 'Continue with Google' below to sign in directly.",
                rawErrorCode = "GOOGLE_ONLY_ACCOUNT"
            )
            EmailAuthProviderType.NOT_FOUND -> AppError(
                problemBn = "এই ইমেইলে কোনো অ্যাকাউন্ট খুঁজে পাওয়া যায়নি।",
                whatToDoBn = "আপনার ইমেইল ঠিকানা সঠিক কিনা যাচাই করুন অথবা 'নতুন অ্যাকাউন্ট তৈরি করুন' থেকে দোকান নিবন্ধন করুন।",
                problemEn = "No account found for this email address.",
                whatToDoEn = "Verify your email address or create a new shop account.",
                rawErrorCode = "USER_NOT_FOUND"
            )
            EmailAuthProviderType.PASSWORD_ONLY -> AppError(
                problemBn = "পাসওয়ার্ড সঠিক নয়।",
                whatToDoBn = "সঠিক পাসওয়ার্ড বা পিন দিয়ে আবার চেষ্টা করুন, অথবা নিচে 'পাসওয়ার্ড ভুলে গেছেন? (Forgot Password)' ব্যবহার করুন।",
                problemEn = "The password is incorrect.",
                whatToDoEn = "Please check your password/PIN and try again, or use Forgot Password to reset.",
                rawErrorCode = if (baseError.rawErrorCode == "INVALID_CREDENTIAL") "INVALID_CREDENTIAL" else "WRONG_PASSWORD"
            )
            EmailAuthProviderType.GOOGLE_AND_PASSWORD -> AppError(
                problemBn = "পাসওয়ার্ড সঠিক নয়।",
                whatToDoBn = "সঠিক পাসওয়ার্ড দিয়ে চেষ্টা করুন, 'পাসওয়ার্ড ভুলে গেছেন?' ব্যবহার করুন, অথবা সরাসরি 'Google দিয়ে প্রবেশ করুন' বোতামে চাপ দিন।",
                problemEn = "The password is incorrect.",
                whatToDoEn = "Try again with the correct password, use Forgot Password, or sign in directly with Google.",
                rawErrorCode = if (baseError.rawErrorCode == "INVALID_CREDENTIAL") "INVALID_CREDENTIAL" else "WRONG_PASSWORD"
            )
            EmailAuthProviderType.UNKNOWN -> baseError
        }
    }

    /**
     * Dedicated error mapper for Password Reset requests.
     * Prevents misleading "Wrong Password" errors when requesting a password reset.
     */
    fun mapPasswordResetError(error: Throwable?): AppError {
        if (error == null) {
            return AppError(
                problemBn = "পাসওয়ার্ড রিসেট অনুরোধ প্রক্রিয়াধীন।",
                whatToDoBn = "অনুগ্রহ করে আপনার ইনবক্স ও স্প্যাম ফোল্ডার পরীক্ষা করুন।",
                problemEn = "Password reset request is processing.",
                whatToDoEn = "Please check your inbox and spam folders.",
                rawErrorCode = "SUCCESS"
            )
        }
        val msg = error.message.orEmpty()
        val errCode = if (error is FirebaseAuthException) error.errorCode else ""

        if (error is UnknownHostException || error is FirebaseNetworkException ||
            msg.contains("network error", ignoreCase = true) ||
            msg.contains("network-request-failed", ignoreCase = true) ||
            msg.contains("failed to connect", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না।",
                whatToDoBn = "মোবাইল ডাটা অথবা Wi-Fi চালু করে আবার চেষ্টা করুন।",
                problemEn = "No internet connection.",
                whatToDoEn = "Turn on mobile data or Wi-Fi and try again.",
                rawErrorCode = "NETWORK_UNAVAILABLE"
            )
        }

        if (error is SocketTimeoutException || msg.contains("timeout", ignoreCase = true)) {
            return AppError(
                problemBn = "সার্ভারের সঙ্গে সংযোগের সময় শেষ হয়ে গেছে।",
                whatToDoBn = "ইন্টারনেট সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন, অথবা নিচে 'Google দিয়ে সরাসরি প্রবেশ করুন' ব্যবহার করুন।",
                problemEn = "Connection to server timed out.",
                whatToDoEn = "Check your internet connection and retry, or tap Sign In with Google below.",
                rawErrorCode = "TIMEOUT"
            )
        }

        if (errCode == "ERROR_INVALID_EMAIL" ||
            msg.contains("badly formatted", ignoreCase = true) ||
            msg.contains("invalid email", ignoreCase = true) ||
            msg.contains("invalid-email", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "ইমেইল ঠিকানাটির ফরম্যাট সঠিক নয়।",
                whatToDoBn = "অনুগ্রহ করে সঠিক জিমেইল ঠিকানা লিখুন (যেমন: user@gmail.com)।",
                problemEn = "The email address is badly formatted.",
                whatToDoEn = "Please enter a valid Gmail address (e.g. user@gmail.com).",
                rawErrorCode = "INVALID_EMAIL"
            )
        }

        if (error is FirebaseAuthInvalidUserException ||
            errCode == "ERROR_USER_NOT_FOUND" ||
            msg.contains("user-not-found", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "এই ইমেইলে কোনো নিবন্ধিত অ্যাকাউন্ট পাওয়া যায়নি।",
                whatToDoBn = "ইমেইলটি সঠিক কিনা যাচাই করুন অথবা নতুন দোকান নিবন্ধন করুন।",
                problemEn = "No account found for this email address.",
                whatToDoEn = "Please verify the email address or register a new shop.",
                rawErrorCode = "USER_NOT_FOUND"
            )
        }

        if (error is FirebaseTooManyRequestsException ||
            errCode == "ERROR_TOO_MANY_REQUESTS" ||
            msg.contains("too-many-requests", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "অতিরিক্ত অনুরোধের কারণে সাময়িকভাবে সেবা স্থগিত রাখা হয়েছে।",
                whatToDoBn = "অনুগ্রহ করে কিছুক্ষণ অপেক্ষা করে আবার চেষ্টা করুন অথবা Google দিয়ে সরাসরি প্রবেশ করুন।",
                problemEn = "Temporarily blocked due to too many reset requests.",
                whatToDoEn = "Please wait a few moments before trying again or sign in with Google.",
                rawErrorCode = "TOO_MANY_REQUESTS"
            )
        }

        return AppError(
            problemBn = "পাসওয়ার্ড রিসেট লিংক পাঠানোর অনুরোধ ব্যর্থ হয়েছে।",
            whatToDoBn = "ইন্টারনেট সংযোগ পরীক্ষা করুন অথবা Google দিয়ে সরাসরি প্রবেশ করুন।",
            problemEn = "Failed to send password reset link.",
            whatToDoEn = "Check your internet connection or sign in directly with Google.",
            rawErrorCode = "RESET_FAILED"
        )
    }

    /**
     * Sanitizes strings before logging to ensure zero leak of credentials, tokens, or PINs.
     */
    fun sanitize(message: String): String {
        return message
            .replace(Regex("(?i)password\\s*=\\s*[^\\s,;]+"), "password=***")
            .replace(Regex("(?i)pin\\s*=\\s*[^\\s,;]+"), "pin=***")
            .replace(Regex("(?i)token\\s*=\\s*[^\\s,;]+"), "token=***")
            .replace(Regex("(?i)secret\\s*=\\s*[^\\s,;]+"), "secret=***")
            .replace(Regex("\\b[0-9]{6}\\b"), "******") // hide potential OTPs
    }

    /**
     * Convenience mapper for custom string errors.
     */
    fun fromMessage(customMsg: String): AppError {
        return map(RuntimeException(customMsg))
    }
}
