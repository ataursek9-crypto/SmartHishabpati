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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.viewmodel.BusinessViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataProtectionCenterScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isAdmin = currentUser?.role == UserRole.ADMIN

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Recycle Bin, 1: Test Data, 2: Opening Setup, 3: Audit Logs
    val tabs = listOf("রিসাইকেল বিন", "টেস্টিং ডেটা ব্যবস্থাপনা", "ওপেনিং হিসাব সেটআপ", "অডিট হিস্ট্রি")

    // Data from ViewModel
    val recycleBinItems by viewModel.recycleBinItems.collectAsStateWithLifecycle()
    val testProducts by viewModel.testProducts.collectAsStateWithLifecycle()
    val testCompanies by viewModel.testCompanies.collectAsStateWithLifecycle()
    val testCustomers by viewModel.testCustomers.collectAsStateWithLifecycle()
    val testSales by viewModel.testSales.collectAsStateWithLifecycle()
    val testReceipts by viewModel.testReceipts.collectAsStateWithLifecycle()
    val testExpenses by viewModel.testExpenses.collectAsStateWithLifecycle()
    val allCustomers by viewModel.customers.collectAsStateWithLifecycle()
    val rawProducts by viewModel.rawProducts.collectAsStateWithLifecycle()
    val allAuditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    val detectedTestDueCustomers by viewModel.detectedTestDueCustomers.collectAsStateWithLifecycle()
    val isDetectingTestDues by viewModel.isDetectingTestDues.collectAsStateWithLifecycle()
    val adminDashboardSummary by viewModel.adminDashboardSummary.collectAsStateWithLifecycle()

    val totalTestItems = testProducts.size + testCompanies.size + testCustomers.size + testSales.size + testReceipts.size + testExpenses.size

    // Dialog States
    var showDetectTestDueDialog by remember { mutableStateOf(false) }
    var itemForPermanentDelete by remember { mutableStateOf<RecycleBinItem?>(null) }
    var itemForRestoreConfirm by remember { mutableStateOf<RecycleBinItem?>(null) }
    var itemForSafeDeleteProduct by remember { mutableStateOf<Product?>(null) }
    var itemForSafeDeleteCompany by remember { mutableStateOf<Company?>(null) }
    var itemForSafeDeleteCustomer by remember { mutableStateOf<Customer?>(null) }
    var itemForSafeDeleteSale by remember { mutableStateOf<Sale?>(null) }
    var itemForSafeDeleteReceipt by remember { mutableStateOf<PaymentReceipt?>(null) }
    var itemForSafeDeleteExpense by remember { mutableStateOf<Expense?>(null) }

    var customerForOpeningDue by remember { mutableStateOf<Customer?>(null) }
    var productForOpeningStock by remember { mutableStateOf<Product?>(null) }

    var safeDeleteReasonInput by remember { mutableStateOf("") }
    var adminPinInput by remember { mutableStateOf("") }
    var openingAmountInput by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ডেটা সুরক্ষা কেন্দ্র (Data Protection)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "রিসাইকেল বিন • ৩০ দিনের রিকভারি • টেস্ট ডেটা পরিচ্ছন্নকরণ • ওপেনিং সেটআপ",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_data_protection")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান", tint = Color.White)
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
            // Tab Row
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryNavy
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                                when (index) {
                                    0 -> if (recycleBinItems.isNotEmpty()) {
                                        Badge(containerColor = AccentRed) {
                                            Text("${recycleBinItems.size}", fontSize = 9.sp)
                                        }
                                    }
                                    1 -> if (totalTestItems > 0) {
                                        Badge(containerColor = SecondaryTeal) {
                                            Text("$totalTestItems", fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> RecycleBinTabContent(
                    items = recycleBinItems,
                    onRestore = { item -> itemForRestoreConfirm = item },
                    onPermanentDelete = { item ->
                        itemForPermanentDelete = item
                        adminPinInput = ""
                        dialogError = ""
                    }
                )
                1 -> TestingDataTabContent(
                    testProducts = testProducts,
                    testCompanies = testCompanies,
                    testCustomers = testCustomers,
                    testSales = testSales,
                    testReceipts = testReceipts,
                    testExpenses = testExpenses,
                    onOpenDetectTestDues = {
                        viewModel.scanTestDueCustomers()
                        showDetectTestDueDialog = true
                    },
                    onCreateSampleData = {
                        isProcessing = true
                        viewModel.createSampleTestData(
                            onSuccess = { msg ->
                                isProcessing = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    onSafeDeleteProduct = { prod ->
                        itemForSafeDeleteProduct = prod
                        safeDeleteReasonInput = "পরীক্ষামূলক টেস্ট পণ্য রিসাইকেল বিনে স্থানান্তর"
                    },
                    onSafeDeleteCompany = { comp ->
                        itemForSafeDeleteCompany = comp
                        safeDeleteReasonInput = "পরীক্ষামূলক টেস্ট কোম্পানি রিসাইকেল বিনে স্থানান্তর"
                    },
                    onSafeDeleteCustomer = { cust ->
                        itemForSafeDeleteCustomer = cust
                        safeDeleteReasonInput = "পরীক্ষামূলক টেস্ট কাস্টমার রিসাইকেল বিনে স্থানান্তর"
                    },
                    onSafeDeleteSale = { sale ->
                        itemForSafeDeleteSale = sale
                        safeDeleteReasonInput = "পরীক্ষামূলক টেস্ট বিক্রি রিসাইকেল বিনে স্থানান্তর"
                    },
                    onSafeDeleteReceipt = { rct ->
                        itemForSafeDeleteReceipt = rct
                        safeDeleteReasonInput = "পরীক্ষামূলক টেস্ট পেমেন্ট রসিদ রিসাইকেল বিনে স্থানান্তর"
                    },
                    onSafeDeleteExpense = { exp ->
                        itemForSafeDeleteExpense = exp
                        safeDeleteReasonInput = "পরীক্ষামূলক টেস্ট খরচ রিসাইকেল বিনে স্থানান্তর"
                    },
                    onUntagProduct = { prod ->
                        viewModel.toggleProductTestDataStatus(prod.id, false, "")
                        Toast.makeText(context, "'${prod.name}' এখন মূল ব্যবসায়িক পণ্য হিসেবে চিহ্নিত", Toast.LENGTH_SHORT).show()
                    },
                    onUntagCompany = { comp ->
                        viewModel.toggleCompanyTestDataStatus(comp.id, false, "")
                        Toast.makeText(context, "'${comp.name}' এখন বাস্তব কোম্পানি হিসেবে চিহ্নিত", Toast.LENGTH_SHORT).show()
                    },
                    onUntagCustomer = { cust ->
                        viewModel.toggleCustomerTestDataStatus(cust.id, false, "")
                        Toast.makeText(context, "'${cust.name}' এখন মূল গ্রাহক হিসেবে চিহ্নিত", Toast.LENGTH_SHORT).show()
                    },
                    onUntagSale = { sale ->
                        viewModel.toggleSaleTestDataStatus(sale.invoiceNumber, false, "")
                        Toast.makeText(context, "ইনভয়েস #${sale.invoiceNumber} এখন বাস্তব বিক্রি হিসেবে চিহ্নিত", Toast.LENGTH_SHORT).show()
                    },
                    onUntagReceipt = { rct ->
                        viewModel.toggleReceiptTestDataStatus(rct.receiptNumber, false, "")
                        Toast.makeText(context, "রসিদ #${rct.receiptNumber} এখন বাস্তব পেমেন্ট হিসেবে চিহ্নিত", Toast.LENGTH_SHORT).show()
                    },
                    onUntagExpense = { exp ->
                        viewModel.toggleExpenseTestDataStatus(exp.id, false, "")
                        Toast.makeText(context, "খরচ '${exp.description}' এখন মূল ব্যবসায়িক খরচ হিসেবে চিহ্নিত", Toast.LENGTH_SHORT).show()
                    }
                )
                2 -> OpeningBalanceSetupTabContent(
                    customers = allCustomers,
                    products = rawProducts,
                    onEditCustomerOpeningDue = { cust ->
                        customerForOpeningDue = cust
                        openingAmountInput = if (cust.openingDue > 0) cust.openingDue.toString() else ""
                        adminPinInput = ""
                        dialogError = ""
                    },
                    onEditProductOpeningStock = { prod ->
                        productForOpeningStock = prod
                        openingAmountInput = if (prod.currentStock > 0) prod.currentStock.toString() else ""
                        adminPinInput = ""
                        dialogError = ""
                    }
                )
                3 -> AuditLogsTabContent(logs = allAuditLogs)
            }
        }
    }

    // Dialog: Permanent Delete Confirmation with Admin PIN
    if (itemForPermanentDelete != null) {
        val targetItem = itemForPermanentDelete!!
        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) itemForPermanentDelete = null
            },
            icon = {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = AccentRed, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("স্থায়ীভাবে মুছে ফেলার সতর্কতা!", fontWeight = FontWeight.Bold, color = AccentRed)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "আপনি '${targetItem.itemName}' (${targetItem.itemType}) স্থায়ীভাবে মুছে ফেলতে চলেছেন।",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ সতর্কবার্তা: স্থায়ীভাবে ডিলিট করার পর এই রেকর্ড আর কখনোই পুনরুদ্ধার করা যাবে না!",
                            color = AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text("নিরাপত্তা নিশ্চিত করতে এডমিন পিন (PIN) লিখুন:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = adminPinInput,
                        onValueChange = {
                            adminPinInput = it
                            dialogError = ""
                        },
                        label = { Text("এডমিন পিন (PIN)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_admin_pin_permanent_delete")
                    )

                    if (dialogError.isNotBlank()) {
                        Text(
                            text = dialogError,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (adminPinInput.isBlank()) {
                            dialogError = "অনুগ্রহ করে এডমিন পিন দিন!"
                            return@Button
                        }
                        isProcessing = true
                        viewModel.permanentlyDeleteRecycleBinItem(
                            item = targetItem,
                            pin = adminPinInput.trim(),
                            onSuccess = {
                                isProcessing = false
                                itemForPermanentDelete = null
                                Toast.makeText(context, "রেকর্ড স্থায়ীভাবে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                dialogError = err
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    enabled = !isProcessing,
                    modifier = Modifier.testTag("btn_confirm_permanent_delete")
                ) {
                    Text("স্থায়ীভাবে মুছুন")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { itemForPermanentDelete = null },
                    enabled = !isProcessing
                ) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Dialog: Restore Confirmation
    if (itemForRestoreConfirm != null) {
        val targetItem = itemForRestoreConfirm!!
        AlertDialog(
            onDismissRequest = { itemForRestoreConfirm = null },
            icon = {
                Icon(Icons.Default.RestoreFromTrash, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("রেকর্ড পুনরুদ্ধার নিশ্চিতকরণ", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "'${targetItem.itemName}' (${targetItem.itemType}) রেকর্ডটি রিসাইকেল বিন থেকে পুনরায় মূল তালিকায় ফিরিয়ে আনা হবে।",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "পূর্ববর্তী ডিলিটের কারণ: ${targetItem.deleteReason}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreRecycleBinItem(
                            item = targetItem,
                            onSuccess = {
                                itemForRestoreConfirm = null
                                Toast.makeText(context, "রেকর্ড সফলভাবে পুনরুদ্ধার করা হয়েছে! ✅", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    modifier = Modifier.testTag("btn_confirm_restore")
                ) {
                    Text("পুনরুদ্ধার করুন")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemForRestoreConfirm = null }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Dialog: Safe Delete Product
    if (itemForSafeDeleteProduct != null) {
        val prod = itemForSafeDeleteProduct!!
        AlertDialog(
            onDismissRequest = { itemForSafeDeleteProduct = null },
            icon = {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(36.dp))
            },
            title = { Text("পণ্য নিরাপদ অপসারণ (Soft Delete)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "পণ্য '${prod.name}' সক্রিয় তালিকা থেকে সরিয়ে রিসাইকেল বিনে রাখা হবে।", fontSize = 13.sp)
                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = "🛡️ ৩০ দিনের গ্যারান্টি: এটি ৩০ দিনের মধ্যে যেকোনো সময় পুনরুদ্ধার করতে পারবেন।", fontSize = 12.sp, color = Color(0xFF1B5E20), modifier = Modifier.padding(10.dp))
                    }
                    OutlinedTextField(
                        value = safeDeleteReasonInput,
                        onValueChange = { safeDeleteReasonInput = it },
                        label = { Text("অপসারণের কারণ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.softDeleteProduct(
                            product = prod,
                            reason = safeDeleteReasonInput.trim(),
                            onSuccess = {
                                itemForSafeDeleteProduct = null
                                Toast.makeText(context, "পণ্যটি রিসাইকেল বিনে পাঠানো হয়েছে ✅", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                ) { Text("রিসাইকেল বিনে পাঠান") }
            },
            dismissButton = { OutlinedButton(onClick = { itemForSafeDeleteProduct = null }) { Text("বাতিল") } }
        )
    }

    // Dialog: Safe Delete Company
    if (itemForSafeDeleteCompany != null) {
        val comp = itemForSafeDeleteCompany!!
        AlertDialog(
            onDismissRequest = { itemForSafeDeleteCompany = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = SecondaryTeal, modifier = Modifier.size(36.dp)) },
            title = { Text("কোম্পানি নিরাপদ অপসারণ (Soft Delete)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "কোম্পানি '${comp.name}' সক্রিয় তালিকা থেকে সরিয়ে রিসাইকেল বিনে রাখা হবে।", fontSize = 13.sp)
                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = "🛡️ ৩০ দিনের গ্যারান্টি: এটি ৩০ দিনের মধ্যে যেকোনো সময় পুনরুদ্ধার করতে পারবেন।", fontSize = 12.sp, color = Color(0xFF1B5E20), modifier = Modifier.padding(10.dp))
                    }
                    OutlinedTextField(
                        value = safeDeleteReasonInput,
                        onValueChange = { safeDeleteReasonInput = it },
                        label = { Text("অপসারণের কারণ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.softDeleteCompany(
                            company = comp,
                            reason = safeDeleteReasonInput.trim(),
                            onSuccess = {
                                itemForSafeDeleteCompany = null
                                Toast.makeText(context, "কোম্পানিটি রিসাইকেল বিনে পাঠানো হয়েছে ✅", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal)
                ) { Text("রিসাইকেল বিনে পাঠান") }
            },
            dismissButton = { OutlinedButton(onClick = { itemForSafeDeleteCompany = null }) { Text("বাতিল") } }
        )
    }

    // Dialog: Safe Delete Customer
    if (itemForSafeDeleteCustomer != null) {
        val cust = itemForSafeDeleteCustomer!!
        AlertDialog(
            onDismissRequest = { itemForSafeDeleteCustomer = null },
            icon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(36.dp)) },
            title = { Text("কাস্টমার নিরাপদ অপসারণ (Soft Delete)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "কাস্টমার '${cust.name}' সক্রিয় গ্রাহক তালিকা থেকে সরিয়ে রিসাইকেল বিনে রাখা হবে।", fontSize = 13.sp)
                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = "🛡️ নিরাপদ সংরক্ষণ: কাস্টমারের বকেয়া ৳${cust.currentDue} সাময়িক ড্যাশবোর্ড থেকে বাদ যাবে এবং ৩০ দিনের মধ্যে পুনরুদ্ধার করা যাবে।", fontSize = 12.sp, color = Color(0xFF1B5E20), modifier = Modifier.padding(10.dp))
                    }
                    OutlinedTextField(
                        value = safeDeleteReasonInput,
                        onValueChange = { safeDeleteReasonInput = it },
                        label = { Text("অপসারণের কারণ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.softDeleteCustomer(
                            customer = cust,
                            reason = safeDeleteReasonInput.trim(),
                            onSuccess = {
                                itemForSafeDeleteCustomer = null
                                Toast.makeText(context, "কাস্টমারটি রিসাইকেল বিনে পাঠানো হয়েছে ✅", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) { Text("রিসাইকেল বিনে পাঠান") }
            },
            dismissButton = { OutlinedButton(onClick = { itemForSafeDeleteCustomer = null }) { Text("বাতিল") } }
        )
    }

    // Dialog: Safe Delete Sale
    if (itemForSafeDeleteSale != null) {
        val sale = itemForSafeDeleteSale!!
        AlertDialog(
            onDismissRequest = { itemForSafeDeleteSale = null },
            icon = { Icon(Icons.Default.RemoveShoppingCart, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(36.dp)) },
            title = { Text("বিক্রি ইনভয়েস অপসারণ (Soft Delete)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "ইনভয়েস #${sale.invoiceNumber} (কাস্টমার: ${sale.customerName}, মোট: ৳${sale.totalAmount}) রিসাইকেল বিনে পাঠানো হবে।", fontSize = 13.sp)
                    Surface(color = Color(0xFFFFF8E1), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = "🔄 স্বয়ংক্রিয় সমন্বয়: বিক্রিত পণ্যের স্টক গুদামে ফেরত যোগ হবে এবং কাস্টমারের বাকি স্বয়ংক্রিয়ভাবে হ্রাস পাবে।", fontSize = 12.sp, color = Color(0xFF8D6E00), modifier = Modifier.padding(10.dp))
                    }
                    OutlinedTextField(
                        value = safeDeleteReasonInput,
                        onValueChange = { safeDeleteReasonInput = it },
                        label = { Text("অপসারণের কারণ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.softDeleteSale(
                            sale = sale,
                            reason = safeDeleteReasonInput.trim(),
                            onSuccess = {
                                itemForSafeDeleteSale = null
                                Toast.makeText(context, "বিক্রি রিসাইকেল বিনে পাঠানো ও স্টক সমন্বয় সম্পন্ন ✅", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                ) { Text("রিসাইকেল বিনে পাঠান") }
            },
            dismissButton = { OutlinedButton(onClick = { itemForSafeDeleteSale = null }) { Text("বাতিল") } }
        )
    }

    // Dialog: Safe Delete Receipt
    if (itemForSafeDeleteReceipt != null) {
        val rct = itemForSafeDeleteReceipt!!
        AlertDialog(
            onDismissRequest = { itemForSafeDeleteReceipt = null },
            icon = { Icon(Icons.Default.MoneyOff, contentDescription = null, tint = SecondaryTeal, modifier = Modifier.size(36.dp)) },
            title = { Text("পেমেন্ট রসিদ অপসারণ (Soft Delete)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "রসিদ #${rct.receiptNumber} (কাস্টমার: ${rct.customerName}, পরিমাণ: ৳${rct.amount}) রিসাইকেল বিনে পাঠানো হবে।", fontSize = 13.sp)
                    Surface(color = Color(0xFFE0F2F1), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = "🔄 স্বয়ংক্রিয় সমন্বয়: কাস্টমারের বকেয়ার হিসাব থেকে এই পরিশোধটি সাময়িকভাবে প্রত্যাহার করা হবে।", fontSize = 12.sp, color = SecondaryTeal, modifier = Modifier.padding(10.dp))
                    }
                    OutlinedTextField(
                        value = safeDeleteReasonInput,
                        onValueChange = { safeDeleteReasonInput = it },
                        label = { Text("অপসারণের কারণ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.softDeletePaymentReceipt(
                            receipt = rct,
                            reason = safeDeleteReasonInput.trim(),
                            onSuccess = {
                                itemForSafeDeleteReceipt = null
                                Toast.makeText(context, "পেমেন্ট রসিদ রিসাইকেল বিনে পাঠানো হয়েছে ✅", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal)
                ) { Text("রিসাইকেল বিনে পাঠান") }
            },
            dismissButton = { OutlinedButton(onClick = { itemForSafeDeleteReceipt = null }) { Text("বাতিল") } }
        )
    }

    // Dialog: Safe Delete Expense
    if (itemForSafeDeleteExpense != null) {
        val exp = itemForSafeDeleteExpense!!
        AlertDialog(
            onDismissRequest = { itemForSafeDeleteExpense = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AccentRed, modifier = Modifier.size(36.dp)) },
            title = { Text("খরচ নিরাপদ অপসারণ (Soft Delete)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "খরচ '${exp.description}' (পরিমাণ: ৳${exp.amount}) রিসাইকেল বিনে পাঠানো হবে।", fontSize = 13.sp)
                    OutlinedTextField(
                        value = safeDeleteReasonInput,
                        onValueChange = { safeDeleteReasonInput = it },
                        label = { Text("অপসারণের কারণ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.softDeleteExpense(
                            expense = exp,
                            reason = safeDeleteReasonInput.trim(),
                            onSuccess = {
                                itemForSafeDeleteExpense = null
                                Toast.makeText(context, "খরচটি রিসাইকেল বিনে পাঠানো হয়েছে ✅", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) { Text("রিসাইকেল বিনে পাঠান") }
            },
            dismissButton = { OutlinedButton(onClick = { itemForSafeDeleteExpense = null }) { Text("বাতিল") } }
        )
    }

    // Dialog: Customer Opening Due
    if (customerForOpeningDue != null) {
        val cust = customerForOpeningDue!!
        AlertDialog(
            onDismissRequest = { if (!isProcessing) customerForOpeningDue = null },
            icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(36.dp)) },
            title = { Text("কাস্টমার ওপেনিং বকেয়া সেটআপ", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("গ্রাহক: '${cust.name}' (ফোন: ${cust.phone})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("বর্তমান নিট বকেয়া: ৳${cust.currentDue}", fontSize = 12.sp, color = AccentRed)

                    OutlinedTextField(
                        value = openingAmountInput,
                        onValueChange = { openingAmountInput = it },
                        label = { Text("প্রারম্ভিক ওপেনিং বকেয়া (৳)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adminPinInput,
                        onValueChange = { adminPinInput = it; dialogError = "" },
                        label = { Text("নিশ্চিত করতে এডমিন পিন দিন") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (dialogError.isNotBlank()) {
                        Text(text = dialogError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = openingAmountInput.toDoubleOrNull()
                        if (amount == null || amount < 0) {
                            dialogError = "সঠিক টাকার পরিমাণ লিখুন!"
                            return@Button
                        }
                        if (adminPinInput.isBlank()) {
                            dialogError = "এডমিন পিন দিন!"
                            return@Button
                        }
                        isProcessing = true
                        viewModel.setCustomerOpeningBalance(
                            customerId = cust.id,
                            openingDue = amount,
                            pin = adminPinInput.trim(),
                            onSuccess = {
                                isProcessing = false
                                customerForOpeningDue = null
                                Toast.makeText(context, "ওপেনিং বকেয়া আপডেট সম্পন্ন! ✅", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                dialogError = err
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                    enabled = !isProcessing
                ) { Text("সংরক্ষণ করুন") }
            },
            dismissButton = { OutlinedButton(onClick = { customerForOpeningDue = null }, enabled = !isProcessing) { Text("বাতিল") } }
        )
    }

    // Dialog: Product Opening Stock
    if (productForOpeningStock != null) {
        val prod = productForOpeningStock!!
        AlertDialog(
            onDismissRequest = { if (!isProcessing) productForOpeningStock = null },
            icon = { Icon(Icons.Default.Inventory2, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(36.dp)) },
            title = { Text("পণ্য প্রারম্ভিক স্টক সেটআপ", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("পণ্য: '${prod.name}' (কোড: ${prod.sku})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("বর্তমান স্টক: ${prod.currentStock} ${prod.unit}", fontSize = 12.sp, color = PrimaryBlue)

                    OutlinedTextField(
                        value = openingAmountInput,
                        onValueChange = { openingAmountInput = it },
                        label = { Text("বাস্তব প্রারম্ভিক স্টক সংখ্যা (${prod.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adminPinInput,
                        onValueChange = { adminPinInput = it; dialogError = "" },
                        label = { Text("নিশ্চিত করতে এডমিন পিন দিন") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (dialogError.isNotBlank()) {
                        Text(text = dialogError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val stock = openingAmountInput.toDoubleOrNull()
                        if (stock == null || stock < 0) {
                            dialogError = "সঠিক স্টক সংখ্যা লিখুন!"
                            return@Button
                        }
                        if (adminPinInput.isBlank()) {
                            dialogError = "এডমিন পিন দিন!"
                            return@Button
                        }
                        isProcessing = true
                        viewModel.setProductOpeningStock(
                            productId = prod.id,
                            openingStock = stock,
                            pin = adminPinInput.trim(),
                            onSuccess = {
                                isProcessing = false
                                productForOpeningStock = null
                                Toast.makeText(context, "প্রারম্ভিক স্টক আপডেট সম্পন্ন! ✅", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                isProcessing = false
                                dialogError = err
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    enabled = !isProcessing
                ) { Text("সংরক্ষণ করুন") }
            },
            dismissButton = { OutlinedButton(onClick = { productForOpeningStock = null }, enabled = !isProcessing) { Text("বাতিল") } }
        )
    }

    if (showDetectTestDueDialog) {
        DetectAndCleanTestDuesDialog(
            detectedTestDueCustomers = detectedTestDueCustomers,
            isDetecting = isDetectingTestDues,
            totalDashboardDue = adminDashboardSummary.totalDue,
            onDismiss = { showDetectTestDueDialog = false },
            onRefresh = { viewModel.scanTestDueCustomers() },
            onSoftDeleteCustomer = { cust, reason ->
                viewModel.softDeleteCustomer(
                    customer = cust,
                    reason = reason,
                    onSuccess = {
                        Toast.makeText(context, "'${cust.name}' রিসাইকেল বিনে পাঠানো হয়েছে ✅", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onSoftDeleteMultipleCustomers = { custs, reason ->
                viewModel.softDeleteMultipleCustomers(
                    customers = custs,
                    reason = reason,
                    onSuccess = { count, totalDue ->
                        Toast.makeText(
                            context,
                            "$count জন টেস্ট কাস্টমার (মোট ৳${BengaliFormatter.formatCurrency(totalDue)} বকেয়া) রিসাইকেল বিনে পাঠানো হয়েছে ✅",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }
}

// ==============================================================
// 1. Recycle Bin Tab
// ==============================================================
@Composable
fun RecycleBinTabContent(
    items: List<RecycleBinItem>,
    onRestore: (RecycleBinItem) -> Unit,
    onPermanentDelete: (RecycleBinItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterTable by remember { mutableStateOf("ALL") } // "ALL", "products", "customers", "sales", "payments", "companies", "expenses"

    val filtered = remember(items, searchQuery, filterTable) {
        items.filter { item ->
            val matchFilter = when (filterTable) {
                "products" -> item.originalTable == "products"
                "customers" -> item.originalTable == "customers"
                "sales" -> item.originalTable == "sales"
                "payments" -> item.originalTable == "payments"
                "companies" -> item.originalTable == "companies"
                "expenses" -> item.originalTable == "expenses"
                else -> true
            }
            val matchQuery = searchQuery.isBlank() ||
                    item.itemName.contains(searchQuery, ignoreCase = true) ||
                    item.itemDetails.contains(searchQuery, ignoreCase = true) ||
                    item.deleteReason.contains(searchQuery, ignoreCase = true)
            matchFilter && matchQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("recycle_bin_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Info Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFE8F5E9),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("নিরাপদ ডিলিট পলিসি সক্রিয়", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "পণ্য, কাস্টমার, বিক্রি, পেমেন্ট বা খরচ ডিলিট করা হলে ৩০ দিন পর্যন্ত রিসাইকেল বিনে থাকে। আপনি যেকোনো সময় 'পুনরুদ্ধার' করে পূর্বাবস্থায় ফেরাতে পারবেন।",
                            fontSize = 12.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        }

        // Search & Filter Row
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("রিসাইকেল বিনে খুঁজুন (নাম, ইনভয়েস, কারণ)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "মুছুন")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterTable == "ALL",
                    onClick = { filterTable = "ALL" },
                    label = { Text("সকল (${items.size})") }
                )
                FilterChip(
                    selected = filterTable == "products",
                    onClick = { filterTable = "products" },
                    label = { Text("পণ্য (${items.count { it.originalTable == "products" }})") }
                )
                FilterChip(
                    selected = filterTable == "customers",
                    onClick = { filterTable = "customers" },
                    label = { Text("কাস্টমার (${items.count { it.originalTable == "customers" }})") }
                )
                FilterChip(
                    selected = filterTable == "sales",
                    onClick = { filterTable = "sales" },
                    label = { Text("বিক্রি (${items.count { it.originalTable == "sales" }})") }
                )
                FilterChip(
                    selected = filterTable == "payments",
                    onClick = { filterTable = "payments" },
                    label = { Text("পেমেন্ট (${items.count { it.originalTable == "payments" }})") }
                )
                FilterChip(
                    selected = filterTable == "companies",
                    onClick = { filterTable = "companies" },
                    label = { Text("কোম্পানি (${items.count { it.originalTable == "companies" }})") }
                )
                FilterChip(
                    selected = filterTable == "expenses",
                    onClick = { filterTable = "expenses" },
                    label = { Text("খরচ (${items.count { it.originalTable == "expenses" }})") }
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (items.isEmpty()) "রিসাইকেল বিন সম্পূর্ণ খালি!" else "খোঁজার ফলাফলে কোনো রেকর্ড পাওয়া যায়নি।",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "কোনো রেকর্ড মুছে ফেলা হলে এখানে ৩০ দিন জমা থাকবে।",
                            fontSize = 12.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                RecycleBinItemCard(
                    item = item,
                    onRestore = { onRestore(item) },
                    onPermanentDelete = { onPermanentDelete(item) }
                )
            }
        }
    }
}

@Composable
fun RecycleBinItemCard(
    item: RecycleBinItem,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    val remainingMillis = remember(item.expiresAt) { item.expiresAt - System.currentTimeMillis() }
    val remainingDays = remember(remainingMillis) {
        if (remainingMillis > 0) TimeUnit.MILLISECONDS.toDays(remainingMillis) else 0L
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recycle_bin_item_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Type Badge + Days Remaining Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (item.originalTable) {
                        "products" -> PrimaryBlue.copy(alpha = 0.12f)
                        "customers" -> AccentGreen.copy(alpha = 0.12f)
                        "sales" -> Color(0xFFE65100).copy(alpha = 0.12f)
                        "payments" -> SecondaryTeal.copy(alpha = 0.12f)
                        "companies" -> PrimaryNavy.copy(alpha = 0.12f)
                        "expenses" -> AccentRed.copy(alpha = 0.12f)
                        else -> PrimaryNavy.copy(alpha = 0.12f)
                    }
                ) {
                    Text(
                        text = item.itemType,
                        color = when (item.originalTable) {
                            "products" -> PrimaryBlue
                            "customers" -> AccentGreen
                            "sales" -> Color(0xFFE65100)
                            "payments" -> SecondaryTeal
                            "companies" -> PrimaryNavy
                            "expenses" -> AccentRed
                            else -> PrimaryNavy
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (remainingDays <= 5) Color(0xFFFFEBEE) else Color(0xFFFFF8E1)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (remainingDays <= 5) AccentRed else AccentAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (remainingDays > 0) "$remainingDays দিন বাকি" else "আজই মেয়াদোত্তীর্ণ",
                            color = if (remainingDays <= 5) AccentRed else Color(0xFF8D6E00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Title & Details
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.itemName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = PrimaryNavy,
                    modifier = Modifier.weight(1f)
                )
                if (item.isTestData) {
                    Surface(
                        color = Color(0xFFF3E5F5),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (item.testTag == "GEMINI_AI") "🤖 AI টেস্ট" else "🧪 টেস্ট",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7B1FA2),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = item.itemDetails,
                fontSize = 12.sp,
                color = TextSecondaryDark
            )

            HorizontalDivider(color = CardBorder)

            // Metadata: Who deleted, when, and reason
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "ডিলিট করেছেন: ${BengaliFormatter.formatAdminDisplayName(item.deletedByAdminName)} • ${BengaliFormatter.formatDate(item.deletedAt)}",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                if (item.deleteReason.isNotBlank()) {
                    Text(
                        text = "কারণ: ${item.deleteReason}",
                        fontSize = 11.sp,
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons: Restore & Permanent Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.2f).testTag("btn_restore_${item.id}"),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("পুনরুদ্ধার করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onPermanentDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("btn_perm_delete_${item.id}"),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("স্থায়ী ডিলিট", fontSize = 12.sp)
                }
            }
        }
    }
}

// ==============================================================
// 2. Testing Data Management Tab
// ==============================================================
@Composable
fun TestingDataTabContent(
    testProducts: List<Product>,
    testCompanies: List<Company>,
    testCustomers: List<Customer>,
    testSales: List<Sale>,
    testReceipts: List<PaymentReceipt>,
    testExpenses: List<Expense>,
    onOpenDetectTestDues: () -> Unit,
    onCreateSampleData: () -> Unit,
    onSafeDeleteProduct: (Product) -> Unit,
    onSafeDeleteCompany: (Company) -> Unit,
    onSafeDeleteCustomer: (Customer) -> Unit,
    onSafeDeleteSale: (Sale) -> Unit,
    onSafeDeleteReceipt: (PaymentReceipt) -> Unit,
    onSafeDeleteExpense: (Expense) -> Unit,
    onUntagProduct: (Product) -> Unit,
    onUntagCompany: (Company) -> Unit,
    onUntagCustomer: (Customer) -> Unit,
    onUntagSale: (Sale) -> Unit,
    onUntagReceipt: (PaymentReceipt) -> Unit,
    onUntagExpense: (Expense) -> Unit
) {
    var subTab by remember { mutableStateOf("ALL") } // "ALL", "PRODUCTS", "CUSTOMERS", "SALES", "PAYMENTS", "COMPANIES", "EXPENSES"

    val totalCount = testProducts.size + testCompanies.size + testCustomers.size + testSales.size + testReceipts.size + testExpenses.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("test_data_tab_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Safe Testing Policy Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF3E5F5),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFF7B1FA2), modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "টেস্টিং ও ডেমো ডেটা ব্যবস্থাপনা",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF4A148C)
                        )
                    }
                    Text(
                        text = "অ্যাপ পরীক্ষার সময় তৈরি হওয়া টেস্ট পণ্য, কাস্টমার, বিক্রি, বাকি ও রসিদ এখানে তালিকাভুক্ত থাকে। এগুলো নিরাপদে রিসাইকেল বিনে পাঠিয়ে মূল ড্যাশবোর্ড ও ব্যবসার হিসাব নির্ভুলভাবে শুরু করতে পারেন।",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                }
            }
        }

        // Summary Metric Cards + Create Test Data Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("টেস্ট পণ্য", fontSize = 11.sp, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${testProducts.size} টি", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("টেস্ট কাস্টমার", fontSize = 11.sp, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${testCustomers.size} জন", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("টেস্ট বিক্রি", fontSize = 11.sp, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${testSales.size} টি", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                }
            }
        }

        // Primary Action: Detect and Clean Old Test Customer Dues via Existing Recycle Bin
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFF3E0),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.CleaningServices,
                                        contentDescription = null,
                                        tint = Color(0xFFE65100),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "পুরোনো টেস্ট বকেয়া শনাক্ত ও পরিষ্কার করুন",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFBF360C)
                            )
                        }
                    }

                    Text(
                        text = "ড্যাশবোর্ডের মোট কাস্টমার বকেয়ার পেছনের পুরোনো টেস্ট কাস্টমার ও প্রাথমিক নমুনা ডেটাসেট লেনদেন নিরাপদে শনাক্ত করে বিদ্যমান রিসাইকেল বিনে স্থানান্তর করুন।",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )

                    Button(
                        onClick = onOpenDetectTestDues,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_open_detect_test_dues")
                    ) {
                        Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "পুরোনো টেস্ট বকেয়া শনাক্ত ও পরিষ্কার করুন",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Action: Generate Sample Gemini & Demo Test Data
        item {
            Button(
                onClick = onCreateSampleData,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                modifier = Modifier.fillMaxWidth().testTag("btn_create_sample_test_data")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("পরীক্ষামূলক টেস্ট ডেটা সেট তৈরি করুন (Sample Test Data)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = subTab == "ALL",
                    onClick = { subTab = "ALL" },
                    label = { Text("সব ($totalCount)") }
                )
                FilterChip(
                    selected = subTab == "PRODUCTS",
                    onClick = { subTab = "PRODUCTS" },
                    label = { Text("পণ্য (${testProducts.size})") }
                )
                FilterChip(
                    selected = subTab == "CUSTOMERS",
                    onClick = { subTab = "CUSTOMERS" },
                    label = { Text("কাস্টমার (${testCustomers.size})") }
                )
                FilterChip(
                    selected = subTab == "SALES",
                    onClick = { subTab = "SALES" },
                    label = { Text("বিক্রি (${testSales.size})") }
                )
                FilterChip(
                    selected = subTab == "PAYMENTS",
                    onClick = { subTab = "PAYMENTS" },
                    label = { Text("পেমেন্ট (${testReceipts.size})") }
                )
                FilterChip(
                    selected = subTab == "COMPANIES",
                    onClick = { subTab = "COMPANIES" },
                    label = { Text("কোম্পানি (${testCompanies.size})") }
                )
                FilterChip(
                    selected = subTab == "EXPENSES",
                    onClick = { subTab = "EXPENSES" },
                    label = { Text("খরচ (${testExpenses.size})") }
                )
            }
        }

        // 1. Products Section
        if (subTab == "ALL" || subTab == "PRODUCTS") {
            item {
                Text(
                    text = "পরীক্ষামূলক পণ্য তালিকা (${testProducts.size}টি)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = PrimaryNavy
                )
            }
            if (testProducts.isEmpty()) {
                item { Text("কোনো টেস্ট পণ্য চিহ্নিত নেই।", fontSize = 12.sp, color = TextMuted) }
            } else {
                items(testProducts, key = { it.id }) { prod ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                                    Text("কোড: ${prod.sku} • স্টক: ${prod.currentStock} ${prod.unit} • মূল্য: ৳${prod.sellingPrice}", fontSize = 11.sp, color = TextSecondaryDark)
                                }
                                Surface(color = Color(0xFFF3E5F5), shape = RoundedCornerShape(4.dp)) {
                                    Text(text = if (prod.imageSource == "AI_GENERATED") "🤖 Gemini AI" else "🧪 টেস্ট", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B1FA2), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onUntagProduct(prod) }) { Text("ট্যাগ সরান (বাস্তব করুন)", fontSize = 11.sp) }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { onSafeDeleteProduct(prod) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("রিসাইকেল বিনে পাঠান", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Customers Section
        if (subTab == "ALL" || subTab == "CUSTOMERS") {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "পরীক্ষামূলক কাস্টমার তালিকা (${testCustomers.size}জন)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = AccentGreen
                )
            }
            if (testCustomers.isEmpty()) {
                item { Text("কোনো টেস্ট কাস্টমার চিহ্নিত নেই।", fontSize = 12.sp, color = TextMuted) }
            } else {
                items(testCustomers, key = { it.id }) { cust ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                                    Text("ফোন: ${cust.phone} • বকেয়া: ৳${cust.currentDue} • মোট বিল: ৳${cust.totalBilled}", fontSize = 11.sp, color = TextSecondaryDark)
                                }
                                Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(4.dp)) {
                                    Text(text = "🧪 টেস্ট গ্রাহক", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGreen, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onUntagCustomer(cust) }) { Text("ট্যাগ সরান (বাস্তব করুন)", fontSize = 11.sp) }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { onSafeDeleteCustomer(cust) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("রিসাইকেল বিনে পাঠান", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Sales Section
        if (subTab == "ALL" || subTab == "SALES") {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "পরীক্ষামূলক বিক্রি ইনভয়েস (${testSales.size}টি)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFE65100)
                )
            }
            if (testSales.isEmpty()) {
                item { Text("কোনো টেস্ট বিক্রি চিহ্নিত নেই।", fontSize = 12.sp, color = TextMuted) }
            } else {
                items(testSales, key = { it.invoiceNumber }) { sale ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("ইনভয়েস #${sale.invoiceNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                                    Text("গ্রাহক: ${sale.customerName} • মোট বিল: ৳${sale.totalAmount} (জমা: ৳${sale.paidAmount}, বাকি: ৳${sale.dueAmount})", fontSize = 11.sp, color = TextSecondaryDark)
                                }
                                Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(4.dp)) {
                                    Text(text = "🧪 টেস্ট বিক্রি", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onUntagSale(sale) }) { Text("ট্যাগ সরান (বাস্তব করুন)", fontSize = 11.sp) }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { onSafeDeleteSale(sale) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("রিসাইকেল বিনে পাঠান", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Payments Section
        if (subTab == "ALL" || subTab == "PAYMENTS") {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "পরীক্ষামূলক পেমেন্ট রসিদ (${testReceipts.size}টি)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = SecondaryTeal
                )
            }
            if (testReceipts.isEmpty()) {
                item { Text("কোনো টেস্ট পেমেন্ট চিহ্নিত নেই।", fontSize = 12.sp, color = TextMuted) }
            } else {
                items(testReceipts, key = { it.receiptNumber }) { rct ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("রসিদ #${rct.receiptNumber}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                                    Text("গ্রাহক: ${rct.customerName} • আদায়: ৳${rct.amount} • মাধ্যম: ${rct.paymentMethod}", fontSize = 11.sp, color = TextSecondaryDark)
                                }
                                Surface(color = Color(0xFFE0F2F1), shape = RoundedCornerShape(4.dp)) {
                                    Text(text = "🧪 টেস্ট রসিদ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SecondaryTeal, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onUntagReceipt(rct) }) { Text("ট্যাগ সরান (বাস্তব করুন)", fontSize = 11.sp) }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { onSafeDeleteReceipt(rct) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("রিসাইকেল বিনে পাঠান", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Companies Section
        if (subTab == "ALL" || subTab == "COMPANIES") {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "পরীক্ষামূলক কোম্পানি / সাপ্লায়ার (${testCompanies.size}টি)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = SecondaryTeal
                )
            }
            if (testCompanies.isEmpty()) {
                item { Text("কোনো টেস্ট কোম্পানি চিহ্নিত নেই।", fontSize = 12.sp, color = TextMuted) }
            } else {
                items(testCompanies, key = { it.id }) { comp ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(comp.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                                    Text("ফোন: ${comp.phone} • বকেয়া: ৳${comp.currentDue}", fontSize = 11.sp, color = TextSecondaryDark)
                                }
                                Surface(color = Color(0xFFE0F2F1), shape = RoundedCornerShape(4.dp)) {
                                    Text(text = "🧪 টেস্ট কোম্পানি", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SecondaryTeal, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onUntagCompany(comp) }) { Text("ট্যাগ সরান (বাস্তব করুন)", fontSize = 11.sp) }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { onSafeDeleteCompany(comp) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("রিসাইকেল বিনে পাঠান", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Expenses Section
        if (subTab == "ALL" || subTab == "EXPENSES") {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "পরীক্ষামূলক খরচ (${testExpenses.size}টি)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = AccentRed
                )
            }
            if (testExpenses.isEmpty()) {
                item { Text("কোনো টেস্ট খরচ চিহ্নিত নেই।", fontSize = 12.sp, color = TextMuted) }
            } else {
                items(testExpenses, key = { it.id }) { exp ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exp.description, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                                    Text("পরিমাণ: ৳${exp.amount} • ক্যাটাগরি: ${exp.category}", fontSize = 11.sp, color = TextSecondaryDark)
                                }
                                Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(4.dp)) {
                                    Text(text = "🧪 টেস্ট খরচ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentRed, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onUntagExpense(exp) }) { Text("ট্যাগ সরান (বাস্তব করুন)", fontSize = 11.sp) }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { onSafeDeleteExpense(exp) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("রিসাইকেল বিনে পাঠান", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================
// 3. Opening Balance & Inventory Setup Tab
// ==============================================================
@Composable
fun OpeningBalanceSetupTabContent(
    customers: List<Customer>,
    products: List<Product>,
    onEditCustomerOpeningDue: (Customer) -> Unit,
    onEditProductOpeningStock: (Product) -> Unit
) {
    var mode by remember { mutableStateOf("CUSTOMERS") } // "CUSTOMERS" or "PRODUCTS"
    var searchQuery by remember { mutableStateOf("") }

    val filteredCustomers = remember(customers, searchQuery) {
        customers.filter {
            searchQuery.isBlank() ||
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery)
        }
    }

    val filteredProducts = remember(products, searchQuery) {
        products.filter {
            searchQuery.isBlank() ||
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.sku.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("opening_balance_setup_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Guidance Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE1F5FE),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "বাস্তব ব্যবসা শুরু: ওপেনিং ব্যালেন্স সেটআপ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = PrimaryNavy
                        )
                    }
                    Text(
                        text = "টেস্ট ডেটা ডিলিট বা রিসাইকেল বিনে পাঠানোর পর আপনার প্রকৃত ব্যবসার কাস্টমারদের পূর্বের বকেয়া এবং দোকানের বিদ্যমান পণ্যের প্রারম্ভিক স্টক এখানে সঠিকভাবে নির্ধারণ করুন।",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                }
            }
        }

        // Toggle Buttons
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { mode = "CUSTOMERS"; searchQuery = "" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == "CUSTOMERS") PrimaryNavy else Color.White,
                        contentColor = if (mode == "CUSTOMERS") Color.White else PrimaryNavy
                    ),
                    border = if (mode == "CUSTOMERS") null else androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("কাস্টমার বকেয়া (${customers.size})", fontSize = 12.sp)
                }

                Button(
                    onClick = { mode = "PRODUCTS"; searchQuery = "" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mode == "PRODUCTS") PrimaryBlue else Color.White,
                        contentColor = if (mode == "PRODUCTS") Color.White else PrimaryNavy
                    ),
                    border = if (mode == "PRODUCTS") null else androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("পণ্যের স্টক (${products.size})", fontSize = 12.sp)
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(if (mode == "CUSTOMERS") "গ্রাহকের নাম বা ফোন খুঁজুন..." else "পণ্যের নাম বা কোড খুঁজুন...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (mode == "CUSTOMERS") {
            if (filteredCustomers.isEmpty()) {
                item {
                    Text("কোনো সক্রিয় কাস্টমার পাওয়া যায়নি।", color = TextMuted, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(filteredCustomers, key = { it.id }) { cust ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                                Text("ফোন: ${cust.phone} • ঠিকানা: ${cust.address.ifBlank { "ঠিকানা নেই" }}", fontSize = 11.sp, color = TextSecondaryDark)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("ওপেনিং বকেয়া: ৳${cust.openingDue}", fontSize = 11.sp, color = PrimaryNavy, fontWeight = FontWeight.SemiBold)
                                    Text("মোট নিট বকেয়া: ৳${cust.currentDue}", fontSize = 11.sp, color = if (cust.currentDue > 0) AccentRed else AccentGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = { onEditCustomerOpeningDue(cust) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("সেট করুন", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        } else {
            if (filteredProducts.isEmpty()) {
                item {
                    Text("কোনো সক্রিয় পণ্য পাওয়া যায়নি।", color = TextMuted, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(filteredProducts, key = { it.id }) { prod ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimaryDark)
                                Text("কোড: ${prod.sku} • ক্যাটাগরি: ${prod.category}", fontSize = 11.sp, color = TextSecondaryDark)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("ক্রয়: ৳${prod.purchasePrice}", fontSize = 11.sp, color = TextSecondaryDark)
                                    Text("বিক্রয়: ৳${prod.sellingPrice}", fontSize = 11.sp, color = TextSecondaryDark)
                                    Text("বর্তমান স্টক: ${prod.currentStock} ${prod.unit}", fontSize = 11.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = { onEditProductOpeningStock(prod) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("সেট করুন", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==============================================================
// 4. Audit Logs (ডেটা পরিবর্তনের ইতিহাস) Tab
// ==============================================================
@Composable
fun AuditLogsTabContent(logs: List<AuditLog>) {
    var searchQuery by remember { mutableStateOf("") }
    var filterAction by remember { mutableStateOf("ALL") }

    val filtered = remember(logs, searchQuery, filterAction) {
        logs.filter { log ->
            val matchAction = when (filterAction) {
                "DELETES" -> log.action.contains("মুছে") || log.action.contains("স্থানান্তর")
                "RESTORES" -> log.action.contains("পুনরুদ্ধার")
                "TEST" -> log.action.contains("টেস্ট")
                "OPENING" -> log.action.contains("ওপেনিং") || log.action.contains("প্রারম্ভিক")
                "SECURITY" -> log.action.contains("লগইন") || log.action.contains("পিন") || log.action.contains("অনুমতি")
                else -> true
            }
            val matchQuery = searchQuery.isBlank() ||
                    log.action.contains(searchQuery, ignoreCase = true) ||
                    log.details.contains(searchQuery, ignoreCase = true) ||
                    log.userName.contains(searchQuery, ignoreCase = true)
            matchAction && matchQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("audit_logs_timeline"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("ইতিহাস খুঁজুন (অ্যাকশন, ব্যবহারকারী, কারণ)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterAction == "ALL",
                    onClick = { filterAction = "ALL" },
                    label = { Text("সকল পরিবর্তন (${logs.size})") }
                )
                FilterChip(
                    selected = filterAction == "DELETES",
                    onClick = { filterAction = "DELETES" },
                    label = { Text("ডিলিট ও স্থানান্তর") }
                )
                FilterChip(
                    selected = filterAction == "RESTORES",
                    onClick = { filterAction = "RESTORES" },
                    label = { Text("পুনরুদ্ধার") }
                )
                FilterChip(
                    selected = filterAction == "TEST",
                    onClick = { filterAction = "TEST" },
                    label = { Text("টেস্ট ডেটা") }
                )
                FilterChip(
                    selected = filterAction == "OPENING",
                    onClick = { filterAction = "OPENING" },
                    label = { Text("ওপেনিং সেটআপ") }
                )
                FilterChip(
                    selected = filterAction == "SECURITY",
                    onClick = { filterAction = "SECURITY" },
                    label = { Text("নিরাপত্তা ও পিন") }
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("কোনো লগ রেকর্ড পাওয়া যায়নি।", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            items(filtered, key = { it.id }) { log ->
                val isDelete = log.action.contains("মুছে") || log.action.contains("স্থানান্তর")
                val isRestore = log.action.contains("পুনরুদ্ধার")
                val isTest = log.action.contains("টেস্ট")
                val isOpening = log.action.contains("ওপেনিং") || log.action.contains("প্রারম্ভিক")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isDelete -> Color(0xFFFFEBEE).copy(alpha = 0.6f)
                            isRestore -> Color(0xFFE8F5E9).copy(alpha = 0.6f)
                            isTest -> Color(0xFFF3E5F5).copy(alpha = 0.6f)
                            isOpening -> Color(0xFFE1F5FE).copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = when {
                                        isDelete -> Icons.Default.DeleteSweep
                                        isRestore -> Icons.Default.RestoreFromTrash
                                        isTest -> Icons.Default.Science
                                        isOpening -> Icons.Default.Storefront
                                        else -> Icons.Default.History
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isDelete -> AccentRed
                                        isRestore -> AccentGreen
                                        isTest -> Color(0xFF7B1FA2)
                                        isOpening -> PrimaryBlue
                                        else -> PrimaryNavy
                                    },
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = log.action,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = when {
                                        isDelete -> AccentRed
                                        isRestore -> Color(0xFF1B5E20)
                                        isTest -> Color(0xFF4A148C)
                                        isOpening -> PrimaryNavy
                                        else -> PrimaryNavy
                                    }
                                )
                            }
                            Text(
                                text = BengaliFormatter.formatDate(log.timestamp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Text(
                            text = log.details,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val displayUser = if (log.userId == "admin_1" || log.userName.contains("আতাউর")) {
                            BengaliFormatter.formatAdminDisplayName(log.userName)
                        } else {
                            log.userName
                        }
                        Text(
                            text = "পরিচালনাকারী: $displayUser (${log.userId})",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

// ==============================================================
// 5. Detect and Clean Old Test Customer Dues Modal Dialog
// ==============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectAndCleanTestDuesDialog(
    detectedTestDueCustomers: List<DetectedTestDueCustomer>,
    isDetecting: Boolean,
    totalDashboardDue: Double,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onSoftDeleteCustomer: (Customer, String) -> Unit,
    onSoftDeleteMultipleCustomers: (List<Customer>, String) -> Unit
) {
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "CONFIRMED_TEST", "SAMPLE_SEED", "MANUAL"
    var showBatchConfirmDialog by remember { mutableStateOf(false) }
    var singleCustomerToRecycle by remember { mutableStateOf<Customer?>(null) }
    var batchReasonInput by remember { mutableStateOf("পুরোনো টেস্ট কাস্টমার বকেয়া পরিচ্ছন্নকরণ") }
    var singleReasonInput by remember { mutableStateOf("টেস্ট কাস্টমার রিসাইকেল বিনে স্থানান্তর") }

    val confirmedTestList = remember(detectedTestDueCustomers) {
        detectedTestDueCustomers.filter { it.category == DueCustomerCategory.CONFIRMED_TEST }
    }
    val sampleSeedList = remember(detectedTestDueCustomers) {
        detectedTestDueCustomers.filter { it.category == DueCustomerCategory.SAMPLE_SEED_DATA }
    }
    val manualReviewList = remember(detectedTestDueCustomers) {
        detectedTestDueCustomers.filter { it.category == DueCustomerCategory.NEEDS_MANUAL_REVIEW }
    }

    val testAndSeedDueTotal = remember(confirmedTestList, sampleSeedList) {
        confirmedTestList.sumOf { it.customer.currentDue } + sampleSeedList.sumOf { it.customer.currentDue }
    }
    val manualDueTotal = remember(manualReviewList) {
        manualReviewList.sumOf { it.customer.currentDue }
    }

    val displayList = remember(detectedTestDueCustomers, selectedFilter) {
        when (selectedFilter) {
            "CONFIRMED_TEST" -> confirmedTestList
            "SAMPLE_SEED" -> sampleSeedList
            "MANUAL" -> manualReviewList
            else -> detectedTestDueCustomers
        }
    }

    val selectedCustomers = remember(detectedTestDueCustomers, selectedIds) {
        detectedTestDueCustomers.filter { it.customer.id in selectedIds }.map { it.customer }
    }
    val selectedDuesTotal = remember(selectedCustomers) {
        selectedCustomers.sumOf { it.currentDue }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "পুরোনো টেস্ট বকেয়া শনাক্ত ও পরিষ্কারকরণ",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "রিসাইকেল বিনে স্থানান্তর • ৩০ দিনের রিকভারি • ড্যাশবোর্ড আপডেট",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_detect_test_dues")) {
                            Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh, modifier = Modifier.testTag("btn_refresh_detect_dues")) {
                            Icon(Icons.Default.Refresh, contentDescription = "পুনরায় স্ক্যান", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryNavy)
                )
            },
            bottomBar = {
                if (selectedCustomers.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "নির্বাচিত: ${selectedCustomers.size} জন",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "বকেয়া হ্রাস: ৳${BengaliFormatter.formatCurrency(selectedDuesTotal)}",
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRed,
                                    fontSize = 14.sp
                                )
                            }
                            Button(
                                onClick = {
                                    batchReasonInput = "পুরোনো টেস্ট কাস্টমার বকেয়া পরিচ্ছন্নকরণ"
                                    showBatchConfirmDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_batch_recycle_confirm")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("নির্বাচিত ${selectedCustomers.size} টি রেকর্ড রিসাইকেল বিনে পাঠান", fontWeight = FontWeight.Bold)
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
                    .testTag("detect_test_dues_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Analysis Summary Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Insights, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ড্যাশবোর্ড বকেয়ার উৎস বিশ্লেষণ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = PrimaryNavy
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("ড্যাশবোর্ড মোট বকেয়া", fontSize = 10.sp, color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold)
                                        Text("৳${BengaliFormatter.formatCurrency(totalDashboardDue)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("টেস্ট ও নমুনা বকেয়া", fontSize = 10.sp, color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold)
                                        Text("৳${BengaliFormatter.formatCurrency(testAndSeedDueTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("আসল কাস্টমার বকেয়া", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                        Text("৳${BengaliFormatter.formatCurrency(manualDueTotal)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                }
                            }

                            Text(
                                text = "🛡️ রিসাইকেল বিন সুরক্ষা: চিহ্নিত রেকর্ড রিসাইকেল বিনে পাঠালে ড্যাশবোর্ডের মোট কাস্টমার বকেয়া থেকে বাদ পড়বে। এটি কোনো স্থায়ী ধ্বংস নয় — ৩০ দিনের মধ্যে যেকোনো সময় সম্পূর্ণ তথ্য ও পূর্বের ব্যালেন্সসহ Restore করা যাবে।",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Selection Helper Action Row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                // Safe auto-select: select ONLY CONFIRMED_TEST and SAMPLE_SEED_DATA. Strictly omit NEEDS_MANUAL_REVIEW!
                                val safeIds = detectedTestDueCustomers
                                    .filter { it.category != DueCustomerCategory.NEEDS_MANUAL_REVIEW }
                                    .map { it.customer.id }
                                    .toSet()
                                selectedIds = safeIds
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("btn_select_all_test_seed")
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সকল টেস্ট ও নমুনা সিলেক্ট করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { selectedIds = emptySet() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_clear_selection")
                        ) {
                            Icon(Icons.Default.Deselect, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("সব বাতিল", fontSize = 11.sp)
                        }
                    }
                }

                // Filter Chips
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" },
                            label = { Text("সকল (${detectedTestDueCustomers.size})", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedFilter == "CONFIRMED_TEST",
                            onClick = { selectedFilter = "CONFIRMED_TEST" },
                            label = { Text("নিশ্চিত টেস্ট (${confirmedTestList.size})", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedFilter == "SAMPLE_SEED",
                            onClick = { selectedFilter = "SAMPLE_SEED" },
                            label = { Text("প্রারম্ভিক নমুনা (${sampleSeedList.size})", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedFilter == "MANUAL",
                            onClick = { selectedFilter = "MANUAL" },
                            label = { Text("ম্যানুয়াল যাচাই (${manualReviewList.size})", fontSize = 11.sp) }
                        )
                    }
                }

                // Empty State
                if (displayList.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp))
                                Text("কোনো বকেয়াযুক্ত টেস্ট কাস্টমার পাওয়া যায়নি!", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20), fontSize = 15.sp)
                                Text("আপনার ড্যাশবোর্ডের মোট কাস্টমার বকেয়া সম্পূর্ণ পরিষ্কার এবং শুধুমাত্র প্রকৃত ব্যবসা রেকর্ডের সাথে সামঞ্জস্যপূর্ণ।", fontSize = 12.sp, color = Color(0xFF2E7D32), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }

                // Customer Item Cards
                items(displayList, key = { it.customer.id }) { item ->
                    val cust = item.customer
                    val isChecked = cust.id in selectedIds

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (item.category) {
                                DueCustomerCategory.CONFIRMED_TEST -> Color(0xFFFAFAFA)
                                DueCustomerCategory.SAMPLE_SEED_DATA -> Color(0xFFFFFDF8)
                                DueCustomerCategory.NEEDS_MANUAL_REVIEW -> Color(0xFFFFFFFF)
                            }
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isChecked) 2.dp else 1.dp,
                            color = if (isChecked) Color(0xFFE65100) else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_test_due_cust_${cust.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) selectedIds + cust.id else selectedIds - cust.id
                                    },
                                    modifier = Modifier.testTag("chk_cust_${cust.id}")
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = cust.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        // Category Badge
                                        when (item.category) {
                                            DueCustomerCategory.CONFIRMED_TEST -> {
                                                Surface(
                                                    color = Color(0xFFF3E5F5),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "🧪 নিশ্চিত টেস্ট",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF7B1FA2),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            DueCustomerCategory.SAMPLE_SEED_DATA -> {
                                                Surface(
                                                    color = Color(0xFFFFF3E0),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "📦 প্রারম্ভিক নমুনা",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFE65100),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            DueCustomerCategory.NEEDS_MANUAL_REVIEW -> {
                                                Surface(
                                                    color = Color(0xFFECEFF1),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "⚠️ ম্যানুয়াল যাচাই",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF455A64),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = "ফোন: ${cust.phone}${if (cust.address.isNotBlank()) " • ${cust.address}" else ""}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            // Due Highlight Card
                            Surface(
                                color = Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ড্যাশবোর্ড বকেয়ায় যুক্ত অংশ:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFC62828)
                                    )
                                    Text(
                                        text = "৳${BengaliFormatter.formatCurrency(cust.currentDue)}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentRed
                                    )
                                }
                            }

                            // Detection Reasons
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                item.reasons.forEach { reason ->
                                    Text(
                                        text = "• $reason",
                                        fontSize = 11.sp,
                                        color = when (item.category) {
                                            DueCustomerCategory.CONFIRMED_TEST -> Color(0xFF6A1B9A)
                                            DueCustomerCategory.SAMPLE_SEED_DATA -> Color(0xFFBF360C)
                                            DueCustomerCategory.NEEDS_MANUAL_REVIEW -> Color(0xFF37474F)
                                        }
                                    )
                                }
                            }

                            // Transactions breakdown
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "লেনদেন বিবরণ: মোট বিল: ৳${BengaliFormatter.formatCurrency(cust.totalBilled)} | পরিশোধ: ৳${BengaliFormatter.formatCurrency(cust.totalPaid)} | বিক্রয় চালান: ${item.salesCount} টি | পেমেন্ট রসিদ: ${item.receiptsCount} টি",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Warning for manual review
                            if (item.category == DueCustomerCategory.NEEDS_MANUAL_REVIEW) {
                                Surface(
                                    color = Color(0xFFFFF8E1),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "⚠️ সতর্কতা: এই কাস্টমারটিতে কোনো স্বয়ংক্রিয় টেস্ট মার্কার নেই। এটি আপনার আসল গ্রাহক হতে পারে। সতর্কতার সাথে সিদ্ধান্ত নিন।",
                                        fontSize = 10.sp,
                                        color = Color(0xFF8D6E63),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            // Single Item Recycle Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        singleCustomerToRecycle = cust
                                        singleReasonInput = when (item.category) {
                                            DueCustomerCategory.CONFIRMED_TEST -> "টেস্ট কাস্টমার পরিচ্ছন্নকরণ (${cust.name})"
                                            DueCustomerCategory.SAMPLE_SEED_DATA -> "প্রারম্ভিক নমুনা ডেটাসেট কাস্টমার পরিচ্ছন্নকরণ (${cust.name})"
                                            DueCustomerCategory.NEEDS_MANUAL_REVIEW -> "কাস্টমার বকেয়া পরিচ্ছন্নকরণ (${cust.name})"
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_recycle_single_${cust.id}")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("রিসাইকেল বিনে পাঠান", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Batch Confirmation Dialog
        if (showBatchConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showBatchConfirmDialog = false },
                icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(36.dp)) },
                title = { Text("নির্বাচিত রেকর্ড রিসাইকেল বিনে স্থানান্তর", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "আপনি নির্বাচিত ${selectedCustomers.size} জন কাস্টমারকে রিসাইকেল বিনে স্থানান্তর করতে যাচ্ছেন।",
                            fontSize = 13.sp
                        )

                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "ড্যাশবোর্ড বকেয়া তাৎক্ষণিক হ্রাস পাবে:",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC62828),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "৳${BengaliFormatter.formatCurrency(selectedDuesTotal)}",
                                    fontSize = 18.sp,
                                    color = AccentRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🛡️ ৩০ দিনের গ্যারান্টি: এটি নিরাপদ সফট-ডিলিট। 'রিসাইকেল বিন' ট্যাব থেকে যেকোনো সময় Restore করলে আগের হিসাব ও সম্পর্ক অক্ষত অবস্থায় ফিরে আসবে।",
                                fontSize = 11.sp,
                                color = Color(0xFF1B5E20),
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        OutlinedTextField(
                            value = batchReasonInput,
                            onValueChange = { batchReasonInput = it },
                            label = { Text("স্থানান্তরের কারণ") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val targetCusts = selectedCustomers
                            val reason = batchReasonInput.trim()
                            showBatchConfirmDialog = false
                            selectedIds = emptySet()
                            onSoftDeleteMultipleCustomers(targetCusts, reason)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        modifier = Modifier.testTag("btn_confirm_batch_recycle")
                    ) {
                        Text("নিশ্চিত ও রিসাইকেল বিনে পাঠান")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showBatchConfirmDialog = false }) { Text("বাতিল") }
                }
            )
        }

        // Single Confirmation Dialog
        if (singleCustomerToRecycle != null) {
            val target = singleCustomerToRecycle!!
            AlertDialog(
                onDismissRequest = { singleCustomerToRecycle = null },
                icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(36.dp)) },
                title = { Text("কাস্টমার রিসাইকেল বিনে স্থানান্তর", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "কাস্টমার '${target.name}' (ফোন: ${target.phone}) সক্রিয় তালিকা থেকে সরিয়ে রিসাইকেল বিনে রাখা হবে।",
                            fontSize = 13.sp
                        )
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ড্যাশবোর্ড বকেয়া থেকে ৳${BengaliFormatter.formatCurrency(target.currentDue)} তাৎক্ষণিক বাদ যাবে।",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🛡️ ৩০ দিনের গ্যারান্টি: রিসাইকেল বিন থেকে যেকোনো সময় Restore করলে এই কাস্টমার ও তার বকেয়া সম্পূর্ণ ফিরে আসবে।",
                                fontSize = 11.sp,
                                color = Color(0xFF1B5E20),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        OutlinedTextField(
                            value = singleReasonInput,
                            onValueChange = { singleReasonInput = it },
                            label = { Text("স্থানান্তরের কারণ") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val cust = target
                            val reason = singleReasonInput.trim()
                            singleCustomerToRecycle = null
                            selectedIds = selectedIds - cust.id
                            onSoftDeleteCustomer(cust, reason)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        modifier = Modifier.testTag("btn_confirm_single_recycle")
                    ) {
                        Text("রিসাইকেল বিনে পাঠান")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { singleCustomerToRecycle = null }) { Text("বাতিল") }
                }
            )
        }
    }
}

