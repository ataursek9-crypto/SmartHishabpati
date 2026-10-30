package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.*
import com.example.data.repository.SuperAdminRepository
import com.example.ui.theme.PrimaryNavy
import com.example.util.BengaliFormatter
import com.example.util.GoogleSignInHelper
import com.google.firebase.auth.FirebaseAuth
import com.example.viewmodel.SuperAdminTab
import com.example.viewmodel.SuperAdminViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminDashboardScreen(
    currentSuperAdminEmail: String,
    onBackToStoreAdmin: () -> Unit,
    viewModel: SuperAdminViewModel = viewModel(
        factory = SuperAdminViewModel.provideFactory(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val isAuthorized = remember(currentSuperAdminEmail) {
        SuperAdminRepository.isSuperAdminEmail(currentSuperAdminEmail) || SuperAdminRepository.isSuperAdminPhone(currentSuperAdminEmail)
    }

    if (!isAuthorized) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("অননুমোদিত প্রবেশাধিকার", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackToStoreAdmin) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "ফিরে যান")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "সুপার এডমিন অনুমতি নেই",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "আপনার বর্তমান ইমেইল ($currentSuperAdminEmail) প্ল্যাটফর্ম সুপার এডমিন তালিকার অন্তর্ভুক্ত নয়। এই প্যানেলটি শুধুমাত্র প্ল্যাটফর্মের মূল পরিচালকের জন্য সংরক্ষিত।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onBackToStoreAdmin,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("স্টোর এডমিন প্যানেলে ফিরে যান")
                        }
                    }
                }
            }
        }
        return
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stats by viewModel.overviewStats.collectAsStateWithLifecycle()
    val businesses by viewModel.filteredBusinesses.collectAsStateWithLifecycle()
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val payments by viewModel.filteredPayments.collectAsStateWithLifecycle()
    val auditLogs by viewModel.filteredAuditLogs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val actorUid = remember { "super_admin_${currentSuperAdminEmail.take(8)}" }
    val actorName = remember { "সুপার এডমিন" }

    // Dialog States
    var businessToManage by remember { mutableStateOf<PlatformBusiness?>(null) }
    var businessToSuspend by remember { mutableStateOf<PlatformBusiness?>(null) }
    var businessToChangePlan by remember { mutableStateOf<PlatformBusiness?>(null) }
    var businessToSendNotice by remember { mutableStateOf<PlatformBusiness?>(null) }
    var businessToSchedulePolicy by remember { mutableStateOf<PlatformBusiness?>(null) }
    var paymentToReview by remember { mutableStateOf<PlatformPayment?>(null) }
    var planToEdit by remember { mutableStateOf<SubscriptionPlan?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var firebaseAuthUser by remember {
        mutableStateOf(try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null })
    }
    var isGoogleSigningIn by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    // Auto-refresh businesses from Cloud Firestore on entry
    LaunchedEffect(Unit) {
        firebaseAuthUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        viewModel.refreshBusinesses()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "সুপার এডমিন কনসোল",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "PLATFORM OWNER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = currentSuperAdminEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshBusinesses() },
                        modifier = Modifier.testTag("super_admin_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "দোকানের তালিকা রিফ্রেশ করুন",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = onBackToStoreAdmin,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("দোকানে ফিরুন", fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = uiState.selectedTab == SuperAdminTab.OVERVIEW,
                    onClick = { viewModel.selectTab(SuperAdminTab.OVERVIEW) },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "ওভারভিউ") },
                    label = { Text("ওভারভিউ", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == SuperAdminTab.BUSINESSES,
                    onClick = { viewModel.selectTab(SuperAdminTab.BUSINESSES) },
                    icon = { Icon(Icons.Default.Business, contentDescription = "ব্যবসা") },
                    label = { Text("ব্যবসা", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == SuperAdminTab.PLANS,
                    onClick = { viewModel.selectTab(SuperAdminTab.PLANS) },
                    icon = { Icon(Icons.Default.CardMembership, contentDescription = "প্ল্যান") },
                    label = { Text("প্ল্যান", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == SuperAdminTab.PAYMENTS,
                    onClick = { viewModel.selectTab(SuperAdminTab.PAYMENTS) },
                    icon = {
                        BadgedBox(badge = {
                            if (stats.pendingPaymentsCount > 0) {
                                Badge { Text(stats.pendingPaymentsCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.Payment, contentDescription = "পেমেন্ট")
                        }
                    },
                    label = { Text("পেমেন্ট", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == SuperAdminTab.AUDIT_LOGS,
                    onClick = { viewModel.selectTab(SuperAdminTab.AUDIT_LOGS) },
                    icon = { Icon(Icons.Default.History, contentDescription = "অডিট লগ") },
                    label = { Text("অডিট", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == SuperAdminTab.SETTINGS,
                    onClick = { viewModel.selectTab(SuperAdminTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "সেটিংস") },
                    label = { Text("সেটিংস", fontSize = 11.sp) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Auth Status Strip
            Surface(
                color = if (firebaseAuthUser != null && SuperAdminRepository.isSuperAdminEmail(firebaseAuthUser?.email)) Color(0xFFF1F8E9) else Color(0xFFFFF8E1),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isAuthed = firebaseAuthUser != null && SuperAdminRepository.isSuperAdminEmail(firebaseAuthUser?.email)
                    Icon(
                        imageVector = if (isAuthed) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (isAuthed) Color(0xFF2E7D32) else Color(0xFFF57F17),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAuthed) "ক্লাউড ডেটাবেস সংযুক্ত: smart-business-manager-71879" else "ক্লাউড ডেটাবেস অ্যাক্সেস অফলাইন",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isAuthed) Color(0xFF1B5E20) else Color(0xFFE65100)
                        )
                        Text(
                            text = if (firebaseAuthUser != null) {
                                "অ্যাকাউন্ট: ${firebaseAuthUser?.email} (UID: ${firebaseAuthUser?.uid?.take(8)}... | ভেরিফাইড: ${firebaseAuthUser?.isEmailVerified})"
                            } else {
                                "ফায়ারবেস অথেনটিকেশন সক্রিয় নেই। লাইভ ডেটা পড়তে সাইন-ইন প্রয়োজন।"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isAuthed) {
                        Button(
                            onClick = {
                                isGoogleSigningIn = true
                                GoogleSignInHelper.launchGoogleSignIn(
                                    context = context,
                                    scope = coroutineScope,
                                    onAuthSuccess = { fUser ->
                                        isGoogleSigningIn = false
                                        firebaseAuthUser = fUser
                                        viewModel.refreshBusinesses()
                                    },
                                    onCancelled = { isGoogleSigningIn = false },
                                    onError = { isGoogleSigningIn = false }
                                )
                            },
                            enabled = !isGoogleSigningIn,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp).testTag("super_admin_inline_google_signin_button")
                        ) {
                            if (isGoogleSigningIn) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("গুগল সাইন-ইন", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Live Feedback Banner
            if (uiState.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearFeedback() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন", modifier = Modifier.size(16.dp))
                            }
                        }

                        // Action buttons if error is permission or auth related
                        if (uiState.errorMessage?.contains("গুগল সাইন-ইন", ignoreCase = true) == true ||
                            uiState.errorMessage?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        isGoogleSigningIn = true
                                        GoogleSignInHelper.launchGoogleSignIn(
                                            context = context,
                                            scope = coroutineScope,
                                            onAuthSuccess = { fUser ->
                                                isGoogleSigningIn = false
                                                firebaseAuthUser = fUser
                                                viewModel.refreshBusinesses()
                                            },
                                            onCancelled = { isGoogleSigningIn = false },
                                            onError = { isGoogleSigningIn = false }
                                        )
                                    },
                                    enabled = !isGoogleSigningIn,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp).testTag("super_admin_banner_google_signin_button")
                                ) {
                                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("অনুমোদিত একাউন্টে সাইন-ইন", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.refreshBusinesses() },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp).testTag("super_admin_retry_refresh_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("পুনরায় লোড", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else if (uiState.successMessage != null) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.clearFeedback() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.successMessage ?: "",
                            color = Color(0xFF1B5E20),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearFeedback() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন", modifier = Modifier.size(16.dp), tint = Color(0xFF1B5E20))
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (uiState.selectedTab) {
                    SuperAdminTab.OVERVIEW -> {
                        SuperAdminOverviewTab(
                            stats = stats,
                            onNavigateToBusinesses = { viewModel.selectTab(SuperAdminTab.BUSINESSES) },
                            onNavigateToPayments = { viewModel.selectTab(SuperAdminTab.PAYMENTS) }
                        )
                    }
                    SuperAdminTab.BUSINESSES -> {
                        SuperAdminBusinessesTab(
                            businesses = businesses,
                            searchQuery = uiState.searchQuery,
                            statusFilter = uiState.businessStatusFilter,
                            onSearchChange = { viewModel.updateSearchQuery(it) },
                            onStatusFilterChange = { viewModel.setBusinessStatusFilter(it) },
                            onManageBusiness = { businessToManage = it },
                            onRefresh = { viewModel.refreshBusinesses() }
                        )
                    }
                SuperAdminTab.PLANS -> {
                    SuperAdminPlansTab(
                        plans = plans,
                        onEditPlan = { planToEdit = it }
                    )
                }
                SuperAdminTab.PAYMENTS -> {
                    SuperAdminPaymentsTab(
                        payments = payments,
                        statusFilter = uiState.paymentStatusFilter,
                        onStatusFilterChange = { viewModel.setPaymentStatusFilter(it) },
                        onReviewPayment = { paymentToReview = it }
                    )
                }
                SuperAdminTab.AUDIT_LOGS -> {
                    SuperAdminAuditLogsTab(
                        logs = auditLogs,
                        searchQuery = uiState.searchQuery,
                        onSearchChange = { viewModel.updateSearchQuery(it) }
                    )
                }
                SuperAdminTab.SETTINGS -> {
                    SuperAdminSettingsTab(
                        settings = settings,
                        onSaveSettings = { newS ->
                            viewModel.saveSettings(newS, actorUid, actorName, currentSuperAdminEmail)
                        }
                    )
                }
            }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Business Management Dialog
    businessToManage?.let { b ->
        AlertDialog(
            onDismissRequest = { businessToManage = null },
            icon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(b.businessName, fontWeight = FontWeight.Bold) },
            text = {
                val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale("bn", "BD")) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("বিজনেস আইডি: ${b.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Text("মালিক: ${b.ownerName} (${b.ownerPhone})${if (b.phoneVerified) " ✓ মোবাইল যাচাইকৃত" else ""}")
                    if (b.ownerEmail.isNotBlank()) Text("ইমেইল: ${b.ownerEmail}", fontSize = 13.sp)
                    Text("ব্যবসার ধরন: ${b.businessType}", fontWeight = FontWeight.Medium)
                    if (b.address.isNotBlank() || b.district.isNotBlank()) {
                        Text("ঠিকানা: ${b.address}${if (b.district.isNotBlank()) ", ${b.district}" else ""}", fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BusinessStatusBadge(b.status)
                        SubscriptionStatusBadge(b.subscriptionStatus)
                    }
                    Text("বর্তমান প্ল্যান: ${b.planName} (কর্মী সীমা: ${b.maxStaffAllowed})")
                    Text("রেজিস্ট্রেশন: ${dateFormat.format(Date(b.createdAt))}", fontSize = 12.sp)
                    Text("প্রথম ব্যবহার: ${dateFormat.format(Date(b.firstUseAt))}  •  সর্বশেষ সক্রিয়: ${dateFormat.format(Date(b.lastActiveAt))}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text("ব্যবহৃত দিন (Elapsed): ${BengaliFormatter.formatNumber(b.elapsedDays)} দিন  •  সক্রিয় দিন (Active): ${BengaliFormatter.formatNumber(b.activeDays)} দিন", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    if (b.isAutoExpiryDisabled) {
                        Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp)) {
                            Text("বর্তমান পলিসি: সম্পূর্ণ ফ্রি ব্যবহার (অটো-এক্সপায়ার বন্ধ)", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    } else {
                        Text("মেয়াদ শেষ: ${dateFormat.format(Date(b.subscriptionExpiresAt))}", fontWeight = FontWeight.SemiBold)
                    }
                    if (b.hasActiveNotice) {
                        Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(6.dp)) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("প্রস্তাবিত মাসিক ফি: ৳${BengaliFormatter.formatCurrency(b.proposedFee)}", color = Color(0xFFE65100), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (b.proposedStartDate != null) {
                                    Text("কার্যকরের প্রস্তাবিত তারিখ: ${dateFormat.format(Date(b.proposedStartDate))}", color = Color(0xFFE65100), fontSize = 10.sp)
                                }
                                if (b.noticeReminderDate != null) {
                                    Text("রিমাইন্ডার তারিখ: ${dateFormat.format(Date(b.noticeReminderDate))}", color = Color(0xFFE65100), fontSize = 10.sp)
                                }
                                if (b.subscriptionNoticeNote.isNotBlank()) {
                                    Text("নোট: ${b.subscriptionNoticeNote}", color = Color(0xFFE65100), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    if (b.scheduledPolicyChange.isNotBlank()) {
                        Surface(color = Color(0xFFE1F5FE), shape = RoundedCornerShape(6.dp)) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("শিডিউলকৃত ভবিষ্যৎ পলিসি: ${b.scheduledPolicyChange}", color = Color(0xFF0277BD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (b.scheduledPolicyEffectiveAt != null) {
                                    Text("কার্যকরের নির্ধারিত তারিখ: ${dateFormat.format(Date(b.scheduledPolicyEffectiveAt))}", color = Color(0xFF0277BD), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    if (b.statusReason.isNotBlank()) {
                        Text("নোট / কারণ: ${b.statusReason}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                businessToSendNotice = b
                                businessToManage = null
                            },
                            modifier = Modifier.weight(1f).testTag("dialog_send_notice_button")
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("নোটিশ পাঠান", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                businessToSchedulePolicy = b
                                businessToManage = null
                            },
                            modifier = Modifier.weight(1f).testTag("dialog_schedule_policy_button")
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("পলিসি শিডিউল", fontSize = 12.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                businessToChangePlan = b
                                businessToManage = null
                            },
                            modifier = Modifier.weight(1f).testTag("dialog_change_plan_button")
                        ) {
                            Text("প্ল্যান পরিবর্তন", fontSize = 12.sp)
                        }
                        if (b.status == BusinessAccountStatus.ACTIVE) {
                            Button(
                                onClick = {
                                    businessToSuspend = b
                                    businessToManage = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f).testTag("dialog_suspend_button")
                            ) {
                                Text("সাময়িক স্থগিত", fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.activateBusiness(b, actorUid, actorName, currentSuperAdminEmail)
                                    businessToManage = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.weight(1f).testTag("dialog_activate_button")
                            ) {
                                Text("সক্রিয় করুন", fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { businessToManage = null }) { Text("বন্ধ করুন") }
            }
        )
    }

    // Notice Dialog
    businessToSendNotice?.let { b ->
        SendSubscriptionNoticeDialog(
            business = b,
            onConfirm = { proposedFee, startDate, reminderDate, note ->
                viewModel.sendSubscriptionNotice(
                    business = b,
                    proposedFee = proposedFee,
                    proposedStartDate = startDate,
                    reminderDate = reminderDate,
                    note = note,
                    actorUid = actorUid,
                    actorName = actorName,
                    actorEmail = currentSuperAdminEmail
                )
                businessToSendNotice = null
            },
            onDismiss = { businessToSendNotice = null }
        )
    }

    // Schedule Policy Dialog
    businessToSchedulePolicy?.let { b ->
        SchedulePolicyChangeDialog(
            business = b,
            onConfirm = { policyDesc, effectiveDate ->
                viewModel.schedulePolicyChange(
                    business = b,
                    policyDescription = policyDesc,
                    effectiveDate = effectiveDate,
                    actorUid = actorUid,
                    actorName = actorName,
                    actorEmail = currentSuperAdminEmail
                )
                businessToSchedulePolicy = null
            },
            onDismiss = { businessToSchedulePolicy = null }
        )
    }

    // 2. Suspend Dialog
    businessToSuspend?.let { b ->
        SuspendBusinessDialog(
            business = b,
            onConfirm = { reason ->
                viewModel.suspendBusiness(b, reason, actorUid, actorName, currentSuperAdminEmail)
                businessToSuspend = null
            },
            onDismiss = { businessToSuspend = null }
        )
    }

    // 3. Change Plan Dialog
    businessToChangePlan?.let { b ->
        ChangePlanDialog(
            business = b,
            plans = plans,
            onConfirm = { planId, days ->
                viewModel.changeBusinessPlan(b, planId, days, actorUid, actorName, currentSuperAdminEmail)
                businessToChangePlan = null
            },
            onDismiss = { businessToChangePlan = null }
        )
    }

    // 4. Review Payment Dialog
    paymentToReview?.let { p ->
        ReviewPaymentDialog(
            payment = p,
            onApprove = {
                viewModel.reviewPayment(p, isApproved = true, "", actorUid, actorName, currentSuperAdminEmail)
                paymentToReview = null
            },
            onReject = { reason ->
                viewModel.reviewPayment(p, isApproved = false, reason, actorUid, actorName, currentSuperAdminEmail)
                paymentToReview = null
            },
            onDismiss = { paymentToReview = null }
        )
    }

    // 5. Edit Plan Dialog
    planToEdit?.let { p ->
        var monthlyPriceStr by remember { mutableStateOf(p.priceMonthly.toInt().toString()) }
        var yearlyPriceStr by remember { mutableStateOf(p.priceYearly.toInt().toString()) }
        var maxStaffStr by remember { mutableStateOf(p.maxStaff.toString()) }
        var maxProductsStr by remember { mutableStateOf(p.maxProducts.toString()) }

        AlertDialog(
            onDismissRequest = { planToEdit = null },
            title = { Text("${p.nameBn} সম্পাদনা") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthlyPriceStr,
                        onValueChange = { monthlyPriceStr = it.filter { c -> c.isDigit() } },
                        label = { Text("মাসিক ফি (৳)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = yearlyPriceStr,
                        onValueChange = { yearlyPriceStr = it.filter { c -> c.isDigit() } },
                        label = { Text("বার্ষিক ফি (৳)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = maxStaffStr,
                        onValueChange = { maxStaffStr = it.filter { c -> c.isDigit() } },
                        label = { Text("সর্বোচ্চ কর্মী সংখ্যা") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = maxProductsStr,
                        onValueChange = { maxProductsStr = it.filter { c -> c.isDigit() } },
                        label = { Text("সর্বোচ্চ পণ্য সীমা") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updated = p.copy(
                        priceMonthly = monthlyPriceStr.toDoubleOrNull() ?: p.priceMonthly,
                        priceYearly = yearlyPriceStr.toDoubleOrNull() ?: p.priceYearly,
                        maxStaff = maxStaffStr.toIntOrNull() ?: p.maxStaff,
                        maxProducts = maxProductsStr.toIntOrNull() ?: p.maxProducts
                    )
                    viewModel.savePlan(updated, actorUid, actorName, currentSuperAdminEmail)
                    planToEdit = null
                }) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { planToEdit = null }) { Text("বাতিল") }
            }
        )
    }
}

// ==================== TABS ====================

@Composable
fun SuperAdminOverviewTab(
    stats: PlatformOverviewStats,
    onNavigateToBusinesses: () -> Unit,
    onNavigateToPayments: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "স্মার্ট বিজনেস প্ল্যাটফর্ম ওভারভিউ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "সকল দোকানের সাবস্ক্রিপশন, রাজস্ব ও অ্যাকাউন্ট স্বাস্থ্য একনজরে দেখুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }

        item {
            // Default Free Policy Live Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "বর্তমান পলিসি: সম্পূর্ণ ফ্রি ব্যবহার সক্রিয়",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1B5E20)
                        )
                        Text(
                            text = "সকল নিবন্ধিত দোকানদার কোনো জোরপূর্বক মেয়াদোত্তীর্ণ বা বাধ্যবাধকতা ছাড়াই আজীবন মৌলিক হিসাব-নিকাশ বিনামূল্যে ব্যবহার করতে পারছেন।",
                            fontSize = 11.sp,
                            color = Color(0xFF2E7D32),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Metrics Grid (Rows of 2)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    title = "মোট প্রতিষ্ঠান",
                    value = BengaliFormatter.formatNumber(stats.totalBusinesses),
                    subtitle = "সক্রিয়: ${BengaliFormatter.formatNumber(stats.activeBusinesses)} টি",
                    icon = Icons.Default.Storefront,
                    containerColor = Color(0xFFF1F8E9),
                    iconTint = Color(0xFF388E3C),
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "মোট রাজস্ব (যাচাইকৃত)",
                    value = "৳" + BengaliFormatter.formatCurrency(stats.verifiedRevenue),
                    subtitle = "অনুমোদিত পেমেন্ট",
                    icon = Icons.Default.MonetizationOn,
                    containerColor = Color(0xFFE8EAF6),
                    iconTint = Color(0xFF3F51B5),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricStatCard(
                    title = "সক্রিয় সাবস্ক্রিপশন",
                    value = BengaliFormatter.formatNumber(stats.activeSubscriptions),
                    subtitle = "মেয়াদোত্তীর্ণ: ${BengaliFormatter.formatNumber(stats.expiredSubscriptions)} টি",
                    icon = Icons.Default.CardMembership,
                    containerColor = Color(0xFFE3F2FD),
                    iconTint = Color(0xFF1976D2),
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "স্থগিত প্রতিষ্ঠান",
                    value = BengaliFormatter.formatNumber(stats.suspendedBusinesses),
                    subtitle = "নীতি পর্যালোচনাধীন",
                    icon = Icons.Default.Block,
                    containerColor = Color(0xFFFFEBEE),
                    iconTint = Color(0xFFD32F2F),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Pending Payments Callout
        if (stats.pendingPaymentsCount > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.clickable { onNavigateToPayments() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationImportant, contentDescription = null, tint = Color(0xFFE65100))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${BengaliFormatter.formatNumber(stats.pendingPaymentsCount)}টি নতুন পেমেন্ট অপেক্ষমান!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            Text(
                                text = "bKash/Nagad ট্রানজ্যাকশন আইডি যাচাই করে অনুমোদন করুন",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFE65100))
                    }
                }
            }
        }

        // Plan Distribution
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("প্ল্যানভিত্তিক দোকানের বণ্টন", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    stats.planDistribution.forEach { (planName, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(planName, style = MaterialTheme.typography.bodyMedium)
                            Text("${BengaliFormatter.formatNumber(count)} টি দোকান", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun SuperAdminBusinessesTab(
    businesses: List<PlatformBusiness>,
    searchQuery: String,
    statusFilter: BusinessAccountStatus?,
    onSearchChange: (String) -> Unit,
    onStatusFilterChange: (BusinessAccountStatus?) -> Unit,
    onManageBusiness: (PlatformBusiness) -> Unit,
    onRefresh: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search TextField
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("দোকানের নাম, মালিক বা ফোন নম্বর দিয়ে খুঁজুন...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { onStatusFilterChange(null) },
                    label = { Text("সকল (${businesses.size})") }
                )
            }
            item {
                val pendingCount = businesses.count { it.status == BusinessAccountStatus.PENDING }
                FilterChip(
                    selected = statusFilter == BusinessAccountStatus.PENDING,
                    onClick = { onStatusFilterChange(BusinessAccountStatus.PENDING) },
                    label = { Text("অপেক্ষমাণ ($pendingCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFF3E0),
                        selectedLabelColor = Color(0xFFE65100)
                    )
                )
            }
            item {
                FilterChip(
                    selected = statusFilter == BusinessAccountStatus.ACTIVE,
                    onClick = { onStatusFilterChange(BusinessAccountStatus.ACTIVE) },
                    label = { Text("সক্রিয়") }
                )
            }
            item {
                FilterChip(
                    selected = statusFilter == BusinessAccountStatus.SUSPENDED,
                    onClick = { onStatusFilterChange(BusinessAccountStatus.SUSPENDED) },
                    label = { Text("স্থগিত") }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (businesses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো ব্যবসা প্রতিষ্ঠান পাওয়া যায়নি।", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(businesses, key = { it.id }) { b ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onManageBusiness(b) }
                            .testTag("business_item_${b.id}"),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = b.businessName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                BusinessStatusBadge(b.status)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = b.businessType,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (b.district.isNotBlank() || b.address.isNotBlank()) {
                                    Text(
                                        text = "• ${if (b.district.isNotBlank()) b.district else b.address}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "মালিক: ${b.ownerName} • ${b.ownerPhone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ব্যবহৃত দিন: ${BengaliFormatter.formatNumber(b.elapsedDays)} দিন  |  সক্রিয় দিন: ${BengaliFormatter.formatNumber(b.activeDays)} দিন",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SubscriptionStatusBadge(b.subscriptionStatus)
                                    Surface(
                                        color = if (b.isAutoExpiryDisabled) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (b.isAutoExpiryDisabled) "সম্পূর্ণ ফ্রি ব্যবহার" else b.planName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (b.isAutoExpiryDisabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (b.hasActiveNotice) {
                                        Surface(
                                            color = Color(0xFFFFF3E0),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "প্রস্তাবিত: ৳${BengaliFormatter.formatCurrency(b.proposedFee)}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE65100),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                TextButton(onClick = { onManageBusiness(b) }) {
                                    Text("ব্যবস্থাপনা", fontSize = 12.sp)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuperAdminPlansTab(
    plans: List<SubscriptionPlan>,
    onEditPlan: (SubscriptionPlan) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "সাবস্ক্রিপশন প্ল্যান ও ফিচার নিয়ন্ত্রণ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "প্রতিটি প্ল্যানের মাসিক/বার্ষিক মূল্য ও কর্মী সীমা পরিবর্তন করা সম্ভব।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(plans, key = { it.id }) { p ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (p.isPopular) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(p.nameBn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(p.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        if (p.isPopular) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("জনপ্রিয়", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (p.priceMonthly == 0.0) "বিনামূল্যে"
                        else "৳${BengaliFormatter.formatNumber(p.priceMonthly.toInt())} / মাস  (৳${BengaliFormatter.formatNumber(p.priceYearly.toInt())} / বছর)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(p.descriptionBn, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("সীমাবদ্ধতা ও সুবিধা:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("• সর্বোচ্চ কর্মী: ${p.maxStaff} জন", fontSize = 12.sp)
                    Text("• সর্বোচ্চ পণ্য: ${p.maxProducts} টি", fontSize = 12.sp)
                    p.features.forEach { f ->
                        Text("✓ $f", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { onEditPlan(p) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("মূল্য ও সীমা সম্পাদনা")
                    }
                }
            }
        }
    }
}

@Composable
fun SuperAdminPaymentsTab(
    payments: List<PlatformPayment>,
    statusFilter: PlatformPaymentStatus?,
    onStatusFilterChange: (PlatformPaymentStatus?) -> Unit,
    onReviewPayment: (PlatformPayment) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale("bn", "BD")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Filter Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { onStatusFilterChange(null) },
                    label = { Text("সকল (${payments.size})") }
                )
            }
            item {
                FilterChip(
                    selected = statusFilter == PlatformPaymentStatus.PENDING,
                    onClick = { onStatusFilterChange(PlatformPaymentStatus.PENDING) },
                    label = { Text("অপেক্ষমান (Pending)") }
                )
            }
            item {
                FilterChip(
                    selected = statusFilter == PlatformPaymentStatus.APPROVED,
                    onClick = { onStatusFilterChange(PlatformPaymentStatus.APPROVED) },
                    label = { Text("অনুমোদিত") }
                )
            }
            item {
                FilterChip(
                    selected = statusFilter == PlatformPaymentStatus.REJECTED,
                    onClick = { onStatusFilterChange(PlatformPaymentStatus.REJECTED) },
                    label = { Text("বাতিলকৃত") }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (payments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো পেমেন্ট রেকর্ড নেই।", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(payments, key = { it.id }) { p ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = p.status == PlatformPaymentStatus.PENDING) {
                                onReviewPayment(p)
                            },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p.businessName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                val (bg, fg, label) = when (p.status) {
                                    PlatformPaymentStatus.PENDING -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), "অপেক্ষমান")
                                    PlatformPaymentStatus.APPROVED -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "অনুমোদিত")
                                    PlatformPaymentStatus.REJECTED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "বাতিল")
                                }
                                Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
                                    Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("পদ্ধতি: ${p.paymentMethod.name} • প্রেরক: ${p.senderNumber}", fontSize = 12.sp)
                                    Text("TrxID: ${p.transactionReference}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("৳${BengaliFormatter.formatCurrency(p.amount)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF2E7D32))
                                    Text(dateFormat.format(Date(p.submittedAt)), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }

                            if (p.status == PlatformPaymentStatus.PENDING) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onReviewPayment(p) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("পেমেন্ট যাচাই ও অনুমোদন করুন")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuperAdminAuditLogsTab(
    logs: List<PlatformAuditLog>,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale("bn", "BD")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("অডিট লগে অ্যাকশন বা প্রতিষ্ঠানের নাম খুঁজুন...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো অডিট লগ পাওয়া যায়নি।", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs, key = { it.id }) { log ->
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(log.action, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(dateFormat.format(Date(log.timestamp)), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("টার্গেট: ${log.targetBusinessName} (${log.targetBusinessId})", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("সম্পাদনকারী: ${log.actorName} (${log.actorEmail})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(log.details, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuperAdminSettingsTab(
    settings: PlatformSystemSettings,
    onSaveSettings: (PlatformSystemSettings) -> Unit
) {
    var bKashNumber by remember(settings) { mutableStateOf(settings.bKashMerchantNumber) }
    var nagadNumber by remember(settings) { mutableStateOf(settings.nagadMerchantNumber) }
    var supportPhone by remember(settings) { mutableStateOf(settings.supportPhone) }
    var supportEmail by remember(settings) { mutableStateOf(settings.supportEmail) }
    var isMaintenance by remember(settings) { mutableStateOf(settings.maintenanceMode) }
    var allowReg by remember(settings) { mutableStateOf(settings.allowNewRegistrations) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("প্ল্যাটফর্ম সিস্টেম স্বাস্থ্য", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ক্লাউড ব্যাকএন্ড সংযোগ")
                        Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp)) {
                            Text("সক্রিয় (Connected)", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("নিরাপত্তা রুলস স্ট্যাটাস")
                        Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp)) {
                            Text("সুরক্ষিত (Zero-Trust)", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("সাবস্ক্রিপশন মার্চেন্ট পেমেন্ট নম্বর", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = bKashNumber,
                        onValueChange = { bKashNumber = it },
                        label = { Text("bKash মার্চেন্ট / পার্সোনাল নম্বর") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nagadNumber,
                        onValueChange = { nagadNumber = it },
                        label = { Text("Nagad মার্চেন্ট / পার্সোনাল নম্বর") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supportPhone,
                        onValueChange = { supportPhone = it },
                        label = { Text("সাপোর্ট হেল্পলাইন নম্বর") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supportEmail,
                        onValueChange = { supportEmail = it },
                        label = { Text("সাপোর্ট ইমেইল") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("নতুন দোকান নিবন্ধন চালু রাখুন", fontWeight = FontWeight.SemiBold)
                            Text("বন্ধ করলে নতুন দোকান রেজিস্টার হতে পারবে না", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(checked = allowReg, onCheckedChange = { allowReg = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("মেইনটেন্যান্স মোড", fontWeight = FontWeight.SemiBold, color = if (isMaintenance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            Text("জরুরি রক্ষণাবেক্ষণের সময় সাময়িক বন্ধ", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(checked = isMaintenance, onCheckedChange = { isMaintenance = it })
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val updated = settings.copy(
                                bKashMerchantNumber = bKashNumber,
                                nagadMerchantNumber = nagadNumber,
                                supportPhone = supportPhone,
                                supportEmail = supportEmail,
                                allowNewRegistrations = allowReg,
                                maintenanceMode = isMaintenance
                            )
                            onSaveSettings(updated)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("সেটিংস সংরক্ষণ করুন")
                    }
                }
            }
        }
    }
}

@Composable
fun SendSubscriptionNoticeDialog(
    business: PlatformBusiness,
    onConfirm: (Double, Long?, Long?, String) -> Unit,
    onDismiss: () -> Unit
) {
    var feeStr by remember { mutableStateOf(if (business.proposedFee > 0.0) business.proposedFee.toInt().toString() else "500") }
    var effectiveDaysStr by remember { mutableStateOf("30") }
    var reminderDaysStr by remember { mutableStateOf("15") }
    var note by remember { mutableStateOf(business.subscriptionNoticeNote.ifBlank { "ভবিষ্যতে প্রো ফিচারের জন্য প্রস্তাবিত সাবস্ক্রিপশন প্যাকেজ।" }) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(36.dp))
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("সাবস্ক্রিপশন নোটিশ / প্রস্তাবনা", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(business.businessName, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Non-blocking Policy Notice Banner
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "সতর্কতা: এই নোটিশ শুধুমাত্র প্রস্তাবনা বা বার্তা হিসেবে যাবে। এটি কোনো অবস্থাতেই দোকানদারের ফ্রি ব্যবহারে স্বয়ংক্রিয় বাধা দেবে না এবং কোনো টাকা কাটবে না।",
                            fontSize = 11.sp,
                            color = Color(0xFF1B5E20),
                            lineHeight = 15.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = feeStr,
                    onValueChange = {
                        feeStr = it.filter { c -> c.isDigit() }
                        errorMessage = ""
                    },
                    label = { Text("প্রস্তাবিত মাসিক ফি (৳) *") },
                    placeholder = { Text("যেমন: ৫০০") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("notice_fee_input")
                )

                OutlinedTextField(
                    value = effectiveDaysStr,
                    onValueChange = { effectiveDaysStr = it.filter { c -> c.isDigit() } },
                    label = { Text("প্রস্তাবিত কার্যকরের সময় (কত দিন পর)") },
                    placeholder = { Text("যেমন: ৩০") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("notice_effective_days_input")
                )

                OutlinedTextField(
                    value = reminderDaysStr,
                    onValueChange = { reminderDaysStr = it.filter { c -> c.isDigit() } },
                    label = { Text("রিমাইন্ডার পাঠানোর সময় (কত দিন পর)") },
                    placeholder = { Text("যেমন: ১৫") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("notice_reminder_days_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("বিশেষ বার্তা / নোট") },
                    placeholder = { Text("দোকানদারের উদ্দেশ্যে বিশেষ বার্তা লিখুন...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("notice_note_input")
                )

                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fee = feeStr.toDoubleOrNull() ?: 0.0
                    if (fee <= 0.0) {
                        errorMessage = "সঠিক প্রস্তাবিত ফি নির্ধারণ করুন!"
                        return@Button
                    }
                    val effectiveDays = effectiveDaysStr.toLongOrNull() ?: 30L
                    val reminderDays = reminderDaysStr.toLongOrNull() ?: 15L

                    val startDate = System.currentTimeMillis() + (effectiveDays * 24L * 60 * 60 * 1000)
                    val reminderDate = System.currentTimeMillis() + (reminderDays * 24L * 60 * 60 * 1000)

                    onConfirm(fee, startDate, reminderDate, note.trim())
                },
                modifier = Modifier.testTag("confirm_send_notice_button")
            ) {
                Text("নোটিশ প্রেরণ করুন")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
fun SchedulePolicyChangeDialog(
    business: PlatformBusiness,
    onConfirm: (policyDescription: String, effectiveDate: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var policyText by remember { mutableStateOf(business.scheduledPolicyChange.ifBlank { "মাসিক প্রিমিয়াম সাবস্ক্রিপশন পলিসি কার্যকর" }) }
    var effectiveDaysStr by remember { mutableStateOf("30") }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ভবিষ্যতের পলিসি পরিবর্তন শিডিউল", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(business.businessName, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "সতর্কতা: এটি একটি নির্ধারিত ভবিষ্যত নীতিমালা। প্রস্তাবিত তারিখ আসার পূর্বে দোকানদারের ফ্রি ব্যবহার কোনো অবস্থাতেই বন্ধ হবে না।",
                            fontSize = 11.sp,
                            color = Color(0xFF1B5E20),
                            lineHeight = 15.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = policyText,
                    onValueChange = { policyText = it; errorMessage = "" },
                    label = { Text("পলিসি বিবরণ *") },
                    placeholder = { Text("যেমন: মাসিক প্রিমিয়াম সাবস্ক্রিপশন প্যাকেজ কার্যকর") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("schedule_policy_text_input")
                )

                OutlinedTextField(
                    value = effectiveDaysStr,
                    onValueChange = { effectiveDaysStr = it.filter { c -> c.isDigit() }; errorMessage = "" },
                    label = { Text("কত দিন পর কার্যকর হবে (দিন) *") },
                    placeholder = { Text("যেমন: ৩০") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("schedule_policy_days_input")
                )

                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanPolicy = policyText.trim()
                    val days = effectiveDaysStr.toLongOrNull() ?: 0L
                    if (cleanPolicy.isBlank()) {
                        errorMessage = "পলিসির বিবরণ লিখুন!"
                        return@Button
                    }
                    if (days <= 0) {
                        errorMessage = "সঠিক কার্যকরের দিন নির্ধারণ করুন (কমপক্ষে ১ দিন)!"
                        return@Button
                    }
                    val effectiveTime = System.currentTimeMillis() + (days * 24L * 60 * 60 * 1000)
                    onConfirm(cleanPolicy, effectiveTime)
                },
                modifier = Modifier.testTag("confirm_schedule_policy_button")
            ) {
                Text("পলিসি শিডিউল করুন")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

