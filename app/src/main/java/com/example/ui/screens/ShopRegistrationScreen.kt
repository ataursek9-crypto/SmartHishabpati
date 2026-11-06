package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlatformBusiness
import com.example.data.repository.SuperAdminRepository
import com.example.ui.theme.*
import com.example.util.AppErrorHandler
import com.example.util.GoogleSignInHelper
import com.example.util.PhoneAuthHelper
import com.example.util.FirebaseInitHelper
import com.example.util.AppLanguageManager
import com.example.util.AppStrings
import com.example.ui.components.LanguageSwitcherButton
import com.example.viewmodel.BusinessViewModel
import com.example.viewmodel.SuperAdminViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 4-Step Multi-Step Registration Wizard for SmartHishabpati
 * Exactly matching the provided reference screenshots:
 * Step 1: ব্যবসার নাম (Business Name & Referral Code) - 1/4
 * Step 2: আপনার নাম (Owner First & Last Name) - 2/4
 * Step 3: ইমেইল ও ফোন দিন (Email, Phone, & Security PIN) - 3/4
 * Step 4: যাচাই করুন (6-digit OTP Verification & Auto-Creation) - 4/4
 */
enum class RegistrationStep(val stepNumber: Int, val stepLabel: String) {
    STEP_1_BUSINESS_NAME(1, "১/৪"),
    STEP_2_OWNER_NAME(2, "২/৪"),
    STEP_3_EMAIL_PHONE_PIN(3, "৩/৪"),
    STEP_4_VERIFICATION(4, "৪/৪")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopRegistrationScreen(
    viewModel: BusinessViewModel,
    superAdminViewModel: SuperAdminViewModel? = null,
    onBack: () -> Unit,
    onRegistrationSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Wizard Step State
    var currentStep by remember { mutableStateOf(RegistrationStep.STEP_1_BUSINESS_NAME) }

    // Intercept Back Press according to wizard step
    BackHandler {
        when (currentStep) {
            RegistrationStep.STEP_1_BUSINESS_NAME -> onBack()
            RegistrationStep.STEP_2_OWNER_NAME -> currentStep = RegistrationStep.STEP_1_BUSINESS_NAME
            RegistrationStep.STEP_3_EMAIL_PHONE_PIN -> currentStep = RegistrationStep.STEP_2_OWNER_NAME
            RegistrationStep.STEP_4_VERIFICATION -> currentStep = RegistrationStep.STEP_3_EMAIL_PHONE_PIN
        }
    }

    // --- Form State ---
    // Step 1: Business Details
    var shopName by remember { mutableStateOf("") }
    var showReferralInput by remember { mutableStateOf(false) }
    var referralCode by remember { mutableStateOf("") }
    var selectedBusinessType by remember { mutableStateOf("মুদি ও জেনারেল স্টোর") }
    var district by remember { mutableStateOf("ঢাকা") }
    var address by remember { mutableStateOf("") }

    // Step 2: Owner Name
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }

    // Step 3: Contact & Security PIN
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    // Step 4: OTP Verification State
    var verificationMethodTab by remember { mutableStateOf(1) } // 0 = ইমেইল, 1 = ফোন (Default matching screenshot)
    var verificationId by remember { mutableStateOf("") }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var otpDigits by remember { mutableStateOf(List(6) { "" }) }
    var countdownSeconds by remember { mutableStateOf(60) }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifyingOtp by remember { mutableStateOf(false) }
    var authenticatedUser by remember { mutableStateOf<FirebaseUser?>(null) }

    // Existing Account Recovery & Multi-Shop Preservation State
    var existingAccountDetected by remember { mutableStateOf(false) }
    var existingAccountProviderType by remember { mutableStateOf(AppErrorHandler.EmailAuthProviderType.UNKNOWN) }
    var existingOwnedShops by remember { mutableStateOf<List<PlatformBusiness>>(emptyList()) }
    var allowAdditionalShopForExistingOwner by remember { mutableStateOf(false) }
    var existingPasswordInput by remember { mutableStateOf("") }
    var showExistingPassword by remember { mutableStateOf(false) }
    var isVerifyingExistingPassword by remember { mutableStateOf(false) }
    var isLinkingProvider by remember { mutableStateOf(false) }
    var recoveryStatusMessage by remember { mutableStateOf("") }

    // UI Feedback
    var errorMessage by remember { mutableStateOf("") }
    var successRegisteredShopId by remember { mutableStateOf<String?>(null) }

    // Pre-check already authenticated Firebase session (e.g., user signed in via Google on LoginScreen and routed to Register Shop)
    LaunchedEffect(Unit) {
        try {
            FirebaseInitHelper.ensureFirebaseInitialized(context)
            val currentFUser = FirebaseAuth.getInstance().currentUser
            if (currentFUser != null) {
                authenticatedUser = currentFUser
                if (email.isBlank() && !currentFUser.email.isNullOrBlank()) {
                    email = currentFUser.email!!
                }
                val dName = currentFUser.displayName ?: ""
                if (firstName.isBlank() && dName.isNotBlank()) {
                    val parts = dName.split(" ")
                    firstName = parts.firstOrNull() ?: ""
                    lastName = parts.drop(1).joinToString(" ")
                }
                val ownedShops = viewModel.loadOwnedShopsForCurrentUser()
                existingOwnedShops = ownedShops
                val pIds = currentFUser.providerData.mapNotNull { it.providerId }
                existingAccountProviderType = viewModel.classifyProviderIds(pIds)
                if (ownedShops.isNotEmpty()) {
                    existingAccountDetected = true
                }
            }
        } catch (_: Exception) {}
    }

    // Countdown Timer for OTP Resend
    LaunchedEffect(countdownSeconds, currentStep) {
        if (currentStep == RegistrationStep.STEP_4_VERIFICATION && countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds -= 1
        }
    }

    // Helper: Convert English numbers to Bengali
    fun toBengaliDigits(input: String): String {
        return input.map { c ->
            when (c) {
                '0' -> '০'; '1' -> '১'; '2' -> '২'; '3' -> '৩'; '4' -> '৪'
                '5' -> '৫'; '6' -> '৬'; '7' -> '৭'; '8' -> '৮'; '9' -> '৯'
                else -> c
            }
        }.joinToString("")
    }

