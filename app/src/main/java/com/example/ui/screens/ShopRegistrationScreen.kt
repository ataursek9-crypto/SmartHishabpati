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
import com.example.data.repository.SuperAdminRepository
import com.example.ui.theme.*
import com.example.util.GoogleSignInHelper
import com.example.util.PhoneAuthHelper
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

    // UI Feedback
    var errorMessage by remember { mutableStateOf("") }
    var successRegisteredShopId by remember { mutableStateOf<String?>(null) }

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

    // --- Action: Send Verification or Create Firebase User ---
    val triggerSendOtp: () -> Unit = {
        errorMessage = ""
        focusManager.clearFocus()

        val cleanEmail = email.trim().lowercase()
        val cleanPhone = phone.trim()

        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            errorMessage = "দয়া করে আপনার সঠিক জিমেইল / ইমেইল ঠিকানা দিন!"
        } else if (cleanPhone.length < 11) {
            errorMessage = "সঠিক ১১ ডিজিটের মোবাইল নম্বর দিন (যেমন: 017XXXXXXXX)!"
        } else if (pin.length < 4) {
            errorMessage = "অনুগ্রহ করে কমপক্ষে ৪ সংখ্যার গোপন পিন বা পাসওয়ার্ড দিন!"
        } else if (pin != confirmPin) {
            errorMessage = "পিন এবং পিন নিশ্চিতকরণ মিলছে না!"
        } else {
            isSendingOtp = true
            // If user chose Gmail verification, create Firebase user & send real link
            val registrationPassword = if (pin.length >= 6) pin else "${pin}99"
            viewModel.registerWithEmailPassword(cleanEmail, registrationPassword) { success, msg, user ->
                isSendingOtp = false
                if (success && user != null) {
                    authenticatedUser = user
                    countdownSeconds = 60
                    currentStep = RegistrationStep.STEP_4_VERIFICATION
                } else {
                    errorMessage = msg
                }
            }
        }
    }

    // --- Action: Check Real Email Verification & Complete Shop Registration ---
    val triggerVerifyAndRegister: () -> Unit = {
        errorMessage = ""
        isVerifyingOtp = true
        focusManager.clearFocus()

        // Enforce REAL Firebase verification check
        viewModel.checkEmailVerificationStatus { isSuccess, isVerified, msg ->
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
                        isPhoneVerified = false, // Phone SMS unverified as billing is disabled
                        ownerEmail = currentEmail,
                        ownerUid = currentUid,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "রেজিস্ট্রেশন",
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
                            contentDescription = "ফিরে যান",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HishabpatiPurple
                )
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
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
                        .padding(bottom = 14.dp),
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
                                    email = it
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

                            // 4-Digit Security PIN Fields
                            OutlinedTextField(
                                value = pin,
                                onValueChange = {
                                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                        pin = it
                                        errorMessage = ""
                                    }
                                },
                                placeholder = { Text("৪-সংখ্যার গোপন লগইন পিন", color = TextMuted) },
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
                                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                        confirmPin = it
                                        errorMessage = ""
                                    }
                                },
                                placeholder = { Text("পিন পুনরায় লিখুন", color = TextMuted) },
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
                            enabled = !isSendingOtp,
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
