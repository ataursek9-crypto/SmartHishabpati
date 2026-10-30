package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ProductSubmission
import com.example.data.model.ProductSubmissionStatus
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.ProductImageSelector
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.viewmodel.BusinessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProductApprovalScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val allSubmissions by viewModel.allSubmissions.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("অপেক্ষমাণ", "অনুমোদিত", "সংশোধন প্রয়োজন")

    val pendingList = remember(allSubmissions) {
        allSubmissions.filter { it.status == ProductSubmissionStatus.PENDING }
    }
    val approvedList = remember(allSubmissions) {
        allSubmissions.filter { it.status == ProductSubmissionStatus.APPROVED }
    }
    val correctionList = remember(allSubmissions) {
        allSubmissions.filter { it.status == ProductSubmissionStatus.NEEDS_CORRECTION }
    }

    val currentList = when (selectedTabIndex) {
        0 -> pendingList
        1 -> approvedList
        else -> correctionList
    }

    // Dialog States
    var submissionToReview by remember { mutableStateOf<ProductSubmission?>(null) }
    var submissionToReturn by remember { mutableStateOf<ProductSubmission?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("প্রোডাক্ট অনুমোদন ও যাচাই", fontWeight = FontWeight.Bold) },
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
        ) {
            // Tab Row with badged counters
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    val count = when (index) {
                        0 -> pendingList.size
                        1 -> approvedList.size
                        else -> correctionList.size
                    }
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(title, fontSize = 13.sp, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal)
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge(
                                    containerColor = if (index == 0 && count > 0) AccentAmber else MaterialTheme.colorScheme.outlineVariant
                                ) {
                                    Text(count.toString(), fontSize = 11.sp)
                                }
                            }
                        }
                    )
                }
            }

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = when (selectedTabIndex) {
                                0 -> "বর্তমানে কোনো অপেক্ষমাণ প্রোডাক্ট সাবমিশন নেই"
                                1 -> "এখনও কোনো প্রোডাক্ট অনুমোদন করা হয়নি"
                                else -> "সংশোধনের জন্য ফেরত পাঠানো কোনো প্রোডাক্ট নেই"
                            },
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentList, key = { it.id }) { item ->
                        AdminSubmissionCard(
                            submission = item,
                            viewModel = viewModel,
                            onReviewAndApprove = { submissionToReview = item },
                            onReturnForCorrection = { submissionToReturn = item }
                        )
                    }
                }
            }
        }
    }

    // Dialog 1: Review, Edit & Approve Dialog
    submissionToReview?.let { sub ->
        AdminReviewAndApproveDialog(
            submission = sub,
            viewModel = viewModel,
            onDismiss = { submissionToReview = null },
            onApproved = {
                submissionToReview = null
            }
        )
    }

    // Dialog 2: Return for Correction Dialog (Mandatory Reason)
    submissionToReturn?.let { sub ->
        AdminReturnForCorrectionDialog(
            submission = sub,
            onDismiss = { submissionToReturn = null },
            onConfirmReturn = { reason ->
                viewModel.returnProductForCorrection(
                    submissionId = sub.id,
                    reason = reason,
                    onSuccess = {
                        submissionToReturn = null
                        Toast.makeText(context, "সংশোধনের জন্য ফেরত পাঠানো হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            }
        )
    }
}

@Composable
fun AdminSubmissionCard(
    submission: ProductSubmission,
    viewModel: BusinessViewModel,
    onReviewAndApprove: () -> Unit,
    onReturnForCorrection: () -> Unit
) {
    var duplicates by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(submission.name, submission.productCode) {
        duplicates = viewModel.checkPotentialDuplicates(submission.name, submission.productCode, submission.id)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_submission_card_${submission.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name, Thumbnail, Category & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductThumbnail(
                    imageUri = submission.imageUri,
                    category = submission.category,
                    imageSource = submission.imageSource,
                    modifier = Modifier.size(54.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = submission.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "SKU/কোড: ${submission.productCode.ifBlank { "স্বয়ংক্রিয়" }} • ক্যাটাগরি: ${submission.category}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (submission.status) {
                        ProductSubmissionStatus.PENDING -> Color(0xFFFFF3E0)
                        ProductSubmissionStatus.APPROVED -> Color(0xFFE8F5E9)
                        ProductSubmissionStatus.NEEDS_CORRECTION -> Color(0xFFFFEBEE)
                    }
                ) {
                    val label = when (submission.status) {
                        ProductSubmissionStatus.PENDING -> "অপেক্ষমাণ"
                        ProductSubmissionStatus.APPROVED -> "অনুমোদিত"
                        ProductSubmissionStatus.NEEDS_CORRECTION -> "সংশোধন প্রয়োজন"
                    }
                    val textColor = when (submission.status) {
                        ProductSubmissionStatus.PENDING -> Color(0xFFE65100)
                        ProductSubmissionStatus.APPROVED -> Color(0xFF2E7D32)
                        ProductSubmissionStatus.NEEDS_CORRECTION -> Color(0xFFC62828)
                    }
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Potential Duplicate Warning Banner (Rule B.7)
            if (duplicates.isNotEmpty() && submission.status != ProductSubmissionStatus.APPROVED) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF57F17), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সম্ভাব্য ডুপ্লিকেট শনাক্ত হয়েছে:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFF57F17))
                        }
                        duplicates.forEach { warn ->
                            Text("• $warn", fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Pricing & Stock Info (Rule B.3)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ক্রয়মূল্য: ${BengaliFormatter.formatCurrency(submission.purchasePrice)}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("বিক্রয়মূল্য: ${BengaliFormatter.formatCurrency(submission.sellingPrice)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    val margin = submission.sellingPrice - submission.purchasePrice
                    Text("সম্ভাব্য লাভ: ${BengaliFormatter.formatCurrency(margin)}", fontSize = 11.sp, color = Color(0xFF2E7D32))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("প্রাথমিক স্টক: ${submission.initialStock} ${submission.unit}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("জমা: ${BengaliFormatter.formatDate(submission.createdAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            // Submitter Details (Rule B.3)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryNavy, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "জমাদানকারী সেলসম্যান: ${submission.salesmanName} (${submission.salesmanPhone})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (submission.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("বিবরণ: ${submission.description}", fontSize = 12.sp, color = Color.Gray)
            }

            // Correction note if exists
            if (submission.status == ProductSubmissionStatus.NEEDS_CORRECTION && submission.adminCorrectionNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("সংশোধনের নোট: ${submission.adminCorrectionNote}", fontSize = 12.sp, color = AccentRed)
            }

            // Action Buttons for Pending or Needs Correction
            if (submission.status != ProductSubmissionStatus.APPROVED) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReturnForCorrection,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_return_correction_${submission.id}"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
                    ) {
                        Icon(Icons.Default.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("সংশোধনের অনুরোধ", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onReviewAndApprove,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_approve_review_${submission.id}"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("যাচাই ও অনুমোদন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminReviewAndApproveDialog(
    submission: ProductSubmission,
    viewModel: BusinessViewModel,
    onDismiss: () -> Unit,
    onApproved: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(submission.name) }
    var productCode by remember { mutableStateOf(submission.productCode) }
    var category by remember { mutableStateOf(submission.category) }
    var purchasePrice by remember { mutableStateOf(submission.purchasePrice.toString()) }
    var sellingPrice by remember { mutableStateOf(submission.sellingPrice.toString()) }
    var initialStock by remember { mutableStateOf(submission.initialStock.toString()) }
    var unit by remember { mutableStateOf(submission.unit) }
    var description by remember { mutableStateOf(submission.description) }

    val productImageMode by viewModel.productImageMode.collectAsStateWithLifecycle()
    var imageUri by remember { mutableStateOf(submission.imageUri) }
    var imageSource by remember { mutableStateOf(submission.imageSource) }
    var imageSourceInfo by remember { mutableStateOf(submission.imageSourceInfo) }

    var isProcessing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text("প্রোডাক্ট তথ্য যাচাই ও অনুমোদন", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "অনুমোদনের পূর্বে প্রয়োজন হলে তথ্য সংশোধন করতে পারেন। অনুমোদনের পর পণ্যটি মূল ইনভেন্টরি ও বিক্রির তালিকায় যুক্ত হবে।",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                item {
                    ProductImageSelector(
                        imageUri = imageUri,
                        imageSource = imageSource,
                        imageSourceInfo = imageSourceInfo,
                        category = category,
                        productName = name,
                        isAutoMode = productImageMode == "AUTO",
                        onImageSelected = { uri, src, info ->
                            imageUri = uri
                            imageSource = src
                            imageSourceInfo = info
                        },
                        onTriggerAiGeneration = {
                            viewModel.generateAiProductImage(
                                title = name,
                                category = category,
                                sku = productCode,
                                unit = unit
                            )
                        },
                        onSearchOnlineImages = { q ->
                            viewModel.searchOnlineImages(q)
                        },
                        onDownloadOnlineImage = { candidate ->
                            viewModel.downloadOnlineImage(candidate)
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("পণ্যের নাম") },
                        modifier = Modifier.fillMaxWidth().testTag("dialog_input_name"),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = productCode,
                        onValueChange = { productCode = it },
                        label = { Text("SKU / প্রোডাক্ট কোড") },
                        placeholder = { Text("যেমন: SHT-FORMAL-01") },
                        modifier = Modifier.fillMaxWidth().testTag("dialog_input_product_code"),
                        singleLine = true
                    )
                }

                item {
                    Column {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("ক্যাটাগরি *") },
                            modifier = Modifier.fillMaxWidth().testTag("dialog_input_category"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            com.example.data.repository.BusinessRepository.CLOTHING_CATEGORIES.forEach { cat ->
                                SuggestionChip(
                                    onClick = { category = cat },
                                    label = { Text(cat, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = purchasePrice,
                            onValueChange = { purchasePrice = it },
                            label = { Text("ক্রয়মূল্য (৳)") },
                            modifier = Modifier.weight(1f).testTag("dialog_input_purchase_price"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = sellingPrice,
                            onValueChange = { sellingPrice = it },
                            label = { Text("বিক্রয়মূল্য (৳)") },
                            modifier = Modifier.weight(1f).testTag("dialog_input_selling_price"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = initialStock,
                            onValueChange = { initialStock = it },
                            label = { Text("প্রাথমিক স্টক") },
                            modifier = Modifier.weight(1f).testTag("dialog_input_stock"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("একক (Unit) *") },
                            modifier = Modifier.weight(1f).testTag("dialog_input_unit"),
                            singleLine = true
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        com.example.data.repository.BusinessRepository.CLOTHING_UNITS.forEach { u ->
                            SuggestionChip(
                                onClick = { unit = u },
                                label = { Text(u, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("বিবরণ") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pName = name.trim()
                    val pBuy = purchasePrice.toDoubleOrNull()
                    val pSell = sellingPrice.toDoubleOrNull()
                    val pStock = initialStock.toDoubleOrNull()

                    if (pName.isBlank() || pBuy == null || pSell == null || pStock == null) {
                        Toast.makeText(context, "সকল তথ্য সঠিকভাবে পূরণ করুন!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isProcessing = true
                    val finalData = submission.copy(
                        name = pName,
                        productCode = productCode.trim(),
                        category = category.trim().ifBlank { "সাধারণ" },
                        purchasePrice = pBuy,
                        sellingPrice = pSell,
                        initialStock = pStock,
                        unit = unit.trim().ifBlank { "পিস" },
                        description = description.trim()
                    )

                    viewModel.approveProductSubmission(
                        submissionId = submission.id,
                        finalData = finalData,
                        onSuccess = {
                            isProcessing = false
                            Toast.makeText(context, "প্রোডাক্ট সফলভাবে অনুমোদিত হয়েছে", Toast.LENGTH_SHORT).show()
                            onApproved()
                        },
                        onError = { err ->
                            isProcessing = false
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                modifier = Modifier.testTag("btn_dialog_confirm_approve"),
                colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                enabled = !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text("অনুমোদন করুন")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
fun AdminReturnForCorrectionDialog(
    submission: ProductSubmission,
    onDismiss: () -> Unit,
    onConfirmReturn: (String) -> Unit
) {
    val context = LocalContext.current
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("সংশোধনের জন্য ফেরত পাঠান", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column {
                Text(
                    text = "প্রোডাক্ট: ${submission.name}\nজমাদানকারী: ${submission.salesmanName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "কী কী পরিবর্তন বা সংশোধন করতে হবে তা উল্লেখ করুন (বাধ্যতামূলক):",
                    fontSize = 12.sp,
                    color = AccentRed
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("সংশোধনের কারণ *") },
                    placeholder = { Text("যেমন: ক্রয়মূল্য যাচাই করুন, সঠিক একক লিখুন...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("correction_reason_input"),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.trim().isBlank()) {
                        Toast.makeText(context, "সংশোধনের কারণ লেখা বাধ্যতামূলক!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onConfirmReturn(reason.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                modifier = Modifier.testTag("btn_admin_return_correction")
            ) {
                Text("ফেরত পাঠান")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}
