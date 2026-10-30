package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.example.data.model.AlertPriority
import com.example.data.model.SalesmanAlertView
import com.example.ui.theme.*
import com.example.viewmodel.BusinessViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesmanAlertScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val alerts by viewModel.salesmanAlerts.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, URGENT, PENDING, COMPLETED

    val filteredList = remember(alerts, selectedFilter) {
        when (selectedFilter) {
            "URGENT" -> alerts.filter { it.alert.priority == AlertPriority.URGENT && !it.receipt.isCompleted }
            "PENDING" -> alerts.filter { !it.receipt.isCompleted }
            "COMPLETED" -> alerts.filter { it.receipt.isCompleted }
            else -> alerts
        }
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("btn_salesman_alert_back")
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
                                text = "আমার অ্যালার্ট ও নির্দেশ",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "এডমিনের পাঠানো দিকনির্দেশনা ও করণীয় কাজ",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .testTag("salesman_alert_list_view"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary Banner
            item {
                val urgentCount = alerts.count { it.alert.priority == AlertPriority.URGENT && !it.receipt.isCompleted }
                val pendingCount = alerts.count { !it.receipt.isCompleted }

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
                            Text("জরুরি অ্যালার্ট", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "$urgentCount টি",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (urgentCount > 0) AccentRed else AccentGreen
                            )
                        }
                        Divider(
                            modifier = Modifier
                                .height(36.dp)
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("অপেক্ষমাণ কাজ", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "$pendingCount টি",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pendingCount > 0) AccentAmber else AccentGreen
                            )
                        }
                    }
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("সকল (${alerts.size})", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == "URGENT",
                        onClick = { selectedFilter = "URGENT" },
                        label = { Text("জরুরি", fontSize = 12.sp, color = if (selectedFilter == "URGENT") AccentRed else Color.Unspecified) }
                    )
                    FilterChip(
                        selected = selectedFilter == "PENDING",
                        onClick = { selectedFilter = "PENDING" },
                        label = { Text("অপেক্ষমাণ", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = selectedFilter == "COMPLETED",
                        onClick = { selectedFilter = "COMPLETED" },
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
                                text = "কোনো নতুন নির্দেশ নেই",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "সব নির্দেশ সম্পন্ন বা দেখা হয়েছে।",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.receipt.id }) { item ->
                    SalesmanAlertItemCard(
                        item = item,
                        onAcknowledge = {
                            viewModel.acknowledgeAlert(item.receipt.id) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onComplete = {
                            viewModel.completeAlert(item.receipt.id) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSpeak = {
                            viewModel.speakAlertText(item.alert)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SalesmanAlertItemCard(
    item: SalesmanAlertView,
    onAcknowledge: () -> Unit,
    onComplete: () -> Unit,
    onSpeak: () -> Unit
) {
    val alert = item.alert
    val receipt = item.receipt
    val isUrgent = alert.priority == AlertPriority.URGENT
    val priorityColor = if (isUrgent) AccentRed else PrimaryBlue

    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val timeFormatted = remember(alert.createdAt) { sdf.format(Date(alert.createdAt)) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (!receipt.isCompleted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!receipt.isCompleted) 2.dp else 0.dp),
        modifier = Modifier.fillMaxWidth().testTag("card_salesman_alert_${receipt.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Title & Priority Badge
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
                            text = "প্রেরক: ${alert.adminName} • $timeFormatted",
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

            // Message Body
            Text(
                text = alert.message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Status Row: Acknowledged & Completed tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (receipt.isCompleted) {
                        Surface(color = AccentGreen.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "✓ কাজ সম্পন্ন",
                                color = AccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else if (receipt.isAcknowledged) {
                        Surface(color = PrimaryBlue.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "✓ বুঝেছি",
                                color = PrimaryBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(color = AccentAmber.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "নতুন নির্দেশ",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Voice speaker button
                    IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "ভয়েস শুনুন", tint = PrimaryNavy, modifier = Modifier.size(20.dp))
                    }
                }

                // Action Buttons: "বুঝেছি" and "কাজ সম্পন্ন"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!receipt.isAcknowledged && !receipt.isCompleted) {
                        OutlinedButton(
                            onClick = onAcknowledge,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_ack_${receipt.id}")
                        ) {
                            Text("বুঝেছি", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (!receipt.isCompleted) {
                        Button(
                            onClick = onComplete,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_complete_${receipt.id}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("কাজ সম্পন্ন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
