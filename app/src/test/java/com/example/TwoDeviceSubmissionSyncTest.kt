package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.data.repository.BusinessRepository
import com.example.data.sync.CloudBusinessAuth
import com.example.data.sync.CloudSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwoDeviceSubmissionSyncTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var adminDb: AppDatabase
    private lateinit var salesmanDb: AppDatabase
    private lateinit var adminSyncManager: CloudSyncManager
    private lateinit var salesmanSyncManager: CloudSyncManager

    private val businessId = "biz_test_sync_999"
    private val adminId = "admin_owner_1"
    private val salesmanId = "salesman_user_2"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        CloudBusinessAuth.clear(app)

        // Device 1: Admin phone local Room database
        adminDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        adminSyncManager = CloudSyncManager(app, adminDb)

        // Device 2: Salesman separate phone local Room database
        salesmanDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        salesmanSyncManager = CloudSyncManager(app, salesmanDb)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        adminDb.close()
        salesmanDb.close()
        CloudBusinessAuth.clear(app)
    }

    // 1. Product submission: Salesman submits on phone -> Admin receives PENDING submission via applyRemoteSubmissionMap
    @Test
    fun testSalesmanProductSubmissionUploadAndAdminReceive() = runTest(testDispatcher) {
        val submission = ProductSubmission(
            id = "sub_prod_101",
            name = "প্রিমিয়াম সুতি পাঞ্জাবি",
            productCode = "SKU-PANJABI-01",
            category = "পোশাক",
            purchasePrice = 800.0,
            sellingPrice = 1360.0,
            initialStock = 20.0,
            unit = "পিস",
            description = "উচ্চমানের কটন সুতি পাঞ্জাবি",
            salesmanId = salesmanId,
            salesmanName = "করিম আহমেদ",
            salesmanPhone = "01711000000",
            status = ProductSubmissionStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Salesman saves locally
        salesmanDb.productSubmissionDao().insertSubmission(submission)
        val salesmanLocal = salesmanDb.productSubmissionDao().getSubmissionById("sub_prod_101")
        assertNotNull("Salesman local database must store the submission", salesmanLocal)
        assertEquals(ProductSubmissionStatus.PENDING, salesmanLocal?.status)

        // Verify sync selection on Salesman device
        val syncList = salesmanDb.productSubmissionDao().getSyncSubmissions()
        assertTrue("getSyncSubmissions must contain the new submission", syncList.any { it.id == "sub_prod_101" })

        // Verify Cloud mapping
        val cloudMap = CloudSyncManager.mapProductSubmissionToCloud(submission, businessId)
        assertEquals("sub_prod_101", cloudMap["id"])
        assertEquals("প্রিমিয়াম সুতি পাঞ্জাবি", cloudMap["name"])
        assertEquals("PENDING", cloudMap["status"])
        assertEquals(businessId, cloudMap["businessId"])
        assertEquals(salesmanId, cloudMap["salesmanId"])

        // Admin device should initially not have this submission
        assertNull("Admin device should initially not have this submission", adminDb.productSubmissionDao().getSubmissionById("sub_prod_101"))

        // Admin device receives and applies remote submission using real CloudSyncManager logic
        adminSyncManager.applyRemoteSubmissionMap(cloudMap)

        // Verify Admin now sees the submission in PENDING status
        val adminReceived = adminDb.productSubmissionDao().getSubmissionById("sub_prod_101")
        assertNotNull("Admin database must receive the salesman product submission", adminReceived)
        assertEquals(ProductSubmissionStatus.PENDING, adminReceived?.status)
        assertEquals("প্রিমিয়াম সুতি পাঞ্জাবি", adminReceived?.name)
        assertEquals("করিম আহমেদ", adminReceived?.salesmanName)
    }

    // 2. Admin approves product submission -> Salesman receives APPROVED status AND approved Product in inventory
    @Test
    fun testAdminProductApprovalReflectsOnSalesmanDevice() = runTest(testDispatcher) {
        val submission = ProductSubmission(
            id = "sub_prod_102",
            name = "লেদার মানিব্যাগ",
            productCode = "SKU-WALLET-01",
            category = "এক্সেসরিজ",
            purchasePrice = 300.0,
            sellingPrice = 510.0,
            initialStock = 15.0,
            unit = "পিস",
            description = "জেনুইন লেদার ওয়ালেট",
            salesmanId = salesmanId,
            salesmanName = "করিম আহমেদ",
            salesmanPhone = "01711000000",
            status = ProductSubmissionStatus.PENDING
        )
        salesmanDb.productSubmissionDao().insertSubmission(submission)
        adminDb.productSubmissionDao().insertSubmission(submission)

        // Admin approves the submission
        val approvedProductId = "prod_wallet_approved_102"
        val reviewTime = System.currentTimeMillis()
        val approvedSub = submission.copy(
            status = ProductSubmissionStatus.APPROVED,
            approvedProductId = approvedProductId,
            reviewedByAdminId = adminId,
            reviewedByAdminName = "ম্যানেজার",
            reviewedAt = reviewTime,
            updatedAt = reviewTime + 500
        )
        adminDb.productSubmissionDao().updateSubmission(approvedSub)

        // Admin also adds the approved product to inventory
        val approvedProduct = Product(
            id = approvedProductId,
            name = submission.name,
            sku = submission.productCode,
            category = submission.category,
            purchasePrice = submission.purchasePrice,
            sellingPrice = submission.sellingPrice,
            currentStock = submission.initialStock,
            unit = submission.unit,
            isActive = true,
            updatedAt = reviewTime + 500
        )
        adminDb.productDao().insertProduct(approvedProduct)

        // Admin uploads approved submission and approved product to Cloud
        val cloudApprovedSubMap = CloudSyncManager.mapProductSubmissionToCloud(approvedSub, businessId)
        val cloudApprovedProdMap = CloudSyncManager.mapProductToCloud(approvedProduct, businessId, isSalesman = false)
        assertEquals("APPROVED", cloudApprovedSubMap["status"])
        assertEquals(approvedProductId, cloudApprovedSubMap["approvedProductId"])

        // Salesman device syncs remote approved submission via applyRemoteSubmissionMap
        salesmanSyncManager.applyRemoteSubmissionMap(cloudApprovedSubMap)

        // Salesman device syncs remote product via applyRemoteProductMap
        salesmanSyncManager.applyRemoteProductMap(cloudApprovedProdMap, isSalesman = true)

        // Verify Salesman submission record is now APPROVED
        val salesmanFinalSub = salesmanDb.productSubmissionDao().getSubmissionById("sub_prod_102")
        assertEquals("Salesman device must reflect APPROVED status", ProductSubmissionStatus.APPROVED, salesmanFinalSub?.status)
        assertEquals(approvedProductId, salesmanFinalSub?.approvedProductId)

        // Verify Salesman product inventory has the approved product with protected cost price
        val salesmanProduct = salesmanDb.productDao().getProductById(approvedProductId)
        assertNotNull("Salesman must have the approved product in local inventory", salesmanProduct)
        assertEquals("লেদার মানিব্যাগ", salesmanProduct?.name)
        assertEquals(510.0, salesmanProduct?.sellingPrice ?: 0.0, 0.001)
        assertEquals(15.0, salesmanProduct?.currentStock ?: 0.0, 0.001)
        assertEquals("Confidential purchase price must be 0 for salesman", 0.0, salesmanProduct?.purchasePrice ?: 0.0, 0.001)
    }

    // 3. Admin returns submission for correction -> Salesman sees NEEDS_CORRECTION and note
    @Test
    fun testAdminReturnForCorrectionWorkflow() = runTest(testDispatcher) {
        val submission = ProductSubmission(
            id = "sub_prod_103",
            name = "টি-শার্ট",
            productCode = "SKU-TSHIRT-01",
            category = "পোশাক",
            purchasePrice = 250.0,
            sellingPrice = 425.0,
            initialStock = 10.0,
            salesmanId = salesmanId,
            salesmanName = "করিম আহমেদ",
            salesmanPhone = "01711000000",
            status = ProductSubmissionStatus.PENDING
        )
        salesmanDb.productSubmissionDao().insertSubmission(submission)
        adminDb.productSubmissionDao().insertSubmission(submission)

        // Admin marks as NEEDS_CORRECTION with note
        val correctionSub = submission.copy(
            status = ProductSubmissionStatus.NEEDS_CORRECTION,
            adminCorrectionNote = "দয়া করে সঠিক সাইজ ও ফেব্রিক বিবরণ দিন",
            updatedAt = System.currentTimeMillis() + 500
        )
        adminDb.productSubmissionDao().updateSubmission(correctionSub)

        val cloudMap = CloudSyncManager.mapProductSubmissionToCloud(correctionSub, businessId)
        assertEquals("NEEDS_CORRECTION", cloudMap["status"])
        assertEquals("দয়া করে সঠিক সাইজ ও ফেব্রিক বিবরণ দিন", cloudMap["adminCorrectionNote"])

        // Salesman device updates via real CloudSyncManager
        salesmanSyncManager.applyRemoteSubmissionMap(cloudMap)

        val resultOnSalesman = salesmanDb.productSubmissionDao().getSubmissionById("sub_prod_103")
        assertEquals(ProductSubmissionStatus.NEEDS_CORRECTION, resultOnSalesman?.status)
        assertEquals("দয়া করে সঠিক সাইজ ও ফেব্রিক বিবরণ দিন", resultOnSalesman?.adminCorrectionNote)
    }

    // 4. Expense submission: Salesman submits -> Admin receives PENDING expense
    @Test
    fun testSalesmanExpenseSubmissionAndAdminReceive() = runTest(testDispatcher) {
        val expense = Expense(
            id = "exp_travel_201",
            salesmanId = salesmanId,
            salesmanName = "করিম আহমেদ",
            category = ExpenseCategory.TRAVEL,
            source = ExpenseSource.COMPANY_CASH,
            amount = 350.0,
            description = "গ্রাহক ভিজিটে সিএনজি ভাড়া",
            status = ExpenseStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )

        // Salesman saves locally
        salesmanDb.expenseDao().insertExpense(expense)
        val salesmanLocalExp = salesmanDb.expenseDao().getExpenseById("exp_travel_201")
        assertNotNull(salesmanLocalExp)
        assertEquals(ExpenseStatus.PENDING, salesmanLocalExp?.status)

        // Verify sync list
        val syncExpenses = salesmanDb.expenseDao().getSyncExpenses()
        assertTrue("getSyncExpenses must contain the new expense", syncExpenses.any { it.id == "exp_travel_201" })

        // Verify Cloud mapping
        val cloudMap = CloudSyncManager.mapExpenseToCloud(expense, businessId)
        assertEquals("exp_travel_201", cloudMap["id"])
        assertEquals(350.0, cloudMap["amount"])
        assertEquals("PENDING", cloudMap["status"])
        assertEquals(businessId, cloudMap["businessId"])

        // Admin receives and applies remote expense using real CloudSyncManager
        assertNull(adminDb.expenseDao().getExpenseById("exp_travel_201"))
        adminSyncManager.applyRemoteExpenseMap(cloudMap)

        val adminReceived = adminDb.expenseDao().getExpenseById("exp_travel_201")
        assertNotNull("Admin must receive the salesman expense", adminReceived)
        assertEquals(ExpenseStatus.PENDING, adminReceived?.status)
        assertEquals(350.0, adminReceived?.amount ?: 0.0, 0.001)
        assertEquals("গ্রাহক ভিজিটে সিএনজি ভাড়া", adminReceived?.description)
    }

    // 5. Admin approves expense -> Salesman device updates from PENDING to APPROVED with adminNote & reviewedAt
    @Test
    fun testAdminExpenseApprovalReflectsOnSalesmanDevice() = runTest(testDispatcher) {
        val expense = Expense(
            id = "exp_food_202",
            salesmanId = salesmanId,
            salesmanName = "করিম আহমেদ",
            category = ExpenseCategory.FOOD,
            source = ExpenseSource.COMPANY_CASH,
            amount = 180.0,
            description = "দুপুরের খাবার",
            status = ExpenseStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
        salesmanDb.expenseDao().insertExpense(expense)
        adminDb.expenseDao().insertExpense(expense)

        // Admin approves with note
        val reviewTime = System.currentTimeMillis()
        val approvedExpense = expense.copy(
            status = ExpenseStatus.APPROVED,
            adminNote = "অনুমোদিত ও ভাউচার পাস",
            reviewedAt = reviewTime
        )
        adminDb.expenseDao().updateExpense(approvedExpense)

        // Cloud mapping from Admin
        val cloudApprovedMap = CloudSyncManager.mapExpenseToCloud(approvedExpense, businessId)
        assertEquals("APPROVED", cloudApprovedMap["status"])
        assertEquals("অনুমোদিত ও ভাউচার পাস", cloudApprovedMap["adminNote"])
        assertEquals(reviewTime, cloudApprovedMap["reviewedAt"])

        // Salesman applies remote update using real CloudSyncManager
        salesmanSyncManager.applyRemoteExpenseMap(cloudApprovedMap)

        // Verify Salesman device is now APPROVED with notes
        val salesmanFinal = salesmanDb.expenseDao().getExpenseById("exp_food_202")
        assertEquals(ExpenseStatus.APPROVED, salesmanFinal?.status)
        assertEquals("অনুমোদিত ও ভাউচার পাস", salesmanFinal?.adminNote)
        assertEquals(reviewTime, salesmanFinal?.reviewedAt)
    }

    // 6. Admin rejects expense -> Salesman device updates from PENDING to REJECTED with adminNote
    @Test
    fun testAdminExpenseRejectionReflectsOnSalesmanDevice() = runTest(testDispatcher) {
        val expense = Expense(
            id = "exp_misc_203",
            salesmanId = salesmanId,
            salesmanName = "করিম আহমেদ",
            category = ExpenseCategory.OTHER,
            source = ExpenseSource.OWN_POCKET,
            amount = 500.0,
            description = "অপ্রয়োজনীয় ব্যক্তিগত খরচ",
            status = ExpenseStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
        salesmanDb.expenseDao().insertExpense(expense)
        adminDb.expenseDao().insertExpense(expense)

        val rejectTime = System.currentTimeMillis()
        val rejectedExpense = expense.copy(
            status = ExpenseStatus.REJECTED,
            adminNote = "কোম্পানি পলিসি বহির্ভূত খরচ, অনুমোদনযোগ্য নয়",
            reviewedAt = rejectTime
        )
        adminDb.expenseDao().updateExpense(rejectedExpense)

        val cloudMap = CloudSyncManager.mapExpenseToCloud(rejectedExpense, businessId)
        assertEquals("REJECTED", cloudMap["status"])

        // Salesman device syncs remote rejection
        salesmanSyncManager.applyRemoteExpenseMap(cloudMap)

        val salesmanResult = salesmanDb.expenseDao().getExpenseById("exp_misc_203")
        assertEquals(ExpenseStatus.REJECTED, salesmanResult?.status)
        assertEquals("কোম্পানি পলিসি বহির্ভূত খরচ, অনুমোদনযোগ্য নয়", salesmanResult?.adminNote)
        assertEquals(rejectTime, salesmanResult?.reviewedAt)
    }

    // 7. Duplicate prevention: sync replay does not duplicate records
    @Test
    fun testDuplicateSubmissionAndExpensePrevention() = runTest(testDispatcher) {
        val submission = ProductSubmission(
            id = "sub_dedup_01",
            name = "ইউনিক আইটেম",
            category = "সাধারণ",
            purchasePrice = 100.0,
            sellingPrice = 170.0,
            initialStock = 5.0,
            salesmanId = salesmanId,
            salesmanName = "সেলসম্যান",
            salesmanPhone = "01700000000"
        )
        salesmanDb.productSubmissionDao().insertSubmission(submission)
        salesmanDb.productSubmissionDao().insertSubmission(submission) // re-insert

        val allSubs = salesmanDb.productSubmissionDao().getSyncSubmissions()
        assertEquals("Re-inserting same ID must not duplicate", 1, allSubs.count { it.id == "sub_dedup_01" })

        val expense = Expense(
            id = "exp_dedup_01",
            salesmanId = salesmanId,
            salesmanName = "সেলসম্যান",
            category = ExpenseCategory.OTHER,
            source = ExpenseSource.COMPANY_CASH,
            amount = 50.0,
            description = "ফটোকপি"
        )
        salesmanDb.expenseDao().insertExpense(expense)
        salesmanDb.expenseDao().insertExpense(expense) // re-insert

        val allExps = salesmanDb.expenseDao().getSyncExpenses()
        assertEquals("Re-inserting same expense ID must not duplicate", 1, allExps.count { it.id == "exp_dedup_01" })
    }

    // 8. BUG 1 Verification: Approved/Rejected expense NEVER reverts to PENDING on subsequent syncs or stale replays
    @Test
    fun testExpenseApprovedStatusDoesNotRevertToPendingOnStaleSync() = runTest(testDispatcher) {
        val expId = "exp_persistent_approval_801"
        val expense = Expense(
            id = expId,
            salesmanId = salesmanId,
            salesmanName = "করিম আহমেদ",
            category = ExpenseCategory.TRAVEL,
            source = ExpenseSource.COMPANY_CASH,
            amount = 450.0,
            description = "দূরপাল্লার যাতায়াত ভাড়া",
            status = ExpenseStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
        adminDb.expenseDao().insertExpense(expense)
        salesmanDb.expenseDao().insertExpense(expense)

        // Admin approves the expense
        val reviewTimestamp = System.currentTimeMillis()
        val approved = expense.copy(
            status = ExpenseStatus.APPROVED,
            adminNote = "ভাউচার যাচাই সম্পন্ন",
            reviewedAt = reviewTimestamp
        )
        adminDb.expenseDao().updateExpense(approved)

        // Verify locally approved on Admin device
        val localApproved = adminDb.expenseDao().getExpenseById(expId)
        assertEquals(ExpenseStatus.APPROVED, localApproved?.status)

        // Stale remote cloud payload (still PENDING) arrives at Admin phone (e.g. from background queue or race condition)
        val stalePendingMap = mapOf<String, Any?>(
            "id" to expId,
            "salesmanId" to salesmanId,
            "salesmanName" to "করিম আহমেদ",
            "category" to ExpenseCategory.TRAVEL.name,
            "source" to ExpenseSource.COMPANY_CASH.name,
            "amount" to 450.0,
            "description" to "দূরপাল্লার যাতায়াত ভাড়া",
            "status" to "PENDING",
            "reviewedAt" to null,
            "adminNote" to "",
            "isDeleted" to false,
            "createdAt" to expense.createdAt
        )
        adminSyncManager.applyRemoteExpenseMap(stalePendingMap)

        // Crucial Assertion: Admin device MUST REMAIN APPROVED and NEVER revert to PENDING
        val finalAdminExp = adminDb.expenseDao().getExpenseById(expId)
        assertNotNull(finalAdminExp)
        assertEquals("Admin approval MUST NOT revert back to PENDING when stale cloud payload arrives",
            ExpenseStatus.APPROVED, finalAdminExp?.status)
        assertEquals("ভাউচার যাচাই সম্পন্ন", finalAdminExp?.adminNote)
        assertEquals(reviewTimestamp, finalAdminExp?.reviewedAt)
    }

    // 9. BUG 2 Verification: Shared Business Code connects two separate devices to the exact same business collection
    @Test
    fun testTwoDeviceSharedBusinessCodeProductSubmissionVisibility() = runTest(testDispatcher) {
        val sharedBusinessCode = "BIZ-CLOTH-999"
        val submissionId = "sub_shared_biz_777"

        val salesmanSubmission = ProductSubmission(
            id = submissionId,
            name = "কাতান সিল্ক শাড়ি",
            productCode = "SKU-KATAN-01",
            category = "শাড়ি",
            purchasePrice = 2500.0,
            sellingPrice = 4250.0,
            initialStock = 8.0,
            unit = "পিস",
            description = "খাঁটি কাতান সিল্ক শাড়ি",
            salesmanId = salesmanId,
            salesmanName = "করিম আহমেদ",
            salesmanPhone = "01711000000",
            status = ProductSubmissionStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Salesman saves locally and maps to the shared business ID
        salesmanDb.productSubmissionDao().insertSubmission(salesmanSubmission)
        val cloudMap = CloudSyncManager.mapProductSubmissionToCloud(salesmanSubmission, sharedBusinessCode)
        assertEquals(sharedBusinessCode, cloudMap["businessId"])
        assertEquals("PENDING", cloudMap["status"])

        // Admin device (on separate phone using the exact same shared business code) receives and applies submission
        assertNull(adminDb.productSubmissionDao().getSubmissionById(submissionId))
        adminSyncManager.applyRemoteSubmissionMap(cloudMap)

        // Admin device must immediately see this submission in PENDING status
        val receivedOnAdmin = adminDb.productSubmissionDao().getSubmissionById(submissionId)
        assertNotNull("Admin device using shared business code MUST receive salesman product submission", receivedOnAdmin)
        assertEquals(ProductSubmissionStatus.PENDING, receivedOnAdmin?.status)
        assertEquals("কাতান সিল্ক শাড়ি", receivedOnAdmin?.name)
        assertEquals(2500.0, receivedOnAdmin?.purchasePrice ?: 0.0, 0.001)
        assertEquals(4250.0, receivedOnAdmin?.sellingPrice ?: 0.0, 0.001)

        // Verify it remains PENDING until Admin explicitly approves it
        assertEquals(ProductSubmissionStatus.PENDING, receivedOnAdmin?.status)
        assertNull("Approved product ID must be null while pending", receivedOnAdmin?.approvedProductId)
    }

    // 10. Root Cause Test: Salesman phone with seeded admin_1 MUST be recognized as salesman
    @Test
    fun testSalesmanPhoneWithSeededAdminIsCorrectlyClassifiedAsSalesman() = runTest(testDispatcher) {
        // Seed default admin_1 into salesmanDb just like AppDatabase seeds it on every real phone
        val seededAdmin = User(
            id = "admin_1",
            name = "অ্যাডমিন (ডিফল্ট)",
            phone = "01700000000",
            role = UserRole.ADMIN,
            pinHash = "hash_1234",
            isActive = true
        )
        salesmanDb.userDao().insertUser(seededAdmin)

        // Salesman logs in with salesman role and connects to business
        CloudBusinessAuth.setStoredBusinessId(app, businessId, "টেস্ট ব্যবসা", UserRole.SALESMAN.name)

        // Must evaluate to true (Salesman), NOT false (Admin)
        val isSalesman = salesmanSyncManager.isCurrentActiveSalesman()
        assertTrue("Salesman device with seeded admin_1 must be correctly identified as SALESMAN", isSalesman)

        // Verify Admin phone with ADMIN role evaluates to false (Admin)
        CloudBusinessAuth.setStoredBusinessId(app, businessId, "টেস্ট ব্যবসা", UserRole.ADMIN.name)
        val isAdmin = adminSyncManager.isCurrentActiveSalesman()
        assertFalse("Admin device must be identified as NOT salesman (ADMIN)", isAdmin)
    }

    // 11. Root Cause Test: Salesman sync must NEVER overwrite Admin approval back to PENDING
    @Test
    fun testExpenseApprovalPermanenceWhenSalesmanHasPendingLocally() = runTest(testDispatcher) {
        val expId = "exp_cross_phone_permanence_901"
        val pendingExpense = Expense(
            id = expId,
            salesmanId = salesmanId,
            salesmanName = "করিম আহমেদ",
            category = ExpenseCategory.FOOD,
            source = ExpenseSource.COMPANY_CASH,
            amount = 250.0,
            description = "টিফিন খরচ",
            status = ExpenseStatus.PENDING,
            createdAt = 100000L
        )

        // Both devices initially have the pending expense
        salesmanDb.expenseDao().insertExpense(pendingExpense)
        adminDb.expenseDao().insertExpense(pendingExpense)

        // Admin approves it
        val reviewTimestamp = 200000L
        val approvedExpense = pendingExpense.copy(
            status = ExpenseStatus.APPROVED,
            adminNote = "অনুমোদিত",
            reviewedAt = reviewTimestamp
        )
        adminDb.expenseDao().updateExpense(approvedExpense)

        // Cloud doc is APPROVED
        val cloudApprovedMap = CloudSyncManager.mapExpenseToCloud(approvedExpense, businessId)
        assertEquals("APPROVED", cloudApprovedMap["status"])

        // Salesman device syncs remote approval
        salesmanSyncManager.applyRemoteExpenseMap(cloudApprovedMap)
        val salesmanAfterSync = salesmanDb.expenseDao().getExpenseById(expId)
        assertEquals(ExpenseStatus.APPROVED, salesmanAfterSync?.status)

        // Even if a stale local PENDING map is attempted on Admin device, it is blocked
        val stalePendingPayload = CloudSyncManager.mapExpenseToCloud(pendingExpense, businessId)
        adminSyncManager.applyRemoteExpenseMap(stalePendingPayload)

        val finalAdminExpense = adminDb.expenseDao().getExpenseById(expId)
        assertEquals("Admin expense must remain permanently APPROVED", ExpenseStatus.APPROVED, finalAdminExpense?.status)
        assertEquals("অনুমোদিত", finalAdminExpense?.adminNote)
    }

    // 12. Cross-Device Specific Test: "থ্রি পিস" (SKU P006, ৳850, ৳1445, 150 সেট) submission from Salesman phone to Admin phone
    @Test
    fun testTwoDeviceThreePieceP006ProductSubmissionAndApproval() = runTest(testDispatcher) {
        val sharedBizId = "biz_shared_apparel_3piece"
        val submissionId = "sub_p006_three_piece"

        val adminUser = User(
            id = adminId,
            name = "অ্যাডমিন আতাউর",
            phone = "01700000000",
            role = UserRole.ADMIN,
            pinHash = "pin_hash_1",
            isActive = true
        )
        adminDb.userDao().insertUser(adminUser)

        val salesmanUser = User(
            id = salesmanId,
            name = "সেলসম্যান রফিক",
            phone = "01800000000",
            role = UserRole.SALESMAN,
            pinHash = "pin_hash_2",
            isActive = true
        )
        salesmanDb.userDao().insertUser(salesmanUser)

        val adminRepo = com.example.data.repository.BusinessRepository(adminDb)

        // 1. Salesman submits "থ্রি পিস" (SKU P006, ৳850 purchase, ৳1445 selling, 150 সেট stock) on Salesman phone
        val salesmanSubmission = ProductSubmission(
            id = submissionId,
            name = "থ্রি পিস",
            productCode = "P006",
            category = "মহিলাদের পোশাক",
            purchasePrice = 850.0,
            sellingPrice = 1445.0,
            initialStock = 150.0,
            unit = "সেট",
            description = "সুতি প্রিন্টেড থ্রি পিস",
            salesmanId = salesmanId,
            salesmanName = "সেলসম্যান রফিক",
            salesmanPhone = "01800000000",
            status = ProductSubmissionStatus.PENDING,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        // Saved locally on Salesman phone
        salesmanDb.productSubmissionDao().insertSubmission(salesmanSubmission)
        val salesmanLocalSub = salesmanDb.productSubmissionDao().getSubmissionById(submissionId)
        assertNotNull(salesmanLocalSub)
        assertEquals(ProductSubmissionStatus.PENDING, salesmanLocalSub?.status)

        // 2. Maps to shared business ID in Cloud
        val cloudMap = CloudSyncManager.mapProductSubmissionToCloud(salesmanSubmission, sharedBizId)
        assertEquals(sharedBizId, cloudMap["businessId"])
        assertEquals("PENDING", cloudMap["status"])
        assertEquals("থ্রি পিস", cloudMap["name"])
        assertEquals("P006", cloudMap["productCode"])
        assertEquals(850.0, cloudMap["purchasePrice"])
        assertEquals(1445.0, cloudMap["sellingPrice"])
        assertEquals(150.0, cloudMap["initialStock"])
        assertEquals("সেট", cloudMap["unit"])

        // 3. Admin phone receives cloud data via applyRemoteSubmissionMap
        assertNull("Before sync, Admin phone has no submission", adminDb.productSubmissionDao().getSubmissionById(submissionId))
        adminSyncManager.applyRemoteSubmissionMap(cloudMap)

        // Admin phone now has the pending submission
        val adminPendingSub = adminDb.productSubmissionDao().getSubmissionById(submissionId)
        assertNotNull("Admin phone must receive the pending submission from cloud", adminPendingSub)
        assertEquals(ProductSubmissionStatus.PENDING, adminPendingSub?.status)
        assertEquals("থ্রি পিস", adminPendingSub?.name)
        assertEquals("P006", adminPendingSub?.productCode)
        assertEquals(850.0, adminPendingSub?.purchasePrice ?: 0.0, 0.001)
        assertEquals(1445.0, adminPendingSub?.sellingPrice ?: 0.0, 0.001)
        assertEquals(150.0, adminPendingSub?.initialStock ?: 0.0, 0.001)
        assertEquals("সেট", adminPendingSub?.unit)

        // Verify pending count on Admin device
        val pendingCount = adminDb.productSubmissionDao().getSyncSubmissions().count { it.status == ProductSubmissionStatus.PENDING }
        assertEquals(1, pendingCount)

        // 4. Verify product is NOT in Admin main inventory before approval
        val allAdminProductsBefore = adminDb.productDao().getAllProductsList()
        assertFalse("Product must NOT exist in main inventory before Admin approval", allAdminProductsBefore.any { it.sku == "P006" || it.name == "থ্রি পিস" })

        // 5. Admin approves the submission
        val approvedProduct = adminRepo.approveProductSubmission(
            submissionId = submissionId,
            finalData = adminPendingSub!!,
            adminUser = adminUser
        )
        assertNotNull(approvedProduct)
        assertEquals("থ্রি পিস", approvedProduct.name)
        assertEquals("P006", approvedProduct.sku)
        assertEquals(150.0, approvedProduct.currentStock, 0.001)
        assertEquals(850.0, approvedProduct.purchasePrice, 0.001)
        assertEquals(1445.0, approvedProduct.sellingPrice, 0.001)

        // 6. Verify product is in Admin inventory exactly once
        val allAdminProductsAfter = adminDb.productDao().getAllProductsList()
        val matchingProducts = allAdminProductsAfter.filter { it.sku == "P006" }
        assertEquals("Product must exist exactly once in Admin inventory", 1, matchingProducts.size)

        // 7. Verify submission status is now APPROVED on Admin device
        val adminApprovedSub = adminDb.productSubmissionDao().getSubmissionById(submissionId)
        assertEquals(ProductSubmissionStatus.APPROVED, adminApprovedSub?.status)
        assertEquals(approvedProduct.id, adminApprovedSub?.approvedProductId)

        // 8. Sync approval decisions to Salesman device via Cloud mapping
        val cloudApprovedSubMap = CloudSyncManager.mapProductSubmissionToCloud(adminApprovedSub!!, sharedBizId)
        val cloudApprovedProdMap = CloudSyncManager.mapProductToCloud(approvedProduct, sharedBizId, isSalesman = false)

        salesmanSyncManager.applyRemoteSubmissionMap(cloudApprovedSubMap)
        salesmanSyncManager.applyRemoteProductMap(cloudApprovedProdMap, isSalesman = true)

        // 9. Verify Salesman phone now reflects APPROVED status and received the product
        val salesmanFinalSub = salesmanDb.productSubmissionDao().getSubmissionById(submissionId)
        assertEquals(ProductSubmissionStatus.APPROVED, salesmanFinalSub?.status)
        assertEquals(approvedProduct.id, salesmanFinalSub?.approvedProductId)

        val salesmanFinalProduct = salesmanDb.productDao().getProductById(approvedProduct.id)
        assertNotNull("Salesman must have the approved product in stock", salesmanFinalProduct)
        assertEquals("থ্রি পিস", salesmanFinalProduct?.name)
        assertEquals("P006", salesmanFinalProduct?.sku)
        assertEquals(150.0, salesmanFinalProduct?.currentStock ?: 0.0, 0.001)
        assertEquals(1445.0, salesmanFinalProduct?.sellingPrice ?: 0.0, 0.001)
        assertEquals("Purchase price must remain confidential (0.0) for salesman", 0.0, salesmanFinalProduct?.purchasePrice ?: 0.0, 0.001)
    }

    // 10. Verify admin email ataur50305@gmail.com authorization and Cloud Submission schema integrity
    @Test
    fun testAdminEmailAuthorizationAndCloudSubmissionSchemaIntegrity() = runTest(testDispatcher) {
        val adminEmail = "ataurgonee4@gmail.com"
        assertTrue("CloudSyncManager must recognize ataurgonee4@gmail.com as authorized admin", CloudSyncManager.isAuthorizedAdminEmail(adminEmail))
        val adminRepo = BusinessRepository(adminDb)
        assertTrue("BusinessRepository must recognize ataurgonee4@gmail.com as authorized admin", adminRepo.isAuthorizedAdminEmail(adminEmail))

        // Verify Product Submission Cloud Document Map matches firestore.rules
        val testSub = ProductSubmission(
            id = "sub_schema_test_01",
            name = "ব্ল্যাক টি-শার্ট",
            productCode = "SKU-BLK-TSHIRT",
            category = "পোশাক",
            purchasePrice = 250.0,
            sellingPrice = 425.0,
            initialStock = 50.0,
            unit = "পিস",
            description = "১০০% কটন প্রিমিয়াম টি-শার্ট",
            salesmanId = salesmanId,
            salesmanName = "করিম আহমেদ",
            salesmanPhone = "01711000000",
            status = ProductSubmissionStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val cloudMap = CloudSyncManager.mapProductSubmissionToCloud(testSub, businessId)

        // Schema assertions matching firestore.rules isValidProductSubmission(data)
        assertEquals("sub_schema_test_01", cloudMap["id"])
        assertEquals("ব্ল্যাক টি-শার্ট", cloudMap["name"])
        assertEquals("PENDING", cloudMap["status"])
        assertEquals(businessId, cloudMap["businessId"])
        assertEquals(salesmanId, cloudMap["salesmanId"])
        assertEquals(250.0, (cloudMap["purchasePrice"] as Number).toDouble(), 0.001)
        assertEquals(425.0, (cloudMap["sellingPrice"] as Number).toDouble(), 0.001)
        assertEquals(50.0, (cloudMap["initialStock"] as Number).toDouble(), 0.001)

        // Admin applies remote submission map
        adminSyncManager.applyRemoteSubmissionMap(cloudMap)
        val adminReceived = adminDb.productSubmissionDao().getSubmissionById("sub_schema_test_01")
        assertNotNull("Admin Room database must receive submission", adminReceived)
        assertEquals(ProductSubmissionStatus.PENDING, adminReceived?.status)
        assertEquals(425.0, adminReceived?.sellingPrice ?: 0.0, 0.001)
    }

    // 11. Verify duplicate approval prevention and stock protection
    @Test
    fun testDuplicateApprovalRejectionAndStockProtection() = runTest(testDispatcher) {
        val adminRepo = BusinessRepository(adminDb)
        val adminUser = User(
            id = adminId,
            name = "প্রোপ্রাইটর",
            phone = "01700000000",
            role = UserRole.ADMIN,
            pinHash = "hash_1234"
        )
        adminDb.userDao().insertUser(adminUser)

        val sub = ProductSubmission(
            id = "sub_dup_protect_01",
            name = "লেদার মানিব্যাগ",
            productCode = "SKU-WALLET-01",
            category = "এক্সেসরিজ",
            purchasePrice = 300.0,
            sellingPrice = 550.0,
            initialStock = 15.0,
            unit = "পিস",
            description = "জেনুইন লেদার মানিব্যাগ",
            salesmanId = salesmanId,
            salesmanName = "করিম",
            salesmanPhone = "01711000000",
            status = ProductSubmissionStatus.PENDING
        )
        adminDb.productSubmissionDao().insertSubmission(sub)

        // First approval: should succeed and create product with initial stock
        val approvedProd = adminRepo.approveProductSubmission(sub.id, sub, adminUser)
        assertNotNull(approvedProd)
        val savedProd = adminDb.productDao().getProductById(approvedProd.id)
        assertNotNull(savedProd)
        assertEquals(15.0, savedProd?.currentStock ?: 0.0, 0.001)

        // Second approval attempt: MUST throw IllegalStateException preventing double-stock
        try {
            adminRepo.approveProductSubmission(sub.id, sub, adminUser)
            fail("Second approval attempt must throw IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("ইতিমধ্যে অনুমোদন করা হয়েছে") == true)
        }

        // Verify stock remains exactly 15.0 (no double addition)
        val finalProd = adminDb.productDao().getProductById(approvedProd.id)
        assertEquals("Stock must not be duplicated", 15.0, finalProd?.currentStock ?: 0.0, 0.001)
    }

    // 12. Verify all authorized master admin emails
    @Test
    fun testMasterAdminEmailsList() {
        val emails = listOf(
            "ataursek9@gmail.com",
            "ataurgonee4@gmail.com",
            "support@smartbiz.local",
            "gonee8666@gmail.com",
            "ataulgonee786@gmail.com",
            "ataurpos@gmail.com",
            "ataur50305@gmail.com",
            "a48268993@gmail.com"
        )
        for (email in emails) {
            assertTrue("Email $email must be recognized as authorized admin", CloudSyncManager.isAuthorizedAdminEmail(email))
        }
    }
}

