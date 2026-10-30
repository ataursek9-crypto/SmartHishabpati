package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import com.example.data.service.OnlineImageCandidate
import com.example.data.service.ProductImageResult
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryNavy
import com.example.ui.theme.SecondaryTeal
import com.example.util.ProductImageStorage
import kotlinx.coroutines.launch
import java.io.File

/**
 * Returns an appropriate professional icon for product categories.
 * Strictly avoids clothes hangers (Checkroom / DryCleaning) to prevent misleading placeholder looks.
 */
fun getCategoryIcon(category: String): ImageVector {
    val cat = category.trim()
    return when {
        cat.contains("শার্ট") || cat.contains("টি-শার্ট") || cat.contains("পোলো") -> Icons.Default.ShoppingBag
        cat.contains("প্যান্ট") || cat.contains("ট্রাউজার্স") || cat.contains("জিন্স") -> Icons.Default.LocalMall
        cat.contains("পাঞ্জাবি") || cat.contains("পায়জামা") -> Icons.Default.Style
        cat.contains("শাড়ি") || cat.contains("থ্রি-পিস") || cat.contains("বোরকা") || cat.contains("হিজাব") -> Icons.Default.AutoAwesome
        cat.contains("জুতা") || cat.contains("স্যান্ডেল") -> Icons.Default.Snowshoeing
        cat.contains("ব্যাগ") || cat.contains("এক্সেসরিজ") -> Icons.Default.Work
        cat.contains("তেল") || cat.contains("চাল") || cat.contains("ডাল") || cat.contains("মুদি") || cat.contains("খাদ্য") -> Icons.Default.LocalGroceryStore
        else -> Icons.Default.Inventory2
    }
}

/**
 * Returns a soft tint background color for category placeholder.
 */
fun getCategoryColor(category: String): Color {
    val cat = category.trim()
    return when {
        cat.contains("শার্ট") || cat.contains("টি-শার্ট") -> Color(0xFFE3F2FD) // Soft blue
        cat.contains("প্যান্ট") || cat.contains("ট্রাউজার্স") || cat.contains("জিন্স") -> Color(0xFFE8EAF6) // Soft indigo
        cat.contains("পাঞ্জাবি") || cat.contains("পায়জামা") -> Color(0xFFFFF3E0) // Soft amber
        cat.contains("শাড়ি") || cat.contains("থ্রি-পিস") -> Color(0xFFFCE4EC) // Soft pink
        cat.contains("জুতা") || cat.contains("স্যান্ডেল") -> Color(0xFFE0F7FA) // Soft cyan
        cat.contains("ব্যাগ") -> Color(0xFFF3E5F5) // Soft purple
        cat.contains("মুদি") || cat.contains("তেল") || cat.contains("চাল") -> Color(0xFFFFFDE7) // Soft yellow
        else -> Color(0xFFECEFF1) // Clean blue grey
    }
}

/**
 * Clean Category Placeholder displayed when image is null or loading fails.
 * Designed like a clean e-commerce product badge (no hanger visuals).
 */
