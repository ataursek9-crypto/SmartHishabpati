package com.example.util

import android.app.Activity
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/**
 * Production-grade Firebase Phone Authentication Helper.
 * Handles Bangladesh (+880) phone number normalization, SMS OTP dispatch,
 * auto-retrieval, code verification, and user authentication state management.
 */
object PhoneAuthHelper {
    private const val TAG = "PhoneAuthHelper"

    /**
     * Normalizes a raw phone number to international E.164 format for Bangladesh (+880).
     * Converts Bengali digits (০-৯) to ASCII digits (0-9).
     */
    fun normalizeToE164(raw: String): String {
        val converted = raw.trim()
            .replace('০', '0').replace('১', '1').replace('২', '2')
            .replace('৩', '3').replace('৪', '4').replace('৫', '5')
            .replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')
        val digits = converted.replace(Regex("[^0-9+]"), "").trim()

        return when {
            digits.startsWith("+880") -> digits
            digits.startsWith("880") -> "+$digits"
            digits.startsWith("+88") && digits.length > 13 -> digits
            digits.startsWith("01") && digits.length == 11 -> "+88$digits"
            digits.startsWith("1") && digits.length == 10 -> "+880$digits"
            digits.startsWith("+") -> digits
            else -> "+880$digits"
        }
    }

    /**
     * Checks if a phone number is a valid 11-digit Bangladeshi mobile number.
     */
    fun isValidBangladeshPhone(raw: String): Boolean {
        val e164 = normalizeToE164(raw)
        // Must be in format +8801[3-9]XXXXXXXX (total 14 characters)
        val regex = Regex("^\\+8801[3-9]\\d{8}$")
        return regex.matches(e164)
    }

    private var lastOtpSentTimestamp: Long = 0L
    private var lastSentPhone: String = ""
    const val OTP_COOLDOWN_SECONDS: Long = 60L

    /**
     * Checks if cooldown period has passed to avoid burning Firebase SMS quota.
     */
    fun getRemainingCooldownSeconds(phone: String): Long {
        val e164 = normalizeToE164(phone)
        if (e164 != lastSentPhone) return 0L
        val elapsed = (System.currentTimeMillis() - lastOtpSentTimestamp) / 1000L
        return if (elapsed < OTP_COOLDOWN_SECONDS) (OTP_COOLDOWN_SECONDS - elapsed) else 0L
    }

