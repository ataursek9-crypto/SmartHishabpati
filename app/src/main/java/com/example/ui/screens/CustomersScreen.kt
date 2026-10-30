package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.data.model.Customer
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.util.PdfReceiptGenerator
import com.example.viewmodel.BusinessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit,
    onCollectPaymentForCustomer: (String) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isAdmin = currentUser?.role == UserRole.ADMIN
    val settings by viewModel.companySettings.collectAsStateWithLifecycle()

    // Role-filtered authorized customers and dues
    val customers by viewModel.authorizedCustomers.collectAsStateWithLifecycle()
    val dueCustomersList by viewModel.authorizedDueCustomers.collectAsStateWithLifecycle()
    val lastTxMap by viewModel.lastTransactionDateMap.collectAsStateWithLifecycle()

    val allSales by viewModel.allSales.collectAsStateWithLifecycle()
    val allReceipts by viewModel.allReceipts.collectAsStateWithLifecycle()
    val mySales by viewModel.mySales.collectAsStateWithLifecycle()
    val myReceipts by viewModel.myReceipts.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var onlyDueFilter by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var selectedCustomerForDetails by remember { mutableStateOf<Customer?>(null) }

    val filtered = remember(customers, searchQuery, onlyDueFilter) {
        customers.filter { c ->
            val matchesQuery = c.name.contains(searchQuery, ignoreCase = true) ||
                    c.phone.contains(searchQuery) ||
                    c.address.contains(searchQuery, ignoreCase = true)
            val matchesDue = if (onlyDueFilter) c.currentDue > 0.0 else true
            matchesQuery && matchesDue
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isAdmin) "কাস্টমার ও বকেয়া ব্যবস্থাপনা" else "আমার কাস্টমার ও বকেয়া",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    // PDF Download Button
                    IconButton(
                        onClick = {
                            val pdfFile = PdfReceiptGenerator.generateCustomerDueListPdf(
                                context = context,
                                settings = settings,
                                dueCustomers = dueCustomersList,
                                totalDueAmount = dueCustomersList.sumOf { it.customer.currentDue },
                                generatedByRole = if (isAdmin) "ADMIN" else "SALESMAN",
                                salesmanName = if (isAdmin) null else currentUser?.name
                            )
                            if (pdfFile != null) {
                                PdfReceiptGenerator.sharePdf(
                                    context,
                                    pdfFile,
                                    if (isAdmin) "বকেয়া কাস্টমার রিপোর্ট (Admin)" else "আমার বকেয়া কাস্টমারদের রিপোর্ট"
                                )
                            } else {
                                Toast.makeText(context, "PDF তৈরিতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("action_download_due_pdf")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "বকেয়া PDF", tint = Color.White)
                    }

                    IconButton(onClick = { showAddCustomerDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "নতুন কাস্টমার", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryNavy,
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
        ) {
            // Search & Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("নাম বা ফোন দিয়ে খুঁজুন...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_customer_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                FilterChip(
                    selected = !onlyDueFilter,
                    onClick = { onlyDueFilter = false },
                    label = { Text("সব (${customers.size})", fontSize = 11.sp) },
                    modifier = Modifier.testTag("filter_all_customers")
                )

                FilterChip(
                    selected = onlyDueFilter,
                    onClick = { onlyDueFilter = true },
                    label = { Text("শুধু বকেয়া (${customers.count { it.currentDue > 0 }})", fontSize = 11.sp) },
                    modifier = Modifier.testTag("filter_due_customers")
                )
            }

            // Summary & PDF Action Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "অনুমোদিত গ্রাহক: ${customers.size} জন",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "বকেয়াদার: ${dueCustomersList.size} জন • মোট বকেয়া: ${BengaliFormatter.formatCurrency(dueCustomersList.sumOf { it.customer.currentDue })}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed
                            )
                        }

                        // Prominent PDF Button inside Screen Header
                        FilledTonalButton(
                            onClick = {
                                val pdfFile = PdfReceiptGenerator.generateCustomerDueListPdf(
                                    context = context,
                                    settings = settings,
                                    dueCustomers = dueCustomersList,
                                    totalDueAmount = dueCustomersList.sumOf { it.customer.currentDue },
                                    generatedByRole = if (isAdmin) "ADMIN" else "SALESMAN",
                                    salesmanName = if (isAdmin) null else currentUser?.name
                                )
                                if (pdfFile != null) {
                                    PdfReceiptGenerator.sharePdf(
                                        context,
                                        pdfFile,
                                        if (isAdmin) "বকেয়া কাস্টমার রিপোর্ট (Admin)" else "আমার বকেয়া কাস্টমারদের রিপোর্ট"
                                    )
                                } else {
                                    Toast.makeText(context, "PDF তৈরিতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_header_download_pdf")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentRed)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("বকেয়া PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PeopleOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (onlyDueFilter) "কোনো কাস্টমারের বকেয়া নেই" else "কোনো কাস্টমার পাওয়া যায়নি",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered) { c ->
                        val lastTx = lastTxMap[c.id] ?: c.updatedAt
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCustomerForDetails = c },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        IconButton(
                                            onClick = { viewModel.toggleCustomerPin(c.id, c.isPinned) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (c.isPinned) Icons.Default.PushPin else Icons.Default.BookmarkBorder,
                                                contentDescription = "পিন করুন",
                                                tint = if (c.isPinned) AccentAmber else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("মোবাইল: ${c.phone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { selectedCustomerForDetails = c },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("বিস্তারিত", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        if (c.currentDue > 0) {
                                            FilledTonalButton(
                                                onClick = { onCollectPaymentForCustomer(c.id) },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text("আদায়", fontSize = 11.sp, color = SecondaryTeal, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                if (c.address.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("ঠিকানা: ${c.address}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "সর্বশেষ লেনদেন: " + (if (lastTx > 0) BengaliFormatter.formatDateOnly(lastTx) else "নেই"),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("মোট বিল", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(BengaliFormatter.formatCurrency(c.totalBilled), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Column {
                                        Text("মোট জমা", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(BengaliFormatter.formatCurrency(c.totalPaid), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AccentGreen)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("মোট বকেয়া", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        Text(
                                            text = BengaliFormatter.formatCurrency(c.currentDue),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (c.currentDue > 0) AccentRed else AccentGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Add Customer Dialog
    if (showAddCustomerDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCustomerDialog = false },
            title = { Text("নতুন কাস্টমার নিবন্ধন", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("কাস্টমারের নাম *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_customer_name_input")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("মোবাইল নম্বর *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_customer_phone_input")
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("ঠিকানা / বাজার") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("add_customer_address_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && phone.isNotBlank()) {
                            viewModel.saveCustomer(
                                Customer(
                                    id = "c_${System.currentTimeMillis()}",
                                    name = name.trim(),
                                    phone = phone.trim(),
                                    address = address.trim()
                                )
                            ) {
                                showAddCustomerDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("btn_save_new_customer")
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomerDialog = false }) { Text("বাতিল") }
            }
        )
    }

    // Detailed Customer Ledger Dialog (Sales, Receipts, Dues)
    if (selectedCustomerForDetails != null) {
        val cust = selectedCustomerForDetails!!
        val customerSales = remember(cust.id, allSales, mySales) {
            val sourceSales = if (isAdmin) allSales else mySales
            sourceSales.filter { it.customerId == cust.id }.sortedByDescending { it.createdAt }
        }
        val customerReceipts = remember(cust.id, allReceipts, myReceipts) {
            val sourceReceipts = if (isAdmin) allReceipts else myReceipts
            sourceReceipts.filter { it.customerId == cust.id }.sortedByDescending { it.createdAt }
        }

        AlertDialog(
            onDismissRequest = { selectedCustomerForDetails = null },
            title = {
                Column {
                    Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryNavy)
                    Text("মোবাইল: ${cust.phone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (cust.address.isNotBlank()) {
                        Text("ঠিকানা: ${cust.address}", fontSize = 13.sp)
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("মোট ক্রয় / বিল:", fontSize = 13.sp)
                        Text(BengaliFormatter.formatCurrency(cust.totalBilled), fontWeight = FontWeight.SemiBold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("মোট পরিশোধ / জমা:", fontSize = 13.sp)
                        Text(BengaliFormatter.formatCurrency(cust.totalPaid), fontWeight = FontWeight.SemiBold, color = AccentGreen)
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("বর্তমান নিট বকেয়া:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(BengaliFormatter.formatCurrency(cust.currentDue), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                    }

                    // Recent Sales / Invoices
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("সাম্প্রতিক বিক্রয় চালান:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryNavy)
                    if (customerSales.isEmpty()) {
                        Text("কোনো রেকর্ড নেই", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            customerSales.take(3).forEach { s ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(s.invoiceNumber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(BengaliFormatter.formatDateOnly(s.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                        Text(BengaliFormatter.formatCurrency(s.totalAmount), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    // Recent Payment Receipts
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("সাম্প্রতিক জমা / রসিদ:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SecondaryTeal)
                    if (customerReceipts.isEmpty()) {
                        Text("কোনো রেকর্ড নেই", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            customerReceipts.take(3).forEach { r ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(r.receiptNumber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(BengaliFormatter.formatDateOnly(r.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                        Text(BengaliFormatter.formatCurrency(r.amount), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (cust.currentDue > 0) {
                    Button(
                        onClick = {
                            val custId = cust.id
                            selectedCustomerForDetails = null
                            onCollectPaymentForCustomer(custId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                        modifier = Modifier.testTag("dialog_collect_payment_btn")
                    ) {
                        Icon(Icons.Default.Paid, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("টাকা আদায় করুন")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCustomerForDetails = null }) {
                    Text("বন্ধ করুন")
                }
            }
        )
    }
}
