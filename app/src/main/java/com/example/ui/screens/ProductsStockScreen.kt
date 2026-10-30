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
import com.example.data.repository.BusinessRepository
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.ProductImageSelector
import com.example.ui.components.SaleSuccessDialog
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.viewmodel.BusinessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsStockScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit,
    onSaleSuccess: ((Sale, List<SaleItem>) -> Unit)? = null
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isAdmin = currentUser?.role == UserRole.ADMIN
    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val productImageMode by viewModel.productImageMode.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var stockFilter by remember { mutableStateOf("ALL") } // "ALL", "IN_STOCK", "OUT_OF_STOCK", "LOW_STOCK"
    var showAddProductDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var selectedProductForAdjust by remember { mutableStateOf<Product?>(null) }
    var productToSafeDelete by remember { mutableStateOf<Product?>(null) }
    var safeDeleteReasonInput by remember { mutableStateOf("") }

    // Quick Sale state
    var selectedProductForSale by remember { mutableStateOf<Product?>(null) }
    var showOutOfStockWarningDialog by remember { mutableStateOf<Product?>(null) }
    var quickSaleSuccessData by remember { mutableStateOf<Pair<Sale, List<SaleItem>>?>(null) }

    val filtered = remember(products, searchQuery, stockFilter) {
        products.filter { p ->
            val matchesSearch = p.name.contains(searchQuery, ignoreCase = true) ||
                    p.sku.contains(searchQuery, ignoreCase = true) ||
                    p.category.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (stockFilter) {
                "IN_STOCK" -> p.currentStock > 0.0
                "OUT_OF_STOCK" -> p.currentStock <= 0.0
                "LOW_STOCK" -> p.currentStock > 0.0 && p.currentStock <= p.alertStockLevel
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAdmin) "পণ্য ও স্টক ব্যবস্থাপনা" else "পণ্যের মজুদ ও সরাসরি বিক্রি", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { showAddProductDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "নতুন পণ্য", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryTeal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { showAddProductDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "নতুন পণ্য যোগ") },
                    text = { Text("নতুন পণ্য যোগ", fontWeight = FontWeight.Bold) },
                    containerColor = SecondaryTeal,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("admin_add_product_fab")
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("পণ্যের নাম বা কোড (SKU) দিয়ে খুঁজুন...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "মুছুন")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Stock Quick Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = stockFilter == "ALL",
                    onClick = { stockFilter = "ALL" },
                    label = { Text("সকল পণ্য (${products.size})") }
                )
                FilterChip(
                    selected = stockFilter == "IN_STOCK",
                    onClick = { stockFilter = "IN_STOCK" },
                    label = { Text("মজুদ আছে (${products.count { it.currentStock > 0.0 }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFE8F5E9),
                        selectedLabelColor = Color(0xFF2E7D32)
                    )
                )
                FilterChip(
                    selected = stockFilter == "OUT_OF_STOCK",
                    onClick = { stockFilter = "OUT_OF_STOCK" },
                    label = { Text("স্টক নেই (${products.count { it.currentStock <= 0.0 }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFEBEE),
                        selectedLabelColor = AccentRed
                    )
                )
                FilterChip(
                    selected = stockFilter == "LOW_STOCK",
                    onClick = { stockFilter = "LOW_STOCK" },
                    label = { Text("স্টক কম (${products.count { it.currentStock > 0.0 && it.currentStock <= it.alertStockLevel }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFF3E0),
                        selectedLabelColor = Color(0xFFE65100)
                    )
                )
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "কোনো পণ্য পাওয়া যায়নি" else "স্টক তালিকা খালি",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered) { p ->
                        val isOutOfStock = p.currentStock <= 0.0
                        val isLowStock = !isOutOfStock && p.currentStock <= p.alertStockLevel

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isOutOfStock) {
                                        showOutOfStockWarningDialog = p
                                    } else {
                                        selectedProductForSale = p
                                    }
                                }
                                .testTag("product_card_${p.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isOutOfStock -> Color(0xFFFFEBEE)
                                    isLowStock -> Color(0xFFFFF3E0)
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Product Name, Thumbnail, Code, and Stock Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProductThumbnail(
                                        imageUri = p.imageUri,
                                        category = p.category,
                                        imageSource = p.imageSource,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false))
                                            if (p.isTestData || p.imageSource == "AI_GENERATED" || p.name.contains("[টেস্ট]", ignoreCase = true)) {
                                                Surface(
                                                    color = Color(0xFFF3E5F5),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (p.imageSource == "AI_GENERATED") "🤖 AI" else "🧪 টেস্ট",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF7B1FA2),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            "কোড: ${p.sku} • ক্যাটাগরি: ${p.category} • একক: ${p.unit}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Surface(
                                        color = when {
                                            isOutOfStock -> AccentRed
                                            isLowStock -> Color(0xFFE65100)
                                            else -> Color(0xFF2E7D32)
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                isOutOfStock -> "স্টক নেই (০ ${p.unit})"
                                                isLowStock -> "${p.currentStock} ${p.unit} (কম)"
                                                else -> "${p.currentStock} ${p.unit}"
                                            },
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Pricing and Quick Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "বিক্রয়মূল্য: ${BengaliFormatter.formatCurrency(p.sellingPrice)}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = PrimaryNavy
                                        )
                                        if (isAdmin) {
                                            Text(
                                                "ক্রয়মূল্য: ${BengaliFormatter.formatCurrency(p.purchasePrice)}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Quick Sale Button
                                        Button(
                                            onClick = {
                                                if (isOutOfStock) {
                                                    showOutOfStockWarningDialog = p
                                                } else {
                                                    selectedProductForSale = p
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isOutOfStock) AccentRed else Color(0xFF2E7D32)
                                            ),
                                            modifier = Modifier.testTag("btn_quick_sale_${p.id}")
                                        ) {
                                            Icon(
                                                if (isOutOfStock) Icons.Default.Block else Icons.Default.ShoppingCart,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isOutOfStock) "স্টক নেই" else "বিক্রি করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (isAdmin) {
                                            OutlinedButton(
                                                onClick = { selectedProductForAdjust = p },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.testTag("btn_adjust_stock_${p.id}")
                                            ) {
                                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("স্টক", fontSize = 11.sp)
                                            }

                                            Button(
                                                onClick = { productToEdit = p },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                                modifier = Modifier.testTag("btn_edit_product_${p.id}")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("এডিট", fontSize = 11.sp)
                                            }

                                            IconButton(
                                                onClick = {
                                                    productToSafeDelete = p
                                                    safeDeleteReasonInput = "পণ্য তালিকা থেকে অপসারণ"
                                                },
                                                modifier = Modifier.size(32.dp).testTag("btn_delete_product_${p.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "রিসাইকেল বিনে পাঠান",
                                                    tint = AccentRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isOutOfStock) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "⚠️ সতর্কতা: পণ্যটির কোনো স্টক অবশিষ্ট নেই! বিক্রি করার জন্য আগে স্টক যোগ করতে হবে।",
                                        fontSize = 11.sp,
                                        color = AccentRed,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else if (isLowStock) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "সতর্কতা: স্টক কমে গেছে (সতর্কতা সীমা: ${p.alertStockLevel} ${p.unit})",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog: Add New Product (Admin only)
    if (showAddProductDialog) {
        var name by remember { mutableStateOf("") }
        var sku by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(BusinessRepository.CLOTHING_CATEGORIES.first()) }
        var purchasePriceText by remember { mutableStateOf("") }
        val calculatedSellingPrice = remember(purchasePriceText) {
            val cost = purchasePriceText.toDoubleOrNull()
            if (cost != null && cost > 0.0) {
                val calculated = BusinessRepository.calculateSellingPrice(cost)
                if (calculated % 1.0 == 0.0) {
                    calculated.toLong().toString()
                } else {
                    String.format(java.util.Locale.US, "%.2f", calculated)
                }
            } else {
                ""
            }
        }
        var stockText by remember { mutableStateOf("") }
        var alertLevelText by remember { mutableStateOf("5") }
        var unit by remember { mutableStateOf("পিস") }
        var imageUri by remember { mutableStateOf<String?>(null) }
        var imageSource by remember { mutableStateOf("NONE") }
        var imageSourceInfo by remember { mutableStateOf("") }
        var isTestData by remember { mutableStateOf(false) }
        var testTag by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddProductDialog = false },
            title = { Text("নতুন পণ্য ডাটাবেসে যোগ করুন (Admin)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (errorMessage.isNotBlank()) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    // Product Image Selector
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
                            if (src == "AI_GENERATED") {
                                isTestData = true
                                testTag = "GEMINI_AI"
                            }
                        },
                        onTriggerAiGeneration = {
                            viewModel.generateAiProductImage(
                                title = name,
                                category = category,
                                sku = sku,
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
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("পণ্যের নাম *") },
                        placeholder = { Text("যেমন: কটন ফরমাল শার্ট (L)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_add_product_name")
                    )
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("SKU / প্রোডাক্ট কোড") },
                        placeholder = { Text("যেমন: SHT-FORMAL-01") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("admin_add_product_sku")
                    )

                    // Product Category (12 Clothing Categories)
                    Column {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("ক্যাটাগরি *") },
                            placeholder = { Text("পোশাকের ক্যাটাগরি নির্বাচন করুন") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("admin_add_product_category")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BusinessRepository.CLOTHING_CATEGORIES.forEach { cat ->
                                SuggestionChip(
                                    onClick = { category = cat },
                                    label = { Text(cat, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Unit Selection (8 Clothing Units)
                    Column {
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("একক (Unit) *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("admin_add_product_unit")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BusinessRepository.CLOTHING_UNITS.forEach { u ->
                                SuggestionChip(
                                    onClick = { unit = u },
                                    label = { Text(u, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = purchasePriceText,
                            onValueChange = { purchasePriceText = it },
                            label = { Text("ক্রয়মূল্য / Cost Price (৳) *") },
                            placeholder = { Text("0.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("admin_add_purchase_price")
                        )
                        OutlinedTextField(
                            value = calculatedSellingPrice,
                            onValueChange = { /* Read-only auto calculated */ },
                            readOnly = true,
                            label = { Text("বিক্রয়মূল্য (স্বয়ংক্রিয় ৭০% লাভ) *") },
                            placeholder = { Text("ক্রয়মূল্য দিলে হিসাব হবে") },
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
                            supportingText = {
                                if (calculatedSellingPrice.isNotBlank()) {
                                    Text("হিসাব: ক্রয়মূল্য × ১.৭০ (৭০% লাভ)", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                } else {
                                    Text("ক্রয়মূল্য লিখলে স্বয়ংক্রিয় পূরণ হবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("admin_add_selling_price")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = stockText, onValueChange = { stockText = it }, label = { Text("বর্তমান স্টক *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f).testTag("admin_add_stock"))
                        OutlinedTextField(value = alertLevelText, onValueChange = { alertLevelText = it }, label = { Text("সতর্কতা সীমা") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f).testTag("admin_add_alert_level"))
                    }

                    // Testing Data Tag Option
                    Surface(
                        color = if (isTestData) Color(0xFFF3E5F5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isTestData) "🧪 পরীক্ষামূলক পণ্য (Testing Data)" else "পরীক্ষামূলক পণ্য হিসেবে ট্যাগ করুন",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = if (isTestData) Color(0xFF7B1FA2) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Google Gemini দিয়ে টেস্ট পণ্য তৈরি করলে বা পরীক্ষামূলক পণ্যের জন্য এটি অন রাখুন।",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = isTestData,
                                onCheckedChange = { checked ->
                                    isTestData = checked
                                    if (checked && testTag.isBlank()) testTag = if (imageSource == "AI_GENERATED") "GEMINI_AI" else "MANUAL_TEST"
                                },
                                modifier = Modifier.testTag("admin_add_switch_test_data")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val buy = purchasePriceText.toDoubleOrNull() ?: 0.0
                        val sell = calculatedSellingPrice.toDoubleOrNull() ?: BusinessRepository.calculateSellingPrice(buy)
                        val st = stockText.toDoubleOrNull() ?: 0.0
                        val alert = alertLevelText.toDoubleOrNull() ?: 5.0

                        if (name.isBlank()) {
                            errorMessage = "পণ্যের নাম খালি রাখা যাবে না!"
                            return@Button
                        }
                        if (category.isBlank()) {
                            errorMessage = "ক্যাটাগরি খালি রাখা যাবে না!"
                            return@Button
                        }
                        if (unit.isBlank()) {
                            errorMessage = "একক খালি রাখা যাবে না!"
                            return@Button
                        }
                        if (buy <= 0) {
                            errorMessage = "সঠিক ক্রয়মূল্য লিখুন!"
                            return@Button
                        }
                        if (sell <= 0) {
                            errorMessage = "সঠিক বিক্রয়মূল্য হিসাব করা যায়নি!"
                            return@Button
                        }

                        val newProd = Product(
                            id = "p_${System.currentTimeMillis()}",
                            name = name.trim(),
                            sku = if (sku.isNotBlank()) sku.trim() else "SKU-${System.currentTimeMillis().toString().takeLast(4)}",
                            category = category.trim(),
                            purchasePrice = buy,
                            sellingPrice = sell,
                            currentStock = st,
                            alertStockLevel = alert,
                            unit = unit.trim(),
                            imageUri = imageUri,
                            imageSource = imageSource,
                            imageSourceInfo = imageSourceInfo,
                            isTestData = isTestData,
                            testTag = if (isTestData) (if (testTag.isNotBlank()) testTag else if (imageSource == "AI_GENERATED") "GEMINI_AI" else "MANUAL_TEST") else ""
                        )
                        viewModel.saveProduct(
                            product = newProd,
                            isNew = true,
                            reason = "এডমিন কর্তৃক নতুন পণ্য যোগ" + if (isTestData) " [টেস্টিং ডেটা]" else "",
                            onSuccess = { showAddProductDialog = false },
                            onError = { errorMessage = it }
                        )
                    },
                    modifier = Modifier.testTag("admin_add_product_save_btn")
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddProductDialog = false }) { Text("বাতিল") }
            }
        )
    }

    // Modal Dialog: Edit Product with Validation, Confirmation & Reason (Admin only)
    if (productToEdit != null) {
        val prod = productToEdit!!
        var editName by remember { mutableStateOf(prod.name) }
        var editSku by remember { mutableStateOf(prod.sku) }
        var editCategory by remember { mutableStateOf(prod.category) }
        var editImageUri by remember { mutableStateOf(prod.imageUri) }
        var editImageSource by remember { mutableStateOf(prod.imageSource) }
        var editImageSourceInfo by remember { mutableStateOf(prod.imageSourceInfo) }
        var editIsTestData by remember { mutableStateOf(prod.isTestData) }
        var editTestTag by remember { mutableStateOf(prod.testTag) }
        var editPurchasePriceText by remember {
            mutableStateOf(
                if (prod.purchasePrice % 1.0 == 0.0) prod.purchasePrice.toLong().toString()
                else String.format(java.util.Locale.US, "%.2f", prod.purchasePrice)
            )
        }
        val calculatedEditSellingPrice = remember(editPurchasePriceText) {
            val cost = editPurchasePriceText.toDoubleOrNull()
            if (cost != null && cost > 0.0) {
                val calculated = BusinessRepository.calculateSellingPrice(cost)
                if (calculated % 1.0 == 0.0) {
                    calculated.toLong().toString()
                } else {
                    String.format(java.util.Locale.US, "%.2f", calculated)
                }
            } else {
                ""
            }
        }
        var editAlertLevelText by remember { mutableStateOf(prod.alertStockLevel.toString()) }
        var editUnit by remember { mutableStateOf(prod.unit) }
        var editReason by remember { mutableStateOf("বাজার দর পরিবর্তন") }
        var showConfirmDialog by remember { mutableStateOf(false) }
        var editErrorMessage by remember { mutableStateOf("") }

        if (!showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { productToEdit = null },
                title = { Text("পণ্য সম্পাদনা (Edit Product)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("আইডি: ${prod.id} (পূর্ববর্তী চালানের মূল্য অপরিবর্তিত থাকবে)", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        if (editErrorMessage.isNotBlank()) {
                            Text(editErrorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }

                        // Product Image Selector for Edit Dialog
                        ProductImageSelector(
                            imageUri = editImageUri,
                            imageSource = editImageSource,
                            imageSourceInfo = editImageSourceInfo,
                            category = editCategory,
                            productName = editName,
                            isAutoMode = productImageMode == "AUTO",
                            onImageSelected = { uri, src, info ->
                                editImageUri = uri
                                editImageSource = src
                                editImageSourceInfo = info
                                if (src == "AI_GENERATED") {
                                    editIsTestData = true
                                    editTestTag = "GEMINI_AI"
                                }
                            },
                            onTriggerAiGeneration = {
                                viewModel.generateAiProductImage(
                                    title = editName,
                                    category = editCategory,
                                    sku = editSku,
                                    unit = editUnit
                                )
                            },
                            onSearchOnlineImages = { q ->
                                viewModel.searchOnlineImages(q)
                            },
                            onDownloadOnlineImage = { candidate ->
                                viewModel.downloadOnlineImage(candidate)
                            }
                        )
                        OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("পণ্যের নাম *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("admin_edit_product_name"))
                        OutlinedTextField(value = editSku, onValueChange = { editSku = it }, label = { Text("SKU / কোড") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("admin_edit_product_sku"))

                        // Product Category (12 Clothing Categories)
                        Column {
                            OutlinedTextField(
                                value = editCategory,
                                onValueChange = { editCategory = it },
                                label = { Text("ক্যাটাগরি *") },
                                placeholder = { Text("পোশাকের ক্যাটাগরি নির্বাচন করুন") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_edit_product_category")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BusinessRepository.CLOTHING_CATEGORIES.forEach { cat ->
                                    SuggestionChip(
                                        onClick = { editCategory = cat },
                                        label = { Text(cat, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Unit Selection (8 Clothing Units)
                        Column {
                            OutlinedTextField(
                                value = editUnit,
                                onValueChange = { editUnit = it },
                                label = { Text("একক (Unit) *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_edit_product_unit")
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BusinessRepository.CLOTHING_UNITS.forEach { u ->
                                    SuggestionChip(
                                        onClick = { editUnit = u },
                                        label = { Text(u, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editPurchasePriceText,
                                onValueChange = { editPurchasePriceText = it },
                                label = { Text("ক্রয়মূল্য / Cost Price (৳) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("admin_edit_purchase_price")
                            )
                            OutlinedTextField(
                                value = calculatedEditSellingPrice,
                                onValueChange = { /* Read-only auto calculated */ },
                                readOnly = true,
                                label = { Text("বিক্রয়মূল্য (স্বয়ংক্রিয় ৭০% লাভ) *") },
                                placeholder = { Text("ক্রয়মূল্য দিলে হিসাব হবে") },
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
                                supportingText = {
                                    if (calculatedEditSellingPrice.isNotBlank()) {
                                        Text("হিসাব: ক্রয়মূল্য × ১.৭০ (৭০% লাভ)", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                    } else {
                                        Text("ক্রয়মূল্য লিখলে স্বয়ংক্রিয় পূরণ হবে", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("admin_edit_selling_price")
                            )
                        }
                        OutlinedTextField(value = editAlertLevelText, onValueChange = { editAlertLevelText = it }, label = { Text("সতর্কতা স্টক সীমা") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth().testTag("admin_edit_alert_level"))
                        OutlinedTextField(value = editReason, onValueChange = { editReason = it }, label = { Text("মূল্য/তথ্য পরিবর্তনের কারণ *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("admin_edit_reason"))

                        // Testing Data Toggle for Edit Dialog
                        Surface(
                            color = if (editIsTestData) Color(0xFFF3E5F5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (editIsTestData) "🧪 পরীক্ষামূলক পণ্য (Testing Data)" else "পরীক্ষামূলক পণ্য হিসেবে ট্যাগ করুন",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = if (editIsTestData) Color(0xFF7B1FA2) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "ডেটা সুরক্ষা কেন্দ্রে আলাদা রাখা ও নিরাপদে পরীক্ষা করার সুবিধা",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Switch(
                                    checked = editIsTestData,
                                    onCheckedChange = { checked ->
                                        editIsTestData = checked
                                        if (checked && editTestTag.isBlank()) editTestTag = if (editImageSource == "AI_GENERATED") "GEMINI_AI" else "MANUAL_TEST"
                                    },
                                    modifier = Modifier.testTag("admin_edit_switch_test_data")
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val buy = editPurchasePriceText.toDoubleOrNull() ?: -1.0
                            val sell = calculatedEditSellingPrice.toDoubleOrNull() ?: -1.0
                            if (editName.isBlank()) {
                                editErrorMessage = "পণ্যের নাম খালি হতে পারে না!"
                                return@Button
                            }
                            if (editCategory.isBlank()) {
                                editErrorMessage = "ক্যাটাগরি খালি হতে পারে না!"
                                return@Button
                            }
                            if (editUnit.isBlank()) {
                                editErrorMessage = "একক খালি হতে পারে না!"
                                return@Button
                            }
                            if (buy <= 0 || sell <= 0) {
                                editErrorMessage = "সঠিক ক্রয়মূল্য লিখুন (বিক্রয়মূল্য স্বয়ংক্রিয়ভাবে হিসাব হবে)!"
                                return@Button
                            }
                            if (editReason.isBlank()) {
                                editErrorMessage = "অনুগ্রহ করে পরিবর্তনের কারণ লিখুন!"
                                return@Button
                            }
                            showConfirmDialog = true
                        },
                        modifier = Modifier.testTag("admin_edit_product_save_btn")
                    ) {
                        Text("পর্যালোচনা ও সেভ")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { productToEdit = null }) { Text("বাতিল") }
                }
            )
        } else {
            // Confirmation Screen for Edit Product
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("মূল্য ও তথ্য পরিবর্তন নিশ্চিতকরণ", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("আপনি কি নিশ্চিতভাবে এই পরিবর্তন সেভ করতে চান?", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• পণ্য: $editName")
                        Text("• ক্যাটাগরি: ${prod.category} → $editCategory")
                        Text("• একক: ${prod.unit} → $editUnit")
                        Text("• ক্রয়মূল্য: ৳${prod.purchasePrice} → ৳$editPurchasePriceText")
                        Text("• বিক্রয়মূল্য: ৳${prod.sellingPrice} → ৳$calculatedEditSellingPrice (স্বয়ংক্রিয় ৭০% লাভ)")
                        Text("• কারণ: $editReason", color = AccentAmber, fontWeight = FontWeight.Medium)
                        if (editIsTestData) {
                            Text("• টেস্টিং স্ট্যাটাস: পরীক্ষামূলক পণ্য (Testing Data) হিসেবে চিহ্নিত", color = Color(0xFF7B1FA2), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "নোট: ইতিপূর্বে হওয়া বিক্রয় ইনভয়েস, রসিদ ও লাভের হিসাব সম্পূর্ণ অপরিবর্তিত থাকবে। ঐতিহাসিক ডেটা সুরক্ষিত রাখা হবে।",
                            fontSize = 11.sp,
                            color = AccentGreen
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val buy = editPurchasePriceText.toDoubleOrNull() ?: prod.purchasePrice
                            val sell = calculatedEditSellingPrice.toDoubleOrNull() ?: BusinessRepository.calculateSellingPrice(buy)
                            val alert = editAlertLevelText.toDoubleOrNull() ?: prod.alertStockLevel

                            val updatedProduct = prod.copy(
                                name = editName.trim(),
                                sku = editSku.trim(),
                                category = editCategory.trim(),
                                unit = editUnit.trim(),
                                purchasePrice = buy,
                                sellingPrice = sell,
                                alertStockLevel = alert,
                                updatedAt = System.currentTimeMillis(),
                                imageUri = editImageUri,
                                imageSource = editImageSource,
                                imageSourceInfo = editImageSourceInfo,
                                isTestData = editIsTestData,
                                testTag = if (editIsTestData) (if (editTestTag.isNotBlank()) editTestTag else if (editImageSource == "AI_GENERATED") "GEMINI_AI" else "MANUAL_TEST") else ""
                            )

                            viewModel.saveProduct(
                                product = updatedProduct,
                                isNew = false,
                                reason = editReason.trim(),
                                onSuccess = {
                                    showConfirmDialog = false
                                    productToEdit = null
                                },
                                onError = {
                                    editErrorMessage = it
                                    showConfirmDialog = false
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.testTag("admin_edit_confirm_btn")
                    ) {
                        Text("হ্যাঁ, সেভ করুন")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) { Text("ফিরে যান") }
                }
            )
        }
    }

    // Modal Dialog: Adjust Stock Manually (Admin only)
    if (selectedProductForAdjust != null) {
        val prod = selectedProductForAdjust!!
        var deltaText by remember { mutableStateOf("") }
        var isAddition by remember { mutableStateOf(true) }
        var reason by remember { mutableStateOf("ক্রয় / চালান গ্রহণ") }

        AlertDialog(
            onDismissRequest = { selectedProductForAdjust = null },
            title = { Text("স্টক সমন্বয়: ${prod.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("বর্তমান মজুদ স্টক: ${prod.currentStock} ${prod.unit}", fontWeight = FontWeight.Bold, color = PrimaryNavy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isAddition,
                            onClick = { isAddition = true },
                            label = { Text("+ নতুন স্টক যোগ") }
                        )
                        FilterChip(
                            selected = !isAddition,
                            onClick = { isAddition = false },
                            label = { Text("- স্টক হ্রাস / ক্ষতি") }
                        )
                    }
                    OutlinedTextField(
                        value = deltaText,
                        onValueChange = { deltaText = it },
                        label = { Text("পরিমাণ (${prod.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("সমন্বয়ের কারণ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = deltaText.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            val finalDelta = if (isAddition) amt else -amt
                            viewModel.adjustProductStock(prod.id, finalDelta, reason) {
                                selectedProductForAdjust = null
                            }
                        }
                    }
                ) {
                    Text("নিশ্চিত করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedProductForAdjust = null }) { Text("বাতিল") }
            }
        )
    }

    // Modal Dialog: Out of Stock Warning Dialog
    if (showOutOfStockWarningDialog != null) {
        val prod = showOutOfStockWarningDialog!!
        AlertDialog(
            onDismissRequest = { showOutOfStockWarningDialog = null },
            icon = { Icon(Icons.Default.Block, contentDescription = null, tint = AccentRed, modifier = Modifier.size(36.dp)) },
            title = { Text("পণ্যটির স্টক নেই!", fontWeight = FontWeight.Bold, color = AccentRed) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("পণ্য: ${prod.name}")
                    Text("কোড / SKU: ${prod.sku} • ক্যাটাগরি: ${prod.category}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Text("বর্তমান মজুদ স্টক: ০ ${prod.unit}", fontWeight = FontWeight.Bold, color = AccentRed)
                    Text(
                        "স্টক শূন্য থাকায় এই পণ্যটি এখন বিক্রি করা সম্ভব নয়। বিক্রি সম্পন্ন করার জন্য প্রথমে পর্যাপ্ত স্টক যোগ করতে হবে।",
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                if (isAdmin) {
                    Button(
                        onClick = {
                            val toAdjust = prod
                            showOutOfStockWarningDialog = null
                            selectedProductForAdjust = toAdjust
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("স্টক যোগ করুন")
                    }
                } else {
                    Button(onClick = { showOutOfStockWarningDialog = null }) {
                        Text("ঠিক আছে")
                    }
                }
            },
            dismissButton = {
                if (isAdmin) {
                    TextButton(onClick = { showOutOfStockWarningDialog = null }) {
                        Text("বন্ধ করুন")
                    }
                }
            }
        )
    }

    // Modal Dialog: Quick Sale Dialog from Stock Screen
    if (selectedProductForSale != null) {
        val prod = selectedProductForSale!!
        var invoiceNumber by remember(prod.id) { mutableStateOf("") }
        LaunchedEffect(prod.id) {
            invoiceNumber = try {
                viewModel.repository.getNextInvoiceNumber()
            } catch (e: Exception) {
                "INV-${System.currentTimeMillis().toString().takeLast(6)}"
            }
        }

        // Customer selection
        var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
        var showCustomerPicker by remember { mutableStateOf(false) }
        var showAddCustomerDialog by remember { mutableStateOf(false) }
        var customerSearchQuery by remember { mutableStateOf("") }

        // Quick add customer fields
        var newCustName by remember { mutableStateOf("") }
        var newCustPhone by remember { mutableStateOf("") }
        var newCustAddress by remember { mutableStateOf("") }

        // Sale input fields
        var enteredQty by remember(prod.id) { mutableStateOf("1") }
        var customPrice by remember(prod.id) {
            mutableStateOf(
                if (prod.sellingPrice % 1.0 == 0.0) prod.sellingPrice.toLong().toString()
                else String.format(java.util.Locale.US, "%.2f", prod.sellingPrice)
            )
        }
        var discountText by remember { mutableStateOf("0") }
        var paidAmountText by remember { mutableStateOf("") }
        var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
        var note by remember { mutableStateOf("স্টক তালিকা থেকে সরাসরি বিক্রি") }

        // Submission state & confirmation
        var isSubmitting by remember { mutableStateOf(false) }
        var saleErrorMessage by remember { mutableStateOf("") }
        var showSummaryStep by remember { mutableStateOf(false) }

        // Live math
        val qty = enteredQty.toDoubleOrNull() ?: 0.0
        val unitPrice = customPrice.toDoubleOrNull() ?: prod.sellingPrice
        val subtotal = (qty * unitPrice).coerceAtLeast(0.0)
        val discount = discountText.toDoubleOrNull() ?: 0.0
        val totalAmount = (subtotal - discount).coerceAtLeast(0.0)
        val paidAmount = if (paidAmountText.isBlank()) totalAmount else (paidAmountText.toDoubleOrNull() ?: totalAmount)
        val dueAmount = (totalAmount - paidAmount).coerceAtLeast(0.0)

        val isQtyExceedingStock = qty > prod.currentStock
        val isQtyInvalid = qty <= 0.0
        val isPriceInvalid = unitPrice <= 0.0

        if (!showSummaryStep) {
            AlertDialog(
                onDismissRequest = {
                    if (!isSubmitting) selectedProductForSale = null
                },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("স্টক থেকে তাৎক্ষণিক বিক্রি", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Surface(
                            color = PrimaryNavy.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "মজুদ: ${prod.currentStock} ${prod.unit}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Product Summary Box
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "কোড: ${prod.sku} • ক্যাটাগরি: ${prod.category} • একক: ${prod.unit}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    "বর্তমান বিক্রয়মূল্য: ${BengaliFormatter.formatCurrency(prod.sellingPrice)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryNavy
                                )
                            }
                        }

                        if (saleErrorMessage.isNotBlank()) {
                            Text(saleErrorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        // Customer Selection Section
                        Column {
                            Text("১. কাস্টমার নির্বাচন *", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (selectedCustomer != null) {
                                Surface(
                                    color = SecondaryTeal.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(selectedCustomer!!.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("মোবাইল: ${selectedCustomer!!.phone} | বকেয়া: ৳${selectedCustomer!!.currentDue}", fontSize = 11.sp)
                                        }
                                        TextButton(onClick = { showCustomerPicker = true }) {
                                            Text("পরিবর্তন")
                                        }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showCustomerPicker = true },
                                    modifier = Modifier.fillMaxWidth().testTag("btn_select_customer_for_quick_sale")
                                ) {
                                    Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("কাস্টমার নির্বাচন বা যোগ করুন")
                                }
                            }
                        }

                        // Quantity Section with Validation
                        Column {
                            Text("২. বিক্রির পরিমাণ (${prod.unit}) *", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = enteredQty,
                                    onValueChange = { enteredQty = it },
                                    label = { Text("পরিমাণ") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    isError = isQtyExceedingStock || isQtyInvalid,
                                    modifier = Modifier.weight(1f).testTag("input_quick_sale_qty")
                                )

                                Button(
                                    onClick = { enteredQty = prod.currentStock.toString() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal)
                                ) {
                                    Text("সর্বোচ্চ", fontSize = 11.sp)
                                }
                            }

                            if (isQtyExceedingStock) {
                                Text(
                                    text = "⚠️ অপর্যাপ্ত স্টক! সর্বোচ্চ মজুদ: ${prod.currentStock} ${prod.unit}",
                                    color = AccentRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (isQtyInvalid) {
                                Text(
                                    text = "সঠিক পরিমাণ লিখুন (০ এর বেশি)",
                                    color = AccentRed,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Unit Selling Price Section
                        Column {
                            Text("৩. বিক্রয়মূল্য প্রতি ${prod.unit} (টাকা) *", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = customPrice,
                                onValueChange = { customPrice = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_quick_sale_price")
                            )
                        }

                        // Discount & Payment Section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = discountText,
                                onValueChange = { discountText = it },
                                label = { Text("ছাড় (৳)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = paidAmountText,
                                onValueChange = { paidAmountText = it },
                                label = { Text("নগদ জমা (৳)") },
                                placeholder = { Text("মোট বিল") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Payment Method
                        Column {
                            Text("৪. পেমেন্ট মাধ্যম", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
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
                                        label = { Text(method.name, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Live Math Preview
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("উপমোট (Subtotal):", fontSize = 12.sp)
                                    Text(BengaliFormatter.formatCurrency(subtotal), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (discount > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("ছাড়:", fontSize = 12.sp, color = AccentRed)
                                        Text("-${BengaliFormatter.formatCurrency(discount)}", fontSize = 12.sp, color = AccentRed)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("সর্বমোট বিল:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(BengaliFormatter.formatCurrency(totalAmount), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryNavy)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("নগদ আদায়:", fontSize = 12.sp, color = Color(0xFF2E7D32))
                                    Text(BengaliFormatter.formatCurrency(paidAmount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                                if (dueAmount > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("অবশিষ্ট বকেয়া:", fontSize = 12.sp, color = AccentRed)
                                        Text(BengaliFormatter.formatCurrency(dueAmount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (selectedCustomer == null) {
                                saleErrorMessage = "দয়া করে কাস্টমার নির্বাচন করুন!"
                                return@Button
                            }
                            if (isQtyExceedingStock) {
                                saleErrorMessage = "পর্যাপ্ত স্টক নেই! সর্বোচ্চ মজুদ: ${prod.currentStock} ${prod.unit}"
                                return@Button
                            }
                            if (isQtyInvalid) {
                                saleErrorMessage = "সঠিক পরিমাণ লিখুন (০ এর বেশি)"
                                return@Button
                            }
                            if (isPriceInvalid) {
                                saleErrorMessage = "সঠিক বিক্রয়মূল্য লিখুন"
                                return@Button
                            }
                            saleErrorMessage = ""
                            showSummaryStep = true
                        },
                        enabled = !isQtyExceedingStock && !isQtyInvalid && !isPriceInvalid,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.testTag("btn_proceed_quick_sale")
                    ) {
                        Text("পরবর্তী ধাপ (পর্যালোচনা)")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedProductForSale = null }) {
                        Text("বাতিল")
                    }
                }
            )
        } else {
            // Confirmation Screen Before Final Execution
            AlertDialog(
                onDismissRequest = {
                    if (!isSubmitting) showSummaryStep = false
                },
                title = { Text("বিক্রি নিশ্চিতকরণ ও রসিদ", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("ইনভয়েস নম্বর: $invoiceNumber", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        Divider()

                        Text("• পণ্য: ${prod.name}")
                        Text("• কোড (SKU): ${prod.sku}")
                        Text("• বিক্রির পরিমাণ: $qty ${prod.unit}")
                        Text("• বিক্রয়ের পর অবশিষ্ট স্টক: ${(prod.currentStock - qty).coerceAtLeast(0.0)} ${prod.unit}", color = SecondaryTeal, fontWeight = FontWeight.SemiBold)
                        Text("• একক দর: ৳$unitPrice")
                        Text("• সর্বমোট মূল্য: ৳$totalAmount", fontWeight = FontWeight.Bold)
                        Text("• নগদ পরিশোধ: ৳$paidAmount", color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                        if (dueAmount > 0) {
                            Text("• বকেয়া (Due): ৳$dueAmount", color = AccentRed, fontWeight = FontWeight.Bold)
                        }
                        Text("• পেমেন্ট মাধ্যম: ${paymentMethod.name}")
                        Text("• ক্রেতা/কাস্টমার: ${selectedCustomer?.name} (${selectedCustomer?.phone})")

                        Divider()
                        Text(
                            "বিক্রি সম্পন্ন করলে স্টক থেকে স্বয়ংক্রিয়ভাবে $qty ${prod.unit} কমে যাবে এবং বিক্রির সম্পূর্ণ লেজার ও হিসাব আপডেট হবে।",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        if (saleErrorMessage.isNotBlank()) {
                            Text(saleErrorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val user = currentUser ?: return@Button
                            val cust = selectedCustomer ?: return@Button

                            isSubmitting = true
                            saleErrorMessage = ""

                            val saleItem = SaleItem(
                                invoiceNumber = invoiceNumber,
                                productId = prod.id,
                                productName = prod.name,
                                quantity = qty,
                                unitPrice = unitPrice,
                                purchasePriceAtSale = prod.purchasePrice,
                                totalPrice = qty * unitPrice
                            )

                            val sale = Sale(
                                invoiceNumber = invoiceNumber,
                                customerId = cust.id,
                                customerName = cust.name,
                                customerPhone = cust.phone,
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
                                sale = sale,
                                items = listOf(saleItem),
                                onSuccess = { inv ->
                                    isSubmitting = false
                                    selectedProductForSale = null
                                    showSummaryStep = false
                                    quickSaleSuccessData = Pair(sale, listOf(saleItem))
                                },
                                onError = { err ->
                                    isSubmitting = false
                                    saleErrorMessage = err
                                }
                            )
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.testTag("btn_confirm_quick_sale")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("বিক্রি নিশ্চিত করুন")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSummaryStep = false },
                        enabled = !isSubmitting
                    ) {
                        Text("ফিরে যান")
                    }
                }
            )
        }

        // Customer Picker Dialog
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
                            placeholder = { Text("নাম বা ফোন নম্বর দিয়ে খুঁজুন...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val filteredCusts = remember(customers, customerSearchQuery) {
                            customers.filter {
                                it.name.contains(customerSearchQuery, ignoreCase = true) ||
                                        it.phone.contains(customerSearchQuery)
                            }
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                            items(filteredCusts) { c ->
                                ListItem(
                                    headlineContent = { Text(c.name, fontWeight = FontWeight.Bold) },
                                    supportingContent = { Text("মোবাইল: ${c.phone} | বকেয়া: ৳${c.currentDue}") },
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

        // Quick Add Customer Dialog
        if (showAddCustomerDialog) {
            AlertDialog(
                onDismissRequest = { showAddCustomerDialog = false },
                title = { Text("নতুন কাস্টমার যোগ", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newCustName,
                            onValueChange = { newCustName = it },
                            label = { Text("কাস্টমারের নাম *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newCustPhone,
                            onValueChange = { newCustPhone = it },
                            label = { Text("মোবাইল নম্বর *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newCustAddress,
                            onValueChange = { newCustAddress = it },
                            label = { Text("ঠিকানা (ঐচ্ছিক)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newCustName.isNotBlank() && newCustPhone.isNotBlank()) {
                                val newCust = Customer(
                                    id = "c_${System.currentTimeMillis()}",
                                    name = newCustName.trim(),
                                    phone = newCustPhone.trim(),
                                    address = newCustAddress.trim()
                                )
                                viewModel.saveCustomer(newCust) {
                                    selectedCustomer = newCust
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

        // Safe Delete Dialog (Soft Delete to Recycle Bin with 30-day recovery)
        if (productToSafeDelete != null) {
            val prod = productToSafeDelete!!
            AlertDialog(
                onDismissRequest = { productToSafeDelete = null },
                icon = {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AccentRed, modifier = Modifier.size(36.dp))
                },
                title = {
                    Text("পণ্য নিরাপদ অপসারণ (Soft Delete)", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "আপনি কি '${prod.name}' পণ্যটি তালিকা থেকে সরিয়ে রিসাইকেল বিনে স্থানান্তর করতে চান?",
                            fontSize = 13.sp
                        )
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🛡️ ৩০ দিনের রিকভারি: পণ্যটি অবিলম্বে ধ্বংস হবে না। পরবর্তী ৩০ দিনের মধ্যে 'ডেটা সুরক্ষা কেন্দ্র' থেকে যেকোনো সময় পুনরুদ্ধার করতে পারবেন।",
                                fontSize = 12.sp,
                                color = Color(0xFF1B5E20),
                                modifier = Modifier.padding(10.dp)
                            )
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
                                reason = safeDeleteReasonInput,
                                onSuccess = {
                                    productToSafeDelete = null
                                    Toast.makeText(context, "'${prod.name}' রিসাইকেল বিনে পাঠানো হয়েছে ✅", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("রিসাইকেল বিনে পাঠান")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { productToSafeDelete = null }) {
                        Text("বাতিল")
                    }
                }
            )
        }
    }

    // Quick Sale Success Animated Dialog
    quickSaleSuccessData?.let { (sale, items) ->
        SaleSuccessDialog(
            sale = sale,
            onDismiss = {
                val current = quickSaleSuccessData
                quickSaleSuccessData = null
                if (current != null) {
                    onSaleSuccess?.invoke(current.first, current.second)
                }
            }
        )
    }
}
