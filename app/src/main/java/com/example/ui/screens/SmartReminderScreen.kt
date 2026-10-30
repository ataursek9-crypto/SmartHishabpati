package com.example.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.util.SmartNotificationHelper
import com.example.viewmodel.BusinessViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartReminderScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val reminders by viewModel.activeReminders.collectAsStateWithLifecycle()
    val preferences by viewModel.userReminderPreference.collectAsStateWithLifecycle()
    val isTtsSupported by viewModel.isBengaliTtsSupported.collectAsStateWithLifecycle()
    val ttsStatusMsg by viewModel.ttsStatusMessage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: কাজ ও সতর্কতা, 1: নতুন কাজ যোগ, 2: সেটিংস
    var selectedFilter by remember { mutableStateOf("PENDING") } // ALL, PENDING, COMPLETED, ALERTS, TASKS

    // Notification permission launcher for Android 13+
    var hasNotifPermission by remember { mutableStateOf(SmartNotificationHelper.hasNotificationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotifPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "নোটিফিকেশন অনুমতি সক্রিয় হয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "নোটিফিকেশন অনুমতি না থাকায় পুশ অ্যালার্ট পাওয়া যাবে না", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(currentUser) {
        viewModel.refreshSmartReminders()
    }

    Scaffold(
        topBar = {
            Surface(
                color = PrimaryNavy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("btn_reminder_back")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "ফিরে যান",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text(
                                    text = if (currentUser?.role == UserRole.ADMIN) "স্মার্ট রিমাইন্ডার ও কাজ" else "আমার রিমাইন্ডার",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "সতর্কবার্তা, হিসাব ও নির্ধারিত কার্যক্রম",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Instant Refresh / Scan button
                        IconButton(
                            onClick = {
                                viewModel.refreshSmartReminders()
                                Toast.makeText(context, "সিস্টেম স্ক্যান সম্পন্ন", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                .testTag("btn_refresh_reminders")
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "রিফ্রেশ করুন",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Top Navigation Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = PrimaryNavy,
                        contentColor = Color.White,
                        indicator = { tabPositions ->
                            if (selectedTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = SecondaryTeal,
                                    height = 3.dp
                                )
                            }
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("কাজ ও সতর্কতা", fontSize = 13.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_reminder_list")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("নতুন কাজ", fontSize = 13.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.AddAlarm, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_add_reminder")
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("সেটিংস ও ভয়েস", fontSize = 13.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_reminder_settings")
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> {
                    ReminderListView(
                        reminders = reminders,
                        selectedFilter = selectedFilter,
                        onFilterChange = { selectedFilter = it },
                        onNavigateToRoute = onNavigateToRoute,
                        onMarkCompleted = { viewModel.markReminderCompleted(it) },
                        onMarkDismissed = { viewModel.markReminderDismissed(it) },
                        onDelete = { viewModel.deleteReminder(it) },
                        onSpeak = { text -> viewModel.playVoiceTest(text) },
                        hasNotifPermission = hasNotifPermission,
                        onRequestNotifPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
                1 -> {
                    AddCustomReminderView(
                        currentUser = currentUser,
                        onSave = { title, desc, cat, prio, time ->
                            viewModel.addCustomReminder(title, desc, cat, prio, time)
                            selectedTab = 0
                        }
                    )
                }
                2 -> {
                    ReminderSettingsView(
                        preferences = preferences,
                        isTtsSupported = isTtsSupported,
                        ttsStatusMessage = ttsStatusMsg,
                        currentUser = currentUser,
                        onSavePreferences = { viewModel.updateReminderPreference(it) },
                        onTestVoice = { viewModel.playVoiceTest() },
                        onStopVoice = { viewModel.stopVoice() }
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderListView(
    reminders: List<ReminderItem>,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onMarkCompleted: (String) -> Unit,
    onMarkDismissed: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSpeak: (String) -> Unit,
    hasNotifPermission: Boolean,
    onRequestNotifPermission: () -> Unit
) {
    val filteredList = remember(reminders, selectedFilter) {
        when (selectedFilter) {
            "PENDING" -> reminders.filter { it.status == ReminderStatus.PENDING }
            "COMPLETED" -> reminders.filter { it.status == ReminderStatus.COMPLETED }
            "ALERTS" -> reminders.filter { it.isSystemGenerated && it.status == ReminderStatus.PENDING }
            "TASKS" -> reminders.filter { !it.isSystemGenerated }
            else -> reminders
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reminder_list_view"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Notification Permission Prompt if needed
        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = Color(0xFFE65100))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("নোটিফিকেশন অনুমতি দেওয়া হয়নি", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE65100))
                            Text("জরুরি স্টক ও হিসাবের এলার্ট পেতে অনুমতি দিন।", fontSize = 11.sp, color = Color.DarkGray)
                        }
                        Button(
                            onClick = onRequestNotifPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("অনুমতি দিন", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Summary Header & Count
        item {
            val pendingCount = reminders.count { it.status == ReminderStatus.PENDING }
            val completedCount = reminders.count { it.status == ReminderStatus.COMPLETED }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("অপেক্ষমাণ কাজ", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = BengaliFormatter.formatNumber(pendingCount.toDouble()),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pendingCount > 0) AccentAmber else AccentGreen
                        )
                    }
                    Divider(
                        modifier = Modifier
                            .height(36.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("সম্পন্ন কাজ", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = BengaliFormatter.formatNumber(completedCount.toDouble()),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                    }
                }
            }
        }

        // Filter Chips Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "PENDING",
                    onClick = { onFilterChange("PENDING") },
                    label = { Text("অপেক্ষমাণ (${reminders.count { it.status == ReminderStatus.PENDING }})", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "ALERTS",
                    onClick = { onFilterChange("ALERTS") },
                    label = { Text("সতর্কবার্তা", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "TASKS",
                    onClick = { onFilterChange("TASKS") },
                    label = { Text("আমার কাজ", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == "COMPLETED",
                    onClick = { onFilterChange("COMPLETED") },
                    label = { Text("সম্পন্ন", fontSize = 12.sp) }
                )
            }
        }

        // Empty state
        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "কোনো কাজ বা সতর্কবার্তা নেই",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "সব হিসাব ও কার্যক্রম হালনাগাদ রয়েছে।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.id }) { reminder ->
                ReminderItemCard(
                    reminder = reminder,
                    onNavigateToRoute = onNavigateToRoute,
                    onMarkCompleted = { onMarkCompleted(reminder.id) },
                    onMarkDismissed = { onMarkDismissed(reminder.id) },
                    onDelete = { onDelete(reminder.id) },
                    onSpeak = { onSpeak("${reminder.title}। ${reminder.description}") }
                )
            }
        }
    }
}

@Composable
fun ReminderItemCard(
    reminder: ReminderItem,
    onNavigateToRoute: (String) -> Unit,
    onMarkCompleted: () -> Unit,
    onMarkDismissed: () -> Unit,
    onDelete: () -> Unit,
    onSpeak: () -> Unit
) {
    val isPending = reminder.status == ReminderStatus.PENDING

    val priorityColor = when (reminder.priority) {
        ReminderPriority.HIGH -> AccentRed
        ReminderPriority.MEDIUM -> AccentAmber
        ReminderPriority.LOW -> PrimaryBlue
    }

    val priorityLabel = when (reminder.priority) {
        ReminderPriority.HIGH -> "জরুরি"
        ReminderPriority.MEDIUM -> "গুরুত্বপূর্ণ"
        ReminderPriority.LOW -> "সাধারণ"
    }

    val categoryIcon = when (reminder.category) {
        ReminderCategory.STOCK_ALERT -> Icons.Default.Warning
        ReminderCategory.EXPENSE_APPROVAL -> Icons.Default.AccountBalanceWallet
        ReminderCategory.PRODUCT_APPROVAL -> Icons.Default.FactCheck
        ReminderCategory.CUSTOMER_DUE -> Icons.Default.People
        ReminderCategory.DAILY_CLOSING -> Icons.Default.EventNote
        ReminderCategory.SALESMAN_FOLLOWUP -> Icons.Default.AssignmentInd
        ReminderCategory.CUSTOM_TASK -> Icons.Default.TaskAlt
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPending) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPending) 2.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_reminder_${reminder.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Category Icon, Title, and Priority Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = priorityColor.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(categoryIcon, contentDescription = null, tint = priorityColor, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = reminder.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val formattedTime = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(reminder.scheduledTime))
                        Text(
                            text = formattedTime,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Surface(
                    color = priorityColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = priorityLabel,
                        color = priorityColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Description Body
            Text(
                text = reminder.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Voice speech button
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "ভয়েস শুনুন",
                            tint = PrimaryNavy,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Direct Action button to route (e.g. Products, Expenses, Customers)
                    if (!reminder.targetRoute.isNullOrBlank()) {
                        val actionLabel = when (reminder.targetRoute) {
                            "PRODUCTS_STOCK" -> "স্টক দেখুন"
                            "EXPENSES" -> "খরচ দেখুন"
                            "ADMIN_PRODUCT_APPROVAL" -> "অনুমোদন করুন"
                            "SALESMAN_SUBMITTED_PRODUCTS" -> "প্রোডাক্ট দেখুন"
                            "CUSTOMERS" -> "কাস্টমারে যান"
                            "REPORTS" -> "রিপোর্ট দেখুন"
                            else -> "খোলা হোক"
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalButton(
                            onClick = { onNavigateToRoute(reminder.targetRoute) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Completion / Dismiss actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPending) {
                        TextButton(
                            onClick = onMarkDismissed,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("স্থগিত", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }

                        Button(
                            onClick = onMarkCompleted,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("সম্পন্ন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "মুছুন", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomReminderView(
    currentUser: User?,
    onSave: (String, String, ReminderCategory, ReminderPriority, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(ReminderPriority.MEDIUM) }
    var selectedCategory by remember { mutableStateOf(ReminderCategory.CUSTOM_TASK) }

    val calendar = remember { Calendar.getInstance() }
    var scheduledHour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY) + 1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("add_custom_reminder_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "নতুন কাজ বা রিমাইন্ডার নির্ধারণ",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = PrimaryNavy
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("কাজের শিরোনাম *") },
            placeholder = { Text("যেমন: মেসার্স রহিম স্টোরে ডেলিভারি ও টাকা আদায়") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_reminder_title"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("কাজের বিস্তারিত বিবরণ ও তাগাদার কারণ") },
            placeholder = { Text("যেমন: ইনভয়েস নং INV-01 এর অবশিষ্ট ৫,০০০ টাকা নেওয়ার তাগাদা দিন") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .testTag("input_reminder_desc"),
            shape = RoundedCornerShape(12.dp)
        )

        // Priority Selection
        Column {
            Text("অগ্রাধিকার নির্বাচন:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedPriority == ReminderPriority.HIGH,
                    onClick = { selectedPriority = ReminderPriority.HIGH },
                    label = { Text("জরুরি (লাল)", color = if (selectedPriority == ReminderPriority.HIGH) AccentRed else Color.Unspecified) }
                )
                FilterChip(
                    selected = selectedPriority == ReminderPriority.MEDIUM,
                    onClick = { selectedPriority = ReminderPriority.MEDIUM },
                    label = { Text("গুরুত্বপূর্ণ (হলুদ)") }
                )
                FilterChip(
                    selected = selectedPriority == ReminderPriority.LOW,
                    onClick = { selectedPriority = ReminderPriority.LOW },
                    label = { Text("সাধারণ (নীল)") }
                )
            }
        }

        // Category Selection
        Column {
            Text("ক্যাটাগরি:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedCategory == ReminderCategory.CUSTOM_TASK,
                    onClick = { selectedCategory = ReminderCategory.CUSTOM_TASK },
                    label = { Text("ব্যক্তিগত কাজ") }
                )
                FilterChip(
                    selected = selectedCategory == ReminderCategory.CUSTOMER_DUE,
                    onClick = { selectedCategory = ReminderCategory.CUSTOMER_DUE },
                    label = { Text("বকেয়া তাগাদা") }
                )
                if (currentUser?.role == UserRole.ADMIN) {
                    FilterChip(
                        selected = selectedCategory == ReminderCategory.DAILY_CLOSING,
                        onClick = { selectedCategory = ReminderCategory.DAILY_CLOSING },
                        label = { Text("হিসাব পরীক্ষা") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (title.isBlank()) return@Button
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, scheduledHour.coerceIn(0, 23))
                cal.set(Calendar.MINUTE, 0)
                onSave(title, description, selectedCategory, selectedPriority, cal.timeInMillis)
            },
            enabled = title.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_save_reminder"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
        ) {
            Icon(Icons.Default.AddAlarm, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("কাজটি সংরক্ষণ করুন", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ReminderSettingsView(
    preferences: ReminderPreference,
    isTtsSupported: Boolean,
    ttsStatusMessage: String,
    currentUser: User?,
    onSavePreferences: (ReminderPreference) -> Unit,
    onTestVoice: () -> Unit,
    onStopVoice: () -> Unit
) {
    var masterEnabled by remember(preferences) { mutableStateOf(preferences.isMasterEnabled) }
    var notifEnabled by remember(preferences) { mutableStateOf(preferences.isNotificationEnabled) }
    var voiceEnabled by remember(preferences) { mutableStateOf(preferences.isVoiceEnabled) }
    var quietHoursEnabled by remember(preferences) { mutableStateOf(preferences.quietHoursEnabled) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("reminder_settings_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "রিমাইন্ডার ও ভয়েস সেটিংস",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PrimaryNavy
            )
            Text(
                text = "সতর্কবার্তা, পুশ নোটিফিকেশন ও বাংলা ভয়েস নিয়ন্ত্রণ করুন",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // Master Switch Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("স্মার্ট রিমাইন্ডার মাস্টার সুইচ", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("বন্ধ করলে সব ধরনের নোটিফিকেশন ও ভয়েস বন্ধ থাকবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = masterEnabled,
                        onCheckedChange = {
                            masterEnabled = it
                            onSavePreferences(preferences.copy(isMasterEnabled = it))
                        },
                        modifier = Modifier.testTag("switch_master_reminder")
                    )
                }
            }
        }

        // Notification Switch Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Android পুশ নোটিফিকেশন", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("ডিভাইসের নোটিফিকেশন বারে সতর্কবার্তা পাঠানো", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = notifEnabled && masterEnabled,
                        enabled = masterEnabled,
                        onCheckedChange = {
                            notifEnabled = it
                            onSavePreferences(preferences.copy(isNotificationEnabled = it))
                        },
                        modifier = Modifier.testTag("switch_notif_reminder")
                    )
                }
            }
        }

        // Voice Switch Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("বাংলা ভয়েস রিমাইন্ডার (TTS)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("জরুরি স্টক বা এলার্ট এলে বাংলা ভয়েসে উচ্চারণ করবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = voiceEnabled && masterEnabled,
                            enabled = masterEnabled,
                            onCheckedChange = {
                                voiceEnabled = it
                                onSavePreferences(preferences.copy(isVoiceEnabled = it))
                            },
                            modifier = Modifier.testTag("switch_voice_reminder")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // TTS Status Indicator
                    Surface(
                        color = if (isTtsSupported) AccentGreen.copy(alpha = 0.12f) else AccentAmber.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isTtsSupported) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isTtsSupported) AccentGreen else AccentAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = ttsStatusMessage,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Test Voice button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onTestVoice,
                            modifier = Modifier.weight(1f).testTag("btn_test_voice")
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ভয়েস টেস্ট শুনুন", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onStopVoice,
                            modifier = Modifier.testTag("btn_stop_voice")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Quiet Hours (বিরক্ত না করার সময়)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("বিরক্ত না করার সময় (Quiet Hours)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("রাত ১০:০০ থেকে সকাল ০৭:০০ পর্যন্ত ভয়েস শব্দ স্বয়ংক্রিয়ভাবে বন্ধ থাকবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = quietHoursEnabled,
                        onCheckedChange = {
                            quietHoursEnabled = it
                            onSavePreferences(preferences.copy(quietHoursEnabled = it))
                        },
                        modifier = Modifier.testTag("switch_quiet_hours")
                    )
                }
            }
        }

        // Admin-only category control guidance
        if (currentUser?.role == UserRole.ADMIN) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryNavy)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("রোল ও গোপনীয়তা নিয়ন্ত্রণ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryNavy)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "সেলসম্যানেরা শুধুমাত্র তাদের নিজস্ব কাজ, কাস্টমার বকেয়া ও খরচের আপডেট দেখতে পাবেন। এডমিনের গোপন হিসাব ও সামগ্রিক রিপোর্ট সম্পূর্ণ সুরক্ষিত।",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}
