package com.example

import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Test

class AdminAlertTest {

    @Test
    fun testAlertDefaultsAndIntegrity() {
        val alert = AdminAlert(
            id = "alert_101",
            adminId = "admin_1",
            adminName = "ব্যবস্থাপক",
            targetType = AlertTargetType.SPECIFIC,
            targetSalesmanId = "salesman_5",
            targetSalesmanName = "রহিম আহমেদ",
            title = "আজকের বকেয়া তাগাদা",
            message = "আজকের কালেকশন দ্রুত সম্পন্ন করুন।",
            priority = AlertPriority.URGENT,
            isVoiceEnabled = true,
            isNotificationEnabled = true,
            scheduledTime = 0L,
            isScheduled = false
        )

        assertEquals("alert_101", alert.id)
        assertEquals("admin_1", alert.adminId)
        assertEquals(AlertPriority.URGENT, alert.priority)
        assertEquals(AlertTargetType.SPECIFIC, alert.targetType)
        assertEquals("salesman_5", alert.targetSalesmanId)
        assertFalse(alert.isScheduled)
        assertFalse(alert.isCancelled)
        assertTrue(alert.isVoiceEnabled)
    }

    @Test
    fun testReceiptLifecycleTransitions() {
        val now = System.currentTimeMillis()
        val receipt = SalesmanAlertReceipt(
            id = "rcpt_1",
            alertId = "alert_101",
            salesmanId = "salesman_5",
            salesmanName = "রহিম আহমেদ",
            isDelivered = true,
            deliveredAt = now,
            isAcknowledged = false,
            acknowledgedAt = null,
            isCompleted = false,
            completedAt = null
        )

        // Initially delivered, not acknowledged, not completed
        assertTrue(receipt.isDelivered)
        assertFalse(receipt.isAcknowledged)
        assertFalse(receipt.isCompleted)

        // Step 1: Salesman acknowledges ("বুঝেছি")
        val ackTime = now + 1000
        val acknowledged = receipt.copy(
            isAcknowledged = true,
            acknowledgedAt = ackTime
        )
        assertTrue(acknowledged.isAcknowledged)
        assertEquals(ackTime, acknowledged.acknowledgedAt)
        assertFalse(acknowledged.isCompleted)

        // Step 2: Salesman completes task ("কাজ সম্পন্ন")
        val compTime = now + 5000
        val completed = acknowledged.copy(
            isCompleted = true,
            completedAt = compTime
        )
        assertTrue(completed.isCompleted)
        assertEquals(compTime, completed.completedAt)
    }

    @Test
    fun testTargetSeparationIntegrity() {
        val allAlert = AdminAlert(
            id = "alert_all",
            adminId = "admin_1",
            adminName = "অ্যাডমিন",
            targetType = AlertTargetType.ALL,
            title = "জরুরি মিটিং",
            message = "সকল সেলস প্রতিনিধিদের সন্ধ্যার মিটিংয়ে উপস্থিত থাকার অনুরোধ করা হচ্ছে।",
            priority = AlertPriority.URGENT
        )

        val specificAlert = AdminAlert(
            id = "alert_spec",
            adminId = "admin_1",
            adminName = "অ্যাডমিন",
            targetType = AlertTargetType.SPECIFIC,
            targetSalesmanId = "salesman_1",
            targetSalesmanName = "করিম",
            title = "বিশেষ ডেলিভারি",
            message = "আজকের স্টক ডেলিভারি চেক করুন।",
            priority = AlertPriority.GENERAL
        )

        assertEquals(AlertTargetType.ALL, allAlert.targetType)
        assertNull(allAlert.targetSalesmanId)

        assertEquals(AlertTargetType.SPECIFIC, specificAlert.targetType)
        assertEquals("salesman_1", specificAlert.targetSalesmanId)
    }

    @Test
    fun testScheduledAlertIntegrity() {
        val futureTime = System.currentTimeMillis() + 3600000L
        val scheduledAlert = AdminAlert(
            id = "alert_sched",
            adminId = "admin_1",
            adminName = "অ্যাডমিন",
            targetType = AlertTargetType.ALL,
            title = "রাত ৮টায় ক্লোজিং রিপোর্ট",
            message = "সব হিসাব সমাপ্ত করুন।",
            priority = AlertPriority.GENERAL,
            scheduledTime = futureTime,
            isScheduled = true
        )

        assertTrue(scheduledAlert.isScheduled)
        assertTrue(scheduledAlert.scheduledTime > System.currentTimeMillis())

        val cancelledAlert = scheduledAlert.copy(isCancelled = true)
        assertTrue(cancelledAlert.isCancelled)
    }
}
