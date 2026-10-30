package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.util.BengaliFormatter
import com.example.viewmodel.BusinessViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesmanStockVerificationScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    val currentUser by viewModel.currentUser.collectAsState()
    val productItems by viewModel.productVerificationItems.collectAsState()
    val allVerifications by viewModel.allStockVerifications.collectAsState()

    // Filter salesman's own submissions
    val myVerifications = remember(allVerifications, currentUser) {
        val uid = currentUser?.id ?: ""
        allVerifications.filter { it.verifierUserId == uid }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: পণ্য তালিকা ও গণনা, 1: আমার জমাকৃত যাচাই
    var searchQuery by remember { mutableStateOf("") }
    var countingProductItem by remember { mutableStateOf<ProductVerificationItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("দোকানের স্টক যাচাই", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("বাস্তব গণনা ও তথ্য যাচাই", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_salesman_stock_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান", tint = MaterialTheme.colorScheme.onPrimary)
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
            // Header Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("পণ্য ও গণনা করুন", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("আমার জমাকৃত রেকর্ড", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            if (myVerifications.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                    Text("${myVerifications.size}", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Products to Verify Tab
                    SalesmanProductsToVerifyTab(
                        productItems = productItems,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onStartCount = { countingProductItem = it }
                    )
                }
                1 -> {
                    // My Submissions & Decisions Tab
                    SalesmanSubmissionsHistoryTab(
                        submissions = myVerifications
                    )
                }
            }
        }
    }

    // Counting & Verification Dialog
    countingProductItem?.let { item ->
        StockCountDialog(
            item = item,
            onDismiss = { countingProductItem = null },
            onSubmit = { physicalStock, notes, imageUri ->
                viewModel.submitStockVerification(
                    productId = item.product.id,
                    physicalStock = physicalStock,
                    notes = notes,
                    imageUri = imageUri
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (success) {
                        countingProductItem = null
                    }
                }
            }
        )
    }
}

@Composable
private fun SalesmanProductsToVerifyTab(
    productItems: List<ProductVerificationItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onStartCount: (ProductVerificationItem) -> Unit
) {
    var filterStatus by remember { mutableStateOf("ALL") } // ALL, PENDING, OVERDUE, RECOUNT

    val filteredList = remember(productItems, searchQuery, filterStatus) {
        productItems.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.product.name.contains(searchQuery, ignoreCase = true) ||
                item.product.sku.contains(searchQuery, ignoreCase = true) ||
                item.product.category.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (filterStatus) {
                "OVERDUE" -> item.isOverdue
                "RECOUNT" -> item.pendingMismatch?.status == VerificationStatus.RECOUNT_REQUESTED
                "PENDING" -> item.lastVerification == null || item.isOverdue
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("input_search_salesman_verify"),
            placeholder = { Text("পণ্যের নাম বা কোড দিয়ে খুঁজুন...", fontSize = 13.sp) },
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

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = filterStatus == "ALL",
                onClick = { filterStatus = "ALL" },
                label = { Text("সব পণ্য (${productItems.size})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filterStatus == "OVERDUE",
                onClick = { filterStatus = "OVERDUE" },
                label = { Text("দেরি / মেয়াদোত্তীর্ণ", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filterStatus == "RECOUNT",
                onClick = { filterStatus = "RECOUNT" },
                label = { Text("পুনরায় গণনা নির্দেশ", fontSize = 11.sp) }
            )
        }

        if (filteredList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো পণ্য তালিকাভুক্ত নেই", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
            ) {
                items(filteredList, key = { it.product.id }) { item ->
                    SalesmanProductVerifyCard(item = item, onStartCount = { onStartCount(item) })
                }
            }
        }
    }
}

@Composable
private fun SalesmanProductVerifyCard(
    item: ProductVerificationItem,
    onStartCount: () -> Unit
) {
    val product = item.product
    val schedule = item.schedule
    val lastVerif = item.lastVerification
    val pendingMismatch = item.pendingMismatch

    val isRecount = pendingMismatch?.status == VerificationStatus.RECOUNT_REQUESTED
    val isPendingMismatch = pendingMismatch?.status == VerificationStatus.MISMATCH_PENDING

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecount) Color(0xFFF3E5F5) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "ক্যাটাগরি: ${product.category} | SKU: ${product.sku.ifBlank { "N/A" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Indicator
                if (isRecount) {
                    Surface(
                        color = Color(0xFFE1BEE7),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "পুনরায় গণনা করুন",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color(0xFF6A1B9A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                } else if (isPendingMismatch) {
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "অমিল (অনুমোদন বাকি)",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                } else if (item.isOverdue) {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "যাচাইয়ের সময় পার হয়েছে",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                } else if (lastVerif != null) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp)
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
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "যাচাই বাকি",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Stock Details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("অ্যাপে রেকর্ডকৃত স্টক", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${BengaliFormatter.formatNumber(product.currentStock)} ${product.unit}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }

                Column(horizontalAlignment = Alignment.End) {
                    val lastDate = lastVerif?.verifiedAt?.let {
                        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(it))
                    } ?: "কখনো হয়নি"
                    Text("সর্বশেষ যাচাই", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(lastDate, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (isRecount && pendingMismatch?.adminNote?.isNotBlank() == true) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "এডমিনের নির্দেশ: ${pendingMismatch.adminNote}",
                            fontSize = 12.sp,
                            color = Color(0xFF4A148C),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onStartCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_count_stock_${product.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecount) Color(0xFF6A1B9A) else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isRecount) "পুনরায় গণনা করে জমা দিন" else "বাস্তবে গুনে যাচাই করুন")
            }
        }
    }
}

