package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import com.example.data.service.ProductImageService
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BusinessRepository(
    private val db: AppDatabase,
    val productImageService: ProductImageService? = null
) {
    val users = db.userDao().getAllUsers()
    val activeUsers = db.userDao().getAllActiveUsers()
    val products = db.productDao().getAllProducts()
    val lowStockProducts = db.productDao().getLowStockProducts()
    val customers = db.customerDao().getAllCustomers()
    val allSales = db.saleDao().getAllSales()
    val allReceipts = db.paymentReceiptDao().getAllReceipts()
    val allExpenses = db.expenseDao().getAllExpenses()
    val stockLogs = db.stockLogDao().getAllStockLogs()
    val auditLogs = db.auditLogDao().getAllAuditLogs()
    val settingsFlow = db.companySettingsDao().getSettingsFlow()
    val allProductSubmissions = db.productSubmissionDao().getAllSubmissions()
    val pendingProductSubmissionsCount = db.productSubmissionDao().getPendingCount()
    val companies = db.companyDao().getAllCompaniesFlow()
    val companyPurchases = db.companyPurchaseDao().getAllPurchasesFlow()
    val companyPayments = db.companyPaymentDao().getAllPaymentsFlow()
    val recycleBinItems = db.recycleBinDao().getActiveRecycleBinItems()
    val allRecycleBinItems = db.recycleBinDao().getAllRecycleBinItems()
    val recycleBinCount = db.recycleBinDao().getActiveCount()
    val testProducts = db.productDao().getTestProducts()
    val testCompanies = db.companyDao().getTestCompaniesFlow()
    val testCustomers = db.customerDao().getTestCustomers()
    val testSales = db.saleDao().getTestSales()
    val testReceipts = db.paymentReceiptDao().getTestReceipts()
    val testExpenses = db.expenseDao().getTestExpenses()

    fun getCompanyPurchases(companyId: String) = db.companyPurchaseDao().getPurchasesByCompanyFlow(companyId)
    fun getCompanyPayments(companyId: String) = db.companyPaymentDao().getPaymentsByCompanyFlow(companyId)
    fun getCompanyByIdFlow(companyId: String) = db.companyDao().getCompanyByIdFlow(companyId)

    fun getSalesBySalesman(salesmanId: String) = db.saleDao().getSalesBySalesman(salesmanId)
    fun getReceiptsBySalesman(salesmanId: String) = db.paymentReceiptDao().getReceiptsBySalesman(salesmanId)
    fun getExpensesBySalesman(salesmanId: String) = db.expenseDao().getExpensesBySalesman(salesmanId)
    fun getSubmissionsBySalesman(salesmanId: String) = db.productSubmissionDao().getSubmissionsBySalesman(salesmanId)
    fun getSubmissionsByStatus(status: ProductSubmissionStatus) = db.productSubmissionDao().getSubmissionsByStatus(status)

    // Smart Reminder Flows
    fun getAdminRemindersFlow(): Flow<List<ReminderItem>> = db.reminderDao().getAdminRemindersFlow()
    fun getSalesmanRemindersFlow(salesmanId: String): Flow<List<ReminderItem>> = db.reminderDao().getSalesmanRemindersFlow(salesmanId)
    fun getAdminPendingCountFlow(): Flow<Int> = db.reminderDao().getAdminPendingCountFlow()
    fun getSalesmanPendingCountFlow(salesmanId: String): Flow<Int> = db.reminderDao().getSalesmanPendingCountFlow(salesmanId)
    fun getReminderPreferenceFlow(userId: String): Flow<ReminderPreference?> = db.reminderDao().getPreferenceFlowForUser(userId)

    suspend fun getReminderById(id: String): ReminderItem? = db.reminderDao().getReminderById(id)
    suspend fun insertReminder(reminder: ReminderItem) = db.reminderDao().insertReminder(reminder)
    suspend fun updateReminderStatus(id: String, status: ReminderStatus, completedAt: Long? = null) = db.reminderDao().updateStatus(id, status, completedAt)
    suspend fun deleteReminder(id: String) = db.reminderDao().deleteReminder(id)
    suspend fun saveReminderPreference(preference: ReminderPreference) = db.reminderDao().insertOrUpdatePreference(preference)
    suspend fun getReminderPreference(userId: String): ReminderPreference = db.reminderDao().getPreferenceForUser(userId) ?: ReminderPreference(userId = userId)

    fun getDbInstance(): AppDatabase = db

    suspend fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun seedInitialDataIfEmpty(includeSampleDemoData: Boolean = false) {
        if (db.userDao().getUserCount() == 0) {
            val adminPin = hashPin("1234")
            val admin = User(
                id = "admin_1",
                name = "মোঃ আতাউর রহমান",
                phone = "01700000000",
                role = UserRole.ADMIN,
                pinHash = adminPin,
                isActive = true,
                accountStatus = UserAccountStatus.ACTIVE,
                shopId = "biz_smart_store_1"
            )
            val salesman = User(
                id = "salesman_1",
                name = "সেলসম্যান হাসিবুল করিম",
                phone = "01800000000",
                role = UserRole.SALESMAN,
                pinHash = adminPin,
                isActive = true,
                accountStatus = UserAccountStatus.ACTIVE,
                shopId = "biz_smart_store_1"
            )
            db.userDao().insertUser(admin)
            db.userDao().insertUser(salesman)

            db.companySettingsDao().insertOrUpdate(CompanySettings())

            if (includeSampleDemoData) {
                val sampleProducts = listOf(
                    Product(
                        id = "p_1",
                        name = "মিনিকেট চাল (৫০ কেজি বস্তা)",
                        sku = "RICE-50",
                        barcode = "8941001",
                        category = "মুদি ও খাদ্য",
                        purchasePrice = 3100.0,
                        sellingPrice = 3450.0,
                        currentStock = 45.0,
                        alertStockLevel = 10.0,
                        unit = "বস্তা",
                        isTestData = true,
                        testTag = "SAMPLE_SEED"
                    ),
                    Product(
                        id = "p_2",
                        name = "তীর সোয়াবিন তেল (৫ লিটার)",
                        sku = "OIL-5L",
                        barcode = "8941002",
                        category = "তেল ও চর্বি",
                        purchasePrice = 780.0,
                        sellingPrice = 850.0,
                        currentStock = 30.0,
                        alertStockLevel = 8.0,
                        unit = "বোতল",
                        isTestData = true,
                        testTag = "SAMPLE_SEED"
                    ),
                    Product(
                        id = "p_3",
                        name = "মেঘনা চিনি (৫০ কেজি)",
                        sku = "SUGAR-50",
                        barcode = "8941003",
                        category = "মুদি ও খাদ্য",
                        purchasePrice = 6200.0,
                        sellingPrice = 6600.0,
                        currentStock = 4.0,
                        alertStockLevel = 6.0,
                        unit = "বস্তা",
                        isTestData = true,
                        testTag = "SAMPLE_SEED"
                    ),
                    Product(
                        id = "p_4",
                        name = "প্রাণ মসুর ডাল (১ কেজি)",
                        sku = "LENTIL-1KG",
                        barcode = "8941004",
                        category = "ডাল",
                        purchasePrice = 115.0,
                        sellingPrice = 135.0,
                        currentStock = 120.0,
                        alertStockLevel = 20.0,
                        unit = "কেজি",
                        isTestData = true,
                        testTag = "SAMPLE_SEED"
                    )
                )
                sampleProducts.forEach {
                    db.productDao().insertProduct(it)
                    db.stockLogDao().insertStockLog(
                        StockLog(
                            productId = it.id,
                            productName = it.name,
                            changeType = "প্রারম্ভিক স্টক",
                            quantityChanged = it.currentStock,
                            remainingStock = it.currentStock,
                            reason = "নমুনা ডেমো প্রারম্ভিক স্টক এন্ট্রি"
                        )
                    )
                }

                val sampleCustomers = listOf(
                    Customer(
                        id = "c_1",
                        name = "মেসার্স হাজী ব্রাদার্স",
                        phone = "01911223344",
                        address = "নিউ মার্কেট, ঢাকা",
                        totalBilled = 15000.0,
                        totalPaid = 10000.0,
                        currentDue = 5000.0,
                        isPinned = true,
                        isTestData = true,
                        testTag = "SAMPLE_SEED"
                    ),
                    Customer(
                        id = "c_2",
                        name = "জননী স্টোর (কালাম ভাই)",
                        phone = "01722334455",
                        address = "মিরপুর-১০, ঢাকা",
                        totalBilled = 8500.0,
                        totalPaid = 8500.0,
                        currentDue = 0.0,
                        isPinned = false,
                        isTestData = true,
                        testTag = "SAMPLE_SEED"
                    ),
                    Customer(
                        id = "c_3",
                        name = "ভাই ভাই এন্টারপ্রাইজ",
                        phone = "01833445566",
                        address = "উত্তরা সেক্টর ৭",
                        totalBilled = 22000.0,
                        totalPaid = 14000.0,
                        currentDue = 8000.0,
                        isPinned = false,
                        isTestData = true,
                        testTag = "SAMPLE_SEED"
                    )
                )
                sampleCustomers.forEach { db.customerDao().insertCustomer(it) }

                if (db.companyDao().countCompanies() == 0) {
                    val sampleCompanies = listOf(
                        Company(
                            id = "comp_1",
                            name = "আকিজ ফুডস অ্যান্ড বেভারেজ লিঃ",
                            phone = "01711000111",
                            address = "মতিঝিল বা/এ, ঢাকা",
                            note = "চাল, চিনি, তেল ও নিত্যপ্রয়োজনীয় ভোগ্যপণ্য সরবরাহকারী",
                            totalPurchased = 120000.0,
                            totalPaid = 80000.0,
                            currentDue = 40000.0,
                            advanceAmount = 0.0,
                            isTestData = true,
                            testTag = "SAMPLE_SEED"
                        ),
                        Company(
                            id = "comp_2",
                            name = "স্কয়ার কনজিউমার সাপ্লাইয়ার্স",
                            phone = "01722000222",
                            address = "উত্তরা, ঢাকা",
                            note = "তেল, মসলা ও প্যাকেটজাত ভোগ্যপণ্য",
                            totalPurchased = 85000.0,
                            totalPaid = 85000.0,
                            currentDue = 0.0,
                            advanceAmount = 0.0,
                            isTestData = true,
                            testTag = "SAMPLE_SEED"
                        ),
                        Company(
                            id = "comp_3",
                            name = "প্রাণ ফুডস সাপ্লাইয়ার্স",
                            phone = "01733000333",
                            address = "বাড্ডা, ঢাকা",
                            note = "স্ন্যাক্স, জুস ও বেকারি পণ্য পরিবেশক",
                            totalPurchased = 50000.0,
                            totalPaid = 60000.0,
                            currentDue = 0.0,
                            advanceAmount = 10000.0,
                            isTestData = true,
                            testTag = "SAMPLE_SEED"
                        )
                    )
                    sampleCompanies.forEach { db.companyDao().insertCompany(it) }
                }
            }

            db.auditLogDao().insertAuditLog(
                AuditLog(
                    userId = "system",
                    userName = "সিস্টেম",
                    action = "ইনিশিয়ালাইজেশন",
                    details = "স্মার্ট বিজনেস ম্যানেজার সিস্টেম প্রারম্ভিক অ্যাডমিন অ্যাকাউন্ট সফলভাবে সেটআপ হয়েছে。"
                )
            )
        } else {
            // Ensure Admin name is updated to "মোঃ আতাউর রহমান" if it has old name, and assign legacy shopId
            val existingAdmin = db.userDao().getUserById("admin_1")
            if (existingAdmin != null) {
                var updated = existingAdmin
                if (updated.name != "মোঃ আতাউর রহমান") {
                    updated = updated.copy(name = "মোঃ আতাউর রহমান")
                }
                if (updated.shopId.isBlank()) {
                    updated = updated.copy(shopId = "biz_smart_store_1")
                }
                if (updated != existingAdmin) {
                    db.userDao().updateUser(updated)
                }
            }
            val existingSalesman = db.userDao().getUserById("salesman_1")
            if (existingSalesman != null && existingSalesman.shopId.isBlank()) {
                db.userDao().updateUser(existingSalesman.copy(shopId = "biz_smart_store_1"))
            }
        }
    }

    // Role & Account Status Enforcement on Login
    sealed class AuthResult {
        data class Success(val user: User) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    suspend fun authenticateUser(phone: String, pin: String): AuthResult {
        var user = db.userDao().getUserByPhone(phone)
            ?: return AuthResult.Error("মোবাইল নম্বর খুঁজে পাওয়া যায়নি!")


        val inputHash = hashPin(pin)
        if (user.pinHash != inputHash) {
            return AuthResult.Error("ভুল পিন কোড! পুনরায় সঠিক পিন দিয়ে চেষ্টা করুন।")
        }

        // Strict Enforcement for Suspended / Deactivated users
        if (!user.isActive || user.accountStatus != UserAccountStatus.ACTIVE) {
            val statusLabel = when (user.accountStatus) {
                UserAccountStatus.SUSPENDED -> "সাময়িক স্থগিত (Suspended)"
                UserAccountStatus.DEACTIVATED -> "নিষ্ক্রিয় বা বিচ্ছিন্ন (Deactivated)"
                else -> "নিষ্ক্রিয়"
            }
            val reasonMsg = if (user.statusReason.isNotBlank()) "\nকারণ: ${user.statusReason}" else ""
            db.auditLogDao().insertAuditLog(
                AuditLog(
                    userId = user.id,
                    userName = user.name,
                    action = "অননুমোদিত প্রবেশের চেষ্টা",
                    details = "$statusLabel অ্যাকাউন্টে লগইন করার চেষ্টা প্রতিহত করা হয়েছে।$reasonMsg"
                )
            )
            return AuthResult.Error("আপনার অ্যাকাউন্টটি $statusLabel করা হয়েছে।$reasonMsg\nকর্তৃপক্ষের সাথে যোগাযোগ করুন।")
        }

        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = user.id,
                userName = user.name,
                action = "লগইন",
                details = "${user.role} হিসেবে সফল লগইন হয়েছে।"
            )
        )
        return AuthResult.Success(user)
    }

    suspend fun authenticate(phone: String, pin: String): User? {
        val res = authenticateUser(phone, pin)
        return if (res is AuthResult.Success) res.user else null
    }

    suspend fun authenticateAdmin(phone: String, pin: String): AuthResult {
        val res = authenticateUser(phone, pin)
        if (res is AuthResult.Success) {
            if (res.user.role != UserRole.ADMIN) {
                db.auditLogDao().insertAuditLog(
                    AuditLog(
                        userId = res.user.id,
                        userName = res.user.name,
                        action = "অননুমোদিত এডমিন প্রবেশের চেষ্টা",
                        details = "সেলসম্যান ${res.user.name} (${res.user.phone}) গোপন এডমিন পোর্টালে প্রবেশের চেষ্টা করেছিলেন। প্রবেশ সম্পূর্ণ ব্লক করা হয়েছে।"
                    )
                )
                return AuthResult.Error("অননুমোদিত প্রবেশ! আপনার অ্যাকাউন্টটি সেলসম্যান (SALESMAN)। এডমিন প্যানেলে প্রবেশের কোনো অধিকার আপনার নেই।")
            }
        }
        return res
    }

    suspend fun getUserByPhone(phone: String): User? {
        val user = db.userDao().getUserByPhone(phone.trim()) ?: return null
        if (user.id == "admin_1" && user.name != "মোঃ আতাউর রহমান") {
            val updated = user.copy(name = "মোঃ আতাউর রহমান")
            db.userDao().updateUser(updated)
            return updated
        }
        return user
    }

    /**
     * Registers a new shop with the owner as Admin.
     * Prevents duplicate registrations with the same mobile number.
     * Securely hashes PIN using SHA-256.
     * Updates CompanySettings with the new shop's name, phone, address.
     */
    suspend fun registerNewShop(
        shopName: String,
        ownerName: String,
        phone: String,
        address: String,
        district: String,
        businessType: String,
        pin: String,
        shopId: String = ""
    ): Result<Pair<User, CompanySettings>> {
        val cleanPhone = phone.trim()
        val cleanShopName = shopName.trim()
        val cleanOwnerName = ownerName.trim()

        if (cleanShopName.length < 2) {
            return Result.failure(IllegalArgumentException("দোকানের নাম কমপক্ষে ২ অক্ষরের হতে হবে!"))
        }
        if (cleanOwnerName.length < 2) {
            return Result.failure(IllegalArgumentException("মালিকের নাম কমপক্ষে ২ অক্ষরের হতে হবে!"))
        }
        if (cleanPhone.length < 11) {
            return Result.failure(IllegalArgumentException("সঠিক ১১ ডিজিটের মোবাইল নম্বর দিন (যেমন: 017XXXXXXXX)!"))
        }
        if (pin.length < 4 || pin.length > 6) {
            return Result.failure(IllegalArgumentException("পিন কোড ৪ থেকে ৬ সংখ্যার হতে হবে!"))
        }

        val resolvedShopId = shopId.ifBlank { "biz_${System.currentTimeMillis() % 10000000}_${(1000..9999).random()}" }

        // Check duplicate phone in local DB
        val existing = db.userDao().getUserByPhone(cleanPhone)
        if (existing != null) {
            if (existing.role != UserRole.ADMIN || (existing.shopId.isNotBlank() && existing.shopId != resolvedShopId)) {
                return Result.failure(IllegalStateException("এই মোবাইল নম্বর (${cleanPhone}) দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট নিবন্ধিত রয়েছে!"))
            }
        }

        val hashedPin = hashPin(pin)
        val adminId = existing?.id ?: "admin_${System.currentTimeMillis() % 10000000}_${(100..999).random()}"
        val newAdmin = User(
            id = adminId,
            name = cleanOwnerName,
            phone = cleanPhone,
            role = UserRole.ADMIN,
            pinHash = hashedPin,
            isActive = true,
            accountStatus = UserAccountStatus.ACTIVE,
            shopId = resolvedShopId
        )

        val fullAddress = if (district.isNotBlank()) {
            if (address.isNotBlank()) {
                if (address.contains(district)) address.trim() else "${address.trim()}, $district"
            } else district.trim()
        } else {
            address.trim()
        }

        val newSettings = CompanySettings(
            id = 1,
            businessName = cleanShopName,
            phone = cleanPhone,
            address = fullAddress.ifBlank { "বাংলাদেশ" }
        )

        db.withTransaction {
            db.userDao().insertUser(newAdmin)
            db.companySettingsDao().insertOrUpdate(newSettings)
            db.auditLogDao().insertAuditLog(
                AuditLog(
                    userId = adminId,
                    userName = cleanOwnerName,
                    action = "নতুন দোকান স্ব-নিবন্ধন",
                    details = "দোকানের নাম: '$cleanShopName', মালিক: '$cleanOwnerName', মোবাইল: $cleanPhone, ধরন: $businessType, ঠিকানা: $fullAddress (সম্পূর্ণ ফ্রি ব্যবহার)"
                )
            )
        }

        return Result.success(Pair(newAdmin, newSettings))
    }

    // Verify current user validity in background
    suspend fun isUserActiveAndAllowed(userId: String): Boolean {
        val user = db.userDao().getUserById(userId) ?: return false
        return user.isActive && user.accountStatus == UserAccountStatus.ACTIVE
    }

    // Atomic Sale Transaction: Records sale with historical snapshot of purchasePriceAtSale
    suspend fun completeSale(
        sale: Sale,
        items: List<SaleItem>,
        actorUser: User
    ): Result<String> {
        return try {
            // Role & Active Status Check
            if (!isUserActiveAndAllowed(actorUser.id)) {
                throw IllegalStateException("আপনার অ্যাকাউন্ট স্থগিত বা নিষ্ক্রিয় রয়েছে! আপনি নতুন বিক্রি করতে পারবেন না।")
            }

            db.withTransaction {
                val existing = db.saleDao().getSaleByInvoice(sale.invoiceNumber)
                if (existing != null) {
                    throw IllegalStateException("এই ইনভয়েস নম্বর (${sale.invoiceNumber}) ইতিমধ্যে সংরক্ষিত আছে!")
                }

                // Verify stock and capture current historical purchase price accurately
                val itemsWithSnapshot = items.map { item ->
                    val prod = db.productDao().getProductById(item.productId)
                        ?: throw IllegalStateException("পণ্য ${item.productName} পাওয়া যায়নি!")
                    if (prod.currentStock < item.quantity) {
                        throw IllegalStateException("পণ্য '${item.productName}' এর পর্যাপ্ত স্টক নেই! বর্তমান স্টক: ${prod.currentStock} ${prod.unit}")
                    }
                    // Guarantee historical snapshot is stored from database at sale execution
                    item.copy(purchasePriceAtSale = prod.purchasePrice)
                }

                db.saleDao().insertSale(sale)
                db.saleDao().insertSaleItems(itemsWithSnapshot)

                for (item in itemsWithSnapshot) {
                    db.productDao().adjustStock(item.productId, -item.quantity)
                    val updatedProd = db.productDao().getProductById(item.productId)
                    db.stockLogDao().insertStockLog(
                        StockLog(
                            productId = item.productId,
                            productName = item.productName,
                            changeType = "বিক্রি",
                            quantityChanged = -item.quantity,
                            remainingStock = updatedProd?.currentStock ?: 0.0,
                            referenceId = sale.invoiceNumber,
                            reason = "ইনভয়েস #${sale.invoiceNumber} বিক্রয়"
                        )
                    )
                }

                db.customerDao().updateCustomerBalances(
                    id = sale.customerId,
                    billedDelta = sale.totalAmount,
                    paidDelta = sale.paidAmount,
                    dueDelta = sale.dueAmount
                )

                if (sale.paidAmount > 0) {
                    val receiptNo = "RCT-${System.currentTimeMillis().toString().takeLast(6)}"
                    val customer = db.customerDao().getCustomerById(sale.customerId)
                    val receipt = PaymentReceipt(
                        receiptNumber = receiptNo,
                        invoiceNumber = sale.invoiceNumber,
                        customerId = sale.customerId,
                        customerName = sale.customerName,
                        customerPhone = sale.customerPhone,
                        salesmanId = sale.salesmanId,
                        salesmanName = sale.salesmanName,
                        amount = sale.paidAmount,
                        previousDue = (customer?.currentDue ?: 0.0) + sale.paidAmount,
                        currentDue = customer?.currentDue ?: 0.0,
                        paymentMethod = sale.paymentMethod,
                        note = "বিক্রির সাথে তাৎক্ষণিক প্রাপ্তি (ইনভয়েস: ${sale.invoiceNumber})"
                    )
                    db.paymentReceiptDao().insertReceipt(receipt)
                }

                db.auditLogDao().insertAuditLog(
                    AuditLog(
                        userId = actorUser.id,
                        userName = actorUser.name,
                        action = "নতুন বিক্রি",
                        details = "ইনভয়েস: ${sale.invoiceNumber}, কাস্টমার: ${sale.customerName}, মোট বিল: ৳${sale.totalAmount}, জমা: ৳${sale.paidAmount}, বাকি: ৳${sale.dueAmount}"
                    )
                )
            }
            Result.success(sale.invoiceNumber)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun collectPayment(
        receipt: PaymentReceipt,
        actorUser: User
    ): Result<String> {
        return try {
            if (!isUserActiveAndAllowed(actorUser.id)) {
                throw IllegalStateException("আপনার অ্যাকাউন্ট স্থগিত বা নিষ্ক্রিয় রয়েছে! আপনি টাকা আদায় করতে পারবেন না।")
            }

            db.withTransaction {
                val customer = db.customerDao().getCustomerById(receipt.customerId)
                    ?: throw IllegalStateException("কাস্টমার তথ্য পাওয়া যায়নি!")

                if (receipt.amount <= 0) {
                    throw IllegalStateException("টাকার পরিমাণ শূন্য বা ঋণাত্মক হতে পারে না!")
                }

                db.paymentReceiptDao().insertReceipt(receipt)

                db.customerDao().updateCustomerBalances(
                    id = receipt.customerId,
                    billedDelta = 0.0,
                    paidDelta = receipt.amount,
                    dueDelta = -receipt.amount
                )

                db.auditLogDao().insertAuditLog(
                    AuditLog(
                        userId = actorUser.id,
                        userName = actorUser.name,
                        action = "টাকা আদায়",
                        details = "রসিদ নং: ${receipt.receiptNumber}, কাস্টমার: ${receipt.customerName}, পরিমাণ: ৳${receipt.amount}, মাধ্যম: ${receipt.paymentMethod}"
                    )
                )
            }
            Result.success(receipt.receiptNumber)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitExpense(expense: Expense, actorUser: User) {
        if (!isUserActiveAndAllowed(actorUser.id)) {
            throw IllegalStateException("আপনার অ্যাকাউন্ট স্থগিত বা নিষ্ক্রিয় রয়েছে!")
        }
        db.expenseDao().insertExpense(expense)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = actorUser.id,
                userName = actorUser.name,
                action = "খরচের আবেদন",
                details = "বিভাগ: ${expense.category}, পরিমাণ: ৳${expense.amount}, বিবরণ: ${expense.description}"
            )
        )
    }

    suspend fun updateExpense(expense: Expense, adminUser: User) {
        if (adminUser.role != UserRole.ADMIN) throw IllegalStateException("অননুমোদিত অ্যাক্সেস!")
        db.expenseDao().updateExpense(expense)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = adminUser.id,
                userName = adminUser.name,
                action = "খরচ পর্যালোচনা",
                details = "আইডি: ${expense.id}, স্ট্যাটাস: ${expense.status}, নোট: ${expense.adminNote}"
            )
        )
    }

    suspend fun saveCustomer(customer: Customer, actorUser: User) {
        if (!isUserActiveAndAllowed(actorUser.id)) {
            throw IllegalStateException("অননুমোদিত অ্যাকাউন্ট!")
        }
        db.customerDao().insertCustomer(customer)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = actorUser.id,
                userName = actorUser.name,
                action = "কাস্টমার সংরক্ষণ",
                details = "নাম: ${customer.name}, ফোন: ${customer.phone}"
            )
        )
    }

    suspend fun toggleCustomerPin(customerId: String, isPinned: Boolean) {
        db.customerDao().updatePinStatus(customerId, isPinned)
    }

    suspend fun toggleSalePin(invoiceNumber: String, isPinned: Boolean) {
        db.saleDao().updatePinStatus(invoiceNumber, isPinned)
    }

    suspend fun toggleReceiptPin(receiptNumber: String, isPinned: Boolean) {
        db.paymentReceiptDao().updatePinStatus(receiptNumber, isPinned)
    }

    // Product Add & Edit with Validation, Price History Log & Snapshot Safety
    suspend fun saveProduct(
        product: Product,
        actorUser: User,
        isNew: Boolean = false,
        reason: String = ""
    ) {
        if (actorUser.role != UserRole.ADMIN) {
            throw IllegalStateException("শুধুমাত্র অ্যাডমিন পণ্য যোগ বা পরিবর্তন করতে পারবেন!")
        }

        if (product.name.isBlank()) throw IllegalArgumentException("পণ্যের নাম খালি রাখা যাবে না!")
        if (product.purchasePrice < 0) throw IllegalArgumentException("ক্রয়মূল্য ঋণাত্মক হতে পারে না!")

        // Rule 1, 2, 3: For Admin Product Add & Edit, auto calculate and enforce Selling Price = Cost Price * 1.70
        val finalSellingPrice = calculateSellingPrice(product.purchasePrice)
        if (finalSellingPrice < 0) throw IllegalArgumentException("বিক্রয়মূল্য ঋণাত্মক হতে পারে না!")

        val preparedProduct = product.copy(sellingPrice = finalSellingPrice)

        val oldProduct = if (!isNew) db.productDao().getProductById(preparedProduct.id) else null

        db.withTransaction {
            db.productDao().insertProduct(preparedProduct)

            if (isNew) {
                db.stockLogDao().insertStockLog(
                    StockLog(
                        productId = preparedProduct.id,
                        productName = preparedProduct.name,
                        changeType = "নতুন পণ্য",
                        quantityChanged = preparedProduct.currentStock,
                        remainingStock = preparedProduct.currentStock,
                        reason = if (reason.isNotBlank()) reason else "পণ্য ডাটাবেসে যোগ করা হয়েছে"
                    )
                )
            } else if (oldProduct != null) {
                // If price changed, record in StockLog for audit trail
                val priceChanged = oldProduct.purchasePrice != preparedProduct.purchasePrice || oldProduct.sellingPrice != preparedProduct.sellingPrice
                if (priceChanged) {
                    val priceChangeDetails = "ক্রয়: ৳${oldProduct.purchasePrice}→৳${preparedProduct.purchasePrice}, বিক্রয়: ৳${oldProduct.sellingPrice}→৳${preparedProduct.sellingPrice} | কারণ: $reason"
                    db.stockLogDao().insertStockLog(
                        StockLog(
                            productId = preparedProduct.id,
                            productName = preparedProduct.name,
                            changeType = "মূল্য পরিবর্তন",
                            quantityChanged = 0.0,
                            remainingStock = preparedProduct.currentStock,
                            reason = priceChangeDetails
                        )
                    )
                }
            }

            val auditAction = if (isNew) "নতুন পণ্য যোগ" else "পণ্য সম্পাদনা (Edit Product)"
            val auditDetails = if (isNew) {
                "পণ্য: ${preparedProduct.name}, বিক্রয়মূল্য: ৳${preparedProduct.sellingPrice}, ক্রয়মূল্য: ৳${preparedProduct.purchasePrice}, স্টক: ${preparedProduct.currentStock}"
            } else {
                "পণ্য: ${preparedProduct.name} (ID: ${preparedProduct.id}) আপডেট করা হয়েছে। কারণ: $reason। পূর্বের ক্রয়: ৳${oldProduct?.purchasePrice}→নতুন: ৳${preparedProduct.purchasePrice}, বিক্রয়: ৳${oldProduct?.sellingPrice}→নতুন: ৳${preparedProduct.sellingPrice} (পূর্বের বিক্রয় চালানের historical snapshot অপরিবর্তিত রাখা হয়েছে)"
            }

            db.auditLogDao().insertAuditLog(
                AuditLog(
                    userId = actorUser.id,
                    userName = actorUser.name,
                    action = auditAction,
                    details = auditDetails
                )
            )
        }
    }

    suspend fun adjustStockManual(
        productId: String,
        delta: Double,
        reason: String,
        actorUser: User
    ) {
        if (actorUser.role != UserRole.ADMIN) throw IllegalStateException("অননুমোদিত অ্যাক্সেস!")
        db.withTransaction {
            val prod = db.productDao().getProductById(productId) ?: return@withTransaction
            db.productDao().adjustStock(productId, delta)
            val updated = db.productDao().getProductById(productId)
            db.stockLogDao().insertStockLog(
                StockLog(
                    productId = productId,
                    productName = prod.name,
                    changeType = if (delta >= 0) "স্টক বৃদ্ধি/ক্রয়" else "স্টক হ্রাস/ক্ষতি",
                    quantityChanged = delta,
                    remainingStock = updated?.currentStock ?: 0.0,
                    reason = reason
                )
            )
            db.auditLogDao().insertAuditLog(
                AuditLog(
                    userId = actorUser.id,
                    userName = actorUser.name,
                    action = "স্টক সমন্বয়",
                    details = "পণ্য: ${prod.name}, পরিবর্তন: $delta, কারণ: $reason"
                )
            )
        }
    }

    suspend fun saveUser(user: User, adminUser: User) {
        if (adminUser.role != UserRole.ADMIN) throw IllegalStateException("অননুমোদিত অ্যাক্সেস!")
        val resolvedShopId = if (user.shopId.isNotBlank()) user.shopId else adminUser.shopId
        val userToSave = user.copy(shopId = resolvedShopId)
        db.userDao().insertUser(userToSave)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = adminUser.id,
                userName = adminUser.name,
                action = "ব্যবহারকারী তৈরি/আপডেট",
                details = "নাম: ${userToSave.name}, পদবী: ${userToSave.role}, স্ট্যাটাস: ${userToSave.accountStatus}, শপ: $resolvedShopId"
            )
        )
    }

    fun getUsersByShopFlow(shopId: String): Flow<List<User>> {
        return db.userDao().getUsersByShop(shopId)
    }

    suspend fun getUserByShopAndPhone(shopId: String, phone: String): User? {
        return db.userDao().getUserByShopAndPhone(shopId, phone.trim())
    }

    suspend fun resetStaffPin(targetUserId: String, shopId: String, newPin: String, adminUser: User): Result<Unit> {
        if (adminUser.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("অননুমোদিত অ্যাক্সেস! শুধুমাত্র অ্যাডমিন স্টাফের পিন রিসেট করতে পারেন।"))
        }
        val target = db.userDao().getUserById(targetUserId)
            ?: return Result.failure(IllegalArgumentException("স্টাফ অ্যাকাউন্ট পাওয়া যায়নি!"))
        if (adminUser.shopId.isNotBlank() && target.shopId.isNotBlank() && adminUser.shopId != target.shopId) {
            return Result.failure(IllegalStateException("অননুমোদিত অ্যাক্সেস! অন্য দোকানের স্টাফের তথ্য পরিবর্তন সম্ভব নয়।"))
        }
        if (newPin.trim().length < 4 || newPin.trim().length > 6) {
            return Result.failure(IllegalArgumentException("পিন কোড ৪ থেকে ৬ সংখ্যার হতে হবে!"))
        }
        val newHash = hashPin(newPin.trim())
        db.userDao().resetStaffPin(targetUserId, newHash)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = adminUser.id,
                userName = adminUser.name,
                action = "স্টাফ পিন কোড রিসেট",
                details = "স্টাফ: ${target.name} (${target.phone}), পদবী: ${target.role} এর পিন কোড অ্যাডমিন কর্তৃক রিসেট করা হয়েছে।"
            )
        )
        return Result.success(Unit)
    }

    suspend fun isDefaultAdminCredentialsActive(): Boolean {
        val admin = db.userDao().getUserById("admin_1") ?: return false
        val defaultPinHash = hashPin("1234")
        return admin.phone == "01700000000" && admin.pinHash == defaultPinHash
    }

    suspend fun getUserById(id: String): User? = db.userDao().getUserById(id)

    suspend fun saveUserDirect(user: User) {
        db.userDao().insertUser(user)
    }

    suspend fun saveCompanySettingsDirect(settings: CompanySettings) {
        db.companySettingsDao().insertOrUpdate(settings)
    }

    fun isAuthorizedAdminEmail(email: String): Boolean {
        return SuperAdminRepository.isSuperAdminEmail(email)
    }

    fun isAuthorizedSuperAdminEmail(email: String): Boolean {
        return SuperAdminRepository.isSuperAdminEmail(email)
    }

    fun isAuthorizedSuperAdminPhone(phone: String): Boolean {
        return SuperAdminRepository.isSuperAdminPhone(phone)
    }

    fun isRevokedSuperAdminEmail(email: String): Boolean {
        return SuperAdminRepository.isRevokedSuperAdminEmail(email)
    }

    suspend fun updateAdminSecurityCredentials(
        adminId: String,
        currentPin: String,
        newName: String,
        newPhone: String,
        newPin: String
    ): Result<Unit> {
        val admin = db.userDao().getUserById(adminId)
            ?: return Result.failure(IllegalStateException("অ্যাডমিন অ্যাকাউন্ট পাওয়া যায়নি"))
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("অননুমোদিত অ্যাক্সেস! শুধুমাত্র অ্যাডমিন এটি করতে পারবেন"))
        }
        val currentHashed = hashPin(currentPin.trim())
        if (admin.pinHash != currentHashed) {
            return Result.failure(IllegalArgumentException("বর্তমান পিন কোডটি সঠিক নয়!"))
        }
        val cleanPhone = newPhone.trim()
        val cleanPin = newPin.trim()
        if (cleanPhone.length < 11) {
            return Result.failure(IllegalArgumentException("সঠিক ১১ ডিজিটের মোবাইল নম্বর প্রদান করুন"))
        }
        if (cleanPin.length < 4) {
            return Result.failure(IllegalArgumentException("নতুন পিন কোড কমপক্ষে ৪ ডিজিটের হতে হবে"))
        }
        val existingUser = db.userDao().getUserByPhone(cleanPhone)
        if (existingUser != null && existingUser.id != adminId) {
            return Result.failure(IllegalArgumentException("এই মোবাইল নম্বরটি ইতিমধ্যে অন্য অ্যাকাউন্টে ব্যবহৃত হচ্ছে"))
        }
        val updatedAdmin = admin.copy(
            name = newName.trim().ifBlank { admin.name },
            phone = cleanPhone,
            pinHash = hashPin(cleanPin)
        )
        db.userDao().insertUser(updatedAdmin)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = adminId,
                userName = updatedAdmin.name,
                action = "অ্যাডমিন নিরাপত্তা ক্রেডেনশিয়াল আপডেট",
                details = "অ্যাডমিন মোবাইল (${updatedAdmin.phone}) ও গোপন পিন সফলভাবে পরিবর্তন করা হয়েছে"
            )
        )
        return Result.success(Unit)
    }

    // Suspend, Deactivate or Reactivate Salesman with Reason & Audit Trail
    suspend fun changeUserAccountStatus(
        targetUserId: String,
        newStatus: UserAccountStatus,
        reason: String,
        adminUser: User
    ) {
        if (adminUser.role != UserRole.ADMIN) throw IllegalStateException("শুধুমাত্র অ্যাডমিন কর্মচারীর স্ট্যাটাস পরিবর্তন করতে পারবেন!")

        val targetUser = db.userDao().getUserById(targetUserId)
            ?: throw IllegalStateException("ব্যবহারকারী পাওয়া যায়নি!")

        val isActive = newStatus == UserAccountStatus.ACTIVE
        val actionTitle = when (newStatus) {
            UserAccountStatus.ACTIVE -> "অ্যাকাউন্ট পুনঃসক্রিয়করণ (Reactivate)"
            UserAccountStatus.SUSPENDED -> "অ্যাকাউন্ট স্থগিত (Suspend)"
            UserAccountStatus.DEACTIVATED -> "অ্যাকাউন্ট বিচ্ছিন্ন/নিষ্ক্রিয় (Deactivate)"
        }

        db.withTransaction {
            db.userDao().updateUserStatus(
                userId = targetUserId,
                status = newStatus,
                isActive = isActive,
                reason = reason.trim()
            )

            db.auditLogDao().insertAuditLog(
                AuditLog(
                    userId = adminUser.id,
                    userName = adminUser.name,
                    action = actionTitle,
                    details = "সেলসম্যান: ${targetUser.name} (${targetUser.phone}), পূর্বের অবস্থা: ${targetUser.accountStatus} → বর্তমান অবস্থা: $newStatus, কারণ: $reason"
                )
            )
        }
    }

    suspend fun updateSettings(settings: CompanySettings, adminUser: User) {
        if (adminUser.role != UserRole.ADMIN) throw IllegalStateException("অননুমোদিত অ্যাক্সেস!")
        db.companySettingsDao().insertOrUpdate(settings)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = adminUser.id,
                userName = adminUser.name,
                action = "কোম্পানি সেটিংস আপডেট",
                details = "ব্যবসার নাম: ${settings.businessName}, ফোন: ${settings.phone}"
            )
        )
    }

    suspend fun getSaleItems(invoiceNumber: String): List<SaleItem> {
        return db.saleDao().getSaleItems(invoiceNumber)
    }

    suspend fun getNextInvoiceNumber(): String {
        val count = db.saleDao().getSalesCount() + 1
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return "INV-$dateStr-%03d".format(count)
    }

    suspend fun getNextReceiptNumber(): String {
        val count = db.paymentReceiptDao().getReceiptCount() + 1
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return "RCT-$dateStr-%03d".format(count)
    }

    companion object {
        // Profit markup rate: 70% on Cost Price (Selling Price = Cost Price * 1.70)
        const val SALESMAN_PROFIT_MARKUP_RATE = 0.70

        // Standard units for clothing business (Used across Admin & Salesman panels)
        val CLOTHING_UNITS = listOf(
            "পিস",
            "সেট",
            "জোড়া",
            "ডজন",
            "প্যাকেট",
            "কার্টন",
            "মিটার",
            "গজ"
        )

        // Clothing product categories
        val CLOTHING_CATEGORIES = listOf(
            "পুরুষদের পোশাক (Men's Wear)",
            "মহিলাদের পোশাক (Women's Wear)",
            "বাচ্চাদের পোশাক (Kids' Wear)",
            "শার্ট ও টি-শার্ট (Shirts & T-Shirts)",
            "প্যান্ট ও জিন্স (Pants & Jeans)",
            "পাঞ্জাবি ও পায়জামা (Panjabi & Pajama)",
            "শাড়ি ও থ্রি-পিস (Sharee & Three-Piece)",
            "বোরকা ও হিজাব (Burqa & Hijab)",
            "শীতকালীন পোশাক (Winter Wear)",
            "আন্ডারগার্মেন্টস ও ইনারওয়্যার (Undergarments)",
            "ঐতিহ্যবাহী ও পার্টি পোশাক (Party & Traditional)",
            "অন্যান্য পোশাক সামগ্রী (Other Clothing)"
        )

        fun calculateSellingPrice(costPrice: Double): Double {
            if (costPrice <= 0.0) return 0.0
            val calculated = costPrice * (1.0 + SALESMAN_PROFIT_MARKUP_RATE)
            return BigDecimal(calculated.toString())
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
        }
    }

    // =========================================================================
    // SALESMAN PRODUCT SUBMISSION & ADMIN APPROVAL WORKFLOW
    // =========================================================================

    suspend fun checkPotentialDuplicates(
        productName: String,
        productCode: String = "",
        currentSubmissionId: String? = null
    ): List<String> {
        val warnings = mutableListOf<String>()
        val trimmedName = productName.trim().lowercase()
        val trimmedCode = productCode.trim().lowercase()

        // 1. Check in live main products table by Name
        if (trimmedName.isNotBlank()) {
            val existingProd = db.productDao().findProductByName(trimmedName)
            if (existingProd != null) {
                warnings.add("মূল ইনভেন্টরিতে '${existingProd.name}' নামে ইতিমধ্যে একটি পণ্য রয়েছে (বর্তমান স্টক: ${existingProd.currentStock} ${existingProd.unit}, বিক্রয়মূল্য: ৳${existingProd.sellingPrice})")
            }
        }

        // 2. Check in live main products table by SKU / Code
        if (trimmedCode.isNotBlank()) {
            val existingBySku = db.productDao().findProductBySku(trimmedCode)
            if (existingBySku != null) {
                warnings.add("মূল ইনভেন্টরিতে '${existingBySku.name}' পণ্যে ইতিমধ্যে একই কোড (${existingBySku.sku}) ব্যবহার করা হয়েছে")
            }
        }

        // 3. Check in product submissions table by Name
        if (trimmedName.isNotBlank()) {
            val existingSubmissions = db.productSubmissionDao().findSubmissionsByName(trimmedName)
            for (sub in existingSubmissions) {
                if (sub.id != currentSubmissionId) {
                    val statusText = when (sub.status) {
                        ProductSubmissionStatus.PENDING -> "অপেক্ষমাণ"
                        ProductSubmissionStatus.APPROVED -> "অনুমোদিত"
                        ProductSubmissionStatus.NEEDS_CORRECTION -> "সংশোধন প্রয়োজন"
                    }
                    warnings.add("পূর্বে '${sub.salesmanName}' কর্তৃক '${sub.name}' নামে সাবমিশন করা হয়েছে (স্ট্যাটাস: $statusText)")
                }
            }
        }

        // 4. Check in product submissions table by Code
        if (trimmedCode.isNotBlank()) {
            val existingSubmissionsByCode = db.productSubmissionDao().findSubmissionsByProductCode(trimmedCode)
            for (sub in existingSubmissionsByCode) {
                if (sub.id != currentSubmissionId) {
                    val statusText = when (sub.status) {
                        ProductSubmissionStatus.PENDING -> "অপেক্ষমাণ"
                        ProductSubmissionStatus.APPROVED -> "অনুমোদিত"
                        ProductSubmissionStatus.NEEDS_CORRECTION -> "সংশোধন প্রয়োজন"
                    }
                    warnings.add("পূর্বে '${sub.salesmanName}' কর্তৃক একই কোড (${sub.productCode}) দিয়ে '${sub.name}' সাবমিট করা হয়েছে (স্ট্যাটাস: $statusText)")
                }
            }
        }

        return warnings
    }

    suspend fun submitProduct(submission: ProductSubmission, actorUser: User) {
        if (!isUserActiveAndAllowed(actorUser.id)) {
            throw IllegalStateException("আপনার অ্যাকাউন্ট স্থগিত বা নিষ্ক্রিয় রয়েছে! আপনি পণ্য জমা দিতে পারবেন না।")
        }

        if (submission.name.isBlank()) throw IllegalArgumentException("পণ্যের নাম খালি রাখা যাবে না!")
        if (submission.purchasePrice < 0) throw IllegalArgumentException("ক্রয়মূল্য ঋণাত্মক হতে পারে না!")
        if (submission.initialStock < 0) throw IllegalArgumentException("প্রাথমিক স্টক ঋণাত্মক হতে পারে না!")

        // Rule 1, 2, 7: For Salesman, Selling Price is strictly auto-calculated at Cost Price * 1.70 (70% profit markup)
        val finalSellingPrice = if (actorUser.role == UserRole.SALESMAN) {
            calculateSellingPrice(submission.purchasePrice)
        } else {
            submission.sellingPrice
        }
        if (finalSellingPrice < 0) throw IllegalArgumentException("বিক্রয়মূল্য ঋণাত্মক হতে পারে না!")

        val finalCode = if (submission.productCode.isNotBlank()) {
            submission.productCode.trim()
        } else {
            "SKU-${System.currentTimeMillis().toString().takeLast(4)}"
        }

        val preparedSubmission = submission.copy(
            productCode = finalCode,
            sellingPrice = finalSellingPrice,
            status = ProductSubmissionStatus.PENDING,
            adminCorrectionNote = "",
            updatedAt = System.currentTimeMillis()
        )

        db.productSubmissionDao().insertSubmission(preparedSubmission)

        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = actorUser.id,
                userName = actorUser.name,
                action = "নতুন প্রোডাক্ট জমা (Submission)",
                details = "পণ্য: ${preparedSubmission.name}, কোড: ${preparedSubmission.productCode}, ক্যাটাগরি: ${preparedSubmission.category}, ক্রয়: ৳${preparedSubmission.purchasePrice}, বিক্রয়: ৳${preparedSubmission.sellingPrice}, স্টক: ${preparedSubmission.initialStock} ${preparedSubmission.unit}, সেলসম্যান: ${actorUser.name} (${actorUser.phone})"
            )
        )
    }

    suspend fun submitProductForApproval(
        name: String,
        sku: String = "",
        category: String,
        unit: String = "পিস",
        purchasePrice: Double,
        initialStock: Double,
        description: String = "",
        salesmanUser: User
    ): String {
        val submissionId = "sub_${System.currentTimeMillis()}"
        val submission = ProductSubmission(
            id = submissionId,
            name = name,
            productCode = sku,
            category = category,
            purchasePrice = purchasePrice,
            sellingPrice = calculateSellingPrice(purchasePrice),
            initialStock = initialStock,
            unit = unit,
            description = description,
            salesmanId = salesmanUser.id,
            salesmanName = salesmanUser.name,
            salesmanPhone = salesmanUser.phone
        )
        submitProduct(submission, salesmanUser)
        return submissionId
    }

    suspend fun resubmitProductForCorrection(
        submissionId: String,
        updatedData: ProductSubmission,
        actorUser: User
    ) {
        if (!isUserActiveAndAllowed(actorUser.id)) {
            throw IllegalStateException("আপনার অ্যাকাউন্ট স্থগিত বা নিষ্ক্রিয় রয়েছে!")
        }

        val existing = db.productSubmissionDao().getSubmissionById(submissionId)
            ?: throw IllegalStateException("জমা দেওয়া প্রোডাক্ট পাওয়া যায়নি!")

        // Rule D.4: Salesman cannot modify another salesman's submitted product
        if (existing.salesmanId != actorUser.id) {
            throw IllegalStateException("অন্য সেলসম্যানের জমা দেওয়া প্রোডাক্ট আপনি সংশোধন করতে পারবেন না!")
        }

        // Rule A.4 & A.5: Can only resubmit if marked for correction
        if (existing.status != ProductSubmissionStatus.NEEDS_CORRECTION) {
            throw IllegalStateException("শুধুমাত্র সংশোধনের জন্য ফেরত দেওয়া প্রোডাক্ট পুনরায় জমা দেওয়া যাবে!")
        }

        if (updatedData.name.isBlank()) throw IllegalArgumentException("পণ্যের নাম খালি রাখা যাবে না!")
        if (updatedData.purchasePrice < 0) throw IllegalArgumentException("ক্রয়মূল্য ঋণাত্মক হতে পারে না!")
        if (updatedData.initialStock < 0) throw IllegalArgumentException("প্রাথমিক স্টক ঋণাত্মক হতে পারে না!")

        // Rule 1, 2, 7: For Salesman resubmission, Selling Price is strictly auto-calculated at Cost Price * 1.70
        val finalSellingPrice = if (actorUser.role == UserRole.SALESMAN) {
            calculateSellingPrice(updatedData.purchasePrice)
        } else {
            updatedData.sellingPrice
        }
        if (finalSellingPrice < 0) throw IllegalArgumentException("বিক্রয়মূল্য ঋণাত্মক হতে পারে না!")

        val finalCode = if (updatedData.productCode.isNotBlank()) {
            updatedData.productCode.trim()
        } else if (existing.productCode.isNotBlank()) {
            existing.productCode.trim()
        } else {
            "SKU-${System.currentTimeMillis().toString().takeLast(4)}"
        }

        val revised = existing.copy(
            name = updatedData.name.trim(),
            productCode = finalCode,
            category = updatedData.category.trim(),
            purchasePrice = updatedData.purchasePrice,
            sellingPrice = finalSellingPrice,
            initialStock = updatedData.initialStock,
            unit = updatedData.unit.trim().ifBlank { "পিস" },
            description = updatedData.description.trim(),
            status = ProductSubmissionStatus.PENDING,
            updatedAt = System.currentTimeMillis()
        )

        db.productSubmissionDao().updateSubmission(revised)

        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = actorUser.id,
                userName = actorUser.name,
                action = "সংশোধিত প্রোডাক্ট পুনঃজমা (Resubmitted)",
                details = "সেলসম্যান: ${actorUser.name}, পণ্য: ${revised.name}, কোড: ${revised.productCode}, পূর্ববর্তী নোট: ${existing.adminCorrectionNote}"
            )
        )
    }

    suspend fun approveProductSubmission(
        submissionId: String,
        finalData: ProductSubmission,
        adminUser: User
    ): Product {
        // Rule C.1, C.2: Real role verification
        if (adminUser.role != UserRole.ADMIN) {
            throw IllegalStateException("শুধুমাত্র এডমিন প্রোডাক্ট অনুমোদন করতে পারবেন!")
        }

        val existing = db.productSubmissionDao().getSubmissionById(submissionId)
            ?: throw IllegalStateException("জমা দেওয়া প্রোডাক্ট পাওয়া যায়নি!")

        // Rule D.3: Admin অনুমোদন দিলে প্রোডাক্ট একবারই মূল তালিকায় যুক্ত হবে
        if (existing.status == ProductSubmissionStatus.APPROVED) {
            throw IllegalStateException("এই প্রোডাক্টটি ইতিমধ্যে অনুমোদন করা হয়েছে!")
        }

        if (finalData.name.isBlank()) throw IllegalArgumentException("পণ্যের নাম খালি রাখা যাবে না!")
        if (finalData.purchasePrice < 0) throw IllegalArgumentException("ক্রয়মূল্য ঋণাত্মক হতে পারে না!")
        if (finalData.sellingPrice < 0) throw IllegalArgumentException("বিক্রয়মূল্য ঋণাত্মক হতে পারে না!")
        if (finalData.initialStock < 0) throw IllegalArgumentException("প্রাথমিক স্টক ঋণাত্মক হতে পারে না!")

        return db.withTransaction {
            val newProductId = if (!existing.approvedProductId.isNullOrBlank()) {
                existing.approvedProductId!!
            } else {
                "prod_${System.currentTimeMillis()}"
            }
            val newSku = if (finalData.productCode.isNotBlank()) {
                finalData.productCode.trim()
            } else if (existing.productCode.isNotBlank()) {
                existing.productCode.trim()
            } else {
                "SKU-${System.currentTimeMillis().toString().takeLast(4)}"
            }
            val approvedProduct = Product(
                id = newProductId,
                name = finalData.name.trim(),
                sku = newSku,
                barcode = "",
                category = finalData.category.trim().ifBlank { "সাধারণ" },
                purchasePrice = finalData.purchasePrice,
                sellingPrice = finalData.sellingPrice,
                currentStock = finalData.initialStock,
                alertStockLevel = 5.0,
                unit = finalData.unit.trim().ifBlank { "পিস" },
                isActive = true,
                updatedAt = System.currentTimeMillis(),
                imageUri = if (!finalData.imageUri.isNullOrBlank()) finalData.imageUri else existing.imageUri,
                imageSource = if (!finalData.imageUri.isNullOrBlank()) finalData.imageSource else existing.imageSource,
                imageSourceInfo = if (!finalData.imageUri.isNullOrBlank()) finalData.imageSourceInfo else existing.imageSourceInfo
            )

            // Insert or update approved product into live table (prevents duplicate items)
            val existingProduct = db.productDao().getProductById(newProductId)
            if (existingProduct != null) {
                db.productDao().updateProduct(approvedProduct)
            } else {
                db.productDao().insertProduct(approvedProduct)
            }

            // Insert initial stock log
            db.stockLogDao().insertStockLog(
                StockLog(
                    productId = approvedProduct.id,
                    productName = approvedProduct.name,
                    changeType = "অনুমোদিত নতুন পণ্য",
                    quantityChanged = approvedProduct.currentStock,
                    remainingStock = approvedProduct.currentStock,
                    reason = "সেলসম্যান '${existing.salesmanName}' এর জমা দেওয়া পণ্য এডমিন '${adminUser.name}' কর্তৃক অনুমোদিত (কোড: $newSku)"
                )
            )

            // Update submission record
            val updatedSubmission = existing.copy(
                name = finalData.name.trim(),
                productCode = newSku,
                category = finalData.category.trim(),
                purchasePrice = finalData.purchasePrice,
                sellingPrice = finalData.sellingPrice,
                initialStock = finalData.initialStock,
                unit = finalData.unit.trim().ifBlank { "পিস" },
                description = finalData.description.trim(),
                status = ProductSubmissionStatus.APPROVED,
                approvedProductId = approvedProduct.id,
                reviewedByAdminId = adminUser.id,
                reviewedByAdminName = adminUser.name,
                reviewedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            db.productSubmissionDao().updateSubmission(updatedSubmission)

            // Audit log
            db.auditLogDao().insertAuditLog(
                AuditLog(
                    userId = adminUser.id,
                    userName = adminUser.name,
                    action = "প্রোডাক্ট অনুমোদন (Approved)",
                    details = "এডমিন: ${adminUser.name}, অনুমোদিত পণ্য: ${approvedProduct.name} (ID: ${approvedProduct.id}), জমাদানকারী: ${existing.salesmanName}, ক্রয়: ৳${approvedProduct.purchasePrice}, বিক্রয়: ৳${approvedProduct.sellingPrice}, স্টক: ${approvedProduct.currentStock} ${approvedProduct.unit}"
                )
            )

            approvedProduct
        }
    }

    suspend fun returnProductForCorrection(
        submissionId: String,
        reason: String,
        adminUser: User
    ) {
        // Rule C.1, C.2: Real role verification
        if (adminUser.role != UserRole.ADMIN) {
            throw IllegalStateException("শুধুমাত্র এডমিন সংশোধনের অনুরোধ করতে পারবেন!")
        }

        // Rule B.5: ফেরত পাঠালে কারণ লিখতে হবে
        if (reason.isBlank()) {
            throw IllegalArgumentException("সংশোধনের কারণ লেখা বাধ্যতামূলক!")
        }

        val existing = db.productSubmissionDao().getSubmissionById(submissionId)
            ?: throw IllegalStateException("জমা দেওয়া প্রোডাক্ট পাওয়া যায়নি!")

        if (existing.status == ProductSubmissionStatus.APPROVED) {
            throw IllegalStateException("অনুমোদিত প্রোডাক্ট সংশোধনের জন্য ফেরত পাঠানো যাবে না!")
        }

        val updatedSubmission = existing.copy(
            status = ProductSubmissionStatus.NEEDS_CORRECTION,
            adminCorrectionNote = reason.trim(),
            reviewedByAdminId = adminUser.id,
            reviewedByAdminName = adminUser.name,
            reviewedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        db.productSubmissionDao().updateSubmission(updatedSubmission)

        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = adminUser.id,
                userName = adminUser.name,
                action = "প্রোডাক্ট সংশোধন অনুরোধ (Needs Correction)",
                details = "এডমিন: ${adminUser.name}, পণ্য: ${existing.name}, সেলসম্যান: ${existing.salesmanName}, কারণ: ${reason.trim()}"
            )
        )
    }

    suspend fun getSubmissionById(submissionId: String): ProductSubmission? {
        return db.productSubmissionDao().getSubmissionById(submissionId)
    }

    // --- Company & Supplier Due Management (কোম্পানির লেনদেন ও বকেয়া হিসাব) ---

    suspend fun saveCompany(
        company: Company,
        isNew: Boolean,
        actorUser: User
    ) {
        if (actorUser.role != UserRole.ADMIN) {
            throw IllegalStateException("শুধুমাত্র এডমিন কোম্পানি যোগ বা এডিট করতে পারেন!")
        }
        if (company.name.isBlank()) {
            throw IllegalArgumentException("কোম্পানির নাম আবশ্যক!")
        }
        if (company.phone.isBlank()) {
            throw IllegalArgumentException("মোবাইল নম্বর আবশ্যক!")
        }

        if (isNew) {
            db.companyDao().insertCompany(company)
            db.auditLogDao().insertAuditLog(
                AuditLog(
                    userId = actorUser.id,
                    userName = actorUser.name,
                    action = "নতুন কোম্পানি যোগ",
                    details = "কোম্পানি: ${company.name}, ফোন: ${company.phone}, ঠিকানা: ${company.address}"
                )
            )
        } else {
            db.companyDao().updateCompany(company.copy(updatedAt = System.currentTimeMillis()))
            db.auditLogDao().insertAuditLog(
                AuditLog(
                    userId = actorUser.id,
                    userName = actorUser.name,
                    action = "কোম্পানির তথ্য পরিবর্তন",
                    details = "কোম্পানি: ${company.name}, ফোন: ${company.phone}, বকেয়া: ৳${company.currentDue}"
                )
            )
        }
    }

    suspend fun deleteCompany(
        company: Company,
        actorUser: User,
        reason: String = "কোম্পানি ও সাপ্লায়ার তালিকা থেকে নিরাপদ অপসারণ"
    ): Result<String> {
        return softDeleteCompany(company, actorUser, reason)
    }

    suspend fun recordCompanyPurchase(
        purchase: CompanyPurchase,
        actorUser: User
    ): Result<String> {
        if (actorUser.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন কোম্পানি থেকে পণ্য ক্রয়ের হিসাব এন্ট্রি করতে পারেন!"))
        }
        if (purchase.companyId.isBlank()) {
            return Result.failure(IllegalArgumentException("কোম্পানি নির্বাচন করা আবশ্যক!"))
        }
        if (purchase.productName.isBlank()) {
            return Result.failure(IllegalArgumentException("পণ্যের নাম আবশ্যক!"))
        }
        if (purchase.quantity <= 0.0) {
            return Result.failure(IllegalArgumentException("ক্রয়ের পরিমাণ শূন্যের বেশি হতে হবে!"))
        }
        if (purchase.unitPurchasePrice < 0.0) {
            return Result.failure(IllegalArgumentException("ক্রয়মূল্য ঋণাত্মক হতে পারে না!"))
        }
        if (purchase.paidAmount < 0.0) {
            return Result.failure(IllegalArgumentException("পরিশোধিত টাকা ঋণাত্মক হতে পারে না!"))
        }

        val totalAmount = purchase.quantity * purchase.unitPurchasePrice
        val paidAmount = purchase.paidAmount
        val dueAmount = (totalAmount - paidAmount).coerceAtLeast(0.0)

        return try {
            db.withTransaction {
                val company = db.companyDao().getCompanyById(purchase.companyId)
                    ?: throw IllegalStateException("নির্বাচিত কোম্পানিটি পাওয়া যায়নি!")

                val preparedPurchase = purchase.copy(
                    companyName = company.name,
                    totalAmount = totalAmount,
                    paidAmount = paidAmount,
                    dueAmount = dueAmount
                )
                db.companyPurchaseDao().insertPurchase(preparedPurchase)

                // Safe Stock Update: Only if addedToStock is true and linked to an existing active product
                if (preparedPurchase.addedToStock && !preparedPurchase.productId.isNullOrBlank()) {
                    val product = db.productDao().getProductById(preparedPurchase.productId)
                    if (product != null) {
                        db.productDao().adjustStock(product.id, preparedPurchase.quantity)
                        val updatedProduct = db.productDao().getProductById(product.id)
                        db.stockLogDao().insertStockLog(
                            StockLog(
                                productId = product.id,
                                productName = product.name,
                                changeType = "কোম্পানি থেকে ক্রয়",
                                quantityChanged = preparedPurchase.quantity,
                                remainingStock = updatedProduct?.currentStock ?: (product.currentStock + preparedPurchase.quantity),
                                referenceId = preparedPurchase.challanNumber.ifBlank { preparedPurchase.id },
                                reason = "কোম্পানি '${company.name}' থেকে চালান #${preparedPurchase.challanNumber.ifBlank { "N/A" }}"
                            )
                        )
                    }
                }

                // Update Company Financials
                val newTotalPurchased = company.totalPurchased + totalAmount
                val newTotalPaid = company.totalPaid + paidAmount
                val net = newTotalPurchased - newTotalPaid
                val newCurrentDue = if (net >= 0.0) net else 0.0
                val newAdvance = if (net < 0.0) -net else 0.0

                val updatedCompany = company.copy(
                    totalPurchased = newTotalPurchased,
                    totalPaid = newTotalPaid,
                    currentDue = newCurrentDue,
                    advanceAmount = newAdvance,
                    updatedAt = System.currentTimeMillis()
                )
                db.companyDao().updateCompany(updatedCompany)

                db.auditLogDao().insertAuditLog(
                    AuditLog(
                        userId = actorUser.id,
                        userName = actorUser.name,
                        action = "কোম্পানি থেকে পণ্য ক্রয়",
                        details = "কোম্পানি: ${company.name}, পণ্য: ${preparedPurchase.productName}, পরিমাণ: ${preparedPurchase.quantity} ${preparedPurchase.unit}, মোট: ৳$totalAmount, পরিশোধ: ৳$paidAmount, বকেয়া: ৳$dueAmount"
                    )
                )

                Result.success(preparedPurchase.id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordCompanyPayment(
        payment: CompanyPayment,
        actorUser: User
    ): Result<String> {
        if (actorUser.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন কোম্পানিকে বকেয়া পরিশোধ এন্ট্রি করতে পারেন!"))
        }
        if (payment.companyId.isBlank()) {
            return Result.failure(IllegalArgumentException("কোম্পানি নির্বাচন করা আবশ্যক!"))
        }
        if (payment.amount <= 0.0) {
            return Result.failure(IllegalArgumentException("পরিশোধের পরিমাণ শূন্যের বেশি হতে হবে!"))
        }

        return try {
            db.withTransaction {
                val company = db.companyDao().getCompanyById(payment.companyId)
                    ?: throw IllegalStateException("নির্বাচিত কোম্পানিটি পাওয়া যায়নি!")

                val preparedPayment = payment.copy(companyName = company.name)
                db.companyPaymentDao().insertPayment(preparedPayment)

                // Update Company Financials
                val newTotalPaid = company.totalPaid + payment.amount
                val net = company.totalPurchased - newTotalPaid
                val newCurrentDue = if (net >= 0.0) net else 0.0
                val newAdvance = if (net < 0.0) -net else 0.0

                val updatedCompany = company.copy(
                    totalPaid = newTotalPaid,
                    currentDue = newCurrentDue,
                    advanceAmount = newAdvance,
                    updatedAt = System.currentTimeMillis()
                )
                db.companyDao().updateCompany(updatedCompany)

                db.auditLogDao().insertAuditLog(
                    AuditLog(
                        userId = actorUser.id,
                        userName = actorUser.name,
                        action = "কোম্পানিকে বকেয়া পরিশোধ",
                        details = "কোম্পানি: ${company.name}, পরিশোধিত টাকা: ৳${payment.amount}, মাধ্যম: ${payment.paymentMethod.name}, রেফারেন্স: ${payment.referenceNumber}, নতুন বকেয়া: ৳$newCurrentDue, অগ্রিম: ৳$newAdvance"
                    )
                )

                Result.success(preparedPayment.id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProductImage(productId: String, imageUri: String?, imageSource: String, imageSourceInfo: String) {
        val prod = db.productDao().getProductById(productId) ?: return
        db.productDao().updateProduct(
            prod.copy(
                imageUri = imageUri,
                imageSource = imageSource,
                imageSourceInfo = imageSourceInfo,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateProductSubmissionImage(submissionId: String, imageUri: String?, imageSource: String, imageSourceInfo: String) {
        val sub = db.productSubmissionDao().getSubmissionById(submissionId) ?: return
        db.productSubmissionDao().updateSubmission(
            sub.copy(
                imageUri = imageUri,
                imageSource = imageSource,
                imageSourceInfo = imageSourceInfo,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setProductImageMode(mode: String) {
        val current = db.companySettingsDao().getSettings() ?: CompanySettings()
        db.companySettingsDao().insertOrUpdate(current.copy(productImageMode = mode))
    }

    // ==========================================
    // Data Protection Center (ডেটা সুরক্ষা কেন্দ্র)
    // ==========================================

    suspend fun verifyAdminPin(adminId: String, pin: String): Boolean {
        val user = db.userDao().getUserById(adminId) ?: return false
        return user.role == UserRole.ADMIN && user.pinHash == hashPin(pin)
    }

    suspend fun softDeleteProduct(
        product: Product,
        admin: User,
        reason: String
    ): Result<String> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন পণ্য মুছতে পারেন!"))
        }
        val safeReason = reason.ifBlank { "নিয়মিত স্টক ও পণ্য তালিকা পরিচ্ছন্নকরণ" }
        // 1. Soft delete in products table
        db.productDao().updateProductActiveStatus(product.id, false)

        // 2. Add to Recycle Bin with 30-day recovery guarantee
        val rbId = "rb_prod_${UUID.randomUUID().toString().take(8)}"
        val isTest = product.isTestData || product.imageSource == "AI_GENERATED" || product.name.contains("[টেস্ট]", ignoreCase = true)
        val tag = if (product.imageSource == "AI_GENERATED") "GEMINI_AI" else if (product.isTestData) product.testTag.ifBlank { "MANUAL_TEST" } else "NONE"
        val item = RecycleBinItem(
            id = rbId,
            originalTable = "products",
            originalId = product.id,
            itemName = product.name,
            itemType = "পণ্য (Product)",
            itemDetails = "কোড: ${product.sku} | ক্যাটাগরি: ${product.category} | বর্তমান স্টক: ${product.currentStock} ${product.unit} | বিক্রয়মূল্য: ৳${product.sellingPrice}",
            snapshotJson = """{"id":"${product.id}","name":"${product.name}","sku":"${product.sku}","category":"${product.category}","sellingPrice":${product.sellingPrice},"purchasePrice":${product.purchasePrice},"stock":${product.currentStock},"unit":"${product.unit}"}""",
            deletedByAdminId = admin.id,
            deletedByAdminName = admin.name,
            deleteReason = safeReason,
            deletedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L),
            isTestData = isTest,
            testTag = tag
        )
        db.recycleBinDao().insertItem(item)

        // 3. Security Audit Log
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = "পণ্য রিসাইকেল বিনে স্থানান্তর",
                details = "পণ্য: '${product.name}' (কোড: ${product.sku}), কারণ: '$safeReason'। ৩০ দিনের মধ্যে পুনরুদ্ধারযোগ্য।"
            )
        )
        return Result.success(rbId)
    }

    suspend fun softDeleteCompany(
        company: Company,
        admin: User,
        reason: String
    ): Result<String> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন কোম্পানি মুছতে পারেন!"))
        }
        val safeReason = reason.ifBlank { "কোম্পানি ও সাপ্লায়ার তালিকা থেকে অপসারণ" }
        // 1. Soft delete in companies table
        db.companyDao().updateCompanyDeletedStatus(company.id, true)

        // 2. Add to Recycle Bin with 30-day recovery guarantee
        val rbId = "rb_comp_${UUID.randomUUID().toString().take(8)}"
        val isTest = company.isTestData || company.name.contains("[টেস্ট]", ignoreCase = true)
        val tag = if (company.isTestData) company.testTag.ifBlank { "MANUAL_TEST" } else "NONE"
        val item = RecycleBinItem(
            id = rbId,
            originalTable = "companies",
            originalId = company.id,
            itemName = company.name,
            itemType = "কোম্পানি / সাপ্লায়ার",
            itemDetails = "ফোন: ${company.phone} | বকেয়া: ৳${company.currentDue} | অগ্রিম: ৳${company.advanceAmount} | ঠিকানা: ${company.address}",
            snapshotJson = """{"id":"${company.id}","name":"${company.name}","phone":"${company.phone}","currentDue":${company.currentDue},"advance":${company.advanceAmount}}""",
            deletedByAdminId = admin.id,
            deletedByAdminName = admin.name,
            deleteReason = safeReason,
            deletedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L),
            isTestData = isTest,
            testTag = tag
        )
        db.recycleBinDao().insertItem(item)

        // 3. Security Audit Log
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = "কোম্পানি রিসাইকেল বিনে স্থানান্তর",
                details = "কোম্পানি: '${company.name}', কারণ: '$safeReason'। ৩০ দিনের মধ্যে পুনরুদ্ধারযোগ্য।"
            )
        )
        return Result.success(rbId)
    }

    suspend fun softDeleteCustomer(
        customer: Customer,
        admin: User,
        reason: String
    ): Result<String> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন কাস্টমার মুছতে পারেন!"))
        }
        val safeReason = reason.ifBlank { "কাস্টমার তালিকা পরিচ্ছন্নকরণ" }
        db.customerDao().updateCustomerDeletedStatus(customer.id, true)
        val rbId = "rb_cust_${UUID.randomUUID().toString().take(8)}"
        val isSampleSeed = customer.id in listOf("c_1", "c_2", "c_3") ||
                customer.phone in listOf("01911223344", "01833445566", "01722334455")
        val isTest = customer.isTestData ||
                customer.name.contains("[টেস্ট]", ignoreCase = true) ||
                customer.name.contains("test", ignoreCase = true) ||
                isSampleSeed
        val tag = if (customer.isTestData) {
            customer.testTag.ifBlank { "MANUAL_TEST" }
        } else if (isSampleSeed) {
            "SAMPLE_SEED"
        } else {
            "NONE"
        }
        val item = RecycleBinItem(
            id = rbId,
            originalTable = "customers",
            originalId = customer.id,
            itemName = customer.name,
            itemType = "কাস্টমার (Customer)",
            itemDetails = "ফোন: ${customer.phone} | বকেয়া: ৳${customer.currentDue} | মোট বিল: ৳${customer.totalBilled} | পরিশোধ: ৳${customer.totalPaid}",
            snapshotJson = """{"id":"${customer.id}","name":"${customer.name}","phone":"${customer.phone}","currentDue":${customer.currentDue},"totalBilled":${customer.totalBilled},"totalPaid":${customer.totalPaid},"openingDue":${customer.openingDue}}""",
            deletedByAdminId = admin.id,
            deletedByAdminName = admin.name,
            deleteReason = safeReason,
            deletedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L),
            isTestData = isTest,
            testTag = tag
        )
        db.recycleBinDao().insertItem(item)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = "কাস্টমার রিসাইকেল বিনে স্থানান্তর",
                details = "কাস্টমার: '${customer.name}' (ফোন: ${customer.phone}), বকেয়া: ৳${customer.currentDue}, কারণ: '$safeReason'। ৩০ দিনের মধ্যে পুনরুদ্ধারযোগ্য।"
            )
        )
        return Result.success(rbId)
    }

    suspend fun softDeleteMultipleCustomers(
        customers: List<Customer>,
        admin: User,
        reason: String
    ): Result<Int> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন কাস্টমার মুছতে পারেন!"))
        }
        val safeReason = reason.ifBlank { "পুরোনো টেস্ট কাস্টমার বকেয়া পরিচ্ছন্নকরণ" }
        return try {
            var count = 0
            var totalDueCleared = 0.0
            for (cust in customers) {
                val res = softDeleteCustomer(cust, admin, safeReason)
                if (res.isSuccess) {
                    count++
                    totalDueCleared += cust.currentDue
                }
            }
            db.auditLogDao().insertAuditLog(
                AuditLog(
                    userId = admin.id,
                    userName = admin.name,
                    action = "একত্রে একাধিক টেস্ট কাস্টমার রিসাইকেল বিনে স্থানান্তর",
                    details = "মোট $count টি টেস্ট/নমুনা কাস্টমার রিসাইকেল বিনে পাঠানো হয়েছে। ড্যাশবোর্ড থেকে মোট হ্রাসকৃত বকেয়া: ৳$totalDueCleared। কারণ: '$safeReason'"
                )
            )
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun softDeleteSale(
        sale: Sale,
        admin: User,
        reason: String
    ): Result<String> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন বিক্রি মুছতে পারেন!"))
        }
        val safeReason = reason.ifBlank { "পরীক্ষামূলক বিক্রি অপসারণ" }
        return try {
            val rbId = "rb_sale_${UUID.randomUUID().toString().take(8)}"
            db.withTransaction {
                // Return stock of items
                val items = db.saleDao().getSaleItems(sale.invoiceNumber)
                for (it in items) {
                    db.productDao().adjustStock(it.productId, it.quantity)
                    val prod = db.productDao().getProductById(it.productId)
                    db.stockLogDao().insertStockLog(
                        StockLog(
                            productId = it.productId,
                            productName = it.productName,
                            changeType = "ফেরত/সমন্বয়",
                            quantityChanged = it.quantity,
                            remainingStock = prod?.currentStock ?: 0.0,
                            referenceId = sale.invoiceNumber,
                            reason = "ইনভয়েস #${sale.invoiceNumber} রিসাইকেল বিনে পাঠানোর কারণে স্টক ফেরত"
                        )
                    )
                }

                // Adjust customer balances
                db.customerDao().updateCustomerBalances(
                    id = sale.customerId,
                    billedDelta = -sale.totalAmount,
                    paidDelta = -sale.paidAmount,
                    dueDelta = -sale.dueAmount
                )

                // Soft-delete any linked receipts for this invoice
                val linkedReceipts = db.paymentReceiptDao().getReceiptsByInvoice(sale.invoiceNumber)
                for (r in linkedReceipts) {
                    db.paymentReceiptDao().updateReceiptDeletedStatus(r.receiptNumber, true)
                }

                // Mark sale as soft-deleted
                db.saleDao().updateSaleDeletedStatus(sale.invoiceNumber, true)

                val isTest = sale.isTestData || sale.customerName.contains("[টেস্ট]", ignoreCase = true) || sale.invoiceNumber.contains("TEST", ignoreCase = true)
                val tag = if (sale.isTestData) sale.testTag.ifBlank { "MANUAL_TEST" } else "NONE"
                val rbItem = RecycleBinItem(
                    id = rbId,
                    originalTable = "sales",
                    originalId = sale.invoiceNumber,
                    itemName = "ইনভয়েস #${sale.invoiceNumber}",
                    itemType = "বিক্রি (Sale)",
                    itemDetails = "কাস্টমার: ${sale.customerName} | মোট: ৳${sale.totalAmount} | জমা: ৳${sale.paidAmount} | বাকি: ৳${sale.dueAmount}",
                    snapshotJson = """{"invoiceNumber":"${sale.invoiceNumber}","customerId":"${sale.customerId}","customerName":"${sale.customerName}","totalAmount":${sale.totalAmount},"paidAmount":${sale.paidAmount},"dueAmount":${sale.dueAmount}}""",
                    deletedByAdminId = admin.id,
                    deletedByAdminName = admin.name,
                    deleteReason = safeReason,
                    deletedAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L),
                    isTestData = isTest,
                    testTag = tag
                )
                db.recycleBinDao().insertItem(rbItem)

                db.auditLogDao().insertAuditLog(
                    AuditLog(
                        userId = admin.id,
                        userName = admin.name,
                        action = "বিক্রি রিসাইকেল বিনে স্থানান্তর",
                        details = "ইনভয়েস: #${sale.invoiceNumber}, কাস্টমার: ${sale.customerName}, মোট: ৳${sale.totalAmount}, স্টক ও ব্যালেন্স স্বয়ংক্রিয় সমন্বয় সম্পন্ন।"
                    )
                )
            }
            Result.success(rbId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun softDeletePaymentReceipt(
        receipt: PaymentReceipt,
        admin: User,
        reason: String
    ): Result<String> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন পেমেন্ট রসিদ মুছতে পারেন!"))
        }
        val safeReason = reason.ifBlank { "ভুল বা টেস্ট রসিদ অপসারণ" }
        return try {
            val rbId = "rb_pmt_${UUID.randomUUID().toString().take(8)}"
            db.withTransaction {
                // Reverse customer balances
                db.customerDao().updateCustomerBalances(
                    id = receipt.customerId,
                    billedDelta = 0.0,
                    paidDelta = -receipt.amount,
                    dueDelta = receipt.amount
                )

                db.paymentReceiptDao().updateReceiptDeletedStatus(receipt.receiptNumber, true)

                val isTest = receipt.isTestData || receipt.customerName.contains("[টেস্ট]", ignoreCase = true) || receipt.receiptNumber.contains("TEST", ignoreCase = true)
                val tag = if (receipt.isTestData) receipt.testTag.ifBlank { "MANUAL_TEST" } else "NONE"
                val rbItem = RecycleBinItem(
                    id = rbId,
                    originalTable = "payments",
                    originalId = receipt.receiptNumber,
                    itemName = "রসিদ #${receipt.receiptNumber}",
                    itemType = "পেমেন্ট / রসিদ",
                    itemDetails = "কাস্টমার: ${receipt.customerName} | পরিমাণ: ৳${receipt.amount} | মাধ্যম: ${receipt.paymentMethod}",
                    snapshotJson = """{"receiptNumber":"${receipt.receiptNumber}","customerId":"${receipt.customerId}","customerName":"${receipt.customerName}","amount":${receipt.amount}}""",
                    deletedByAdminId = admin.id,
                    deletedByAdminName = admin.name,
                    deleteReason = safeReason,
                    deletedAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L),
                    isTestData = isTest,
                    testTag = tag
                )
                db.recycleBinDao().insertItem(rbItem)

                db.auditLogDao().insertAuditLog(
                    AuditLog(
                        userId = admin.id,
                        userName = admin.name,
                        action = "পেমেন্ট রসিদ রিসাইকেল বিনে স্থানান্তর",
                        details = "রসিদ: #${receipt.receiptNumber}, কাস্টমার: ${receipt.customerName}, পরিমাণ: ৳${receipt.amount}, কাস্টমার বকেয়া স্বয়ংক্রিয় সমন্বয় সম্পন্ন।"
                    )
                )
            }
            Result.success(rbId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun softDeleteExpense(
        expense: Expense,
        admin: User,
        reason: String
    ): Result<String> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন খরচ মুছতে পারেন!"))
        }
        val safeReason = reason.ifBlank { "টেস্ট বা অননুমোদিত খরচ অপসারণ" }
        val rbId = "rb_exp_${UUID.randomUUID().toString().take(8)}"
        db.expenseDao().updateExpenseDeletedStatus(expense.id, true)
        val isTest = expense.isTestData || expense.description.contains("[টেস্ট]", ignoreCase = true)
        val tag = if (expense.isTestData) expense.testTag.ifBlank { "MANUAL_TEST" } else "NONE"
        val rbItem = RecycleBinItem(
            id = rbId,
            originalTable = "expenses",
            originalId = expense.id,
            itemName = expense.description,
            itemType = "খরচ (Expense)",
            itemDetails = "পরিমাণ: ৳${expense.amount} | ক্যাটাগরি: ${expense.category} | দাখিলকারী: ${expense.salesmanName}",
            snapshotJson = """{"id":"${expense.id}","amount":${expense.amount},"category":"${expense.category}","salesmanName":"${expense.salesmanName}"}""",
            deletedByAdminId = admin.id,
            deletedByAdminName = admin.name,
            deleteReason = safeReason,
            deletedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000L),
            isTestData = isTest,
            testTag = tag
        )
        db.recycleBinDao().insertItem(rbItem)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = "খরচ রিসাইকেল বিনে স্থানান্তর",
                details = "খরচ: '${expense.description}', পরিমাণ: ৳${expense.amount}, কারণ: '$safeReason'। ৩০ দিনের মধ্যে পুনরুদ্ধারযোগ্য।"
            )
        )
        return Result.success(rbId)
    }

    suspend fun restoreRecycleBinItem(
        item: RecycleBinItem,
        admin: User
    ): Result<Unit> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন রেকর্ড পুনরুদ্ধার করতে পারেন!"))
        }
        return try {
            db.withTransaction {
                when (item.originalTable) {
                    "products" -> {
                        db.productDao().updateProductActiveStatus(item.originalId, true)
                    }
                    "companies" -> {
                        db.companyDao().updateCompanyDeletedStatus(item.originalId, false)
                    }
                    "customers" -> {
                        db.customerDao().updateCustomerDeletedStatus(item.originalId, false)
                    }
                    "sales" -> {
                        val sale = db.saleDao().getSaleByInvoice(item.originalId)
                        if (sale != null) {
                            // Re-deduct product stock
                            val items = db.saleDao().getSaleItems(sale.invoiceNumber)
                            for (it in items) {
                                db.productDao().adjustStock(it.productId, -it.quantity)
                            }
                            // Re-apply customer balance
                            db.customerDao().updateCustomerBalances(
                                id = sale.customerId,
                                billedDelta = sale.totalAmount,
                                paidDelta = sale.paidAmount,
                                dueDelta = sale.dueAmount
                            )
                            // Restore linked receipts
                            val linkedReceipts = db.paymentReceiptDao().getReceiptsByInvoice(sale.invoiceNumber)
                            for (r in linkedReceipts) {
                                db.paymentReceiptDao().updateReceiptDeletedStatus(r.receiptNumber, false)
                            }
                            db.saleDao().updateSaleDeletedStatus(sale.invoiceNumber, false)
                        }
                    }
                    "payments" -> {
                        val receipt = db.paymentReceiptDao().getReceiptByNumber(item.originalId)
                        if (receipt != null) {
                            db.customerDao().updateCustomerBalances(
                                id = receipt.customerId,
                                billedDelta = 0.0,
                                paidDelta = receipt.amount,
                                dueDelta = -receipt.amount
                            )
                            db.paymentReceiptDao().updateReceiptDeletedStatus(receipt.receiptNumber, false)
                        }
                    }
                    "expenses" -> {
                        db.expenseDao().updateExpenseDeletedStatus(item.originalId, false)
                    }
                }
                db.recycleBinDao().markRestored(item.id, admin.id)
                db.auditLogDao().insertAuditLog(
                    AuditLog(
                        userId = admin.id,
                        userName = admin.name,
                        action = "রেকর্ড পুনরুদ্ধার (Restore)",
                        details = "${item.itemType} '${item.itemName}' রিসাইকেল বিন থেকে সফলভাবে পুনরুদ্ধার করা হয়েছে।"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun permanentlyDeleteRecycleBinItem(
        item: RecycleBinItem,
        admin: User,
        adminPin: String
    ): Result<Unit> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন স্থায়ীভাবে রেকর্ড মুছতে পারেন!"))
        }
        if (!verifyAdminPin(admin.id, adminPin)) {
            return Result.failure(IllegalArgumentException("এডমিন পিন সঠিক নয়! নিরাপত্তা স্বার্থে মুছে ফেলা বাতিল করা হলো।"))
        }
        return try {
            db.withTransaction {
                when (item.originalTable) {
                    "products" -> {
                        db.productDao().deleteProductPermanently(item.originalId)
                    }
                    "companies" -> {
                        db.companyDao().deleteCompanyPermanently(item.originalId)
                    }
                    "customers" -> {
                        // Cascading cleanup of customer's sales, sale_items and payments to prevent orphans
                        db.saleDao().deleteSaleItemsByCustomer(item.originalId)
                        db.saleDao().deleteSalesByCustomer(item.originalId)
                        db.paymentReceiptDao().deleteReceiptsByCustomer(item.originalId)
                        db.customerDao().deleteCustomerPermanently(item.originalId)
                    }
                    "sales" -> {
                        db.saleDao().deleteSaleItemsPermanently(item.originalId)
                        val linkedReceipts = db.paymentReceiptDao().getReceiptsByInvoice(item.originalId)
                        for (r in linkedReceipts) {
                            db.paymentReceiptDao().deleteReceiptPermanently(r.receiptNumber)
                        }
                        db.saleDao().deleteSalePermanently(item.originalId)
                    }
                    "payments" -> {
                        db.paymentReceiptDao().deleteReceiptPermanently(item.originalId)
                    }
                    "expenses" -> {
                        db.expenseDao().deleteExpensePermanently(item.originalId)
                    }
                }
                // Mark as PERMANENT_TOMBSTONE so it leaves the active Recycle Bin UI
                // but persists as an offline tombstone marker to block sync resurrection
                db.recycleBinDao().markPermanentlyDeleted(item.originalTable, item.originalId)
                db.auditLogDao().insertAuditLog(
                    AuditLog(
                        userId = admin.id,
                        userName = admin.name,
                        action = "স্থায়ীভাবে মুছে ফেলা (Permanent Delete)",
                        details = "${item.itemType} '${item.itemName}' স্থায়ীভাবে ডেটাবেস থেকে মুছে ফেলা হয়েছে।"
                    )
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setCustomerOpeningBalance(
        customerId: String,
        openingDue: Double,
        admin: User,
        adminPin: String
    ): Result<Unit> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন প্রারম্ভিক হিসাব নির্ধারণ করতে পারেন!"))
        }
        if (!verifyAdminPin(admin.id, adminPin)) {
            return Result.failure(IllegalArgumentException("এডমিন পিন সঠিক নয়!"))
        }
        val customer = db.customerDao().getCustomerById(customerId)
            ?: return Result.failure(IllegalStateException("কাস্টমার পাওয়া যায়নি!"))
        val cleanDue = openingDue.coerceAtLeast(0.0)
        db.customerDao().updateCustomerOpeningDue(customerId, cleanDue)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = "কাস্টমার ওপেনিং বকেয়া নির্ধারণ",
                details = "কাস্টমার: '${customer.name}', পূর্ববর্তী বকেয়া: ৳${customer.currentDue}, নতুন ওপেনিং বকেয়া: ৳$cleanDue"
            )
        )
        return Result.success(Unit)
    }

    suspend fun setProductOpeningStock(
        productId: String,
        openingStock: Double,
        admin: User,
        adminPin: String
    ): Result<Unit> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন প্রারম্ভিক স্টক নির্ধারণ করতে পারেন!"))
        }
        if (!verifyAdminPin(admin.id, adminPin)) {
            return Result.failure(IllegalArgumentException("এডমিন পিন সঠিক নয়!"))
        }
        val prod = db.productDao().getProductById(productId)
            ?: return Result.failure(IllegalStateException("পণ্য পাওয়া যায়নি!"))
        val cleanStock = openingStock.coerceAtLeast(0.0)
        val diff = cleanStock - prod.currentStock
        db.productDao().updateProductStockDirect(productId, cleanStock)
        db.stockLogDao().insertStockLog(
            StockLog(
                productId = productId,
                productName = prod.name,
                changeType = "ওপেনিং স্টক নির্ধারণ",
                quantityChanged = diff,
                remainingStock = cleanStock,
                reason = "বাস্তব ব্যবসা শুরু উপলক্ষে প্রারম্ভিক স্টক সেটআপ"
            )
        )
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = "পণ্য প্রারম্ভিক স্টক নির্ধারণ",
                details = "পণ্য: '${prod.name}', পূর্ববর্তী স্টক: ${prod.currentStock} ${prod.unit}, নতুন ওপেনিং স্টক: $cleanStock ${prod.unit}"
            )
        )
        return Result.success(Unit)
    }

    suspend fun setProductTestDataStatus(productId: String, isTest: Boolean, tag: String, admin: User) {
        if (admin.role != UserRole.ADMIN) return
        db.productDao().updateTestDataStatus(productId, isTest, tag)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = if (isTest) "পণ্য টেস্ট ডেটা হিসেবে চিহ্নিতকরণ" else "পণ্য টেস্ট ডেটা ট্যাগ অপসারণ",
                details = "পণ্য ID: $productId, স্ট্যাটাস: ${if (isTest) "টেস্টিং ($tag)" else "বাস্তব ব্যবসায়িক ডেটা"}"
            )
        )
    }

    suspend fun setCompanyTestDataStatus(companyId: String, isTest: Boolean, tag: String, admin: User) {
        if (admin.role != UserRole.ADMIN) return
        db.companyDao().updateCompanyTestDataStatus(companyId, isTest, tag)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = if (isTest) "কোম্পানি টেস্ট ডেটা হিসেবে চিহ্নিতকরণ" else "কোম্পানি টেস্ট ট্যাগ অপসারণ",
                details = "কোম্পানি ID: $companyId, স্ট্যাটাস: ${if (isTest) "টেস্টিং ($tag)" else "বাস্তব ব্যবসায়িক ডেটা"}"
            )
        )
    }

    suspend fun setCustomerTestDataStatus(customerId: String, isTest: Boolean, tag: String, admin: User) {
        if (admin.role != UserRole.ADMIN) return
        db.customerDao().updateCustomerTestDataStatus(customerId, isTest, tag)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = if (isTest) "কাস্টমার টেস্ট ডেটা হিসেবে চিহ্নিতকরণ" else "কাস্টমার টেস্ট ট্যাগ অপসারণ",
                details = "কাস্টমার ID: $customerId, স্ট্যাটাস: ${if (isTest) "টেস্টিং ($tag)" else "বাস্তব গ্রাহক"}"
            )
        )
    }

    suspend fun setSaleTestDataStatus(invoiceNumber: String, isTest: Boolean, tag: String, admin: User) {
        if (admin.role != UserRole.ADMIN) return
        db.saleDao().updateSaleTestDataStatus(invoiceNumber, isTest, tag)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = if (isTest) "বিক্রি টেস্ট ডেটা হিসেবে চিহ্নিতকরণ" else "বিক্রি টেস্ট ট্যাগ অপসারণ",
                details = "ইনভয়েস: $invoiceNumber, স্ট্যাটাস: ${if (isTest) "টেস্টিং ($tag)" else "বাস্তব বিক্রি"}"
            )
        )
    }

    suspend fun setReceiptTestDataStatus(receiptNumber: String, isTest: Boolean, tag: String, admin: User) {
        if (admin.role != UserRole.ADMIN) return
        db.paymentReceiptDao().updateReceiptTestDataStatus(receiptNumber, isTest, tag)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = if (isTest) "রসিদ টেস্ট ডেটা হিসেবে চিহ্নিতকরণ" else "রসিদ টেস্ট ট্যাগ অপসারণ",
                details = "রসিদ নং: $receiptNumber, স্ট্যাটাস: ${if (isTest) "টেস্টিং ($tag)" else "বাস্তব আদায়"}"
            )
        )
    }

    suspend fun setExpenseTestDataStatus(expenseId: String, isTest: Boolean, tag: String, admin: User) {
        if (admin.role != UserRole.ADMIN) return
        db.expenseDao().updateExpenseTestDataStatus(expenseId, isTest, tag)
        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = if (isTest) "খরচ টেস্ট ডেটা হিসেবে চিহ্নিতকরণ" else "খরচ টেস্ট ট্যাগ অপসারণ",
                details = "খরচ ID: $expenseId, স্ট্যাটাস: ${if (isTest) "টেস্টিং ($tag)" else "বাস্তব খরচ"}"
            )
        )
    }

    suspend fun createSampleTestData(admin: User): Result<String> {
        if (admin.role != UserRole.ADMIN) {
            return Result.failure(IllegalStateException("শুধুমাত্র এডমিন টেস্ট ডেটা তৈরি করতে পারেন!"))
        }
        val rand = (100..999).random()
        val sampleProd = Product(
            id = "test_prod_${System.currentTimeMillis()}",
            name = "[টেস্ট - Gemini AI] স্মার্ট টি-শার্ট $rand",
            sku = "TEST-TSHIRT-$rand",
            category = "পোশাক ও ফ্যাশন",
            purchasePrice = 350.0,
            sellingPrice = 520.0,
            currentStock = 15.0,
            unit = "পিস",
            isActive = true,
            imageSource = "AI_GENERATED",
            imageSourceInfo = "Google Gemini Test Generation",
            isTestData = true,
            testTag = "GEMINI_AI"
        )
        val sampleComp = Company(
            id = "test_comp_${System.currentTimeMillis()}",
            name = "[টেস্ট] ডেমো টেক্সটাইল ও ফ্যাশন $rand",
            phone = "01999000$rand",
            address = "মিরপুর, ঢাকা (টেস্টিং ঠিকানা)",
            note = "সিস্টেম পরীক্ষা ও হিসাব যাচাইয়ের জন্য তৈরি কৃত্রিম টেস্ট কোম্পানি",
            totalPurchased = 25000.0,
            totalPaid = 15000.0,
            currentDue = 10000.0,
            isTestData = true,
            testTag = "DEMO"
        )
        val sampleCust = Customer(
            id = "test_cust_${System.currentTimeMillis()}",
            name = "[টেস্ট কাস্টমার] করিম স্টোর $rand",
            phone = "01888111$rand",
            address = "ধানমন্ডি, ঢাকা (টেস্ট গ্রাহক)",
            totalBilled = 8500.0,
            totalPaid = 3500.0,
            currentDue = 5000.0,
            isTestData = true,
            testTag = "TEST_CUSTOMER"
        )
        val sampleSale = Sale(
            invoiceNumber = "INV-TEST-$rand",
            customerId = sampleCust.id,
            customerName = sampleCust.name,
            customerPhone = sampleCust.phone,
            salesmanId = admin.id,
            salesmanName = admin.name,
            subtotal = 8500.0,
            discount = 0.0,
            totalAmount = 8500.0,
            paidAmount = 3500.0,
            dueAmount = 5000.0,
            paymentMethod = PaymentMethod.CASH,
            note = "সিস্টেম টেস্ট বিক্রয় ইনভয়েস",
            isTestData = true,
            testTag = "TEST_SALE"
        )
        val sampleReceipt = PaymentReceipt(
            receiptNumber = "RCT-TEST-$rand",
            invoiceNumber = sampleSale.invoiceNumber,
            customerId = sampleCust.id,
            customerName = sampleCust.name,
            customerPhone = sampleCust.phone,
            salesmanId = admin.id,
            salesmanName = admin.name,
            amount = 3500.0,
            previousDue = 8500.0,
            currentDue = 5000.0,
            paymentMethod = PaymentMethod.CASH,
            note = "টেস্ট পেমেন্ট কালেকশন",
            isTestData = true,
            testTag = "TEST_PAYMENT"
        )
        val sampleExpense = Expense(
            id = "test_exp_${System.currentTimeMillis()}",
            salesmanId = admin.id,
            salesmanName = admin.name,
            category = ExpenseCategory.DELIVERY,
            source = ExpenseSource.COMPANY_CASH,
            amount = 350.0,
            description = "[টেস্ট খরচ] ডেমো ডেলিভারি পরিবহন ভাড়া $rand",
            status = ExpenseStatus.APPROVED,
            isTestData = true,
            testTag = "TEST_EXPENSE"
        )

        db.productDao().insertProduct(sampleProd)
        db.companyDao().insertCompany(sampleComp)
        db.customerDao().insertCustomer(sampleCust)
        db.saleDao().insertSale(sampleSale)
        db.paymentReceiptDao().insertReceipt(sampleReceipt)
        db.expenseDao().insertExpense(sampleExpense)

        db.auditLogDao().insertAuditLog(
            AuditLog(
                userId = admin.id,
                userName = admin.name,
                action = "পরীক্ষামূলক টেস্ট ডেটা সেট তৈরি",
                details = "পণ্য: '${sampleProd.name}', কাস্টমার: '${sampleCust.name}', ইনভয়েস: #${sampleSale.invoiceNumber}, কোম্পানি: '${sampleComp.name}'"
            )
        )
        return Result.success("টেস্ট পণ্য, কোম্পানি, কাস্টমার, বিক্রি, পেমেন্ট ও খরচের ডেটা সফলভাবে তৈরি হয়েছে!")
    }

    /**
     * Safely analyzes all active customers (isDeleted = 0) that have positive dues
     * or are marked as test data. Accurately determines whether a due record originates
     * from confirmed test data, initial template seed data, or requires manual review.
     */
    suspend fun detectTestDueCustomers(): List<DetectedTestDueCustomer> {
        val allActiveCustomers = db.customerDao().getAllCustomers().firstOrNull() ?: emptyList()
        val dueCustomers = allActiveCustomers.filter { it.currentDue > 0.0 }
        val result = mutableListOf<DetectedTestDueCustomer>()

        for (cust in dueCustomers) {
            val sales = db.saleDao().getSalesByCustomer(cust.id)
            val receipts = db.paymentReceiptDao().getReceiptsByCustomer(cust.id)
            val salesCount = sales.size
            val receiptsCount = receipts.size
            val totalSalesAmt = sales.sumOf { it.totalAmount }
            val totalPaidAmt = receipts.sumOf { it.amount }

            val reasons = mutableListOf<String>()

            // 1. Check for initial template demo seed data (c_1, c_2, c_3)
            val isSeedSample = cust.testTag == "SAMPLE_SEED" ||
                    cust.id in listOf("c_1", "c_2", "c_3") ||
                    cust.phone in listOf("01911223344", "01833445566", "01722334455") ||
                    (cust.name in listOf("মেসার্স হাজী ব্রাদার্স", "জননী স্টোর (কালাম ভাই)", "ভাই ভাই এন্টারপ্রাইজ") && sales.isEmpty())

            // 2. Check for confirmed test indicators
            val isExplicitTest = !isSeedSample && (
                    cust.isTestData ||
                    (cust.testTag.isNotBlank() && cust.testTag != "NONE") ||
                    cust.id.startsWith("test_") ||
                    cust.id.startsWith("demo_") ||
                    cust.name.contains("[টেস্ট]", ignoreCase = true) ||
                    cust.name.contains("[test]", ignoreCase = true) ||
                    cust.name.contains("(টেস্ট)", ignoreCase = true) ||
                    cust.name.contains("(test)", ignoreCase = true) ||
                    cust.name.contains("টেস্ট কাস্টমার", ignoreCase = true) ||
                    cust.name.contains("test customer", ignoreCase = true) ||
                    (sales.isNotEmpty() && sales.all { it.isTestData || it.invoiceNumber.startsWith("INV-TEST") })
            )

            val category = when {
                isSeedSample -> {
                    reasons.add("অ্যাপ ইনস্টলেশনের প্রারম্ভিক ডেমো/নমুনা ডেটাসেট (Template Seed Data)")
                    if (cust.id in listOf("c_1", "c_2", "c_3")) reasons.add("ডিফল্ট ডেমো আইডি: ${cust.id}")
                    if (sales.isEmpty()) reasons.add("কোনো বাস্তব বিক্রয় ইনভয়েস নেই (শুধুমাত্র প্রারম্ভিক ডেমো ব্যালেন্স)")
                    DueCustomerCategory.SAMPLE_SEED_DATA
                }
                isExplicitTest -> {
                    if (cust.isTestData) reasons.add("সিস্টেম টেস্ট ফ্ল্যাগ সক্রিয় (isTestData=true)")
                    if (cust.testTag.isNotBlank() && cust.testTag != "NONE") reasons.add("টেস্ট ট্যাগ: ${cust.testTag}")
                    if (cust.id.startsWith("test_") || cust.id.startsWith("demo_")) reasons.add("টেস্ট কাস্টমার আইডি: ${cust.id}")
                    if (cust.name.contains("টেস্ট", ignoreCase = true) || cust.name.contains("test", ignoreCase = true)) reasons.add("কাস্টমারের নামে টেস্ট লেবেল যুক্ত")
                    if (sales.isNotEmpty() && sales.all { it.isTestData || it.invoiceNumber.startsWith("INV-TEST") }) reasons.add("সকল বিক্রয় ইনভয়েস টেস্ট চালান")
                    DueCustomerCategory.CONFIRMED_TEST
                }
                else -> {
                    reasons.add("সতর্কতা: কোনো স্পষ্ট টেস্ট চিহ্ন পাওয়া যায়নি")
                    if (sales.isNotEmpty()) reasons.add("সক্রিয় বিক্রয় ইনভয়েস বিদ্যমান ($salesCount টি)")
                    DueCustomerCategory.NEEDS_MANUAL_REVIEW
                }
            }

            result.add(
                DetectedTestDueCustomer(
                    customer = cust,
                    category = category,
                    reasons = reasons,
                    salesCount = salesCount,
                    receiptsCount = receiptsCount,
                    totalSalesAmount = totalSalesAmt,
                    totalPaidAmount = totalPaidAmt,
                    hasTransactions = salesCount > 0 || receiptsCount > 0
                )
            )
        }

        return result.sortedWith(
            compareBy<DetectedTestDueCustomer> {
                when (it.category) {
                    DueCustomerCategory.CONFIRMED_TEST -> 0
                    DueCustomerCategory.SAMPLE_SEED_DATA -> 1
                    DueCustomerCategory.NEEDS_MANUAL_REVIEW -> 2
                }
            }.thenByDescending { it.customer.currentDue }
        )
    }
}

