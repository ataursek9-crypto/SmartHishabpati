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
            msg.contains("network error", ignoreCase = true) || msg.contains("failed to connect", ignoreCase = true)
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

        // 4. Firebase Authentication: Wrong Password / Invalid Credentials
        if (error is FirebaseAuthInvalidCredentialsException ||
            errCode == "ERROR_WRONG_PASSWORD" ||
            errCode == "ERROR_INVALID_CREDENTIAL" ||
            msg.contains("password is invalid", ignoreCase = true) ||
            msg.contains("wrong password", ignoreCase = true)
        ) {
            return AppError(
                problemBn = "পাসওয়ার্ড সঠিক নয়।",
                whatToDoBn = "আবার চেষ্টা করুন অথবা পাসওয়ার্ড পরিবর্তন (Forgot Password) করুন।",
                problemEn = "The password is incorrect.",
                whatToDoEn = "Please try again or reset your password.",
                rawErrorCode = "WRONG_PASSWORD"
            )
        }

        // 5. Firebase Authentication: User Not Found
        if (error is FirebaseAuthInvalidUserException || errCode == "ERROR_USER_NOT_FOUND") {
            return AppError(
                problemBn = "এই ইমেইল বা নম্বরে কোনো অ্যাকাউন্ট খুঁজে পাওয়া যায়নি।",
                whatToDoBn = "আপনার ইমেইল বা নম্বরটি সঠিক কিনা যাচাই করুন অথবা নতুন দোকান নিবন্ধন করুন।",
                problemEn = "No account found for this email or phone number.",
                whatToDoEn = "Verify your credentials or create a new shop account.",
                rawErrorCode = "USER_NOT_FOUND"
            )
        }

        // 6. User Collision (Email or Phone already in use)
        if (error is FirebaseAuthUserCollisionException || errCode == "ERROR_EMAIL_ALREADY_IN_USE") {
            return AppError(
                problemBn = "এই ইমেইল বা নম্বরটি দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট নিবন্ধিত আছে।",
                whatToDoBn = "আপনার আগের পাসওয়ার্ড দিয়ে লগইন করুন অথবা পাসওয়ার্ড রিসেট করুন।",
                problemEn = "An account already exists with this email or phone number.",
                whatToDoEn = "Log in using your existing password or reset it.",
                rawErrorCode = "ACCOUNT_EXISTS"
            )
        }

        // 7. Rate Limit / Too Many Requests
        if (error is FirebaseTooManyRequestsException || errCode == "ERROR_TOO_MANY_REQUESTS" || msg.contains("blocked all requests", ignoreCase = true)) {
            return AppError(
                problemBn = "অতিরিক্ত ভুল চেষ্টার কারণে সাময়িকভাবে সেবা স্থগিত রাখা হয়েছে।",
                whatToDoBn = "অনুগ্রহ করে কিছুক্ষণ অপেক্ষা করে আবার চেষ্টা করুন অথবা বিকল্প মাধ্যমে সাইন ইন করুন।",
                problemEn = "Temporarily blocked due to too many failed attempts.",
                whatToDoEn = "Please wait a few moments before trying again or use an alternative login method.",
                rawErrorCode = "TOO_MANY_REQUESTS"
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
