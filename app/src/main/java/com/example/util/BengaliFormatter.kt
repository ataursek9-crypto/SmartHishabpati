package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.CompanySettings
import com.example.data.model.PaymentReceipt
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.data.model.Company
import com.example.data.model.CompanyPurchase
import com.example.data.model.CompanyPayment
import com.example.viewmodel.CustomerDueItem
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BengaliFormatter {
    private val bdFormat = NumberFormat.getNumberInstance(Locale("bn", "BD")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }

    fun formatNumber(number: Double): String {
        return bdFormat.format(number)
    }

    fun formatNumber(number: Int): String {
        return bdFormat.format(number)
    }

    fun formatNumber(number: Long): String {
        return bdFormat.format(number)
    }

    fun formatCurrency(amount: Double): String {
        return "৳ " + String.format(Locale.US, "%,.2f", amount)
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM, yyyy - hh:mm a", Locale.ENGLISH)
        return sdf.format(Date(timestamp))
    }

    fun formatDateOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        return sdf.format(Date(timestamp))
    }

    fun formatAdminDisplayName(name: String? = null): String {
        var clean = name?.trim().orEmpty()
        while (clean.startsWith("এডমিন:", ignoreCase = true) ||
            clean.startsWith("এডমিন", ignoreCase = true) ||
            clean.startsWith("admin:", ignoreCase = true) ||
            clean.startsWith("admin", ignoreCase = true)
        ) {
            clean = clean.replaceFirst(Regex("^(?i)(এডমিন:?|admin:?)\\s*"), "").trim()
        }
        return if (clean.isBlank()) "মোঃ আতাউর রহমান" else clean
    }

    fun formatAdminEnglishName(name: String? = null): String {
        var clean = name?.trim().orEmpty()
        while (clean.startsWith("Admin:", ignoreCase = true) ||
            clean.startsWith("Admin", ignoreCase = true) ||
            clean.startsWith("এডমিন:", ignoreCase = true) ||
            clean.startsWith("এডমিন", ignoreCase = true)
        ) {
            clean = clean.replaceFirst(Regex("^(?i)(admin:?|এডমিন:?)\\s*"), "").trim()
        }
        return if (clean.isBlank()) "Md Ataur Rahman" else clean
    }
}

object PdfReceiptGenerator {

