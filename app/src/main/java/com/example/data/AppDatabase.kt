package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.model.*

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN accountStatus TEXT NOT NULL DEFAULT 'ACTIVE'")
        db.execSQL("ALTER TABLE users ADD COLUMN statusReason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE users ADD COLUMN statusUpdatedAt INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS product_submissions (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                purchasePrice REAL NOT NULL,
                sellingPrice REAL NOT NULL,
                initialStock REAL NOT NULL,
                unit TEXT NOT NULL,
                description TEXT NOT NULL,
                salesmanId TEXT NOT NULL,
                salesmanName TEXT NOT NULL,
                salesmanPhone TEXT NOT NULL,
                status TEXT NOT NULL,
                adminCorrectionNote TEXT NOT NULL,
                approvedProductId TEXT,
                reviewedByAdminId TEXT,
                reviewedByAdminName TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                reviewedAt INTEGER
            )
        """.trimIndent())
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE product_submissions ADD COLUMN productCode TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `companies` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `totalPurchased` REAL NOT NULL,
                `totalPaid` REAL NOT NULL,
                `currentDue` REAL NOT NULL,
                `advanceAmount` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `company_purchases` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `companyId` TEXT NOT NULL,
                `companyName` TEXT NOT NULL,
                `challanNumber` TEXT NOT NULL,
                `productId` TEXT,
                `productName` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `unit` TEXT NOT NULL,
                `unitPurchasePrice` REAL NOT NULL,
                `totalAmount` REAL NOT NULL,
                `paidAmount` REAL NOT NULL,
                `dueAmount` REAL NOT NULL,
                `paymentMethod` TEXT NOT NULL,
                `addedToStock` INTEGER NOT NULL,
                `note` TEXT NOT NULL,
                `purchaseDate` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `company_payments` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `companyId` TEXT NOT NULL,
                `companyName` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `paymentMethod` TEXT NOT NULL,
                `referenceNumber` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `paymentDate` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN imageUri TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE products ADD COLUMN imageSource TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE products ADD COLUMN imageSourceInfo TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE product_submissions ADD COLUMN imageUri TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE product_submissions ADD COLUMN imageSource TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE product_submissions ADD COLUMN imageSourceInfo TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE company_settings ADD COLUMN productImageMode TEXT NOT NULL DEFAULT 'AUTO'")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `recycle_bin` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `originalTable` TEXT NOT NULL,
                `originalId` TEXT NOT NULL,
                `itemName` TEXT NOT NULL,
                `itemType` TEXT NOT NULL,
                `itemDetails` TEXT NOT NULL,
                `snapshotJson` TEXT NOT NULL,
                `deletedByAdminId` TEXT NOT NULL,
                `deletedByAdminName` TEXT NOT NULL,
                `deleteReason` TEXT NOT NULL,
                `deletedAt` INTEGER NOT NULL,
                `expiresAt` INTEGER NOT NULL,
                `isTestData` INTEGER NOT NULL DEFAULT 0,
                `testTag` TEXT NOT NULL DEFAULT 'NONE',
                `isRestored` INTEGER NOT NULL DEFAULT 0,
                `restoredAt` INTEGER,
                `restoredByAdminId` TEXT
            )
        """.trimIndent())
        db.execSQL("ALTER TABLE products ADD COLUMN isTestData INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE products ADD COLUMN testTag TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE companies ADD COLUMN isTestData INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE companies ADD COLUMN testTag TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE companies ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `reminders` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `deduplicationKey` TEXT NOT NULL,
                `roleTarget` TEXT NOT NULL,
                `targetUserId` TEXT,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `priority` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `targetRoute` TEXT,
                `targetEntityId` TEXT,
                `scheduledTime` INTEGER NOT NULL,
                `completedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `isSystemGenerated` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `reminder_preferences` (
                `userId` TEXT NOT NULL PRIMARY KEY,
                `isMasterEnabled` INTEGER NOT NULL DEFAULT 1,
                `isNotificationEnabled` INTEGER NOT NULL DEFAULT 1,
                `isVoiceEnabled` INTEGER NOT NULL DEFAULT 1,
                `voiceLanguage` TEXT NOT NULL DEFAULT 'bn',
                `quietHoursEnabled` INTEGER NOT NULL DEFAULT 1,
                `quietHoursStartHour` INTEGER NOT NULL DEFAULT 22,
                `quietHoursEndHour` INTEGER NOT NULL DEFAULT 7,
                `scheduledDailyHour` INTEGER NOT NULL DEFAULT 18,
                `scheduledDailyMinute` INTEGER NOT NULL DEFAULT 30,
                `enabledCategories` TEXT NOT NULL DEFAULT 'DAILY_CLOSING,EXPENSE_APPROVAL,PRODUCT_APPROVAL,STOCK_ALERT,CUSTOMER_DUE,SALESMAN_FOLLOWUP,CUSTOM_TASK',
                `updatedAt` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `admin_alerts` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `adminId` TEXT NOT NULL,
                `adminName` TEXT NOT NULL,
                `targetType` TEXT NOT NULL,
                `targetSalesmanId` TEXT,
                `targetSalesmanName` TEXT,
                `title` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `priority` TEXT NOT NULL,
                `isVoiceEnabled` INTEGER NOT NULL DEFAULT 1,
                `isNotificationEnabled` INTEGER NOT NULL DEFAULT 1,
                `scheduledTime` INTEGER NOT NULL DEFAULT 0,
                `isScheduled` INTEGER NOT NULL DEFAULT 0,
                `isCancelled` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `salesman_alert_receipts` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `alertId` TEXT NOT NULL,
                `salesmanId` TEXT NOT NULL,
                `salesmanName` TEXT NOT NULL,
                `isDelivered` INTEGER NOT NULL DEFAULT 1,
                `deliveredAt` INTEGER,
                `isAcknowledged` INTEGER NOT NULL DEFAULT 0,
                `acknowledgedAt` INTEGER,
                `isCompleted` INTEGER NOT NULL DEFAULT 0,
                `completedAt` INTEGER,
                `notifiedAt` INTEGER
            )
        """.trimIndent())
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `stock_verifications` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `productId` TEXT NOT NULL,
                `productName` TEXT NOT NULL,
                `productSku` TEXT NOT NULL DEFAULT '',
                `productCategory` TEXT NOT NULL DEFAULT '',
                `productUnit` TEXT NOT NULL DEFAULT 'পিস',
                `appStockSnapshot` REAL NOT NULL,
                `physicalStock` REAL NOT NULL,
                `difference` REAL NOT NULL,
                `differenceType` TEXT NOT NULL,
                `verifierUserId` TEXT NOT NULL,
                `verifierUserName` TEXT NOT NULL,
                `verifierRole` TEXT NOT NULL,
                `verifiedAt` INTEGER NOT NULL,
                `notes` TEXT NOT NULL DEFAULT '',
                `imageUri` TEXT DEFAULT NULL,
                `status` TEXT NOT NULL,
                `reviewedByAdminId` TEXT DEFAULT NULL,
                `reviewedByAdminName` TEXT DEFAULT NULL,
                `reviewedAt` INTEGER DEFAULT NULL,
                `adminNote` TEXT DEFAULT NULL,
                `isResolved` INTEGER NOT NULL DEFAULT 0,
                `stockLogId` INTEGER DEFAULT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `stock_verification_schedules` (
                `productId` TEXT NOT NULL PRIMARY KEY,
                `intervalDays` INTEGER NOT NULL DEFAULT 7,
                `lastVerifiedAt` INTEGER NOT NULL DEFAULT 0,
                `nextDueAt` INTEGER NOT NULL DEFAULT 0,
                `assignedSalesmanId` TEXT DEFAULT NULL,
                `assignedSalesmanName` TEXT DEFAULT NULL,
                `isReminderEnabled` INTEGER NOT NULL DEFAULT 1,
                `updatedAt` INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE customers ADD COLUMN isTestData INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE customers ADD COLUMN testTag TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE customers ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE customers ADD COLUMN openingDue REAL NOT NULL DEFAULT 0.0")

        db.execSQL("ALTER TABLE sales ADD COLUMN isTestData INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sales ADD COLUMN testTag TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sales ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE payments ADD COLUMN isTestData INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE payments ADD COLUMN testTag TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE payments ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE expenses ADD COLUMN isTestData INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE expenses ADD COLUMN testTag TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE expenses ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN shopId TEXT NOT NULL DEFAULT ''")
        // Assign existing legacy seed users to default shop so they do not pollute new registrations
        db.execSQL("UPDATE users SET shopId = 'biz_smart_store_1' WHERE shopId = '' OR shopId IS NULL")
    }
}

@Database(
    entities = [
        User::class,
        Product::class,
        Customer::class,
        Sale::class,
        SaleItem::class,
        PaymentReceipt::class,
        Expense::class,
        StockLog::class,
        AuditLog::class,
        CompanySettings::class,
        ProductSubmission::class,
        Company::class,
        CompanyPurchase::class,
        CompanyPayment::class,
        RecycleBinItem::class,
        ReminderItem::class,
        ReminderPreference::class,
        AdminAlert::class,
        SalesmanAlertReceipt::class,
        StockVerification::class,
        StockVerificationSchedule::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun paymentReceiptDao(): PaymentReceiptDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun stockLogDao(): StockLogDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun companySettingsDao(): CompanySettingsDao
    abstract fun productSubmissionDao(): ProductSubmissionDao
    abstract fun companyDao(): CompanyDao
    abstract fun companyPurchaseDao(): CompanyPurchaseDao
    abstract fun companyPaymentDao(): CompanyPaymentDao
    abstract fun recycleBinDao(): RecycleBinDao
    abstract fun reminderDao(): ReminderDao
    abstract fun alertDao(): AlertDao
    abstract fun stockVerificationDao(): StockVerificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_business_database.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12
                    )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun setInstanceForTesting(db: AppDatabase?) {
            synchronized(this) {
                INSTANCE = db
            }
        }
    }
}
