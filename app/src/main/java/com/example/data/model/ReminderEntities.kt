package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReminderPriority {
    HIGH, MEDIUM, LOW
}

enum class ReminderStatus {
    PENDING, COMPLETED, DISMISSED
}

enum class ReminderCategory {
    DAILY_CLOSING,      // দিনের বিক্রি ও হিসাব পরীক্ষা
    EXPENSE_APPROVAL,   // খরচের আবেদন অনুমোদন
    PRODUCT_APPROVAL,   // প্রোডাক্ট অনুমোদন ও যাচাই
    STOCK_ALERT,        // স্টক সংকট সতর্কতা
    CUSTOMER_DUE,       // কাস্টমার বকেয়া তাগাদা
    SALESMAN_FOLLOWUP,  // সেলসম্যানের ব্যক্তিগত কাজ ও তাগাদা
    CUSTOM_TASK         // ব্যবহারকারীর নিজস্ব নির্ধারিত কাজ
}

@Entity(tableName = "reminders")
data class ReminderItem(
    @PrimaryKey val id: String,
    val deduplicationKey: String,
    val roleTarget: UserRole,
    val targetUserId: String? = null,
    val title: String,
    val description: String,
    val category: ReminderCategory,
    val priority: ReminderPriority = ReminderPriority.MEDIUM,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val targetRoute: String? = null,
    val targetEntityId: String? = null,
    val scheduledTime: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isSystemGenerated: Boolean = true
)

@Entity(tableName = "reminder_preferences")
data class ReminderPreference(
    @PrimaryKey val userId: String,
    val isMasterEnabled: Boolean = true,
    val isNotificationEnabled: Boolean = true,
    val isVoiceEnabled: Boolean = true,
    val voiceLanguage: String = "bn",
    val quietHoursEnabled: Boolean = true,
    val quietHoursStartHour: Int = 22, // রাত ১০টা
    val quietHoursEndHour: Int = 7,   // সকাল ৭টা
    val scheduledDailyHour: Int = 18, // সন্ধ্যা ৬টা ৩০ মিনিট
    val scheduledDailyMinute: Int = 30,
    val enabledCategories: String = "DAILY_CLOSING,EXPENSE_APPROVAL,PRODUCT_APPROVAL,STOCK_ALERT,CUSTOMER_DUE,SALESMAN_FOLLOWUP,CUSTOM_TASK",
    val updatedAt: Long = System.currentTimeMillis()
)
