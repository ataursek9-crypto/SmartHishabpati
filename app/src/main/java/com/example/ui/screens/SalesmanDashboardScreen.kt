package com.example.ui.screens

import android.content.Context
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import com.example.util.GoogleSignInButton
import com.example.util.GoogleSignInHelper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Customer
import com.example.data.model.ExpenseStatus
import com.example.data.model.VerificationStatus
import com.example.data.sync.*
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.util.PdfReceiptGenerator
import com.example.viewmodel.BusinessViewModel

@Composable
fun SalesmanDashboardScreen(
    viewModel: BusinessViewModel,
    onNavigateToNewSale: () -> Unit,
    onNavigateToStock: () -> Unit,
    onNavigateToCollectPayment: (String?) -> Unit,
    onNavigateToMyCustomers: () -> Unit,
    onNavigateToSubmitProduct: () -> Unit,
    onNavigateToMySubmittedProducts: () -> Unit,
    onNavigateToMySales: () -> Unit,
    onNavigateToMyReceipts: () -> Unit,
    onNavigateToExpenseApply: () -> Unit,
    onNavigateToMyReport: () -> Unit,
    onNavigateToAiAssistant: () -> Unit,
    onNavigateToReminders: () -> Unit = {},
    onNavigateToAlerts: () -> Unit = {},
    onNavigateToStockVerification: () -> Unit = {}
) {
    val context = LocalContext.current
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val summary by viewModel.salesmanDashboardSummary.collectAsStateWithLifecycle()
    val myExpenses by viewModel.myExpenses.collectAsStateWithLifecycle()
    val settings by viewModel.companySettings.collectAsStateWithLifecycle()
    val pendingRemindersCount by viewModel.pendingRemindersCount.collectAsStateWithLifecycle()
    val pendingAlertsCount by viewModel.salesmanPendingAlertsCount.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val verificationItems by viewModel.productVerificationItems.collectAsStateWithLifecycle()
    val pendingVerificationCount = remember(verificationItems) {
        verificationItems.count { it.isOverdue || it.lastVerification == null || it.pendingMismatch?.status == VerificationStatus.RECOUNT_REQUESTED }
    }

    val pendingCount = remember(myExpenses) { myExpenses.count { it.status == ExpenseStatus.PENDING } }
    val approvedExpense = remember(myExpenses) {
        myExpenses.filter { it.status == ExpenseStatus.APPROVED }.sumOf { it.amount }
    }

    val activeBizId by viewModel.activeBusinessId.collectAsStateWithLifecycle()
    var showConnectDialog by remember { mutableStateOf(false) }
    var inputBizId by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshActiveBusinessId()
        if (viewModel.getActiveBusinessId().isNotBlank()) {
            viewModel.triggerSync()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("salesman_dashboard_screen"),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // Salesman Personal Header
        item {
            Surface(
                color = PrimaryNavy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "শুভদিন, ${user?.name ?: "সেলস প্রতিনিধি"}",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = settings.businessName,
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall
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
                                    .testTag("salesman_sync_status_badge")
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
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    .testTag("btn_salesman_reminder_bell")
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
                                        contentDescription = "আমার রিমাইন্ডার",
                                        tint = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = onNavigateToAlerts,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    .testTag("btn_salesman_alert_bell_top")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (pendingAlertsCount > 0) {
                                            Badge(
                                                containerColor = AccentRed,
                                                contentColor = Color.White
                                            ) {
                                                Text(
                                                    text = if (pendingAlertsCount > 9) "9+" else pendingAlertsCount.toString(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Campaign,
                                        contentDescription = "এডমিনের অ্যালার্ট",
                                        tint = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = onNavigateToAiAssistant,
                                modifier = Modifier.background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI সহকারী", tint = AccentAmber)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Cloud Business Connection Card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("salesman_business_connection_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (activeBizId.isNotBlank()) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (activeBizId.isNotBlank()) Color(0xFF81C784) else Color(0xFFFFB74D),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (activeBizId.isNotBlank()) "সংযুক্ত ক্লাউড ব্যবসা:" else "ক্লাউড ব্যবসার সাথে সংযোগ নেই",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = if (activeBizId.isNotBlank()) activeBizId else "অ্যাডমিনের বিজনেস কোড দিয়ে যুক্ত হন",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    inputBizId = activeBizId
                                    showConnectDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_salesman_connect_business")
                            ) {
                                Icon(
                                    imageVector = if (activeBizId.isNotBlank()) Icons.Default.Sync else Icons.Default.AddLink,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (activeBizId.isNotBlank()) "সিঙ্ক/কোড" else "কানেক্ট",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Personal Today's Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeroStatCard(
                            title = "আজকের নিজের বিক্রি",
                            value = BengaliFormatter.formatCurrency(summary.todaySales),
                            icon = Icons.Default.TrendingUp,
                            backgroundColor = Color(0xFF1565C0),
                            modifier = Modifier.weight(1f)
                        )
                        HeroStatCard(
                            title = "আজকের নিজের আদায়",
                            value = BengaliFormatter.formatCurrency(summary.todayCollected),
                            icon = Icons.Default.AccountBalanceWallet,
                            backgroundColor = Color(0xFF00796B),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Large Primary Operational Buttons (Designed for fast on-the-road field entry)
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "প্রধান ফিল্ড কার্যক্রম",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "দ্রুত ও সহজে সেলস বা পেমেন্ট এন্ট্রি করুন",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Big Primary Button: NEW SALE
                Button(
                    onClick = onNavigateToNewSale,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .testTag("btn_salesman_new_sale"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("নতুন বিক্রি এন্ট্রি", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("পণ্য নির্বাচন, ক্যাশ/বকেয়া ও ভাউচার", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Big Primary Button: VIEW STOCK & QUICK SALE
                Button(
                    onClick = onNavigateToStock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("btn_salesman_view_stock"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("স্টক দেখুন", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("মজুদ পণ্য তালিকা ও তাৎক্ষণিক বিক্রি", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Big Secondary Button: COLLECT DUE / MONEY RECEIPT
                Button(
                    onClick = { onNavigateToCollectPayment(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("btn_salesman_collect_due"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("টাকা জমা নিন ও মানি রিসিপ্ট", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("বকেয়া কালেকশন ও ইনস্ট্যান্ট মানি রসিদ", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Operational Button: MY CUSTOMERS & DUE
                Button(
                    onClick = onNavigateToMyCustomers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .testTag("btn_salesman_customers_due"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E35B1))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("আমার কাস্টমার ও বকেয়া", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("অনুমোদিত কাস্টমারদের তালিকা ও বকেয়া হিসাব", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Operational Button: MY REMINDERS & TASKS
                Button(
                    onClick = onNavigateToReminders,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("btn_salesman_my_reminders"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
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
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("আমার রিমাইন্ডার ও কাজ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("কাস্টমার তাগাদা, খরচের স্থিতি ও নির্ধারিত কার্যক্রম", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Operational Button: ADMIN ALERTS & INSTRUCTIONS
                Button(
                    onClick = onNavigateToAlerts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("btn_salesman_my_alerts"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingAlertsCount > 0) Color(0xFFC2185B) else Color(0xFF00796B)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (pendingAlertsCount > 0) {
                                    Badge(
                                        containerColor = AccentRed,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = if (pendingAlertsCount > 9) "9+" else pendingAlertsCount.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("এডমিনের অ্যালার্ট ও নির্দেশ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (pendingAlertsCount > 0) "$pendingAlertsCount টি নতুন/জরুরি নির্দেশ রয়েছে" else "জরুরি নোটিশ ও দিকনির্দেশনা",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Operational Button: SHOP STOCK VERIFICATION (আমার স্টক যাচাই)
                Button(
                    onClick = onNavigateToStockVerification,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .testTag("btn_salesman_stock_verification"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingVerificationCount > 0) Color(0xFF00695C) else Color(0xFF0277BD)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (pendingVerificationCount > 0) {
                                    Badge(
                                        containerColor = AccentRed,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = if (pendingVerificationCount > 9) "9+" else pendingVerificationCount.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("আমার স্টক যাচাই", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (pendingVerificationCount > 0) "$pendingVerificationCount টি পণ্যের স্টক যাচাই বাকি/দেরি" else "দোকানের বাস্তব পণ্য গুনে মিলিয়ে নিন",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Product Submission Buttons for Salesman (Rule A.1, A.2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onNavigateToSubmitProduct,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("btn_salesman_submit_product"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AddBox, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("প্রোডাক্ট যোগ করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToMySubmittedProducts,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("btn_salesman_my_submitted_products"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory, contentDescription = null, tint = PrimaryNavy)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("আমার জমা দেওয়া প্রোডাক্ট", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Two side by side buttons: Expense & My Report
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToExpenseApply,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("btn_salesman_expense"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = AccentAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("খরচের আবেদন", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToMyReport,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("btn_salesman_report"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Assessment, contentDescription = null, tint = PrimaryNavy)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("আমার রিপোর্ট", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }


        // Salesman Overview Status
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "ব্যক্তিগত চলতি হিসাব",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("অনুমোদিত নিজের খরচ:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(BengaliFormatter.formatCurrency(approvedExpense), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("অপেক্ষমাণ খরচের আবেদন:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$pendingCount টি আবেদন", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                        }
                    }
                }
            }
        }

        // Quick Navigation to Sales & Receipts lists
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ইতিহাস ও তালিকা",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToMySales() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = PrimaryNavy)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("আমার বিক্রির তালিকা", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToMyReceipts() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Paid, contentDescription = null, tint = SecondaryTeal)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("মানি রিসিপ্টসমূহ", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    if (showConnectDialog) {
        var inputPhone by remember { mutableStateOf(user?.phone ?: "") }
        var inputPin by remember { mutableStateOf("") }
        var connectError by remember { mutableStateOf("") }
        var isGoogleSigningIn by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        var authUser by remember { mutableStateOf(try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }) }

        AlertDialog(
            onDismissRequest = { if (!isConnecting) showConnectDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "ক্লাউড বিজনেসে সংযুক্তকরণ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "অ্যাডমিন ফোনের ড্যাশবোর্ডে প্রদর্শিত 'ক্লাউড বিজনেস কোড', আপনার নিবন্ধিত মোবাইল নম্বর ও পিন কোড দিন। এর ফলে অ্যাডমিনের পণ্য ও স্টক নিরাপদভাবে আপনার ফোনে সিঙ্ক হবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (authUser == null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ক্লাউড ডাটা সিঙ্ক করার জন্য প্রথমে গুগল সাইন-ইন আবশ্যক।",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        GoogleSignInButton(
                            isLoading = isGoogleSigningIn,
                            onClick = {
                                isGoogleSigningIn = true
                                connectError = ""
                                GoogleSignInHelper.launchGoogleSignIn(
                                    context = context,
                                    scope = coroutineScope,
                                    onAuthSuccess = { fUser ->
                                        isGoogleSigningIn = false
                                        authUser = fUser
                                        Toast.makeText(context, "গুগল সাইন-ইন সফল: ${fUser.displayName ?: fUser.email}", Toast.LENGTH_SHORT).show()
                                    },
                                    onCancelled = {
                                        isGoogleSigningIn = false
                                    },
                                    onError = { err ->
                                        isGoogleSigningIn = false
                                        connectError = err
                                    }
                                )
                            }
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "গুগল আইডি: ${authUser?.email ?: authUser?.displayName ?: "প্রমাণিত"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputBizId,
                        onValueChange = { inputBizId = it; connectError = "" },
                        label = { Text("ক্লাউড বিজনেস কোড *") },
                        placeholder = { Text("অ্যাডমিনের বিজনেস কোড পেস্ট করুন") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    inputBizId = clip.trim()
                                    connectError = ""
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "পেস্ট করুন")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("input_salesman_biz_id")
                    )

                    OutlinedTextField(
                        value = inputPhone,
                        onValueChange = { inputPhone = it; connectError = "" },
                        label = { Text("নিবন্ধিত মোবাইল নম্বর *") },
                        placeholder = { Text("018XXXXXXXX") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("input_salesman_phone")
                    )

                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = { inputPin = it; connectError = "" },
                        label = { Text("গোপন পিন কোড *") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_salesman_pin")
                    )

                    if (connectError.isNotBlank()) {
                        Text(
                            text = connectError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = inputBizId.trim()
                        if (clean.isBlank()) {
                            connectError = "বিজনেস কোড খালি রাখা যাবে না!"
                            return@Button
                        }
                        if (inputPhone.isBlank()) {
                            connectError = "নিবন্ধিত মোবাইল নম্বর দিন!"
                            return@Button
                        }
                        if (inputPin.isBlank()) {
                            connectError = "গোপন পিন কোড দিন!"
                            return@Button
                        }
                        isConnecting = true
                        connectError = ""
                        viewModel.connectSalesmanToBusiness(
                            businessId = clean,
                            phone = inputPhone.trim(),
                            pin = inputPin.trim()
                        ) { success, msg ->
                            isConnecting = false
                            if (success) {
                                showConnectDialog = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            } else {
                                connectError = msg
                            }
                        }
                    },
                    enabled = !isConnecting,
                    modifier = Modifier.testTag("btn_confirm_salesman_connect")
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("কানেক্ট ও সিঙ্ক করুন")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConnectDialog = false },
                    enabled = !isConnecting
                ) {
                    Text("বাতিল")
                }
            }
        )
    }
}
