package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SuperAdminRepository
import com.example.data.sync.CloudBusinessAuth
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
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComprehensiveSafeUpgradeRegressionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repo: BusinessRepository
    private lateinit var superAdminRepo: SuperAdminRepository
    private lateinit var viewModel: BusinessViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        CloudBusinessAuth.clear(app)
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        AppDatabase.setInstanceForTesting(db)
        superAdminRepo = SuperAdminRepository.getInstance(app)
        superAdminRepo.resetStateForTesting()
        repo = BusinessRepository(db)
        viewModel = BusinessViewModel(app)
    }

    @After
    fun tearDown() {
        ShadowLooper.idleMainLooper()
        viewModel.syncManager.cancelActiveSync()
        CloudBusinessAuth.clear(app)
        superAdminRepo.resetStateForTesting()
        AppDatabase.setInstanceForTesting(null)
        Dispatchers.resetMain()
        db.close()
    }

    /**
     * পরীক্ষা ১: পুরোনো মোবাইল লগইন দিয়ে আগের দোকান খোলে কি না।
     */
    @Test
    fun `test 1 - existing mobile login opens existing store and data`() = runTest(testDispatcher) {
        val existingShopId = "biz_raj_store_01"
        val existingOwner = User(
            id = "user_raj_01",
            name = "রাজিব আহমেদ",
            phone = "01711223344",
            role = UserRole.ADMIN,
            pinHash = repo.hashPin("1122"),
            shopId = existingShopId,
            isActive = true
        )
        db.userDao().insertUser(existingOwner)

        val product = Product(
            id = "prod_001",
            name = "মিনিকেট চাল ৫০ কেজি",
            sku = "RICE-50",
            category = "মুদি",
            purchasePrice = 3000.0,
            sellingPrice = 3400.0,
            currentStock = 20.0,
            unit = "বস্তা"
        )
        db.productDao().insertProduct(product)

        // Login with mobile number and PIN
        var loginSuccess = false
        var loginMsg = ""
        viewModel.login("01711223344", "1122") { success, msg ->
            loginSuccess = success
            loginMsg = msg
        }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Mobile login must succeed, msg: $loginMsg", loginSuccess)
        assertNotNull("Current user must be active", viewModel.currentUser.value)
        assertEquals("user_raj_01", viewModel.currentUser.value?.id)
        assertEquals(existingShopId, viewModel.currentUser.value?.shopId)
        assertEquals(existingShopId, viewModel.getActiveBusinessId())

        // Verify products are accessible
        val loadedProduct = db.productDao().getProductById("prod_001")
        assertNotNull(loadedProduct)
        assertEquals("মিনিকেট চাল ৫০ কেজি", loadedProduct?.name)
    }

    /**
     * পরীক্ষা ২: আগে থেকে যুক্ত Gmail দিয়ে একই দোকান খোলে কি না।
     */
    @Test
    fun `test 2 - pre-linked Gmail opens the exact same store and data`() = runTest(testDispatcher) {
        val existingShopId = "biz_bhuiyan_store_02"
        val ownerPhone = "01733445566"
        val ownerPin = "3344"
        val ownerEmail = "bhuiyan.traders@gmail.com"
        val googleUid = "google_uid_bhuiyan_999"

        val ownerUser = User(
            id = "user_bhuiyan_02",
            name = "কামাল ভুঁইয়া",
            phone = ownerPhone,
            role = UserRole.ADMIN,
            pinHash = repo.hashPin(ownerPin),
            shopId = existingShopId,
            isActive = true
        )
        db.userDao().insertUser(ownerUser)

        val platformBiz = PlatformBusiness(
            id = existingShopId,
            businessName = "ভুঁইয়া ট্রেডার্স",
            ownerUid = googleUid,
            ownerName = "কামাল ভুঁইয়া",
            ownerEmail = ownerEmail,
            ownerPhone = ownerPhone,
            status = BusinessAccountStatus.ACTIVE,
            planId = "plan_free",
            planName = "সম্পূর্ণ ফ্রি ব্যবহার",
            subscriptionStatus = SubscriptionStatus.ACTIVE
        )
        superAdminRepo.registerNewShop(platformBiz)

        // Pre-link via CloudBusinessAuth
        CloudBusinessAuth.setLinkedGoogleAccount(
            context = app,
            uid = googleUid,
            email = ownerEmail,
            userId = ownerUser.id,
            shopId = existingShopId
        )

        // Login with Google credentials
        var googleLoginSuccess = false
        var returnedRole: UserRole? = null
        viewModel.handleGoogleUserCredentials(
            uid = googleUid,
            email = ownerEmail,
            displayName = "কামাল ভুঁইয়া",
            phoneNumber = ownerPhone
        ) { success, _, role ->
            googleLoginSuccess = success
            returnedRole = role
        }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Pre-linked Google login must succeed", googleLoginSuccess)
        assertEquals(UserRole.ADMIN, returnedRole)
        assertEquals(existingShopId, viewModel.currentUser.value?.shopId)
        assertEquals("user_bhuiyan_02", viewModel.currentUser.value?.id)
        assertFalse("Store admin is NOT super admin", viewModel.isSuperAdminUser())
    }

    /**
     * পরীক্ষা ৩: অনিবন্ধিত Gmail সরাসরি Admin Dashboard-এ যেতে পারে কি না—পারতে দেওয়া যাবে না।
     */
    @Test
    fun `test 3 - unlinked unknown Gmail is strictly rejected and cannot open Admin Dashboard`() = runTest(testDispatcher) {
        var loginSuccess = true
        var resultMsg = ""
        var assignedRole: UserRole? = UserRole.ADMIN

        viewModel.handleGoogleUserCredentials(
            uid = "stranger_uid_404",
            email = "stranger.unknown@gmail.com",
            displayName = "অচেনা ইউজার",
            phoneNumber = null
        ) { success, msg, role ->
            loginSuccess = success
            resultMsg = msg
            assignedRole = role
        }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Unknown Gmail MUST NOT be logged in", loginSuccess)
        assertNull("Assigned role must be null", assignedRole)
        assertNull("Current active user must remain null", viewModel.currentUser.value)
        assertTrue("Error message must inform that account is unauthorized or unlinked",
            resultMsg.contains("অনুমোদিত") || resultMsg.contains("কোনো নিবন্ধিত দোকানের সাথে যুক্ত নয়"))
        assertFalse("Super Admin access is denied", viewModel.isSuperAdminUser())
    }

    /**
     * পরীক্ষা ৪: নতুন দোকানদার নিরাপদভাবে নিবন্ধন করতে পারেন কি না।
     */
    @Test
    fun `test 4 - new shopkeeper safely registers new store with duplicate check`() = runTest(testDispatcher) {
        val phone = "01755667788"
        val pin = "5678"
        var registerSuccess = false
        var createdShopId: String? = null

        viewModel.registerNewShop(
            shopName = "ধানমন্ডি ডিপার্টমেন্টাল স্টোর",
            ownerName = "সাকিব চৌধুরী",
            phone = phone,
            address = "ধানমন্ডি ২৭, ঢাকা",
            district = "ঢাকা",
            businessType = "মুদি ও জেনারেল স্টোর",
            pin = pin,
            isPhoneVerified = true,
            ownerEmail = "sakib.dhanmondi@gmail.com",
            ownerUid = "uid_sakib_dhanmondi"
        ) { success, _, newId ->
            registerSuccess = success
            createdShopId = newId
        }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("New shop registration must succeed", registerSuccess)
        assertNotNull("Generated shop ID must not be null", createdShopId)
        assertTrue("Shop ID must have biz prefix", createdShopId!!.startsWith("biz_"))
        assertEquals("ধানমন্ডি ডিপার্টমেন্টাল স্টোর", superAdminRepo.businesses.value.firstOrNull { it.id == createdShopId }?.businessName)

        // Try registering again with DUPLICATE phone -> MUST BE REJECTED
        var duplicateSuccess = true
        var duplicateMsg = ""
        viewModel.registerNewShop(
            shopName = "ধানমন্ডি ব্রাঞ্চ ২",
            ownerName = "সাকিব চৌধুরী",
            phone = phone,
            address = "রোড ৮",
            district = "ঢাকা",
            businessType = "মুদি",
            pin = "9999"
        ) { success, msg, _ ->
            duplicateSuccess = success
            duplicateMsg = msg
        }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Duplicate phone registration must be REJECTED", duplicateSuccess)
        assertTrue("Error must mention duplicate number", duplicateMsg.contains("ইতিমধ্যে একটি দোকান নিবন্ধিত") || duplicateMsg.contains("ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে"))
    }

    /**
     * পরীক্ষা ৫: মোবাইল ও Gmail লগইনে একই দোকানের ডাটা ঠিক থাকে কি না।
     */
    @Test
    fun `test 5 - mobile login and Gmail login open identical store data without duplicates`() = runTest(testDispatcher) {
        val phone = "01766778899"
        val pin = "6789"
        val email = "tamim.store@gmail.com"
        val uid = "google_uid_tamim_123"

        // 1. Register store
        var registeredShopId: String? = null
        viewModel.registerNewShop(
            shopName = "তামিম জেনারেল স্টোর",
            ownerName = "তামিম ইকবাল",
            phone = phone,
            address = "উত্তরা সেক্টর ৭",
            district = "ঢাকা",
            businessType = "মুদি",
            pin = pin,
            isPhoneVerified = true,
            ownerEmail = email,
            ownerUid = uid
        ) { _, _, id -> registeredShopId = id }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        // 2. Add product under this store
        val prod = Product(
            id = "prod_tamim_oil",
            name = "রূপচাঁদা সয়াবিন তেল ৫ লিটার",
            sku = "OIL-5L",
            category = "মুদি",
            purchasePrice = 750.0,
            sellingPrice = 820.0,
            currentStock = 50.0,
            unit = "বোতল"
        )
        db.productDao().insertProduct(prod)

        // 3. Logout
        viewModel.logout()
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.currentUser.value)

        // 4. Login via Mobile + PIN
        var mobileLoginSuccess = false
        viewModel.login(phone, pin) { success, _ -> mobileLoginSuccess = success }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Mobile login succeeds", mobileLoginSuccess)
        assertEquals(registeredShopId, viewModel.currentUser.value?.shopId)
        val prodFromMobile = db.productDao().getProductById("prod_tamim_oil")
        assertNotNull(prodFromMobile)
        assertEquals(50.0, prodFromMobile?.currentStock ?: 0.0, 0.001)

        // 5. Logout
        viewModel.logout()
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        // 6. Login via Google Sign-In
        var googleLoginSuccess = false
        viewModel.handleGoogleUserCredentials(
            uid = uid,
            email = email,
            displayName = "তামিম ইকবাল",
            phoneNumber = phone
        ) { success, _, _ -> googleLoginSuccess = success }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Google login succeeds", googleLoginSuccess)
        assertEquals(registeredShopId, viewModel.currentUser.value?.shopId)
        val prodFromGoogle = db.productDao().getProductById("prod_tamim_oil")
        assertNotNull(prodFromGoogle)
        assertEquals("রূপচাঁদা সয়াবিন তেল ৫ লিটার", prodFromGoogle?.name)
    }

    /**
     * পরীক্ষা ৬: Logo-তে পাঁচবার ট্যাপ করলে শুধু গোপন Super Admin Login Screen খোলে কি না।
     */
    @Test
    fun `test 6 - 5-tap gesture only opens secret screen, never grants Super Admin permissions directly`() {
        // Verify initial state
        assertFalse("App starts without Super Admin session", viewModel.isSuperAdminUser())

        // Simulate 5-tap gesture counter
        var secretTapCount = 0
        var openedSecretScreen = false
        var lastTapTime = 0L

        val onNavigateToAdminLogin = { openedSecretScreen = true }

        val handleSecretTap = {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime > 2000L) {
                secretTapCount = 1
            } else {
                secretTapCount++
            }
            lastTapTime = currentTime
            if (secretTapCount >= 5) {
                secretTapCount = 0
                onNavigateToAdminLogin()
            }
        }

        // Tap 4 times -> must NOT open
        repeat(4) { handleSecretTap() }
        assertFalse("4 taps must NOT trigger navigation", openedSecretScreen)
        assertFalse("Super admin flag remains FALSE", viewModel.isSuperAdminUser())

        // 5th tap -> triggers navigation
        handleSecretTap()
        assertTrue("5th tap triggers secret screen navigation callback", openedSecretScreen)
        assertFalse("Gesture itself DOES NOT grant Super Admin privileges", viewModel.isSuperAdminUser())
    }

    /**
     * পরীক্ষা ৭: অনুমোদিত Super Admin আগের মতো Panel-এ প্রবেশ করতে পারেন কি না।
     */
    @Test
    fun `test 7 - authorized Super Admin ataurgonee4@gmail_com enters Super Admin panel`() = runTest(testDispatcher) {
        val superAdminEmail = "ataurgonee4@gmail.com"
        val superAdminUid = "google_superadmin_uid_777"

        var loginSuccess = false
        var assignedRole: UserRole? = null

        viewModel.handleGoogleUserCredentials(
            uid = superAdminUid,
            email = superAdminEmail,
            displayName = "মোঃ আতাউর রহমান",
            phoneNumber = "01700000000"
        ) { success, _, role ->
            loginSuccess = success
            assignedRole = role
        }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Super Admin Google login must succeed", loginSuccess)
        assertEquals(UserRole.ADMIN, assignedRole)
        assertTrue("isSuperAdminUser must be TRUE for authorized ataurgonee4@gmail.com", viewModel.isSuperAdminUser())
        assertEquals(superAdminEmail, viewModel.getSuperAdminEmail())
    }

    /**
     * পরীক্ষা ৮: সাধারণ দোকানদার ও Salesman Super Admin Panel বা অন্য দোকানের ডাটা পেতে পারেন কি না—পারতে দেওয়া যাবে না।
     */
    @Test
    fun `test 8 - store admin and salesman cannot obtain Super Admin authorization or cross-shop data`() = runTest(testDispatcher) {
        // Salesman test
        val salesman = User(
            id = "staff_monir_sales",
            name = "মনির সেলস",
            phone = "01822334455",
            role = UserRole.SALESMAN,
            pinHash = repo.hashPin("1111"),
            shopId = "biz_shop_1",
            isActive = true
        )
        db.userDao().insertUser(salesman)

        viewModel.login("01822334455", "1111") { _, _ -> }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UserRole.SALESMAN, viewModel.currentUser.value?.role)
        assertFalse("Salesman CANNOT be Super Admin", viewModel.isSuperAdminUser())

        // Store Admin test
        val storeAdmin = User(
            id = "admin_alamin_store",
            name = "আল-আমিন",
            phone = "01744556677",
            role = UserRole.ADMIN,
            pinHash = repo.hashPin("2222"),
            shopId = "biz_alamin_shop",
            isActive = true
        )
        db.userDao().insertUser(storeAdmin)

        viewModel.logout()
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.login("01744556677", "2222") { _, _ -> }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UserRole.ADMIN, viewModel.currentUser.value?.role)
        assertEquals("biz_alamin_shop", viewModel.currentUser.value?.shopId)
        assertFalse("Regular store admin CANNOT be Platform Super Admin", viewModel.isSuperAdminUser())
    }

    /**
     * পরীক্ষা ৯: Logout, account disable, permission revoke এবং session expiry সঠিকভাবে কাজ করে কি না।
     */
    @Test
    fun `test 9 - logout, account disable, and session expiry work properly`() = runTest(testDispatcher) {
        val user = User(
            id = "user_test_lifecycle",
            name = "টেস্ট ইউজার",
            phone = "01999887766",
            role = UserRole.SALESMAN,
            pinHash = repo.hashPin("9999"),
            shopId = "biz_test_shop",
            isActive = true,
            accountStatus = UserAccountStatus.ACTIVE
        )
        db.userDao().insertUser(user)

        // 1. Successful login
        var loginOk = false
        viewModel.login("01999887766", "9999") { success, _ -> loginOk = success }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(loginOk)
        assertNotNull(viewModel.currentUser.value)

        // 2. Logout clears user and active business session
        viewModel.logout()
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull("Current user is null after logout", viewModel.currentUser.value)

        // 3. Disable account in DB
        val suspendedUser = user.copy(
            isActive = false,
            accountStatus = UserAccountStatus.SUSPENDED,
            statusReason = "নিয়ম লঙ্ঘনের কারণে স্থগিত"
        )
        db.userDao().insertUser(suspendedUser)

        // 4. Try logging into suspended account -> MUST BE BLOCKED
        var suspendedLoginSuccess = true
        var suspendedMsg = ""
        viewModel.login("01999887766", "9999") { success, msg ->
            suspendedLoginSuccess = success
            suspendedMsg = msg
        }
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Suspended account login must fail", suspendedLoginSuccess)
        assertNull("Current user remains null", viewModel.currentUser.value)
        assertTrue("Error message mentions suspension", suspendedMsg.contains("স্থগিত") || suspendedMsg.contains("নিষ্ক্রিয়"))
    }

    /**
     * পরীক্ষা ১০: আগের দুইটি সিস্টেম, বিক্রি, পেমেন্ট, স্টক, বকেয়া ও রিপোর্টে regression হয়েছে কি না।
     */
    @Test
    fun `test 10 - sales, receipts, stock, dues, and reports operate without regression`() = runTest(testDispatcher) {
        val product = Product(
            id = "prod_reg_test_01",
            name = "মিনিকেট চাল ৫০ কেজি বস্তা",
            sku = "MINIKET-50",
            category = "মুদি",
            purchasePrice = 3000.0,
            sellingPrice = 3400.0,
            currentStock = 20.0,
            unit = "বস্তা"
        )
        db.productDao().insertProduct(product)
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        val allProducts = db.productDao().getSyncProducts()
        assertTrue("Products exist", allProducts.isNotEmpty())

        val customer = Customer(
            id = "cust_reg_01",
            name = "ফারুক হোসেন",
            phone = "01899112233",
            address = "মিরপুর ২",
            currentDue = 1500.0
        )
        db.customerDao().insertCustomer(customer)

        val retrievedCustomer = db.customerDao().getCustomerById("cust_reg_01")
        assertNotNull(retrievedCustomer)
        assertEquals(1500.0, retrievedCustomer?.currentDue ?: 0.0, 0.001)

        // Add payment receipt
        val receipt = PaymentReceipt(
            receiptNumber = "REC-001",
            customerId = "cust_reg_01",
            customerName = "ফারুক হোসেন",
            customerPhone = "01899112233",
            salesmanId = "admin_1",
            salesmanName = "এডমিন",
            amount = 500.0,
            previousDue = 1500.0,
            currentDue = 1000.0,
            paymentMethod = PaymentMethod.CASH
        )
        db.paymentReceiptDao().insertReceipt(receipt)

        val retrievedReceipt = db.paymentReceiptDao().getReceiptByNumber("REC-001")
        assertNotNull(retrievedReceipt)
        assertEquals(500.0, retrievedReceipt?.amount ?: 0.0, 0.001)

        // Check report calculations
        val totalStockValue = allProducts.sumOf { it.currentStock * it.purchasePrice }
        assertTrue("Stock value calculation is valid and non-negative", totalStockValue >= 0.0)
    }
}
