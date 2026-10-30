package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UserRole {
    ADMIN, SALESMAN, SUPER_ADMIN
}

enum class UserAccountStatus {
    ACTIVE,      // সক্রিয়
    SUSPENDED,   // সাময়িক স্থগিত
    DEACTIVATED  // নিষ্ক্রিয় / বিচ্ছিন্ন
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val role: UserRole,
    val pinHash: String, // SHA-256 hashed PIN/password
    val isActive: Boolean = true,
    val accountStatus: UserAccountStatus = UserAccountStatus.ACTIVE,
    val statusReason: String = "",
    val statusUpdatedAt: Long = System.currentTimeMillis(),
    val canDiscount: Boolean = true,
    val canViewCustomerDue: Boolean = true,
    val canCollectPayment: Boolean = true,
    val canApplyExpense: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val shopId: String = "" // Explicit shopId association
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String,
    val name: String,
    val sku: String,
    val barcode: String = "",
    val category: String,
    val purchasePrice: Double, // গোপনীয় ক্রয়মূল্য (সেলসম্যানকে অ্যাক্সেস দেওয়া হয় না)
    val sellingPrice: Double,
    val currentStock: Double,
    val alertStockLevel: Double = 5.0,
    val unit: String = "পিস", // পিস, কেজি, লিটার, কার্টন ইত্যাদি
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    val imageUri: String? = null,
    val imageSource: String = "NONE", // NONE, USER_PICKED, CAMERA, AI_GENERATED, ONLINE_SEARCH
    val imageSourceInfo: String = "",
    val isTestData: Boolean = false,
    val testTag: String = ""
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val address: String = "",
    val totalBilled: Double = 0.0,
    val totalPaid: Double = 0.0,
    val currentDue: Double = 0.0,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isTestData: Boolean = false,
    val testTag: String = "",
    val isDeleted: Boolean = false,
    val openingDue: Double = 0.0
)

enum class PaymentMethod {
    CASH, BKASH, NAGAD, ROCKET, BANK_TRANSFER
}

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey val invoiceNumber: String, // e.g. "INV-20261009-001"
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val salesmanId: String,
    val salesmanName: String,
    val subtotal: Double,
    val discount: Double = 0.0,
    val totalAmount: Double,
    val paidAmount: Double,
    val dueAmount: Double,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val note: String = "",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isTestData: Boolean = false,
    val testTag: String = "",
    val isDeleted: Boolean = false
)

@Entity(tableName = "sale_items")
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val productId: String,
    val productName: String,
    val quantity: Double,
    val unitPrice: Double,
    val purchasePriceAtSale: Double = 0.0, // বিক্রির সময়ের ক্রয়মূল্য যাতে পরবর্তী দাম পরিবর্তনের কারণে ঐতিহাসিক লাভ অপরিবর্তিত থাকে
    val totalPrice: Double
)

@Entity(tableName = "payments")
data class PaymentReceipt(
    @PrimaryKey val receiptNumber: String, // e.g. "RCT-20261009-001"
    val invoiceNumber: String = "",
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val salesmanId: String,
    val salesmanName: String,
    val amount: Double,
    val previousDue: Double,
    val currentDue: Double,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val note: String = "",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isTestData: Boolean = false,
    val testTag: String = "",
    val isDeleted: Boolean = false
)

enum class ExpenseCategory {
    TRAVEL, FOOD, DELIVERY, LOADING, OTHER
}

enum class ExpenseSource {
    COMPANY_CASH, OWN_POCKET
}

enum class ExpenseStatus {
    PENDING, APPROVED, REJECTED
}

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey val id: String,
    val salesmanId: String,
    val salesmanName: String,
    val category: ExpenseCategory,
    val source: ExpenseSource,
    val amount: Double,
    val description: String,
    val status: ExpenseStatus = ExpenseStatus.PENDING,
    val adminNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null,
    val isTestData: Boolean = false,
    val testTag: String = "",
    val isDeleted: Boolean = false
)

@Entity(tableName = "stock_logs")
data class StockLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: String,
    val productName: String,
    val changeType: String, // "বিক্রি", "ক্রয়", "ফেরত", "সমন্বয়", "দাম পরিবর্তন"
    val quantityChanged: Double,
    val remainingStock: Double,
    val referenceId: String = "",
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val userName: String,
    val action: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "company_settings")