    // Helper: Format Seconds into mm:ss in Bengali
    fun formatTimerBengali(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s).map { c ->
            when (c) {
                '0' -> '০'; '1' -> '১'; '2' -> '২'; '3' -> '৩'; '4' -> '৪'
                '5' -> '৫'; '6' -> '৬'; '7' -> '৭'; '8' -> '৮'; '9' -> '৯'
                else -> c
            }
        }.joinToString("")
    }

    // Helper: Mask Contact Info for Step 4
    val maskedContactDisplay = remember(phone, email, verificationMethodTab) {
        if (verificationMethodTab == 1 && phone.isNotBlank()) {
            val clean = phone.trim()
            if (clean.length >= 7) clean.take(3) + "*****" + clean.takeLast(3) else clean
        } else if (email.isNotBlank()) {
            val parts = email.split("@")
            if (parts.size == 2) {
                val name = parts[0]
                val domain = parts[1]
                val visible = if (name.length > 3) name.take(3) else name.take(1)
                "$visible***@$domain"
            } else email
        } else {
            "017*****000"
        }
    }

    // Full Name calculation
    val fullOwnerName = remember(firstName, lastName) {
        "$firstName $lastName".trim()
    }

    // --- Action: Verify / Recover via Google Sign-In (Preserves existing UID & discovers existing shops) ---
    var isGoogleSigningUp by remember { mutableStateOf(false) }

    val triggerGoogleSignUp: () -> Unit = {
        errorMessage = ""
        recoveryStatusMessage = ""
        isGoogleSigningUp = true
        focusManager.clearFocus()
        GoogleSignInHelper.launchGoogleSignIn(
            context = context,
            scope = coroutineScope,
            onAuthSuccess = { fUser ->
                isGoogleSigningUp = false
                authenticatedUser = fUser
                existingAccountDetected = false
                email = fUser.email ?: email
                val gName = fUser.displayName ?: ""
                if (firstName.isBlank() && gName.isNotBlank()) {
                    val parts = gName.split(" ")
                    firstName = parts.firstOrNull() ?: ""
                    lastName = parts.drop(1).joinToString(" ")
                }
                isVerifyingOtp = true
                coroutineScope.launch {
                    val ownedShops = viewModel.loadOwnedShopsForCurrentUser()
                    existingOwnedShops = ownedShops
                    existingAccountProviderType = viewModel.classifyProviderIds(fUser.providerData.mapNotNull { it.providerId })

                    if (ownedShops.isNotEmpty() && !allowAdditionalShopForExistingOwner) {
                        isVerifyingOtp = false
                        existingAccountDetected = true
                        recoveryStatusMessage = if (AppLanguageManager.isEnglish()) {
                            "Google account verified (${fUser.email}). Found ${ownedShops.size} existing shop(s)—choose 'Open Existing Shop' or 'Add New Shop to This Gmail' below."
                        } else {
                            "Google অ্যাকাউন্ট যাচাই সম্পন্ন (${fUser.email})! এই অ্যাকাউন্টে আগে থেকেই ${ownedShops.size}টি দোকান রয়েছে। নিচে 'আগের দোকানে প্রবেশ করুন' অথবা 'একই Gmail-এ নতুন দোকান যোগ করুন' নির্বাচন করুন।"
                        }
                    } else if (pin.length == 6 && pin == confirmPin && shopName.isNotBlank() && PhoneAuthHelper.isValidBangladeshPhone(phone.trim())) {
                        viewModel.registerNewShop(
                            shopName = shopName.trim(),
                            ownerName = fullOwnerName.ifBlank { fUser.displayName ?: "দোকান স্বত্বাধিকারী" },
                            phone = phone.trim(),
                            address = address.ifBlank { district },
                            district = district,
                            businessType = selectedBusinessType,
                            pin = pin.trim(),
                            isPhoneVerified = false,
                            ownerEmail = fUser.email ?: email.trim().lowercase(),
                            ownerUid = fUser.uid,
                            allowAdditionalShop = allowAdditionalShopForExistingOwner,
                            superAdminViewModel = superAdminViewModel
                        ) { regSuccess, regMsg, shopId ->
                            isVerifyingOtp = false
                            if (regSuccess && shopId != null) {
                                successRegisteredShopId = shopId
                            } else {
                                if (regMsg.contains("ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে")) {
                                    existingAccountDetected = true
                                }
                                errorMessage = regMsg
                            }
                        }
                    } else {
                        isVerifyingOtp = false
                        currentStep = RegistrationStep.STEP_4_VERIFICATION
                    }
                }
            },
            onCancelled = {
                isGoogleSigningUp = false
            },
            onError = { err ->
                isGoogleSigningUp = false
                errorMessage = err
            }
        )
    }

    // --- Action: Audit Email First, Reuse Existing UID if Signed In, or Create Firebase User ONLY When Email Is New ---
    val triggerSendOtp: () -> Unit = {
        errorMessage = ""
        recoveryStatusMessage = ""
        focusManager.clearFocus()

        val cleanEmail = viewModel.sanitizeEmail(email)
        email = cleanEmail
        val cleanPhone = phone.trim()

        if (!viewModel.isValidEmailFormat(cleanEmail)) {
            errorMessage = if (AppLanguageManager.isEnglish()) {
                "Please enter a valid Gmail / email address!"
            } else {
                "দয়া করে আপনার সঠিক জিমেইল / ইমেইল ঠিকানা দিন!"
            }
        } else if (!PhoneAuthHelper.isValidBangladeshPhone(cleanPhone)) {
            errorMessage = if (AppLanguageManager.isEnglish()) {
                "Please enter a valid 11-digit mobile number (e.g. 017XXXXXXXX)!"
            } else {
                "সঠিক ১১ ডিজিটের মোবাইল নম্বর দিন (যেমন: 017XXXXXXXX)!"
            }
        } else if (pin.length != 6 || !pin.all { it.isDigit() }) {
            errorMessage = if (AppLanguageManager.isEnglish()) {
                "Please enter a 6-digit security PIN!"
            } else {
                "অনুগ্রহ করে ৬-সংখ্যার গোপন পিন লিখুন!"
            }
        } else if (pin != confirmPin) {
            errorMessage = if (AppLanguageManager.isEnglish()) {
                "PIN and Confirm PIN do not match!"
            } else {
                "পিন এবং পিন নিশ্চিতকরণ মিলছে না!"
            }
        } else {
            isSendingOtp = true
            coroutineScope.launch {
                val currentAuth = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null } ?: authenticatedUser
                val signedInSameEmail = currentAuth != null && currentAuth.email?.trim()?.lowercase() == cleanEmail

                if (signedInSameEmail && currentAuth != null) {
                    authenticatedUser = currentAuth
                    val ownedShops = viewModel.loadOwnedShopsForCurrentUser()
                    existingOwnedShops = ownedShops
                    existingAccountProviderType = viewModel.classifyProviderIds(currentAuth.providerData.mapNotNull { it.providerId })

                    if (ownedShops.isNotEmpty() && !allowAdditionalShopForExistingOwner) {
                        isSendingOtp = false
                        existingAccountDetected = true
                        errorMessage = if (AppLanguageManager.isEnglish()) {
                            "This Gmail ($cleanEmail) already has ${ownedShops.size} registered shop(s) on UID ${currentAuth.uid}. Use the recovery card above to open an existing shop or enable 'Add New Shop to This Gmail'."
                        } else {
                            "এই Gmail ($cleanEmail) দিয়ে ইতিমধ্যে ${ownedShops.size}টি দোকান তৈরি করা আছে (UID: ${currentAuth.uid})। নতুন Firebase অ্যাকাউন্ট তৈরি না করে উপরের বক্স থেকে 'আগের দোকানে প্রবেশ করুন' অথবা 'একই Gmail-এ নতুন দোকান যোগ করুন' বাটনে ট্যাপ করুন।"
                        }
                    } else {
                        // Already verified on existing Firebase Auth user -> register shop directly on existing UID without calling createUserWithEmailAndPassword
                        viewModel.registerNewShop(
                            shopName = shopName.trim(),
                            ownerName = fullOwnerName.ifBlank { currentAuth.displayName ?: "দোকান স্বত্বাধিকারী" },
                            phone = cleanPhone,
                            address = address.ifBlank { district },
                            district = district,
                            businessType = selectedBusinessType,
                            pin = pin.trim(),
                            isPhoneVerified = false,
                            ownerEmail = cleanEmail,
                            ownerUid = currentAuth.uid,
                            allowAdditionalShop = allowAdditionalShopForExistingOwner,
                            superAdminViewModel = superAdminViewModel
                        ) { regSuccess, regMsg, shopId ->
                            isSendingOtp = false
                            if (regSuccess && shopId != null) {
                                successRegisteredShopId = shopId
                            } else {
                                if (regMsg.contains("ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে")) {
                                    existingAccountDetected = true
                                }
                                errorMessage = regMsg
                            }
                        }
                    }
                } else {
                    // Audit email before ever calling createUserWithEmailAndPassword
                    val audit = viewModel.auditEmailAuthStatus(cleanEmail)
                    if (audit.exists) {
                        isSendingOtp = false
                        existingAccountDetected = true
                        existingAccountProviderType = audit.providerType
                        existingOwnedShops = audit.existingShops
                        errorMessage = when (audit.providerType) {
                            AppErrorHandler.EmailAuthProviderType.GOOGLE_ONLY -> {
                                if (AppLanguageManager.isEnglish()) {
                                    "An account with Gmail ($cleanEmail) already exists via Google Sign-In. Do not create a duplicate account—verify with Google below to reuse your existing UID."
                                } else {
                                    "এই Gmail ($cleanEmail) দিয়ে ইতিমধ্যে Google Sign-In অ্যাকাউন্ট তৈরি করা আছে। নতুন অ্যাকাউন্ট তৈরি না করে নিচের Account Recovery অংশ থেকে 'Google দিয়ে যাচাই ও অ্যাকাউন্ট পুনরুদ্ধার' বাটনে চাপ দিন।"
                                }
                            }
                            else -> {
                                if (AppLanguageManager.isEnglish()) {
                                    "An account with Gmail ($cleanEmail) already exists. Verify your existing password or Google Sign-In below to continue on your existing UID."
                                } else {
                                    "এই Gmail ($cleanEmail) দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা আছে। নতুন অ্যাকাউন্ট তৈরি না করে নিচের Account Recovery অংশ থেকে আপনার আগের পাসওয়ার্ড অথবা Google দিয়ে যাচাই করুন।"
                                }
                            }
                        }
                    } else {
                        val registrationPassword = pin.trim()
                        viewModel.registerWithEmailPassword(cleanEmail, registrationPassword) { success, msg, user ->
                            isSendingOtp = false
                            if (success && user != null) {
                                authenticatedUser = user
                                existingAccountDetected = false
                                countdownSeconds = 60
                                currentStep = RegistrationStep.STEP_4_VERIFICATION
                            } else {
                                if (msg.contains("ACCOUNT_EXISTS") || msg.contains("ইতিমধ্যে একটি অ্যাকাউন্ট")) {
                                    existingAccountDetected = true
                                    if (msg.contains("GOOGLE_ONLY")) {
                                        existingAccountProviderType = AppErrorHandler.EmailAuthProviderType.GOOGLE_ONLY
                                    }
                                    errorMessage = msg
                                        .replace("ACCOUNT_EXISTS_GOOGLE_ONLY:", "")
                                        .replace("ACCOUNT_EXISTS_EXISTING_UID:", "")
                                        .trim()
                                } else {
                                    errorMessage = msg
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Action: Check Real Email Verification & Complete Shop Registration on Existing or Newly Created UID ---
    val triggerVerifyAndRegister: () -> Unit = {
        errorMessage = ""
        isVerifyingOtp = true
        focusManager.clearFocus()

        FirebaseInitHelper.ensureFirebaseInitialized(context)
        val currentUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null } ?: authenticatedUser
        val isGoogleUser = currentUser?.providerData?.any { it.providerId == "google.com" } == true

        if ((isGoogleUser || allowAdditionalShopForExistingOwner) && currentUser != null) {
            // Google user or recovered multi-shop owner is already authenticated on their UID
            coroutineScope.launch {
                viewModel.registerNewShop(
                    shopName = shopName.trim(),
                    ownerName = fullOwnerName.ifBlank { currentUser.displayName ?: "দোকান স্বত্বাধিকারী" },
                    phone = phone.trim(),
                    address = address.ifBlank { district },
                    district = district,
                    businessType = selectedBusinessType,
                    pin = pin.trim(),
                    isPhoneVerified = false,
                    ownerEmail = currentUser.email ?: email.trim().lowercase(),
                    ownerUid = currentUser.uid,
                    allowAdditionalShop = allowAdditionalShopForExistingOwner,
                    superAdminViewModel = superAdminViewModel
                ) { regSuccess, regMsg, shopId ->
                    isVerifyingOtp = false
                    if (regSuccess && shopId != null) {
                        successRegisteredShopId = shopId
                    } else {
                        if (regMsg.contains("ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে")) {
                            existingAccountDetected = true
                        }
                        errorMessage = regMsg
                    }
                }
            }
        } else {
            // Enforce REAL Firebase verification check
            viewModel.checkEmailVerificationStatus { _, isVerified, _ ->
                if (isVerified) {
                    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: authenticatedUser?.uid ?: ""
                    val currentEmail = FirebaseAuth.getInstance().currentUser?.email ?: email.trim().lowercase()

                    coroutineScope.launch {
                        viewModel.registerNewShop(
                            shopName = shopName.trim(),
                            ownerName = fullOwnerName.ifBlank { "দোকান স্বত্বাধিকারী" },
                            phone = phone.trim(),
                            address = address.ifBlank { district },
                            district = district,
                            businessType = selectedBusinessType,
                            pin = pin.trim(),
                            isPhoneVerified = false,
                            ownerEmail = currentEmail,
                            ownerUid = currentUid,
                            allowAdditionalShop = allowAdditionalShopForExistingOwner,
                            superAdminViewModel = superAdminViewModel
                        ) { regSuccess, regMsg, shopId ->
                            isVerifyingOtp = false
                            if (regSuccess && shopId != null) {
                                successRegisteredShopId = shopId
                            } else {
                                errorMessage = regMsg
                            }
                        }
                    }
                } else {
                    isVerifyingOtp = false
                    errorMessage = "আপনার জিমেইল (${email.trim()}) এখনও যাচাই করা হয়নি! দয়া করে আপনার জিমেইলে আসা লিংকে ক্লিক করে 'যাচাই সম্পন্ন করেছি' বাটনে চাপুন।"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (AppLanguageManager.isEnglish()) "Shop Registration" else "রেজিস্ট্রেশন",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (currentStep) {
                                RegistrationStep.STEP_1_BUSINESS_NAME -> onBack()
                                RegistrationStep.STEP_2_OWNER_NAME -> currentStep = RegistrationStep.STEP_1_BUSINESS_NAME
                                RegistrationStep.STEP_3_EMAIL_PHONE_PIN -> currentStep = RegistrationStep.STEP_2_OWNER_NAME
                                RegistrationStep.STEP_4_VERIFICATION -> currentStep = RegistrationStep.STEP_3_EMAIL_PHONE_PIN
                            }
                        },
                        modifier = Modifier.testTag("reg_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (AppLanguageManager.isEnglish()) "Back" else "ফিরে যান",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    LanguageSwitcherButton()
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HishabpatiPurple
                )
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        val registrationScrollState = rememberScrollState()
        LaunchedEffect(currentStep) {
            registrationScrollState.scrollTo(0)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(registrationScrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- 1. Progress Indicator (Matching Screenshot Segmented Bars) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 4 Rounded Segment Bars
                for (stepIdx in 1..4) {
                    val isActive = stepIdx <= currentStep.stepNumber
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isActive) HishabpatiPurple else HishabpatiInactiveTrack)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Progress Step Label on right ("১/৪", "২/৪", "৩/৪", "৪/৪")
                Text(
                    text = currentStep.stepLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = HishabpatiPurple
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- 2. Step Header Icon & Title (Matching Screenshots) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Badge (Rounded lavender square)
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(HishabpatiPurpleLight),
                    contentAlignment = Alignment.Center
                ) {
                    val iconVector = when (currentStep) {
                        RegistrationStep.STEP_1_BUSINESS_NAME -> Icons.Default.Storefront
                        RegistrationStep.STEP_2_OWNER_NAME -> Icons.Default.Person
                        RegistrationStep.STEP_3_EMAIL_PHONE_PIN -> Icons.Default.Email
                        RegistrationStep.STEP_4_VERIFICATION -> Icons.Default.Security
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = HishabpatiPurple,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    val headerTitle = when (currentStep) {
                        RegistrationStep.STEP_1_BUSINESS_NAME -> "ব্যবসার নাম"
                        RegistrationStep.STEP_2_OWNER_NAME -> "আপনার নাম"
                        RegistrationStep.STEP_3_EMAIL_PHONE_PIN -> "ইমেইল ও ফোন দিন"
                        RegistrationStep.STEP_4_VERIFICATION -> if (verificationMethodTab == 1) "ফোন যাচাই করুন" else "ইমেইল যাচাই করুন"
                    }
                    Text(
                        text = headerTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = HishabpatiTextDark
                    )
                    if (currentStep == RegistrationStep.STEP_4_VERIFICATION) {
                        Text(
                            text = "যেকোনো একটি যাচাই করলেই শুরু করতে পারবেন।",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- Error Message Banner (if any) ---
            if (errorMessage.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("reg_error_banner"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = AccentRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = errorMessage, color = Color(0xFF991B1B), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    }
                }
            }

            // --- Verified Session, Existing Shops & Safe Provider Linking Card ---
            val activeSessionUser = authenticatedUser
            if (activeSessionUser != null || existingOwnedShops.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("reg_verified_session_banner"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E8FF)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFC4B5FD))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = HishabpatiPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (AppLanguageManager.isEnglish()) {
                                        "Verified Account Session (Existing UID Preserved)"
                                    } else {
                                        "যাচাইকৃত অ্যাকাউন্ট সেশন (বিদ্যমান UID সংরক্ষিত)"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF3B0764)
                                )
                                Text(
                                    text = "${activeSessionUser?.email ?: email.trim()} • UID: ${(activeSessionUser?.uid ?: "").take(10)}...",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF5B21B6)
                                )
                            }
                        }

                        if (recoveryStatusMessage.isNotBlank()) {
                            Surface(
                                color = Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = recoveryStatusMessage,
                                    color = Color(0xFF166534),
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        // Show all existing shops mapped to this Gmail / UID so old shop data is never lost
                        if (existingOwnedShops.isNotEmpty()) {
                            Text(
                                text = if (AppLanguageManager.isEnglish()) {
                                    "Existing Shops (${existingOwnedShops.size}):"
                                } else {
                                    "এই অ্যাকাউন্টের বিদ্যমান দোকান (${existingOwnedShops.size}টি):"
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color(0xFF3B0764)
                            )

                            existingOwnedShops.forEachIndexed { idx, existingBiz ->
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storefront,
                                            contentDescription = null,
                                            tint = HishabpatiPurple,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = existingBiz.businessName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = HishabpatiTextDark
                                            )
                                            Text(
                                                text = "${existingBiz.ownerPhone} • ID: ${existingBiz.id.take(14)}",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.selectShopAndProceed(existingBiz) { ok, msg, _ ->
                                                    if (ok) {
                                                        onRegistrationSuccess()
                                                    } else {
                                                        errorMessage = msg
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(34.dp)
                                                .testTag(if (idx == 0) "reg_open_existing_shop_button" else "reg_open_existing_shop_$idx")
                                        ) {
                                            Text(
                                                text = AppStrings.openExistingShopButton,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            if (!allowAdditionalShopForExistingOwner) {
                                OutlinedButton(
                                    onClick = {
                                        allowAdditionalShopForExistingOwner = true
                                        errorMessage = ""
                                        recoveryStatusMessage = if (AppLanguageManager.isEnglish()) {
                                            "Multi-shop mode enabled! Fill out the new shop details and submit—your existing shop(s) remain safe on the same UID."
                                        } else {
                                            "নতুন দোকান যোগ করার মোড চালু হয়েছে! নিচের ধাপগুলো পূরণ করে সাবমিট করুন—আপনার আগের দোকান ও তথ্য সম্পূর্ণ নিরাপদ থাকবে।"
                                        }
                                    },
                                    border = BorderStroke(1.dp, HishabpatiPurple),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("reg_add_additional_shop_button")
                                ) {
                                    Icon(Icons.Default.AddBusiness, contentDescription = null, tint = HishabpatiPurple, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = AppStrings.addNewShopSameGmailButton,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HishabpatiPurple
                                    )
                                }
                            } else {
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (AppLanguageManager.isEnglish()) {
                                                "Adding a new shop to your existing UID (all previous shops preserved)."
                                            } else {
                                                "বিদ্যমান UID-তে নতুন দোকান যুক্ত হচ্ছে (পুরোনো সব দোকান অপরিবর্তিত থাকবে)।"
                                            },
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF166534)
                                        )
                                    }
                                }
                            }
                        }

                        // Optional Safe Provider Linking (Links Password to Google UID or Google to Password UID without creating a second UID)
                        if (activeSessionUser != null) {
                            val activeProviderIds = activeSessionUser.providerData.mapNotNull { it.providerId }
                            val hasGoogleLinked = activeProviderIds.contains("google.com")
                            val hasPasswordLinked = activeProviderIds.contains("password")

                            if (hasGoogleLinked && !hasPasswordLinked && pin.length == 6) {
                                OutlinedButton(
                                    onClick = {
                                        isLinkingProvider = true
                                        viewModel.linkPasswordProviderToCurrentUser(pin.trim()) { ok, msg, updated ->
                                            isLinkingProvider = false
                                            if (ok) {
                                                authenticatedUser = updated ?: authenticatedUser
                                                recoveryStatusMessage = msg
                                            } else {
                                                errorMessage = msg
                                            }
                                        }
                                    },
                                    enabled = !isLinkingProvider,
                                    border = BorderStroke(1.dp, Color(0xFF7C3AED)),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .testTag("reg_link_password_button")
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = HishabpatiPurple, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (AppLanguageManager.isEnglish()) "Link 6-Digit PIN to Google UID" else "Google UID-তে ৬-সংখ্যার পিন/পাসওয়ার্ড লিংক করুন",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HishabpatiPurple
                                    )
                                }
                            } else if (hasPasswordLinked && !hasGoogleLinked) {
                                OutlinedButton(
                                    onClick = {
                                        isLinkingProvider = true
                                        GoogleSignInHelper.requestGoogleIdToken(
                                            context = context,
                                            scope = coroutineScope,
                                            onIdToken = { idToken ->
                                                viewModel.linkGoogleCredentialToCurrentUser(idToken) { ok, msg, updated ->
                                                    isLinkingProvider = false
                                                    if (ok) {
                                                        authenticatedUser = updated ?: authenticatedUser
                                                        recoveryStatusMessage = msg
                                                    } else {
                                                        errorMessage = msg
                                                    }
                                                }
                                            },
                                            onCancelled = { isLinkingProvider = false },
                                            onError = { err ->
                                                isLinkingProvider = false
                                                errorMessage = err
                                            }
                                        )
                                    },
                                    enabled = !isLinkingProvider,
                                    border = BorderStroke(1.dp, Color(0xFF7C3AED)),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .testTag("reg_link_google_button")
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = HishabpatiPurple, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (AppLanguageManager.isEnglish()) "Link Google Sign-In to Existing UID" else "বিদ্যমান UID-তে Google অ্যাকাউন্ট লিংক করুন",
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

            // --- 3. Step Specific Body Content ---
            when (currentStep) {
                // ==================== STEP 1: BUSINESS NAME ====================
                RegistrationStep.STEP_1_BUSINESS_NAME -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = HishabpatiCardBg),
                        border = BorderStroke(1.dp, HishabpatiCardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OutlinedTextField(
                                value = shopName,
                                onValueChange = {
                                    shopName = it
                                    errorMessage = ""
                                },
                                placeholder = { Text("ব্যবসা প্রতিষ্ঠানের নাম", color = TextMuted) },
                                leadingIcon = {
                                    Icon(Icons.Default.Storefront, contentDescription = null, tint = Color(0xFF718096))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = HishabpatiPurple,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_step1_shop_name_input")
                            )

                            // Referral Code Toggle Link (+ রেফারাল কোড যুক্ত করুন)
                            Row(
                                modifier = Modifier
                                    .clickable { showReferralInput = !showReferralInput }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (showReferralInput) Icons.Default.Remove else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = HishabpatiPurple,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showReferralInput) "রেফারাল কোড বাদ দিন" else "রেফারাল কোড যুক্ত করুন",
                                    color = HishabpatiPurple,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            AnimatedVisibility(visible = showReferralInput) {
                                OutlinedTextField(
                                    value = referralCode,
                                    onValueChange = { referralCode = it },
                                    placeholder = { Text("রেফারাল কোড (ঐচ্ছিক)", color = TextMuted) },
                                    leadingIcon = {
                                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Color(0xFF718096))
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        focusedBorderColor = HishabpatiPurple,
                                        unfocusedBorderColor = Color(0xFFCBD5E1)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f, fill = false))
                    Spacer(modifier = Modifier.height(40.dp))

                    // Bottom Navigation (Step 1)
                    Button(
                        onClick = {
                            if (shopName.trim().isBlank()) {
                                errorMessage = "দয়া করে আপনার ব্যবসা প্রতিষ্ঠানের নাম লিখুন!"
                            } else {
                                errorMessage = ""
                                currentStep = RegistrationStep.STEP_2_OWNER_NAME
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("reg_step1_next_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple)
                    ) {
                        Text(
                            text = "পরবর্তী",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "ইতিমধ্যে অ্যাকাউন্ট আছে? লগইন করুন",
                        color = HishabpatiPurple,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(8.dp)
                            .testTag("reg_goto_login_button")
                    )
                }

                // ==================== STEP 2: OWNER NAME ====================
                RegistrationStep.STEP_2_OWNER_NAME -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = HishabpatiCardBg),
                        border = BorderStroke(1.dp, HishabpatiCardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = {
                                    firstName = it
                                    errorMessage = ""
                                },
                                placeholder = { Text("নামের প্রথম অংশ", color = TextMuted) },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF718096))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = HishabpatiPurple,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_step2_first_name_input")
                            )

                            OutlinedTextField(
                                value = lastName,
                                onValueChange = {
                                    lastName = it
                                    errorMessage = ""
                                },
                                placeholder = { Text("নামের শেষ অংশ", color = TextMuted) },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF718096))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = HishabpatiPurple,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_step2_last_name_input")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f, fill = false))
                    Spacer(modifier = Modifier.height(40.dp))

                    // Bottom Navigation (Step 2: Previous & Next)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { currentStep = RegistrationStep.STEP_1_BUSINESS_NAME },
                            modifier = Modifier.testTag("reg_step2_prev_button")
                        ) {
                            Text(
                                text = "আগের ধাপ",
                                color = Color(0xFF4A5568),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Button(
                            onClick = {
                                if (firstName.trim().isBlank()) {
                                    errorMessage = "দয়া করে আপনার নামের প্রথম অংশ লিখুন!"
                                } else {
                                    errorMessage = ""
                                    currentStep = RegistrationStep.STEP_3_EMAIL_PHONE_PIN
                                }
                            },
                            modifier = Modifier
                                .widthIn(min = 150.dp)
                                .height(52.dp)
                                .testTag("reg_step2_next_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple)
                        ) {
                            Text(
                                text = "পরবর্তী",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // ==================== STEP 3: EMAIL, PHONE & PIN ====================
                RegistrationStep.STEP_3_EMAIL_PHONE_PIN -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = HishabpatiCardBg),
                        border = BorderStroke(1.dp, HishabpatiCardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = {
                                    email = it.replace(Regex("[\\u200B-\\u200D\\uFEFF\\u00A0\\u202F\\u00AD\\u200E\\u200F]"), "")
                                    errorMessage = ""
                                },
                                placeholder = { Text("ইমেইল (ঐচ্ছিক)", color = TextMuted) },
                                leadingIcon = {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF718096))
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = HishabpatiPurple,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_step3_email_input")
                            )

                            OutlinedTextField(
                                value = phone,
                                onValueChange = {
                                    phone = it
                                    errorMessage = ""
                                },
                                placeholder = { Text("ফোন নম্বর (যেমন: 017XXXXXXXX)", color = TextMuted) },
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF718096))
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = HishabpatiPurple,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_step3_phone_input")
                            )

                            // 6-Digit Security PIN Fields
                            OutlinedTextField(
                                value = pin,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                        pin = it
                                        errorMessage = ""
                                    }
                                },
                                placeholder = { Text("৬-সংখ্যার গোপন লগইন পিন", color = TextMuted) },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF718096))
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showPin = !showPin }) {
                                        Icon(
                                            imageVector = if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                            tint = Color(0xFF718096)
                                        )
                                    }
                                },
                                visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = HishabpatiPurple,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_step3_pin_input")
                            )

                            OutlinedTextField(
                                value = confirmPin,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                        confirmPin = it
                                        errorMessage = ""
                                    }
                                },
                                placeholder = { Text("৬-সংখ্যার পিন পুনরায় লিখুন", color = TextMuted) },
                                leadingIcon = {
                                    Icon(Icons.Default.LockClock, contentDescription = null, tint = Color(0xFF718096))
                                },
                                visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = HishabpatiPurple,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_step3_confirm_pin_input")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f, fill = false))
                    Spacer(modifier = Modifier.height(24.dp))

                    // Bottom Navigation (Step 3: Previous & Send Verification Code)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { currentStep = RegistrationStep.STEP_2_OWNER_NAME },
                            modifier = Modifier.testTag("reg_step3_prev_button")
                        ) {
                            Text(
                                text = "আগের ধাপ",
                                color = Color(0xFF4A5568),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Button(
                            onClick = triggerSendOtp,
                            enabled = !isSendingOtp && !isGoogleSigningUp,
                            modifier = Modifier
                                .widthIn(min = 160.dp)
                                .height(52.dp)
                                .testTag("reg_step3_send_code_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple)
                        ) {
                            if (isSendingOtp) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = "যাচাই কোড পাঠান",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Instant Google Sign-In Button (Allows existing Google users to register smoothly)
                    OutlinedButton(
                        onClick = triggerGoogleSignUp,
                        enabled = !isGoogleSigningUp && !isSendingOtp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("reg_google_signup_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF5F3FF)),
                        border = BorderStroke(1.dp, HishabpatiPurple)
                    ) {
                        if (isGoogleSigningUp) {
                            CircularProgressIndicator(color = HishabpatiPurple, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GoogleLogoMini(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Google দিয়ে যাচাই ও দোকান খুলুন",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HishabpatiPurple
                                )
                            }
                        }
                    }

                    // Inline Existing Account Recovery Card when Gmail already exists (e.g., anc94450@gmail.com)
                    val isAlreadySignedIntoEnteredEmail = authenticatedUser?.email?.trim()?.lowercase() == email.trim().lowercase() && email.trim().isNotBlank()
                    if (existingAccountDetected && !isAlreadySignedIntoEnteredEmail) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_existing_account_recovery_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                            border = BorderStroke(1.dp, Color(0xFFFCD34D))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ManageAccounts,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${AppStrings.existingAccountRecoveryTitle}: ${email.trim()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }

                                Text(
                                    text = if (existingAccountProviderType == AppErrorHandler.EmailAuthProviderType.GOOGLE_ONLY) {
                                        if (AppLanguageManager.isEnglish()) {
                                            "This Gmail was registered with Google Sign-In. Tap below to verify with Google and continue on your existing UID without losing any shop data."
                                        } else {
                                            "এই Gmail দিয়ে আগে Google Sign-In অ্যাকাউন্ট তৈরি হয়েছে। নতুন অ্যাকাউন্ট না বানিয়ে নিচের বাটনে ট্যাপ করে আপনার বিদ্যমান UID দিয়ে দোকান খুলুন বা আগের দোকানে প্রবেশ করুন।"
                                        }
                                    } else {
                                        if (AppLanguageManager.isEnglish()) {
                                            "This Gmail already exists in Firebase Auth. Verify using Google Sign-In OR your existing password below to recover your UID safely."
                                        } else {
                                            "এই Gmail দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট রয়েছে। নতুন অ্যাকাউন্ট তৈরি না করে Google Sign-In অথবা আপনার আগের পাসওয়ার্ড দিয়ে নিরাপদে অ্যাকাউন্ট পুনরুদ্ধার করুন।"
                                        }
                                    },
                                    fontSize = 12.sp,
                                    lineHeight = 16.5.sp,
                                    color = Color(0xFF78350F)
                                )

                                Button(
                                    onClick = triggerGoogleSignUp,
                                    enabled = !isGoogleSigningUp && !isVerifyingExistingPassword,
                                    colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("reg_recovery_google_button")
                                ) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (AppLanguageManager.isEnglish()) {
                                            "Verify with Google & Recover UID"
                                        } else {
                                            "Google দিয়ে যাচাই ও অ্যাকাউন্ট পুনরুদ্ধার"
                                        },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                if (existingAccountProviderType != AppErrorHandler.EmailAuthProviderType.GOOGLE_ONLY) {
                                    OutlinedTextField(
                                        value = existingPasswordInput,
                                        onValueChange = {
                                            existingPasswordInput = it
                                            errorMessage = ""
                                        },
                                        placeholder = {
                                            Text(
                                                text = if (AppLanguageManager.isEnglish()) "Enter existing account password/PIN" else "আগের অ্যাকাউন্টের পাসওয়ার্ড বা পিন দিন",
                                                fontSize = 12.5.sp,
                                                color = TextMuted
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Password, contentDescription = null, tint = Color(0xFF718096), modifier = Modifier.size(18.dp))
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { showExistingPassword = !showExistingPassword }) {
                                                Icon(
                                                    imageVector = if (showExistingPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = Color(0xFF718096)
                                                )
                                            }
                                        },
                                        visualTransformation = if (showExistingPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White,
                                            focusedBorderColor = HishabpatiPurple,
                                            unfocusedBorderColor = Color(0xFFCBD5E1)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("reg_recovery_password_input")
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            isVerifyingExistingPassword = true
                                            errorMessage = ""
                                            focusManager.clearFocus()
                                            viewModel.verifyExistingPasswordForRegistration(
                                                email = email.trim(),
                                                password = existingPasswordInput
                                            ) { ok, msg, recoveredUser, ownedShops ->
                                                isVerifyingExistingPassword = false
                                                if (ok && recoveredUser != null) {
                                                    authenticatedUser = recoveredUser
                                                    existingOwnedShops = ownedShops
                                                    existingAccountProviderType = viewModel.classifyProviderIds(
                                                        recoveredUser.providerData.mapNotNull { it.providerId }
                                                    )
                                                    if (ownedShops.isEmpty() || allowAdditionalShopForExistingOwner) {
                                                        // Register new shop on recovered UID without creating a duplicate Auth user
                                                        if (shopName.isNotBlank() && PhoneAuthHelper.isValidBangladeshPhone(phone.trim()) && pin.length == 6 && pin == confirmPin) {
                                                            isVerifyingOtp = true
                                                            coroutineScope.launch {
                                                                viewModel.registerNewShop(
                                                                    shopName = shopName.trim(),
                                                                    ownerName = fullOwnerName.ifBlank { recoveredUser.displayName ?: "দোকান স্বত্বাধিকারী" },
                                                                    phone = phone.trim(),
                                                                    address = address.ifBlank { district },
                                                                    district = district,
                                                                    businessType = selectedBusinessType,
                                                                    pin = pin.trim(),
                                                                    isPhoneVerified = false,
                                                                    ownerEmail = recoveredUser.email ?: email.trim().lowercase(),
                                                                    ownerUid = recoveredUser.uid,
                                                                    allowAdditionalShop = true,
                                                                    superAdminViewModel = superAdminViewModel
                                                                ) { regOk, regMsg, shopId ->
                                                                    isVerifyingOtp = false
                                                                    if (regOk && shopId != null) {
                                                                        successRegisteredShopId = shopId
                                                                    } else {
                                                                        errorMessage = regMsg
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            recoveryStatusMessage = msg
                                                        }
                                                    } else {
                                                        recoveryStatusMessage = msg
                                                    }
                                                } else {
                                                    errorMessage = msg
                                                }
                                            }
                                        },
                                        enabled = !isVerifyingExistingPassword,
                                        border = BorderStroke(1.dp, Color(0xFFB45309)),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                            .testTag("reg_recovery_verify_password_button")
                                    ) {
                                        if (isVerifyingExistingPassword) {
                                            CircularProgressIndicator(color = Color(0xFFB45309), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        } else {
                                            Text(
                                                text = if (AppLanguageManager.isEnglish()) {
                                                    "Verify Existing Password & Continue"
                                                } else {
                                                    "আগের পাসওয়ার্ড যাচাই করে এগিয়ে যান"
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB45309)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Disclaimer text matching screenshot
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "রেজিস্ট্রেশন করার মাধ্যমে আপনি আমাদের ",
                            fontSize = 11.sp,
                            color = Color(0xFF718096)
                        )
                        Text(
                            text = "শর্তাবলীতে",
                            fontSize = 11.sp,
                            color = HishabpatiPurple,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { showTermsDialog = true }
                        )
                        Text(
                            text = " সম্মতি প্রকাশ করছেন।",
                            fontSize = 11.sp,
                            color = Color(0xFF718096)
                        )
                    }
                }

                // ==================== STEP 4: VERIFICATION (OTP) ====================
                RegistrationStep.STEP_4_VERIFICATION -> {
                    // Method Toggle Tabs: ইমেইল | ফোন (Matching Screenshot 4)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { verificationMethodTab = 0 }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ইমেইল",
                                fontWeight = if (verificationMethodTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (verificationMethodTab == 0) HishabpatiPurple else Color(0xFF718096),
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(3.dp)
                                    .background(if (verificationMethodTab == 0) HishabpatiPurple else Color.Transparent)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { verificationMethodTab = 1 }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ফোন",
                                fontWeight = if (verificationMethodTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (verificationMethodTab == 1) HishabpatiPurple else Color(0xFF718096),
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(3.dp)
                                    .background(if (verificationMethodTab == 1) HishabpatiPurple else Color.Transparent)
                            )
                        }
                    }

                    // Verification Card (Matching Screenshot 4)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = HishabpatiCardBg),
                        border = BorderStroke(1.dp, HishabpatiCardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Top Contact Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(HishabpatiPurpleLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (verificationMethodTab == 1) Icons.Default.Phone else Icons.Default.Email,
                                        contentDescription = null,
                                        tint = HishabpatiPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = maskedContactDisplay,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = HishabpatiTextDark
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "আপনার জিমেইলে (${email.trim()}) একটি অফিশিয়াল ভেরিফিকেশন লিঙ্ক পাঠানো হয়েছে।",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HishabpatiTextDark,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "১. আপনার জিমেইল অ্যাপ বা ব্রাউজার খুলুন।\n২. গুগল থেকে আসা 'Verify your email' লিংকে ক্লিক করুন।\n৩. তারপর নিচের 'যাচাই সম্পন্ন করেছি' বাটনে চাপ দিন।",
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFF4A5568),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Resend Countdown Timer
                            if (countdownSeconds > 0) {
                                Text(
                                    text = "${formatTimerBengali(countdownSeconds)} সেকেন্ড পরে আবার লিংক পাঠানো যাবে",
                                    color = HishabpatiPurple,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "পুনরায় ভেরিফিকেশন লিংক পাঠান",
                                    color = HishabpatiPurple,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier
                                        .clickable {
                                            countdownSeconds = 60
                                            viewModel.resendVerificationEmail { success, msg ->
                                                if (success) {
                                                    errorMessage = ""
                                                } else {
                                                    errorMessage = msg
                                                }
                                            }
                                        }
                                        .padding(4.dp)
                                        .testTag("reg_resend_otp_button")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f, fill = false))
                    Spacer(modifier = Modifier.height(30.dp))

                    // Bottom Navigation (Step 4: Verify & Register)
                    Button(
                        onClick = triggerVerifyAndRegister,
                        enabled = !isVerifyingOtp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("reg_verify_and_finish_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple)
                    ) {
                        if (isVerifyingOtp) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "যাচাই সম্পন্ন করেছি — দোকান খুলুন",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { currentStep = RegistrationStep.STEP_3_EMAIL_PHONE_PIN },
                        modifier = Modifier.testTag("reg_step4_prev_button")
                    ) {
                        Text(
                            text = "আগের ধাপ",
                            color = Color(0xFF4A5568),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // --- Terms Dialog ---
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Text(
                    text = "SmartHishabpati ব্যবহারের শর্তাবলী",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = HishabpatiPurple
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "১. SmartHishabpati একটি হিসাবরক্ষণ ও ইনভেন্টরি ম্যানেজমেন্ট অ্যাপ।\n" +
                                "২. আপনার দোকানের হিসাব ও কাস্টমার ডেটা ক্লাউড ফায়ারস্টোরে নিরাপদভাবে সংরক্ষিত থাকবে।\n" +
                                "৩. গোপনীয় পিন ও অথেনটিকেশন তথ্য কাউকে শেয়ার করবেন না।\n" +
                                "৪. কোনো প্রকার মাসিক ফি ছাড়াই আজীবন বেসিক হিসাবরক্ষণ সম্পূর্ণ ফ্রি ব্যবহার করতে পারবেন।",
                        fontSize = 12.sp,
                        color = Color(0xFF334155),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("সম্মত আছি", fontWeight = FontWeight.Bold, color = HishabpatiPurple)
                }
            }
        )
    }

    // --- Success Dialog (Rendered ONLY after successful registration) ---
    successRegisteredShopId?.let { shopId ->
        AlertDialog(
            onDismissRequest = { /* Must tap continue */ },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(54.dp)
                )
            },
            title = {
                Text(
                    text = "🎉 অভিনন্দন!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    color = HishabpatiPurple
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SmartHishabpati-তে আপনাকে স্বাগতম। আপনার দোকানের অ্যাকাউন্ট সফলভাবে তৈরি হয়েছে। এখন আপনি হিসাবরক্ষণ শুরু করতে পারবেন।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF334155),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Surface(
                        color = HishabpatiCardBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HishabpatiCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("দোকানের নাম:", fontSize = 12.sp, color = TextMuted)
                                Text(shopName.trim(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = HishabpatiTextDark)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("স্বত্বাধিকারী:", fontSize = 12.sp, color = TextMuted)
                                Text(fullOwnerName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = HishabpatiPurple)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("মোবাইল:", fontSize = 12.sp, color = TextMuted)
                                Text(phone.trim(), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = HishabpatiTextDark)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("দোকান কোড (ID):", fontSize = 11.sp, color = TextMuted)
                                Text(shopId, fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        successRegisteredShopId = null
                        onRegistrationSuccess()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("reg_success_continue_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HishabpatiPurple)
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("আমার দোকানে প্রবেশ করুন", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        )
    }
}
