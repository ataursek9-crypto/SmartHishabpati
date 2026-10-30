package com.example.ui.screens

import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Customer
import com.example.data.model.ExpenseStatus
import com.example.data.sync.*
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.util.PdfReceiptGenerator
import com.example.viewmodel.BusinessViewModel
import com.example.viewmodel.CustomerDueItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: BusinessViewModel,
    onNavigateToSales: () -> Unit,
    onNavigateToNewSale: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onNavigateToCollectPayment: (String?) -> Unit,
    onNavigateToReceipts: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToProductApprovals: () -> Unit,
    onNavigateToSalesmen: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAuditLogs: () -> Unit,
    onNavigateToAiAssistant: () -> Unit,
    onNavigateToCompanyDue: () -> Unit = {},
    onNavigateToDataProtectionCenter: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {},
    onNavigateToSalesmanAlerts: () -> Unit = {},
    onNavigateToStockControl: () -> Unit = {},
    onNavigateToSuperAdmin: () -> Unit = {}
) {
    val context = LocalContext.current
    val summary by viewModel.adminDashboardSummary.collectAsStateWithLifecycle()
    val lowStockList by viewModel.lowStockProducts.collectAsStateWithLifecycle()
    val allExpenses by viewModel.allExpenses.collectAsStateWithLifecycle()
    val pendingExpenses = remember(allExpenses) { allExpenses.filter { it.status == ExpenseStatus.PENDING } }
    val allSubmissions by viewModel.allSubmissions.collectAsStateWithLifecycle()
    val pendingSubmissions = remember(allSubmissions) { allSubmissions.filter { it.status == com.example.data.model.ProductSubmissionStatus.PENDING } }
    val settings by viewModel.companySettings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val pendingRemindersCount by viewModel.pendingRemindersCount.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val isDefaultAdminPinActive by viewModel.isDefaultAdminPinActive.collectAsStateWithLifecycle()
    val adminName = BengaliFormatter.formatAdminDisplayName(currentUser?.name)
    val activeBizId by viewModel.activeBusinessId.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshActiveBusinessId()
        viewModel.syncManager.startProductsRealtimeListener()
        viewModel.fetchDirectProductSubmissions()
        viewModel.fetchDirectExpenses()
        viewModel.triggerSync()
    }

    var showChangeCredentialsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("admin_dashboard_screen"),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // Business Header & Greeting Banner
        item {
            Surface(
                color = PrimaryNavy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = settings.businessName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = adminName,
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .clickable {
                                        viewModel.triggerSync { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .testTag("admin_sync_status_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val (icon, tint) = when (syncState.status) {
                                        SyncStatus.SYNCED -> Icons.Default.CloudDone to Color(0xFF81C784)
                                        SyncStatus.SYNCING -> Icons.Default.Sync to Color(0xFF64B5F6)
                                        SyncStatus.PENDING -> Icons.Default.CloudQueue to Color(0xFFFFB74D)
                                        SyncStatus.LOCAL_SAVED, SyncStatus.OFFLINE -> Icons.Default.CloudOff to Color.White.copy(alpha = 0.8f)
                                        SyncStatus.ERROR -> Icons.Default.SyncProblem to Color(0xFFE57373)
                                    }
                                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = syncState.status.labelBn,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onNavigateToReminders,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    .testTag("btn_admin_reminder_bell")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (pendingRemindersCount > 0) {
                                            Badge(
                                                containerColor = AccentRed,
                                                contentColor = Color.White
                                            ) {
                                                Text(
                                                    text = if (pendingRemindersCount > 9) "9+" else pendingRemindersCount.toString(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = "স্মার্ট রিমাইন্ডার",
                                        tint = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = onNavigateToSalesmanAlerts,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    .testTag("btn_admin_salesman_alert_top")
                            ) {
                                Icon(
                                    Icons.Default.Campaign,
                                    contentDescription = "সেলসম্যান অ্যালার্ট",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = onNavigateToAiAssistant,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                    .testTag("btn_ai_assistant")
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "AI সহকারী",
                                    tint = AccentAmber
                                )
                            }
                        }
                    }

                    if (activeBizId.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_business_id_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "ক্লাউড বিজনেস কোড (সেলসম্যানের সাথে শেয়ার করুন):",
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = activeBizId,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Business ID", activeBizId))
                                        Toast.makeText(context, "বিজনেস আইডি কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("কপি", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hero Metric Cards (Today's Sales & Cash Collected)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeroStatCard(
                            title = "আজকের মোট বিক্রি",
                            value = BengaliFormatter.formatCurrency(summary.todaySales),
                            icon = Icons.Default.PointOfSale,
                            backgroundColor = Color(0xFF1E4E8C),
                            modifier = Modifier.weight(1f)
                        )
                        HeroStatCard(
                            title = "আজকের মোট আদায়",
                            value = BengaliFormatter.formatCurrency(summary.todayCollected),
                            icon = Icons.Default.Paid,
                            backgroundColor = Color(0xFF0E6B5E),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Secondary Metrics Grid (Due, Expense, Profit, Stock Valuation)
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ব্যবসায়িক সার্বিক অবস্থা",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricMiniCard(
                        title = "মোট কাস্টমার বকেয়া",
                        value = BengaliFormatter.formatCurrency(summary.totalDue),
                        color = AccentRed,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "অনুমোদিত মোট খরচ",
                        value = BengaliFormatter.formatCurrency(summary.totalApprovedExpense),
                        color = AccentAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricMiniCard(
                        title = "আনুমানিক নিট লাভ",
                        value = BengaliFormatter.formatCurrency(summary.netProfit),
                        color = AccentGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniCard(
                        title = "বর্তমান মোট স্টক মূল্য",
                        value = BengaliFormatter.formatCurrency(summary.totalStockValuation),
                        color = PrimaryNavy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (isDefaultAdminPinActive) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("admin_default_pin_security_warning")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "নিরাপত্তা সতর্কতা: ডিফল্ট পিন সক্রিয়",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "ব্যবসায়িক তথ্যের সুরক্ষার জন্য অবিলম্বে প্রাথমিক পিন (১২৩৪) পরিবর্তন করে আপনার নিজস্ব মোবাইল নম্বর ও নতুন পিন কোড সেট করুন।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showChangeCredentialsDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("btn_change_default_admin_pin")
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("নিজস্ব পিন ও নম্বর সেট করুন")
                        }
                    }
                }
            }
        }

        // Quick Action Buttons
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "দ্রুত কার্যক্রম (Quick Actions)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = "নতুন বিক্রি",
                        icon = Icons.Default.AddShoppingCart,
                        containerColor = PrimaryBlue,
                        onClick = onNavigateToNewSale,
                        modifier = Modifier.weight(1f).testTag("btn_admin_new_sale")
                    )
                    QuickActionTile(
                        title = "স্টক দেখুন",
                        icon = Icons.Default.Inventory2,
                        containerColor = SecondaryTeal,
                        onClick = onNavigateToProducts,
                        modifier = Modifier.weight(1f).testTag("btn_admin_view_stock")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionTile(
                        title = "টাকা আদায়",
                        icon = Icons.Default.ReceiptLong,
                        containerColor = Color(0xFF00897B),
                        onClick = { onNavigateToCollectPayment(null) },
                        modifier = Modifier.weight(1f).testTag("btn_admin_collect_due")
                    )
                    QuickActionTile(
                        title = "খরচ অনুমোদন",
                        icon = Icons.Default.PendingActions,
                        containerColor = AccentAmber,
                        badgeCount = summary.pendingExpenseCount,
                        onClick = onNavigateToExpenses,
                        modifier = Modifier.weight(1f).testTag("btn_admin_pending_expenses")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Prominent Button: Company & Supplier Due Management (কোম্পানির লেনদেন)
                Button(
                    onClick = onNavigateToCompanyDue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_admin_company_due"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("কোম্পানির লেনদেন", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("সাপ্লায়ার হিসাব, পণ্য ক্রয় ও বকেয়া খতিয়ান", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
            }
        }


        // Alerts Section: Low Stock, Pending Product Approvals and Expenses
        if (lowStockList.isNotEmpty() || pendingExpenses.isNotEmpty() || pendingSubmissions.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "গুরুত্বপূর্ণ সতর্কতা ও নোটিফিকেশন",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AccentRed
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (pendingSubmissions.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToProductApprovals() }
                                .testTag("card_alert_pending_submissions")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color(0xFFE65100))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${pendingSubmissions.size}টি নতুন প্রোডাক্ট অনুমোদনের অপেক্ষায়",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "যেমন: '${pendingSubmissions.first().name}' (জমা দিয়েছেন: ${pendingSubmissions.first().salesmanName})",
                                        fontSize = 12.sp,
                                        color = TextSecondaryDark
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFE65100))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (lowStockList.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF9A9A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToProducts() }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${lowStockList.size}টি পণ্যের স্টক শেষ হয়ে আসছে!",
                                        fontWeight = FontWeight.Bold,
                                        color = AccentRed,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "যেমন: ${lowStockList.first().name} (স্টক মাত্র ${lowStockList.first().currentStock})",
                                        fontSize = 12.sp,
                                        color = TextSecondaryDark
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentRed)
                            }
                        }
                    }

                    if (pendingExpenses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD54F)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToExpenses() }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.HourglassTop, contentDescription = null, tint = AccentAmber)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${pendingExpenses.size}টি খরচের আবেদন অনুমোদনের অপেক্ষায়",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8D6E00),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "মোট অপেক্ষমাণ দাবি: ${BengaliFormatter.formatCurrency(pendingExpenses.sumOf { it.amount })}",
                                        fontSize = 12.sp,
                                        color = TextSecondaryDark
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentAmber)
                            }
                        }
                    }
                }
            }
        }

        // Super Admin Entry Banner (Only shown if current authenticated user has platform super admin privileges)
        if (viewModel.isSuperAdminUser()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onNavigateToSuperAdmin() }
                        .testTag("btn_super_admin_portal"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A237E)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD54F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF1A237E), modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "প্ল্যাটফর্ম সুপার এডমিন প্যানেল",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(color = Color(0xFFFFD54F), shape = RoundedCornerShape(4.dp)) {
                                    Text("OWNER", color = Color(0xFF1A237E), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "সকল দোকানের সাবস্ক্রিপশন, পেমেন্ট ও প্ল্যাটফর্ম নিয়ন্ত্রণ",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFFFFD54F))
                    }
                }
            }
        }

        // Full Admin Modules Grid
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "সকল ব্যবসায়িক মডিউল",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                val modules = listOf(
                    ModuleItem("বিক্রি ও অর্ডার", Icons.Default.ShoppingCart, PrimaryBlue, onNavigateToSales),
                    ModuleItem("পণ্য ও স্টক", Icons.Default.Inventory2, SecondaryTeal, onNavigateToProducts),
                    ModuleItem("স্টক কন্ট্রোল সেন্টার", Icons.Default.Inventory, Color(0xFF00695C), onNavigateToStockControl),
                    ModuleItem("প্রোডাক্ট অনুমোদন ও যাচাই", Icons.Default.FactCheck, Color(0xFFE65100), onNavigateToProductApprovals),
                    ModuleItem("কাস্টমার ও বকেয়া", Icons.Default.People, Color(0xFF5E35B1), onNavigateToCustomers),
                    ModuleItem("কোম্পানির লেনদেন", Icons.Default.Business, Color(0xFF00796B), onNavigateToCompanyDue),
                    ModuleItem("টাকা আদায় ও রিসিপ্ট", Icons.Default.Receipt, Color(0xFF00897B), onNavigateToReceipts),
                    ModuleItem("খরচ ব্যবস্থাপনা", Icons.Default.AccountBalanceWallet, AccentAmber, onNavigateToExpenses),
                    ModuleItem("সেলসম্যান ও স্টাফ", Icons.Default.Badge, Color(0xFF3949AB), onNavigateToSalesmen),
                    ModuleItem("হিসাব ও রিপোর্ট", Icons.Default.Assessment, Color(0xFFD81B60), onNavigateToReports),
                    ModuleItem("অডিট ও নিরাপত্তা লগ", Icons.Default.Security, Color(0xFF546E7A), onNavigateToAuditLogs),
                    ModuleItem("স্মার্ট রিমাইন্ডার", Icons.Default.NotificationsActive, Color(0xFF6A1B9A), onNavigateToReminders),
                    ModuleItem("সেলসম্যান অ্যালার্ট", Icons.Default.Campaign, Color(0xFFC2185B), onNavigateToSalesmanAlerts),
                    ModuleItem("ব্যবসার সেটিংস", Icons.Default.Settings, Color(0xFF455A64), onNavigateToSettings),
                    ModuleItem("ডেটা সুরক্ষা কেন্দ্র", Icons.Default.AdminPanelSettings, Color(0xFF2E7D32), onNavigateToDataProtectionCenter)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in modules.indices step 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (j in 0 until 3) {
                                if (i + j < modules.size) {
                                    val mod = modules[i + j]
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(96.dp)
                                            .clickable { mod.onClick() },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Surface(
                                                color = mod.color.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(mod.icon, contentDescription = mod.title, tint = mod.color, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = mod.title,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextPrimaryDark,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChangeCredentialsDialog) {
        var currentPinInput by remember { mutableStateOf("") }
        var newNameInput by remember { mutableStateOf(currentUser?.name ?: "") }
        var newPhoneInput by remember { mutableStateOf(currentUser?.phone ?: "") }
        var newPinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        var dialogError by remember { mutableStateOf("") }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showChangeCredentialsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryNavy)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("অ্যাডমিন নিরাপত্তা পিন পরিবর্তন", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        "আপনার ব্যবসার পূর্ণ নিরাপত্তা নিশ্চিত করতে নতুন পিন ও সঠিক মোবাইল নম্বর দিন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = currentPinInput,
                        onValueChange = { currentPinInput = it; dialogError = "" },
                        label = { Text("বর্তমান পিন কোড") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_current_admin_pin")
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it; dialogError = "" },
                        label = { Text("অ্যাডমিন নাম") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_new_admin_name")
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newPhoneInput,
                        onValueChange = { newPhoneInput = it; dialogError = "" },
                        label = { Text("নতুন মোবাইল নম্বর") },
                        placeholder = { Text("01XXXXXXXXX") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_new_admin_phone")
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { newPinInput = it; dialogError = "" },
                        label = { Text("নতুন পিন কোড (কমপক্ষে ৪ সংখ্যা)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_new_admin_pin")
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { confirmPinInput = it; dialogError = "" },
                        label = { Text("নতুন পিন নিশ্চিত করুন") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_confirm_admin_pin")
                    )

                    if (dialogError.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = dialogError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentPinInput.isBlank()) {
                            dialogError = "বর্তমান পিন কোড লিখুন"
                            return@Button
                        }
                        if (newPhoneInput.trim().length < 11) {
                            dialogError = "সঠিক ১১ ডিজিটের মোবাইল নম্বর দিন"
                            return@Button
                        }
                        if (newPinInput.trim().length < 4) {
                            dialogError = "নতুন পিন কোড কমপক্ষে ৪ ডিজিট হতে হবে"
                            return@Button
                        }
                        if (newPinInput.trim() != confirmPinInput.trim()) {
                            dialogError = "নতুন পিন কোড দুটি মিলছে না!"
                            return@Button
                        }

                        isSaving = true
                        viewModel.updateAdminSecurityCredentials(
                            currentPin = currentPinInput.trim(),
                            newName = newNameInput.trim(),
                            newPhone = newPhoneInput.trim(),
                            newPin = newPinInput.trim()
                        ) { success, msg ->
                            isSaving = false
                            if (success) {
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                showChangeCredentialsDialog = false
                            } else {
                                dialogError = msg
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                    modifier = Modifier.testTag("btn_confirm_change_admin_credentials")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("সংরক্ষণ করুন")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showChangeCredentialsDialog = false },
                    enabled = !isSaving
                ) {
                    Text("বাতিল")
                }
            }
        )
    }
}

data class ModuleItem(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
fun HeroStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MetricMiniCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = TextSecondaryDark,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun QuickActionTile(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
    badgeCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (badgeCount > 0) {
                Surface(
                    color = AccentRed,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$badgeCount", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
