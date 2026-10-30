package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.ui.components.ProductImageSelector
import com.example.ui.theme.*
import com.example.viewmodel.BusinessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesmanProductSubmissionScreen(
    viewModel: BusinessViewModel,
    submissionToEdit: ProductSubmission? = null,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val isResubmission = submissionToEdit != null

    var name by remember { mutableStateOf(submissionToEdit?.name ?: "") }
    var productCode by remember { mutableStateOf(submissionToEdit?.productCode ?: "") }
    var category by remember { mutableStateOf(submissionToEdit?.category ?: "পুরুষদের পোশাক (Men's Wear)") }
    var purchasePrice by remember {
        mutableStateOf(
            if (submissionToEdit != null && submissionToEdit.purchasePrice > 0) {
                if (submissionToEdit.purchasePrice % 1.0 == 0.0) submissionToEdit.purchasePrice.toLong().toString()
                else String.format(java.util.Locale.US, "%.2f", submissionToEdit.purchasePrice)
            } else ""
        )
    }

    // Reactive automatic selling price calculation (Selling Price = Cost Price * 1.70)
    // Decimals rounded to 2 decimal places, empty/zero/invalid shows empty string
    val calculatedSellingPrice = remember(purchasePrice) {
        val cost = purchasePrice.toDoubleOrNull()
        if (cost != null && cost > 0.0) {
            val calculated = com.example.data.repository.BusinessRepository.calculateSellingPrice(cost)
            if (calculated % 1.0 == 0.0) {
                calculated.toLong().toString()
            } else {
                String.format(java.util.Locale.US, "%.2f", calculated)
            }
        } else {
            ""
        }
    }

    var initialStock by remember { mutableStateOf(if (submissionToEdit != null) submissionToEdit.initialStock.toString() else "") }
    var unit by remember { mutableStateOf(submissionToEdit?.unit ?: "পিস") }
    var description by remember { mutableStateOf(submissionToEdit?.description ?: "") }

    val productImageMode by viewModel.productImageMode.collectAsStateWithLifecycle()
    val activeBizId by viewModel.activeBusinessId.collectAsStateWithLifecycle()
    var imageUri by remember(submissionToEdit) { mutableStateOf(submissionToEdit?.imageUri) }
    var imageSource by remember(submissionToEdit) { mutableStateOf(submissionToEdit?.imageSource ?: "NONE") }
    var imageSourceInfo by remember(submissionToEdit) { mutableStateOf(submissionToEdit?.imageSourceInfo ?: "") }

    var isSubmitting by remember { mutableStateOf(false) }
    var duplicateWarnings by remember { mutableStateOf<List<String>>(emptyList()) }

    // Check duplicate warnings as name or productCode changes
    LaunchedEffect(name, productCode) {
        if (name.trim().length >= 3 || productCode.trim().length >= 2) {
            duplicateWarnings = viewModel.checkPotentialDuplicates(name.trim(), productCode.trim(), submissionToEdit?.id)
        } else {
            duplicateWarnings = emptyList()
        }
    }

    val categorySuggestions = com.example.data.repository.BusinessRepository.CLOTHING_CATEGORIES
    val unitSuggestions = com.example.data.repository.BusinessRepository.CLOTHING_UNITS

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isResubmission) "প্রোডাক্ট সংশোধন ও পুনঃজমা" else "নতুন প্রোডাক্ট জমা দিন",
                        fontWeight = FontWeight.Bold
                    )
                },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Notice card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "সেলসম্যান হিসেবে আপনি নতুন প্রোডাক্ট তথ্য জমা দিতে পারেন। এডমিন যাচাই করে অনুমোদন দিলে তা মূল ইনভেন্টরি ও বিক্রির তালিকায় যুক্ত হবে।",
                            fontSize = 12.sp,
                            color = PrimaryNavy
                        )
                    }
                }
            }

            // Cloud Connection Destination Status
            item {
                if (activeBizId.isNotBlank()) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ক্লাউড সিঙ্ক সক্রিয় (বিজনেস আইডি: $activeBizId) — জমা দেওয়ার সাথে সাথে অ্যাডমিনের ফোনে চলে যাবে।",
                                fontSize = 11.sp,
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "সতর্কতা: ক্লাউড ব্যবসার সাথে সংযোগ নেই। প্রোডাক্টটি কেবল এই ফোনে সংরক্ষিত থাকবে। অ্যাডমিনের ফোনে পাঠানোর জন্য ড্যাশবোর্ড থেকে অ্যাডমিনের বিজনেস কোড কানেক্ট করুন।",
                                fontSize = 11.sp,
                                color = Color(0xFFBF360C)
                            )
                        }
                    }
                }
            }

            // If resubmitting for correction, show Admin's required correction reason prominently
            if (isResubmission && !submissionToEdit.adminCorrectionNote.isNullOrBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Feedback, contentDescription = null, tint = AccentRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "এডমিনের সংশোধনের নোট",
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRed,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = submissionToEdit.adminCorrectionNote,
                                fontSize = 13.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }

            // Potential duplicate warning banner
            if (duplicateWarnings.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "সম্ভাব্য ডুপ্লিকেট সতর্কতা!",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF8D6E00),
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            duplicateWarnings.forEach { warn ->
                                Text("• $warn", fontSize = 12.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }
            }

            // Product Image Selector
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

            // Product Name Field
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("পণ্যের নাম *") },
                    placeholder = { Text("যেমন: কটন ফরমাল শার্ট (L)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_product_name"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Product Code (SKU) Field - Admin Panel-এর মতো
            item {
                OutlinedTextField(
                    value = productCode,
                    onValueChange = { productCode = it },
                    label = { Text("SKU / প্রোডাক্ট কোড") },
                    placeholder = { Text("যেমন: SHT-FORMAL-01") },
                    supportingText = {
                        Text(
                            text = if (productCode.isNotBlank()) "কোড: ${productCode.trim()}" else "খালি রাখলে স্বয়ংক্রিয় কোড (SKU-xxxx) তৈরি হবে",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_product_code"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // Category Selection
            item {
                Column {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("ক্যাটাগরি *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_product_category"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categorySuggestions.forEach { cat ->
                            SuggestionChip(
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Purchase Price & Selling Price Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { purchasePrice = it },
                        label = { Text("ক্রয়মূল্য / Cost Price (৳) *") },
                        placeholder = { Text("0.0") },
                        supportingText = { Text("লাভের হার স্বয়ংক্রিয় ৭০%", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_product_purchase_price"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = calculatedSellingPrice,
                        onValueChange = { /* Read-only for Salesman */ },
                        readOnly = true,
                        label = { Text("বিক্রয়মূল্য / Selling Price (৳)") },
                        placeholder = { Text("ক্রয়মূল্য দিলে হিসাব হবে") },
                        supportingText = {
                            if (calculatedSellingPrice.isNotBlank()) {
                                Text("হিসাব: ক্রয়মূল্য × ১.৭০ (৭০% লাভ)", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("ক্রয়মূল্য লিখলে স্বয়ংক্রিয় পূরণ হবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "লক করা (স্বয়ংক্রিয় হিসাব)",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_product_selling_price"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // Initial Stock & Unit Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = initialStock,
                        onValueChange = { initialStock = it },
                        label = { Text("প্রাথমিক স্টক *") },
                        placeholder = { Text("0.0") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_product_initial_stock"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("একক / Unit *") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_product_unit"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    unitSuggestions.forEach { u ->
                        SuggestionChip(
                            onClick = { unit = u },
                            label = { Text(u, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // Description / Remarks
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("বিবরণ / নোট (ঐচ্ছিক)") },
                    placeholder = { Text("কোম্পানি, বিশেষ অফার বা বিবরণ লিখুন...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("input_product_description"),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )
            }

            // Submit Button
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val pName = name.trim()
                        val pCat = category.trim()
                        val pBuy = purchasePrice.toDoubleOrNull()
                        val pSell = calculatedSellingPrice.toDoubleOrNull()
                        val pStock = initialStock.toDoubleOrNull()

                        if (pName.isBlank()) {
                            Toast.makeText(context, "পণ্যের নাম দিন!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (pBuy == null || pBuy <= 0.0) {
                            Toast.makeText(context, "সঠিক ক্রয়মূল্য দিন!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (pSell == null || pSell <= 0.0) {
                            Toast.makeText(context, "বিক্রয়মূল্য সঠিকভাবে হিসাব করা যায়নি!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (pStock == null || pStock < 0.0) {
                            Toast.makeText(context, "সঠিক প্রাথমিক স্টক দিন!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSubmitting = true
                        if (isResubmission) {
                            viewModel.resubmitProduct(
                                submissionId = submissionToEdit!!.id,
                                name = pName,
                                productCode = productCode.trim(),
                                category = pCat,
                                purchasePrice = pBuy,
                                sellingPrice = pSell,
                                initialStock = pStock,
                                unit = unit.trim().ifBlank { "পিস" },
                                description = description.trim(),
                                imageUri = imageUri,
                                imageSource = imageSource,
                                imageSourceInfo = imageSourceInfo,
                                onSuccess = {
                                    isSubmitting = false
                                    Toast.makeText(context, "সংশোধিত প্রোডাক্ট পুনঃজমা দেওয়া হয়েছে", Toast.LENGTH_LONG).show()
                                    onBack()
                                },
                                onError = { err ->
                                    isSubmitting = false
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            viewModel.submitProduct(
                                name = pName,
                                productCode = productCode.trim(),
                                category = pCat,
                                purchasePrice = pBuy,
                                sellingPrice = pSell,
                                initialStock = pStock,
                                unit = unit.trim().ifBlank { "পিস" },
                                description = description.trim(),
                                imageUri = imageUri,
                                imageSource = imageSource,
                                imageSourceInfo = imageSourceInfo,
                                onSuccess = {
                                    isSubmitting = false
                                    Toast.makeText(context, "প্রোডাক্ট সফলভাবে জমা দেওয়া হয়েছে", Toast.LENGTH_LONG).show()
                                    onBack()
                                },
                                onError = { err ->
                                    isSubmitting = false
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("btn_submit_product"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(if (isResubmission) Icons.Default.Update else Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isResubmission) "সংশোধন করে পুনরায় জমা দিন" else "অনুমোদনের জন্য জমা দিন",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
