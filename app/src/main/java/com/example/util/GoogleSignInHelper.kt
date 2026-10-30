package com.example.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object GoogleSignInHelper {
    private const val TAG = "GoogleSignInHelper"

    // Real SHA-1 and SHA-256 certificate fingerprints for this APK signed by debug.keystore
    // Verified using: keytool -list -v -keystore debug.keystore -alias androiddebugkey
    const val APK_SIGNING_SHA1 = "E0:60:7B:C4:F6:CB:8F:52:33:38:B3:2F:FB:90:1A:E5:D3:47:58:F5"
    const val APK_SIGNING_SHA256 = "3D:33:36:B4:EE:F7:21:48:36:D4:8E:42:B8:DE:33:93:2B:99:BB:55:C5:28:4B:FF:98:6F:FF:0F:2B:C2:BA:CD"
    const val DEFAULT_WEB_CLIENT_ID = "484040397249-h2tfr5q8pgu65ncl9fgaslcbdsvj9kkj.apps.googleusercontent.com"
    const val APPLICATION_ID = "com.aistudio.smartbiz.mgr"

    data class DiagnosticInfo(
        val packageName: String,
        val actualSha1: String,
        val actualSha256: String,
        val webClientId: String,
        val firebaseProjectId: String = "smart-business-manager-71879"
    )

    fun getDiagnosticInfo(context: Context): DiagnosticInfo {
        return DiagnosticInfo(
            packageName = context.packageName,
            actualSha1 = getSigningSha1(context),
            actualSha256 = getSigningSha256(context),
            webClientId = getWebClientId(context)
        )
    }

    @Suppress("DEPRECATION")
    fun getSigningSha1(context: Context): String {
        return try {
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else null
            } else {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
                packageInfo.signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray() ?: return APK_SIGNING_SHA1
            val md = java.security.MessageDigest.getInstance("SHA-1")
            val digest = md.digest(cert)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            APK_SIGNING_SHA1
        }
    }

    @Suppress("DEPRECATION")
    fun getSigningSha256(context: Context): String {
        return try {
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else null
            } else {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
                packageInfo.signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray() ?: return APK_SIGNING_SHA256
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest(cert)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            APK_SIGNING_SHA256
        }
    }

    fun getWebClientId(context: Context): String {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                context.getString(resId)
            } else {
                context.getString(R.string.default_web_client_id)
            }
        } catch (_: Exception) {
            DEFAULT_WEB_CLIENT_ID
        }
    }

    fun findActivity(context: Context): Activity? {
        var currentContext: Context? = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is Activity) {
                return currentContext
            }
            currentContext = currentContext.baseContext
        }
        return null
    }

    fun launchFirebaseOAuthFallback(
        activity: Activity,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onCancelled: (() -> Unit)? = null,
        onError: (String) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onError("অ্যাক্টিভিটি প্রস্তুত নয়, পুনরায় চেষ্টা করুন।")
            return
        }

        Log.i(TAG, "Initiating Firebase OAuthProvider fallback for Google Sign-In")
        val provider = OAuthProvider.newBuilder("google.com")
        val auth = FirebaseAuth.getInstance()

        auth.startActivityForSignInWithProvider(activity, provider.build())
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                if (user != null) {
                    Log.i(TAG, "Firebase OAuthProvider fallback success: uid=${user.uid}, email=${user.email}")
                    onAuthSuccess(user)
                } else {
                    onError("ফায়ারবেস ব্যবহারকারী সেশন শুরু করা সম্ভব হয়নি।")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firebase OAuthProvider fallback failure: ${e.message}", e)
                val exMsg = e.localizedMessage ?: e.message ?: ""
                val isCancelled = exMsg.contains("cancel", ignoreCase = true) ||
                        (e is com.google.firebase.auth.FirebaseAuthException && e.errorCode == "ERROR_WEB_CONTEXT_CANCELED")
                if (isCancelled) {
                    if (onCancelled != null) {
                        onCancelled()
                    } else {
                        onError("গুগল সাইন-ইন উইন্ডো বন্ধ করা হয়েছে।")
                    }
                } else if (e is com.google.firebase.FirebaseNetworkException) {
                    onError("ইন্টারনেট সংযোগে সমস্যা হচ্ছে। আপনার ইন্টারনেট সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন।")
                } else {
                    val currentSha1 = getSigningSha1(activity)
                    onError("বিকল্প গুগল সাইন-ইন সম্পন্ন করা যায়নি: $exMsg\n\n" +
                            "Firebase Console-এ এই অ্যাপের সাইনিং SHA-1 যুক্ত থাকা নিশ্চিত করুন:\n" +
                            "• SHA-1: $currentSha1\n" +
                            "• Package: ${activity.packageName}")
                }
            }
    }

    fun launchGoogleSignIn(
        context: Context,
        scope: CoroutineScope,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onCancelled: (() -> Unit)? = null,
        onError: (String) -> Unit
    ) {
        val activity = findActivity(context)
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onError("সিস্টেম ত্রুটি: অ্যাক্টিভিটি পাওয়া যায়নি বা বন্ধ রয়েছে।")
            return
        }

        val clientId = getWebClientId(context)
        if (clientId.isBlank()) {
            onError("গুগল সাইন-ইন কনফিগারেশন পাওয়া যায়নি (default_web_client_id অনুপস্থিত)।")
            return
        }

        val currentSha1 = getSigningSha1(context)
        val currentSha256 = getSigningSha256(context)
        Log.i(TAG, "Initiating Google Sign-In with serverClientId=$clientId, package=${context.packageName}, sha1=$currentSha1")

        val credentialManager = CredentialManager.create(context)
        // Standard Android CredentialManager requirement: exactly ONE option per request for interactive picker
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                val googleIdToken: String? = when {
                    credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                        try {
                            GoogleIdTokenCredential.createFrom(credential.data).idToken
                        } catch (e: Exception) {
                            credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                                ?: credential.data.getString("id_token")
                        }
                    }
                    credential is CustomCredential -> {
                        try {
                            GoogleIdTokenCredential.createFrom(credential.data).idToken
                        } catch (e: Exception) {
                            credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                                ?: credential.data.getString("id_token")
                        }
                    }
                    else -> null
                }

                if (!googleIdToken.isNullOrBlank()) {
                    Log.i(TAG, "Google ID Token received successfully")
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = FirebaseAuth.getInstance().signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        Log.i(TAG, "FirebaseAuth sign-in success: uid=${user.uid}, email=${user.email}")
                        onAuthSuccess(user)
                    } else {
                        onError("ফায়ারবেস ব্যবহারকারী সেশন শুরু করা সম্ভব হয়নি। (AuthResult returned null user)")
                    }
                } else {
                    val unexpectedType = credential::class.java.name
                    Log.e(TAG, "Unexpected credential type: $unexpectedType, type=${credential.type}")
                    onError("গুগল আইডি টোকেন পাওয়া যায়নি [ক্রেডেনশিয়াল টাইপ: ${credential.type}]")
                }
            } catch (e: GetCredentialCancellationException) {
                // User cancelled or tapped outside bottom sheet - return cleanly to login screen without closing or crashing
                Log.i(TAG, "User cancelled Google Sign-In picker")
                if (onCancelled != null) {
                    onCancelled()
                }
            } catch (e: NoCredentialException) {
                val exMsg = e.message ?: "No account credentials"
                Log.w(TAG, "NoCredentialException: $exMsg. Falling back to web OAuth.")
                launchFirebaseOAuthFallback(activity, onAuthSuccess, onCancelled, onError)
            } catch (e: androidx.credentials.exceptions.GetCredentialCustomException) {
                val exType = e.type
                val exMsg = e.message ?: ""
                Log.w(TAG, "GetCredentialCustomException: type=$exType, message=$exMsg")
                val isDevError = exMsg.contains("10") || exMsg.contains("DEVELOPER_ERROR", ignoreCase = true) || exType.contains("DEVELOPER_ERROR", ignoreCase = true)
                val isCanceled = exMsg.contains("16") || exMsg.contains("CANCEL", ignoreCase = true) || exType.contains("CANCEL", ignoreCase = true)

                if (isCanceled) {
                    if (onCancelled != null) {
                        onCancelled()
                    }
                } else if (isDevError) {
                    Log.i(TAG, "Developer Error 10 encountered with CredentialManager, falling back to Firebase OAuthProvider")
                    // Automatic fallback to web OAuthProvider so user is not blocked by missing SHA-1 on physical device
                    launchFirebaseOAuthFallback(activity, onAuthSuccess, onCancelled) { fallbackError ->
                        onError("গুগল সাইন-ইন সার্ভিস কনফিগারেশন ত্রুটি (Developer Error 10):\n\n" +
                                "Firebase Console-এ এই অ্যাপের সাইনিং SHA-1 যুক্ত থাকা নিশ্চিত করুন:\n" +
                                "• Package Name: ${context.packageName}\n" +
                                "• সাইনিং SHA-1: $currentSha1\n" +
                                "• Web Client ID: $clientId\n\n" +
                                "বিকল্প লগইন ব্যর্থতা: $fallbackError")
                    }
                } else {
                    Log.i(TAG, "Attempting OAuth fallback for CustomException: $exType")
                    launchFirebaseOAuthFallback(activity, onAuthSuccess, onCancelled, onError)
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialInterruptedException) {
                Log.w(TAG, "GetCredentialInterruptedException: ${e.message}")
                if (onCancelled != null) {
                    onCancelled()
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialProviderConfigurationException) {
                Log.w(TAG, "GetCredentialProviderConfigurationException: ${e.message}. Attempting OAuth fallback.")
                launchFirebaseOAuthFallback(activity, onAuthSuccess, onCancelled, onError)
            } catch (e: androidx.credentials.exceptions.GetCredentialUnsupportedException) {
                Log.w(TAG, "GetCredentialUnsupportedException: ${e.message}. Attempting OAuth fallback.")
                launchFirebaseOAuthFallback(activity, onAuthSuccess, onCancelled, onError)
            } catch (e: GetCredentialException) {
                val exType = e.type
                val exMsg = e.message ?: ""
                Log.w(TAG, "GetCredentialException: type=$exType, message=$exMsg. Attempting OAuth fallback.")
                launchFirebaseOAuthFallback(activity, onAuthSuccess, onCancelled, onError)
            } catch (e: com.google.firebase.auth.FirebaseAuthException) {
                val errCode = e.errorCode
                val exMsg = e.message ?: ""
                Log.e(TAG, "FirebaseAuthException: errorCode=$errCode, msg=$exMsg")
                if (errCode == "ERROR_OPERATION_NOT_ALLOWED" || exMsg.contains("operation-not-allowed", ignoreCase = true)) {
                    onError("Firebase Authentication-এ Google Sign-in সক্রিয় করা নেই!\n" +
                            "Firebase Console > Authentication > Sign-in method-এ যান, Google চালু করুন এবং Support Email নির্বাচন করে সংরক্ষণ করুন।")
                } else if (errCode == "ERROR_INVALID_CREDENTIAL") {
                    onError("Google শংসাপত্রটি অবৈধ বা মেয়াদোত্তীর্ণ। অনুগ্রহ করে পুনরায় চেষ্টা করুন।")
                } else {
                    onError("Firebase Authentication ত্রুটি ($errCode): $exMsg")
                }
            } catch (e: com.google.firebase.FirebaseNetworkException) {
                Log.e(TAG, "FirebaseNetworkException: ${e.message}")
                onError("ইন্টারনেট সংযোগে সমস্যা হচ্ছে। আপনার ইন্টারনেট সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন।")
            } catch (t: Throwable) {
                val exClass = t.javaClass.simpleName
                val exMsg = t.localizedMessage ?: t.message ?: "Unknown"
                Log.e(TAG, "General Authentication Exception: $exClass: $exMsg", t)
                onError("সাইন-ইন সম্পন্ন করা যায়নি [$exClass]: $exMsg")
            }
        }
    }

    fun getBengaliErrorMessage(e: Throwable): String {
        return when {
            e is java.net.UnknownHostException || e is com.google.firebase.FirebaseNetworkException ->
                "ইন্টারনেট সংযোগে সমস্যা হচ্ছে। আপনার ইন্টারনেট সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন।"
            e is java.net.SocketTimeoutException ->
                "সার্ভার সংযোগ টাইমআউট হয়েছে। দুর্বল ইন্টারনেটের কারণে এই সমস্যা হতে পারে। পুনরায় চেষ্টা করুন।"
            e is GetCredentialCancellationException || e.message?.contains("cancel", ignoreCase = true) == true ->
                "গুগল সাইন-ইন বাতিল করা হয়েছে।"
            e is NoCredentialException ->
                "ডিভাইসে কোনো গুগল একাউন্ট পাওয়া যায়নি।"
            else ->
                "গুগল সাইন-ইন প্রক্রিয়ায় ত্রুটি ঘটেছে: ${e.localizedMessage ?: e.message ?: "অজানা ত্রুটি"}"
        }
    }
}

