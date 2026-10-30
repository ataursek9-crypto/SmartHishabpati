package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.repository.AdminMismatchAction
import com.example.util.BengaliFormatter
import com.example.viewmodel.BusinessViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStockControlScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    val stats by viewModel.stockControlSummaryStats.collectAsState()
    val productItems by viewModel.productVerificationItems.collectAsState()
    val allVerifications by viewModel.allStockVerifications.collectAsState()
    val pendingApprovals by viewModel.pendingStockApprovals.collectAsState()
    val allUsers by viewModel.users.collectAsState()
    val salesmen = remember(allUsers) { allUsers.filter { it.role == UserRole.SALESMAN && it.isActive } }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: পণ্য ও শিডিউল, 1: অমিল অনুমোদন, 2: ইতিহাস
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterStatus by remember { mutableStateOf("ALL") }

    // Dialog states
    var productForHistory by remember { mutableStateOf<Product?>(null) }
    var verificationForReview by remember { mutableStateOf<StockVerification?>(null) }
    var productForSchedule by remember { mutableStateOf<ProductVerificationItem?>(null) }
    var showSendAlertDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("স্টক কন্ট্রোল সেন্টার", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("দোকানের স্টক মিলানো ও নিরীক্ষা", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_admin_stock_control_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSendAlertDialog = true },
                        modifier = Modifier.testTag("btn_send_stock_alert")
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = "যাচাইয়ের নির্দেশ পাঠান", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Dashboard Summary Cards
            StockControlStatsHeader(stats = stats)

            // Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("পণ্য ও স্থিতি", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                            if (stats.overdueCount > 0) {
                                Spacer(Modifier.width(4.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("${stats.overdueCount}", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("অনুমোদনের অপেক্ষায়", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            if (stats.pendingAdminApprovalCount > 0) {
                                Spacer(Modifier.width(4.dp))
                                Badge(containerColor = Color(0xFFE65100)) {
                                    Text("${stats.pendingAdminApprovalCount}", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("যাচাইয়ের ইতিহাস", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // Tab 1: Products & Schedules
                    ProductStockVerificationTab(
                        productItems = productItems,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onViewProductHistory = { productForHistory = it },
                        onConfigureSchedule = { productForSchedule = it }
                    )
                }
                1 -> {
                    // Tab 2: Pending Mismatches & Approval
                    PendingMismatchesTab(
                        pendingApprovals = pendingApprovals,
                        onReview = { verificationForReview = it }
                    )
                }
                2 -> {
                    // Tab 3: Complete Verification History
                    VerificationHistoryTab(
                        allVerifications = allVerifications,
                        onViewDetails = { /* Can view full details */ }
                    )
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Single Product Detailed History Dialog
    productForHistory?.let { product ->
        val productVerifications = remember(product, allVerifications) {
            allVerifications.filter { it.productId == product.id }
        }
        ProductHistoryDialog(
            product = product,
            verifications = productVerifications,
            onDismiss = { productForHistory = null }
        )
    }

    // 2. Mismatch Review / Approval Dialog
    verificationForReview?.let { verification ->
        AdminMismatchReviewDialog(
            verification = verification,
            onDismiss = { verificationForReview = null },
            onSubmitReview = { action, adminNote ->
                viewModel.reviewStockMismatch(
                    verificationId = verification.id,
                    action = action,
                    adminNote = adminNote
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        verificationForReview = null
                    }
                }
            }
        )
    }

    // 3. Schedule Configuration Dialog
    productForSchedule?.let { item ->
        ConfigureScheduleDialog(
            item = item,
            salesmen = salesmen,
            onDismiss = { productForSchedule = null },
            onSave = { intervalDays, assignedSalesmanId, assignedSalesmanName, reminderEnabled ->
                viewModel.configureProductSchedule(
                    productId = item.product.id,
                    intervalDays = intervalDays,
                    assignedSalesmanId = assignedSalesmanId,
                    assignedSalesmanName = assignedSalesmanName,
                    isReminderEnabled = reminderEnabled
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        productForSchedule = null
                    }
                }
            }
        )
    }

    // 4. Send Verification Alert Dialog
    if (showSendAlertDialog) {
        SendVerificationAlertDialog(
            salesmen = salesmen,
            onDismiss = { showSendAlertDialog = false },
            onSend = { targetSalesmanId, targetSalesmanName, title, message, priority, isVoiceEnabled ->
                viewModel.sendStockVerificationAlert(
                    targetSalesmanId = targetSalesmanId,
                    targetSalesmanName = targetSalesmanName,
                    title = title,
                    message = message,
                    priority = priority,
                    isVoiceEnabled = isVoiceEnabled
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        showSendAlertDialog = false
                    }
                }
            }
        )
    }
}

@Composable
private fun StockControlStatsHeader(stats: StockControlSummaryStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "সার্বক্ষণিক নিরীক্ষা সারাংশ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                val lastDateStr = stats.lastVerificationTime?.let {
                    SimpleDateFormat("dd/MM hh:mm a", Locale.getDefault()).format(Date(it))
                } ?: "কখনো হয়নি"
                Text(
                    "সর্বশেষ: $lastDateStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "মোট পণ্য",
                    value = "${stats.totalProducts}",
                    color = MaterialTheme.colorScheme.primary
                )
                StatMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "আজ যাচাই",
                    value = "${stats.verifiedTodayCount}",
                    color = Color(0xFF2E7D32)
                )
                StatMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "যাচাই বাকি/দেরি",
                    value = "${stats.pendingVerificationCount}",
                    color = if (stats.overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "অনুমোদন বাকি",
                    value = "${stats.pendingAdminApprovalCount}",
                    color = if (stats.pendingAdminApprovalCount > 0) Color(0xFFE65100) else Color(0xFF757575)
                )
            }
        }
    }
}

