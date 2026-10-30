package com.example.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.util.BengaliFormatter
import com.example.util.BengaliVoiceHelper
import com.example.util.ReminderBroadcastReceiver
import com.example.util.SmartNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class SmartReminderManager(
    private val context: Context,
    private val db: AppDatabase,
    private val voiceHelper: BengaliVoiceHelper? = null
) {
    // In-memory set of deduplication keys notified in this session to prevent spamming notifications
    private val notifiedDeduplicationKeys = mutableSetOf<String>()

    suspend fun getPreferenceForUser(userId: String): ReminderPreference {
        return withContext(Dispatchers.IO) {
            db.reminderDao().getPreferenceForUser(userId) ?: ReminderPreference(userId = userId).also {
                db.reminderDao().insertOrUpdatePreference(it)
            }
        }
    }

    suspend fun savePreference(preference: ReminderPreference) {
        withContext(Dispatchers.IO) {
            db.reminderDao().insertOrUpdatePreference(preference.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Scans actual business database records and creates/updates reminders.
     * Strictly enforces role separation: Admin vs Salesman.
     */
    suspend fun scanAndSyncReminders(currentUser: User?): List<ReminderItem> = withContext(Dispatchers.IO) {
        if (currentUser == null) return@withContext emptyList()

        val role = currentUser.role
        val pref = getPreferenceForUser(currentUser.id)
        val generatedReminders = mutableListOf<ReminderItem>()

        if (role == UserRole.ADMIN) {
            syncAdminReminders(generatedReminders)
        } else if (role == UserRole.SALESMAN) {
            syncSalesmanReminders(currentUser, generatedReminders)
        }

        // Handle Notifications and Voice for newly identified high/medium priority alerts
        if (pref.isMasterEnabled) {
            val pendingAlerts = generatedReminders.filter { it.status == ReminderStatus.PENDING }
            for (alert in pendingAlerts) {
                if (!notifiedDeduplicationKeys.contains(alert.deduplicationKey)) {
                    notifiedDeduplicationKeys.add(alert.deduplicationKey)

                    if (pref.isNotificationEnabled) {
                        SmartNotificationHelper.sendNotification(context, alert)
                    }

                    if (pref.isVoiceEnabled && alert.priority == ReminderPriority.HIGH) {
                        voiceHelper?.speak(
                            text = "${alert.title}। ${alert.description}",
                            isVoiceEnabled = true,
                            quietHoursEnabled = pref.quietHoursEnabled,
                            quietHoursStart = pref.quietHoursStartHour,
                            quietHoursEnd = pref.quietHoursEndHour
                        )
                    }
                }
            }
        }

        generatedReminders
    }

    private suspend fun syncAdminReminders(output: MutableList<ReminderItem>) {
        val now = System.currentTimeMillis()

        // 1. Stock Alert: Low stock products
        val allProducts = db.productDao().getAllProducts().first()
        val lowStockProducts = allProducts.filter { it.isActive && it.currentStock <= it.alertStockLevel }

        for (product in lowStockProducts) {
            val dedupKey = "STOCK_ALERT_${product.id}"
            val existing = db.reminderDao().getReminderByDeduplicationKey(dedupKey)
            if (existing == null || existing.status == ReminderStatus.PENDING) {
                val item = ReminderItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    deduplicationKey = dedupKey,
                    roleTarget = UserRole.ADMIN,
                    targetUserId = null,
                    title = "স্টক সংকট: ${product.name}",
                    description = "বর্তমান মজুদ মাত্র ${BengaliFormatter.formatNumber(product.currentStock)} ${product.unit} (সতর্কবার্তা স্তর: ${BengaliFormatter.formatNumber(product.alertStockLevel)} ${product.unit})। নতুন পণ্য সরবরাহ প্রয়োজন।",
                    category = ReminderCategory.STOCK_ALERT,
                    priority = ReminderPriority.HIGH,
                    status = ReminderStatus.PENDING,
                    targetRoute = "PRODUCTS_STOCK",
                    targetEntityId = product.id,
                    scheduledTime = now,
                    createdAt = existing?.createdAt ?: now,
                    isSystemGenerated = true
                )
                db.reminderDao().insertReminder(item)
                output.add(item)
            }
        }
        // Auto-complete stock alerts for replenished products
        for (product in allProducts.filter { it.isActive && it.currentStock > it.alertStockLevel }) {
            db.reminderDao().completeByDeduplicationKey("STOCK_ALERT_${product.id}")
        }

        // 2. Pending Expenses Approval
        val allExpenses = db.expenseDao().getAllExpenses().first()
        val pendingExpenses = allExpenses.filter { it.status == ExpenseStatus.PENDING }
        val expenseDedupKey = "EXPENSE_APPROVAL_PENDING"

        if (pendingExpenses.isNotEmpty()) {
            val totalPendingAmount = pendingExpenses.sumOf { it.amount }
            val existing = db.reminderDao().getReminderByDeduplicationKey(expenseDedupKey)
            val item = ReminderItem(
                id = existing?.id ?: UUID.randomUUID().toString(),
                deduplicationKey = expenseDedupKey,
                roleTarget = UserRole.ADMIN,
                targetUserId = null,
                title = "খরচের আবেদন অনুমোদন বাকি",
                description = "${BengaliFormatter.formatNumber(pendingExpenses.size.toDouble())}টি খরচের আবেদন এডমিন অনুমোদনের অপেক্ষায় (মোট দাবি: ${BengaliFormatter.formatCurrency(totalPendingAmount)})।",
                category = ReminderCategory.EXPENSE_APPROVAL,
                priority = ReminderPriority.HIGH,
                status = ReminderStatus.PENDING,
                targetRoute = "EXPENSES",
                scheduledTime = now,
                createdAt = existing?.createdAt ?: now,
                isSystemGenerated = true
            )
            db.reminderDao().insertReminder(item)
            output.add(item)
        } else {
            db.reminderDao().completeByDeduplicationKey(expenseDedupKey)
        }

        // 3. Pending Product Submissions
        val allSubmissions = db.productSubmissionDao().getAllSubmissions().first()
        val pendingSubmissions = allSubmissions.filter { it.status == ProductSubmissionStatus.PENDING }
        val submissionDedupKey = "PRODUCT_APPROVAL_PENDING"

        if (pendingSubmissions.isNotEmpty()) {
            val existing = db.reminderDao().getReminderByDeduplicationKey(submissionDedupKey)
            val item = ReminderItem(
                id = existing?.id ?: UUID.randomUUID().toString(),
                deduplicationKey = submissionDedupKey,
                roleTarget = UserRole.ADMIN,
                targetUserId = null,
                title = "নতুন পণ্য অনুমোদন বাকি",
                description = "${BengaliFormatter.formatNumber(pendingSubmissions.size.toDouble())}টি নতুন পণ্য পর্যালোচনার অপেক্ষায়। সেলসম্যান জমা দিয়েছেন।",
                category = ReminderCategory.PRODUCT_APPROVAL,
                priority = ReminderPriority.MEDIUM,
                status = ReminderStatus.PENDING,
                targetRoute = "ADMIN_PRODUCT_APPROVAL",
                scheduledTime = now,
                createdAt = existing?.createdAt ?: now,
                isSystemGenerated = true
            )
            db.reminderDao().insertReminder(item)
            output.add(item)
        } else {
            db.reminderDao().completeByDeduplicationKey(submissionDedupKey)
        }

        // 4. Customer Due Followup for top due customers
        val customers = db.customerDao().getAllCustomers().first()
        val highDueCustomers = customers.filter { it.currentDue >= 5000.0 }.sortedByDescending { it.currentDue }.take(5)

        for (customer in highDueCustomers) {
            val dueDedupKey = "CUSTOMER_DUE_${customer.id}"
            val existing = db.reminderDao().getReminderByDeduplicationKey(dueDedupKey)
            if (existing == null || existing.status == ReminderStatus.PENDING) {
                val item = ReminderItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    deduplicationKey = dueDedupKey,
                    roleTarget = UserRole.ADMIN,
                    targetUserId = null,
                    title = "বকেয়া আদায় তাগাদা: ${customer.name}",
                    description = "গ্রাহকের বর্তমান বকেয়া ${BengaliFormatter.formatCurrency(customer.currentDue)}। যোগাযোগ: ${customer.phone}",
                    category = ReminderCategory.CUSTOMER_DUE,
                    priority = ReminderPriority.MEDIUM,
                    status = ReminderStatus.PENDING,
                    targetRoute = "CUSTOMERS",
                    targetEntityId = customer.id,
                    scheduledTime = now,
                    createdAt = existing?.createdAt ?: now,
                    isSystemGenerated = true
                )
                db.reminderDao().insertReminder(item)
                output.add(item)
            }
        }
        for (customer in customers.filter { it.currentDue <= 0.0 }) {
            db.reminderDao().completeByDeduplicationKey("CUSTOMER_DUE_${customer.id}")
        }

        // 5. Daily Sales & Closing Audit
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        val dailyDedupKey = "DAILY_CLOSING_$todayStr"
        val existingDaily = db.reminderDao().getReminderByDeduplicationKey(dailyDedupKey)
        if (existingDaily == null) {
            val item = ReminderItem(
                id = UUID.randomUUID().toString(),
                deduplicationKey = dailyDedupKey,
                roleTarget = UserRole.ADMIN,
                targetUserId = null,
                title = "দিনের হিসাব ও ক্যাশ ড্রয়ার মিলানো",
                description = "দিনের মোট বিক্রি ও ক্যাশ আদায়ের হিসাব যাচাই করুন এবং হিসাব ক্লোজিং সম্পন্ন করুন।",
                category = ReminderCategory.DAILY_CLOSING,
                priority = ReminderPriority.MEDIUM,
                status = ReminderStatus.PENDING,
                targetRoute = "REPORTS",
                scheduledTime = now,
                createdAt = now,
                isSystemGenerated = true
            )
            db.reminderDao().insertReminder(item)
            output.add(item)
        }

        // 6. Overdue Stock Verification & Pending Mismatches
        val allSchedules = db.stockVerificationDao().getAllSchedules()
        val overdueSchedules = allSchedules.filter { it.isReminderEnabled && it.nextDueAt > 0L && now > it.nextDueAt }
        if (overdueSchedules.isNotEmpty()) {
            val overdueKey = "STOCK_VERIFICATION_OVERDUE"
            val existing = db.reminderDao().getReminderByDeduplicationKey(overdueKey)
            val isUrgent = overdueSchedules.any { (now - it.nextDueAt) > (7L * 24 * 60 * 60 * 1000L) }
            val item = ReminderItem(
                id = existing?.id ?: UUID.randomUUID().toString(),
                deduplicationKey = overdueKey,
                roleTarget = UserRole.ADMIN,
                targetUserId = null,
                title = if (isUrgent) "জরুরি: দীর্ঘদিন স্টক যাচাই বাকি!" else "দোকানের স্টক যাচাইয়ের সময় পার হয়েছে",
                description = "${BengaliFormatter.formatNumber(overdueSchedules.size.toDouble())}টি পণ্যের নির্ধারিত যাচাইয়ের সময়সীমা পার হয়েছে। সেলসম্যানকে গণনা করার নির্দেশ দিন।",
                category = ReminderCategory.STOCK_ALERT,
                priority = if (isUrgent) ReminderPriority.HIGH else ReminderPriority.MEDIUM,
                status = ReminderStatus.PENDING,
                targetRoute = "ADMIN_STOCK_CONTROL",
                scheduledTime = now,
                createdAt = existing?.createdAt ?: now,
                isSystemGenerated = true
            )
            db.reminderDao().insertReminder(item)
            output.add(item)
        } else {
            db.reminderDao().completeByDeduplicationKey("STOCK_VERIFICATION_OVERDUE")
        }
    }

    private suspend fun syncSalesmanReminders(salesman: User, output: MutableList<ReminderItem>) {
        val now = System.currentTimeMillis()
        val salesmanId = salesman.id

        // 1. Salesman's pending expenses
        val myExpenses = db.expenseDao().getExpensesBySalesman(salesmanId).first()
        val myPending = myExpenses.filter { it.status == ExpenseStatus.PENDING }
        val expDedupKey = "SALESMAN_EXPENSE_PENDING_$salesmanId"

        if (myPending.isNotEmpty()) {
            val existing = db.reminderDao().getReminderByDeduplicationKey(expDedupKey)
            val item = ReminderItem(
                id = existing?.id ?: UUID.randomUUID().toString(),
                deduplicationKey = expDedupKey,
                roleTarget = UserRole.SALESMAN,
                targetUserId = salesmanId,
                title = "খরচের আবেদন প্রক্রিয়াধীন",
                description = "আপনার ${BengaliFormatter.formatNumber(myPending.size.toDouble())}টি খরচের আবেদন এডমিন পর্যালোচনায় রয়েছে।",
                category = ReminderCategory.SALESMAN_FOLLOWUP,
                priority = ReminderPriority.LOW,
                status = ReminderStatus.PENDING,
                targetRoute = "EXPENSES",
                scheduledTime = now,
                createdAt = existing?.createdAt ?: now,
                isSystemGenerated = true
            )
            db.reminderDao().insertReminder(item)
            output.add(item)
        } else {
            db.reminderDao().completeByDeduplicationKey(expDedupKey)
        }

        // 2. Salesman's product submissions needing correction
        val mySubmissions = db.productSubmissionDao().getSubmissionsBySalesman(salesmanId).first()
        val needsCorrection = mySubmissions.filter { it.status == ProductSubmissionStatus.NEEDS_CORRECTION }

        for (sub in needsCorrection) {
            val dedupKey = "SALESMAN_SUBMISSION_CORRECTION_${sub.id}"
            val existing = db.reminderDao().getReminderByDeduplicationKey(dedupKey)
            if (existing == null || existing.status == ReminderStatus.PENDING) {
                val item = ReminderItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    deduplicationKey = dedupKey,
                    roleTarget = UserRole.SALESMAN,
                    targetUserId = salesmanId,
                    title = "পণ্য সংশোধনের নোট: ${sub.name}",
                    description = "এডমিন নোট: ${sub.adminCorrectionNote.ifBlank { "সংশোধন করে পুনরায় জমা দিন" }}",
                    category = ReminderCategory.PRODUCT_APPROVAL,
                    priority = ReminderPriority.HIGH,
                    status = ReminderStatus.PENDING,
                    targetRoute = "SALESMAN_SUBMITTED_PRODUCTS",
                    targetEntityId = sub.id,
                    scheduledTime = now,
                    createdAt = existing?.createdAt ?: now,
                    isSystemGenerated = true
                )
                db.reminderDao().insertReminder(item)
                output.add(item)
            }
        }

        // 3. Customer follow-up for customers with due
        val customers = db.customerDao().getAllCustomers().first()
        val dueCustomers = customers.filter { it.currentDue > 0 }.take(4)

        for (cust in dueCustomers) {
            val dedupKey = "SALESMAN_CUST_DUE_${cust.id}_$salesmanId"
            val existing = db.reminderDao().getReminderByDeduplicationKey(dedupKey)
            if (existing == null || existing.status == ReminderStatus.PENDING) {
                val item = ReminderItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    deduplicationKey = dedupKey,
                    roleTarget = UserRole.SALESMAN,
                    targetUserId = salesmanId,
                    title = "কাস্টমার তাগাদা: ${cust.name}",
                    description = "বকেয়া: ${BengaliFormatter.formatCurrency(cust.currentDue)}। ফোন: ${cust.phone}। কালেকশনের চেষ্টা করুন।",
                    category = ReminderCategory.SALESMAN_FOLLOWUP,
                    priority = ReminderPriority.MEDIUM,
                    status = ReminderStatus.PENDING,
                    targetRoute = "CUSTOMERS",
                    targetEntityId = cust.id,
                    scheduledTime = now,
                    createdAt = existing?.createdAt ?: now,
                    isSystemGenerated = true
                )
                db.reminderDao().insertReminder(item)
                output.add(item)
            }
        }

        // 4. Overdue Stock Verification for Salesman
        val allSchedules = db.stockVerificationDao().getAllSchedules()
        val myOverdue = allSchedules.filter {
            it.isReminderEnabled && it.nextDueAt > 0L && now > it.nextDueAt &&
            (it.assignedSalesmanId == null || it.assignedSalesmanId == salesmanId)
        }
        if (myOverdue.isNotEmpty()) {
            val salesDedupKey = "SALESMAN_STOCK_VERIFICATION_OVERDUE_$salesmanId"
            val existing = db.reminderDao().getReminderByDeduplicationKey(salesDedupKey)
            val item = ReminderItem(
                id = existing?.id ?: UUID.randomUUID().toString(),
                deduplicationKey = salesDedupKey,
                roleTarget = UserRole.SALESMAN,
                targetUserId = salesmanId,
                title = "দোকানের স্টক যাচাই বাকি রয়েছে",
                description = "${BengaliFormatter.formatNumber(myOverdue.size.toDouble())}টি পণ্যের বাস্তব গণনা সম্পন্ন করে অ্যাপে জমা দিন।",
                category = ReminderCategory.STOCK_ALERT,
                priority = ReminderPriority.MEDIUM,
                status = ReminderStatus.PENDING,
                targetRoute = "SALESMAN_STOCK_VERIFICATION",
                scheduledTime = now,
                createdAt = existing?.createdAt ?: now,
                isSystemGenerated = true
            )
            db.reminderDao().insertReminder(item)
            output.add(item)
        } else {
            db.reminderDao().completeByDeduplicationKey("SALESMAN_STOCK_VERIFICATION_OVERDUE_$salesmanId")
        }
    }

    /**
     * Schedules periodic check via AlarmManager.
     */
    fun schedulePeriodicCheck() {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                action = ReminderBroadcastReceiver.ACTION_CHECK_REMINDERS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Repeating interval ~ 30 minutes
            val interval = 30 * 60 * 1000L
            val triggerAt = SystemClock.elapsedRealtime() + interval
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                interval,
                pendingIntent
            )
        } catch (_: Exception) {}
    }
}
