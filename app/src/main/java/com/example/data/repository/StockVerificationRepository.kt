package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class AdminMismatchAction {
    APPROVE_AND_ADJUST, // অনুমোদন ও স্টক সংশোধন
    REJECT,             // প্রত্যাখ্যান (স্টক অপরিবর্তিত)
    REQUEST_RECOUNT     // পুনরায় গণনার নির্দেশ
}

class StockVerificationRepository(
    private val db: AppDatabase,
    private val alertRepository: AlertRepository? = null
) {
    private val verificationDao = db.stockVerificationDao()
    private val productDao = db.productDao()
    private val stockLogDao = db.stockLogDao()
    private val auditLogDao = db.auditLogDao()
    private val reminderDao = db.reminderDao()
    private val alertDao = db.alertDao()

    // --- Flows ---
    fun getAllVerificationsFlow(): Flow<List<StockVerification>> = verificationDao.getAllVerificationsFlow()
    fun getRecentVerificationsFlow(limit: Int = 150): Flow<List<StockVerification>> = verificationDao.getRecentVerificationsFlow(limit)
    fun getVerificationsForProductFlow(productId: String): Flow<List<StockVerification>> = verificationDao.getVerificationsForProductFlow(productId)
    fun getPendingApprovalsFlow(): Flow<List<StockVerification>> = verificationDao.getPendingApprovalsFlow()
    fun getPendingApprovalCountFlow(): Flow<Int> = verificationDao.getPendingApprovalCountFlow()
    fun getVerificationsByVerifierFlow(userId: String): Flow<List<StockVerification>> = verificationDao.getVerificationsByVerifierFlow(userId)
    fun getAllSchedulesFlow(): Flow<List<StockVerificationSchedule>> = verificationDao.getAllSchedulesFlow()

    suspend fun getVerificationById(id: String): StockVerification? = verificationDao.getVerificationById(id)

    /**
     * Ensure all active products have a default schedule (e.g. 7 days) if missing.
     */
    suspend fun ensureSchedulesForProducts(products: List<Product>) {
        val existingSchedules = verificationDao.getAllSchedules().associateBy { it.productId }
        val now = System.currentTimeMillis()

        for (product in products) {
            if (!existingSchedules.containsKey(product.id)) {
                val defaultIntervalDays = 7
                val nextDue = now + (defaultIntervalDays * 24L * 60 * 60 * 1000L)
                verificationDao.insertOrUpdateSchedule(
                    StockVerificationSchedule(
                        productId = product.id,
                        intervalDays = defaultIntervalDays,
                        lastVerifiedAt = 0L,
                        nextDueAt = nextDue,
                        isReminderEnabled = true,
                        updatedAt = now
                    )
                )
            }
        }
    }

    /**
     * Submits a physical stock count verification.
     * Crucial: Submitting does NOT alter live product stock if there is a mismatch!
     * Only exact matches are automatically marked resolved.
     */
    suspend fun submitStockVerification(
        productId: String,
        physicalStock: Double,
        notes: String,
        imageUri: String?,
        actorUser: User
    ): StockVerification {
        val product = productDao.getProductById(productId)
            ?: throw IllegalArgumentException("পণ্য খুঁজে পাওয়া যায়নি!")

        val now = System.currentTimeMillis()
        val appStockSnapshot = product.currentStock
        val difference = physicalStock - appStockSnapshot
        val differenceType = when {
            difference == 0.0 -> StockDifferenceType.EXACT_MATCH
            difference < 0.0 -> StockDifferenceType.SHORTAGE
            else -> StockDifferenceType.SURPLUS
        }

        val isMatched = (difference == 0.0)
        val initialStatus = if (isMatched) VerificationStatus.MATCHED else VerificationStatus.MISMATCH_PENDING
        val isResolved = isMatched

        val verification = StockVerification(
            id = "verif_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            productId = product.id,
            productName = product.name,
            productSku = product.sku,
            productCategory = product.category,
            productUnit = product.unit,
            appStockSnapshot = appStockSnapshot,
            physicalStock = physicalStock,
            difference = difference,
            differenceType = differenceType,
            verifierUserId = actorUser.id,
            verifierUserName = actorUser.name,
            verifierRole = actorUser.role,
            verifiedAt = now,
            notes = notes.trim(),
            imageUri = imageUri,
            status = initialStatus,
            reviewedByAdminId = if (isMatched && actorUser.role == UserRole.ADMIN) actorUser.id else null,
            reviewedByAdminName = if (isMatched && actorUser.role == UserRole.ADMIN) actorUser.name else null,
            reviewedAt = if (isMatched && actorUser.role == UserRole.ADMIN) now else null,
            adminNote = if (isMatched) "সঠিক মিলেছে (স্বয়ংক্রিয়)" else null,
            isResolved = isResolved
        )

        db.withTransaction {
            verificationDao.insertVerification(verification)

            if (isMatched) {
                // Update schedule next due date
                val currentSchedule = verificationDao.getScheduleForProduct(product.id)
                val intervalDays = currentSchedule?.intervalDays ?: 7
                val nextDue = now + (intervalDays * 24L * 60 * 60 * 1000L)
                verificationDao.updateScheduleVerified(product.id, lastVerifiedAt = now, nextDueAt = nextDue, updatedAt = now)

                // Audit log
                auditLogDao.insertAuditLog(
                    AuditLog(
                        userId = actorUser.id,
                        userName = actorUser.name,
                        action = "স্টক যাচাই মিলেছে",
                        details = "${product.name} — স্টক সঠিক পাওয়া গেছে ($physicalStock ${product.unit})"
                    )
                )

                // Dismiss any pending stock audit reminder for this product
                val reminderKey = "VERIF_${product.id}"
                reminderDao.getReminderByDeduplicationKey(reminderKey)?.let { rem ->
                    reminderDao.updateStatus(rem.id, ReminderStatus.COMPLETED, now)
                }
            } else {
                // Mismatch found
                val diffText = if (difference < 0) "ঘাটতি: ${-difference}" else "অতিরিক্ত: +$difference"
                auditLogDao.insertAuditLog(
                    AuditLog(
                        userId = actorUser.id,
                        userName = actorUser.name,
                        action = "স্টক অমিল রিপোর্ট",
                        details = "${product.name} — অ্যাপে: $appStockSnapshot, বাস্তবে: $physicalStock ($diffText ${product.unit}). এডমিনের অনুমোদন আবশ্যক।"
                    )
                )

                // Trigger in-app Reminder for Admin to review
                val reminderKey = "MISMATCH_VERIF_${verification.id}"
                reminderDao.insertReminder(
                    ReminderItem(
                        id = "rem_mismatch_${UUID.randomUUID().toString().take(8)}",
                        deduplicationKey = reminderKey,
                        roleTarget = UserRole.ADMIN,
                        title = "স্টক অমিল যাচাই আবশ্যক",
                        description = "${product.name} পণ্যে $diffText ${product.unit} অমিল পাওয়া গেছে (${actorUser.name} কর্তৃক)। অনুগ্রহ করে পর্যালোচনা করুন।",
                        category = ReminderCategory.STOCK_ALERT,
                        priority = ReminderPriority.HIGH,
                        status = ReminderStatus.PENDING,
                        targetRoute = "ADMIN_STOCK_CONTROL",
                        targetEntityId = verification.id,
                        scheduledTime = now,
                        isSystemGenerated = true
                    )
                )
            }
        }

        return verification
    }

    /**
     * Admin review workflow for stock mismatches.
     * Strictly restricted to ADMIN role.
     */
    suspend fun reviewStockMismatch(
        verificationId: String,
        action: AdminMismatchAction,
        adminNote: String,
        actorAdmin: User
    ) {
        if (actorAdmin.role != UserRole.ADMIN) {
            throw SecurityException("অননুমোদিত অ্যাক্সেস! শুধুমাত্র এডমিন স্টক অমিল অনুমোদন বা সংশোধন করতে পারেন।")
        }

        val verification = verificationDao.getVerificationById(verificationId)
            ?: throw IllegalArgumentException("যাচাই রেকর্ড খুঁজে পাওয়া যায়নি!")

        val now = System.currentTimeMillis()

        db.withTransaction {
            when (action) {
                AdminMismatchAction.APPROVE_AND_ADJUST -> {
                    // Safe adjustment of live product stock
                    productDao.adjustStock(verification.productId, verification.difference)
                    val updatedProd = productDao.getProductById(verification.productId)

                    // Insert detailed StockLog
                    stockLogDao.insertStockLog(
                        StockLog(
                            productId = verification.productId,
                            productName = verification.productName,
                            changeType = "স্টক যাচাই সমন্বয়",
                            quantityChanged = verification.difference,
                            remainingStock = updatedProd?.currentStock ?: 0.0,
                            referenceId = verification.id,
                            reason = "এডমিন কর্তৃক স্টক যাচাই অনুমোদিত ও সমন্বিত: ${verification.notes}. এডমিন নোট: ${adminNote.ifBlank { "অনুমোদিত" }}"
                        )
                    )

                    // Update verification record
                    val updatedVerification = verification.copy(
                        status = VerificationStatus.APPROVED_ADJUSTED,
                        reviewedByAdminId = actorAdmin.id,
                        reviewedByAdminName = actorAdmin.name,
                        reviewedAt = now,
                        adminNote = adminNote.trim(),
                        isResolved = true,
                        stockLogId = null
                    )
                    verificationDao.updateVerification(updatedVerification)

                    // Update schedule
                    val currentSchedule = verificationDao.getScheduleForProduct(verification.productId)
                    val intervalDays = currentSchedule?.intervalDays ?: 7
                    val nextDue = now + (intervalDays * 24L * 60 * 60 * 1000L)
                    verificationDao.updateScheduleVerified(verification.productId, lastVerifiedAt = now, nextDueAt = nextDue, updatedAt = now)

                    // Insert AuditLog
                    auditLogDao.insertAuditLog(
                        AuditLog(
                            userId = actorAdmin.id,
                            userName = actorAdmin.name,
                            action = "স্টক যাচাই অনুমোদন ও সমন্বয়",
                            details = "${verification.productName}: বাস্তব ${verification.physicalStock} অনুমোদিত। সমন্বিত পার্থক্য: ${verification.difference}. বর্তমান স্টক: ${updatedProd?.currentStock}"
                        )
                    )

                    // Complete reminder
                    val reminderKey = "MISMATCH_VERIF_${verification.id}"
                    reminderDao.getReminderByDeduplicationKey(reminderKey)?.let { rem ->
                        reminderDao.updateStatus(rem.id, ReminderStatus.COMPLETED, now)
                    }
                }

                AdminMismatchAction.REJECT -> {
                    // Live stock is UNCHANGED
                    val updatedVerification = verification.copy(
                        status = VerificationStatus.REJECTED,
                        reviewedByAdminId = actorAdmin.id,
                        reviewedByAdminName = actorAdmin.name,
                        reviewedAt = now,
                        adminNote = adminNote.trim(),
                        isResolved = true
                    )
                    verificationDao.updateVerification(updatedVerification)

                    // AuditLog
                    auditLogDao.insertAuditLog(
                        AuditLog(
                            userId = actorAdmin.id,
                            userName = actorAdmin.name,
                            action = "স্টক যাচাই প্রত্যাখ্যান",
                            details = "${verification.productName}: বাস্তব গণনা ${verification.physicalStock} প্রত্যাখ্যাত। স্টক অপরিবর্তিত। কারণ: $adminNote"
                        )
                    )

                    // Complete reminder
                    val reminderKey = "MISMATCH_VERIF_${verification.id}"
                    reminderDao.getReminderByDeduplicationKey(reminderKey)?.let { rem ->
                        reminderDao.updateStatus(rem.id, ReminderStatus.COMPLETED, now)
                    }
                }

                AdminMismatchAction.REQUEST_RECOUNT -> {
                    // Live stock is UNCHANGED
                    val updatedVerification = verification.copy(
                        status = VerificationStatus.RECOUNT_REQUESTED,
                        reviewedByAdminId = actorAdmin.id,
                        reviewedByAdminName = actorAdmin.name,
                        reviewedAt = now,
                        adminNote = adminNote.trim(),
                        isResolved = false
                    )
                    verificationDao.updateVerification(updatedVerification)

                    // AuditLog
                    auditLogDao.insertAuditLog(
                        AuditLog(
                            userId = actorAdmin.id,
                            userName = actorAdmin.name,
                            action = "স্টক পুনরায় গণনা নির্দেশ",
                            details = "${verification.productName}: পুনরায় গণনার নির্দেশ দেওয়া হয়েছে (${verification.verifierUserName}-কে)। নির্দেশ: $adminNote"
                        )
                    )

                    // Send Alert to the verifier salesman
                    val alertTitle = "দোকানের স্টক পুনরায় গণনা করুন"
                    val alertMessage = "${verification.productName} এর বাস্তব স্টক পুনরায় গণনা করে জমা দিন। এডমিনের নোট: ${adminNote.ifBlank { "অনুগ্রহ করে পুনরায় নিখুঁতভাবে গণনা করুন।" }}"

                    val alertId = "alert_recount_${UUID.randomUUID().toString().take(8)}"
                    alertDao.insertAlert(
                        AdminAlert(
                            id = alertId,
                            adminId = actorAdmin.id,
                            adminName = actorAdmin.name,
                            targetType = AlertTargetType.SPECIFIC,
                            targetSalesmanId = verification.verifierUserId,
                            targetSalesmanName = verification.verifierUserName,
                            title = alertTitle,
                            message = alertMessage,
                            priority = AlertPriority.URGENT,
                            isVoiceEnabled = true,
                            isNotificationEnabled = true,
                            createdAt = now
                        )
                    )
                    alertDao.insertReceipt(
                        SalesmanAlertReceipt(
                            id = "rcpt_${UUID.randomUUID().toString().take(8)}",
                            alertId = alertId,
                            salesmanId = verification.verifierUserId,
                            salesmanName = verification.verifierUserName,
                            isDelivered = true,
                            deliveredAt = now
                        )
                    )
                }
            }
        }
    }

    /**
     * Updates verification schedule interval and assignment for a product.
     * Strictly restricted to ADMIN role.
     */
    suspend fun configureProductSchedule(
        productId: String,
        intervalDays: Int,
        assignedSalesmanId: String?,
        assignedSalesmanName: String?,
        isReminderEnabled: Boolean,
        actorAdmin: User
    ) {
        if (actorAdmin.role != UserRole.ADMIN) {
            throw SecurityException("শুধুমাত্র এডমিন স্টক যাচাইয়ের সময়সীমা নির্ধারণ করতে পারেন!")
        }

        val existing = verificationDao.getScheduleForProduct(productId)
        val now = System.currentTimeMillis()
        val lastVerified = existing?.lastVerifiedAt ?: 0L
        val nextDue = if (lastVerified > 0) {
            lastVerified + (intervalDays * 24L * 60 * 60 * 1000L)
        } else {
            now + (intervalDays * 24L * 60 * 60 * 1000L)
        }

        val updated = StockVerificationSchedule(
            productId = productId,
            intervalDays = intervalDays,
            lastVerifiedAt = lastVerified,
            nextDueAt = nextDue,
            assignedSalesmanId = assignedSalesmanId,
            assignedSalesmanName = assignedSalesmanName,
            isReminderEnabled = isReminderEnabled,
            updatedAt = now
        )
        verificationDao.insertOrUpdateSchedule(updated)

        auditLogDao.insertAuditLog(
            AuditLog(
                userId = actorAdmin.id,
                userName = actorAdmin.name,
                action = "স্টক যাচাই সময়সীমা নির্ধারণ",
                details = "পণ্য ID: $productId — প্রতি $intervalDays দিন অন্তর যাচাই নির্ধারিত।"
            )
        )
    }

    /**
     * Dispatches verification instructions from Admin to Salesmen.
     */
    suspend fun sendVerificationInstructionAlert(
        targetSalesmanId: String?,
        targetSalesmanName: String?,
        title: String,
        message: String,
        priority: AlertPriority,
        isVoiceEnabled: Boolean,
        actorAdmin: User,
        salesmenList: List<User>
    ) {
        if (actorAdmin.role != UserRole.ADMIN) {
            throw SecurityException("শুধুমাত্র এডমিন অ্যালার্ট পাঠাতে পারেন!")
        }

        val now = System.currentTimeMillis()
        val isAll = targetSalesmanId.isNullOrBlank()

        val alert = AdminAlert(
            id = "alert_verif_${UUID.randomUUID().toString().take(8)}",
            adminId = actorAdmin.id,
            adminName = actorAdmin.name,
            targetType = if (isAll) AlertTargetType.ALL else AlertTargetType.SPECIFIC,
            targetSalesmanId = if (isAll) null else targetSalesmanId,
            targetSalesmanName = if (isAll) null else targetSalesmanName,
            title = title.ifBlank { "দোকানের স্টক যাচাই করুন" },
            message = message,
            priority = priority,
            isVoiceEnabled = isVoiceEnabled,
            isNotificationEnabled = true,
            createdAt = now
        )

        db.withTransaction {
            alertDao.insertAlert(alert)

            val targets = if (isAll) {
                salesmenList.filter { it.role == UserRole.SALESMAN && it.isActive }
            } else {
                salesmenList.filter { it.id == targetSalesmanId }
            }

            for (target in targets) {
                alertDao.insertReceipt(
                    SalesmanAlertReceipt(
                        id = "rcpt_${UUID.randomUUID().toString().take(8)}",
                        alertId = alert.id,
                        salesmanId = target.id,
                        salesmanName = target.name,
                        isDelivered = true,
                        deliveredAt = now
                    )
                )
            }
        }
    }
}