@Composable
private fun StatMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProductStockVerificationTab(
    productItems: List<ProductVerificationItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onViewProductHistory: (Product) -> Unit,
    onConfigureSchedule: (ProductVerificationItem) -> Unit
) {
    val filteredItems = remember(productItems, searchQuery) {
        if (searchQuery.isBlank()) productItems
        else productItems.filter {
            it.product.name.contains(searchQuery, ignoreCase = true) ||
            it.product.sku.contains(searchQuery, ignoreCase = true) ||
            it.product.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("input_search_product_stock"),
            placeholder = { Text("পণ্যের নাম, SKU বা ক্যাটাগরি দিয়ে খুঁজুন...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "মুছুন")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (filteredItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো পণ্য পাওয়া যায়নি", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredItems, key = { it.product.id }) { item ->
                    ProductVerificationRowCard(
                        item = item,
                        onViewHistory = { onViewProductHistory(item.product) },
                        onConfigureSchedule = { onConfigureSchedule(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductVerificationRowCard(
    item: ProductVerificationItem,
    onViewHistory: () -> Unit,
    onConfigureSchedule: () -> Unit
) {
    val product = item.product
    val schedule = item.schedule
    val lastVerif = item.lastVerification

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewHistory() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "ক্যাটাগরি: ${product.category} | SKU: ${product.sku.ifBlank { "N/A" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Chip
                if (item.pendingMismatch != null) {
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "অমিল অপেক্ষমাণ",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (item.isOverdue) {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "যাচাই মেয়াদোত্তীর্ণ",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color(0xFFE65100),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (lastVerif != null) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "মিলেছে",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color(0xFF2E7D32),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "যাচাই বাকি",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "বর্তমান অ্যাপ স্টক: ${BengaliFormatter.formatNumber(product.currentStock)} ${product.unit}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val lastDateStr = if (item.daysSinceLastVerification >= 0) {
                        "${BengaliFormatter.formatNumber(item.daysSinceLastVerification)} দিন আগে (${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(lastVerif?.verifiedAt ?: 0L))})"
                    } else {
                        "কখনো যাচাই হয়নি"
                    }
                    Text(
                        "সর্বশেষ যাচাই: $lastDateStr",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(
                        onClick = onConfigureSchedule,
                        modifier = Modifier.testTag("btn_configure_schedule_${product.id}")
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = "শিডিউল পরিবর্তন", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = onViewHistory,
                        modifier = Modifier.testTag("btn_view_history_${product.id}")
                    ) {
                        Icon(Icons.Default.History, contentDescription = "ইতিহাস দেখুন", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingMismatchesTab(
    pendingApprovals: List<StockVerification>,
    onReview: (StockVerification) -> Unit
) {
    if (pendingApprovals.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))
                Text("কোনো অমিল অনুমোদনের অপেক্ষায় নেই!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("দোকানের সব পণ্য ঠিকঠাক মিলেছে বা সমাধান হয়েছে।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(pendingApprovals, key = { it.id }) { verif ->
                PendingMismatchCard(verif = verif, onReview = { onReview(verif) })
            }
        }
    }
}

@Composable
private fun PendingMismatchCard(
    verif: StockVerification,
    onReview: () -> Unit
) {
    val isShortage = verif.difference < 0
    val diffText = if (isShortage) "ঘাটতি: ${verif.difference} ${verif.productUnit}" else "অতিরিক্ত: +${verif.difference} ${verif.productUnit}"
    val diffColor = if (isShortage) MaterialTheme.colorScheme.error else Color(0xFFE65100)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(verif.productName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "যাচাইকারী: ${verif.verifierUserName} (${if (verif.verifierRole == UserRole.ADMIN) "এডমিন" else "সেলসম্যান"})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = diffColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        diffText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = diffColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("অ্যাপের স্টক", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${verif.appStockSnapshot} ${verif.productUnit}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text("➔", modifier = Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("বাস্তব গণনা", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${verif.physicalStock} ${verif.productUnit}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = diffColor)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("পার্থক্য", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${verif.difference}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = diffColor)
                }
            }

            if (verif.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("মন্তব্য: ${verif.notes}", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_review_mismatch_${verif.id}"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("পর্যালোচনা ও সিদ্ধান্ত নিন")
            }
        }
    }
}

@Composable
private fun VerificationHistoryTab(
    allVerifications: List<StockVerification>,
    onViewDetails: (StockVerification) -> Unit
) {
    var filterType by remember { mutableStateOf("ALL") } // ALL, MATCHED, MISMATCH, ADJUSTED, REJECTED

    val filteredList = remember(allVerifications, filterType) {
        when (filterType) {
            "MATCHED" -> allVerifications.filter { it.status == VerificationStatus.MATCHED }
            "MISMATCH" -> allVerifications.filter { it.difference != 0.0 }
            "ADJUSTED" -> allVerifications.filter { it.status == VerificationStatus.APPROVED_ADJUSTED }
            "REJECTED" -> allVerifications.filter { it.status == VerificationStatus.REJECTED }
            else -> allVerifications
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = filterType == "ALL",
                onClick = { filterType = "ALL" },
                label = { Text("সব (${allVerifications.size})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filterType == "MATCHED",
                onClick = { filterType = "MATCHED" },
                label = { Text("মিলেছে", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filterType == "MISMATCH",
                onClick = { filterType = "MISMATCH" },
                label = { Text("অমিল", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filterType == "ADJUSTED",
                onClick = { filterType = "ADJUSTED" },
                label = { Text("সমন্বিত", fontSize = 11.sp) }
            )
        }

        if (filteredList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো যাচাই রেকর্ড পাওয়া যায়নি", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredList, key = { it.id }) { verif ->
                    HistoryItemCard(verif = verif)
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(verif: StockVerification) {
    val dateStr = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(verif.verifiedAt))
    val statusLabel = when (verif.status) {
        VerificationStatus.MATCHED -> "মিলেছে"
        VerificationStatus.MISMATCH_PENDING -> "অমিল (অপেক্ষমাণ)"
        VerificationStatus.APPROVED_ADJUSTED -> "অনুমোদিত ও সমন্বিত"
        VerificationStatus.REJECTED -> "প্রত্যাখ্যাত"
        VerificationStatus.RECOUNT_REQUESTED -> "পুনরায় গণনা নির্দেশ"
    }

    val statusColor = when (verif.status) {
        VerificationStatus.MATCHED -> Color(0xFF2E7D32)
        VerificationStatus.APPROVED_ADJUSTED -> Color(0xFF1565C0)
        VerificationStatus.MISMATCH_PENDING -> Color(0xFFE65100)
        VerificationStatus.REJECTED -> MaterialTheme.colorScheme.error
        VerificationStatus.RECOUNT_REQUESTED -> Color(0xFF6A1B9A)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(verif.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("$dateStr | ${verif.verifierUserName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "অ্যাপ স্টক: ${verif.appStockSnapshot} | বাস্তব: ${verif.physicalStock} ${verif.productUnit}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "পার্থক্য: ${if (verif.difference > 0) "+${verif.difference}" else "${verif.difference}"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (verif.difference == 0.0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }

            if (verif.adminNote?.isNotBlank() == true) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "এডমিনের মন্তব্য: ${verif.adminNote} (${verif.reviewedByAdminName ?: "Admin"})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

// --- DIALOG IMPLEMENTATIONS ---

@Composable
private fun ProductHistoryDialog(
    product: Product,
    verifications: List<StockVerification>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("সম্পূর্ণ স্টক যাচাইয়ের ইতিহাস", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("বর্তমান রেকর্ডকৃত স্টক:", fontSize = 12.sp)
                    Text("${product.currentStock} ${product.unit}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()

                if (verifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("এই পণ্যের কোনো যাচাই রেকর্ড নেই", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(verifications, key = { it.id }) { verif ->
                            HistoryItemCard(verif = verif)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন")
            }
        }
    )
}

@Composable
private fun AdminMismatchReviewDialog(
    verification: StockVerification,
    onDismiss: () -> Unit,
    onSubmitReview: (AdminMismatchAction, String) -> Unit
) {
    var adminNote by remember { mutableStateOf("") }
    val isShortage = verification.difference < 0
    val diffText = if (isShortage) "ঘাটতি: ${verification.difference} ${verification.productUnit}" else "অতিরিক্ত: +${verification.difference} ${verification.productUnit}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("স্টক অমিল পর্যালোচনা", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(verification.productName, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("যাচাইয়ের সময় অ্যাপের স্টক:", fontSize = 12.sp)
                            Text("${verification.appStockSnapshot} ${verification.productUnit}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("বাস্তবে গুনে পাওয়া স্টক:", fontSize = 12.sp)
                            Text("${verification.physicalStock} ${verification.productUnit}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("অমিলের পরিমাণ:", fontSize = 12.sp)
                            Text(diffText, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("যাচাইকারী: ${verification.verifierUserName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (verification.notes.isNotBlank()) {
                            Text("সেলসম্যানের মন্তব্য: ${verification.notes}", fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = adminNote,
                    onValueChange = { adminNote = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("এডমিনের সিদ্ধান্ত বা কারণ লিখুন") },
                    placeholder = { Text("যেমন: কাউন্টিং সঠিক হয়েছে, স্টক সমন্বয় করা হলো") },
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "অনুমোদন দিলে মূল পণ্যের স্টক সমন্বয় হবে এবং অডিট ট্রেইল সংরক্ষিত হবে।",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { onSubmitReview(AdminMismatchAction.APPROVE_AND_ADJUST, adminNote) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("অনুমোদন ও স্টক সমন্বয় করুন")
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onSubmitReview(AdminMismatchAction.REQUEST_RECOUNT, adminNote) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("পুনরায় গণনা", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { onSubmitReview(AdminMismatchAction.REJECT, adminNote) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("প্রত্যাখ্যান", fontSize = 12.sp)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
private fun ConfigureScheduleDialog(
    item: ProductVerificationItem,
    salesmen: List<User>,
    onDismiss: () -> Unit,
    onSave: (intervalDays: Int, assignedSalesmanId: String?, assignedSalesmanName: String?, reminderEnabled: Boolean) -> Unit
) {
    val existingSchedule = item.schedule
    var selectedInterval by remember { mutableStateOf(existingSchedule?.intervalDays ?: 7) }
    var customDaysText by remember { mutableStateOf(if (selectedInterval !in listOf(1, 3, 7, 15, 30)) "$selectedInterval" else "") }
    var selectedSalesmanId by remember { mutableStateOf(existingSchedule?.assignedSalesmanId ?: "") }
    var isReminderEnabled by remember { mutableStateOf(existingSchedule?.isReminderEnabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("যাচাইয়ের সময়সীমা নির্ধারণ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(item.product.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("কত দিন অন্তর যাচাই হবে?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                listOf(
                    1 to "প্রতিদিন (১ দিন)",
                    3 to "প্রতি ৩ দিন",
                    7 to "প্রতি ৭ দিন (সাপ্তাহিক)",
                    15 to "প্রতি ১৫ দিন",
                    30 to "প্রতি ৩০ দিন (মাসিক)"
                ).forEach { (days, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedInterval = days
                                customDaysText = ""
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedInterval == days, onClick = {
                            selectedInterval = days
                            customDaysText = ""
                        })
                        Text(label, fontSize = 13.sp)
                    }
                }

                // Custom option
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedInterval !in listOf(1, 3, 7, 15, 30),
                        onClick = { selectedInterval = customDaysText.toIntOrNull() ?: 10 }
                    )
                    Text("কাস্টম দিনসংখ্যা", fontSize = 13.sp)
                }

                if (selectedInterval !in listOf(1, 3, 7, 15, 30)) {
                    OutlinedTextField(
                        value = customDaysText,
                        onValueChange = {
                            customDaysText = it
                            it.toIntOrNull()?.let { days -> selectedInterval = days }
                        },
                        label = { Text("দিন লিখুন") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(6.dp))
                HorizontalDivider()

                // Reminder toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("মেয়াদোত্তীর্ণ হলে স্বয়ংক্রিয় রিমাইন্ডার", fontSize = 13.sp)
                    Switch(checked = isReminderEnabled, onCheckedChange = { isReminderEnabled = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalDays = if (selectedInterval !in listOf(1, 3, 7, 15, 30)) {
                    customDaysText.toIntOrNull() ?: 7
                } else selectedInterval

                val assignedName = salesmen.find { it.id == selectedSalesmanId }?.name
                onSave(finalDays, selectedSalesmanId.ifBlank { null }, assignedName, isReminderEnabled)
            }) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
private fun SendVerificationAlertDialog(
    salesmen: List<User>,
    onDismiss: () -> Unit,
    onSend: (targetSalesmanId: String?, targetSalesmanName: String?, title: String, message: String, priority: AlertPriority, isVoiceEnabled: Boolean) -> Unit
) {
    var selectedSalesmanId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("দোকানের স্টক যাচাই করুন") }
    var message by remember { mutableStateOf("শার্ট ও মুদি বিভাগের সব পণ্য গুনে অ্যাপের স্টকের সঙ্গে মিলিয়ে আজকের মধ্যে যাচাই জমা দিন।") }
    var priority by remember { mutableStateOf(AlertPriority.GENERAL) }
    var isVoiceEnabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("স্টক যাচাই নির্দেশ পাঠান", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("প্রাপক নির্বাচন করুন:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedSalesmanId == null,
                        onClick = { selectedSalesmanId = null },
                        label = { Text("সকল সেলসম্যান") }
                    )
                }

                if (salesmen.isNotEmpty()) {
                    Column {
                        salesmen.forEach { s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSalesmanId = s.id },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedSalesmanId == s.id, onClick = { selectedSalesmanId = s.id })
                                Text(s.name, fontSize = 13.sp)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("শিরোনাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("বিস্তারিত নির্দেশ") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("বাংলা ভয়েস প্রম্পট (TTS)", fontSize = 13.sp)
                    Switch(checked = isVoiceEnabled, onCheckedChange = { isVoiceEnabled = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val sName = salesmen.find { it.id == selectedSalesmanId }?.name
                onSend(selectedSalesmanId, sName, title, message, priority, isVoiceEnabled)
            }) {
                Text("পাঠান")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}
