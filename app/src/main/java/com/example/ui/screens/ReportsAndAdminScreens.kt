package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CompanySettings
import com.example.data.model.ExpenseStatus
import com.example.data.model.User
import com.example.data.model.UserAccountStatus
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.viewmodel.BusinessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isAdmin = currentUser?.role == UserRole.ADMIN

    // Role-filtered sales, receipts and expenses
    val sales = if (isAdmin) {
        viewModel.allSales.collectAsStateWithLifecycle().value
    } else {
        viewModel.mySales.collectAsStateWithLifecycle().value
    }

    val receipts = if (isAdmin) {
        viewModel.allReceipts.collectAsStateWithLifecycle().value
    } else {
        viewModel.myReceipts.collectAsStateWithLifecycle().value
    }

    val expenses = if (isAdmin) {
        viewModel.allExpenses.collectAsStateWithLifecycle().value
    } else {
        viewModel.myExpenses.collectAsStateWithLifecycle().value
    }

    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()

    val totalSalesAmt = remember(sales) { sales.sumOf { it.totalAmount } }
    val totalCollectedAmt = remember(receipts) { receipts.sumOf { it.amount } }
    val totalDueAmt = remember(customers) { customers.sumOf { it.currentDue } }
    val totalApprovedExpenses = remember(expenses) {
        expenses.filter { it.status == ExpenseStatus.APPROVED }.sumOf { it.amount }
    }

    // Profit margin calculation - ONLY for Admin!
    val grossProfit = remember(totalSalesAmt) { totalSalesAmt * 0.18 }
    val netProfit = remember(grossProfit, totalApprovedExpenses) { grossProfit - totalApprovedExpenses }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isAdmin) "ব্যবসায়িক হিসাব ও লাভ-ক্ষতি রিপোর্ট" else "আমার সেলস ও কালেকশন রিপোর্ট",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isAdmin) Color(0xFFD81B60) else PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Main Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isAdmin) "সার্বিক লাভ-ক্ষতি ও আর্থিক বিবরণী" else "ব্যক্তিগত বিক্রয় ও কালেকশন সারাংশ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryNavy
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (isAdmin) {
                        // Admin sees company-wide Financials and Profit/Loss
                        ReportRow("মোট বিক্রয় আয় (Gross Sales):", BengaliFormatter.formatCurrency(totalSalesAmt), false)
                        ReportRow("মোট নগদ ও ডিজিটাল আদায়:", BengaliFormatter.formatCurrency(totalCollectedAmt), false, AccentGreen)
                        ReportRow("কাস্টমারদের কাছে মোট বকেয়া:", BengaliFormatter.formatCurrency(totalDueAmt), false, AccentRed)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        ReportRow("অনুমোদিত ব্যবসায়িক মোট খরচ:", BengaliFormatter.formatCurrency(totalApprovedExpenses), false, AccentAmber)
                        ReportRow("আনুমানিক মোট গ্রস মার্জিন (~১৮%):", BengaliFormatter.formatCurrency(grossProfit), false)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        ReportRow("আনুমানিক নিট লাভ / ক্ষতি:", BengaliFormatter.formatCurrency(netProfit), true, if (netProfit >= 0) AccentGreen else AccentRed)
                    } else {
                        // Salesman sees ONLY their allowed sales, collections and authorized field expenses
                        // NO Purchase price, NO Gross margin, NO Net profit, NO Company financial secrets
                        ReportRow("আমার মোট বিক্রি (Total Sales):", BengaliFormatter.formatCurrency(totalSalesAmt), true, PrimaryNavy)
                        ReportRow("আমার মোট টাকা আদায় (Total Collected):", BengaliFormatter.formatCurrency(totalCollectedAmt), true, AccentGreen)
                        ReportRow("আমার মোট ইনভয়েস সংখ্যা:", "${sales.size} টি", false)
                        ReportRow("আমার মোট মানি রিসিপ্ট সংখ্যা:", "${receipts.size} টি", false)
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        ReportRow("অনুমোদিত ব্যক্তিগত খরচ:", BengaliFormatter.formatCurrency(totalApprovedExpenses), false, AccentAmber)
                        ReportRow("অপেক্ষমাণ খরচের আবেদন:", "${expenses.count { it.status == ExpenseStatus.PENDING }} টি", false)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stock Summary Card (Strictly Role-Guarded)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isAdmin) "মজুদ পণ্য ও সম্পদ মূল্যায়ন (Admin)" else "পণ্যের বর্তমান মজুদ স্টক (Stock Status)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = SecondaryTeal
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (isAdmin) {
                        var totalBuyVal = 0.0
                        var totalSellVal = 0.0
                        products.forEach {
                            totalBuyVal += (it.currentStock * it.purchasePrice)
                            totalSellVal += (it.currentStock * it.sellingPrice)
                        }

                        ReportRow("মোট পণ্যের আইটেম সংখ্যা:", "${products.size} টি", false)
                        ReportRow("ক্রয়মূল্য অনুযায়ী স্টক মূল্যায়ন:", BengaliFormatter.formatCurrency(totalBuyVal), false)
                        ReportRow("বিক্রয়মূল্য অনুযায়ী সম্ভাব্য মোট মূল্য:", BengaliFormatter.formatCurrency(totalSellVal), false)
                    } else {
                        // Salesman view: Item count, total units, low stock alerts - strictly NO valuation numbers
                        val totalUnits = products.sumOf { it.currentStock }
                        val lowCount = products.count { it.currentStock <= it.alertStockLevel }

                        ReportRow("মোট সক্রিয় পণ্যের ধরন:", "${products.size} টি পণ্য", false)
                        ReportRow("গুদামে মোট মজুদ সংখ্যা:", String.format("%.0f একক/পিস", totalUnits), false)
                        ReportRow("কম থাকা পণ্যের সংখ্যা:", "$lowCount টি পণ্যে সতর্কতা", false, if (lowCount > 0) AccentRed else AccentGreen)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "দ্রষ্টব্য: পণ্যের ক্রয়মূল্য ও কোম্পানির অভ্যন্তরীণ আর্থিক পরিসংখ্যান শুধুমাত্র এডমিন নিয়ন্ত্রণাধীনে সংরক্ষিত।",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    Toast.makeText(context, "রিপোর্ট তথ্য সফলভাবে প্রস্তুত রয়েছে।", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isAdmin) Color(0xFFD81B60) else PrimaryBlue)
            ) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAdmin) "এডমিন রিপোর্ট এক্সপোর্ট / প্রিন্ট" else "আমার রিপোর্ট এক্সপোর্ট করুন",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun ReportRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    color: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontSize = if (isBold) 15.sp else 13.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit,
    onNavigateToDataProtectionCenter: () -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentSettings by viewModel.companySettings.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var name by remember(currentSettings) { mutableStateOf(currentSettings.businessName) }
    var phone by remember(currentSettings) { mutableStateOf(currentSettings.phone) }
    var address by remember(currentSettings) { mutableStateOf(currentSettings.address) }
    var footer by remember(currentSettings) { mutableStateOf(currentSettings.receiptFooter) }
    var imageMode by remember(currentSettings) { mutableStateOf(currentSettings.productImageMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ব্যবসায়িক সেটিংস", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF455A64),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Admin Profile Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_settings_admin_profile"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryNavy.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = PrimaryNavy.copy(alpha = 0.1f),
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "এডমিন প্রোফাইল", tint = PrimaryNavy, modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(BengaliFormatter.formatAdminDisplayName(currentUser?.name), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryNavy)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(4.dp)) {
                                Text("প্রধান এডমিন", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Text("${BengaliFormatter.formatAdminEnglishName()} • সিস্টেম অ্যাডমিনিস্ট্রেটর", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.outline)
                        Text("মোবাইল: ${currentUser?.phone ?: "01700000000"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("কোম্পানি ও রসিদ পরিচিতি", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("প্রতিষ্ঠানের নাম") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("যোগাযোগের ফোন নম্বর") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("প্রতিষ্ঠানের ঠিকানা") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = footer,
                        onValueChange = { footer = it },
                        label = { Text("রসিদের নিচের বার্তা (Footer)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Device & Login Security Settings
            val keepOnLogout by viewModel.keepAccountsOnLogout.collectAsStateWithLifecycle()
            val savedAccountsList by viewModel.savedAccounts.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "লগইন ও ডিভাইস নিরাপত্তা (Saved Accounts)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryNavy
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("লগআউটের পরও সংরক্ষিত নম্বর রাখুন", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "বন্ধ করলে লগআউট করার সাথে সাথে এই ডিভাইসে সংরক্ষিত সব নম্বর মুছে যাবে।",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = keepOnLogout,
                            onCheckedChange = { viewModel.setKeepAccountsOnLogout(it) }
                        )
                    }

                    if (savedAccountsList.isNotEmpty()) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "বর্তমানে সংরক্ষিত নম্বর: ${savedAccountsList.size} টি",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            TextButton(
                                onClick = {
                                    viewModel.clearAllRememberedPhones()
                                    Toast.makeText(context, "সংরক্ষিত সব নম্বর মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("সব মুছুন", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Product Image Management Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "প্রোডাক্ট ইমেজ সেটিংস (AI ও অনলাইন সার্চ)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryNavy
                    )
                    Text(
                        text = "প্রোডাক্ট যোগ করার সময় কোনো ছবি না থাকলে অ্যাপ কীভাবে আচরণ করবে তা নির্ধারণ করুন:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { imageMode = "AUTO" },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                if (imageMode == "AUTO") 2.dp else 1.dp,
                                if (imageMode == "AUTO") PrimaryBlue else MaterialTheme.colorScheme.outlineVariant
                            ),
                            color = if (imageMode == "AUTO") PrimaryBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = imageMode == "AUTO",
                                        onClick = { imageMode = "AUTO" }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("⚡ অটো মোড", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text(
                                    "ছবি না থাকলে স্বয়ংক্রিয়ভাবে AI বা অনলাইন থেকে ছবি সংগ্রহের চেষ্টা করবে।",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { imageMode = "MANUAL" },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                if (imageMode == "MANUAL") 2.dp else 1.dp,
                                if (imageMode == "MANUAL") SecondaryTeal else MaterialTheme.colorScheme.outlineVariant
                            ),
                            color = if (imageMode == "MANUAL") SecondaryTeal.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = imageMode == "MANUAL",
                                        onClick = { imageMode = "MANUAL" }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🎯 ম্যানুয়াল মোড", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text(
                                    "ব্যবহারকারী বাটন চেপে AI তৈরি বা অনলাইনে খুঁজে প্রিভিউ করে গ্রহণ করবেন।",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    val imgService = remember { com.example.data.service.ProductImageService(context) }
                    var apiKeyInput by remember { mutableStateOf("") }
                    var isEditingKey by remember { mutableStateOf(false) }
                    var keyUpdatedCounter by remember { mutableIntStateOf(0) }
                    val hasKey = remember(keyUpdatedCounter) { imgService.isApiKeyConfigured() }
                    val maskedKey = remember(keyUpdatedCounter) { imgService.getMaskedApiKey() }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Gemini AI API কী স্ট্যাটাস", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = if (hasKey) "সংযুক্ত ✅ ($maskedKey)" else "কনফিগার করা নেই ⚠️",
                                    fontSize = 12.sp,
                                    color = if (hasKey) AccentGreen else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            TextButton(onClick = { isEditingKey = !isEditingKey }) {
                                Text(if (isEditingKey) "বন্ধ" else "কী সেট করুন", fontSize = 12.sp)
                            }
                        }

                        if (isEditingKey) {
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text("নতুন Gemini API Key পেস্ট করুন") },
                                placeholder = { Text("AIzaSy...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (apiKeyInput.isNotBlank()) {
                                            imgService.saveCustomApiKey(apiKeyInput.trim())
                                            apiKeyInput = ""
                                            isEditingKey = false
                                            keyUpdatedCounter++
                                            Toast.makeText(context, "API Key সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = apiKeyInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                                ) {
                                    Text("সংরক্ষণ করুন")
                                }
                                if (hasKey) {
                                    OutlinedButton(
                                        onClick = {
                                            imgService.clearCustomApiKey()
                                            isEditingKey = false
                                            keyUpdatedCounter++
                                            Toast.makeText(context, "কাস্টম কী মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("কী মুছুন")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Data Protection Center Card
            val recycleCount by viewModel.recycleBinCount.collectAsStateWithLifecycle()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDataProtectionCenter() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryNavy.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = PrimaryNavy.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column {
                                Text(
                                    text = "ডেটা সুরক্ষা কেন্দ্র (Data Protection)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = PrimaryNavy
                                )
                                Text(
                                    text = "রিসাইকেল বিন • ৩০ দিনের রিকভারি • টেস্ট ডেটা",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        if (recycleCount > 0) {
                            Badge(containerColor = AccentRed) {
                                Text("$recycleCount টি আইটেম", fontSize = 11.sp)
                            }
                        }
                    }

                    Text(
                        text = "১. রিসাইকেল বিন ও মুছে ফেলা রেকর্ড ৩০ দিনের মধ্যে পুনরুদ্ধার\n২. Google Gemini টেস্ট পণ্য ও পরীক্ষামূলক কোম্পানির বাকি ব্যবস্থাপনা\n৩. অডিট ট্রেইল ও সম্পূর্ণ ডেটা পরিবর্তনের ইতিহাস",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )

                    Button(
                        onClick = onNavigateToDataProtectionCenter,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_open_data_protection_center")
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ডেটা সুরক্ষা কেন্দ্রে প্রবেশ করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Project & APK Export System Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_settings_export_system"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.DownloadForOffline, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                                }
                            }
                            Column {
                                Text(
                                    text = "প্রজেক্ট ও APK এক্সপোর্ট সিস্টেম",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1B5E20)
                                )
                                Text(
                                    text = "সোর্স কোড + ইনস্টলেশন উপযোগী আসল APK",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "প্রস্তুত ✅",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE0E0E0))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("APK ফাইল: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("release-files/SmartBusinessManager.apk", fontSize = 11.5.sp, color = PrimaryNavy, fontWeight = FontWeight.Medium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("এক্সপোর্ট জিপ: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("SmartBusinessManager_Export.zip", fontSize = 11.5.sp, color = PrimaryNavy, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            text = "• AI Studio-র Export ZIP ডাউনলোড করলে এই ইনস্টলযোগ্য APK ও সোর্স কোড উভয়ই একসাথে পাবেন।\n• টার্মিনালে 'gradle exportZip' বা './export_project.sh' চালালেও নতুন APK তৈরি ও প্যাকেজ তৈরি হবে।",
                            fontSize = 11.sp,
                            color = Color.DarkGray,
                            lineHeight = 16.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("APK Path", "release-files/SmartBusinessManager.apk"))
                            Toast.makeText(context, "APK পাথ ক্লিপবোর্ডে কপি করা হয়েছে: release-files/SmartBusinessManager.apk", Toast.LENGTH_LONG).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("btn_copy_apk_path")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("APK ফাইলের পাথ কপি করুন", fontSize = 12.sp)
                    }
                }
            }


            Button(
                onClick = {
                    viewModel.updateCompanySettings(
                        currentSettings.copy(
                            businessName = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            receiptFooter = footer.trim(),
                            productImageMode = imageMode
                        )
                    )
                    Toast.makeText(context, "সেটিংস সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
            ) {
                Text("সেটিংস সংরক্ষণ করুন", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesmenManagementScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val users by viewModel.users.collectAsStateWithLifecycle()
    val companySettings by viewModel.companySettings.collectAsStateWithLifecycle()
    val currentAdmin by viewModel.currentUser.collectAsStateWithLifecycle()
    val activeShopId = viewModel.getActiveBusinessId().ifBlank { currentAdmin?.shopId ?: "" }

    var showAddDialog by remember { mutableStateOf(false) }
    var userToResetPin by remember { mutableStateOf<User?>(null) }
    var userToEdit by remember { mutableStateOf<User?>(null) }

    // Status Change Dialog State
    var selectedUserForStatusChange by remember { mutableStateOf<User?>(null) }
    var targetStatus by remember { mutableStateOf(UserAccountStatus.ACTIVE) }
    var statusReasonInput by remember { mutableStateOf("") }
    var statusChangeError by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("সেলসম্যান ও স্টাফ ব্যবস্থাপনা", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("btn_add_new_staff")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "নতুন স্টাফ", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF3949AB),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Shop Identity Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = Color(0xFF3949AB),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = companySettings.businessName.ifBlank { "আমার দোকান" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1A237E)
                            )
                            Text(
                                text = "দোকান কোড: ${if (activeShopId.isNotBlank()) activeShopId else "স্বত্বাধিকারী"} • স্টাফ সংখ্যা: ${users.size} জন",
                                fontSize = 12.sp,
                                color = Color(0xFF3949AB)
                            )
                        }
                    }
                }
            }

            if (users.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "এই দোকানে এখনও কোনো অতিরিক্ত কর্মী যুক্ত করা হয়নি।\nউপরে '+' বাটনে ক্লিক করে নতুন স্টাফ যুক্ত করুন।",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            items(users, key = { it.id }) { u ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("staff_card_${u.phone}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (u.accountStatus) {
                            UserAccountStatus.ACTIVE -> MaterialTheme.colorScheme.surface
                            UserAccountStatus.SUSPENDED -> Color(0xFFFFF8E1)
                            UserAccountStatus.DEACTIVATED -> Color(0xFFFFEBEE)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(u.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (u.role == UserRole.ADMIN) Color(0xFFE3F2FD) else Color(0xFFEDE7F6)
                                    ) {
                                        Text(
                                            text = if (u.role == UserRole.ADMIN) "এডমিন" else "সেলসম্যান",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (u.role == UserRole.ADMIN) Color(0xFF1565C0) else Color(0xFF512DA8),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text("মোবাইল: ${u.phone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }

                            // Status Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (u.accountStatus) {
                                    UserAccountStatus.ACTIVE -> Color(0xFFE8F5E9)
                                    UserAccountStatus.SUSPENDED -> Color(0xFFFFECB3)
                                    UserAccountStatus.DEACTIVATED -> Color(0xFFFFCDD2)
                                }
                            ) {
                                Text(
                                    text = when (u.accountStatus) {
                                        UserAccountStatus.ACTIVE -> "সক্রিয়"
                                        UserAccountStatus.SUSPENDED -> "স্থগিত"
                                        UserAccountStatus.DEACTIVATED -> "বিচ্ছিন্ন"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (u.accountStatus) {
                                        UserAccountStatus.ACTIVE -> AccentGreen
                                        UserAccountStatus.SUSPENDED -> Color(0xFFB78103)
                                        UserAccountStatus.DEACTIVATED -> AccentRed
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (u.statusReason.isNotBlank() && u.accountStatus != UserAccountStatus.ACTIVE) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "কারণ: ${u.statusReason}",
                                fontSize = 11.sp,
                                color = if (u.accountStatus == UserAccountStatus.SUSPENDED) Color(0xFF8D6E00) else AccentRed,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "অনুমতি: ${if (u.canDiscount) "ডিসকাউন্ট" else "ডিসকাউন্ট বন্ধ"} • ${if (u.canCollectPayment) "টাকা আদায়" else "আদায় বন্ধ"} • ${if (u.canApplyExpense) "খরচ এন্ট্রি" else "খরচ বন্ধ"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val joinDateStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(u.createdAt))
                        Text(
                            text = "যুক্ত হয়েছেন: $joinDateStr",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Action Buttons: Edit, Reset PIN, Suspend / Reactivate
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Edit Profile & Permissions
                            OutlinedButton(
                                onClick = { userToEdit = u },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_edit_staff_${u.phone}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("এডিট", fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Reset PIN
                            OutlinedButton(
                                onClick = { userToResetPin = u },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("btn_reset_pin_${u.phone}")
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("পিন রিসেট", fontSize = 11.sp)
                            }

                            // Don't allow suspending self
                            if (u.id != currentAdmin?.id) {
                                Spacer(modifier = Modifier.width(6.dp))
                                when (u.accountStatus) {
                                    UserAccountStatus.ACTIVE -> {
                                        OutlinedButton(
                                            onClick = {
                                                selectedUserForStatusChange = u
                                                targetStatus = UserAccountStatus.SUSPENDED
                                                statusReasonInput = ""
                                                statusChangeError = ""
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.PauseCircle, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("স্থগিত", fontSize = 11.sp, color = AccentAmber)
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        OutlinedButton(
                                            onClick = {
                                                selectedUserForStatusChange = u
                                                targetStatus = UserAccountStatus.DEACTIVATED
                                                statusReasonInput = ""
                                                statusChangeError = ""
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Block, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("বিচ্ছিন্ন", fontSize = 11.sp, color = AccentRed)
                                        }
                                    }

                                    UserAccountStatus.SUSPENDED -> {
                                        Button(
                                            onClick = {
                                                selectedUserForStatusChange = u
                                                targetStatus = UserAccountStatus.ACTIVE
                                                statusReasonInput = "সাময়িক স্থগিতাদেশ প্রত্যাহার"
                                                statusChangeError = ""
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("সক্রিয়", fontSize = 11.sp)
                                        }
                                    }

                                    UserAccountStatus.DEACTIVATED -> {
                                        Button(
                                            onClick = {
                                                selectedUserForStatusChange = u
                                                targetStatus = UserAccountStatus.ACTIVE
                                                statusReasonInput = "অ্যাকাউন্ট পুনরায় চালু করা হয়েছে"
                                                statusChangeError = ""
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("সক্রিয়", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Status Change Confirmation Dialog (Suspend / Deactivate / Reactivate)
    if (selectedUserForStatusChange != null) {
        val userToChange = selectedUserForStatusChange!!
        val actionName = when (targetStatus) {
            UserAccountStatus.ACTIVE -> "পুনঃসক্রিয় (Reactivate)"
            UserAccountStatus.SUSPENDED -> "স্থগিত (Suspend)"
            UserAccountStatus.DEACTIVATED -> "বিচ্ছিন্ন / নিষ্ক্রিয় (Deactivate)"
        }

        AlertDialog(
            onDismissRequest = { selectedUserForStatusChange = null },
            title = { Text("স্টাফ অ্যাকাউন্ট $actionName নিশ্চিতকরণ", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("কর্মী: ${userToChange.name} (${userToChange.phone})", fontWeight = FontWeight.Bold)
                    Text(
                        text = when (targetStatus) {
                            UserAccountStatus.SUSPENDED -> "সতর্কতা: স্থগিত অবস্থায় এই কর্মী সিস্টেমে লগইন করতে পারবে না এবং কোনো নতুন বিক্রি, কালেকশন বা খরচ করতে পারবে না।"
                            UserAccountStatus.DEACTIVATED -> "সতর্কতা: ডিঅ্যাক্টিভেট করলে অ্যাকাউন্ট সম্পূর্ণ বিচ্ছিন্ন হয়ে যাবে। পূর্ববর্তী সমস্ত হিসাব অপরিবর্তিত থাকবে।"
                            UserAccountStatus.ACTIVE -> "এই কর্মী পুনরায় সিস্টেমে লগইন করে স্বাভাবিক ব্যবসায়িক কার্যক্রম পরিচালনা করতে পারবে।"
                        },
                        fontSize = 12.sp,
                        color = if (targetStatus == UserAccountStatus.ACTIVE) AccentGreen else AccentRed
                    )

                    OutlinedTextField(
                        value = statusReasonInput,
                        onValueChange = { statusReasonInput = it },
                        label = { Text("পদক্ষেপের সুনির্দিষ্ট কারণ লিখুন *") },
                        placeholder = { Text("যেমন: ছুটি, অডিট তদন্ত, চাকরি ত্যাগ") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (statusChangeError.isNotBlank()) {
                        Text(statusChangeError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (statusReasonInput.isBlank()) {
                            statusChangeError = "অনুগ্রহ করে কারণ উল্লেখ করুন!"
                            return@Button
                        }
                        viewModel.changeSalesmanStatus(
                            targetUserId = userToChange.id,
                            newStatus = targetStatus,
                            reason = statusReasonInput.trim(),
                            onSuccess = {
                                selectedUserForStatusChange = null
                                Toast.makeText(context, "স্ট্যাটাস পরিবর্তন সম্পন্ন হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                            onError = { statusChangeError = it }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (targetStatus) {
                            UserAccountStatus.ACTIVE -> AccentGreen
                            UserAccountStatus.SUSPENDED -> AccentAmber
                            UserAccountStatus.DEACTIVATED -> AccentRed
                        }
                    )
                ) {
                    Text("নিশ্চিত করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedUserForStatusChange = null }) { Text("বাতিল") }
            }
        )
    }

    // Modal PIN Reset Dialog
    if (userToResetPin != null) {
        val target = userToResetPin!!
        var newPin by remember { mutableStateOf("") }
        var resetError by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { userToResetPin = null },
            title = { Text("স্টাফের পিন কোড রিসেট", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("কর্মী: ${target.name} (${target.phone})", fontWeight = FontWeight.Bold)
                    Text("নতুন ৪ থেকে ৬ সংখ্যার পিন কোড নির্ধারণ করুন:", fontSize = 13.sp)
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        label = { Text("নতুন পিন কোড *") },
                        placeholder = { Text("যেমন: 5678") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (resetError.isNotBlank()) {
                        Text(resetError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.trim().length < 4 || newPin.trim().length > 6) {
                            resetError = "পিন কোড ৪ থেকে ৬ সংখ্যার হতে হবে!"
                            return@Button
                        }
                        viewModel.resetStaffPin(target.id, newPin.trim()) { success, msg ->
                            if (success) {
                                userToResetPin = null
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                resetError = msg
                            }
                        }
                    }
                ) {
                    Text("পিন আপডেট করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToResetPin = null }) { Text("বাতিল") }
            }
        )
    }

    // Modal Edit Staff Profile & Permissions Dialog
    if (userToEdit != null) {
        val target = userToEdit!!
        var editName by remember { mutableStateOf(target.name) }
        var editRole by remember { mutableStateOf(target.role) }
        var editCanDiscount by remember { mutableStateOf(target.canDiscount) }
        var editCanViewDue by remember { mutableStateOf(target.canViewCustomerDue) }
        var editCanCollectPayment by remember { mutableStateOf(target.canCollectPayment) }
        var editCanApplyExpense by remember { mutableStateOf(target.canApplyExpense) }
        var editError by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { userToEdit = null },
            title = { Text("স্টাফের তথ্য ও অনুমতি সম্পাদনা", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("নাম *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("মোবাইল: ${target.phone} (অপরিবর্তনযোগ্য)", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

                    Text("পদবী নির্বাচন:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = editRole == UserRole.SALESMAN, onClick = { editRole = UserRole.SALESMAN })
                        Text("সেলসম্যান", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = editRole == UserRole.ADMIN, onClick = { editRole = UserRole.ADMIN })
                        Text("সহকারী এডমিন", fontSize = 13.sp)
                    }

                    Text("অনুমতিসমূহ:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editCanDiscount, onCheckedChange = { editCanDiscount = it })
                        Text("ডিসকাউন্ট অনুমোদন", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editCanViewDue, onCheckedChange = { editCanViewDue = it })
                        Text("কাস্টমার বকেয়া দেখা", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editCanCollectPayment, onCheckedChange = { editCanCollectPayment = it })
                        Text("বকেয়া টাকা আদায়", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editCanApplyExpense, onCheckedChange = { editCanApplyExpense = it })
                        Text("খরচ এন্ট্রি", fontSize = 13.sp)
                    }

                    if (editError.isNotBlank()) {
                        Text(editError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.trim().length < 2) {
                            editError = "সঠিক নাম লিখুন!"
                            return@Button
                        }
                        val updated = target.copy(
                            name = editName.trim(),
                            role = editRole,
                            canDiscount = editCanDiscount,
                            canViewCustomerDue = editCanViewDue,
                            canCollectPayment = editCanCollectPayment,
                            canApplyExpense = editCanApplyExpense
                        )
                        viewModel.updateStaffProfile(updated) { success, msg ->
                            if (success) {
                                userToEdit = null
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                editError = msg
                            }
                        }
                    }
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToEdit = null }) { Text("বাতিল") }
            }
        )
    }

    // Modal Add Staff Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(UserRole.SALESMAN) }
        var canDiscount by remember { mutableStateOf(true) }
        var canCollectPayment by remember { mutableStateOf(true) }
        var canApplyExpense by remember { mutableStateOf(true) }
        var addError by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("নতুন কর্মী যোগ করুন", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("নাম *") },
                        placeholder = { Text("কর্মীর পুরো নাম") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_staff_name")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("মোবাইল নম্বর *") },
                        placeholder = { Text("01XXXXXXXXX") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("input_staff_phone")
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("গোপন পিন কোড *") },
                        placeholder = { Text("৪ থেকে ৬ সংখ্যার পিন") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("input_staff_pin")
                    )

                    Text("পদবী:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == UserRole.SALESMAN, onClick = { role = UserRole.SALESMAN })
                        Text("সেলসম্যান", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = role == UserRole.ADMIN, onClick = { role = UserRole.ADMIN })
                        Text("সহকারী এডমিন", fontSize = 13.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canDiscount, onCheckedChange = { canDiscount = it })
                        Text("ডিসকাউন্ট দেওয়ার অনুমতি", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = canCollectPayment, onCheckedChange = { canCollectPayment = it })
                        Text("বকেয়া টাকা আদায়", fontSize = 13.sp)
                    }

                    if (addError.isNotBlank()) {
                        Text(addError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank() || phone.isBlank() || pin.isBlank()) {
                            addError = "সকল প্রয়োজনীয় তথ্য পূরণ করুন!"
                            return@Button
                        }
                        viewModel.createStaffMember(
                            name = name.trim(),
                            phone = phone.trim(),
                            rawPin = pin.trim(),
                            role = role,
                            canDiscount = canDiscount,
                            canViewCustomerDue = true,
                            canCollectPayment = canCollectPayment,
                            canApplyExpense = canApplyExpense
                        ) { success, msg ->
                            if (success) {
                                showAddDialog = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                addError = msg
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_save_new_staff")
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("বাতিল") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogsScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val logs by viewModel.auditLogs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("নিরাপত্তা ও অডিট লগ", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF546E7A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(log.action, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryNavy)
                            Text(BengaliFormatter.formatDate(log.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(log.details, fontSize = 12.sp)
                        val displayUser = if (log.userId == "admin_1" || log.userName.contains("আতাউর")) {
                            BengaliFormatter.formatAdminDisplayName(log.userName)
                        } else {
                            log.userName
                        }
                        Text("ব্যবহারকারী: $displayUser (${log.userId})", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
