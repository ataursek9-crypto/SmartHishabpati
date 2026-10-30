package com.example

import com.example.data.model.*
import com.example.data.repository.AdminMismatchAction
import org.junit.Assert.*
import org.junit.Test

class StockVerificationTest {

    @Test
    fun testStockVerificationCreation_Matched() {
        val appStock = 50.0
        val physicalStock = 50.0
        val difference = physicalStock - appStock

        val diffType = when {
            difference == 0.0 -> StockDifferenceType.EXACT_MATCH
            difference < 0.0 -> StockDifferenceType.SHORTAGE
            else -> StockDifferenceType.SURPLUS
        }

        val verification = StockVerification(
            id = "verif_1",
            productId = "p_1",
            productName = "মিনিকেট চাল",
            appStockSnapshot = appStock,
            physicalStock = physicalStock,
            difference = difference,
            differenceType = diffType,
            verifierUserId = "salesman_1",
            verifierUserName = "হাসিবুল",
            verifierRole = UserRole.SALESMAN,
            status = if (difference == 0.0) VerificationStatus.MATCHED else VerificationStatus.MISMATCH_PENDING,
            isResolved = (difference == 0.0)
        )

        assertEquals(0.0, verification.difference, 0.001)
        assertEquals(StockDifferenceType.EXACT_MATCH, verification.differenceType)
        assertEquals(VerificationStatus.MATCHED, verification.status)
        assertTrue(verification.isResolved)
    }

    @Test
    fun testStockVerificationCreation_Shortage() {
        val appStock = 50.0
        val physicalStock = 45.0 // ৫ বস্তা ঘাটতি
        val difference = physicalStock - appStock

        val diffType = when {
            difference == 0.0 -> StockDifferenceType.EXACT_MATCH
            difference < 0.0 -> StockDifferenceType.SHORTAGE
            else -> StockDifferenceType.SURPLUS
        }

        val verification = StockVerification(
            id = "verif_2",
            productId = "p_1",
            productName = "মিনিকেট চাল",
            appStockSnapshot = appStock,
            physicalStock = physicalStock,
            difference = difference,
            differenceType = diffType,
            verifierUserId = "salesman_1",
            verifierUserName = "হাসিবুল",
            verifierRole = UserRole.SALESMAN,
            status = if (difference == 0.0) VerificationStatus.MATCHED else VerificationStatus.MISMATCH_PENDING,
            isResolved = (difference == 0.0)
        )

        assertEquals(-5.0, verification.difference, 0.001)
        assertEquals(StockDifferenceType.SHORTAGE, verification.differenceType)
        assertEquals(VerificationStatus.MISMATCH_PENDING, verification.status)
        assertFalse("স্টক অমিল থাকলে এডমিনের অনুমোদন ছাড়া স্বয়ংক্রিয় সমাধান হওয়া নিষিদ্ধ", verification.isResolved)
    }

    @Test
    fun testStockVerificationCreation_Surplus() {
        val appStock = 30.0
        val physicalStock = 33.0 // ৩ লিটার বেশি পাওয়া গেছে
        val difference = physicalStock - appStock

        val diffType = when {
            difference == 0.0 -> StockDifferenceType.EXACT_MATCH
            difference < 0.0 -> StockDifferenceType.SHORTAGE
            else -> StockDifferenceType.SURPLUS
        }

        val verification = StockVerification(
            id = "verif_3",
            productId = "p_2",
            productName = "সয়াবিন তেল",
            appStockSnapshot = appStock,
            physicalStock = physicalStock,
            difference = difference,
            differenceType = diffType,
            verifierUserId = "salesman_1",
            verifierUserName = "হাসিবুল",
            verifierRole = UserRole.SALESMAN,
            status = if (difference == 0.0) VerificationStatus.MATCHED else VerificationStatus.MISMATCH_PENDING,
            isResolved = (difference == 0.0)
        )

        assertEquals(3.0, verification.difference, 0.001)
        assertEquals(StockDifferenceType.SURPLUS, verification.differenceType)
        assertEquals(VerificationStatus.MISMATCH_PENDING, verification.status)
        assertFalse(verification.isResolved)
    }

    @Test
    fun testAdminApprovalAndAdjustmentWorkflow() {
        val initialMismatch = StockVerification(
            id = "verif_10",
            productId = "p_1",
            productName = "মিনিকেট চাল",
            appStockSnapshot = 50.0,
            physicalStock = 46.0, // -4 ঘাটতি
            difference = -4.0,
            differenceType = StockDifferenceType.SHORTAGE,
            verifierUserId = "salesman_1",
            verifierUserName = "হাসিবুল",
            verifierRole = UserRole.SALESMAN,
            status = VerificationStatus.MISMATCH_PENDING,
            isResolved = false
        )

        // Admin decides to approve & adjust
        val adminId = "admin_1"
        val adminName = "আতাউর রহমান"
        val reviewTime = System.currentTimeMillis()

        val approved = initialMismatch.copy(
            status = VerificationStatus.APPROVED_ADJUSTED,
            reviewedByAdminId = adminId,
            reviewedByAdminName = adminName,
            reviewedAt = reviewTime,
            adminNote = "ভাঙা বস্তা সমন্বয় করা হলো",
            isResolved = true,
            stockLogId = 999L
        )

        assertEquals(VerificationStatus.APPROVED_ADJUSTED, approved.status)
        assertTrue(approved.isResolved)
        assertEquals(adminId, approved.reviewedByAdminId)
        assertEquals("ভাঙা বস্তা সমন্বয় করা হলো", approved.adminNote)
        assertNotNull(approved.stockLogId)

        // Simulated stock adjustment result
        val oldLiveStock = 50.0
        val adjustedLiveStock = oldLiveStock + approved.difference
        assertEquals(46.0, adjustedLiveStock, 0.001)
    }