@Composable
fun CategoryIconPlaceholder(
    category: String,
    modifier: Modifier = Modifier
) {
    val bgColor = remember(category) { getCategoryColor(category) }
    val icon = remember(category) { getCategoryIcon(category) }

    Box(
        modifier = modifier
            .background(bgColor)
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = category,
                tint = PrimaryNavy.copy(alpha = 0.75f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = category.take(10),
                fontSize = 9.sp,
                color = PrimaryNavy.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Reusable Product Thumbnail with Coil AsyncImage + Category Fallback + Source Badge.
 */
@Composable
fun ProductThumbnail(
    imageUri: String?,
    category: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    imageSource: String? = null,
    showBadge: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        if (!imageUri.isNullOrBlank()) {
            val context = LocalContext.current
            val imageRequest = remember(imageUri) {
                ProductImageStorage.buildImageRequest(context, imageUri)
            }
            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = contentDescription ?: category,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    }
                },
                error = {
                    CategoryIconPlaceholder(category = category, modifier = Modifier.fillMaxSize())
                }
            )

            // Source badge
            if (showBadge && !imageSource.isNullOrBlank() && imageSource != "NONE") {
                val badgeText = when (imageSource) {
                    "AI_GENERATED" -> "AI"
                    "ONLINE_SEARCH" -> "🌐"
                    "CAMERA" -> "📷"
                    "USER_PICKED" -> "🖼️"
                    else -> ""
                }
                val badgeColor = when (imageSource) {
                    "AI_GENERATED" -> Color(0xFF6A1B9A) // Purple
                    "ONLINE_SEARCH" -> Color(0xFF0277BD) // Deep Blue
                    "CAMERA" -> Color(0xFF2E7D32) // Forest Green
                    else -> PrimaryNavy
                }
                if (badgeText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .background(badgeColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            CategoryIconPlaceholder(category = category, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Interactive Image Selector for Add / Edit Product forms.
 */
@Composable
fun ProductImageSelector(
    imageUri: String?,
    imageSource: String,
    imageSourceInfo: String,
    category: String,
    productName: String,
    isAutoMode: Boolean,
    onImageSelected: (uri: String?, source: String, info: String) -> Unit,
    onTriggerAiGeneration: suspend () -> ProductImageResult,
    onSearchOnlineImages: suspend (query: String) -> List<OnlineImageCandidate>,
    onDownloadOnlineImage: suspend (candidate: OnlineImageCandidate) -> ProductImageResult,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showOnlineSearchDialog by remember { mutableStateOf(false) }
    var onlineCandidates by remember { mutableStateOf<List<OnlineImageCandidate>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }
    var selectedOnlineCandidate by remember { mutableStateOf<OnlineImageCandidate?>(null) }
    var previewCandidate by remember { mutableStateOf<OnlineImageCandidate?>(null) }
    var showPreviewDialog by remember { mutableStateOf(false) }

    // Dialog state for newly generated AI image confirmation
    var pendingAiResult by remember { mutableStateOf<ProductImageResult.Success?>(null) }
    var showAiConfirmationDialog by remember { mutableStateOf(false) }

    // Dialog state for quota limit zero notice
    var showQuotaNoticeDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }

    // Camera capture Uri state
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessing = true
                statusMessage = "ছবি সেভ হচ্ছে..."
                val savedPath = ProductImageStorage.copyUriToInternalStorage(context, uri)
                isProcessing = false
                if (savedPath != null) {
                    onImageSelected(savedPath, "USER_PICKED", "গ্যালারি থেকে সংগৃহীত")
                    statusMessage = "গ্যালারির ছবি সফলভাবে যুক্ত হয়েছে"
                } else {
                    Toast.makeText(context, "ছবি কপি করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Camera capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = tempCameraUri
        if (success && uri != null) {
            coroutineScope.launch {
                isProcessing = true
                statusMessage = "ক্যামেরার ছবি প্রসেস হচ্ছে..."
                val savedPath = ProductImageStorage.copyUriToInternalStorage(context, uri)
                isProcessing = false
                if (savedPath != null) {
                    onImageSelected(savedPath, "CAMERA", "ক্যামেরা দিয়ে তোলা")
                    statusMessage = "ক্যামেরার ছবি সফলভাবে যুক্ত হয়েছে"
                } else {
                    Toast.makeText(context, "ক্যামেরার ছবি সেভ করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            try {
                val (uri, _) = ProductImageStorage.createTempImageUriForCamera(context)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "ক্যামেরা চালু করা যায়নি: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "ছবি তুলতে ক্যামেরার অনুমতি প্রয়োজন", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "প্রোডাক্টের ছবি",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = PrimaryNavy
                )
                // Mode indicator
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAutoMode) PrimaryBlue.copy(alpha = 0.15f) else SecondaryTeal.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isAutoMode) "⚡ অটো মোড" else "🎯 ম্যানুয়াল মোড",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAutoMode) PrimaryBlue else SecondaryTeal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Main Preview Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (!imageUri.isNullOrBlank()) {
                                showPreviewDialog = true
                            }
                        }
                ) {
                    ProductThumbnail(
                        imageUri = imageUri,
                        category = category,
                        imageSource = imageSource,
                        modifier = Modifier.fillMaxSize(),
                        showBadge = true
                    )
                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!imageUri.isNullOrBlank()) {
                        Text(
                            text = when (imageSource) {
                                "USER_PICKED" -> "🖼️ গ্যালারির ছবি"
                                "CAMERA" -> "📷 ক্যামেরা ক্যাপচার"
                                "AI_GENERATED" -> "✨ AI-জেনারেটেড ছবি"
                                "ONLINE_SEARCH" -> "🌐 অনলাইন অনুসন্ধানকৃত ছবি"
                                else -> "যুক্ত করা ছবি"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = PrimaryNavy
                        )
                        if (imageSourceInfo.isNotBlank()) {
                            Text(
                                text = imageSourceInfo,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { showPreviewDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("প্রিভিউ", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    onImageSelected(null, "NONE", "")
                                    statusMessage = "ছবি মুছে ফেলা হয়েছে"
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp).testTag("btn_remove_product_image")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("মুছুন", fontSize = 11.sp)
                            }
                        }
                    } else {
                        Text(
                            text = "কোনো ছবি যুক্ত করা হয়নি",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isAutoMode)
                                "সংরক্ষণের সময় ছবি না থাকলে অটো AI/অনলাইন থেকে বড় প্রফেশনাল ছবি নেওয়া হবে"
                            else
                                "ক্যামেরা, গ্যালারি, AI অথবা অনলাইন থেকে বাস্তবসম্মত ছবি নির্বাচন করুন",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Photo Picker
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("btn_pick_gallery_image"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("গ্যালারি", fontSize = 11.sp)
                }

                // Camera
                OutlinedButton(
                    onClick = {
                        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (permission == PackageManager.PERMISSION_GRANTED) {
                            try {
                                val (uri, _) = ProductImageStorage.createTempImageUriForCamera(context)
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                Toast.makeText(context, "ক্যামেরা চালু করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("btn_capture_camera_image"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ক্যামেরা", fontSize = 11.sp)
                }
            }

            // Manual Mode Extra Options: AI & Online Search
            if (!isAutoMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // AI Generation Button
                    Button(
                        onClick = {
                            if (productName.isBlank()) {
                                Toast.makeText(context, "AI ছবি তৈরিতে প্রথমে পণ্যের নাম লিখুন", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            coroutineScope.launch {
                                isProcessing = true
                                statusMessage = "AI দিয়ে প্রফেশনাল ছবি তৈরি হচ্ছে..."
                                val result = onTriggerAiGeneration()
                                isProcessing = false
                                when (result) {
                                    is ProductImageResult.Success -> {
                                        pendingAiResult = result
                                        showAiConfirmationDialog = true
                                        statusMessage = "AI ছবি তৈরি হয়েছে! প্রিভিউ দেখে নিশ্চিত করুন।"
                                    }
                                    is ProductImageResult.Error -> {
                                        statusMessage = result.message
                                        if (result.isQuotaLimitZero) {
                                            showQuotaNoticeDialog = true
                                        } else if (result.message.contains("AI API কী") || result.message.contains("API Key") || result.message.contains("কনফিগার করা নেই") || result.message.contains("নিষ্ক্রিয়")) {
                                            showApiKeyDialog = true
                                        } else {
                                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("btn_generate_ai_image"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI তৈরি", fontSize = 11.sp, color = Color.White)
                    }

                    // Online Search Button
                    Button(
                        onClick = {
                            showOnlineSearchDialog = true
                            coroutineScope.launch {
                                isSearchingOnline = true
                                val results = onSearchOnlineImages(productName.ifBlank { category })
                                onlineCandidates = results
                                isSearchingOnline = false
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("btn_search_online_image"),
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("অনলাইনে খুঁজুন", fontSize = 11.sp, color = Color.White)
                    }
                }
            }

            // Status message
            if (statusMessage != null) {
                Text(
                    text = statusMessage!!,
                    fontSize = 11.sp,
                    color = if (statusMessage!!.contains("ব্যর্থ") || statusMessage!!.contains("নেই") || statusMessage!!.contains("লিমিট ০"))
                        MaterialTheme.colorScheme.error
                    else
                        Color(0xFF2E7D32),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    // Dialog 1: AI Generated Image Confirmation & Preview
    if (showAiConfirmationDialog && pendingAiResult != null) {
        val result = pendingAiResult!!
        Dialog(onDismissRequest = { showAiConfirmationDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "✨ AI-জেনারেটেড প্রোডাক্ট ইমেজ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = PrimaryNavy
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        SubcomposeAsyncImage(
                            model = File(result.filePath),
                            contentDescription = productName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                }
                            }
                        )
                    }

                    Text(
                        text = result.info,
                        fontSize = 11.sp,
                        color = SecondaryTeal,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showAiConfirmationDialog = false
                                pendingAiResult = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("বাতিল", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                onImageSelected(result.filePath, result.source, result.info)
                                showAiConfirmationDialog = false
                                pendingAiResult = null
                                statusMessage = "AI ছবি প্রোডাক্টে সফলভাবে সংরক্ষণ করা হয়েছে"
                            },
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                        ) {
                            Text("ছবিটি গ্রহণ করুন", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialog 2: Free Tier Quota limit 0 helpful guidance dialog
    if (showQuotaNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showQuotaNoticeDialog = false },
            title = {
                Text("Gemini AI ইমেজ কোটা সীমাবদ্ধতা", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Gemini API-র ফ্রি টিয়ারে সরাসরি ইমেজ জেনারেশন কোটা ০ (Free Tier Quota: 0 for image generation)। এর জন্য গুগল ক্লাউডে পেইড বিলিং প্রয়োজন।",
                        fontSize = 13.sp
                    )
                    Text(
                        text = "তবে আপনি এখনই সম্পূর্ণ বিনামূল্যে 'অনলাইনে খুঁজুন' অপশন থেকে টি-শার্ট, জিন্স বা অন্য পণ্যের আসল স্টুডিও কোয়ালিটি ছবি ব্যবহার করতে পারবেন।",
                        fontSize = 12.sp,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQuotaNoticeDialog = false
                        showOnlineSearchDialog = true
                        coroutineScope.launch {
                            isSearchingOnline = true
                            onlineCandidates = onSearchOnlineImages(productName.ifBlank { category })
                            isSearchingOnline = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("অনলাইন থেকে ছবি বাছুন")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showQuotaNoticeDialog = false
                        showApiKeyDialog = true
                    }) {
                        Text("কী পরিবর্তন")
                    }
                    TextButton(onClick = { showQuotaNoticeDialog = false }) {
                        Text("বুঝেছি")
                    }
                }
            }
        )
    }

    // Dialog 2.5: API Key Configuration Dialog
    if (showApiKeyDialog) {
        val imgService = remember { com.example.data.service.ProductImageService(context) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Text("Gemini AI API কী কনফিগারেশন", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "AI দ্বারা প্রোডাক্টের স্টুডিও ছবি তৈরি করতে Gemini API Key প্রয়োজন।\n\nআপনি Google AI Studio (ai.google.dev) থেকে সংগৃহীত API কী নিচে পেস্ট করতে পারেন। এই ডিভাইসে এটি নিরাপদে সংরক্ষিত থাকবে।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (imgService.isApiKeyConfigured()) {
                        Text(
                            text = "বর্তমান কী: ${imgService.getMaskedApiKey()}",
                            fontSize = 11.sp,
                            color = PrimaryNavy,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (apiKeyInput.isNotBlank()) {
                            imgService.saveCustomApiKey(apiKeyInput.trim())
                            showApiKeyDialog = false
                            Toast.makeText(context, "API Key সংরক্ষিত হয়েছে! ছবি তৈরি হচ্ছে...", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                isProcessing = true
                                statusMessage = "AI দিয়ে প্রফেশনাল ছবি তৈরি হচ্ছে..."
                                val retryResult = onTriggerAiGeneration()
                                isProcessing = false
                                when (retryResult) {
                                    is ProductImageResult.Success -> {
                                        pendingAiResult = retryResult
                                        showAiConfirmationDialog = true
                                        statusMessage = "AI ছবি তৈরি হয়েছে!"
                                    }
                                    is ProductImageResult.Error -> {
                                        statusMessage = retryResult.message
                                        if (retryResult.isQuotaLimitZero) {
                                            showQuotaNoticeDialog = true
                                        } else {
                                            Toast.makeText(context, retryResult.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = apiKeyInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                ) {
                    Text("সংরক্ষণ ও তৈরি করুন")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showApiKeyDialog = false
                        showOnlineSearchDialog = true
                        coroutineScope.launch {
                            isSearchingOnline = true
                            onlineCandidates = onSearchOnlineImages(productName.ifBlank { category })
                            isSearchingOnline = false
                        }
                    }) {
                        Text("অনলাইনে খুঁজুন")
                    }
                    TextButton(onClick = { showApiKeyDialog = false }) {
                        Text("বাতিল")
                    }
                }
            }
        )
    }

    // Dialog 3: Online Image Search Results Picker with 2-Column Grid, Visible Previews & Confirmation
    if (showOnlineSearchDialog) {
        Dialog(onDismissRequest = { showOnlineSearchDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "অনলাইন প্রোডাক্ট গ্যালারি",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = PrimaryNavy
                        )
                        IconButton(
                            onClick = { showOnlineSearchDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন", modifier = Modifier.size(18.dp))
                        }
                    }

                    Text(
                        text = "অনুসন্ধান: '${productName.ifBlank { category }}' (ছবিতে ট্যাপ করে বড় দেখুন ও বাছুন)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    if (isSearchingOnline) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PrimaryBlue)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("অনলাইনে আসল প্রোডাক্টের ছবি লোড হচ্ছে...", fontSize = 12.sp, color = PrimaryNavy)
                            }
                        }
                    } else if (onlineCandidates.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("কোনো উপযুক্ত ছবি পাওয়া যায়নি", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        // Requirement 1 & 2 & 4: 2-column grid with clear previews and tap-to-expand
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 290.dp)
                        ) {
                            items(onlineCandidates) { candidate ->
                                val isSelected = selectedOnlineCandidate?.imageUrl == candidate.imageUrl
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Requirement 4: Tap image/card to open large preview
                                            previewCandidate = candidate
                                        }
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(115.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFF1F5F9))
                                        ) {
                                            // Requirement 2 & 3: Real preview, custom User-Agent, Loading indicator, Error handling
                                            val imageReq = remember(candidate.imageUrl) {
                                                ProductImageStorage.buildImageRequest(context, candidate.imageUrl)
                                            }
                                            SubcomposeAsyncImage(
                                                model = imageReq,
                                                contentDescription = candidate.title,
                                                modifier = Modifier.fillMaxSize().padding(4.dp),
                                                contentScale = ContentScale.Fit,
                                                loading = {
                                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = PrimaryBlue)
                                                            Spacer(Modifier.height(4.dp))
                                                            Text("লোড হচ্ছে...", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
                                                        }
                                                    }
                                                },
                                                error = {
                                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            modifier = Modifier.padding(4.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.BrokenImage,
                                                                contentDescription = "ছবি লোড ত্রুটি",
                                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Spacer(Modifier.height(2.dp))
                                                            Text("ছবি লোড হয়নি", fontSize = 9.sp, color = MaterialTheme.colorScheme.error)
                                                            Text("ট্যাপ করে দেখুন", fontSize = 8.sp, color = PrimaryBlue)
                                                        }
                                                    }
                                                }
                                            )

                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .background(PrimaryBlue, CircleShape)
                                                        .size(22.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "নির্বাচিত",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = candidate.title,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = candidate.license,
                                            fontSize = 9.sp,
                                            color = SecondaryTeal,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isSelected) "✓ নির্বাচিত" else "ট্যাপে প্রিভিউ",
                                                fontSize = 9.sp,
                                                color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.outline,
                                                fontWeight = FontWeight.Medium
                                            )
                                            TextButton(
                                                onClick = { previewCandidate = candidate },
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                                modifier = Modifier.height(22.dp)
                                            ) {
                                                Text("বড় দেখুন 🔍", fontSize = 10.sp, color = PrimaryBlue)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Requirement 6: Selected Image Preview & Confirmation Card before final save
                    if (selectedOnlineCandidate != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81C784))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                ) {
                                    SubcomposeAsyncImage(
                                        model = remember(selectedOnlineCandidate!!.imageUrl) {
                                            ProductImageStorage.buildImageRequest(context, selectedOnlineCandidate!!.imageUrl)
                                        },
                                        contentDescription = selectedOnlineCandidate!!.title,
                                        modifier = Modifier.fillMaxSize().padding(2.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "নির্বাচিত: ${selectedOnlineCandidate!!.title}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "নিচে 'ছবিটি সংরক্ষণ করুন' বাটনে চাপুন",
                                        fontSize = 10.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                                TextButton(
                                    onClick = { selectedOnlineCandidate = null },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("বাতিল", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showOnlineSearchDialog = false
                                selectedOnlineCandidate = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("বাতিল", fontSize = 12.sp)
                        }

                        // Requirement 6: "ছবিটি সংরক্ষণ করুন" button confirms and downloads to product
                        Button(
                            onClick = {
                                val candidateToSave = selectedOnlineCandidate
                                if (candidateToSave != null) {
                                    coroutineScope.launch {
                                        isProcessing = true
                                        showOnlineSearchDialog = false
                                        statusMessage = "নির্বাচিত ছবি ডাউনলোড ও স্থায়ীভাবে সংরক্ষণ হচ্ছে..."
                                        val res = onDownloadOnlineImage(candidateToSave)
                                        isProcessing = false
                                        when (res) {
                                            is ProductImageResult.Success -> {
                                                onImageSelected(res.filePath, res.source, res.info)
                                                selectedOnlineCandidate = null
                                                statusMessage = "অনলাইন ছবি সফলভাবে প্রোডাক্টে সংরক্ষিত হয়েছে ✅"
                                            }
                                            is ProductImageResult.Error -> {
                                                statusMessage = res.message
                                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = selectedOnlineCandidate != null && !isProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy),
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Text(
                                text = if (selectedOnlineCandidate != null) "ছবিটি সংরক্ষণ করুন" else "ছবি নির্বাচন করুন",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog 3.5: Candidate Large Preview Dialog (Requirement 4 & 5)
    if (previewCandidate != null) {
        val candidate = previewCandidate!!
        Dialog(onDismissRequest = { previewCandidate = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = candidate.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = PrimaryNavy
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        val previewReq = remember(candidate.imageUrl) {
                            ProductImageStorage.buildImageRequest(context, candidate.imageUrl)
                        }
                        SubcomposeAsyncImage(
                            model = previewReq,
                            contentDescription = candidate.title,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            contentScale = ContentScale.Fit,
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = PrimaryBlue)
                                        Spacer(Modifier.height(8.dp))
                                        Text("বড় ছবি লোড হচ্ছে...", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            },
                            error = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                                        Spacer(Modifier.height(4.dp))
                                        Text("ছবি প্রিভিউ লোড হতে সমস্যা হয়েছে", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        )
                    }

                    Text(
                        text = "লাইসেন্স: ${candidate.license}",
                        fontSize = 11.sp,
                        color = SecondaryTeal,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    // Requirement 5: Exactly two buttons under large preview: "এই ছবি নির্বাচন করুন" & "ফিরে যান"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { previewCandidate = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ফিরে যান", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                // Requirement 6: Sets as selected candidate, returns to gallery for confirmation
                                selectedOnlineCandidate = candidate
                                previewCandidate = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Text("এই ছবি নির্বাচন করুন", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialog 4: Image Full Preview
    if (showPreviewDialog && !imageUri.isNullOrBlank()) {
        Dialog(onDismissRequest = { showPreviewDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = productName.ifBlank { "প্রোডাক্ট প্রিভিউ" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        SubcomposeAsyncImage(
                            model = remember(imageUri) { ProductImageStorage.buildImageRequest(context, imageUri) },
                            contentDescription = productName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (imageSourceInfo.isNotBlank()) {
                        Text(
                            text = imageSourceInfo,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = { showPreviewDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                    ) {
                        Text("ঠিক আছে")
                    }
                }
            }
        }
    }
}
