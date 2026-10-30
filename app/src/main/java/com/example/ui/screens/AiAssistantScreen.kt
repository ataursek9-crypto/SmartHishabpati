package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.data.model.UserRole
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.viewmodel.BusinessViewModel

data class ChatMessage(
    val sender: String,
    val text: String,
    val isUser: Boolean,
    val time: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val summary by viewModel.adminDashboardSummary.collectAsStateWithLifecycle()
    val lowStockList by viewModel.lowStockProducts.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val sales by viewModel.allSales.collectAsStateWithLifecycle()
    val expenses by viewModel.allExpenses.collectAsStateWithLifecycle()

    var inputPrompt by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = "স্মার্ট AI সহকারী",
                text = "আসসালামু আলাইকুম! আমি আপনার স্মার্ট ব্যবসায়িক সহকারী। আপনি ব্যবসায়িক হিসাব, আজকের বিক্রি, কম স্টক, কাস্টমার বকেয়া ইত্যাদি বিষয়ে আমাকে যে কোনো প্রশ্ন করতে পারেন।",
                isUser = false
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ব্যবসায়িক AI সহকারী", fontWeight = FontWeight.Bold) },
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
            // Suggested Prompts Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "আজ কত বিক্রি?",
                    "কোন পণ্য কম?",
                    "বকেয়া কত?"
                ).forEach { prompt ->
                    SuggestionChip(
                        onClick = {
                            inputPrompt = prompt
                        },
                        label = { Text(prompt, fontSize = 11.sp) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (msg.isUser) PrimaryBlue else MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier.widthIn(max = 300.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (msg.isUser) "আপনি" else "স্মার্ট AI সহকারী",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (msg.isUser) Color.White.copy(alpha = 0.8f) else PrimaryNavy
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = msg.text,
                                    fontSize = 13.sp,
                                    color = if (msg.isUser) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputPrompt,
                        onValueChange = { inputPrompt = it },
                        placeholder = { Text("প্রশ্ন লিখুন (যেমন: আজকের বিক্রি কত?)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputPrompt.isNotBlank()) {
                                val query = inputPrompt.trim()
                                messages.add(ChatMessage(sender = user?.name ?: "ইউজার", text = query, isUser = true))
                                inputPrompt = ""

                                // Process local real-time intelligent business analysis response
                                val responseText = generateAssistantResponse(
                                    query = query,
                                    userRole = user?.role ?: UserRole.SALESMAN,
                                    summary = summary,
                                    lowStock = lowStockList,
                                    customers = customers
                                )
                                messages.add(ChatMessage(sender = "স্মার্ট AI", text = responseText, isUser = false))
                            }
                        },
                        modifier = Modifier
                            .background(PrimaryNavy, RoundedCornerShape(24.dp))
                            .testTag("ai_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "পাঠান", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun generateAssistantResponse(
    query: String,
    userRole: UserRole,
    summary: com.example.viewmodel.DashboardSummary,
    lowStock: List<com.example.data.model.Product>,
    customers: List<com.example.data.model.Customer>
): String {
    val q = query.lowercase()
    return when {
        q.contains("বিক্রি") || q.contains("সেলস") || q.contains("আজকে") -> {
            "আজকের মোট বিক্রি হয়েছে ${BengaliFormatter.formatCurrency(summary.todaySales)} এবং নগদ/ডিজিটাল জমা এসেছে ${BengaliFormatter.formatCurrency(summary.todayCollected)}।"
        }
        q.contains("স্টক") || q.contains("পণ্য") || q.contains("কম") -> {
            if (lowStock.isEmpty()) {
                "সব পণ্যের পর্যাপ্ত স্টক রয়েছে। কোনো পণ্যের স্টক কম নেই।"
            } else {
                val names = lowStock.joinToString(", ") { "${it.name} (বাকি ${it.currentStock} ${it.unit})" }
                "বর্তমানে ${lowStock.size}টি পণ্যের স্টক নির্ধারিত সতর্কতার নিচে নেমে এসেছে: $names।"
            }
        }
        q.contains("বকেয়া") || q.contains("বাকি") || q.contains("কাস্টমার") -> {
            val topDebtor = customers.maxByOrNull { it.currentDue }
            "কাস্টমারদের মোট বকেয়া রয়েছে ${BengaliFormatter.formatCurrency(summary.totalDue)}।" +
                    (if (topDebtor != null && topDebtor.currentDue > 0) " সবচেয়ে বেশি বকেয়া রয়েছে '${topDebtor.name}' এর কাছে (${BengaliFormatter.formatCurrency(topDebtor.currentDue)})।" else "")
        }
        q.contains("খরচ") || q.contains("ব্যয়") -> {
            "চলতি হিসাবে মোট অনুমোদিত খরচ হয়েছে ${BengaliFormatter.formatCurrency(summary.totalApprovedExpense)}। ${if (summary.pendingExpenseCount > 0) "আরও ${summary.pendingExpenseCount}টি খরচের আবেদন অনুমোদনের অপেক্ষায় আছে।" else "কোনো নতুন আবেদন অপেক্ষমাণ নেই।"}"
        }
        q.contains("লাভ") || q.contains("প্রফিট") -> {
            if (userRole == UserRole.ADMIN) {
                "অনুমোদিত হিসাব ও বিক্রয় মার্জিনের ভিত্তিতে বর্তমান আনুমানিক নিট লাভ প্রায় ${BengaliFormatter.formatCurrency(summary.netProfit)}।"
            } else {
                "গোপনীয় ব্যবসায়িক নীতি অনুযায়ী লাভের হিসাব শুধুমাত্র এডমিন দেখতে পারেন।"
            }
        }
        else -> {
            "আমি আপনার ব্যবসার ডাটাবেস বিশ্লেষণ করেছি। আপনি আজকের বিক্রি, নগদ জমা, বকেয়া তালিকা, কম থাকা স্টক বা খরচের হিসাব সম্পর্কে জানতে যে কোনো তথ্য চাইতে পারেন।"
        }
    }
}