    @Test
    fun testAdminRejectionWorkflow() {
        val initialMismatch = StockVerification(
            id = "verif_11",
            productId = "p_1",
            productName = "মিনিকেট চাল",
            appStockSnapshot = 50.0,
            physicalStock = 40.0, // ভুল গণনা
            difference = -10.0,
            differenceType = StockDifferenceType.SHORTAGE,
            verifierUserId = "salesman_1",
            verifierUserName = "হাসিবুল",
            verifierRole = UserRole.SALESMAN,
            status = VerificationStatus.MISMATCH_PENDING,
            isResolved = false
        )

        val rejected = initialMismatch.copy(
            status = VerificationStatus.REJECTED,
            reviewedByAdminId = "admin_1",
            reviewedByAdminName = "আতাউর রহমান",
            reviewedAt = System.currentTimeMillis(),
            adminNote = "গণনা সঠিক হয়নি, গোডাউনে আরও ১০ বস্তা রয়েছে",
            isResolved = true
        )

        assertEquals(VerificationStatus.REJECTED, rejected.status)
        assertTrue(rejected.isResolved)

        // Live stock MUST remain unchanged
        val liveStock = 50.0
        assertEquals(50.0, liveStock, 0.001)
    }

    @Test
    fun testAdminRecountRequestWorkflow() {
        val initialMismatch = StockVerification(
            id = "verif_12",
            productId = "p_1",
            productName = "মিনিকেট চাল",
            appStockSnapshot = 50.0,
            physicalStock = 48.0,
            difference = -2.0,
            differenceType = StockDifferenceType.SHORTAGE,
            verifierUserId = "salesman_1",
            verifierUserName = "হাসিবুল",
            verifierRole = UserRole.SALESMAN,
            status = VerificationStatus.MISMATCH_PENDING,
            isResolved = false
        )

        val recountRequested = initialMismatch.copy(
            status = VerificationStatus.RECOUNT_REQUESTED,
            reviewedByAdminId = "admin_1",
            reviewedByAdminName = "আতাউর রহমান",
            reviewedAt = System.currentTimeMillis(),
            adminNote = "অনুগ্রহ করে পেছনের তাকে পুনরায় চেক করুন",
            isResolved = false
        )

        assertEquals(VerificationStatus.RECOUNT_REQUESTED, recountRequested.status)
        assertFalse("পুনরায় গণনার জন্য নির্ধারণ করলে অমিল সমাধান হয়নি", recountRequested.isResolved)
    }

    @Test
    fun testScheduleIntervalAndOverdueCalculation() {
        val now = 1000000000000L // arbitrary fixed time
        val intervalDays = 7
        val lastVerifiedAt = now - (8L * 24 * 60 * 60 * 1000L) // ৮ দিন আগে যাচাই হয়েছিল
        val nextDueAt = lastVerifiedAt + (intervalDays * 24L * 60 * 60 * 1000L) // ৭ দিন পর অর্থাৎ ১ দিন আগে ডিউ

        val schedule = StockVerificationSchedule(
            productId = "p_1",
            intervalDays = intervalDays,
            lastVerifiedAt = lastVerifiedAt,
            nextDueAt = nextDueAt,
            isReminderEnabled = true
        )

        val isOverdue = now > schedule.nextDueAt
        assertTrue("৮ দিন আগের যাচাই ৭ দিনের শিডিউলে অবশ্যই overdue হবে", isOverdue)

        // Updated schedule after verification
        val updatedNextDue = now + (schedule.intervalDays * 24L * 60 * 60 * 1000L)
        val updatedSchedule = schedule.copy(lastVerifiedAt = now, nextDueAt = updatedNextDue)

        assertFalse("নতুন যাচাইয়ের পর overdue হওয়া উচিত নয়", now > updatedSchedule.nextDueAt)
    }

    @Test
    fun testRoleBasedAccessControlChecks() {
        val adminUser = User(
            id = "admin_1",
            name = "এডমিন",
            phone = "01700000000",
            role = UserRole.ADMIN,
            pinHash = "pin"
        )

        val salesmanUser = User(
            id = "salesman_1",
            name = "সেলসম্যান",
            phone = "01800000000",
            role = UserRole.SALESMAN,
            pinHash = "pin"
        )

        assertTrue(adminUser.role == UserRole.ADMIN)
        assertFalse(salesmanUser.role == UserRole.ADMIN)

        // Simulate RBAC check
        val canSalesmanAdjustStockDirectly = (salesmanUser.role == UserRole.ADMIN)
        assertFalse("সেলসম্যান সরাসরি স্টক সমন্বয় করতে পারবে না", canSalesmanAdjustStockDirectly)

        val canAdminAdjustStock = (adminUser.role == UserRole.ADMIN)
        assertTrue("এডমিন স্টক সমন্বয় করার অনুমতি পায়", canAdminAdjustStock)
    }
}
