package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PaymentReceipt
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.ui.theme.*
import com.example.util.BengaliFormatter
import com.example.util.PdfReceiptGenerator
import com.example.viewmodel.BusinessViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleReceiptViewScreen(
    sale: Sale,
    items: List<SaleItem>,
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val settings by viewModel.companySettings.collectAsStateWithLifecycle()
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(sale, items, settings) {
        generatedPdfFile = PdfReceiptGenerator.generateSaleInvoicePdf(context, settings, sale, items)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ইনভয়েস ভাউচার #${sale.invoiceNumber}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.toggleSalePin(sale.invoiceNumber, sale.isPinned)
                    }) {
                        Icon(
                            if (sale.isPinned) Icons.Default.PushPin else Icons.Default.BookmarkBorder,
                            contentDescription = "পিন করুন",
                            tint = if (sale.isPinned) AccentAmber else Color.White
                        )
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
            // Paper Invoice Card Styling
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Brand
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(settings.businessName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PrimaryNavy)
                        Text(settings.address, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Text("মোবাইল: ${settings.phone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = PrimaryLightBlue,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "বিক্রয় চালান / ইনভয়েস",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Meta Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("চালান নং: ${sale.invoiceNumber}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(BengaliFormatter.formatDate(sale.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("কাস্টমার: ${sale.customerName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("মোবাইল: ${sale.customerPhone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("বিক্রয় প্রতিনিধি: ${sale.salesmanName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Items Table
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("পণ্য", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2f))
                            Text("পরিমাণ", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("দর", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("মোট", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.2f))
                        }
                    }

                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.productName, fontSize = 12.sp, modifier = Modifier.weight(2f))
                            Text("${item.quantity}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("৳${item.unitPrice}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("৳${item.totalPrice}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.2f))
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Totals
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("মোট উপ-বিল:")
                        Text(BengaliFormatter.formatCurrency(sale.subtotal))
                    }
                    if (sale.discount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ডিসকাউন্ট:")
                            Text("- ${BengaliFormatter.formatCurrency(sale.discount)}", color = AccentGreen)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("সর্বমোট বিল:", fontWeight = FontWeight.Bold)
                        Text(BengaliFormatter.formatCurrency(sale.totalAmount), fontWeight = FontWeight.Bold, color = PrimaryNavy)
                    }
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("পরিশোধ / নগদ জমা:", fontWeight = FontWeight.SemiBold)
                        Text(BengaliFormatter.formatCurrency(sale.paidAmount), fontWeight = FontWeight.SemiBold, color = AccentGreen)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("অবশিষ্ট বকেয়া:", fontWeight = FontWeight.Bold)
                        Text(
                            BengaliFormatter.formatCurrency(sale.dueAmount),
                            fontWeight = FontWeight.Bold,
                            color = if (sale.dueAmount > 0) AccentRed else AccentGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        settings.receiptFooter,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons: PDF Share, Print, Back to Dashboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val file = generatedPdfFile ?: PdfReceiptGenerator.generateSaleInvoicePdf(context, settings, sale, items)
                        if (file != null) {
                            PdfReceiptGenerator.sharePdf(context, file, "ইনভয়েস #${sale.invoiceNumber}")
                        } else {
                            Toast.makeText(context, "PDF তৈরিতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PDF শেয়ার / প্রিন্ট", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("হোমে ফিরে যান", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyReceiptViewScreen(
    receipt: PaymentReceipt,
    viewModel: BusinessViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val settings by viewModel.companySettings.collectAsStateWithLifecycle()
    var generatedPdfFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(receipt, settings) {
        generatedPdfFile = PdfReceiptGenerator.generateMoneyReceiptPdf(context, settings, receipt)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("মানি রসিদ #${receipt.receiptNumber}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.toggleReceiptPin(receipt.receiptNumber, receipt.isPinned)
                    }) {
                        Icon(
                            if (receipt.isPinned) Icons.Default.PushPin else Icons.Default.BookmarkBorder,
                            contentDescription = "পিন করুন",
                            tint = if (receipt.isPinned) AccentAmber else Color.White
                        )
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(settings.businessName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SecondaryTeal)
                        Text("মোবাইল: ${settings.phone} | ${settings.address}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = SecondaryLightTeal,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "অফিসিয়াল মানি রিসিপ্ট",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecondaryTeal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("রসিদ নং: ${receipt.receiptNumber}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(BengaliFormatter.formatDate(receipt.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("গ্রাহকের নাম: ${receipt.customerName}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("মোবাইল: ${receipt.customerPhone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("আদায়কারী: ${receipt.salesmanName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("পেমেন্ট মেথড: ${receipt.paymentMethod}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Large Paid Box
                    Surface(
                        color = SecondaryLightTeal,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("আদায়কৃত মোট টাকা", fontSize = 12.sp, color = SecondaryTeal)
                            Text(
                                BengaliFormatter.formatCurrency(receipt.amount),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = SecondaryTeal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("পূর্ববর্তী বকেয়া:")
                        Text(BengaliFormatter.formatCurrency(receipt.previousDue))
                    }
                    Divider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("বর্তমান অবশিষ্ট বকেয়া:", fontWeight = FontWeight.Bold)
                        Text(
                            BengaliFormatter.formatCurrency(receipt.currentDue),
                            fontWeight = FontWeight.Bold,
                            color = if (receipt.currentDue > 0) AccentRed else AccentGreen
                        )
                    }

                    if (receipt.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("মন্তব্য: ${receipt.note}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val file = generatedPdfFile ?: PdfReceiptGenerator.generateMoneyReceiptPdf(context, settings, receipt)
                        if (file != null) {
                            PdfReceiptGenerator.sharePdf(context, file, "মানি রসিদ #${receipt.receiptNumber}")
                        } else {
                            Toast.makeText(context, "PDF তৈরিতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PDF শেয়ার / প্রিন্ট", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ফিরে যান", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
