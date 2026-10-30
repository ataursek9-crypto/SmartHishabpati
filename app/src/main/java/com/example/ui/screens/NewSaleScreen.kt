package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.example.data.model.*
import com.example.ui.components.SaleSuccessDialog
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.util.PdfReceiptGenerator
import com.example.viewmodel.BusinessViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSaleScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit,
    onSaleSuccess: (Sale, List<SaleItem>) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()

    var invoiceNumber by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        invoiceNumber = viewModel.repository.getNextInvoiceNumber()
    }

    // Customer Selection State
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var customerSearchQuery by remember { mutableStateOf("") }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }

    // Cart Items State: Map of Product to (quantity, unitPrice)
    var cartItems by remember { mutableStateOf(listOf<SaleItem>()) }

    // Product Picker Dialog
    var showProductPicker by remember { mutableStateOf(false) }
    var productSearchQuery by remember { mutableStateOf("") }

    // Discount & Payment
    var discountText by remember { mutableStateOf("0") }
    var paidAmountText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var note by remember { mutableStateOf("") }

    // Calculations
    val subtotal = remember(cartItems) { cartItems.sumOf { it.totalPrice } }
    val discount = remember(discountText) { discountText.toDoubleOrNull() ?: 0.0 }
    val totalAmount = remember(subtotal, discount) { (subtotal - discount).coerceAtLeast(0.0) }
    val paidAmount = remember(paidAmountText, totalAmount) {
        paidAmountText.toDoubleOrNull() ?: totalAmount
    }
    val dueAmount = remember(totalAmount, paidAmount) {
        (totalAmount - paidAmount).coerceAtLeast(0.0)
    }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successSaleData by remember { mutableStateOf<Pair<Sale, List<SaleItem>>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("নতুন বিক্রি এন্ট্রি", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Invoice & Date Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ইনভয়েস নম্বর:", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text(invoiceNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("তারিখ:", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text(BengaliFormatter.formatDateOnly(System.currentTimeMillis()), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Step 1: Customer Selection
            Text(
                text = "১. কাস্টমার নির্বাচন করুন *",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (selectedCustomer == null) {
                OutlinedButton(
                    onClick = { showCustomerPicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_select_customer"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PersonSearch, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("কাস্টমার খুঁজুন বা নির্বাচন করুন")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryLightBlue)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedCustomer!!.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryNavy)
                            Text("মোবাইল: ${selectedCustomer!!.phone}", fontSize = 12.sp, color = Color.DarkGray)
                            if (currentUser?.canViewCustomerDue != false) {
                                Text(
                                    "আগের বকেয়া: ${BengaliFormatter.formatCurrency(selectedCustomer!!.currentDue)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedCustomer!!.currentDue > 0) AccentRed else AccentGreen
                                )
                            }
                        }
                        IconButton(onClick = { selectedCustomer = null }) {
                            Icon(Icons.Default.Close, contentDescription = "পরিবর্তন করুন", tint = AccentRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Step 2: Product Items Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "২. বিক্রির পণ্য যোগ করুন *",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    onClick = { showProductPicker = true },
                    modifier = Modifier.testTag("btn_add_product")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("পণ্য যোগ করুন")
                }
            }

            if (cartItems.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "কোনো পণ্য এখনও যোগ করা হয়নি। ওপরে 'পণ্য যোগ করুন' চাপুন।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cartItems.forEachIndexed { index, item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "${item.quantity} × ${BengaliFormatter.formatCurrency(item.unitPrice)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Text(
                                    BengaliFormatter.formatCurrency(item.totalPrice),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = PrimaryNavy
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        cartItems = cartItems.filterIndexed { i, _ -> i != index }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "মুছুন", tint = AccentRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Step 3: Billing & Payment Summary
            Text(
                text = "৩. বিল ও পেমেন্ট হিসাব",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("মোট উপ-বিল (Subtotal):", fontSize = 13.sp)
                        Text(BengaliFormatter.formatCurrency(subtotal), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (currentUser?.canDiscount != false) {
                        OutlinedTextField(
                            value = discountText,
                            onValueChange = { discountText = it },
                            label = { Text("অনুমোদিত ডিসকাউন্ট (টাকা)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sale_discount_input"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("সর্বমোট বিল (Net Bill):", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(BengaliFormatter.formatCurrency(totalAmount), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryNavy)
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Paid Amount Input
                    OutlinedTextField(
                        value = paidAmountText,
                        onValueChange = { paidAmountText = it },
                        label = { Text("কত টাকা জমা পেয়েছেন? *") },
                        placeholder = { Text("পুরো বিল পরিশোধ হলে খালি রাখুন বা $totalAmount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sale_paid_amount_input"),
                        singleLine = true,
                        trailingIcon = {
                            TextButton(onClick = { paidAmountText = totalAmount.toString() }) {
                                Text("পূর্ণ জমা", fontSize = 11.sp)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Remaining Due Notification
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("অবশিষ্ট বকেয়া:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            BengaliFormatter.formatCurrency(dueAmount),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (dueAmount > 0) AccentRed else AccentGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Payment Method Selector
                    Text("পেমেন্টের মাধ্যম:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            PaymentMethod.CASH to "নগদ ক্যাশ",
                            PaymentMethod.BKASH to "বিকাশ",
                            PaymentMethod.NAGAD to "নগদ ওয়ালেট",
                            PaymentMethod.BANK_TRANSFER to "ব্যাংক"
                        ).forEach { (method, label) ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Note
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("বিশেষ নোট বা মন্তব্য (ঐচ্ছিক)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Complete Sale Button
            Button(
                onClick = {
                    if (selectedCustomer == null) {
                        errorMessage = "দয়া করে একজন কাস্টমার নির্বাচন করুন!"
                        return@Button
                    }
                    if (cartItems.isEmpty()) {
                        errorMessage = "দয়া করে অন্তত একটি পণ্য যোগ করুন!"
                        return@Button
                    }
                    isSubmitting = true
                    errorMessage = ""

                    val user = currentUser ?: return@Button
                    val finalSale = Sale(
                        invoiceNumber = invoiceNumber,
                        customerId = selectedCustomer!!.id,
                        customerName = selectedCustomer!!.name,
                        customerPhone = selectedCustomer!!.phone,
                        salesmanId = user.id,
                        salesmanName = user.name,
                        subtotal = subtotal,
                        discount = discount,
                        totalAmount = totalAmount,
                        paidAmount = paidAmount,
                        dueAmount = dueAmount,
                        paymentMethod = paymentMethod,
                        note = note
                    )

                    viewModel.createSale(
                        sale = finalSale,
                        items = cartItems,
                        onSuccess = { inv ->
                            isSubmitting = false
                            successSaleData = Pair(finalSale, cartItems)
                        },
                        onError = { err ->
                            isSubmitting = false
                            errorMessage = err
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_complete_sale"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("বিক্রি সম্পন্ন করুন ও রসিদ তৈরি", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Sale Success Animated Dialog
    successSaleData?.let { (sale, items) ->
        SaleSuccessDialog(
            sale = sale,
            onDismiss = {
                val current = successSaleData
                successSaleData = null
                if (current != null) {
                    onSaleSuccess(current.first, current.second)
                }
            }
        )
    }

    // Modal Sheet / Dialog: Customer Picker
    if (showCustomerPicker) {
        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("কাস্টমার নির্বাচন", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    TextButton(onClick = {
                        showCustomerPicker = false
                        showAddCustomerDialog = true
                    }) {
                        Text("+ নতুন কাস্টমার")
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = customerSearchQuery,
                        onValueChange = { customerSearchQuery = it },
                        placeholder = { Text("নাম বা ফোন নম্বর দিয়ে খুঁজুন...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val filtered = remember(customers, customerSearchQuery) {
                        customers.filter {
                            it.name.contains(customerSearchQuery, ignoreCase = true) ||
                                    it.phone.contains(customerSearchQuery)
                        }
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(filtered) { c ->
                            ListItem(
                                headlineContent = { Text(c.name, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text("ফোন: ${c.phone} | বকেয়া: ৳${c.currentDue}") },
                                modifier = Modifier.clickable {
                                    selectedCustomer = c
                                    showCustomerPicker = false
                                }
                            )
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCustomerPicker = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Modal Dialog: Add New Customer Quick Form
    if (showAddCustomerDialog) {
        var newCustName by remember { mutableStateOf("") }
        var newCustPhone by remember { mutableStateOf("") }
        var newCustAddress by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddCustomerDialog = false },
            title = { Text("নতুন কাস্টমার যোগ করুন", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newCustName,
                        onValueChange = { newCustName = it },
                        label = { Text("কাস্টমারের নাম লিখুন *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCustPhone,
                        onValueChange = { newCustPhone = it },
                        label = { Text("মোবাইল নম্বর লিখুন *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCustAddress,
                        onValueChange = { newCustAddress = it },
                        label = { Text("ঠিকানা / বাজার (ঐচ্ছিক)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCustName.isNotBlank() && newCustPhone.isNotBlank()) {
                            val newC = Customer(
                                id = "c_${System.currentTimeMillis()}",
                                name = newCustName.trim(),
                                phone = newCustPhone.trim(),
                                address = newCustAddress.trim()
                            )
                            viewModel.saveCustomer(newC) {
                                selectedCustomer = newC
                                showAddCustomerDialog = false
                            }
                        }
                    }
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomerDialog = false }) {
                    Text("বাতিল")
                }
            }
        )
    }

    // Modal Sheet / Dialog: Product Picker
    if (showProductPicker) {
        var selectedProductForQty by remember { mutableStateOf<Product?>(null) }
        var enteredQty by remember { mutableStateOf("1") }
        var customPrice by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showProductPicker = false },
            title = { Text("পণ্য নির্বাচন করুন", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                if (selectedProductForQty == null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = productSearchQuery,
                            onValueChange = { productSearchQuery = it },
                            placeholder = { Text("পণ্যের নাম বা SKU লিখুন...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val filteredProds = remember(products, productSearchQuery) {
                            products.filter {
                                it.name.contains(productSearchQuery, ignoreCase = true) ||
                                        it.sku.contains(productSearchQuery, ignoreCase = true)
                            }
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                            items(filteredProds) { p ->
                                ListItem(
                                    headlineContent = { Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                                    supportingContent = {
                                        Text("স্টক: ${p.currentStock} ${p.unit} | বিক্রয়মূল্য: ৳${p.sellingPrice}")
                                    },
                                    modifier = Modifier.clickable {
                                        selectedProductForQty = p
                                        customPrice = p.sellingPrice.toString()
                                    }
                                )
                                Divider()
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = selectedProductForQty!!.name,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryNavy,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "বর্তমান মজুদ স্টক: ${selectedProductForQty!!.currentStock} ${selectedProductForQty!!.unit}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        OutlinedTextField(
                            value = enteredQty,
                            onValueChange = { enteredQty = it },
                            label = { Text("পরিমাণ (${selectedProductForQty!!.unit}) লিখুন *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = customPrice,
                            onValueChange = { customPrice = it },
                            label = { Text("প্রতি ${selectedProductForQty!!.unit} বিক্রয়মূল্য (টাকা) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (selectedProductForQty != null) {
                    Button(
                        onClick = {
                            val qty = enteredQty.toDoubleOrNull() ?: 1.0
                            val price = customPrice.toDoubleOrNull() ?: selectedProductForQty!!.sellingPrice
                            if (qty > 0 && price >= 0) {
                                cartItems = cartItems + SaleItem(
                                    invoiceNumber = invoiceNumber,
                                    productId = selectedProductForQty!!.id,
                                    productName = selectedProductForQty!!.name,
                                    quantity = qty,
                                    unitPrice = price,
                                    purchasePriceAtSale = selectedProductForQty!!.purchasePrice,
                                    totalPrice = qty * price
                                )
                                showProductPicker = false
                            }
                        }
                    ) {
                        Text("কার্টে যোগ করুন")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (selectedProductForQty != null) {
                            selectedProductForQty = null
                        } else {
                            showProductPicker = false
                        }
                    }
                ) {
                    Text(if (selectedProductForQty != null) "পেছনে" else "বাতিল")
                }
            }
        )
    }
}
