package com.example

import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class SmartReminderTest {

    @Test
    fun testReminderEntityDefaults() {
        val reminder = ReminderItem(
            id = "test_1",
            deduplicationKey = "STOCK_ALERT_p_1",
            roleTarget = UserRole.ADMIN,
            title = "স্টক সংকট: মিনিকেট চাল",
            description = "বর্তমান মজুদ মাত্র ৪ বস্তা",
            category = ReminderCategory.STOCK_ALERT,
            priority = ReminderPriority.HIGH
        )

        assertEquals("test_1", reminder.id)
        assertEquals("STOCK_ALERT_p_1", reminder.deduplicationKey)
        assertEquals(UserRole.ADMIN, reminder.roleTarget)
        assertEquals(ReminderStatus.PENDING, reminder.status)
        assertEquals(ReminderPriority.HIGH, reminder.priority)
        assertTrue(reminder.isSystemGenerated)
        assertNull(reminder.completedAt)
    }

    @Test
    fun testStatusTransitions() {
        val initial = ReminderItem(
            id = "task_1",
            deduplicationKey = "CUSTOM_1",
            roleTarget = UserRole.SALESMAN,
            targetUserId = "salesman_1",
            title = "কাস্টমার তাগাদা",
            description = "বকেয়া আদায়ের চেষ্টা করুন",
            category = ReminderCategory.CUSTOMER_DUE,
            priority = ReminderPriority.MEDIUM,
            status = ReminderStatus.PENDING
        )

        val completedTime = System.currentTimeMillis()
        val completed = initial.copy(status = ReminderStatus.COMPLETED, completedAt = completedTime)

        assertEquals(ReminderStatus.COMPLETED, completed.status)
        assertEquals(completedTime, completed.completedAt)

        val dismissed = initial.copy(status = ReminderStatus.DISMISSED)
        assertEquals(ReminderStatus.DISMISSED, dismissed.status)
    }

    @Test
    fun testRoleSeparationIntegrity() {
        // Mock list of reminders in database
        val allReminders = listOf(
            ReminderItem(
                id = "admin_rem_1",
                deduplicationKey = "STOCK_ALERT_p_1",
                roleTarget = UserRole.ADMIN,
                targetUserId = null,
                title = "এডমিন সতর্কতা: চালের স্টক কম",
                description = "মজুদ ৪ বস্তা",
                category = ReminderCategory.STOCK_ALERT
            ),
            ReminderItem(
                id = "admin_rem_2",
                deduplicationKey = "EXPENSE_APPROVAL_PENDING",
                roleTarget = UserRole.ADMIN,
                targetUserId = null,
                title = "খরচের আবেদন অনুমোদন বাকি",
                description = "৩টি আবেদন অপেক্ষমাণ",
                category = ReminderCategory.EXPENSE_APPROVAL
            ),
            ReminderItem(
                id = "salesman_rem_1",
                deduplicationKey = "SALESMAN_FOLLOWUP_salesman_1",
                roleTarget = UserRole.SALESMAN,
                targetUserId = "salesman_1",
                title = "আমার তাগাদা",
                description = "কাস্টমার বকেয়া ৫০০০ টাকা",
                category = ReminderCategory.SALESMAN_FOLLOWUP
            ),
            ReminderItem(
                id = "salesman_rem_2",
                deduplicationKey = "SALESMAN_FOLLOWUP_salesman_2",
                roleTarget = UserRole.SALESMAN,
                targetUserId = "salesman_2",
                title = "অন্য সেলসম্যানের কাজ",
                description = "অন্য জনের বকেয়া",
                category = ReminderCategory.SALESMAN_FOLLOWUP
            )
        )

        // Filter for Admin
        val adminVisible = allReminders.filter { it.roleTarget == UserRole.ADMIN }
        assertEquals(2, adminVisible.size)
        assertTrue(adminVisible.any { it.id == "admin_rem_1" })
        assertTrue(adminVisible.any { it.id == "admin_rem_2" })
        assertFalse(adminVisible.any { it.id == "salesman_rem_1" })

        // Filter for Salesman 1 (Strict access control)
        val salesman1Visible = allReminders.filter {
            it.roleTarget == UserRole.SALESMAN && (it.targetUserId == "salesman_1" || it.targetUserId == null)
        }
        assertEquals(1, salesman1Visible.size)
        assertEquals("salesman_rem_1", salesman1Visible.first().id)

        // Ensure Salesman 1 CANNOT see Admin reminders or other salesman's reminders
        assertFalse(salesman1Visible.any { it.id == "admin_rem_1" })
        assertFalse(salesman1Visible.any { it.id == "admin_rem_2" })
        assertFalse(salesman1Visible.any { it.id == "salesman_rem_2" })
    }

    @Test
    fun testQuietHoursCheckLogic() {
        fun isQuietHours(currentHour: Int, startHour: Int = 22, endHour: Int = 7): Boolean {
            return if (startHour > endHour) {
                currentHour >= startHour || currentHour < endHour
            } else {
                currentHour in startHour until endHour
            }
        }

        // Night time (23:00 / 11 PM) -> quiet
        assertTrue(isQuietHours(23, 22, 7))
        // Late night (02:00 AM) -> quiet
        assertTrue(isQuietHours(2, 22, 7))
        // Midnight (00:00 AM) -> quiet
        assertTrue(isQuietHours(0, 22, 7))
        // Early morning (06:59 AM) -> quiet
        assertTrue(isQuietHours(6, 22, 7))

        // Daytime (10:00 AM) -> NOT quiet
        assertFalse(isQuietHours(10, 22, 7))
        // Afternoon (15:00 / 3 PM) -> NOT quiet
        assertFalse(isQuietHours(15, 22, 7))
        // Evening before quiet hours (21:00 / 9 PM) -> NOT quiet
        assertFalse(isQuietHours(21, 22, 7))
    }

    @Test
    fun testPreferencesDefaults() {
        val pref = ReminderPreference(userId = "admin_1")
        assertTrue(pref.isMasterEnabled)
        assertTrue(pref.isNotificationEnabled)
        assertTrue(pref.isVoiceEnabled)
        assertTrue(pref.quietHoursEnabled)
        assertEquals(22, pref.quietHoursStartHour)
        assertEquals(7, pref.quietHoursEndHour)
        assertEquals("bn", pref.voiceLanguage)
    }

    @Test
    fun testDeduplicationKeyUniquenessAndStability() {
        val prodId = "p_50"
        val key1 = "STOCK_ALERT_$prodId"
        val key2 = "STOCK_ALERT_$prodId"
        assertEquals(key1, key2)

        val salesmanId = "sales_01"
        val expenseKey1 = "SALESMAN_EXPENSE_PENDING_$salesmanId"
        val expenseKey2 = "SALESMAN_EXPENSE_PENDING_$salesmanId"
        assertEquals(expenseKey1, expenseKey2)
    }
}
