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

    /**
     * Sends Firebase SMS OTP to the given phone number.
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
            onError("সঠিক ১১ ডিজিটের বাংলাদেশী মোবাইল নম্বর দিন (যেমন: 017XXXXXXXX)!")
            return
        }

        try {
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
                                onError("অথেনটিকেশন সম্পন্ন হয়েছে কিন্তু ব্যবহারকারীর তথ্য পাওয়া যায়নি।")
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
        onSuccess: (user: FirebaseUser) -> Unit,
        onError: (errorMessage: String) -> Unit
    ) {
        val cleanCode = code.trim()
            .replace('০', '0').replace('১', '1').replace('২', '2')
            .replace('৩', '3').replace('৪', '4').replace('৫', '5')
            .replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')

        if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) {
            onError("৬ সংখ্যার সঠিক ওটিপি (OTP) কোড লিখুন!")
            return
        }

        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, cleanCode)
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user != null) {
                        Log.i(TAG, "Successfully authenticated Phone user: ${user.uid}, phone: ${user.phoneNumber}")
                        onSuccess(user)
                    } else {
                        onError("অথেনটিকেশন সফল কিন্তু ব্যবহারকারীর পরিচয় পাওয়া যায়নি।")
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
                msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true)
    }

    /**
     * Translates Firebase Auth exceptions into helpful, user-friendly Bengali messages.
     */
    fun translateFirebaseError(e: Throwable): String {
        val msg = e.localizedMessage ?: e.message ?: ""
        val errCode = if (e is FirebaseAuthException) e.errorCode else ""

        if (errCode == "ERROR_OPERATION_NOT_ALLOWED" || isProviderOrSmsRegionDisabledMessage(msg)) {
            return "Firebase-এ Phone প্রোভাইডার বন্ধ রয়েছে অথবা বাংলাদেশে (+880) SMS পাঠানোর অনুমতি (SMS Region Policy) নেই।\n\n" +
                    "তাৎক্ষণিক সমাধানের উপায়:\n" +
                    "১. পাশের 'Google অ্যাকাউন্ট' ট্যাব নির্বাচন করে SMS ছাড়া এক ক্লিকেই নিবন্ধন করুন (প্রস্তাবিত)।\n" +
                    "২. অথবা Firebase Console > Authentication > Sign-in method-এ 'Phone' প্রোভাইডার Enable করুন।\n" +
                    "৩. এবং Firebase Console > Authentication > Settings > 'SMS Region Policy'-তে Bangladesh (+880) অনুমোদন (Allow) করুন।"
        }

        return when (e) {
            is FirebaseAuthInvalidCredentialsException -> {
                "প্রদত্ত ওটিপি (OTP) কোডটি সঠিক নয় অথবা এর মেয়াদ শেষ হয়ে গেছে। অনুগ্রহ করে সঠিক কোড দিন অথবা আবার কোড পাঠান।"
            }
            is FirebaseTooManyRequestsException -> {
                "এসএমএস কোটা শেষ হয়েছে অথবা অতিরিক্ত চেষ্টার কারণে সাময়িকভাবে স্থগিত করা হয়েছে। কিছুক্ষণ পর পুনরায় চেষ্টা করুন অথবা Google সাইন-ইন ব্যবহার করুন।"
            }
            is FirebaseNetworkException -> {
                "ইন্টারনেট সংযোগ বিচ্ছিন্ন! দয়া করে আপনার ডিভাইসের ডাটা বা ওয়াই-ফাই সংযোগ পরীক্ষা করুন।"
            }
            else -> {
                when {
                    msg.contains("app not authorized", ignoreCase = true) || msg.contains("SHA-1", ignoreCase = true) ->
                        "অ্যাপের SHA-1 বা প্লে ইন্টিগ্রিটি কনফিগারেশন ত্রুটি। Firebase কনসোলে বর্তমান debug.keystore SHA-1 (B4:F3:6C:65:C4:65:C9:22:00:43:5D:34:5C:E4:67:DC:FA:89:F8:C5) যুক্ত আছে কিনা পরীক্ষা করুন।"
                    msg.contains("quota", ignoreCase = true) ->
                        "দৈনিক SMS কোটা পূর্ণ হয়েছে। অনুগ্রহ করে কিছু সময় পর চেষ্টা করুন অথবা Google সাইন-ইন দিয়ে তাৎক্ষণিক নিবন্ধন করুন।"
                    msg.contains("invalid phone", ignoreCase = true) ->
                        "মোবাইল নম্বরটি সঠিক ফরম্যাটে নেই। অনুগ্রহ করে সঠিক ১১ ডিজিটের নম্বর দিন (যেমন: 017XXXXXXXX)।"
                    msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                        "ফায়ারবেস অনুমোদনের ত্রুটি (PERMISSION_DENIED)।"
                    else -> "যাচাইকরণ ব্যর্থ হয়েছে: $msg"
                }
            }
        }
    }
}