data class CompanySettings(
    @PrimaryKey val id: Int = 1,
    val businessName: String = "স্মার্ট বিজনেস লিঃ",
    val phone: String = "০১৭১২-৩৪৫৬৭৮",
    val address: String = "ঢাকা, বাংলাদেশ",
    val currencySymbol: String = "৳",
    val receiptFooter: String = "আমাদের সাথে ব্যবসা করার জন্য ধন্যবাদ!",
    val isAppLocked: Boolean = false,
    val appLockPin: String = "",
    val productImageMode: String = "AUTO" // "AUTO", "MANUAL"
)

enum class ProductSubmissionStatus {
    PENDING,          // অপেক্ষমাণ (এডমিন পর্যালোচনার জন্য)
    APPROVED,         // অনুমোদিত (মূল স্টক ও পণ্য তালিকায় যুক্ত)
    NEEDS_CORRECTION  // সংশোধনের জন্য ফেরত (এডমিনের নোটসহ)
}

@Entity(tableName = "product_submissions")
data class ProductSubmission(
    @PrimaryKey val id: String, // e.g. "sub_172855..."
    val name: String,
    val productCode: String = "",
    val category: String,
    val purchasePrice: Double,
    val sellingPrice: Double,
    val initialStock: Double,
    val unit: String = "পিস",
    val description: String = "",
    val salesmanId: String,
    val salesmanName: String,
    val salesmanPhone: String,
    val status: ProductSubmissionStatus = ProductSubmissionStatus.PENDING,
    val adminCorrectionNote: String = "",
    val approvedProductId: String? = null,
    val reviewedByAdminId: String? = null,
    val reviewedByAdminName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null,
    val imageUri: String? = null,
    val imageSource: String = "NONE",
    val imageSourceInfo: String = ""
)

@Entity(tableName = "companies")
data class Company(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val address: String = "",
    val note: String = "",
    val totalPurchased: Double = 0.0,
    val totalPaid: Double = 0.0,
    val currentDue: Double = 0.0,
    val advanceAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isTestData: Boolean = false,
    val testTag: String = "",
    val isDeleted: Boolean = false
)

@Entity(tableName = "company_purchases")
data class CompanyPurchase(
    @PrimaryKey val id: String,
    val companyId: String,
    val companyName: String,
    val challanNumber: String = "",
    val productId: String? = null,
    val productName: String,
    val quantity: Double,
    val unit: String = "পিস",
    val unitPurchasePrice: Double,
    val totalAmount: Double,
    val paidAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val addedToStock: Boolean = true,
    val note: String = "",
    val purchaseDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "company_payments")
data class CompanyPayment(
    @PrimaryKey val id: String,
    val companyId: String,
    val companyName: String,
    val amount: Double,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val referenceNumber: String = "",
    val note: String = "",
    val paymentDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recycle_bin")
data class RecycleBinItem(
    @PrimaryKey val id: String,
    val originalTable: String,
    val originalId: String,
    val itemName: String,
    val itemType: String,
    val itemDetails: String,
    val snapshotJson: String,
    val deletedByAdminId: String,
    val deletedByAdminName: String,
    val deleteReason: String,
    val deletedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L),
    val isTestData: Boolean = false,
    val testTag: String = "NONE",
    val isRestored: Boolean = false,
    val restoredAt: Long? = null,
    val restoredByAdminId: String? = null
)

enum class DueCustomerCategory {
    CONFIRMED_TEST,      // স্পষ্ট টেস্ট ডেটা (isTestData, testTag, নামের টেস্ট চিহ্ন)
    SAMPLE_SEED_DATA,    // প্রারম্ভিক নমুনা ডেটা (Initial Demo Seed Data - c_1, c_3)
    NEEDS_MANUAL_REVIEW  // ম্যানুয়াল যাচাই প্রয়োজন (বাস্তব বা অনির্দিষ্ট কাস্টমার)
}

data class DetectedTestDueCustomer(
    val customer: Customer,
    val category: DueCustomerCategory,
    val reasons: List<String>,
    val salesCount: Int,
    val receiptsCount: Int,
    val totalSalesAmount: Double,
    val totalPaidAmount: Double,
    val hasTransactions: Boolean
)

