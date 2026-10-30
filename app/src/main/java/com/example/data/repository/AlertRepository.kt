package com.example.data.repository

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.util.BengaliVoiceHelper
import com.example.util.SmartNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class AlertRepository(
    private val db: AppDatabase,
    private val context: Context,
    private val voiceHelper: BengaliVoiceHelper? = null
) {
    val allAlertsFlow: Flow<List<AdminAlert>> = db.alertDao().getAllAlertsFlow()

    /**
     * Combined flow for Admin showing all alerts and per-salesman receipt acknowledgements.
     */
    val adminAlertDetailsFlow: Flow<List<AdminAlertDetail>> = combine(
        db.alertDao().getAllAlertsFlow(),
        db.alertDao().getAllReceiptsFlow()
    ) { alerts, receipts ->
        val receiptsByAlert = receipts.groupBy { it.alertId }
        alerts.map { alert ->
            AdminAlertDetail(
                alert = alert,
                receipts = receiptsByAlert[alert.id] ?: emptyList()
            )
        }
    }

    /**
     * Role-restricted flow for Salesman: returns only alerts directed to this salesman that are active and due.
     */
    fun getSalesmanAlertsFlow(salesmanId: String): Flow<List<SalesmanAlertView>> {
        return combine(
            db.alertDao().getReceiptsForSalesmanFlow(salesmanId),
            db.alertDao().getAllAlertsFlow()
        ) { receipts, alerts ->
            val alertsById = alerts.associateBy { it.id }
            val now = System.currentTimeMillis()
            receipts.mapNotNull { receipt ->
                val alert = alertsById[receipt.alertId]
                if (alert != null && !alert.isCancelled && (!alert.isScheduled || alert.scheduledTime <= now)) {
                    SalesmanAlertView(alert = alert, receipt = receipt)
                } else {
                    null
                }
            }
        }
    }

    /**
     * Unread/Pending count for Salesman badge.
     */
    fun getSalesmanPendingCountFlow(salesmanId: String): Flow<Int> {
        return getSalesmanAlertsFlow(salesmanId).map { list ->
            list.count { !it.receipt.isCompleted }
        }
    }

    /**
     * Create and send alert from Admin to Salesman / All Salesmen.
     */
    suspend fun createAlert(
        admin: User,
        targetType: AlertTargetType,
        targetSalesman: User?,
        title: String,
        message: String,
        priority: AlertPriority,
        isVoiceEnabled: Boolean,
        isNotificationEnabled: Boolean,
        scheduledTime: Long?
    ): Result<AdminAlert> = withContext(Dispatchers.IO) {
        if (admin.role != UserRole.ADMIN) {
            return@withContext Result.failure(SecurityException("শুধুমাত্র অনুমোদিত এডমিন নির্দেশ পাঠাতে পারেন।"))
        }

        if (title.isBlank() || message.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("শিরোনাম এবং বিস্তারিত বার্তা আবশ্যক।"))
        }

        val now = System.currentTimeMillis()
        val isScheduled = scheduledTime != null && scheduledTime > now
        val schedTime = if (isScheduled) scheduledTime!! else 0L

        val alertId = UUID.randomUUID().toString()
        val alert = AdminAlert(
            id = alertId,
            adminId = admin.id,
            adminName = admin.name,
            targetType = targetType,
            targetSalesmanId = if (targetType == AlertTargetType.SPECIFIC) targetSalesman?.id else null,
            targetSalesmanName = if (targetType == AlertTargetType.SPECIFIC) targetSalesman?.name else null,
            title = title.trim(),
            message = message.trim(),
            priority = priority,
            isVoiceEnabled = isVoiceEnabled,
            isNotificationEnabled = isNotificationEnabled,
            scheduledTime = schedTime,
            isScheduled = isScheduled,
            isCancelled = false,
            createdAt = now
        )

        db.alertDao().insertAlert(alert)

        // Generate Receipts
        val receipts = mutableListOf<SalesmanAlertReceipt>()
        if (targetType == AlertTargetType.SPECIFIC && targetSalesman != null) {
            receipts.add(
                SalesmanAlertReceipt(
                    id = "rcpt_${alertId}_${targetSalesman.id}",
                    alertId = alertId,
                    salesmanId = targetSalesman.id,
                    salesmanName = targetSalesman.name,
                    isDelivered = !isScheduled,
                    deliveredAt = if (!isScheduled) now else null
                )
            )
        } else if (targetType == AlertTargetType.ALL) {
            val allUsers = db.userDao().getAllActiveUsers().first()
            val salesmen = allUsers.filter { it.role == UserRole.SALESMAN }
            for (sm in salesmen) {
                receipts.add(
                    SalesmanAlertReceipt(
                        id = "rcpt_${alertId}_${sm.id}",
                        alertId = alertId,
                        salesmanId = sm.id,
                        salesmanName = sm.name,
                        isDelivered = !isScheduled,
                        deliveredAt = if (!isScheduled) now else null
                    )
                )
            }
        }

        if (receipts.isNotEmpty()) {
            db.alertDao().insertReceipts(receipts)
        }

        // Trigger Notification if immediate
        if (!isScheduled && isNotificationEnabled) {
            val reminderEquivalent = ReminderItem(
                id = alertId,
                deduplicationKey = "ALERT_$alertId",
                roleTarget = UserRole.SALESMAN,
                targetUserId = if (targetType == AlertTargetType.SPECIFIC) targetSalesman?.id else null,
                title = if (priority == AlertPriority.URGENT) "🚨 জরুরি নির্দেশ: $title" else "নির্দেশ: $title",
                description = message,
                category = ReminderCategory.SALESMAN_FOLLOWUP,
                priority = if (priority == AlertPriority.URGENT) ReminderPriority.HIGH else ReminderPriority.MEDIUM,
                status = ReminderStatus.PENDING,
                targetRoute = "SALESMAN_ALERTS",
                createdAt = now
            )
            SmartNotificationHelper.sendNotification(context, reminderEquivalent)
        }

        Result.success(alert)
    }

    /**
     * Salesman acknowledges ("বুঝেছি").
     */
    suspend fun acknowledgeAlert(receiptId: String, currentSalesmanId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val receipt = db.alertDao().getReceiptById(receiptId)
            ?: return@withContext Result.failure(IllegalArgumentException("নির্দেশটি পাওয়া যায়নি।"))

        if (receipt.salesmanId != currentSalesmanId) {
            return@withContext Result.failure(SecurityException("অননুমোদিত অ্যাক্সেস! এটি আপনার নির্দেশ নয়।"))
        }

        db.alertDao().acknowledgeReceipt(receiptId, System.currentTimeMillis())
        Result.success(Unit)
    }

    /**
     * Salesman completes task ("কাজ সম্পন্ন").
     */
    suspend fun completeAlert(receiptId: String, currentSalesmanId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val receipt = db.alertDao().getReceiptById(receiptId)
            ?: return@withContext Result.failure(IllegalArgumentException("নির্দেশটি পাওয়া যায়নি।"))

        if (receipt.salesmanId != currentSalesmanId) {
            return@withContext Result.failure(SecurityException("অননুমোদিত অ্যাক্সেস! এটি আপনার নির্দেশ নয়।"))
        }

        db.alertDao().completeReceipt(receiptId, System.currentTimeMillis())
        Result.success(Unit)
    }

    /**
     * Admin cancels scheduled alert.
     */
    suspend fun cancelAlert(alertId: String, admin: User): Result<Unit> = withContext(Dispatchers.IO) {
        if (admin.role != UserRole.ADMIN) {
            return@withContext Result.failure(SecurityException("শুধুমাত্র এডমিন নির্দেশ বাতিল করতে পারেন।"))
        }
        db.alertDao().cancelAlert(alertId)
        Result.success(Unit)
    }

    /**
     * Background check: dispatch scheduled alerts that are now due.
     */
    suspend fun checkAndDispatchScheduledAlerts(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dueAlerts = db.alertDao().getDueScheduledAlerts(now)
        var count = 0
        for (alert in dueAlerts) {
            val receipts = db.alertDao().getReceiptsForAlert(alert.id)
            val updated = receipts.map {
                it.copy(isDelivered = true, deliveredAt = now)
            }
            db.alertDao().insertReceipts(updated)
            db.alertDao().updateAlert(alert.copy(isScheduled = false))

            if (alert.isNotificationEnabled) {
                val reminderEquivalent = ReminderItem(
                    id = alert.id,
                    deduplicationKey = "ALERT_${alert.id}",
                    roleTarget = UserRole.SALESMAN,
                    targetUserId = alert.targetSalesmanId,
                    title = if (alert.priority == AlertPriority.URGENT) "🚨 জরুরি নির্দেশ: ${alert.title}" else "নির্দেশ: ${alert.title}",
                    description = alert.message,
                    category = ReminderCategory.SALESMAN_FOLLOWUP,
                    priority = if (alert.priority == AlertPriority.URGENT) ReminderPriority.HIGH else ReminderPriority.MEDIUM,
                    status = ReminderStatus.PENDING,
                    targetRoute = "SALESMAN_ALERTS",
                    createdAt = now
                )
                SmartNotificationHelper.sendNotification(context, reminderEquivalent)
            }
            count++
        }
        count
    }
}
