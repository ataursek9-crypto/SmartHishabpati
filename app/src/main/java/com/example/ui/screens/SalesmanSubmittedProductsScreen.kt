package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ProductSubmission
import com.example.data.model.ProductSubmissionStatus
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.viewmodel.BusinessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesmanSubmittedProductsScreen(
    viewModel: BusinessViewModel,
    onNavigateToSubmitNew: () -> Unit,
    onNavigateToResubmit: (ProductSubmission) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val mySubmissions by viewModel.mySubmissions.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf<ProductSubmissionStatus?>(null) }

    val activeBizId by viewModel.activeBusinessId.collectAsStateWithLifecycle()
    val syncInfo by viewModel.submissionSyncInfo.collectAsStateWithLifecycle()
    val isFetching by viewModel.isFetchingSubmissions.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.fetchDirectProductSubmissions()
        viewModel.syncManager.startProductsRealtimeListener()
        viewModel.triggerSync()
    }

    val filteredList = remember(mySubmissions, selectedFilter) {
        if (selectedFilter == null) mySubmissions
        else mySubmissions.filter { it.status == selectedFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("আমার জমা দেওয়া প্রোডাক্ট", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.fetchDirectProductSubmissions()
                            viewModel.triggerSync { _, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("btn_sync_salesman_submissions")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "সিঙ্ক করুন", tint = Color.White)
                    }
                    IconButton(
                        onClick = onNavigateToSubmitNew,
                        modifier = Modifier.testTag("btn_top_submit_new_product")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "নতুন প্রোডাক্ট", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToSubmitNew,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_submit_product")
            ) {
                Icon(Icons.Default.Add, contentDescription = "নতুন প্রোডাক্ট জমা দিন")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Diagnostic business & cloud sync status banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "বিজনেস আইডি: ${activeBizId.ifBlank { "অনুপস্থিত (গুগল সাইন-ইন প্রয়োজন)" }}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (syncInfo != null) {
                            Text(
                                text = syncInfo ?: "",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.fetchDirectProductSubmissions { ok, msg, count ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isFetching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = "সার্ভার সিঙ্ক", tint = PrimaryNavy, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Status Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("সব (${mySubmissions.size})", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == ProductSubmissionStatus.PENDING,
                    onClick = { selectedFilter = ProductSubmissionStatus.PENDING },
                    label = { Text("অপেক্ষমাণ (${mySubmissions.count { it.status == ProductSubmissionStatus.PENDING }})", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == ProductSubmissionStatus.APPROVED,
                    onClick = { selectedFilter = ProductSubmissionStatus.APPROVED },
                    label = { Text("অনুমোদিত (${mySubmissions.count { it.status == ProductSubmissionStatus.APPROVED }})", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedFilter == ProductSubmissionStatus.NEEDS_CORRECTION,
                    onClick = { selectedFilter = ProductSubmissionStatus.NEEDS_CORRECTION },
                    label = { Text("সংশোধন (${mySubmissions.count { it.status == ProductSubmissionStatus.NEEDS_CORRECTION }})", fontSize = 12.sp) }
                )
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedFilter == null) "আপনি এখনও কোনো প্রোডাক্ট জমা দেননি" else "এই ক্যাটাগরিতে কোনো প্রোডাক্ট নেই",
                            color = Color.Gray,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToSubmitNew,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("নতুন প্রোডাক্ট জমা দিন")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        SalesmanSubmissionCard(
                            submission = item,
                            onResubmit = { onNavigateToResubmit(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SalesmanSubmissionCard(
    submission: ProductSubmission,
    onResubmit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("submission_card_${submission.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = submission.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "SKU/কোড: ${submission.productCode.ifBlank { "স্বয়ংক্রিয়" }} • ক্যাটাগরি: ${submission.category}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (submission.status) {
                        ProductSubmissionStatus.PENDING -> Color(0xFFFFF3E0)
                        ProductSubmissionStatus.APPROVED -> Color(0xFFE8F5E9)
                        ProductSubmissionStatus.NEEDS_CORRECTION -> Color(0xFFFFEBEE)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val (icon, label, textColor) = when (submission.status) {
                            ProductSubmissionStatus.PENDING -> Triple(Icons.Default.HourglassTop, "অপেক্ষমাণ", Color(0xFFE65100))
                            ProductSubmissionStatus.APPROVED -> Triple(Icons.Default.CheckCircle, "অনুমোদিত", Color(0xFF2E7D32))
                            ProductSubmissionStatus.NEEDS_CORRECTION -> Triple(Icons.Default.Error, "সংশোধন প্রয়োজন", Color(0xFFC62828))
                        }
                        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = label, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Pricing and Stock Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ক্রয়মূল্য: ${BengaliFormatter.formatCurrency(submission.purchasePrice)}", fontSize = 12.sp, color = Color.DarkGray)
                    Text("বিক্রয়মূল্য: ${BengaliFormatter.formatCurrency(submission.sellingPrice)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("প্রস্তাবিত স্টক: ${submission.initialStock} ${submission.unit}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                    Text("(অনুমোদনের পর যোগ হবে)", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    Text("জমা: ${BengaliFormatter.formatDate(submission.createdAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }

            if (submission.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "বিবরণ: ${submission.description}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Rule A.5: Admin correction notes & resubmit button
            if (submission.status == ProductSubmissionStatus.NEEDS_CORRECTION) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Feedback, contentDescription = null, tint = AccentRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "এডমিনের সংশোধনের কারণ:",
                                fontWeight = FontWeight.Bold,
                                color = AccentRed,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = submission.adminCorrectionNote.ifBlank { "তথ্য সংশোধন করুন" },
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onResubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_resubmit_${submission.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("সংশোধন করে পুনরায় জমা দিন", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Rule A.4: Notice for pending submissions (cannot edit directly)
            if (submission.status == ProductSubmissionStatus.PENDING) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🔒 জমা দেওয়ার পর এটি অপরিবর্তনযোগ্য। এডমিন পর্যালোচনা শেষে সিদ্ধান্ত নেবেন।",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (submission.status == ProductSubmissionStatus.APPROVED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = " এটি অনুমোদিত হয়ে মূল ইনভেন্টরিতে অন্তর্ভুক্ত হয়েছে।",
                    fontSize = 11.sp,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}
