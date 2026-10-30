package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Status of a stock verification record.
 */
enum class VerificationStatus {
    MATCHED,            // মিলেছে (বাস্তব ও অ্যাপের স্টক সমান)
    MISMATCH_PENDING,   // অমিল (এডমিনের অনুমোদনের অপেক্ষমাণ)
    APPROVED_ADJUSTED,  // অনুমোদিত ও স্টক সমন্বিত
    REJECTED,           // প্রত্যাখ্যাত (স্টক অপরিবর্তিত)
    RECOUNT_REQUESTED   // পুনরায় গণনার নির্দেশ
}

/**
 * Type of difference observed during physical stock counting.
 */
enum class StockDifferenceType {
    EXACT_MATCH, // সঠিক মিলেছে (পার্থক্য = ০)
    SHORTAGE,    // স্টক কম / ঘাটতি (বাস্তব < অ্যাপ)
    SURPLUS      // অতিরিক্ত স্টক / বেশি (বাস্তব > অ্যাপ)
}

/**
 * Preset intervals for automatic stock verification schedules.
 */
enum class VerificationScheduleInterval(val days: Int, val labelBn: String) {
    DAILY(1, "প্রতিদিন (১ দিন)"),
    EVERY_3_DAYS(3, "প্রতি ৩ দিন"),
    WEEKLY(7, "প্রতি ৭ দিন (সাপ্তাহিক)"),
    BIWEEKLY(15, "প্রতি ১৫ দিন"),
    MONTHLY(30, "প্রতি ৩০ দিন (মাসিক)"),
    CUSTOM(-1, "কাস্টম দিনসংখ্যা");

    companion object {
        fun fromDays(days: Int): VerificationScheduleInterval {
            return entries.find { it.days == days } ?: CUSTOM
        }
    }
}

/**
 * Immutable snapshot record of physical stock counting vs recorded system stock.
 * Historical entries cannot be modified or deleted directly.
 */
@Entity(tableName = "stock_verifications")
data class StockVerification(
    @PrimaryKey val id: String, // e.g. "verif_172855..."
    val productId: String,
    val productName: String,
    val productSku: String = "",
    val productCategory: String = "",
    val productUnit: String = "পিস",
    val appStockSnapshot: Double, // যাচাই শুরু করার সময়ের মূল স্টক
    val physicalStock: Double,    // বাস্তবে গুনে পাওয়া স্টক
    val difference: Double,       // physicalStock - appStockSnapshot
    val differenceType: StockDifferenceType,
    val verifierUserId: String,
    val verifierUserName: String,
    val verifierRole: UserRole,
    val verifiedAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val imageUri: String? = null,
    val status: VerificationStatus,
    val reviewedByAdminId: String? = null,
    val reviewedByAdminName: String? = null,
    val reviewedAt: Long? = null,
    val adminNote: String? = null,
    val isResolved: Boolean = false,
    val stockLogId: Long? = null
)

/**
 * Configurable verification schedule for each product.
 */
@Entity(tableName = "stock_verification_schedules")
data class StockVerificationSchedule(
    @PrimaryKey val productId: String,
    val intervalDays: Int = 7,
    val lastVerifiedAt: Long = 0L,
    val nextDueAt: Long = 0L,
    val assignedSalesmanId: String? = null,
    val assignedSalesmanName: String? = null,
    val isReminderEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Combined view model for product verification status.
 */
data class ProductVerificationItem(
    val product: Product,
    val schedule: StockVerificationSchedule?,
    val lastVerification: StockVerification?,
    val pendingMismatch: StockVerification?,
    val isOverdue: Boolean,
    val daysSinceLastVerification: Int
)

/**
 * Aggregated statistics for the Stock Control Center.
 */
data class StockControlSummaryStats(
    val totalProducts: Int = 0,
    val lastVerificationTime: Long? = null,
    val verifiedTodayCount: Int = 0,
    val pendingVerificationCount: Int = 0,
    val mismatchCount: Int = 0,
    val pendingAdminApprovalCount: Int = 0,
    val resolvedMismatchesCount: Int = 0,
    val overdueCount: Int = 0
)
