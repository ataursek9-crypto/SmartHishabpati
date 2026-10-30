package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.util.PdfReceiptGenerator
import com.example.viewmodel.BusinessViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyDueScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val companies by viewModel.companies.collectAsStateWithLifecycle()
    val purchases by viewModel.companyPurchases.collectAsStateWithLifecycle()
    val payments by viewModel.companyPayments.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val settings by viewModel.companySettings.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("ALL") } // "ALL", "HAS_DUE", "PAID", "HAS_ADVANCE"

    // Dialog & Detail states
    var showAddCompanyDialog by remember { mutableStateOf(false) }
    var companyToEdit by remember { mutableStateOf<Company?>(null) }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var preselectedCompanyForPurchase by remember { mutableStateOf<Company?>(null) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var preselectedCompanyForPayment by remember { mutableStateOf<Company?>(null) }
    var selectedCompanyForDetails by remember { mutableStateOf<Company?>(null) }

    // Summary calculations across all companies
    val totalPurchasedAll = remember(companies) { companies.sumOf { it.totalPurchased } }
    val totalPaidAll = remember(companies) { companies.sumOf { it.totalPaid } }
    val totalDueAll = remember(companies) { companies.sumOf { it.currentDue } }
    val totalAdvanceAll = remember(companies) { companies.sumOf { it.advanceAmount } }

    val filteredCompanies = remember(companies, searchQuery, filterType) {
        companies.filter { comp ->
            val matchesQuery = searchQuery.isBlank() ||
                    comp.name.contains(searchQuery, ignoreCase = true) ||
                    comp.phone.contains(searchQuery, ignoreCase = true) ||
                    comp.address.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (filterType) {
                "HAS_DUE" -> comp.currentDue > 0
                "PAID" -> comp.currentDue <= 0 && comp.advanceAmount <= 0
                "HAS_ADVANCE" -> comp.advanceAmount > 0
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "কোম্পানির লেনদেন ও বকেয়া",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "সাপ্লায়ার হিসাব, ক্রয় চালান ও পেমেন্ট খতিয়ান",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_company_due")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddCompanyDialog = true },
                        modifier = Modifier.testTag("btn_topbar_add_company")
                    ) {
                        Icon(Icons.Default.AddBusiness, contentDescription = "নতুন কোম্পানি", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryNavy)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Overall Financial KPI Banner
            Surface(
                color = PrimaryNavy.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "সর্বমোট কোম্পানি ও সাপ্লায়ার হিসাব",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = PrimaryNavy
                        )
                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "মোট ${companies.size}টি কোম্পানি",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Total Purchases
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("মোট কেনাকাটা", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    BengaliFormatter.formatCurrency(totalPurchasedAll),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryNavy
                                )
                            }
                        }

                        // Total Paid
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("মোট পরিশোধ", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    BengaliFormatter.formatCurrency(totalPaidAll),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }

                        // Total Due
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (totalDueAll > 0) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("মোট বকেয়া (বাকি)", fontSize = 11.sp, color = AccentRed)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    BengaliFormatter.formatCurrency(totalDueAll),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRed
                                )
                            }
                        }
                    }

                    if (totalAdvanceAll > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "কোম্পানিতে অগ্রিম জমা আছে: ${BengaliFormatter.formatCurrency(totalAdvanceAll)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SecondaryTeal
                            )
                        }
                    }
                }
            }

            // Quick Operational Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showAddCompanyDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                    modifier = Modifier.weight(1f).testTag("btn_action_add_company"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("নতুন কোম্পানি", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        preselectedCompanyForPurchase = null
                        showPurchaseDialog = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.weight(1f).testTag("btn_action_company_purchase"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("পণ্য ক্রয় এন্ট্রি", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        preselectedCompanyForPayment = null
                        showPaymentDialog = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                    modifier = Modifier.weight(1f).testTag("btn_action_company_payment"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("টাকা পরিশোধ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .testTag("input_search_companies"),
                placeholder = { Text("কোম্পানির নাম, মোবাইল বা ঠিকানা দিয়ে খুঁজুন...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "মুছুন")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterType == "ALL",
                    onClick = { filterType = "ALL" },
                    label = { Text("সব কোম্পানি (${companies.size})", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = filterType == "HAS_DUE",
                    onClick = { filterType = "HAS_DUE" },
                    label = { Text("বকেয়া আছে (${companies.count { it.currentDue > 0 }})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFEBEE),
                        selectedLabelColor = AccentRed
                    )
                )
                FilterChip(
                    selected = filterType == "PAID",
                    onClick = { filterType = "PAID" },
                    label = { Text("পরিশোধিত (${companies.count { it.currentDue <= 0 && it.advanceAmount <= 0 }})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFE8F5E9),
                        selectedLabelColor = Color(0xFF2E7D32)
                    )
                )
                FilterChip(
                    selected = filterType == "HAS_ADVANCE",
                    onClick = { filterType = "HAS_ADVANCE" },
                    label = { Text("অগ্রিম জমা (${companies.count { it.advanceAmount > 0 }})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SecondaryTeal.copy(alpha = 0.15f),
                        selectedLabelColor = SecondaryTeal
                    )
                )
            }

            // Company List
            if (filteredCompanies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "কোনো কোম্পানি খুঁজে পাওয়া যায়নি" else "কোম্পানির তালিকা খালি। নতুন কোম্পানি যোগ করুন।",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCompanies, key = { it.id }) { company ->
                        CompanyCard(
                            company = company,
                            onViewDetails = { selectedCompanyForDetails = company },
                            onNewPurchase = {
                                preselectedCompanyForPurchase = company
                                showPurchaseDialog = true
                            },
                            onNewPayment = {
                                preselectedCompanyForPayment = company
                                showPaymentDialog = true
                            },
                            onEdit = { companyToEdit = company }
                        )
                    }
                }
            }
        }
    }

    // Modal Dialog: Add / Edit Company
    if (showAddCompanyDialog || companyToEdit != null) {
        val isNew = companyToEdit == null
        val comp = companyToEdit ?: Company(
            id = "comp_${System.currentTimeMillis()}",
            name = "",
            phone = "",
            address = "",
            note = ""
        )
        AddEditCompanyDialog(
            initialCompany = comp,
            isNew = isNew,
            onDismiss = {
                showAddCompanyDialog = false
                companyToEdit = null
            },
            onSave = { updatedComp ->
                viewModel.saveCompany(
                    company = updatedComp,
                    isNew = isNew,
                    onSuccess = {
                        showAddCompanyDialog = false
                        companyToEdit = null
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    // Modal Dialog: Record Company Purchase
    if (showPurchaseDialog) {
        RecordCompanyPurchaseDialog(
            companies = companies,
            preselectedCompany = preselectedCompanyForPurchase,
            products = products,
            onDismiss = { showPurchaseDialog = false },
            onSubmit = { purchase ->
                viewModel.recordCompanyPurchase(
                    purchase = purchase,
                    onSuccess = {
                        showPurchaseDialog = false
                        Toast.makeText(context, "কোম্পানি থেকে পণ্য ক্রয়ের চালান সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    // Modal Dialog: Record Company Payment
    if (showPaymentDialog) {
        RecordCompanyPaymentDialog(
            companies = companies,
            preselectedCompany = preselectedCompanyForPayment,
            onDismiss = { showPaymentDialog = false },
            onSubmit = { payment ->
                viewModel.recordCompanyPayment(
                    payment = payment,
                    onSuccess = {
                        showPaymentDialog = false
                        Toast.makeText(context, "কোম্পানি পেমেন্ট সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }

    // Full Company Details & Ledger View
    if (selectedCompanyForDetails != null) {
        // Observe updated state of the active selected company
        val activeComp = companies.find { it.id == selectedCompanyForDetails!!.id } ?: selectedCompanyForDetails!!
        val companyPurchasesList = purchases.filter { it.companyId == activeComp.id }
        val companyPaymentsList = payments.filter { it.companyId == activeComp.id }

        CompanyDetailsDialog(
            company = activeComp,
            purchases = companyPurchasesList,
            payments = companyPaymentsList,
            settings = settings,
            onDismiss = { selectedCompanyForDetails = null },
            onNewPurchase = {
                preselectedCompanyForPurchase = activeComp
                showPurchaseDialog = true
            },
            onNewPayment = {
                preselectedCompanyForPayment = activeComp
                showPaymentDialog = true
            },
            onEditCompany = {
                companyToEdit = activeComp
            }
        )
    }
}

@Composable
fun CompanyCard(
    company: Company,
    onViewDetails: () -> Unit,
    onNewPurchase: () -> Unit,
    onNewPayment: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetails() }
            .testTag("company_card_${company.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name, Phone & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(company.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryNavy)
                        if (company.isTestData || company.name.contains("[টেস্ট]", ignoreCase = true)) {
                            Surface(
                                color = Color(0xFFF3E5F5),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "🧪 টেস্ট",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7B1FA2),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text("মোবাইল: ${company.phone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    if (company.address.isNotBlank()) {
                        Text("ঠিকানা: ${company.address}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }

                Surface(
                    color = when {
                        company.currentDue > 0 -> Color(0xFFFFEBEE)
                        company.advanceAmount > 0 -> Color(0xFFE0F2F1)
                        else -> Color(0xFFE8F5E9)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when {
                            company.currentDue > 0 -> "বকেয়া: ৳${company.currentDue}"
                            company.advanceAmount > 0 -> "অগ্রিম: ৳${company.advanceAmount}"
                            else -> "পরিশোধিত"
                        },
                        color = when {
                            company.currentDue > 0 -> AccentRed
                            company.advanceAmount > 0 -> SecondaryTeal
                            else -> Color(0xFF2E7D32)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Financial Metrics Row
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("মোট ক্রয়: ${BengaliFormatter.formatCurrency(company.totalPurchased)}", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    Text("মোট পরিশোধ: ${BengaliFormatter.formatCurrency(company.totalPaid)}", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2E7D32))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewDetails,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_view_ledger_${company.id}")
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("বিস্তারিত লেজার", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onNewPurchase,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_card_purchase_${company.id}")
                    ) {
                        Text("+ ক্রয়", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onNewPayment,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_card_pay_${company.id}")
                    ) {
                        Text("+ পেমেন্ট", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp).testTag("btn_card_edit_${company.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "এডিট", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AddEditCompanyDialog(
    initialCompany: Company,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Company) -> Unit
) {
    var name by remember { mutableStateOf(initialCompany.name) }
    var phone by remember { mutableStateOf(initialCompany.phone) }
    var address by remember { mutableStateOf(initialCompany.address) }
    var note by remember { mutableStateOf(initialCompany.note) }
    var isTest by remember { mutableStateOf(initialCompany.isTestData) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "নতুন কোম্পানি / সাপ্লায়ার যোগ" else "কোম্পানির তথ্য পরিবর্তন", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("কোম্পানির নাম *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_company_name")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("যোগাযোগের মোবাইল নম্বর *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_company_phone")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("ঠিকানা (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_company_address")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("নোট বা পণ্যের বিবরণ (ঐচ্ছিক)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("input_company_note")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🧪 টেস্ট কোম্পানি হিসেবে চিহ্নিত করুন", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("পরীক্ষামূলক বা ডেমো কোম্পানি আলাদা থাকবে", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = isTest,
                        onCheckedChange = { isTest = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "কোম্পানির নাম আবশ্যক!"
                        return@Button
                    }
                    if (phone.isBlank()) {
                        errorMessage = "মোবাইল নম্বর আবশ্যক!"
                        return@Button
                    }
                    errorMessage = ""
                    onSave(
                        initialCompany.copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            note = note.trim(),
                            isTestData = isTest,
                            testTag = if (isTest) "DEMO" else ""
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.testTag("btn_save_company")
            ) {
                Text(if (isNew) "কোম্পানি সেভ করুন" else "আপডেট করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCompanyPurchaseDialog(
    companies: List<Company>,
    preselectedCompany: Company?,
    products: List<Product>,
    onDismiss: () -> Unit,
    onSubmit: (CompanyPurchase) -> Unit
) {
    var selectedCompany by remember { mutableStateOf(preselectedCompany ?: companies.firstOrNull()) }
    var challanNumber by remember { mutableStateOf("CHL-${System.currentTimeMillis().toString().takeLast(6)}") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var customProductName by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var unitText by remember { mutableStateOf("পিস") }
    var unitPriceText by remember { mutableStateOf("") }
    var paidAmountText by remember { mutableStateOf("0") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var addedToStock by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    var expandedCompanyDropdown by remember { mutableStateOf(false) }
    var expandedProductDropdown by remember { mutableStateOf(false) }

    // Math
    val qty = quantityText.toDoubleOrNull() ?: 0.0
    val rate = unitPriceText.toDoubleOrNull() ?: 0.0
    val totalAmount = (qty * rate).coerceAtLeast(0.0)
    val paidAmount = paidAmountText.toDoubleOrNull() ?: 0.0
    val dueAmount = (totalAmount - paidAmount).coerceAtLeast(0.0)
    val advanceAmount = if (paidAmount > totalAmount) paidAmount - totalAmount else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("কোম্পানি থেকে পণ্য ক্রয় এন্ট্রি", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                // 1. Company Selection
                Column {
                    Text("১. কোম্পানি / সাপ্লায়ার নির্বাচন *", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedCompanyDropdown,
                        onExpandedChange = { expandedCompanyDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCompany?.name ?: "কোম্পানি বেছে নিন",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCompanyDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("select_company_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCompanyDropdown,
                            onDismissRequest = { expandedCompanyDropdown = false }
                        ) {
                            companies.forEach { comp ->
                                DropdownMenuItem(
                                    text = { Text("${comp.name} (বকেয়া: ৳${comp.currentDue})") },
                                    onClick = {
                                        selectedCompany = comp
                                        expandedCompanyDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Challan number
                OutlinedTextField(
                    value = challanNumber,
                    onValueChange = { challanNumber = it },
                    label = { Text("চালান / ভাউচার নম্বর") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_purchase_challan")
                )

                // 2. Product Selection (Existing or Custom)
                Column {
                    Text("২. পণ্যের বিবরণ *", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedProductDropdown,
                        onExpandedChange = { expandedProductDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = selectedProduct?.name ?: if (customProductName.isNotBlank()) customProductName else "বিদ্যমান পণ্য নির্বাচন করুন (ঐচ্ছিক)",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProductDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("select_product_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProductDropdown,
                            onDismissRequest = { expandedProductDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("-- কাস্টম নতুন পণ্য লিখুন --") },
                                onClick = {
                                    selectedProduct = null
                                    expandedProductDropdown = false
                                }
                            )
                            products.forEach { prod ->
                                DropdownMenuItem(
                                    text = { Text("${prod.name} (স্টক: ${prod.currentStock} ${prod.unit})") },
                                    onClick = {
                                        selectedProduct = prod
                                        customProductName = prod.name
                                        unitText = prod.unit
                                        unitPriceText = prod.purchasePrice.toString()
                                        expandedProductDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedProduct == null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customProductName,
                            onValueChange = { customProductName = it },
                            label = { Text("পণ্যের নাম লিখুন *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_custom_product_name")
                        )
                    }
                }

                // 3. Quantity & Unit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("পরিমাণ *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_purchase_qty")
                    )

                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text("একক (কেজি/পিস)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_purchase_unit")
                    )
                }

                // 4. Unit Purchase Price
                OutlinedTextField(
                    value = unitPriceText,
                    onValueChange = { unitPriceText = it },
                    label = { Text("প্রতি এককের ক্রয়মূল্য (টাকা) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_purchase_rate")
                )

                // 5. Instant Payment
                OutlinedTextField(
                    value = paidAmountText,
                    onValueChange = { paidAmountText = it },
                    label = { Text("তাৎক্ষণিক পরিশোধিত টাকা (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_purchase_paid")
                )

                // Payment Method
                if (paidAmount > 0) {
                    Column {
                        Text("পেমেন্ট মাধ্যম", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PaymentMethod.entries.forEach { method ->
                                FilterChip(
                                    selected = paymentMethod == method,
                                    onClick = { paymentMethod = method },
                                    label = { Text(method.name, fontSize = 10.5.sp) }
                                )
                            }
                        }
                    }
                }

                // Stock Checkbox
                if (selectedProduct != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = addedToStock,
                            onCheckedChange = { addedToStock = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("বিদ্যমান পণ্যের স্টকে স্বয়ংক্রিয়ভাবে যুক্ত করুন (+${qty} ${unitText})", fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("নোট (ঐচ্ছিক)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Math Summary Box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("মোট ক্রয়মূল্য:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(BengaliFormatter.formatCurrency(totalAmount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("পরিশোধিত টাকা:", fontSize = 12.sp)
                            Text(BengaliFormatter.formatCurrency(paidAmount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        Divider(modifier = Modifier.padding(vertical = 2.dp))
                        if (dueAmount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("কোম্পানির নতুন বকেয়া (বাকি):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                                Text(BengaliFormatter.formatCurrency(dueAmount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                            }
                        } else if (advanceAmount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("অতিরিক্ত পরিশোধ (অগ্রিম জমা):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SecondaryTeal)
                                Text(BengaliFormatter.formatCurrency(advanceAmount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SecondaryTeal)
                            }
                        } else {
                            Text("হিসাব: সম্পূর্ণ পরিশোধিত", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val comp = selectedCompany
                    if (comp == null) {
                        errorMessage = "কোম্পানি নির্বাচন করা আবশ্যক!"
                        return@Button
                    }
                    val prodName = selectedProduct?.name ?: customProductName.trim()
                    if (prodName.isBlank()) {
                        errorMessage = "পণ্যের নাম আবশ্যক!"
                        return@Button
                    }
                    if (qty <= 0) {
                        errorMessage = "সঠিক পরিমাণ লিখুন!"
                        return@Button
                    }
                    if (rate < 0) {
                        errorMessage = "সঠিক ক্রয়মূল্য লিখুন!"
                        return@Button
                    }
                    errorMessage = ""

                    val purchase = CompanyPurchase(
                        id = "cpur_${System.currentTimeMillis()}",
                        companyId = comp.id,
                        companyName = comp.name,
                        challanNumber = challanNumber.trim(),
                        productId = selectedProduct?.id,
                        productName = prodName,
                        quantity = qty,
                        unit = unitText.trim().ifBlank { "পিস" },
                        unitPurchasePrice = rate,
                        totalAmount = totalAmount,
                        paidAmount = paidAmount,
                        dueAmount = dueAmount,
                        paymentMethod = paymentMethod,
                        addedToStock = addedToStock && selectedProduct != null,
                        note = note.trim(),
                        purchaseDate = System.currentTimeMillis()
                    )
                    onSubmit(purchase)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.testTag("btn_submit_purchase")
            ) {
                Text("ক্রয় নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCompanyPaymentDialog(
    companies: List<Company>,
    preselectedCompany: Company?,
    onDismiss: () -> Unit,
    onSubmit: (CompanyPayment) -> Unit
) {
    var selectedCompany by remember { mutableStateOf(preselectedCompany ?: companies.firstOrNull()) }
    var amountText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var referenceNumber by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    var expandedCompanyDropdown by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val currentDue = selectedCompany?.currentDue ?: 0.0
    val remainingDue = (currentDue - amount).coerceAtLeast(0.0)
    val resultingAdvance = if (amount > currentDue) amount - currentDue else (selectedCompany?.advanceAmount ?: 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("কোম্পানিকে বকেয়া টাকা পরিশোধ", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                // Company Selector
                Column {
                    Text("১. কোম্পানি নির্বাচন *", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedCompanyDropdown,
                        onExpandedChange = { expandedCompanyDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = selectedCompany?.let { "${it.name} (বকেয়া: ৳${it.currentDue})" } ?: "কোম্পানি বেছে নিন",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCompanyDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("select_payment_company_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = expandedCompanyDropdown,
                            onDismissRequest = { expandedCompanyDropdown = false }
                        ) {
                            companies.forEach { comp ->
                                DropdownMenuItem(
                                    text = { Text("${comp.name} • বর্তমান বকেয়া: ৳${comp.currentDue}") },
                                    onClick = {
                                        selectedCompany = comp
                                        expandedCompanyDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("পরিশোধিত টাকার পরিমাণ (৳) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_payment_amount")
                )

                // Quick Full Pay Button
                if (currentDue > 0) {
                    Button(
                        onClick = {
                            amountText = if (currentDue % 1.0 == 0.0) currentDue.toLong().toString()
                            else String.format(Locale.US, "%.2f", currentDue)
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("সম্পূর্ণ বকেয়া ৳${currentDue} পরিশোধ করুন", fontSize = 12.sp)
                    }
                }

                // Payment Method
                Column {
                    Text("পেমেন্ট মাধ্যম *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PaymentMethod.entries.forEach { method ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = { Text(method.name, fontSize = 10.5.sp) }
                            )
                        }
                    }
                }

                // Reference / Receipt No
                OutlinedTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = { Text("রসিদ / ট্রানজেকশন / চেক নম্বর (ঐচ্ছিক)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_payment_ref")
                )

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("নোট (ঐচ্ছিক)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // Impact Preview
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("পূর্বের মোট বকেয়া:", fontSize = 12.sp)
                            Text(BengaliFormatter.formatCurrency(currentDue), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("জমা হচ্ছে:", fontSize = 12.sp)
                            Text(BengaliFormatter.formatCurrency(amount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        Divider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("পরিশোধের পর অবশিষ্ট বকেয়া:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(BengaliFormatter.formatCurrency(remainingDue), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (remainingDue > 0) AccentRed else Color(0xFF2E7D32))
                        }
                        if (amount > currentDue) {
                            Text(
                                "অতিরিক্ত টাকা কোম্পানির অগ্রিম জমা হিসেবে থাকবে: ${BengaliFormatter.formatCurrency(resultingAdvance)}",
                                fontSize = 11.sp,
                                color = SecondaryTeal,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val comp = selectedCompany
                    if (comp == null) {
                        errorMessage = "কোম্পানি নির্বাচন করা আবশ্যক!"
                        return@Button
                    }
                    if (amount <= 0) {
                        errorMessage = "সঠিক পরিশোধের পরিমাণ লিখুন!"
                        return@Button
                    }
                    errorMessage = ""

                    val payment = CompanyPayment(
                        id = "cpay_${System.currentTimeMillis()}",
                        companyId = comp.id,
                        companyName = comp.name,
                        amount = amount,
                        paymentMethod = paymentMethod,
                        referenceNumber = referenceNumber.trim(),
                        note = note.trim(),
                        paymentDate = System.currentTimeMillis()
                    )
                    onSubmit(payment)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                modifier = Modifier.testTag("btn_submit_payment")
            ) {
                Text("পেমেন্ট নিশ্চিত করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun CompanyDetailsDialog(
    company: Company,
    purchases: List<CompanyPurchase>,
    payments: List<CompanyPayment>,
    settings: CompanySettings,
    onDismiss: () -> Unit,
    onNewPurchase: () -> Unit,
    onNewPayment: () -> Unit,
    onEditCompany: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Ledger, 1: Purchases, 2: Payments

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(company.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryNavy)
                    Text("মোবাইল: ${company.phone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                // KPI Summary Row
                Surface(
                    color = PrimaryNavy.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("মোট ক্রয়: ${BengaliFormatter.formatCurrency(company.totalPurchased)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("মোট পরিশোধ: ${BengaliFormatter.formatCurrency(company.totalPaid)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "বর্তমান বকেয়া: ${BengaliFormatter.formatCurrency(company.currentDue)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (company.currentDue > 0) AccentRed else Color(0xFF2E7D32)
                            )
                            if (company.advanceAmount > 0) {
                                Text(
                                    "অগ্রিম জমা: ${BengaliFormatter.formatCurrency(company.advanceAmount)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SecondaryTeal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onNewPurchase,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text("+ পণ্য ক্রয়", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onNewPayment,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text("+ টাকা পরিশোধ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val pdfFile = PdfReceiptGenerator.generateCompanyStatementPdf(
                                context = context,
                                settings = settings,
                                company = company,
                                purchases = purchases,
                                payments = payments
                            )
                            if (pdfFile != null) {
                                PdfReceiptGenerator.sharePdf(context, pdfFile, "${company.name} লেজার স্টেটমেন্ট")
                            } else {
                                Toast.makeText(context, "পিডিএফ তৈরিতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("PDF", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab Selector
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("খতিয়ান (${purchases.size + payments.size})", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("ক্রয় (${purchases.size})", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("পরিশোধ (${payments.size})", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab Content
                when (selectedTab) {
                    0 -> {
                        // Combined Ledger Chronological
                        val combinedList = remember(purchases, payments) {
                            val items = mutableListOf<LedgerDisplayItem>()
                            purchases.forEach { p ->
                                items.add(
                                    LedgerDisplayItem(
                                        date = p.purchaseDate,
                                        type = "ক্রয়",
                                        title = p.productName,
                                        subtitle = "চালান #${p.challanNumber.ifBlank { p.id.takeLast(6) }} • ${p.quantity} ${p.unit}",
                                        amount = p.totalAmount,
                                        paid = p.paidAmount,
                                        due = p.dueAmount,
                                        isPurchase = true
                                    )
                                )
                            }
                            payments.forEach { pay ->
                                items.add(
                                    LedgerDisplayItem(
                                        date = pay.paymentDate,
                                        type = "পেমেন্ট",
                                        title = "বকেয়া পরিশোধ (${pay.paymentMethod.name})",
                                        subtitle = "রেফারেন্স: ${pay.referenceNumber.ifBlank { "N/A" }}${if (pay.note.isNotBlank()) " • ${pay.note}" else ""}",
                                        amount = 0.0,
                                        paid = pay.amount,
                                        due = 0.0,
                                        isPurchase = false
                                    )
                                )
                            }
                            items.sortedByDescending { it.date }
                        }

                        if (combinedList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("কোনো লেনদেন রেকর্ড নেই", color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(combinedList) { item ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Surface(
                                                    color = if (item.isPurchase) PrimaryBlue.copy(alpha = 0.15f) else Color(0xFFE8F5E9),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        item.type,
                                                        color = if (item.isPurchase) PrimaryBlue else Color(0xFF2E7D32),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(item.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(BengaliFormatter.formatDateOnly(item.date), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                                if (item.isPurchase) {
                                                    Text(
                                                        "মোট: ${BengaliFormatter.formatCurrency(item.amount)} (পরিশোধ: ৳${item.paid})",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                } else {
                                                    Text(
                                                        "পরিশোধ: ${BengaliFormatter.formatCurrency(item.paid)}",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2E7D32)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Purchases List
                        if (purchases.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("কোনো ক্রয়ের চালান নেই", color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(purchases) { p ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(p.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text(BengaliFormatter.formatCurrency(p.totalAmount), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryNavy)
                                            }
                                            Text("পরিমাণ: ${p.quantity} ${p.unit} • দর: ৳${p.unitPurchasePrice}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                            Text("চালান: ${p.challanNumber.ifBlank { "N/A" }} • তারিখ: ${BengaliFormatter.formatDateOnly(p.purchaseDate)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("নগদ পরিশোধ: ৳${p.paidAmount}", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                                if (p.dueAmount > 0) {
                                                    Text("বকেয়া: ৳${p.dueAmount}", fontSize = 11.sp, color = AccentRed, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Payments List
                        if (payments.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("কোনো পেমেন্টের রেকর্ড নেই", color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(payments) { pay ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("পরিশোধিত: ${BengaliFormatter.formatCurrency(pay.amount)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                                                Text(pay.paymentMethod.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Text("রেফারেন্স: ${pay.referenceNumber.ifBlank { "N/A" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                            if (pay.note.isNotBlank()) {
                                                Text("নোট: ${pay.note}", fontSize = 11.sp)
                                            }
                                            Text("তারিখ: ${BengaliFormatter.formatDateOnly(pay.paymentDate)}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

data class LedgerDisplayItem(
    val date: Long,
    val type: String,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val paid: Double,
    val due: Double,
    val isPurchase: Boolean
)
