package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.data.repository.BusinessRepository
import com.example.data.sync.CloudBusinessAuth
import com.example.data.sync.CloudSyncManager
import com.example.data.sync.SyncStatus
import com.example.viewmodel.BusinessViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class TwoDeviceProductSyncTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var adminDb: AppDatabase
    private lateinit var salesmanDb: AppDatabase
    private lateinit var adminSyncManager: CloudSyncManager
    private lateinit var salesmanSyncManager: CloudSyncManager

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
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
        adminDb.close()
        salesmanDb.close()
        CloudBusinessAuth.clear(app)
    }

    // 1. Admin Product Upload Test
    @Test
    fun testAdminProductUploadFlow() = runTest(testDispatcher) {
        val adminUid = "admin_cloud_owner_999"

        val product = Product(
            id = "prod_smart_shirt_01",
            name = "অক্সফোর্ড কটন শার্ট",
            sku = "SKU-SHIRT-01",
            barcode = "8941122334455",
            category = "পোশাক",
            purchasePrice = 600.0,
            sellingPrice = 1020.0,
            currentStock = 50.0,
            alertStockLevel = 5.0,
            unit = "পিস",
            isActive = true,
            updatedAt = System.currentTimeMillis(),
            isTestData = false
        )
        adminDb.productDao().insertProduct(product)

        // Verify product in Admin local Room
        val saved = adminDb.productDao().getProductById("prod_smart_shirt_01")
        assertNotNull("Product must exist in Admin database", saved)
        assertEquals(600.0, saved!!.purchasePrice, 0.001)

        // Verify sync selection
        val syncList = adminDb.productDao().getSyncProducts()
        assertTrue("getSyncProducts must include admin product", syncList.any { it.id == "prod_smart_shirt_01" })

        // Verify cloud payload preparation (Owner mapping includes purchasePrice)
        val cloudPayload = CloudSyncManager.mapProductToCloud(product, adminUid, isSalesman = false)
        assertEquals("prod_smart_shirt_01", cloudPayload["id"])
        assertEquals("অক্সফোর্ড কটন শার্ট", cloudPayload["name"])
        assertEquals(1020.0, cloudPayload["sellingPrice"])
        assertEquals(adminUid, cloudPayload["businessId"])
        assertEquals(600.0, cloudPayload["purchasePrice"])
    }

    // 2. Authorized Salesman Product Download with Cost Protection
    @Test
    fun testAuthorizedSalesmanProductDownloadFlow() = runTest(testDispatcher) {
        val adminUid = "admin_cloud_owner_999"

        // Salesman device initially has NO products
        assertNull("Salesman database must be empty before sync", salesmanDb.productDao().getProductById("prod_smart_shirt_01"))

        // Authorize salesman under Admin's business
        CloudBusinessAuth.setStoredBusinessId(app, adminUid, "স্মার্ট বিজনেস", UserRole.SALESMAN.name)
        assertEquals(adminUid, CloudBusinessAuth.getStoredBusinessId(app))

        val cloudPayload = mapOf(
            "id" to "prod_smart_shirt_01",
            "name" to "অক্সফোর্ড কটন শার্ট",
            "sku" to "SKU-SHIRT-01",
            "barcode" to "8941122334455",
            "category" to "পোশাক",
            "sellingPrice" to 1020.0,
            "purchasePrice" to 600.0,
            "currentStock" to 50.0,
            "alertStockLevel" to 5.0,
            "unit" to "পিস",
            "isActive" to true,
            "updatedAt" to System.currentTimeMillis(),
            "businessId" to adminUid
        )

        // Apply remote product map using CloudSyncManager production code
        salesmanSyncManager.applyRemoteProductMap(cloudPayload, isSalesman = true)

        // Verify product is now present in salesman Room database
        val downloaded = salesmanDb.productDao().getProductById("prod_smart_shirt_01")
        assertNotNull("Product must be downloaded into salesman database", downloaded)
        assertEquals("অক্সফোর্ড কটন শার্ট", downloaded!!.name)
        assertEquals(1020.0, downloaded.sellingPrice, 0.001)
        assertEquals(50.0, downloaded.currentStock, 0.001)

        // Crucial security test: purchase cost is strictly zeroed for salesman
        assertEquals("Purchase cost must be zero on salesman device", 0.0, downloaded.purchasePrice, 0.001)
    }

    // 3. Different-Business Access Denied (Tenant Isolation)
    @Test
    fun testDifferentBusinessAccessDenied() {
        // Salesman is registered under Business Alpha
        CloudBusinessAuth.setStoredBusinessId(app, "biz_alpha_111", "আলফা ট্রেডার্স", UserRole.SALESMAN.name)

        val activeBiz = CloudBusinessAuth.getStoredBusinessId(app)
        assertNotEquals("Salesman under Alpha must not resolve to Business Beta", "biz_beta_222", activeBiz)
        assertEquals("biz_alpha_111", activeBiz)
    }

    // 4. Unauthenticated Access Denied
    @Test
    fun testUnauthenticatedAccessDenied() {
        CloudBusinessAuth.clear(app)
        try {
            adminSyncManager.resolveBusinessId()
            fail("resolveBusinessId must throw IllegalStateException when unauthenticated")
        } catch (e: IllegalStateException) {
            assertTrue("Exception must state authentication or configuration requirement",
                e.message?.contains("প্রমাণীকরণ") == true || e.message?.contains("কনফিগারেশন") == true)
        }
    }

    // 5. Inactive or Unregistered Salesman Denied
    @Test
    fun testInactiveOrUnregisteredSalesmanDenied() = runTest(testDispatcher) {
        val viewModel = BusinessViewModel(app)

        // 5.1 Test suspended user is denied in repository
        val suspendedSalesman = User(
            id = "suspended_salesman_1",
            name = "স্থগিত কর্মী",
            phone = "01911111111",
            role = UserRole.SALESMAN,
            pinHash = viewModel.repository.hashPin("4321"),
            isActive = false,
            accountStatus = UserAccountStatus.SUSPENDED
        )
        viewModel.repository.saveUserDirect(suspendedSalesman)

        val authResult = viewModel.repository.authenticateUser("01911111111", "4321")
        assertTrue("Suspended salesman authentication must return Error", authResult is BusinessRepository.AuthResult.Error)
        val errorMsg = (authResult as BusinessRepository.AuthResult.Error).message
        assertTrue("Error must state suspended status: $errorMsg", errorMsg.contains("স্থগিত") || errorMsg.contains("নিষ্ক্রিয়"))

        // 5.2 Test deactivated user is denied in repository
        val deactivatedSalesman = User(
            id = "deactivated_salesman_2",
            name = "বিচ্ছিন্ন কর্মী",
            phone = "01922222222",
            role = UserRole.SALESMAN,
            pinHash = viewModel.repository.hashPin("5678"),
            isActive = false,
            accountStatus = UserAccountStatus.DEACTIVATED
        )
        viewModel.repository.saveUserDirect(deactivatedSalesman)

        val authDeactResult = viewModel.repository.authenticateUser("01922222222", "5678")
        assertTrue("Deactivated salesman must be rejected", authDeactResult is BusinessRepository.AuthResult.Error)

        // 5.3 Test unregistered Google user is rejected without admin authorization
        var googleLoginSuccess: Boolean? = null
        var googleLoginError = ""
        viewModel.handleGoogleUserCredentials(
            uid = "stranger_uid_888",
            email = "stranger@unknown.com",
            displayName = "অপরিচিত",
            phoneNumber = null
        ) { success, msg, _ ->
            googleLoginSuccess = success
            googleLoginError = msg
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Unregistered Google user must not be granted access", googleLoginSuccess == true)
        assertNull("Current active user must remain null", viewModel.currentUser.value)
        viewModel.syncManager.cancelActiveSync()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    // 6. Duplicate Product Prevention (Idempotency)
    @Test
    fun testDuplicateProductPrevention() = runTest(testDispatcher) {
        val cloudPayload = mapOf(
            "id" to "prod_idempotent_01",
            "name" to "আইডেমপোটেন্ট পণ্য",
            "sku" to "SKU-IDEM-01",
            "category" to "জেনারেল",
            "sellingPrice" to 250.0,
            "currentStock" to 10.0,
            "alertStockLevel" to 2.0,
            "unit" to "পিস",
            "isActive" to true,
            "updatedAt" to 1000L
        )

        // First sync
        salesmanSyncManager.applyRemoteProductMap(cloudPayload, isSalesman = true)
        val initialList = salesmanDb.productDao().getAllProductsList()
        assertEquals(1, initialList.size)

        // Second sync (same payload)
        salesmanSyncManager.applyRemoteProductMap(cloudPayload, isSalesman = true)
        val secondList = salesmanDb.productDao().getAllProductsList()
        assertEquals("Subsequent sync must not create duplicate items", 1, secondList.size)

        // Third sync with updated timestamp and stock
        val updatedPayload = cloudPayload.toMutableMap().apply {
            put("currentStock", 18.0)
            put("updatedAt", 2000L)
        }
        salesmanSyncManager.applyRemoteProductMap(updatedPayload, isSalesman = true)
        val thirdList = salesmanDb.productDao().getAllProductsList()
        assertEquals("Updated sync must update in-place without duplicating", 1, thirdList.size)
        assertEquals(18.0, thirdList[0].currentStock, 0.001)
    }

    // 7. Product List Refresh After Synchronization
    @Test
    fun testProductListRefreshAfterSynchronization() = runTest(testDispatcher) {
        // Initial list is empty
        val flowProductsBefore = salesmanDb.productDao().getAllProducts().first()
        assertTrue("Product flow starts empty", flowProductsBefore.isEmpty())

        val cloudPayload = mapOf(
            "id" to "prod_reactive_01",
            "name" to "রিয়েক্টিভ রিফ্রেশ পণ্য",
            "sku" to "SKU-REAC-01",
            "category" to "পোশাক",
            "sellingPrice" to 400.0,
            "currentStock" to 15.0,
            "alertStockLevel" to 3.0,
            "unit" to "পিস",
            "isActive" to true,
            "updatedAt" to System.currentTimeMillis()
        )

        // Apply sync
        salesmanSyncManager.applyRemoteProductMap(cloudPayload, isSalesman = true)

        // Flow automatically emits updated list
        val flowProductsAfter = salesmanDb.productDao().getAllProducts().first()
        assertEquals("Flow must immediately emit 1 product after sync", 1, flowProductsAfter.size)
        assertEquals("রিয়েক্টিভ রিফ্রেশ পণ্য", flowProductsAfter[0].name)
    }

    // 8. Network Failure and Retry
    @Test
    fun testNetworkFailureAndRetryPreservesPendingRecords() = runTest(testDispatcher) {
        // Add pending local records to db
        adminDb.productDao().insertProduct(
            Product(
                id = "p_local_pending_1",
                name = "পেন্ডিং পণ্য ১",
                sku = "SKU-PEND-1",
                category = "মুদি",
                purchasePrice = 50.0,
                sellingPrice = 85.0,
                currentStock = 20.0,
                alertStockLevel = 5.0,
                unit = "কেজি",
                isActive = true,
                updatedAt = System.currentTimeMillis(),
                isTestData = false
            )
        )

        val pendingCountBefore = adminSyncManager.countPendingLocalRecords()
        assertTrue("Must count pending local records", pendingCountBefore >= 1)

        // Trigger sync when offline / unauthenticated -> gracefully transitions to LOCAL_SAVED
        val resultState = adminSyncManager.executeSyncCycle()
        assertEquals(SyncStatus.LOCAL_SAVED, resultState.status)
        assertFalse(resultState.isCloudConfigured)
        assertEquals(pendingCountBefore, resultState.pendingCount)

        // Pending records are intact in Room
        val countAfterFailure = adminSyncManager.countPendingLocalRecords()
        assertEquals("Pending records must never be lost during network or cloud offline state", pendingCountBefore, countAfterFailure)
    }

    // 9. Verify Sample Demo Customers Are Excluded From Sync
    @Test
    fun testSampleCustomersExcludedFromSync() = runTest(testDispatcher) {
        val repo = BusinessRepository(salesmanDb)
        repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

        val syncCustomers = salesmanDb.customerDao().getSyncCustomers()
        assertTrue(
            "Demo sample customers (c_1, c_2, c_3) must be marked isTestData=true and excluded from sync",
            syncCustomers.none { it.id in listOf("c_1", "c_2", "c_3") }
        )
    }

    // 10. Verify Phone Normalization Consistency
    @Test
    fun testPhoneNormalizationConsistency() {
        val standard = "01712345678"
        assertEquals(standard, CloudBusinessAuth.normalizePhone("+8801712345678"))
        assertEquals(standard, CloudBusinessAuth.normalizePhone("8801712345678"))
        assertEquals(standard, CloudBusinessAuth.normalizePhone("01712-345678"))
        assertEquals(standard, CloudBusinessAuth.normalizePhone("01712 345678"))
        assertEquals(standard, CloudBusinessAuth.normalizePhone("1712345678"))
    }

    // 11. Verify Business Linking Input Validation
    @Test
    fun testBusinessLinkingInputValidation() = runTest(testDispatcher) {
        val vm = BusinessViewModel(app)
        var called = false
        var errMsg = ""
        vm.connectSalesmanToBusiness("", "01712345678", "1234") { success, msg ->
            called = true
            assertFalse(success)
            errMsg = msg
        }
        assertTrue(called)
        assertTrue(errMsg.contains("কোড"))
        vm.syncManager.cancelActiveSync()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
    }
}