    /**
     * Sends Firebase SMS OTP to the given phone number with quota protection.
     */
    fun sendVerificationCode(
        activity: Activity,
        phone: String,
        forceResendingToken: PhoneAuthProvider.ForceResendingToken? = null,
        onCodeSent: (verificationId: String, token: PhoneAuthProvider.ForceResendingToken) -> Unit,
        onAutoVerified: (user: FirebaseUser) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        val e164Phone = normalizeToE164(phone)
        if (!isValidBangladeshPhone(e164Phone)) {
            val invalidMsg = if (AppLanguageManager.isEnglish()) {
                "Problem: Invalid phone number.\nWhat to do: Please enter a valid 11-digit Bangladeshi mobile number (e.g. 017XXXXXXXX)."
            } else {
                "সমস্যা: মোবাইল নম্বরটি সঠিক নয়।\nকরণীয়: সঠিক ১১ ডিজিটের বাংলাদেশী মোবাইল নম্বর দিন (যেমন: 017XXXXXXXX)।"
            }
            onError(invalidMsg)
            return
        }

        // Quota Protection: Prevent rapid consecutive requests from exhausting project SMS quota
        val remainingCooldown = getRemainingCooldownSeconds(e164Phone)
        if (remainingCooldown > 0) {
            val waitMsg = if (AppLanguageManager.isEnglish()) {
                "Problem: Please wait before requesting another OTP.\nWhat to do: Try again in $remainingCooldown seconds."
            } else {
                "সমস্যা: অতিরিক্ত ওটিপি অনুরোধ রোধে সাময়িক অপেক্ষা প্রয়োজন।\nকরণীয়: অনুগ্রহ করে আর $remainingCooldown সেকেন্ড পর পুনরায় চেষ্টা করুন।"
            }
            onError(waitMsg)
            return
        }

        // Record cooldown state immediately
        lastSentPhone = e164Phone
        lastOtpSentTimestamp = System.currentTimeMillis()

        try {
            FirebaseInitHelper.ensureFirebaseInitialized(activity)
            val auth = FirebaseAuth.getInstance()
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    Log.i(TAG, "Phone auto-verification completed directly via SMS auto-retrieval.")
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { authResult ->
                            val user = authResult.user
                            if (user != null) {
                                onAutoVerified(user)
                            } else {
                                onError(AppErrorHandler.map(null).toUserMessage())
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Auto-verification sign-in failed: ${e.message}")
                            onError(translateFirebaseError(e))
                        }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e(TAG, "Phone verification failed: ${e.message}", e)
                    onError(translateFirebaseError(e))
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    Log.i(TAG, "Verification code sent to $e164Phone with verificationId: $verificationId")
                    onCodeSent(verificationId, token)
                }
            }

            val builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(e164Phone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)

            if (forceResendingToken != null) {
                builder.setForceResendingToken(forceResendingToken)
            }

            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Exception during sendVerificationCode: ${e.message}", e)
            onError(translateFirebaseError(e))
        }
    }

    /**
     * Verifies the 6-digit SMS OTP entered by the user and signs in to Firebase.
     */
    fun verifyCodeAndSignIn(
        verificationId: String,
        code: String,
        activity: Activity? = null,
        onSuccess: (user: FirebaseUser) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        val cleanCode = code.trim()
            .replace('০', '0').replace('১', '1').replace('২', '2')
            .replace('৩', '3').replace('৪', '4').replace('৫', '5')
            .replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')

        if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) {
            val invalidMsg = if (AppLanguageManager.isEnglish()) {
                "Problem: Invalid OTP.\nWhat to do: Please enter the exact 6-digit code received via SMS."
            } else {
                "সমস্যা: ওটিপি সঠিক নয়।\nকরণীয়: অনুগ্রহ করে এসএমএসে আসা ৬ সংখ্যার সঠিক কোড লিখুন।"
            }
            onError(invalidMsg)
            return
        }

        try {
            activity?.let { FirebaseInitHelper.ensureFirebaseInitialized(it) }
            val credential = PhoneAuthProvider.getCredential(verificationId, cleanCode)
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        Log.i(TAG, "Successfully authenticated Phone user: ${user.uid}, phone: ${user.phoneNumber}")
                        onSuccess(user)
                    } else {
                        onError(AppErrorHandler.map(null).toUserMessage())
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OTP verification sign-in failed: ${e.message}", e)
                    onError(translateFirebaseError(e))
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during verifyCodeAndSignIn: ${e.message}", e)
            onError(translateFirebaseError(e))
        }
    }

    /**
     * Checks if the error corresponds to Firebase Phone Provider disabled or SMS region policy restrictions.
     */
    fun isProviderOrSmsRegionDisabled(e: Throwable): Boolean {
        val msg = e.localizedMessage ?: e.message ?: ""
        val errCode = if (e is FirebaseAuthException) e.errorCode else ""
        return isProviderOrSmsRegionDisabledMessage(msg) || errCode == "ERROR_OPERATION_NOT_ALLOWED"
    }

    fun isProviderOrSmsRegionDisabledMessage(msg: String): Boolean {
        return msg.contains("operation is not allowed", ignoreCase = true) ||
                msg.contains("sign-in provider is disabled", ignoreCase = true) ||
                msg.contains("SMS unable to be sent", ignoreCase = true) ||
                msg.contains("region enabled", ignoreCase = true) ||
                msg.contains("SMS Region Policy", ignoreCase = true) ||
                msg.contains("billing", ignoreCase = true) ||
                msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true)
    }

    /**
     * Translates Firebase Auth exceptions into actionable bilingual messages.
     */
    fun translateFirebaseError(e: Throwable): String {
        val isEn = AppLanguageManager.isEnglish()
        val msg = e.localizedMessage ?: e.message ?: ""
        val errCode = if (e is FirebaseAuthException) e.errorCode else ""

        if (errCode == "ERROR_OPERATION_NOT_ALLOWED" || isProviderOrSmsRegionDisabledMessage(msg)) {
            return if (isEn) {
                "Firebase Phone Authentication is disabled, SMS Region Policy blocks Bangladesh (+880), or Blaze billing is not activated.\n\n" +
                        "Recommended actions:\n" +
                        "1. Use 'Sign in with Google' for instant one-click login/registration without SMS dependencies.\n" +
                        "2. In Firebase Console > Authentication > Sign-in method, ensure 'Phone' provider is enabled.\n" +
                        "3. In Firebase Console > Authentication > Settings > SMS Region Policy, allow Bangladesh (+880) and ensure Blaze billing is active."
            } else {
                "Firebase-এ Phone প্রোভাইডার বন্ধ রয়েছে, বাংলাদেশে (+880) SMS পাঠানোর অনুমতি (SMS Region Policy) নেই অথবা Blaze বিলিং সক্রিয় নেই।\n\n" +
                        "করণীয়:\n" +
                        "১. SMS ঝামেলা ছাড়া তাৎক্ষণিক প্রবেশ করতে 'Google দিয়ে প্রবেশ' ব্যবহার করুন (প্রস্তাবিত)।\n" +
                        "২. Firebase Console > Authentication > Sign-in method-এ 'Phone' প্রোভাইডার Enable করুন।\n" +
                        "৩. Firebase Console > Authentication > Settings > 'SMS Region Policy'-তে Bangladesh (+880) Allow করুন এবং Blaze বিলিং সক্রিয় রাখুন।"
            }
        }

        return when (e) {
            is FirebaseAuthInvalidCredentialsException -> {
                if (isEn) {
                    "Problem: The OTP code is incorrect or expired.\nWhat to do: Please enter the exact 6-digit code or request a new OTP."
                } else {
                    "সমস্যা: প্রদত্ত ওটিপি (OTP) কোডটি সঠিক নয় অথবা এর মেয়াদ শেষ হয়ে গেছে।\nকরণীয়: অনুগ্রহ করে সঠিক ৬ সংখ্যার কোড দিন অথবা আবার কোড পাঠান।"
                }
            }
            is FirebaseTooManyRequestsException -> {
                if (isEn) {
                    "Problem: SMS quota exceeded or temporarily blocked due to repeated attempts.\nWhat to do: Please try again later or use Google Sign-In."
                } else {
                    "সমস্যা: দৈনিক এসএমএস কোটা শেষ হয়েছে অথবা অতিরিক্ত চেষ্টার কারণে সাময়িকভাবে স্থগিত করা হয়েছে।\nকরণীয়: কিছুক্ষণ পর পুনরায় চেষ্টা করুন অথবা Google সাইন-ইন ব্যবহার করুন।"
                }
            }
            is FirebaseNetworkException -> {
                if (isEn) {
                    "Problem: No internet connection.\nWhat to do: Please check your mobile data or Wi-Fi connection and retry."
                } else {
                    "সমস্যা: ইন্টারনেট সংযোগ বিচ্ছিন্ন!\nকরণীয়: দয়া করে আপনার ডিভাইসের ডাটা বা ওয়াই-ফাই সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন।"
                }
            }
            else -> {
                when {
                    msg.contains("app not authorized", ignoreCase = true) ||
                    msg.contains("SHA-1", ignoreCase = true) ||
                    msg.contains("app verification failed", ignoreCase = true) ||
                    msg.contains("reCAPTCHA", ignoreCase = true) -> {
                        if (isEn) {
                            "Device verification (Play Integrity / SHA-1) failed.\nWhat to do: Ensure current debug.keystore SHA-1 (AE:D3:D1:F1:5F:C6:78:C4:E0:23:4D:2A:25:7F:8B:4E:2C:97:75:9E) is added to your Firebase Console project."
                        } else {
                            "ডিভাইস ভেরিফিকেশন (Play Integrity / SHA-1) ত্রুটি।\nকরণীয়: Firebase কনসোলে বর্তমান debug.keystore SHA-1 (AE:D3:D1:F1:5F:C6:78:C4:E0:23:4D:2A:25:7F:8B:4E:2C:97:75:9E) যুক্ত আছে কিনা পরীক্ষা করুন।"
                        }
                    }
                    msg.contains("quota", ignoreCase = true) -> {
                        if (isEn) {
                            "Daily SMS quota reached. Please try again later or sign in with Google."
                        } else {
                            "দৈনিক SMS কোটা পূর্ণ হয়েছে। অনুগ্রহ করে কিছু সময় পর চেষ্টা করুন অথবা Google সাইন-ইন দিয়ে প্রবেশ করুন।"
                        }
                    }
                    msg.contains("invalid phone", ignoreCase = true) -> {
                        if (isEn) {
                            "Problem: Invalid phone number format.\nWhat to do: Enter a valid 11-digit Bangladeshi mobile number (e.g. 017XXXXXXXX)."
                        } else {
                            "সমস্যা: মোবাইল নম্বরটি সঠিক ফরম্যাটে নেই।\nকরণীয়: অনুগ্রহ করে সঠিক ১১ ডিজিটের নম্বর দিন (যেমন: 017XXXXXXXX)।"
                        }
                    }
                    msg.contains("PERMISSION_DENIED", ignoreCase = true) -> {
                        if (isEn) "Firebase permission denied (PERMISSION_DENIED)." else "ফায়ারবেস অনুমোদনের ত্রুটি (PERMISSION_DENIED)।"
                    }
                    else -> {
                        val prefix = if (isEn) "Verification failed: " else "যাচাইকরণ ব্যর্থ হয়েছে: "
                        "$prefix$msg"
                    }
                }
            }
        }
    }
}
