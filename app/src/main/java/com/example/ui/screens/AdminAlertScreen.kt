package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.example.viewmodel.BusinessViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAlertScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allUsers by viewModel.users.collectAsStateWithLifecycle()
    val activeSalesmen = remember(allUsers) {
        allUsers.filter { it.role == UserRole.SALESMAN && it.isActive && it.accountStatus == UserAccountStatus.ACTIVE }
    }
    val alertDetails by viewModel.adminAlertDetails.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: নির্দেশ পাঠান, 1: প্রেরিত নির্দেশের খতিয়ান

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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("btn_admin_alert_back")
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
                                text = "সেলসম্যান অ্যালার্ট ও নির্দেশ",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "সেলসম্যানদের সরাসরি নির্দেশনা ও ট্র্যাকিং",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

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
                            text = { Text("নতুন নির্দেশ পাঠান", fontSize = 13.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_send_alert")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("নির্দেশের খতিয়ান (${alertDetails.size})", fontSize = 13.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                            icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_alert_history")
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
                    AdminAlertFormView(
                        activeSalesmen = activeSalesmen,
                        onSendAlert = { targetType, targetSalesman, title, message, priority, isVoice, isNotif, scheduledTime ->
                            viewModel.sendAlert(
                                targetType = targetType,
                                targetSalesman = targetSalesman,
                                title = title,
                                message = message,
                                priority = priority,
                                isVoiceEnabled = isVoice,
                                isNotificationEnabled = isNotif,
                                scheduledTime = scheduledTime
                            ) { success, msg ->
                                if (success) {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    selectedTab = 1
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onTestVoice = { sample -> viewModel.playVoiceTest(sample) }
                    )
                }
                1 -> {
                    AdminAlertHistoryView(
                        alertDetails = alertDetails,
                        onCancelAlert = { alertId ->
                            viewModel.cancelAlert(alertId) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSpeakAlert = { alert -> viewModel.speakAlertText(alert) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAlertFormView(
    activeSalesmen: List<User>,
    onSendAlert: (AlertTargetType, User?, String, String, AlertPriority, Boolean, Boolean, Long?) -> Unit,
    onTestVoice: (String) -> Unit
) {
    var targetType by remember { mutableStateOf(AlertTargetType.ALL) }
    var selectedSalesman by remember { mutableStateOf<User?>(activeSalesmen.firstOrNull()) }
    var priority by remember { mutableStateOf(AlertPriority.GENERAL) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isVoiceEnabled by remember { mutableStateOf(true) }
    var isNotificationEnabled by remember { mutableStateOf(true) }
    var isScheduled by remember { mutableStateOf(false) }

    // Schedule delay in hours (e.g. 1 hour later, 2 hours later, or evening)
    var scheduleHoursOffset by remember { mutableIntStateOf(1) }

    var showConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("admin_alert_form_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Quick Guidance Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "সেলসম্যানদের প্রয়োজনীয় দিকনির্দেশনা সরাসরি তাদের অ্যাপে নোটিফিকেশন ও ভয়েসে পৌঁছে দিন।",
                        fontSize = 12.sp,
                        color = PrimaryNavy
                    )
                }
            }
        }

        // Recipient Selection
        item {
            Text("প্রাপক নির্বাচন করুন *", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = targetType == AlertTargetType.ALL,
                    onClick = { targetType = AlertTargetType.ALL },
                    label = { Text("সকল সেলসম্যান (${activeSalesmen.size} জন)") },
                    leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("chip_target_all")
                )
                FilterChip(
                    selected = targetType == AlertTargetType.SPECIFIC,
                    onClick = { targetType = AlertTargetType.SPECIFIC },
                    label = { Text("নির্দিষ্ট সেলসম্যান") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("chip_target_specific")
                )
            }

            if (targetType == AlertTargetType.SPECIFIC) {
                Spacer(modifier = Modifier.height(8.dp))
                if (activeSalesmen.isEmpty()) {
                    Text("কোনো সক্রিয় সেলসম্যান নেই।", fontSize = 12.sp, color = AccentRed)
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSalesman?.let { "${it.name} (${it.phone})" } ?: "সেলসম্যান নির্বাচন করুন",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("সেলসম্যান") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("dropdown_salesman_selector"),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            activeSalesmen.forEach { sm ->
                                DropdownMenuItem(
                                    text = { Text("${sm.name} - ${sm.phone}") },
                                    onClick = {
                                        selectedSalesman = sm
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Priority Selection
        item {
            Text("অ্যালার্টের ধরন / অগ্রাধিকার *", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = priority == AlertPriority.GENERAL,
                    onClick = { priority = AlertPriority.GENERAL },
                    label = { Text("সাধারণ নির্দেশ") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("chip_priority_general")
                )
                FilterChip(
                    selected = priority == AlertPriority.URGENT,
                    onClick = { priority = AlertPriority.URGENT },
                    label = { Text("🚨 জরুরি অ্যালার্ট", fontWeight = FontWeight.Bold, color = if (priority == AlertPriority.URGENT) AccentRed else Color.Unspecified) },
                    leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("chip_priority_urgent")
                )
            }
        }

        // Title and Message Input
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("নির্দেশের শিরোনাম *") },
                placeholder = { Text("যেমন: আজকের বিক্রির হিসাব জমা দিন") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_alert_title"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("বিস্তারিত নির্দেশনা *") },
                placeholder = { Text("যেমন: আজকের সব বিক্রি ও টাকা আদায়ের হিসাব রাত ৮টার মধ্যে অ্যাপে জমা দিন।") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .testTag("input_alert_message"),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Quick Preset Suggestions
        item {
            Column {
                Text("দ্রুত নির্বাচন (প্রিসেট নির্দেশ):", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionChip(
                        onClick = {
                            title = "আজকের বিক্রির হিসাব জমা দিন"
                            message = "আজকের সব বিক্রি ও টাকা আদায়ের হিসাব রাত ৮টার মধ্যে অ্যাপে জমা দিন।"
                        },
                        label = { Text("বিক্রির হিসাব", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            title = "বকেয়া তাগাদা ও কালেকশন"
                            message = "গুরুত্বপূর্ণ কাস্টমারদের বকেয়া দ্রুত আদায় করে ক্যাশ ব্যালেন্স আপডেট করুন।"
                        },
                        label = { Text("বকেয়া তাগাদা", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = {
                            title = "জরুরি ডেলিভারি ফলোআপ"
                            message = "আজকের নির্ধারিত পণ্য ডেলিভারি সম্পন্ন করে ইনভয়েস নিশ্চিত করুন।"
                            priority = AlertPriority.URGENT
                        },
                        label = { Text("জরুরি ডেলিভারি", fontSize = 11.sp) }
                    )
                }
            }
        }

        // Options: Voice & Notification
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("বাংলা ভয়েস নির্দেশ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("সেলসম্যানের ফোনে বাংলায় পড়ে শোনাবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = isVoiceEnabled,
                            onCheckedChange = { isVoiceEnabled = it },
                            modifier = Modifier.testTag("switch_alert_voice")
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Android নোটিফিকেশন", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("সেলসম্যানের নোটিফিকেশন বারে সতর্কবার্তা পাঠাবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = isNotificationEnabled,
                            onCheckedChange = { isNotificationEnabled = it },
                            modifier = Modifier.testTag("switch_alert_notif")
                        )
                    }
                }
            }
        }

        // Schedule Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("নির্দিষ্ট সময়ে পাঠানোর জন্য Schedule করুন", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(if (isScheduled) "নির্ধারিত সময়ের আগে পাঠানো হবে না" else "এখনই তাৎক্ষণিকভাবে পাঠানো হবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = isScheduled,
                            onCheckedChange = { isScheduled = it },
                            modifier = Modifier.testTag("switch_alert_schedule")
                        )
                    }

                    if (isScheduled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("কখন পাঠানো হবে:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = scheduleHoursOffset == 1,
                                onClick = { scheduleHoursOffset = 1 },
                                label = { Text("১ ঘণ্টা পর") }
                            )
                            FilterChip(
                                selected = scheduleHoursOffset == 2,
                                onClick = { scheduleHoursOffset = 2 },
                                label = { Text("২ ঘণ্টা পর") }
                            )
                            FilterChip(
                                selected = scheduleHoursOffset == 5,
                                onClick = { scheduleHoursOffset = 5 },
                                label = { Text("৫ ঘণ্টা পর") }
                            )
                        }
                    }
                }
            }
        }

        // Voice Test Button
        if (title.isNotBlank() || message.isNotBlank()) {
            item {
                OutlinedButton(
                    onClick = {
                        val prefix = if (priority == AlertPriority.URGENT) "অ্যাডমিনের জরুরি নির্দেশ।" else "অ্যাডমিনের নির্দেশ।"
                        onTestVoice("$prefix ${title.ifBlank { "আজকের বিক্রির হিসাব জমা দিন" }}। ${message.ifBlank { "হিসাব জমা দিন" }}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ভয়েস পরীক্ষা করুন (উচ্চারণ শুনুন)", fontSize = 13.sp)
                }
            }
        }

        // Submit Button
        item {
            Button(
                onClick = {
                    if (title.isBlank() || message.isBlank()) return@Button
                    showConfirmDialog = true
                },
                enabled = title.isNotBlank() && message.isNotBlank() && (targetType == AlertTargetType.ALL || selectedSalesman != null),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_submit_alert"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (priority == AlertPriority.URGENT) AccentRed else PrimaryNavy
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScheduled) "নির্দেশ শিডিউল করুন" else "নির্দেশ পাঠান",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Confirmation Dialog before Sending
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (priority == AlertPriority.URGENT) Icons.Default.Warning else Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = if (priority == AlertPriority.URGENT) AccentRed else PrimaryBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isScheduled) "শিডিউল নিশ্চিতকরণ" else "নির্দেশ পাঠানো নিশ্চিতকরণ",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "প্রাপক: ${if (targetType == AlertTargetType.ALL) "সকল সেলসম্যান (${activeSalesmen.size} জন)" else "${selectedSalesman?.name} (${selectedSalesman?.phone})"}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("ধরন: ${if (priority == AlertPriority.URGENT) "🚨 জরুরি" else "সাধারণ নির্দেশ"}")
                    Text("শিরোনাম: $title", fontWeight = FontWeight.Bold)
                    Text("বার্তা: $message")
                    if (isScheduled) {
                        Text("সময়: $scheduleHoursOffset ঘণ্টা পর পাঠানো হবে", color = AccentAmber, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        val schedTimestamp = if (isScheduled) {
                            System.currentTimeMillis() + (scheduleHoursOffset * 60 * 60 * 1000L)
                        } else null
                        onSendAlert(targetType, selectedSalesman, title, message, priority, isVoiceEnabled, isNotificationEnabled, schedTimestamp)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (priority == AlertPriority.URGENT) AccentRed else PrimaryNavy)
                ) {
                    Text("হ্যাঁ, পাঠান", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

@Composable
fun AdminAlertHistoryView(
    alertDetails: List<AdminAlertDetail>,
    onCancelAlert: (String) -> Unit,
    onSpeakAlert: (AdminAlert) -> Unit
) {
    if (alertDetails.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("পূর্বে কোনো নির্দেশ পাঠানো হয়নি", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("সেলসম্যানদের নতুন নির্দেশ পাঠাতে 'নতুন নির্দেশ পাঠান' ট্যাবে যান।", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("admin_alert_history_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(alertDetails, key = { it.alert.id }) { item ->
                AdminAlertHistoryCard(
                    detail = item,
                    onCancel = { onCancelAlert(item.alert.id) },
                    onSpeak = { onSpeakAlert(item.alert) }
                )
            }
        }
    }
}

@Composable
fun AdminAlertHistoryCard(
    detail: AdminAlertDetail,
    onCancel: () -> Unit,
    onSpeak: () -> Unit
) {
    val alert = detail.alert
    val receipts = detail.receipts

    val isUrgent = alert.priority == AlertPriority.URGENT
    val priorityColor = if (isUrgent) AccentRed else PrimaryBlue

    val ackCount = receipts.count { it.isAcknowledged }
    val compCount = receipts.count { it.isCompleted }
    val totalCount = receipts.size

    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val timeFormatted = remember(alert.createdAt) { sdf.format(Date(alert.createdAt)) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("card_alert_history_${alert.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title, Priority, Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = priorityColor.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isUrgent) Icons.Default.Warning else Icons.Default.Campaign,
                                contentDescription = null,
                                tint = priorityColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = alert.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "প্রেরণ: $timeFormatted",
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
                        text = if (isUrgent) "🚨 জরুরি" else "সাধারণ",
                        color = priorityColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Message text
            Text(
                text = alert.message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Target Recipient & Status
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "প্রাপক: ${if (alert.targetType == AlertTargetType.ALL) "সকল সেলসম্যান ($totalCount জন)" else "${alert.targetSalesmanName ?: "নির্দিষ্ট সেলসম্যান"}"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (alert.isScheduled && !alert.isCancelled) {
                        val schedTime = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(alert.scheduledTime))
                        Text(
                            text = "শিডিউল সময়: $schedTime (অপেক্ষমাণ)",
                            fontSize = 11.sp,
                            color = AccentAmber,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (alert.isCancelled) {
                        Text("স্ট্যাটাস: বাতিল করা হয়েছে", fontSize = 11.sp, color = AccentRed, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            text = "বুঝেছেন: $ackCount/$totalCount জন • কাজ সম্পন্ন: $compCount/$totalCount জন",
                            fontSize = 11.sp,
                            color = if (compCount == totalCount && totalCount > 0) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Per Salesman Detailed Checklist
            if (receipts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    receipts.forEach { rcpt ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${rcpt.salesmanName}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (rcpt.isAcknowledged) {
                                    Text("✓ বুঝেছি", fontSize = 10.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("অপেক্ষমাণ", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                if (rcpt.isCompleted) {
                                    Text("✓ সম্পন্ন", fontSize = 10.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions: Speak & Cancel (if scheduled)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "ভয়েস শুনুন", tint = PrimaryNavy, modifier = Modifier.size(20.dp))
                }

                if (alert.isScheduled && !alert.isCancelled) {
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentRed)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("শিডিউল বাতিল", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
