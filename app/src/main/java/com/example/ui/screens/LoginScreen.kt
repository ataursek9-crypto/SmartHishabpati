package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.LanguageSwitcherButton
import com.example.ui.theme.*
import com.example.util.AppErrorHandler
import com.example.util.AppLanguageManager
import com.example.util.AppStrings
import com.example.util.GoogleSignInHelper
import com.example.util.PhoneAuthHelper
import com.example.viewmodel.BusinessViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * SmartHishabpati LoginScreen
 * Exactly matching the uploaded reference design:
 * 1. Top Business Hero Illustration with smooth, lightweight floating animation.
 * 2. Brand Identity: "SmartHishabpati" + "— হিসাব রাখুন স্মার্টভাবে —".
 * 3. Security Banner Card: "আপনার হিসাব নিরাপদ ও সুরক্ষিত".
 * 4. Login Form Card: Mobile Number, 4-digit PIN (with Show/Hide toggle), and Purple Login Button.
 * 5. Google Sign-In & Help Links: "পিন ভুলে গেছেন?", "সাহায্য প্রয়োজন?".
 * 6. "নতুন অ্যাকাউন্ট তৈরি করুন" navigation button opening the 4-step Registration Wizard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: BusinessViewModel,
    onNavigateToAdminLogin: () -> Unit,
    onNavigateToRegistration: () -> Unit = {},
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val savedAccounts by viewModel.savedAccounts.collectAsStateWithLifecycle()
    val rememberedEmail by viewModel.rememberedEmail.collectAsStateWithLifecycle()
    val savedRememberMe by viewModel.rememberMeState.collectAsStateWithLifecycle()
    val pendingShopSelection by viewModel.pendingShopSelection.collectAsStateWithLifecycle()
    val currentLanguage by AppLanguageManager.currentLanguage.collectAsStateWithLifecycle()

    // Form State
    var loginMethodTab by remember { mutableIntStateOf(0) } // 0 = Gmail, 1 = Phone
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var rememberMe by remember(savedRememberMe) { mutableStateOf(savedRememberMe) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Google Sign-In State
    var isGoogleLoading by remember { mutableStateOf(false) }
    var showGoogleDiagDialog by remember { mutableStateOf(false) }
    var googleStatusMessage by remember { mutableStateOf("") }
    var pendingGoogleUser by remember { mutableStateOf<com.google.firebase.auth.FirebaseUser?>(null) }
    var linkBusinessIdInput by remember { mutableStateOf("") }
    var linkPhoneInput by remember { mutableStateOf("") }
    var linkPinInput by remember { mutableStateOf("") }
    var linkErrorMessage by remember { mutableStateOf("") }
    var isLinkingLoading by remember { mutableStateOf(false) }

    // Dialogs
    var showForgotPinDialog by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var forgotPasswordEmailInput by remember { mutableStateOf("") }
    var forgotPasswordStatusMsg by remember { mutableStateOf("") }
    var isSendingPasswordReset by remember { mutableStateOf(false) }
    var isPasswordResetSuccess by remember { mutableStateOf(false) }
    var isGoogleOnlyAccountPrompt by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Legacy PIN Upgrade Dialog State
    val requiresPinUpgrade by viewModel.requiresPinUpgrade.collectAsStateWithLifecycle()
    var upgradeNewPin by remember { mutableStateOf("") }
    var upgradeConfirmPin by remember { mutableStateOf("") }
    var upgradeErrorMsg by remember { mutableStateOf("") }
    var isUpgradingPin by remember { mutableStateOf(false) }
    var showUpgradePin by remember { mutableStateOf(false) }

    // Existing Shop PIN Setup via OTP State
    val pendingPinSetup by viewModel.pendingPinSetup.collectAsStateWithLifecycle()
    var otpVerificationId by remember { mutableStateOf("") }
    var otpCodeInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmNewPinInput by remember { mutableStateOf("") }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtpAndPin by remember { mutableStateOf(false) }
    var pinSetupErrorMessage by remember { mutableStateOf("") }
    var otpSentSuccess by remember { mutableStateOf(false) }

    // Auto-fill email from remembered email if empty
    LaunchedEffect(rememberedEmail) {
        if (email.isBlank() && rememberedEmail.isNotBlank()) {
            email = rememberedEmail
        }
    }

    // Auto-fill phone from last remembered account if empty
    LaunchedEffect(savedAccounts) {
        if (phone.isBlank() && savedAccounts.isNotEmpty()) {
            phone = savedAccounts.first().phone
        }
    }

    // 5-Tap Secret Gesture Counter for Hidden Super Admin Portal
    var secretTapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }

    val handleSecretTap = {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTapTime > 2000L) {
            secretTapCount = 1
        } else {
            secretTapCount++
        }
        lastTapTime = currentTime

        if (secretTapCount >= 5) {
            secretTapCount = 0
            onNavigateToAdminLogin()
        }
    }

    // Gentle, low-memory floating animation for the top illustration (runs at 60fps with zero battery drain)
    val infiniteTransition = rememberInfiniteTransition(label = "heroFloat")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    // Trigger Login Action
    val triggerLogin = {
        if (loginMethodTab == 0) {
            // Gmail + Password Login
            val cleanEmail = email.trim().lowercase()
            val cleanPass = password.trim()
            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                errorMessage = "দয়া করে সঠিক জিমেইল / ইমেইল ঠিকানা লিখুন!"
            } else if (cleanPass.isBlank()) {
                errorMessage = "দয়া করে আপনার পাসওয়ার্ড লিখুন!"
            } else {
                isLoading = true
                errorMessage = ""
                focusManager.clearFocus()
                viewModel.loginWithEmailPassword(cleanEmail, cleanPass) { success, msg, _ ->
                    isLoading = false
                    if (success) {
                        viewModel.setRememberMePreference(rememberMe)
                        if (rememberMe) {
                            viewModel.saveRememberedEmail(cleanEmail)
                        } else {
                            viewModel.clearRememberedEmail()
                        }
                        onLoginSuccess()
                    } else {
                        errorMessage = msg
                    }
                }
            }
        } else {
            // Phone + PIN Login
            val trimmedPhone = phone.trim()
                .replace('০', '0').replace('১', '1').replace('২', '2')
                .replace('৩', '3').replace('৪', '4').replace('৫', '5')
                .replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')
            val trimmedPin = pin.trim()
                .replace('০', '0').replace('১', '1').replace('২', '2')
                .replace('৩', '3').replace('৪', '4').replace('৫', '5')
                .replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')

            if (trimmedPhone.isBlank()) {
                errorMessage = "দয়া করে আপনার মোবাইল নম্বর লিখুন!"
            } else if (trimmedPin.isBlank()) {
                errorMessage = "দয়া করে আপনার ৪-সংখ্যার পিন লিখুন!"
            } else {
                isLoading = true
                errorMessage = ""
                focusManager.clearFocus()

                viewModel.login(trimmedPhone, trimmedPin) { success, msg ->
                    isLoading = false
                    if (success) {
                        viewModel.setRememberMePreference(rememberMe)
                        if (rememberMe) {
                            viewModel.saveRememberedPhone(trimmedPhone)
                        } else {
                            viewModel.removeRememberedPhone(trimmedPhone)
                        }
                        onLoginSuccess()
                    } else {
                        errorMessage = msg
                    }
                }
            }
        }
    }

    // Trigger Google Sign-In Action
    val triggerGoogleSignIn = {
        isGoogleLoading = true
        errorMessage = ""
        googleStatusMessage = ""
        GoogleSignInHelper.launchGoogleSignIn(
            context = context,
            scope = coroutineScope,
            onAuthSuccess = { fUser ->
                viewModel.handleGoogleSignInSuccess(fUser) { success, msg, _ ->
                    isGoogleLoading = false
                    if (success) {
                        onLoginSuccess()
                    } else {
                        googleStatusMessage = msg
                        pendingGoogleUser = fUser
                        linkBusinessIdInput = viewModel.getActiveBusinessId()
                        linkPhoneInput = phone.ifBlank { "" }
                        linkPinInput = ""
                        linkErrorMessage = ""
                    }
                }
            },
            onCancelled = {
                isGoogleLoading = false
                googleStatusMessage = ""
            },
            onError = { err ->
                isGoogleLoading = false
                googleStatusMessage = err
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFF))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top-Right Language Switcher (PART 2: Globe Icon with 🇧🇩 বাংলা & 🇬🇧 English)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 16.dp)
                .zIndex(10f)
        ) {
            LanguageSwitcherButton()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // =================================================================
            // 1. TOP ILLUSTRATION (Matching Reference Image with Soft Waves)
            // =================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                contentAlignment = Alignment.Center
            ) {
                // Top wavy background glow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(bottomStart = 80.dp, bottomEnd = 80.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFEDE9FE),
                                    Color(0xFFF5F3FF),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Hero Illustration Image with subtle floating animation
                Image(
                    painter = painterResource(id = R.drawable.login_hero_illustration),
                    contentDescription = "SmartHishabpati Business Illustration",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(210.dp)
                        .offset(y = floatOffset.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =================================================================
            // 2. BRAND IDENTITY: Store Squircle + "SmartHishabpati" + Slogan
            // =================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Central Store Squircle Icon (5-tap secret trigger)
                Surface(
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("app_logo_secret_trigger")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { handleSecretTap() },
                    shape = RoundedCornerShape(16.dp),
                    color = HishabpatiPurple,
                    shadowElevation = 3.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "লোগো",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Brand Name Text
                Text(
                    text = "SmartHishabpati",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 25.sp,
                        color = Color(0xFF1E1B4B)
                    ),
                    modifier = Modifier
                        .testTag("app_title_secret_trigger")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { handleSecretTap() }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bengali Slogan with horizontal wings: — হিসাব রাখুন স্মার্টভাবে —
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(1.5.dp)
                        .background(HishabpatiPurple)
                )
                Text(
                    text = "  ${AppStrings.appSlogan.trim('-').trim()}  ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = HishabpatiPurple
                )
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(1.5.dp)
                        .background(HishabpatiPurple)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =================================================================
            // 3. SECURITY BANNER CARD ("আপনার হিসাব নিরাপদ ও সুরক্ষিত")
            // =================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE9FE)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(HishabpatiPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = AppStrings.securityBanner,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color(0xFF1E1B4B)
                        )
                        Text(
                            text = if (AppLanguageManager.isEnglish()) "Easily log in and manage your business" else "সহজে লগইন করুন এবং ব্যবসা পরিচালনা করুন",
                            fontSize = 12.sp,
                            color = Color(0xFF4B5563)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Error Banner (if any)
            if (errorMessage.isNotBlank() || googleStatusMessage.isNotBlank()) {
                val bannerText = if (errorMessage.isNotBlank()) errorMessage else googleStatusMessage
                val isNoShopMsg = bannerText.contains("USER_HAS_NO_SHOPS") || bannerText.contains("নতুন দোকান")
                val isCredentialIssue = bannerText.contains("Google", ignoreCase = true) ||
                        bannerText.contains("পাসওয়ার্ড", ignoreCase = true) ||
                        bannerText.contains("INVALID_CREDENTIAL", ignoreCase = true) ||
                        bannerText.contains("WRONG_PASSWORD", ignoreCase = true) ||
                        bannerText.contains("ACCOUNT_EXISTS", ignoreCase = true)
                val cleanBannerText = bannerText.replace("USER_HAS_NO_SHOPS:", "").trim()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isNoShopMsg) Color(0xFFFEF3C7) else Color(0xFFFEE2E2)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isNoShopMsg) Color(0xFFFCD34D) else Color(0xFFFCA5A5))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isNoShopMsg) Icons.Default.Storefront else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (isNoShopMsg) Color(0xFFD97706) else AccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cleanBannerText,
                                color = if (isNoShopMsg) Color(0xFF92400E) else Color(0xFF991B1B),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (isNoShopMsg) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onNavigateToRegistration,
                                colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Icon(Icons.Default.AddBusiness, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🏪 এখনই নতুন দোকান রেজিস্ট্রেশন করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        } else if (isCredentialIssue) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { triggerGoogleSignIn() },
                                    colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (AppLanguageManager.isEnglish()) "Sign in with Google" else "Google দিয়ে প্রবেশ",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        forgotPasswordEmailInput = email.ifBlank { "" }
                                        forgotPasswordStatusMsg = ""
                                        isPasswordResetSuccess = false
                                        isGoogleOnlyAccountPrompt = false
                                        showForgotPasswordDialog = true
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, HishabpatiPurple),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Icon(Icons.Default.LockReset, contentDescription = null, tint = HishabpatiPurple, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (AppLanguageManager.isEnglish()) "Forgot Password" else "পাসওয়ার্ড রিসেট",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HishabpatiPurple
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =================================================================
            // 4. DUAL TAB SELECTOR: Gmail | Phone (Matching Modern Auth UI)
            // =================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE9FE)),
                border = BorderStroke(1.dp, Color(0xFFDDD6FE))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (loginMethodTab == 0) Color.White else Color.Transparent)
                            .clickable {
                                loginMethodTab = 0
                                errorMessage = ""
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = if (loginMethodTab == 0) HishabpatiPurple else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = AppStrings.tabGmailLogin,
                                fontWeight = if (loginMethodTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (loginMethodTab == 0) HishabpatiPurple else Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (loginMethodTab == 1) Color.White else Color.Transparent)
                            .clickable {
                                loginMethodTab = 1
                                errorMessage = ""
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = if (loginMethodTab == 1) HishabpatiPurple else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = AppStrings.tabPhoneLogin,
                                fontWeight = if (loginMethodTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (loginMethodTab == 1) HishabpatiPurple else Color(0xFF64748B),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =================================================================
            // 4. LOGIN FORM CARD (Gmail or Phone Form)
            // =================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (loginMethodTab == 0) {
                        // Gmail Address Input
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                errorMessage = ""
                            },
                            placeholder = { Text(if (AppLanguageManager.isEnglish()) "Your registered Gmail address" else "আপনার নিবন্ধিত Gmail ঠিকানা", color = Color(0xFF94A3B8)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = HishabpatiPurple,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_email_input")
                        )

                        // Password Input
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMessage = ""
                            },
                            placeholder = { Text(if (AppLanguageManager.isEnglish()) "6-digit PIN or password" else "৬-সংখ্যার পিন বা পাসওয়ার্ড", color = Color(0xFF94A3B8)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showPassword = !showPassword },
                                    modifier = Modifier.testTag("login_password_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B)
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { triggerLogin() }),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = HishabpatiPurple,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_password_input")
                        )
                    } else {
                        // Phone Tab Info Banner
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (AppLanguageManager.isEnglish()) "Use phone number and PIN for existing shops or saved offline access." else "পুরোনো দোকান বা সংরক্ষিত অফলাইন পিনের জন্য ফোন নম্বর ও পিন ব্যবহার করুন।",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF92400E),
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        // Mobile Number Input
                        OutlinedTextField(
                            value = phone,
                            onValueChange = {
                                phone = it
                                errorMessage = ""
                            },
                            placeholder = { Text(if (AppLanguageManager.isEnglish()) "Mobile number" else "মোবাইল নম্বর", color = Color(0xFF94A3B8)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B)
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = HishabpatiPurple,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_phone_input")
                        )

                        // 4 or 6-Digit PIN Input
                        OutlinedTextField(
                            value = pin,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                    pin = it
                                    errorMessage = ""
                                }
                            },
                            placeholder = { Text(if (AppLanguageManager.isEnglish()) "4 or 6-digit PIN" else "৪ বা ৬-সংখ্যার পিন", color = Color(0xFF94A3B8)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showPin = !showPin },
                                    modifier = Modifier.testTag("login_pin_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B)
                                    )
                                }
                            },
                            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { triggerLogin() }),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = HishabpatiPurple,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_pin_input")
                        )
                    }

                    // Remember Me & Forgot Password Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                val next = !rememberMe
                                rememberMe = next
                                viewModel.setRememberMePreference(next)
                            }
                        ) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = {
                                    rememberMe = it
                                    viewModel.setRememberMePreference(it)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = HishabpatiPurple),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = AppStrings.rememberMe,
                                fontSize = 13.sp,
                                color = Color(0xFF475569)
                            )
                        }

                        Text(
                            text = if (loginMethodTab == 0) AppStrings.forgotPassword else AppStrings.forgotPin,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HishabpatiPurple,
                            modifier = Modifier.clickable {
                                if (loginMethodTab == 0) {
                                    forgotPasswordEmailInput = email.ifBlank { "" }
                                    forgotPasswordStatusMsg = ""
                                    isPasswordResetSuccess = false
                                    isGoogleOnlyAccountPrompt = false
                                    showForgotPasswordDialog = true
                                } else {
                                    showForgotPinDialog = true
                                }
                            }
                        )
                    }

                    // Large Purple Login Button
                    Button(
                        onClick = triggerLogin,
                        enabled = !isLoading && (
                            (loginMethodTab == 0 && email.isNotBlank() && password.isNotBlank()) ||
                            (loginMethodTab == 1 && phone.isNotBlank() && pin.isNotBlank())
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_submit_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HishabpatiPurple,
                            disabledContainerColor = Color(0xFFCBD5E1)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = AppStrings.buttonLogin,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // =================================================================
            // 5. "অথবা" DIVIDER
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                Text(
                    text = if (AppLanguageManager.isEnglish()) "  OR  " else "  অথবা  ",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
            }

            // =================================================================
            // 6. GOOGLE SIGN-IN BUTTON (Matching Reference)
            // =================================================================
            OutlinedButton(
                onClick = triggerGoogleSignIn,
                enabled = !isGoogleLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("google_signin_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFEDE9FE)),
                border = BorderStroke(1.dp, Color(0xFFDDD6FE))
            ) {
                if (isGoogleLoading) {
                    CircularProgressIndicator(color = HishabpatiPurple, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        GoogleLogoMini(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = AppStrings.googleSignInButton,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E1B4B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =================================================================
            // 7. HELP LINKS: "পিন ভুলে গেছেন?" | "সাহায্য প্রয়োজন?"
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clickable { showForgotPinDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("forgot_pin_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = HishabpatiPurple,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = AppStrings.forgotPin,
                        color = HishabpatiPurple,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(Color(0xFFCBD5E1))
                )

                Row(
                    modifier = Modifier
                        .clickable { showHelpDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("help_support_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = HishabpatiPurple,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = AppStrings.needHelp,
                        color = HishabpatiPurple,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =================================================================
            // 8. "নতুন অ্যাকাউন্ট তৈরি করুন" BUTTON (Opens 4-Step Registration)
            // =================================================================
            OutlinedButton(
                onClick = onNavigateToRegistration,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("login_create_account_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                border = BorderStroke(1.5.dp, HishabpatiPurple)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PersonAddAlt1,
                            contentDescription = null,
                            tint = HishabpatiPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = AppStrings.createNewShop,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp,
                            color = HishabpatiPurple
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = HishabpatiPurple,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =================================================================
            // 9. FOOTER (Matching Reference Image)
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.width(40.dp).height(1.dp).background(Color(0xFFCBD5E1)))
                Text(
                    text = "  SmartHishabpati v1.0  ",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Box(modifier = Modifier.width(40.dp).height(1.dp).background(Color(0xFFCBD5E1)))
            }
        }
    }

    // --- Existing Shop PIN Setup via OTP Dialog ---
    if (pendingPinSetup != null) {
        val setupInfo = pendingPinSetup!!
        AlertDialog(
            onDismissRequest = {
                if (!isVerifyingOtpAndPin && !isSendingOtp) {
                    viewModel.clearPendingPinSetup()
                    otpVerificationId = ""
                    otpCodeInput = ""
                    newPinInput = ""
                    confirmNewPinInput = ""
                    pinSetupErrorMessage = ""
                    otpSentSuccess = false
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "PIN Setup",
                        tint = HishabpatiPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "দোকানের নিরাপত্তা ও পিন সেটআপ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = HishabpatiPurple
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E8FF)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "প্রতিষ্ঠান: ${setupInfo.businessName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = HishabpatiPurple
                            )
                            Text(
                                text = "মোবাইল: ${setupInfo.phone}",
                                fontSize = 13.sp,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }

                    Text(
                        text = "ক্লাউড সিকিউরিটি আপগ্রেডের কারণে আপনার অ্যাকাউন্টে প্রথমবার পিন সেটআপ করা প্রয়োজন। মালিকানা নিশ্চিত করতে নিচের নম্বরে পাঠানো ৬ সংখ্যার ওটিপি কোড এবং আপনার পছন্দের নতুন ৪-সংখ্যার পিন প্রদান করুন।",
                        fontSize = 12.sp,
                        color = Color(0xFF334155),
                        lineHeight = 17.sp
                    )

                    // Button to send OTP
                    OutlinedButton(
                        onClick = {
                            val activity = context as? android.app.Activity
                            if (activity != null) {
                                isSendingOtp = true
                                pinSetupErrorMessage = ""
                                PhoneAuthHelper.sendVerificationCode(
                                    activity = activity,
                                    phone = setupInfo.phone,
                                    onCodeSent = { vId, _ ->
                                        isSendingOtp = false
                                        otpVerificationId = vId
                                        otpSentSuccess = true
                                    },
                                    onAutoVerified = { fUser ->
                                        isSendingOtp = false
                                        coroutineScope.launch {
                                            val token = try { fUser.getIdToken(false).await().token ?: "" } catch (_: Exception) { "" }
                                            if (token.isNotBlank() && newPinInput.length in 4..6 && newPinInput == confirmNewPinInput) {
                                                viewModel.setupExistingShopPin(
                                                    businessId = setupInfo.businessId,
                                                    phone = setupInfo.phone,
                                                    newPin = newPinInput,
                                                    idToken = token
                                                ) { success, msg ->
                                                    if (success) {
                                                        onLoginSuccess()
                                                    } else {
                                                        pinSetupErrorMessage = msg
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onError = { err ->
                                        isSendingOtp = false
                                        pinSetupErrorMessage = err
                                    }
                                )
                            } else {
                                pinSetupErrorMessage = "সিস্টেম এরর: অ্যাক্টিভিটি পাওয়া যায়নি!"
                            }
                        },
                        enabled = !isSendingOtp && !isVerifyingOtpAndPin,
                        modifier = Modifier.fillMaxWidth().testTag("send_otp_setup_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HishabpatiPurple)
                    ) {
                        if (isSendingOtp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = HishabpatiPurple)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ওটিপি পাঠানো হচ্ছে...", fontSize = 13.sp)
                        } else {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (otpSentSuccess) "পুনরায় ওটিপি পাঠান" else "ফোনে ওটিপি কোড পাঠান", fontSize = 13.sp)
                        }
                    }

                    if (otpSentSuccess) {
                        Text(
                            text = "আপনার মোবাইলে ৬ সংখ্যার ওটিপি কোড পাঠানো হয়েছে।",
                            fontSize = 11.sp,
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // OTP Input Field
                    OutlinedTextField(
                        value = otpCodeInput,
                        onValueChange = { if (it.length <= 6) otpCodeInput = it.filter { char -> char.isDigit() } },
                        label = { Text("৬ সংখ্যার ওটিপি কোড", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("otp_code_setup_input")
                    )

                    // New PIN Input Field
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { if (it.length <= 6) newPinInput = it.filter { char -> char.isDigit() } },
                        label = { Text("নতুন ৪-৬ সংখ্যার পিন", fontSize = 12.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_pin_setup_input")
                    )

                    // Confirm New PIN Input Field
                    OutlinedTextField(
                        value = confirmNewPinInput,
                        onValueChange = { if (it.length <= 6) confirmNewPinInput = it.filter { char -> char.isDigit() } },
                        label = { Text("নতুন পিন পুনরায় লিখুন", fontSize = 12.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("confirm_new_pin_setup_input")
                    )

                    if (pinSetupErrorMessage.isNotBlank()) {
                        Text(
                            text = pinSetupErrorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanOtp = otpCodeInput.trim()
                        val cleanPin = newPinInput.trim()
                        val cleanConfirm = confirmNewPinInput.trim()

                        if (otpVerificationId.isBlank()) {
                            pinSetupErrorMessage = "প্রথমে 'ফোনে ওটিপি কোড পাঠান' বাটনে চাপ দিন।"
                        } else if (cleanOtp.length != 6) {
                            pinSetupErrorMessage = "সঠিক ৬ সংখ্যার ওটিপি কোড লিখুন।"
                        } else if (cleanPin.length < 4 || cleanPin.length > 6) {
                            pinSetupErrorMessage = "নতুন পিন কোড ৪ থেকে ৬ সংখ্যার হতে হবে।"
                        } else if (cleanPin != cleanConfirm) {
                            pinSetupErrorMessage = "উভয় পিন কোড মিলছে না!"
                        } else {
                            isVerifyingOtpAndPin = true
                            pinSetupErrorMessage = ""
                            PhoneAuthHelper.verifyCodeAndSignIn(
                                verificationId = otpVerificationId,
                                code = cleanOtp,
                                onSuccess = { fUser ->
                                    coroutineScope.launch {
                                        val token = try { fUser.getIdToken(false).await().token ?: "" } catch (_: Exception) { "" }
                                        if (token.isBlank()) {
                                            isVerifyingOtpAndPin = false
                                            pinSetupErrorMessage = "ওটিপি প্রমাণীকরণ টোকেন সংগ্রহ ব্যর্থ হয়েছে।"
                                            return@launch
                                        }
                                        viewModel.setupExistingShopPin(
                                            businessId = setupInfo.businessId,
                                            phone = setupInfo.phone,
                                            newPin = cleanPin,
                                            idToken = token
                                        ) { success, msg ->
                                            isVerifyingOtpAndPin = false
                                            if (success) {
                                                onLoginSuccess()
                                            } else {
                                                pinSetupErrorMessage = msg
                                            }
                                        }
                                    }
                                },
                                onError = { err ->
                                    isVerifyingOtpAndPin = false
                                    pinSetupErrorMessage = err
                                }
                            )
                        }
                    },
                    enabled = !isVerifyingOtpAndPin,
                    colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple),
                    modifier = Modifier.testTag("submit_pin_setup_button")
                ) {
                    if (isVerifyingOtpAndPin) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("নিশ্চিত করুন ও লগইন", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isVerifyingOtpAndPin && !isSendingOtp) {
                            viewModel.clearPendingPinSetup()
                            otpVerificationId = ""
                            otpCodeInput = ""
                            newPinInput = ""
                            confirmNewPinInput = ""
                            pinSetupErrorMessage = ""
                            otpSentSuccess = false
                        }
                    },
                    enabled = !isVerifyingOtpAndPin
                ) {
                    Text("বাতিল", color = Color(0xFF64748B))
                }
            }
        )
    }

    // --- Forgot PIN Dialog ---
    if (showForgotPinDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPinDialog = false },
            title = {
                Text(
                    text = "পিন ভুলে গেছেন?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = HishabpatiPurple
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "১. আপনার দোকানের স্বত্বাধিকারীর সাথে যোগাযোগ করে নতুন পিন সেট করে নিতে পারেন।\n\n" +
                                "২. আপনি যদি নিজেই দোকানের মালিক হন, তবে আপনার পূর্বে নিবন্ধিত Google অ্যাকাউন্ট দিয়ে 'Google দিয়ে লগইন করুন' চাপুন।\n\n" +
                                "৩. অথবা নতুন দোকানের হিসাব শুরু করতে নিচে 'নতুন অ্যাকাউন্ট তৈরি করুন' বোতামে চাপুন।",
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showForgotPinDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple)
                ) {
                    Text("ঠিক আছে", color = Color.White)
                }
            }
        )
    }

    // --- Help & Support Dialog ---
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = "সাহায্য ও সাপোর্ট",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = HishabpatiPurple
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SmartHishabpati ব্যবহারে যে কোনো সহযোগিতায় আমাদের সাথে যোগাযোগ করুন:\n\n" +
                                "• হটলাইন: 01700000000\n" +
                                "• ইমেইল: support@smartbiz.local\n" +
                                "• সময়: সকাল ৯টা - রাত ১০টা\n\n" +
                                "সুপার এডমিন পোর্টালে প্রবেশের জন্য ওপরের লোগোতে পর পর ৫ বার ট্যাপ করুন।",
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 19.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple)
                ) {
                    Text("ঠিক আছে", color = Color.White)
                }
            }
        )
    }

    // --- Pending Google Account Link Dialog ---
    if (pendingGoogleUser != null) {
        val fUser = pendingGoogleUser!!
        AlertDialog(
            onDismissRequest = { if (!isLinkingLoading) pendingGoogleUser = null },
            title = {
                Text(
                    text = "গুগল অ্যাকাউন্ট সংযুক্তকরণ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = HishabpatiPurple
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "আপনার গুগল অ্যাকাউন্ট (${fUser.email ?: fUser.uid}) এর সাথে এখনো কোনো দোকান সংযুক্ত নেই।\nনিচের তথ্যাদি দিয়ে সংযুক্ত করুন অথবা নতুন দোকান খুলুন:",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )

                    OutlinedTextField(
                        value = linkPhoneInput,
                        onValueChange = { linkPhoneInput = it },
                        placeholder = { Text("নিবন্ধিত ফোন নম্বর", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = linkPinInput,
                        onValueChange = { if (it.length <= 4) linkPinInput = it },
                        placeholder = { Text("৪-সংখ্যার পিন", fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (linkErrorMessage.isNotBlank()) {
                        Text(
                            text = linkErrorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isLinkingLoading = true
                        linkErrorMessage = ""
                        viewModel.linkGoogleAccountWithPin(
                            firebaseUser = fUser,
                            phone = linkPhoneInput,
                            pin = linkPinInput
                        ) { success, msg, _ ->
                            isLinkingLoading = false
                            if (success) {
                                pendingGoogleUser = null
                                onLoginSuccess()
                            } else {
                                linkErrorMessage = msg
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple)
                ) {
                    Text("সংযুক্ত ও লগইন", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingGoogleUser = null }) {
                    Text("বাতিল", color = Color(0xFF64748B))
                }
            }
        )
    }

    // --- Forgot Password Dialog (Gmail Firebase Password Reset) ---
    if (showForgotPasswordDialog) {
        val isEn = AppLanguageManager.isEnglish()
        AlertDialog(
            onDismissRequest = { if (!isSendingPasswordReset) showForgotPasswordDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LockReset,
                        contentDescription = "Password Reset",
                        tint = HishabpatiPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEn) "Reset Password" else "পাসওয়ার্ড রিসেট করুন",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = HishabpatiPurple
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isEn) {
                            "Enter the Gmail address registered with your account. We will request a password reset link from Firebase."
                        } else {
                            "আপনার একাউন্টে নিবন্ধিত Gmail ঠিকানাটি লিখুন। আমরা Firebase-এ পাসওয়ার্ড রিসেট লিংক পাঠানোর অনুরোধ পাঠাব।"
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 18.sp
                    )

                    OutlinedTextField(
                        value = forgotPasswordEmailInput,
                        onValueChange = {
                            forgotPasswordEmailInput = it
                            forgotPasswordStatusMsg = ""
                            isPasswordResetSuccess = false
                            isGoogleOnlyAccountPrompt = false
                        },
                        placeholder = { Text(if (isEn) "Registered Gmail address" else "নিবন্ধিত Gmail ঠিকানা", color = Color(0xFF94A3B8)) },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF64748B))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("forgot_password_email_input")
                    )

                    if (forgotPasswordStatusMsg.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPasswordResetSuccess) Color(0xFFDCFCE7)
                                else if (isGoogleOnlyAccountPrompt) Color(0xFFF3E8FF)
                                else Color(0xFFFEE2E2)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = forgotPasswordStatusMsg,
                                    color = if (isPasswordResetSuccess) Color(0xFF15803D)
                                    else if (isGoogleOnlyAccountPrompt) HishabpatiPurple
                                    else Color(0xFFB91C1C),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = if (isPasswordResetSuccess || isGoogleOnlyAccountPrompt) FontWeight.Medium else FontWeight.Normal
                                )
                                if (isPasswordResetSuccess) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = AppStrings.emailDeliveryNotice,
                                        fontSize = 11.sp,
                                        color = Color(0xFF166534),
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    // 1-Click Google Sign-In Option (Ideal for Google accounts or instant access)
                    OutlinedButton(
                        onClick = {
                            showForgotPasswordDialog = false
                            triggerGoogleSignIn()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF5F3FF)),
                        border = BorderStroke(1.dp, HishabpatiPurple),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("google_login_from_reset_button")
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = HishabpatiPurple, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEn) "Sign In with Google (No Password Needed)" else "Google দিয়ে সরাসরি প্রবেশ করুন",
                            color = HishabpatiPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (!isGoogleOnlyAccountPrompt) {
                    Button(
                        onClick = {
                            isSendingPasswordReset = true
                            forgotPasswordStatusMsg = ""
                            isPasswordResetSuccess = false
                            isGoogleOnlyAccountPrompt = false
                            viewModel.sendPasswordResetEmail(forgotPasswordEmailInput) { success, isGoogleOnly, msg ->
                                isSendingPasswordReset = false
                                isPasswordResetSuccess = success
                                isGoogleOnlyAccountPrompt = isGoogleOnly
                                forgotPasswordStatusMsg = msg
                            }
                        },
                        enabled = !isSendingPasswordReset && forgotPasswordEmailInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple),
                        modifier = Modifier.testTag("send_password_reset_button")
                    ) {
                        if (isSendingPasswordReset) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (isEn) "Send Reset Link" else "রিসেট লিংক পাঠান", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotPasswordDialog = false },
                    enabled = !isSendingPasswordReset
                ) {
                    Text(if (isEn) "Close" else "বন্ধ করুন", color = Color(0xFF64748B))
                }
            }
        )
    }

    // --- Multi-Shop Selection Dialog (When same Gmail/Phone owns multiple shops) ---
    if (pendingShopSelection != null) {
        val selectionState = pendingShopSelection!!
        var isSelectingShop by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isSelectingShop) {
                    viewModel.dismissShopSelection()
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = "Select Shop",
                        tint = HishabpatiPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "আপনার দোকান নির্বাচন করুন",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = HishabpatiPurple
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "এই অ্যাকাউন্টের অধীনে ${selectionState.availableShops.size}টি দোকান পাওয়া গেছে। আপনি কোন দোকানে প্রবেশ করতে চান?",
                        fontSize = 13.sp,
                        color = Color(0xFF475569),
                        lineHeight = 18.sp
                    )

                    selectionState.availableShops.forEach { shop ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isSelectingShop) {
                                    isSelectingShop = true
                                    viewModel.selectShopAndProceed(shop) { success, msg, _ ->
                                        isSelectingShop = false
                                        if (success) {
                                            onLoginSuccess()
                                        } else {
                                            errorMessage = msg
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color(0xFFEDE9FE), shape = RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = HishabpatiPurple,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = shop.businessName.ifBlank { "নামবিহীন দোকান" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = "${shop.planName.ifBlank { "সাধারণ ব্যবসা" }} • ${shop.ownerPhone.ifBlank { "ফোন অপ্রাপ্য" }}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = "ID: ${shop.id}",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = HishabpatiPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (isSelectingShop) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = HishabpatiPurple)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("দোকানের তথ্য প্রস্তুত হচ্ছে...", fontSize = 12.sp, color = HishabpatiPurple)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissShopSelection()
                        onNavigateToRegistration()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AddBusiness, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("নতুন দোকান খুলুন", color = Color.White, fontSize = 12.5.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissShopSelection() },
                    enabled = !isSelectingShop
                ) {
                    Text("বাতিল", color = Color(0xFF64748B))
                }
            }
        )
    }

    // --- Legacy 4-Digit to 6-Digit PIN Upgrade Dialog ---
    if (requiresPinUpgrade) {
        AlertDialog(
            onDismissRequest = {
                if (!isUpgradingPin) {
                    viewModel.dismissPinUpgrade()
                    onLoginSuccess()
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "PIN Upgrade",
                        tint = HishabpatiPurple,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "নিরাপত্তা ও ৬-সংখ্যার পিন আপগ্রেড",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = HishabpatiPurple
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "আপনার অ্যাকাউন্টটিতে পুরোনো ৪-সংখ্যার পিন ব্যবহৃত হচ্ছিল। অ্যাকাউন্টকে আধুনিক নিরাপত্তায় সুরক্ষিত করতে এবং ভবিষ্যতে সহজে জিমেইল দিয়ে সরাসরি লগইন করতে অনুগ্রহ করে আপনার পছন্দের নতুন একটি ৬-সংখ্যার পিন সেট করুন।",
                        fontSize = 12.5.sp,
                        color = Color(0xFF334155),
                        lineHeight = 17.sp
                    )

                    OutlinedTextField(
                        value = upgradeNewPin,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                upgradeNewPin = it
                                upgradeErrorMsg = ""
                            }
                        },
                        placeholder = { Text("নতুন ৬-সংখ্যার গোপন পিন", color = Color(0xFF94A3B8)) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = HishabpatiPurple)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showUpgradePin = !showUpgradePin }) {
                                Icon(
                                    imageVector = if (showUpgradePin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B)
                                )
                            }
                        },
                        visualTransformation = if (showUpgradePin) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("upgrade_new_pin_input")
                    )

                    OutlinedTextField(
                        value = upgradeConfirmPin,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                upgradeConfirmPin = it
                                upgradeErrorMsg = ""
                            }
                        },
                        placeholder = { Text("পিন পুনরায় লিখুন", color = Color(0xFF94A3B8)) },
                        leadingIcon = {
                            Icon(Icons.Default.LockClock, contentDescription = null, tint = HishabpatiPurple)
                        },
                        visualTransformation = if (showUpgradePin) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("upgrade_confirm_pin_input")
                    )

                    if (upgradeErrorMsg.isNotBlank()) {
                        Text(text = upgradeErrorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isUpgradingPin = true
                        upgradeErrorMsg = ""
                        viewModel.upgradeUserPin(upgradeNewPin, upgradeConfirmPin) { success, msg ->
                            isUpgradingPin = false
                            if (success) {
                                onLoginSuccess()
                            } else {
                                upgradeErrorMsg = msg
                            }
                        }
                    },
                    enabled = !isUpgradingPin && upgradeNewPin.length == 6 && upgradeConfirmPin.length == 6,
                    colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple),
                    modifier = Modifier.testTag("submit_pin_upgrade_button")
                ) {
                    if (isUpgradingPin) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("আপগ্রেড সম্পন্ন করুন", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissPinUpgrade()
                        onLoginSuccess()
                    },
                    enabled = !isUpgradingPin
                ) {
                    Text("পরে করব", color = Color(0xFF64748B))
                }
            }
        )
    }
}

/**
 * Miniature Google 'G' icon drawn with official brand colors.
 */
@Composable
fun GoogleLogoMini(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sizePx = size.minDimension
        val stroke = sizePx * 0.22f
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (sizePx - stroke) / 2f
        val rectTopLeft = Offset(center.x - radius, center.y - radius)
        val rectSize = Size(radius * 2, radius * 2)

        // Blue Horizontal Bar
        drawLine(
            color = Color(0xFF4285F4),
            start = center,
            end = Offset(center.x + radius, center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Square
        )

        // Arc Blue (top right)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = 0f,
            sweepAngle = -45f,
            useCenter = false,
            topLeft = rectTopLeft,
            size = rectSize,
            style = Stroke(width = stroke)
        )

        // Arc Red (top)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = -45f,
            sweepAngle = -90f,
            useCenter = false,
            topLeft = rectTopLeft,
            size = rectSize,
            style = Stroke(width = stroke)
        )

        // Arc Yellow (left)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = -135f,
            sweepAngle = -90f,
            useCenter = false,
            topLeft = rectTopLeft,
            size = rectSize,
            style = Stroke(width = stroke)
        )

        // Arc Green (bottom)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 30f,
            sweepAngle = 105f,
            useCenter = false,
            topLeft = rectTopLeft,
            size = rectSize,
            style = Stroke(width = stroke)
        )
    }
}
