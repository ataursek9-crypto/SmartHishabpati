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
import com.example.data.model.Customer
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentReceipt
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.util.PdfReceiptGenerator
import com.example.viewmodel.BusinessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectPaymentScreen(
    viewModel: BusinessViewModel,
    preselectedCustomerId: String? = null,
    onBack: () -> Unit,
    onReceiptCreated: (PaymentReceipt) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()

    var receiptNumber by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        receiptNumber = viewModel.repository.getNextReceiptNumber()
    }

    var selectedCustomer by remember {
        mutableStateOf<Customer?>(customers.find { it.id == preselectedCustomerId })
    }
    LaunchedEffect(customers, preselectedCustomerId) {
        if (selectedCustomer == null && preselectedCustomerId != null) {
            selectedCustomer = customers.find { it.id == preselectedCustomerId }
        }
    }

    var showCustomerPicker by remember { mutableStateOf(false) }
    var customerSearchQuery by remember { mutableStateOf("") }

    var amountText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var note by remember { mutableStateOf("") }

    val amount = remember(amountText) { amountText.toDoubleOrNull() ?: 0.0 }
    val prevDue = remember(selectedCustomer) { selectedCustomer?.currentDue ?: 0.0 }
    val remainingDue = remember(prevDue, amount) { (prevDue - amount).coerceAtLeast(0.0) }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("টাকা জমা ও মানি রিসিপ্ট", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryTeal,
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
            // Receipt Header
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
                        Text("মানি রসিদ নম্বর:", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text(receiptNumber, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SecondaryTeal)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("তারিখ ও সময়:", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
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
                        .testTag("btn_select_customer_receipt"),
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
                    colors = CardDefaults.cardColors(containerColor = SecondaryLightTeal)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selectedCustomer!!.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SecondaryTeal)
                            Text("মোবাইল: ${selectedCustomer!!.phone}", fontSize = 12.sp, color = Color.DarkGray)
                            Text(
                                "বর্তমান বকেয়া: ${BengaliFormatter.formatCurrency(selectedCustomer!!.currentDue)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedCustomer!!.currentDue > 0) AccentRed else AccentGreen
                            )
                        }
                        IconButton(onClick = { selectedCustomer = null }) {
                            Icon(Icons.Default.Close, contentDescription = "পরিবর্তন", tint = AccentRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Step 2: Payment Amount Entry
            Text(
                text = "২. আদায়ের পরিমাণ ও মাধ্যম *",
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
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("কত টাকা জমা দিচ্ছেন? (টাকা) *") },
                        placeholder = { Text("যেমন: ৫০০০") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_amount_input"),
                        singleLine = true,
                        trailingIcon = {
                            if (selectedCustomer != null && selectedCustomer!!.currentDue > 0) {
                                TextButton(onClick = { amountText = selectedCustomer!!.currentDue.toString() }) {
                                    Text("সম্পূর্ণ বকেয়া", fontSize = 11.sp)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Calculation Preview
                    if (selectedCustomer != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("জমার পর অবশিষ্ট বকেয়া থাকবে:", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                BengaliFormatter.formatCurrency(remainingDue),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingDue > 0) AccentRed else AccentGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Payment Method Chips
                    Text("পেমেন্টের মাধ্যম নির্বাচন করুন:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            PaymentMethod.CASH to "নগদ",
                            PaymentMethod.BKASH to "বিকাশ",
                            PaymentMethod.NAGAD to "নগদ",
                            PaymentMethod.BANK_TRANSFER to "ব্যাংক"
                        ).forEach { (m, label) ->
                            FilterChip(
                                selected = paymentMethod == m,
                                onClick = { paymentMethod = m },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("নোট বা রেফারেন্স (ঐচ্ছিক)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            if (errorMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save & Generate Receipt Button
            Button(
                onClick = {
                    if (selectedCustomer == null) {
                        errorMessage = "কাস্টমার নির্বাচন করুন!"
                        return@Button
                    }
                    if (amount <= 0) {
                        errorMessage = "সঠিক টাকার পরিমাণ লিখুন!"
                        return@Button
                    }
                    isSubmitting = true
                    errorMessage = ""

                    val user = currentUser ?: return@Button
                    val receipt = PaymentReceipt(
                        receiptNumber = receiptNumber,
                        customerId = selectedCustomer!!.id,
                        customerName = selectedCustomer!!.name,
                        customerPhone = selectedCustomer!!.phone,
                        salesmanId = user.id,
                        salesmanName = user.name,
                        amount = amount,
                        previousDue = prevDue,
                        currentDue = remainingDue,
                        paymentMethod = paymentMethod,
                        note = note
                    )

                    viewModel.collectPayment(
                        receipt = receipt,
                        onSuccess = {
                            isSubmitting = false
                            Toast.makeText(context, "টাকা জমা সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                            onReceiptCreated(receipt)
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
                    .testTag("btn_save_receipt"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("জমা নিশ্চিত করুন ও রসিদ তৈরি", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Modal Customer Picker
    if (showCustomerPicker) {
        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            title = { Text("কাস্টমার নির্বাচন", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = customerSearchQuery,
                        onValueChange = { customerSearchQuery = it },
                        placeholder = { Text("নাম বা ফোন দিয়ে খুঁজুন...") },
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
}
