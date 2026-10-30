package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AlertPriority {
    GENERAL, // সাধারণ
    URGENT   // জরুরি
}

enum class AlertTargetType {
    SPECIFIC, // নির্দিষ্ট সেলসম্যান
    ALL       // সকল সেলসম্যান
}

@Entity(tableName = "admin_alerts")
data class AdminAlert(
    @PrimaryKey val id: String,
    val adminId: String,
    val adminName: String,
    val targetType: AlertTargetType = AlertTargetType.SPECIFIC,
    val targetSalesmanId: String? = null,
    val targetSalesmanName: String? = null,
    val title: String,
    val message: String,
    val priority: AlertPriority = AlertPriority.GENERAL,
    val isVoiceEnabled: Boolean = true,
    val isNotificationEnabled: Boolean = true,
    val scheduledTime: Long = 0L,
    val isScheduled: Boolean = false,
    val isCancelled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "salesman_alert_receipts")
data class SalesmanAlertReceipt(
    @PrimaryKey val id: String,
    val alertId: String,
    val salesmanId: String,
    val salesmanName: String,
    val isDelivered: Boolean = true,
    val deliveredAt: Long? = null,
    val isAcknowledged: Boolean = false, // "বুঝেছি"
    val acknowledgedAt: Long? = null,
    val isCompleted: Boolean = false,    // "কাজ সম্পন্ন"
    val completedAt: Long? = null,
    val notifiedAt: Long? = null
)

/**
 * Combined data representation for Salesman screen.
 */
data class SalesmanAlertView(
    val alert: AdminAlert,
    val receipt: SalesmanAlertReceipt
)

/**
 * Combined data representation for Admin alert tracking.
 */
data class AdminAlertDetail(
    val alert: AdminAlert,
    val receipts: List<SalesmanAlertReceipt>
)
