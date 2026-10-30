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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.*
import com.example.util.GoogleSignInHelper
import com.example.viewmodel.BusinessViewModel

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

    // Form State
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
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
    var showHelpDialog by remember { mutableStateOf(false) }

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
                    viewModel.saveRememberedPhone(trimmedPhone)
                    onLoginSuccess()
                } else {
                    errorMessage = msg
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
                    text = "  হিসাব রাখুন স্মার্টভাবে  ",
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
                            text = "আপনার হিসাব নিরাপদ ও সুরক্ষিত",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color(0xFF1E1B4B)
                        )
                        Text(
                            text = "সহজে লগইন করুন এবং ব্যবসা পরিচালনা করুন",
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
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
                        Text(text = bannerText, color = Color(0xFF991B1B), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                    }
                }
            }

            // =================================================================
            // 4. LOGIN FORM CARD (Mobile, PIN, Login Button)
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
                    // Mobile Number Input
                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it
                            errorMessage = ""
                        },
                        placeholder = { Text("মোবাইল নম্বর", color = Color(0xFF94A3B8)) },
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

                    // 4-Digit PIN Input
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pin = it
                                errorMessage = ""
                            }
                        },
                        placeholder = { Text("৪-সংখ্যার পিন", color = Color(0xFF94A3B8)) },
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

                    // Large Purple Login Button (Matching Reference)
                    Button(
                        onClick = triggerLogin,
                        enabled = !isLoading && phone.isNotBlank() && pin.isNotBlank(),
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
                                    text = "লগইন করুন",
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
                    text = "  অথবা  ",
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
                            text = "Google দিয়ে লগইন করুন",
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
                        text = "পিন ভুলে গেছেন?",
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
                        text = "সাহায্য প্রয়োজন?",
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
                            text = "নতুন অ্যাকাউন্ট তৈরি করুন",
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
