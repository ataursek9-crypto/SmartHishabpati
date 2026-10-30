package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY role ASC, name ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE isActive = 1 AND accountStatus = 'ACTIVE'")
    fun getAllActiveUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE isActive = 1 AND accountStatus = 'ACTIVE'")
    suspend fun getAllActiveUsersList(): List<User>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): User?

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE users SET accountStatus = :status, isActive = :isActive, statusReason = :reason, statusUpdatedAt = :timestamp WHERE id = :userId")
    suspend fun updateUserStatus(userId: String, status: UserAccountStatus, isActive: Boolean, reason: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT * FROM users WHERE shopId = :shopId ORDER BY role ASC, name ASC")
    fun getUsersByShop(shopId: String): Flow<List<User>>

    @Query("SELECT * FROM users WHERE shopId = :shopId AND isActive = 1 AND accountStatus = 'ACTIVE' ORDER BY name ASC")
    fun getActiveUsersByShop(shopId: String): Flow<List<User>>

    @Query("SELECT * FROM users WHERE shopId = :shopId AND isActive = 1 AND accountStatus = 'ACTIVE'")
    suspend fun getActiveUsersByShopList(shopId: String): List<User>

    @Query("SELECT * FROM users WHERE shopId = :shopId AND phone = :phone LIMIT 1")
    suspend fun getUserByShopAndPhone(shopId: String, phone: String): User?

    @Query("SELECT * FROM users WHERE shopId = :shopId AND role = 'ADMIN' LIMIT 1")
    suspend fun getAdminByShop(shopId: String): User?

    @Query("SELECT COUNT(*) FROM users WHERE shopId = :shopId")
    suspend fun getUserCountByShop(shopId: String): Int

    @Query("UPDATE users SET pinHash = :newPinHash WHERE id = :userId")
    suspend fun resetStaffPin(userId: String, newPinHash: String)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getAllProductsList(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): Product?

    @Query("SELECT * FROM products WHERE currentStock <= alertStockLevel AND isActive = 1")
    fun getLowStockProducts(): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET currentStock = currentStock + :delta, updatedAt = :time WHERE id = :id")
    suspend fun adjustStock(id: String, delta: Double, time: Long = System.currentTimeMillis())

    @Query("SELECT * FROM products WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findProductByName(name: String): Product?

    @Query("SELECT * FROM products WHERE LOWER(TRIM(sku)) = LOWER(TRIM(:sku)) LIMIT 1")
    suspend fun findProductBySku(sku: String): Product?

    @Query("SELECT * FROM products WHERE isTestData = 1 ORDER BY name ASC")
    fun getTestProducts(): Flow<List<Product>>

    @Query("UPDATE products SET isTestData = :isTest, testTag = :tag WHERE id = :id")
    suspend fun updateTestDataStatus(id: String, isTest: Boolean, tag: String)

    @Query("UPDATE products SET isActive = :isActive WHERE id = :id")
    suspend fun updateProductActiveStatus(id: String, isActive: Boolean)

    @Query("UPDATE products SET currentStock = :stock, updatedAt = :time WHERE id = :id")
    suspend fun updateProductStockDirect(id: String, stock: Double, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductPermanently(id: String)

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1 AND isTestData = 0")
    suspend fun getRealProductsCount(): Int

    @Query("SELECT * FROM products")
    suspend fun getSyncProducts(): List<Product>
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE isDeleted = 0 ORDER BY isPinned DESC, name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE isDeleted = 0 AND isTestData = 1 ORDER BY name ASC")
    fun getTestCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: String): Customer?

    @Query("SELECT * FROM customers WHERE phone = :phone AND isDeleted = 0 LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Query("UPDATE customers SET totalBilled = totalBilled + :billedDelta, totalPaid = totalPaid + :paidDelta, currentDue = currentDue + :dueDelta, updatedAt = :time WHERE id = :id")
    suspend fun updateCustomerBalances(id: String, billedDelta: Double, paidDelta: Double, dueDelta: Double, time: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET isPinned = :isPinned WHERE id = :id")
    suspend fun updatePinStatus(id: String, isPinned: Boolean)

    @Query("UPDATE customers SET isDeleted = :isDeleted, updatedAt = :time WHERE id = :id")
    suspend fun updateCustomerDeletedStatus(id: String, isDeleted: Boolean, time: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET isTestData = :isTest, testTag = :tag, updatedAt = :time WHERE id = :id")
    suspend fun updateCustomerTestDataStatus(id: String, isTest: Boolean, tag: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE customers SET openingDue = :openingDue, currentDue = :openingDue + totalBilled - totalPaid, updatedAt = :time WHERE id = :id")
    suspend fun updateCustomerOpeningDue(id: String, openingDue: Double, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerPermanently(id: String)

    @Query("SELECT COUNT(*) FROM customers WHERE isDeleted = 0 AND isTestData = 0")
    suspend fun getRealCustomersCount(): Int

    @Query("SELECT * FROM customers WHERE isTestData = 0")
    suspend fun getSyncCustomers(): List<Customer>
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE salesmanId = :salesmanId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getSalesBySalesman(salesmanId: String): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE isDeleted = 0 AND isTestData = 1 ORDER BY createdAt DESC")
    fun getTestSales(): Flow<List<Sale>>

    @Query("SELECT * FROM sales WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getSaleByInvoice(invoiceNumber: String): Sale?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: Sale)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItem>)

    @Query("SELECT * FROM sale_items WHERE invoiceNumber = :invoiceNumber")
    suspend fun getSaleItems(invoiceNumber: String): List<SaleItem>

    @Query("SELECT * FROM sale_items WHERE invoiceNumber = :invoiceNumber")
    fun getSaleItemsFlow(invoiceNumber: String): Flow<List<SaleItem>>

    @Query("SELECT COUNT(*) FROM sales WHERE isDeleted = 0")
    suspend fun getSalesCount(): Int

    @Query("SELECT COUNT(*) FROM sales WHERE isDeleted = 0 AND isTestData = 0")
    suspend fun getRealSalesCount(): Int

    @Query("UPDATE sales SET isPinned = :isPinned WHERE invoiceNumber = :invoiceNumber")
    suspend fun updatePinStatus(invoiceNumber: String, isPinned: Boolean)

    @Query("UPDATE sales SET isDeleted = :isDeleted WHERE invoiceNumber = :invoiceNumber")
    suspend fun updateSaleDeletedStatus(invoiceNumber: String, isDeleted: Boolean)

    @Query("UPDATE sales SET isTestData = :isTest, testTag = :tag WHERE invoiceNumber = :invoiceNumber")
    suspend fun updateSaleTestDataStatus(invoiceNumber: String, isTest: Boolean, tag: String)

    @Query("DELETE FROM sales WHERE invoiceNumber = :invoiceNumber")
    suspend fun deleteSalePermanently(invoiceNumber: String)

    @Query("DELETE FROM sale_items WHERE invoiceNumber = :invoiceNumber")
    suspend fun deleteSaleItemsPermanently(invoiceNumber: String)

    @Query("SELECT * FROM sales WHERE customerId = :customerId")
    suspend fun getSalesByCustomer(customerId: String): List<Sale>

    @Query("DELETE FROM sales WHERE customerId = :customerId")
    suspend fun deleteSalesByCustomer(customerId: String)

    @Query("DELETE FROM sale_items WHERE invoiceNumber IN (SELECT invoiceNumber FROM sales WHERE customerId = :customerId)")
    suspend fun deleteSaleItemsByCustomer(customerId: String)

    @Query("SELECT * FROM sales WHERE isTestData = 0")
    suspend fun getSyncSales(): List<Sale>
}

@Dao
interface PaymentReceiptDao {
    @Query("SELECT * FROM payments WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllReceipts(): Flow<List<PaymentReceipt>>

    @Query("SELECT * FROM payments WHERE salesmanId = :salesmanId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getReceiptsBySalesman(salesmanId: String): Flow<List<PaymentReceipt>>

    @Query("SELECT * FROM payments WHERE isDeleted = 0 AND isTestData = 1 ORDER BY createdAt DESC")
    fun getTestReceipts(): Flow<List<PaymentReceipt>>

    @Query("SELECT * FROM payments WHERE receiptNumber = :receiptNumber LIMIT 1")
    suspend fun getReceiptByNumber(receiptNumber: String): PaymentReceipt?

    @Query("SELECT * FROM payments WHERE invoiceNumber = :invoiceNumber")
    suspend fun getReceiptsByInvoice(invoiceNumber: String): List<PaymentReceipt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: PaymentReceipt)

    @Query("SELECT COUNT(*) FROM payments WHERE isDeleted = 0")
    suspend fun getReceiptCount(): Int

    @Query("UPDATE payments SET isPinned = :isPinned WHERE receiptNumber = :receiptNumber")
    suspend fun updatePinStatus(receiptNumber: String, isPinned: Boolean)

    @Query("UPDATE payments SET isDeleted = :isDeleted WHERE receiptNumber = :receiptNumber")
    suspend fun updateReceiptDeletedStatus(receiptNumber: String, isDeleted: Boolean)

    @Query("UPDATE payments SET isTestData = :isTest, testTag = :tag WHERE receiptNumber = :receiptNumber")
    suspend fun updateReceiptTestDataStatus(receiptNumber: String, isTest: Boolean, tag: String)

    @Query("DELETE FROM payments WHERE receiptNumber = :receiptNumber")
    suspend fun deleteReceiptPermanently(receiptNumber: String)

    @Query("SELECT * FROM payments WHERE customerId = :customerId")
    suspend fun getReceiptsByCustomer(customerId: String): List<PaymentReceipt>

    @Query("DELETE FROM payments WHERE customerId = :customerId")
    suspend fun deleteReceiptsByCustomer(customerId: String)

    @Query("SELECT COUNT(*) FROM payments WHERE isDeleted = 0 AND isTestData = 0")
    suspend fun getRealReceiptsCount(): Int

    @Query("SELECT * FROM payments WHERE isTestData = 0")
    suspend fun getSyncReceipts(): List<PaymentReceipt>
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE salesmanId = :salesmanId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getExpensesBySalesman(salesmanId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 AND isTestData = 1 ORDER BY createdAt DESC")
    fun getTestExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: String): Expense?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Query("UPDATE expenses SET isDeleted = :isDeleted WHERE id = :id")
    suspend fun updateExpenseDeletedStatus(id: String, isDeleted: Boolean)

    @Query("UPDATE expenses SET isTestData = :isTest, testTag = :tag WHERE id = :id")
    suspend fun updateExpenseTestDataStatus(id: String, isTest: Boolean, tag: String)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpensePermanently(id: String)

    @Query("SELECT COUNT(*) FROM expenses WHERE isDeleted = 0 AND isTestData = 0")
    suspend fun getRealExpensesCount(): Int

    @Query("SELECT * FROM expenses WHERE isTestData = 0")
    suspend fun getSyncExpenses(): List<Expense>
}

@Dao
interface StockLogDao {
    @Query("SELECT * FROM stock_logs ORDER BY timestamp DESC")
    fun getAllStockLogs(): Flow<List<StockLog>>

    @Query("SELECT * FROM stock_logs WHERE productId = :productId ORDER BY timestamp DESC")
    suspend fun getLogsForProduct(productId: String): List<StockLog>

    @Insert
    suspend fun insertStockLog(log: StockLog)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLog>>

    @Insert
    suspend fun insertAuditLog(log: AuditLog)
}

@Dao
interface CompanySettingsDao {
    @Query("SELECT * FROM company_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<CompanySettings?>

    @Query("SELECT * FROM company_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): CompanySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: CompanySettings)
}

@Dao
interface ProductSubmissionDao {
    @Query("SELECT * FROM product_submissions ORDER BY updatedAt DESC")
    fun getAllSubmissions(): Flow<List<ProductSubmission>>

    @Query("SELECT * FROM product_submissions WHERE salesmanId = :salesmanId ORDER BY updatedAt DESC")
    fun getSubmissionsBySalesman(salesmanId: String): Flow<List<ProductSubmission>>

    @Query("SELECT * FROM product_submissions WHERE status = :status ORDER BY updatedAt DESC")
    fun getSubmissionsByStatus(status: ProductSubmissionStatus): Flow<List<ProductSubmission>>

    @Query("SELECT * FROM product_submissions WHERE id = :id LIMIT 1")
    suspend fun getSubmissionById(id: String): ProductSubmission?

    @Query("SELECT * FROM product_submissions WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name))")
    suspend fun findSubmissionsByName(name: String): List<ProductSubmission>

    @Query("SELECT * FROM product_submissions WHERE LOWER(TRIM(productCode)) = LOWER(TRIM(:code))")
    suspend fun findSubmissionsByProductCode(code: String): List<ProductSubmission>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubmission(submission: ProductSubmission)

    @Update
    suspend fun updateSubmission(submission: ProductSubmission)

    @Query("SELECT COUNT(*) FROM product_submissions WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM product_submissions")
    suspend fun getSyncSubmissions(): List<ProductSubmission>
}

@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllCompaniesFlow(): Flow<List<Company>>

    @Query("SELECT * FROM companies WHERE isDeleted = 0 AND isTestData = 1 ORDER BY name ASC")
    fun getTestCompaniesFlow(): Flow<List<Company>>

    @Query("SELECT * FROM companies WHERE id = :id LIMIT 1")
    fun getCompanyByIdFlow(id: String): Flow<Company?>

    @Query("SELECT * FROM companies WHERE id = :id LIMIT 1")
    suspend fun getCompanyById(id: String): Company?

    @Query("SELECT * FROM companies WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findCompanyByName(name: String): Company?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompany(company: Company)

    @Update
    suspend fun updateCompany(company: Company)

    @Query("UPDATE companies SET isDeleted = :isDeleted, updatedAt = :time WHERE id = :id")
    suspend fun updateCompanyDeletedStatus(id: String, isDeleted: Boolean, time: Long = System.currentTimeMillis())

    @Query("UPDATE companies SET isTestData = :isTest, testTag = :tag, updatedAt = :time WHERE id = :id")
    suspend fun updateCompanyTestDataStatus(id: String, isTest: Boolean, tag: String, time: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteCompany(company: Company)

    @Query("DELETE FROM companies WHERE id = :id")
    suspend fun deleteCompanyPermanently(id: String)

    @Query("SELECT COUNT(*) FROM companies WHERE isDeleted = 0")
    suspend fun countCompanies(): Int
}

@Dao
interface CompanyPurchaseDao {
    @Query("SELECT * FROM company_purchases ORDER BY purchaseDate DESC, createdAt DESC")
    fun getAllPurchasesFlow(): Flow<List<CompanyPurchase>>

    @Query("SELECT * FROM company_purchases WHERE companyId = :companyId ORDER BY purchaseDate DESC, createdAt DESC")
    fun getPurchasesByCompanyFlow(companyId: String): Flow<List<CompanyPurchase>>

    @Query("SELECT * FROM company_purchases WHERE companyId = :companyId ORDER BY purchaseDate DESC, createdAt DESC")
    suspend fun getPurchasesByCompany(companyId: String): List<CompanyPurchase>

    @Query("SELECT * FROM company_purchases WHERE id = :id LIMIT 1")
    suspend fun getPurchaseById(id: String): CompanyPurchase?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchase(purchase: CompanyPurchase)

    @Delete
    suspend fun deletePurchase(purchase: CompanyPurchase)
}

@Dao
interface CompanyPaymentDao {
    @Query("SELECT * FROM company_payments ORDER BY paymentDate DESC, createdAt DESC")
    fun getAllPaymentsFlow(): Flow<List<CompanyPayment>>

    @Query("SELECT * FROM company_payments WHERE companyId = :companyId ORDER BY paymentDate DESC, createdAt DESC")
    fun getPaymentsByCompanyFlow(companyId: String): Flow<List<CompanyPayment>>

    @Query("SELECT * FROM company_payments WHERE companyId = :companyId ORDER BY paymentDate DESC, createdAt DESC")
    suspend fun getPaymentsByCompany(companyId: String): List<CompanyPayment>

    @Query("SELECT * FROM company_payments WHERE id = :id LIMIT 1")
    suspend fun getPaymentById(id: String): CompanyPayment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: CompanyPayment)

    @Delete
    suspend fun deletePayment(payment: CompanyPayment)
}

@Dao
interface RecycleBinDao {
    @Query("SELECT * FROM recycle_bin WHERE isRestored = 0 AND itemType NOT IN ('PERMANENT_TOMBSTONE', 'TOMBSTONE') ORDER BY deletedAt DESC")
    fun getActiveRecycleBinItems(): Flow<List<RecycleBinItem>>

    @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC")
    fun getAllRecycleBinItems(): Flow<List<RecycleBinItem>>

    @Query("SELECT * FROM recycle_bin WHERE id = :id AND itemType NOT IN ('PERMANENT_TOMBSTONE', 'TOMBSTONE') LIMIT 1")
    suspend fun getItemById(id: String): RecycleBinItem?

    @Query("SELECT * FROM recycle_bin WHERE originalTable = :table AND originalId = :origId LIMIT 1")
    suspend fun getItemByOriginalId(table: String, origId: String): RecycleBinItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: RecycleBinItem)

    @Update
    suspend fun updateItem(item: RecycleBinItem)

    @Query("UPDATE recycle_bin SET isRestored = 1, restoredAt = :restoredAt, restoredByAdminId = :adminId WHERE id = :id")
    suspend fun markRestored(id: String, adminId: String, restoredAt: Long = System.currentTimeMillis())

    @Query("UPDATE recycle_bin SET itemType = 'PERMANENT_TOMBSTONE', deleteReason = 'PERMANENT_TOMBSTONE', isRestored = 0, deletedAt = :time WHERE originalTable = :table AND originalId = :origId")
    suspend fun markPermanentlyDeleted(table: String, origId: String, time: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteItem(item: RecycleBinItem)

    @Query("DELETE FROM recycle_bin WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("SELECT COUNT(*) FROM recycle_bin WHERE isRestored = 0 AND itemType NOT IN ('PERMANENT_TOMBSTONE', 'TOMBSTONE')")
    fun getActiveCount(): Flow<Int>

    @Query("SELECT * FROM recycle_bin WHERE isRestored = 0")
    suspend fun getSyncTombstones(): List<RecycleBinItem>
}
