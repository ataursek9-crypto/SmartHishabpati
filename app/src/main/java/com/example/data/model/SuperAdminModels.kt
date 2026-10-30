package com.example.data.model

/**
 * Status of a registered business on the platform.
 */
enum class BusinessAccountStatus {
    PENDING,     // অপেক্ষমাণ (নতুন রেজিস্ট্রেশন, অনুমোদনের অপেক্ষায়)
    ACTIVE,      // সক্রিয়
    SUSPENDED,   // স্থগিত (অ্যাডমিন কর্তৃক বন্ধ)
    DEACTIVATED  // নিষ্ক্রিয়
}

/**
 * Subscription status of a business.
 */
enum class SubscriptionStatus {
    TRIAL,         // ট্রায়াল
    ACTIVE,        // সক্রিয়
    GRACE_PERIOD,  // গ্রেস পিরিয়ড
    EXPIRED,       // মেয়াদোত্তীর্ণ
    CANCELLED,     // বাতিলকৃত
    SUSPENDED      // স্থগিত
}

/**
 * Supported payment methods for platform subscriptions.
 */
enum class PlatformPaymentMethod {
    BKASH, NAGAD, ROCKET, BANK_TRANSFER, MANUAL
}

/**
 * Review status of subscription payments.
 */
enum class PlatformPaymentStatus {
    PENDING, APPROVED, REJECTED
}

/**
 * Platform Business Account Metadata for Super Admin Management.
 * Minimal metadata needed for platform administration; does NOT expose shop customer/sales transaction records.
 */
data class PlatformBusiness(
    val id: String,
    val businessName: String,
    val ownerUid: String,
    val ownerName: String,
    val ownerEmail: String = "",
    val ownerPhone: String,
    val status: BusinessAccountStatus = BusinessAccountStatus.ACTIVE,
    val statusReason: String = "",
    val planId: String = "plan_free",
    val planName: String = "সম্পূর্ণ ফ্রি ব্যবহার",
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val subscriptionExpiresAt: Long = 0L, // 0L indicates no automatic expiration (perpetual free)
    val staffCount: Int = 1,
    val maxStaffAllowed: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    val firstUseAt: Long = createdAt,
    val lastActiveAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    // Extended shop registration & platform management fields
    val businessType: String = "সাধারণ ব্যবসা",
    val address: String = "",
    val district: String = "",
    val isAutoExpiryDisabled: Boolean = true, // Default Free Policy: Automatic expiry strictly disabled
    val activeDaysCount: Int = 1,
    val phoneVerified: Boolean = false,
    // Subscription Fee Proposal / Notice from Super Admin
    val proposedFee: Double = 0.0,
    val proposedStartDate: Long? = null,
    val noticeReminderDate: Long? = null,
    val subscriptionNoticeNote: String = "",
    val hasActiveNotice: Boolean = false,
    // Scheduled Future Policy Changes
    val scheduledPolicyChange: String = "",
    val scheduledPolicyEffectiveAt: Long? = null
) {
    val elapsedDays: Long
        get() = ((System.currentTimeMillis() - createdAt) / (24L * 60 * 60 * 1000)).coerceAtLeast(0L)

    val activeDays: Long
        get() = activeDaysCount.toLong().coerceAtLeast(1L)
}

/**
 * Configurable Platform Subscription Plan.
 */
data class SubscriptionPlan(
    val id: String,
    val code: String,
    val name: String,
    val nameBn: String,
    val descriptionBn: String,
    val priceMonthly: Double,
    val priceYearly: Double,
    val maxStaff: Int,
    val maxProducts: Int,
    val features: List<String>,
    val isPopular: Boolean = false,
    val isActive: Boolean = true
)

/**
 * Subscription History & Status Record.
 */
data class PlatformSubscription(
    val id: String,
    val businessId: String,
    val businessName: String,
    val planId: String,
    val planName: String,
    val status: SubscriptionStatus,
    val startDate: Long,
    val endDate: Long,
    val amount: Double,
    val currency: String = "BDT",
    val paymentId: String? = null,
    val autoRenew: Boolean = false,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Payment Transaction Record for Super Admin Verification.
 */
data class PlatformPayment(
    val id: String,
    val businessId: String,
    val businessName: String,
    val amount: Double,
    val currency: String = "BDT",
    val paymentMethod: PlatformPaymentMethod,
    val transactionReference: String,
    val senderNumber: String,
    val status: PlatformPaymentStatus = PlatformPaymentStatus.PENDING,
    val targetPlanId: String,
    val durationMonths: Int = 1,
    val submittedAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null,
    val reviewedByUid: String? = null,
    val reviewedByName: String? = null,
    val rejectionReason: String = "",
    val notes: String = ""
)

/**
 * Immutable Platform Audit Log for Super Admin Actions.
 */
data class PlatformAuditLog(
    val id: String,
    val actorUid: String,
    val actorName: String,
    val actorEmail: String,
    val action: String,
    val targetBusinessId: String,
    val targetBusinessName: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Platform System Configuration and Contact Settings.
 */
data class PlatformSystemSettings(
    val maintenanceMode: Boolean = false,
    val allowNewRegistrations: Boolean = true,
    val supportPhone: String = "01700000000",
    val supportEmail: String = "support@smartbiz.local",
    val bKashMerchantNumber: String = "01700000000",
    val nagadMerchantNumber: String = "01800000000",
    val minSupportedAppVersion: String = "1.0",
    val broadcastNoticeBn: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Aggregated Platform Health and Metrics.
 */
data class PlatformOverviewStats(
    val totalBusinesses: Int = 0,
    val pendingBusinesses: Int = 0,
    val activeBusinesses: Int = 0,
    val suspendedBusinesses: Int = 0,
    val activeSubscriptions: Int = 0,
    val expiringSubscriptions: Int = 0,
    val expiredSubscriptions: Int = 0,
    val pendingPaymentsCount: Int = 0,
    val verifiedRevenue: Double = 0.0,
    val planDistribution: Map<String, Int> = emptyMap(),
    val isCloudConnected: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)
