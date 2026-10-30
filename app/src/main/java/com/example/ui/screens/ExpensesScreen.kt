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
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.viewmodel.BusinessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isAdmin = currentUser?.role == UserRole.ADMIN

    LaunchedEffect(Unit) {
        viewModel.syncManager.startProductsRealtimeListener()
        viewModel.fetchDirectExpenses()
        viewModel.triggerSync()
    }

    val expenses = if (isAdmin) {
        viewModel.allExpenses.collectAsStateWithLifecycle().value
    } else {
        viewModel.myExpenses.collectAsStateWithLifecycle().value
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = if (isAdmin) listOf("অপেক্ষমাণ (${expenses.count { it.status == ExpenseStatus.PENDING }})", "অনুমোদিত", "বাতিল", "নতুন খরচ") else listOf("আমার খরচ", "নতুন আবেদন")

    var showReviewDialogForExpense by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAdmin) "খরচ ও অনুমোদন ব্যবস্থাপনা" else "আমার খরচের হিসাব", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.fetchDirectExpenses()
                            viewModel.triggerSync { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("btn_sync_expenses")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "সিঙ্ক করুন", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AccentAmber,
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
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }
            }

            if (isAdmin) {
                when (selectedTab) {
                    0 -> ExpenseListView(
                        expenses = expenses.filter { it.status == ExpenseStatus.PENDING },
                        isAdmin = true,
                        onExpenseClick = { showReviewDialogForExpense = it }
                    )
                    1 -> ExpenseListView(
                        expenses = expenses.filter { it.status == ExpenseStatus.APPROVED },
                        isAdmin = true,
                        onExpenseClick = { showReviewDialogForExpense = it }
                    )
                    2 -> ExpenseListView(
                        expenses = expenses.filter { it.status == ExpenseStatus.REJECTED },
                        isAdmin = true,
                        onExpenseClick = { showReviewDialogForExpense = it }
                    )
                    3 -> NewExpenseForm(viewModel = viewModel, onSubmitted = { selectedTab = 0 })
                }
            } else {
                when (selectedTab) {
                    0 -> ExpenseListView(
                        expenses = expenses,
                        isAdmin = false,
                        onExpenseClick = {}
                    )
                    1 -> NewExpenseForm(viewModel = viewModel, onSubmitted = { selectedTab = 0 })
                }
            }
        }
    }

    // Expense Review Dialog for Admin
    if (showReviewDialogForExpense != null) {
        val exp = showReviewDialogForExpense!!
        var adminNote by remember { mutableStateOf("") }
        var isProcessing by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                if (!isProcessing) showReviewDialogForExpense = null
            },
            title = { Text("খরচের আবেদন যাচাই ও অনুমোদন", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("আবেদনকারী: ${exp.salesmanName}", fontWeight = FontWeight.Bold)
                    Text("বিভাগ: ${exp.category} | পরিমাণ: ৳${exp.amount}", color = AccentAmber, fontWeight = FontWeight.Bold)
                    Text("বিবরণ: ${exp.description}")
                    Text("উৎস: ${if (exp.source == ExpenseSource.COMPANY_CASH) "কোম্পানির নগদ ক্যাশ" else "নিজের পকেট"}")
                    Text("তারিখ: ${BengaliFormatter.formatDate(exp.createdAt)}")

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = adminNote,
                        onValueChange = { adminNote = it },
                        label = { Text("এডমিন মন্তব্য / কারণ (ঐচ্ছিক)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            isProcessing = true
                            errorMessage = null
                            viewModel.updateExpenseStatus(exp, ExpenseStatus.APPROVED, adminNote) { success, msg ->
                                isProcessing = false
                                if (success) {
                                    showReviewDialogForExpense = null
                                } else {
                                    errorMessage = msg
                                }
                            }
                        },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("অনুমোদন")
                    }
                    Button(
                        onClick = {
                            isProcessing = true
                            errorMessage = null
                            viewModel.updateExpenseStatus(exp, ExpenseStatus.REJECTED, adminNote) { success, msg ->
                                isProcessing = false
                                if (success) {
                                    showReviewDialogForExpense = null
                                } else {
                                    errorMessage = msg
                                }
                            }
                        },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Text("বাতিল")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReviewDialogForExpense = null },
                    enabled = !isProcessing
                ) {
                    Text("বন্ধ করুন")
                }
            }
        )
    }
}