@Composable
private fun StockCountDialog(
    item: ProductVerificationItem,
    onDismiss: () -> Unit,
    onSubmit: (physicalStock: Double, notes: String, imageUri: String?) -> Unit
) {
    val product = item.product
    var physicalStockInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var capturedImageUri by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            capturedImageUri = uri.toString()
        }
    }

    val countedNum = physicalStockInput.toDoubleOrNull()
    val difference = countedNum?.let { it - product.currentStock }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("স্টক গণনা ও যাচাই", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(product.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Snapshot Box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("অ্যাপে রেকর্ডকৃত স্টক (Snapshot):", fontSize = 12.sp)
                        Text(
                            "${BengaliFormatter.formatNumber(product.currentStock)} ${product.unit}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Physical Count Input
                OutlinedTextField(
                    value = physicalStockInput,
                    onValueChange = { physicalStockInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_physical_stock"),
                    label = { Text("বাস্তবে গুনে পাওয়া পরিমাণ (${product.unit})") },
                    placeholder = { Text("যেমন: ২৫") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                // Real-time Difference Preview
                if (difference != null) {
                    val diffColor = when {
                        difference == 0.0 -> Color(0xFF2E7D32)
                        difference < 0.0 -> MaterialTheme.colorScheme.error
                        else -> Color(0xFFE65100)
                    }
                    val diffStatusText = when {
                        difference == 0.0 -> "নিখুঁত মিলেছে! কোনো পার্থক্য নেই।"
                        difference < 0.0 -> "ঘাটতি: ${BengaliFormatter.formatNumber(-difference)} ${product.unit} কম পাওয়া গেছে!"
                        else -> "অতিরিক্ত: +${BengaliFormatter.formatNumber(difference)} ${product.unit} বেশি পাওয়া গেছে!"
                    }

                    Surface(
                        color = diffColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (difference == 0.0) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = diffColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                diffStatusText,
                                color = diffColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("মন্তব্য বা কারণ (ঐচ্ছিক)") },
                    placeholder = { Text("কোনো ভাঙা বা ত্রুটিপূর্ণ পণ্য থাকলে লিখুন...") },
                    minLines = 2,
                    maxLines = 3
                )

                // Optional Photo Picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (capturedImageUri != null) "ছবি সংযুক্ত করা হয়েছে" else "প্রমাণ ছবি (ঐচ্ছিক)",
                        fontSize = 12.sp,
                        color = if (capturedImageUri != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (capturedImageUri != null) "পরিবর্তন" else "ছবি তুলুন")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = countedNum
                    if (count != null && count >= 0.0) {
                        onSubmit(count, notesInput, capturedImageUri)
                    }
                },
                enabled = countedNum != null && countedNum >= 0.0,
                modifier = Modifier.testTag("btn_submit_stock_verification")
            ) {
                Text("যাচাই জমা দিন")
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
private fun SalesmanSubmissionsHistoryTab(
    submissions: List<StockVerification>
) {
    if (submissions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("আপনি এখনো কোনো স্টক যাচাই জমা দেননি", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(submissions, key = { it.id }) { verif ->
                SalesmanSubmissionRecordCard(verif = verif)
            }
        }
    }
}

@Composable
private fun SalesmanSubmissionRecordCard(verif: StockVerification) {
    val dateStr = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(verif.verifiedAt))

    val statusLabel = when (verif.status) {
        VerificationStatus.MATCHED -> "মিলেছে (স্বয়ংক্রিয়)"
        VerificationStatus.MISMATCH_PENDING -> "অমিল (এডমিন অনুমোদন অপেক্ষমাণ)"
        VerificationStatus.APPROVED_ADJUSTED -> "এডমিন কর্তৃক অনুমোদিত ও সমন্বিত"
        VerificationStatus.REJECTED -> "এডমিন কর্তৃক প্রত্যাখ্যাত"
        VerificationStatus.RECOUNT_REQUESTED -> "এডমিন পুনরায় গণনার নির্দেশ দিয়েছেন"
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(verif.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("পূর্বে ছিল: ${verif.appStockSnapshot} | আপনি গুনেছেন: ${verif.physicalStock} ${verif.productUnit}", fontSize = 12.sp)
                Text(
                    "পার্থক্য: ${if (verif.difference > 0) "+${verif.difference}" else "${verif.difference}"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (verif.difference == 0.0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }

            if (verif.adminNote?.isNotBlank() == true) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text(
                    "এডমিনের মন্তব্য: ${verif.adminNote}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