@Composable
fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("google_sign_in_button"),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F)
        ),
        border = BorderStroke(1.dp, Color(0xFFDADCE0)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 2.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = Color(0xFF4285F4),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Google অ্যাকাউন্ট দিয়ে সাইন ইন",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF3C4043)
                )
            }
        }
    }
}

@Composable
fun GoogleSignInDiagnosticDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val diag = remember { GoogleSignInHelper.getDiagnosticInfo(context) }
    val fullSummary = "Package: ${diag.packageName}\n" +
            "SHA-1: ${diag.actualSha1}\n" +
            "SHA-256: ${diag.actualSha256}\n" +
            "Web Client ID: ${diag.webClientId}\n" +
            "Firebase Project: ${diag.firebaseProjectId}"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Google Sign-In কনফিগারেশন ও কি তথ্য",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Google Sign-In সফলভাবে কাজ করার জন্য Firebase Console-এ নিচের সাইনিং SHA-1 যুক্ত থাকা আবশ্যক। যুক্ত না থাকলে Google Play Services সাইন-ইন বন্ধ করে দেয়।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("• Package Name:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        SelectionContainer {
                            Text(diag.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }

                        Text("• বর্তমান APK-এর সাইনিং SHA-1:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        SelectionContainer {
                            Text(diag.actualSha1, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }

                        Text("• বর্তমান APK-এর সাইনিং SHA-256:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        SelectionContainer {
                            Text(diag.actualSha256, style = MaterialTheme.typography.bodySmall)
                        }

                        Text("• Web Client ID:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        SelectionContainer {
                            Text(diag.webClientId, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Google Sign-In Config", fullSummary)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "কনফিগারেশন তথ্য কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("সব তথ্য কপি করুন")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ঠিক আছে")
            }
        }
    )
}