@Composable
fun ExpenseListView(
    expenses: List<Expense>,
    isAdmin: Boolean,
    onExpenseClick: (Expense) -> Unit
) {
    if (expenses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("কোনো খরচ পাওয়া যায়নি।", color = MaterialTheme.colorScheme.outline)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(expenses) { exp ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExpenseClick(exp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (exp.category) {
                                    ExpenseCategory.TRAVEL -> "যাতায়াত খরচ"
                                    ExpenseCategory.FOOD -> "খাবার খরচ"
                                    ExpenseCategory.DELIVERY -> "ডেলিভারি খরচ"
                                    ExpenseCategory.LOADING -> "মাল ওঠানো-নামানো"
                                    ExpenseCategory.OTHER -> "অন্যান্য খরচ"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = BengaliFormatter.formatCurrency(exp.amount),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = AccentAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = exp.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${exp.salesmanName} • ${BengaliFormatter.formatDateOnly(exp.createdAt)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (exp.status) {
                                    ExpenseStatus.PENDING -> Color(0xFFFFF8E1)
                                    ExpenseStatus.APPROVED -> Color(0xFFE8F5E9)
                                    ExpenseStatus.REJECTED -> Color(0xFFFFEBEE)
                                }
                            ) {
                                Text(
                                    text = when (exp.status) {
                                        ExpenseStatus.PENDING -> "অপেক্ষমাণ"
                                        ExpenseStatus.APPROVED -> "অনুমোদিত"
                                        ExpenseStatus.REJECTED -> "বাতিল"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (exp.status) {
                                        ExpenseStatus.PENDING -> AccentAmber
                                        ExpenseStatus.APPROVED -> AccentGreen
                                        ExpenseStatus.REJECTED -> AccentRed
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (exp.adminNote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("এডমিন নোট: ${exp.adminNote}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewExpenseForm(
    viewModel: BusinessViewModel,
    onSubmitted: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf(ExpenseCategory.TRAVEL) }
    var source by remember { mutableStateOf(ExpenseSource.COMPANY_CASH) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("খরচের বিভাগ নির্বাচন করুন:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                ExpenseCategory.TRAVEL to "যাতায়াত",
                ExpenseCategory.FOOD to "খাবার",
                ExpenseCategory.DELIVERY to "ডেলিভারি",
                ExpenseCategory.LOADING to "কুলি/লেবার",
                ExpenseCategory.OTHER to "অন্যান্য"
            ).forEach { (cat, label) ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text("টাকার উৎস নির্বাচন করুন:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = source == ExpenseSource.COMPANY_CASH,
                onClick = { source = ExpenseSource.COMPANY_CASH },
                label = { Text("কোম্পানির নগদ ক্যাশ থেকে") }
            )
            FilterChip(
                selected = source == ExpenseSource.OWN_POCKET,
                onClick = { source = ExpenseSource.OWN_POCKET },
                label = { Text("নিজের পকেট থেকে") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("খরচের টাকার পরিমাণ লিখুন *") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expense_amount_input"),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("খরচের কারণ ও বিস্তারিত বিবরণ লিখুন *") },
            placeholder = { Text("যেমন: বাসের ভাড়া ও মালামাল পরিবহন") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        if (errorMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                val amt = amountText.toDoubleOrNull() ?: 0.0
                if (amt <= 0) {
                    errorMessage = "সঠিক টাকার পরিমাণ দিন!"
                    return@Button
                }
                if (description.isBlank()) {
                    errorMessage = "খরচের বিবরণ লিখুন!"
                    return@Button
                }
                val user = currentUser ?: return@Button
                val exp = Expense(
                    id = "exp_${System.currentTimeMillis()}",
                    salesmanId = user.id,
                    salesmanName = user.name,
                    category = category,
                    source = source,
                    amount = amt,
                    description = description.trim(),
                    status = if (user.role == UserRole.ADMIN) ExpenseStatus.APPROVED else ExpenseStatus.PENDING
                )
                viewModel.submitExpense(exp) {
                    onSubmitted()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_submit_expense"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
        ) {
            Text("খরচের আবেদন দাখিল করুন", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