    fun generateSaleInvoicePdf(
        context: Context,
        settings: CompanySettings,
        sale: Sale,
        items: List<SaleItem>
    ): File? {
        try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595x842)
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint()
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                isAntiAlias = true
            }

            val titlePaint = Paint().apply {
                color = Color.rgb(15, 62, 125) // Primary Navy
                textSize = 20f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val subTitlePaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 10f
                isAntiAlias = true
            }

            val headerPaint = Paint().apply {
                color = Color.rgb(230, 240, 255)
            }

            // Header Banner
            canvas.drawRect(0f, 0f, 595f, 110f, headerPaint)
            canvas.drawText(settings.businessName, 40f, 45f, titlePaint)
            canvas.drawText("ঠিকানা: ${settings.address} | ফোন: ${settings.phone}", 40f, 70f, subTitlePaint)
            canvas.drawText("বিক্রয় ইনভয়েস (SALES INVOICE)", 40f, 95f, Paint().apply {
                color = Color.rgb(0, 121, 107)
                textSize = 14f
                isFakeBoldText = true
            })

            // Invoice Details Box
            var y = 140f
            textPaint.isFakeBoldText = true
            canvas.drawText("ইনভয়েস নং: ${sale.invoiceNumber}", 40f, y, textPaint)
            canvas.drawText("তারিখ: ${BengaliFormatter.formatDate(sale.createdAt)}", 330f, y, textPaint)
            y += 20f
            textPaint.isFakeBoldText = false
            canvas.drawText("কাস্টমার: ${sale.customerName}", 40f, y, textPaint)
            canvas.drawText("মোবাইল: ${sale.customerPhone}", 330f, y, textPaint)
            y += 20f
            canvas.drawText("বিক্রয় প্রতিনিধি: ${sale.salesmanName}", 40f, y, textPaint)
            canvas.drawText("পেমেন্ট মেথড: ${sale.paymentMethod}", 330f, y, textPaint)

            y += 35f
            // Table Header
            val tableHeadPaint = Paint().apply { color = Color.rgb(220, 230, 245) }
            canvas.drawRect(40f, y - 18f, 555f, y + 8f, tableHeadPaint)
            textPaint.isFakeBoldText = true
            canvas.drawText("ক্রম", 45f, y, textPaint)
            canvas.drawText("পণ্যের বিবরণ", 80f, y, textPaint)
            canvas.drawText("পরিমাণ", 330f, y, textPaint)
            canvas.drawText("দর (টাকা)", 400f, y, textPaint)
            canvas.drawText("মোট বিল (টাকা)", 480f, y, textPaint)
            y += 20f

            // Table Rows
            textPaint.isFakeBoldText = false
            items.forEachIndexed { index, item ->
                canvas.drawText("${index + 1}", 45f, y, textPaint)
                canvas.drawText(item.productName.take(32), 80f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "%.1f", item.quantity), 330f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "%.2f", item.unitPrice), 400f, y, textPaint)
                canvas.drawText(String.format(Locale.US, "%.2f", item.totalPrice), 480f, y, textPaint)
                y += 22f
            }

            // Divider Line
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 25f

            // Calculation Summary
            textPaint.textSize = 12f
            canvas.drawText("উপ-মোট (Subtotal):", 330f, y, textPaint)
            canvas.drawText("৳ ${String.format(Locale.US, "%.2f", sale.subtotal)}", 480f, y, textPaint)
            y += 20f

            if (sale.discount > 0) {
                canvas.drawText("ডিসকাউন্ট:", 330f, y, textPaint)
                canvas.drawText("- ৳ ${String.format(Locale.US, "%.2f", sale.discount)}", 480f, y, textPaint)
                y += 20f
            }

            textPaint.isFakeBoldText = true
            canvas.drawText("সর্বমোট বিল:", 330f, y, textPaint)
            canvas.drawText("৳ ${String.format(Locale.US, "%.2f", sale.totalAmount)}", 480f, y, textPaint)
            y += 20f

            val greenPaint = Paint().apply {
                color = Color.rgb(46, 125, 50)
                textSize = 12f
                isFakeBoldText = true
            }
            canvas.drawText("নগদ/জমা প্রদান:", 330f, y, greenPaint)
            canvas.drawText("৳ ${String.format(Locale.US, "%.2f", sale.paidAmount)}", 480f, y, greenPaint)
            y += 20f

            val duePaint = Paint().apply {
                color = if (sale.dueAmount > 0) Color.rgb(211, 47, 47) else Color.rgb(46, 125, 50)
                textSize = 13f
                isFakeBoldText = true
            }
            canvas.drawText("বর্তমান বকেয়া:", 330f, y, duePaint)
            canvas.drawText("৳ ${String.format(Locale.US, "%.2f", sale.dueAmount)}", 480f, y, duePaint)

            // Footer Notice
            y += 60f
            val notePaint = Paint().apply {
                color = Color.GRAY
                textSize = 11f
                isAntiAlias = true
            }
            canvas.drawText(settings.receiptFooter, 40f, y, notePaint)
            y += 40f
            canvas.drawLine(40f, y, 160f, y, linePaint)
            canvas.drawLine(430f, y, 550f, y, linePaint)
            y += 15f
            canvas.drawText("গ্রাহকের স্বাক্ষর", 55f, y, notePaint)
            canvas.drawText("অনুমোদিত স্বাক্ষর", 445f, y, notePaint)

            doc.finishPage(page)

            // Save PDF
            val outDir = File(context.cacheDir, "invoices")
            if (!outDir.exists()) outDir.mkdirs()
            val pdfFile = File(outDir, "${sale.invoiceNumber}.pdf")
            val outputStream = FileOutputStream(pdfFile)
            doc.writeTo(outputStream)
            doc.close()
            outputStream.flush()
            outputStream.close()
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun generateMoneyReceiptPdf(
        context: Context,
        settings: CompanySettings,
        receipt: PaymentReceipt
    ): File? {
        try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 500, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val bannerPaint = Paint().apply { color = Color.rgb(15, 62, 125) }
            canvas.drawRect(0f, 0f, 595f, 90f, bannerPaint)

            val headTitle = Paint().apply {
                color = Color.WHITE
                textSize = 20f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText(settings.businessName, 30f, 40f, headTitle)

            val headSub = Paint().apply {
                color = Color.rgb(200, 225, 255)
                textSize = 10f
                isAntiAlias = true
            }
            canvas.drawText("মোবাইল: ${settings.phone} | ${settings.address}", 30f, 65f, headSub)
            canvas.drawText("অফিসিয়াল মানি রিসিপ্ট", 380f, 50f, Paint().apply {
                color = Color.YELLOW
                textSize = 14f
                isFakeBoldText = true
            })

            var y = 130f
            val labelPaint = Paint().apply {
                color = Color.BLACK
                textSize = 13f
                isAntiAlias = true
            }
            val boldLabel = Paint().apply {
                color = Color.BLACK
                textSize = 13f
                isFakeBoldText = true
                isAntiAlias = true
            }

            canvas.drawText("রসিদ নম্বর: ${receipt.receiptNumber}", 30f, y, boldLabel)
            canvas.drawText("তারিখ: ${BengaliFormatter.formatDate(receipt.createdAt)}", 330f, y, boldLabel)

            y += 30f
            canvas.drawText("গ্রাহকের নাম: ${receipt.customerName}", 30f, y, labelPaint)
            canvas.drawText("মোবাইল: ${receipt.customerPhone}", 330f, y, labelPaint)

            y += 30f
            canvas.drawText("আদায়কারী প্রতিনিধির নাম: ${receipt.salesmanName}", 30f, y, labelPaint)
            canvas.drawText("পেমেন্ট মেথড: ${receipt.paymentMethod}", 330f, y, labelPaint)

            y += 40f
            val boxPaint = Paint().apply { color = Color.rgb(240, 248, 255) }
            canvas.drawRect(30f, y - 20f, 565f, y + 60f, boxPaint)

            val amtPaint = Paint().apply {
                color = Color.rgb(46, 125, 50)
                textSize = 16f
                isFakeBoldText = true
            }
            canvas.drawText("প্রাপ্ত টাকার পরিমাণ: ৳ ${String.format(Locale.US, "%.2f", receipt.amount)}", 50f, y + 5f, amtPaint)

            val dueSmall = Paint().apply {
                color = Color.DKGRAY
                textSize = 12f
            }
            canvas.drawText("পূর্ববর্তী বকেয়া: ৳ ${String.format(Locale.US, "%.2f", receipt.previousDue)}", 50f, y + 35f, dueSmall)
            canvas.drawText("অবশিষ্ট বকেয়া: ৳ ${String.format(Locale.US, "%.2f", receipt.currentDue)}", 330f, y + 35f, dueSmall)

            y += 110f
            canvas.drawText("নোট: ${if (receipt.note.isNotBlank()) receipt.note else "ধন্যবাদ"}", 30f, y, dueSmall)

            y += 50f
            val linePaint = Paint().apply { color = Color.LTGRAY }
            canvas.drawLine(30f, y, 160f, y, linePaint)
            canvas.drawLine(420f, y, 550f, y, linePaint)
            y += 15f
            canvas.drawText("গ্রাহকের স্বাক্ষর", 50f, y, dueSmall)
            canvas.drawText("আদায়কারীর স্বাক্ষর", 440f, y, dueSmall)

            doc.finishPage(page)

            val outDir = File(context.cacheDir, "receipts")
            if (!outDir.exists()) outDir.mkdirs()
            val pdfFile = File(outDir, "${receipt.receiptNumber}.pdf")
            val outputStream = FileOutputStream(pdfFile)
            doc.writeTo(outputStream)
            doc.close()
            outputStream.flush()
            outputStream.close()
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Multi-page Customer Due List PDF Report generator with automatic pagination,
     * header, footer, page numbering, totals and role subtitle.
     */
    fun generateCustomerDueListPdf(
        context: Context,
        settings: CompanySettings,
        dueCustomers: List<CustomerDueItem>,
        totalDueAmount: Double,
        generatedByRole: String,
        salesmanName: String? = null
    ): File? {
        try {
            val doc = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842 // Standard A4 in points
            val itemsPerPage = 22 // Fits comfortably per page with headers and footers
            val totalPages = if (dueCustomers.isEmpty()) 1 else ((dueCustomers.size + itemsPerPage - 1) / itemsPerPage)

            val titlePaint = Paint().apply {
                color = Color.rgb(15, 62, 125)
                textSize = 18f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val subTitlePaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 10f
                isAntiAlias = true
            }

            val tableHeadPaint = Paint().apply { color = Color.rgb(230, 240, 255) }
            val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                isAntiAlias = true
            }

            val boldPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val redPaint = Paint().apply {
                color = Color.rgb(211, 47, 47)
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val todayFormatted = SimpleDateFormat("dd MMM, yyyy - hh:mm a", Locale.ENGLISH).format(Date())

            for (pageIndex in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas

                // Header
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 90f, Paint().apply { color = Color.rgb(240, 245, 255) })
                canvas.drawText(settings.businessName, 30f, 35f, titlePaint)
                canvas.drawText("ঠিকানা: ${settings.address} | ফোন: ${settings.phone}", 30f, 52f, subTitlePaint)

                val reportTitle = if (salesmanName != null) {
                    "বকেয়া কাস্টমার রিপোর্ট (প্রতিনিধি: $salesmanName)"
                } else {
                    "সার্বিক কাস্টমার বকেয়া রিপোর্ট (Admin All Due List)"
                }
                canvas.drawText(reportTitle, 30f, 75f, Paint().apply {
                    color = Color.rgb(0, 121, 107)
                    textSize = 12f
                    isFakeBoldText = true
                    isAntiAlias = true
                })
                canvas.drawText("তারিখ: $todayFormatted", 370f, 75f, subTitlePaint)

                var y = 115f

                // Summary on first page
                if (pageIndex == 0) {
                    canvas.drawRect(30f, y - 15f, (pageWidth - 30).toFloat(), y + 25f, Paint().apply { color = Color.rgb(255, 243, 243) })
                    canvas.drawText("মোট বকেয়াদার কাস্টমার সংখ্যা: ${dueCustomers.size} জন", 40f, y + 5f, boldPaint)
                    canvas.drawText(
                        "সর্বমোট বকেয়ার পরিমাণ: ${BengaliFormatter.formatCurrency(totalDueAmount)}",
                        320f,
                        y + 5f,
                        Paint().apply {
                            color = Color.rgb(211, 47, 47)
                            textSize = 11f
                            isFakeBoldText = true
                        }
                    )
                    y += 50f
                }

                // Table Header
                canvas.drawRect(30f, y - 15f, (pageWidth - 30).toFloat(), y + 8f, tableHeadPaint)
                canvas.drawText("ক্রম", 35f, y, boldPaint)
                canvas.drawText("কাস্টমারের নাম ও ঠিকানা", 65f, y, boldPaint)
                canvas.drawText("মোবাইল নম্বর", 240f, y, boldPaint)
                canvas.drawText("সর্বশেষ লেনদেন", 345f, y, boldPaint)
                canvas.drawText("বর্তমান বকেয়া", 470f, y, boldPaint)
                y += 20f

                // Rows for this page
                val startIndex = pageIndex * itemsPerPage
                val endIndex = minOf(startIndex + itemsPerPage, dueCustomers.size)

                if (dueCustomers.isEmpty()) {
                    canvas.drawText("কোনো বকেয়া কাস্টমার পাওয়া যায়নি।", 40f, y + 10f, textPaint)
                    y += 30f
                } else {
                    for (i in startIndex until endIndex) {
                        val item = dueCustomers[i]
                        val rowNum = i + 1
                        canvas.drawText("$rowNum", 35f, y, textPaint)

                        val nameDisplay = if (item.customer.address.isNotBlank()) {
                            "${item.customer.name.take(24)} (${item.customer.address.take(14)})"
                        } else {
                            item.customer.name.take(30)
                        }
                        canvas.drawText(nameDisplay, 65f, y, boldPaint)
                        canvas.drawText(item.customer.phone, 240f, y, textPaint)

                        val lastDateStr = if (item.lastTransactionDate > 0) {
                            BengaliFormatter.formatDateOnly(item.lastTransactionDate)
                        } else {
                            "লেনদেন নেই"
                        }
                        canvas.drawText(lastDateStr, 345f, y, textPaint)

                        canvas.drawText(
                            BengaliFormatter.formatCurrency(item.customer.currentDue),
                            470f,
                            y,
                            redPaint
                        )

                        canvas.drawLine(30f, y + 5f, (pageWidth - 30).toFloat(), y + 5f, linePaint)
                        y += 22f
                    }
                }

                // Page Footer & Pagination
                val footerY = pageHeight - 35f
                canvas.drawLine(30f, footerY - 10f, (pageWidth - 30).toFloat(), footerY - 10f, linePaint)
                canvas.drawText("SmartHishabpati • গোপনীয় ব্যবসায়িক রিপোর্ট", 30f, footerY + 5f, subTitlePaint)
                canvas.drawText("পৃষ্ঠা ${pageIndex + 1} / $totalPages", (pageWidth - 90).toFloat(), footerY + 5f, subTitlePaint)

                doc.finishPage(page)
            }

            val outDir = File(context.cacheDir, "due_reports")
            if (!outDir.exists()) outDir.mkdirs()
            val dateFileName = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val prefix = if (salesmanName != null) "My_Due_Report" else "Customer_Due_Report"
            val pdfFile = File(outDir, "${prefix}_$dateFileName.pdf")
            val outputStream = FileOutputStream(pdfFile)
            doc.writeTo(outputStream)
            doc.close()
            outputStream.flush()
            outputStream.close()
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun generateCompanyStatementPdf(
        context: Context,
        settings: CompanySettings,
        company: Company,
        purchases: List<CompanyPurchase>,
        payments: List<CompanyPayment>
    ): File? {
        try {
            val doc = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842

            // Combine purchases and payments into chronological ledger
            data class LedgerItem(
                val date: Long,
                val type: String,
                val ref: String,
                val details: String,
                val debit: Double,  // Purchase amount (payable to company)
                val credit: Double  // Paid amount (paid to company)
            )

            val ledgerItems = mutableListOf<LedgerItem>()
            purchases.forEach { p ->
                ledgerItems.add(
                    LedgerItem(
                        date = p.purchaseDate,
                        type = "ক্রয়",
                        ref = p.challanNumber.ifBlank { p.id.takeLast(6) },
                        details = "${p.productName} (${p.quantity} ${p.unit})",
                        debit = p.totalAmount,
                        credit = p.paidAmount
                    )
                )
            }
            payments.forEach { pay ->
                ledgerItems.add(
                    LedgerItem(
                        date = pay.paymentDate,
                        type = "পেমেন্ট",
                        ref = pay.referenceNumber.ifBlank { pay.id.takeLast(6) },
                        details = "বকেয়া পরিশোধ (${pay.paymentMethod.name})${if (pay.note.isNotBlank()) " - ${pay.note}" else ""}",
                        debit = 0.0,
                        credit = pay.amount
                    )
                )
            }
            ledgerItems.sortByDescending { it.date }

            val itemsPerPage = 18
            val totalPages = if (ledgerItems.isEmpty()) 1 else ((ledgerItems.size - 1) / itemsPerPage) + 1

            val titlePaint = Paint().apply {
                color = Color.rgb(20, 35, 60)
                textSize = 15f
                isFakeBoldText = true
            }
            val subTitlePaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 9f
            }
            val boldPaint = Paint().apply {
                color = Color.BLACK
                textSize = 9.5f
                isFakeBoldText = true
            }
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 9f
            }
            val greenPaint = Paint().apply {
                color = Color.rgb(46, 125, 50)
                textSize = 9.5f
                isFakeBoldText = true
            }
            val redPaint = Paint().apply {
                color = Color.rgb(198, 40, 40)
                textSize = 9.5f
                isFakeBoldText = true
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 0.8f
            }
            val tableHeadPaint = Paint().apply {
                color = Color.rgb(240, 244, 248)
                style = Paint.Style.FILL
            }
            val boxPaint = Paint().apply {
                color = Color.rgb(245, 247, 250)
                style = Paint.Style.FILL
            }

            for (pageIndex in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas
                var y = 35f

                if (pageIndex == 0) {
                    canvas.drawText(settings.businessName, 30f, y, titlePaint)
                    y += 13f
                    canvas.drawText("${settings.address} | মোবা: ${settings.phone}", 30f, y, subTitlePaint)
                    y += 18f
                    canvas.drawLine(30f, y, (pageWidth - 30).toFloat(), y, linePaint)
                    y += 18f

                    // Title
                    canvas.drawText("সাপ্লায়ার / কোম্পানি লেনদেন স্টেটমেন্ট (Supplier Ledger)", 30f, y, boldPaint.apply { textSize = 12f })
                    y += 15f

                    // Summary Box
                    canvas.drawRoundRect(30f, y, (pageWidth - 30).toFloat(), y + 60f, 8f, 8f, boxPaint)
                    canvas.drawText("কোম্পানির নাম: ${company.name}", 40f, y + 16f, boldPaint.apply { textSize = 10f })
                    canvas.drawText("মোবাইল: ${company.phone}  |  ঠিকানা: ${company.address.ifBlank { "N/A" }}", 40f, y + 30f, subTitlePaint)

                    canvas.drawText("মোট ক্রয়: ${BengaliFormatter.formatCurrency(company.totalPurchased)}", 40f, y + 48f, boldPaint.apply { textSize = 9.5f })
                    canvas.drawText("মোট পরিশোধ: ${BengaliFormatter.formatCurrency(company.totalPaid)}", 220f, y + 48f, greenPaint)
                    if (company.currentDue > 0) {
                        canvas.drawText("বর্তমান বকেয়া: ${BengaliFormatter.formatCurrency(company.currentDue)}", 400f, y + 48f, redPaint)
                    } else if (company.advanceAmount > 0) {
                        canvas.drawText("অগ্রিম জমা: ${BengaliFormatter.formatCurrency(company.advanceAmount)}", 400f, y + 48f, greenPaint)
                    } else {
                        canvas.drawText("হিসাব: পরিশোধিত (পরিপূর্ণ)", 400f, y + 48f, greenPaint)
                    }
                    y += 75f
                } else {
                    canvas.drawText("${settings.businessName} • ${company.name} লেনদেন স্টেটমেন্ট (চলমান)", 30f, y, boldPaint)
                    y += 20f
                }

                // Table Header
                canvas.drawRect(30f, y - 13f, (pageWidth - 30).toFloat(), y + 7f, tableHeadPaint)
                canvas.drawText("তারিখ", 35f, y, boldPaint)
                canvas.drawText("ধরন", 105f, y, boldPaint)
                canvas.drawText("রেফারেন্স/চালান", 145f, y, boldPaint)
                canvas.drawText("বিবরণ", 230f, y, boldPaint)
                canvas.drawText("মোট মূল্য (৳)", 410f, y, boldPaint)
                canvas.drawText("পরিশোধ (৳)", 495f, y, boldPaint)
                y += 18f

                val startIndex = pageIndex * itemsPerPage
                val endIndex = minOf(startIndex + itemsPerPage, ledgerItems.size)

                if (ledgerItems.isEmpty()) {
                    canvas.drawText("কোনো লেনদেন পাওয়া যায়নি।", 40f, y + 10f, textPaint)
                    y += 25f
                } else {
                    for (i in startIndex until endIndex) {
                        val item = ledgerItems[i]
                        canvas.drawText(BengaliFormatter.formatDateOnly(item.date), 35f, y, textPaint)
                        canvas.drawText(item.type, 105f, y, boldPaint)
                        canvas.drawText(item.ref.take(14), 145f, y, textPaint)
                        canvas.drawText(item.details.take(28), 230f, y, textPaint)

                        val debitText = if (item.debit > 0) String.format(Locale.US, "%,.1f", item.debit) else "-"
                        canvas.drawText(debitText, 410f, y, textPaint)

                        val creditText = if (item.credit > 0) String.format(Locale.US, "%,.1f", item.credit) else "-"
                        canvas.drawText(creditText, 495f, y, greenPaint)

                        canvas.drawLine(30f, y + 5f, (pageWidth - 30).toFloat(), y + 5f, linePaint)
                        y += 21f
                    }
                }

                // Footer
                val footerY = pageHeight - 35f
                canvas.drawLine(30f, footerY - 10f, (pageWidth - 30).toFloat(), footerY - 10f, linePaint)
                canvas.drawText("SmartHishabpati • সাপ্লায়ার লেজার স্টেটমেন্ট", 30f, footerY + 5f, subTitlePaint)
                canvas.drawText("পৃষ্ঠা ${pageIndex + 1} / $totalPages", (pageWidth - 90).toFloat(), footerY + 5f, subTitlePaint)

                doc.finishPage(page)
            }

            val outDir = File(context.cacheDir, "company_statements")
            if (!outDir.exists()) outDir.mkdirs()
            val dateFileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeName = company.name.replace(" ", "_").take(15)
            val pdfFile = File(outDir, "Statement_${safeName}_$dateFileName.pdf")
            val outputStream = FileOutputStream(pdfFile)
            doc.writeTo(outputStream)
            doc.close()
            outputStream.flush()
            outputStream.close()
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun sharePdf(context: Context, file: File, title: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "পিডিএফ শেয়ার করুন"))
        } catch (e: Exception) {
            Toast.makeText(context, "শেয়ার করতে সমস্যা হয়েছে: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
