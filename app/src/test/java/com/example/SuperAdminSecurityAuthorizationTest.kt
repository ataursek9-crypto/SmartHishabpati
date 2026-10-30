package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.BusinessAccountStatus
import com.example.data.model.PlatformBusiness
import com.example.data.model.SubscriptionStatus
import com.example.data.model.User
import com.example.data.model.UserAccountStatus
import com.example.data.model.UserRole
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SuperAdminRepository
import com.example.data.sync.CloudBusinessAuth
import com.example.viewmodel.BusinessViewModel
import com.example.viewmodel.SuperAdminViewModel
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
class SuperAdminSecurityAuthorizationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repo: BusinessRepository
    private lateinit var superAdminRepo: SuperAdminRepository
    private lateinit var viewModel: BusinessViewModel
    private lateinit var superAdminViewModel: SuperAdminViewModel

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
        superAdminViewModel = SuperAdminViewModel(app, superAdminRepo)
    }

    @After
    fun tearDown() {
        try {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            if (::viewModel.isInitialized) {
                viewModel.syncManager.cancelActiveSync()
            }
            if (::app.isInitialized) {
                CloudBusinessAuth.clear(app)
            }
            if (::superAdminRepo.isInitialized) {
                superAdminRepo.resetStateForTesting()
            }
            AppDatabase.setInstanceForTesting(null)
            if (::db.isInitialized && db.isOpen) {
                db.close()
            }
        } catch (_: Throwable) {}
        try {
            Dispatchers.resetMain()
        } catch (_: Throwable) {}
    }

    /**
     * Scenario 1: অনুমোদিত Platform Super Admin (ataurgonee4@gmail.com, 01700000000) লগইন করলে বিদ্যমান অ্যাডমিন অ্যাকাউন্ট ম্যাপ হয়, Super Admin বাটন দেখা যায় এবং প্যানেল খোলে।
     */
    @Test
    fun `scenario 1 - authorized Super Admin ataurgonee4@gmail_com Google login enables Super Admin button and maps existing account`() = runTest(testDispatcher) {
        val superAdminEmail = "ataurgonee4@gmail.com"
        assertTrue("Email must be in authorized super admin list", SuperAdminRepository.isSuperAdminEmail(superAdminEmail))

        // Pre-populate seed admin_1 with phone 01700000000
        val existingAdmin = User(
            id = "admin_1",
            name = "মোঃ আতাউর রহমান",
            phone = "01700000000",
            role = UserRole.ADMIN,
            shopId = "biz_smart_store_1",
            pinHash = repo.hashPin("1234"),
            isActive = true,
            accountStatus = UserAccountStatus.ACTIVE
        )
        repo.saveUserDirect(existingAdmin)

        var loginSuccess = false
        var assignedRole: UserRole? = null

        viewModel.handleGoogleUserCredentials(
            uid = "firebase_uid_ataurgonee4",
            email = superAdminEmail,
            displayName = "মোঃ আতাউর রহমান",
            phoneNumber = "01700000000"
        ) { success, _, role ->
            loginSuccess = success
            assignedRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Super admin login must succeed", loginSuccess)
        assertEquals("Super admin is assigned ADMIN role", UserRole.ADMIN, assignedRole)
        assertTrue("Super Admin button must be shown (isSuperAdminUser == true)", viewModel.isSuperAdminUser())
        assertEquals("getSuperAdminEmail must return authorized email", superAdminEmail, viewModel.getSuperAdminEmail())
        assertTrue("SuperAdminRepository validates super admin email", SuperAdminRepository.isSuperAdminEmail(viewModel.getSuperAdminEmail()))
        assertEquals("Shop ID must map to existing primary business", "biz_smart_store_1", viewModel.currentUser.value?.shopId)
        assertEquals("Phone number must match existing account", "01700000000", viewModel.currentUser.value?.phone)
    }

    /**
     * Scenario 1B: সঠিক Super Admin authorization ছাড়া একই ফোন বা অননুমোদিত জিমেইল কোনোভাবেই Super Admin হতে পারে না।
     */
    @Test
    fun `scenario 1B - unauthorized Google email cannot gain Super Admin access even with admin phone`() = runTest(testDispatcher) {
        val unauthorizedEmail = "fake.admin@gmail.com"
        assertFalse("Unauthorized email must NOT be in super admin allowlist", SuperAdminRepository.isSuperAdminEmail(unauthorizedEmail))

        var loginSuccess = false
        viewModel.handleGoogleUserCredentials(
            uid = "fake_admin_uid_99",
            email = unauthorizedEmail,
            displayName = "ভুয়া অ্যাডমিন",
            phoneNumber = "01700000000"
        ) { success, _, _ ->
            loginSuccess = success
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        // CRITICAL CHECK: Super Admin permission must be refused
        assertFalse(
            "Unauthorized account must NEVER be granted Super Admin access!",
            viewModel.isSuperAdminUser()
        )
        assertEquals("", viewModel.getSuperAdminEmail())
    }

    /**
     * Scenario 2: নতুন Google দিয়ে দোকান রেজিস্ট্রেশন করলে Super Admin বাটন দেখা যায় না।
     */
    @Test
    fun `scenario 2 - newly registered shop owner NEVER sees Super Admin button`() = runTest(testDispatcher) {
        var registerSuccess = false
        var newShopId: String? = null

        viewModel.registerNewShop(
            shopName = "মিরপুর বস্ত্র বিতান",
            ownerName = "মোহাম্মদ রফিক",
            phone = "01712345678",
            address = "মিরপুর ১, ঢাকা",
            district = "ঢাকা",
            businessType = "বস্ত্র ও গার্মেন্টস",
            pin = "7890",
            isPhoneVerified = true
        ) { success, _, shopId ->
            registerSuccess = success
            newShopId = shopId
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Registration must succeed", registerSuccess)
        assertNotNull("ShopId must be generated", newShopId)

        val currentUser = viewModel.currentUser.value
        assertNotNull("Current user must be set", currentUser)
        assertEquals(UserRole.ADMIN, currentUser!!.role)
        assertEquals(newShopId, currentUser.shopId)

        // CRITICAL CHECK: Super Admin button must NEVER be visible for new shop owner
        assertFalse(
            "New shopkeeper must NEVER have Super Admin button enabled!",
            viewModel.isSuperAdminUser()
        )
        assertEquals("getSuperAdminEmail must return empty string for new shop owner", "", viewModel.getSuperAdminEmail())
    }

    /**
     * Scenario 3: সাধারণ দোকানের Admin লগইন করলে Super Admin বাটন দেখা যায় না।
     */
    @Test
    fun `scenario 3 - regular shop store admin login does NOT see Super Admin button`() = runTest(testDispatcher) {
        val storeAdminPinHash = repo.hashPin("1234")
        val storeAdminUser = User(
            id = "store_admin_001",
            name = "দোকান অ্যাডমিন",
            phone = "01711223344",
            role = UserRole.ADMIN,
            shopId = "biz_store_9999",
            pinHash = storeAdminPinHash,
            isActive = true,
            accountStatus = UserAccountStatus.ACTIVE
        )
        repo.saveUserDirect(storeAdminUser)

        var loginSuccess = false
        viewModel.loginAdmin("01711223344", "1234") { success, _ ->
            loginSuccess = success
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Store admin login must succeed", loginSuccess)
        assertEquals(UserRole.ADMIN, viewModel.currentUser.value?.role)
        assertEquals("biz_store_9999", viewModel.currentUser.value?.shopId)

        // CRITICAL: Regular shop admin must NOT see Super Admin button
        assertFalse(
            "Regular store admin must NOT see Platform Super Admin button!",
            viewModel.isSuperAdminUser()
        )
        assertEquals("", viewModel.getSuperAdminEmail())
    }

    /**
     * Scenario 4: Salesman লগইন করলে Super Admin বাটন দেখা যায় না।
     */
    @Test
    fun `scenario 4 - salesman login does NOT see Super Admin button`() = runTest(testDispatcher) {
        val salesmanPinHash = repo.hashPin("4321")
        val salesmanUser = User(
            id = "salesman_001",
            name = "সেলসম্যান জামান",
            phone = "01855667788",
            role = UserRole.SALESMAN,
            shopId = "biz_store_9999",
            pinHash = salesmanPinHash,
            isActive = true,
            accountStatus = UserAccountStatus.ACTIVE
        )
        repo.saveUserDirect(salesmanUser)

        var loginSuccess = false
        viewModel.login("01855667788", "4321") { success, _ ->
            loginSuccess = success
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Salesman login must succeed", loginSuccess)
        assertEquals(UserRole.SALESMAN, viewModel.currentUser.value?.role)

        // CRITICAL: Salesman must NEVER see Super Admin button
        assertFalse(
            "Salesman must NEVER see Super Admin button!",
            viewModel.isSuperAdminUser()
        )
        assertEquals("", viewModel.getSuperAdminEmail())
    }

    /**
     * Scenario 5: সাধারণ ব্যবহারকারী সরাসরি Super Admin screen বা protected function খোলার চেষ্টা করলে access denied হয়।
     */
    @Test
    fun `scenario 5 - unauthorized user calling protected Super Admin repository functions is denied`() = runTest(testDispatcher) {
        val regularUserEmail = "regular.owner@gmail.com"
        assertFalse(SuperAdminRepository.isSuperAdminEmail(regularUserEmail))

        val businesses = superAdminRepo.businesses.value
        assertTrue("Platform businesses must exist", businesses.isNotEmpty())
        val target = businesses.first()

        // 1. Try to suspend business with unauthorized email
        val suspendResult = superAdminRepo.updateBusinessStatus(
            businessId = target.id,
            newStatus = BusinessAccountStatus.SUSPENDED,
            reason = "অবৈধ স্থগিতের চেষ্টা",
            actorUid = "unauth_user",
            actorName = "অসাধু ব্যবহারকারী",
            actorEmail = regularUserEmail
        )
        assertTrue("Attempt to update business status by unauthorized email must FAIL", suspendResult.isFailure)
        assertTrue("Exception must be SecurityException", suspendResult.exceptionOrNull() is SecurityException)

        // 2. Try to change business plan with unauthorized email
        val planResult = superAdminRepo.updateBusinessPlan(
            businessId = target.id,
            planId = "plan_enterprise",
            durationDays = 30,
            actorUid = "unauth_user",
            actorName = "অসাধু ব্যবহারকারী",
            actorEmail = regularUserEmail
        )
        assertTrue("Attempt to change plan by unauthorized email must FAIL", planResult.isFailure)
        assertTrue("Exception must be SecurityException", planResult.exceptionOrNull() is SecurityException)

        // 3. Try to review payment with unauthorized email
        val reviewResult = superAdminRepo.reviewPayment(
            paymentId = "pay_test_01",
            isApproved = true,
            rejectionReason = "",
            actorUid = "unauth_user",
            actorName = "অসাধু ব্যবহারকারী",
            actorEmail = regularUserEmail
        )
        assertTrue("Attempt to approve payment by unauthorized email must FAIL", reviewResult.isFailure)
        assertTrue("Exception must be SecurityException", reviewResult.exceptionOrNull() is SecurityException)

        // 4. Try via SuperAdminViewModel
        superAdminViewModel.suspendBusiness(
            business = target,
            reason = "অবৈধ চেষ্টা",
            actorUid = "unauth_user",
            actorName = "অসাধু ব্যবহারকারী",
            actorEmail = regularUserEmail
        )
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            "ViewModel UI state must reflect unauthorized error",
            superAdminViewModel.uiState.value.errorMessage?.contains("অননুমোদিত") == true
        )
    }

    /**
     * Scenario 6: Logout করে অন্য দোকানদারের অ্যাকাউন্টে লগইন করলে আগের Super Admin access থেকে যায় না।
     */
    @Test
    fun `scenario 6 - logging out of Super Admin and logging into shopkeeper account completely clears Super Admin privileges`() = runTest(testDispatcher) {
        // Step A: Login as Super Admin
        val superAdminEmail = "ataurgonee4@gmail.com"
        viewModel.handleGoogleUserCredentials(
            uid = "super_admin_uid_786",
            email = superAdminEmail,
            displayName = "সুপার এডমিন",
            phoneNumber = null
        ) { _, _, _ -> }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.isSuperAdminUser())

        // Step B: Logout
        viewModel.logout()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull("Current user must be null after logout", viewModel.currentUser.value)
        assertFalse("Super admin flag must be reset upon logout", viewModel.isSuperAdminUser())
        assertEquals("", viewModel.getSuperAdminEmail())

        // Step C: Login as another shopkeeper (Phone & PIN)
        val shopAdmin = User(
            id = "shopkeeper_55",
            name = "দোকানদার শফিক",
            phone = "01733445566",
            role = UserRole.ADMIN,
            shopId = "biz_shafiq_store",
            pinHash = repo.hashPin("9999"),
            isActive = true,
            accountStatus = UserAccountStatus.ACTIVE
        )
        repo.saveUserDirect(shopAdmin)

        viewModel.loginAdmin("01733445566", "9999") { _, _ -> }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("shopkeeper_55", viewModel.currentUser.value?.id)
        // CRITICAL CHECK: Previous Super Admin access MUST NOT leak into the new user!
        assertFalse(
            "Previous Super Admin access must NOT persist when logging into shopkeeper account!",
            viewModel.isSuperAdminUser()
        )
        assertEquals("", viewModel.getSuperAdminEmail())
    }

    /**
     * Scenario 7: অ্যাপ পুনরায় চালু ও session restore করার পরেও role যাচাই সঠিক থাকে।
     */
    @Test
    fun `scenario 7 - session restore properly validates role and refuses unauthorized Super Admin privilege`() = runTest(testDispatcher) {
        // Test A: Normal shop owner does NOT get Super Admin upon ViewModel recreation / app restart
        CloudBusinessAuth.clear(app)
        CloudBusinessAuth.setStoredBusinessId(app, "biz_test_shop_10", "টেস্ট শপ", UserRole.ADMIN.name)
        CloudBusinessAuth.setStoredRole(app, UserRole.ADMIN.name)
        CloudBusinessAuth.setStoredSuperAdminFlag(app, false)
        CloudBusinessAuth.setStoredAuthEmail(app, null)

        val freshViewModel = BusinessViewModel(app)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(
            "Restored normal shop session must NOT be granted Super Admin access",
            freshViewModel.isSuperAdminUser()
        )
        assertEquals("", freshViewModel.getSuperAdminEmail())

        // Test B: If a deactivated/revoked superadmin account tries to restore, access is refused
        val revokedSuperAdmin = User(
            id = "revoked_superadmin_uid",
            name = "সাবেক সুপার এডমিন",
            phone = "01700000000",
            role = UserRole.ADMIN,
            pinHash = repo.hashPin("1234"),
            isActive = false, // Deactivated!
            accountStatus = UserAccountStatus.SUSPENDED
        )
        repo.saveUserDirect(revokedSuperAdmin)

        CloudBusinessAuth.setStoredAuthEmail(app, "ataulgonee786@gmail.com")
        CloudBusinessAuth.setStoredSuperAdminFlag(app, true)

        val checkViewModel = BusinessViewModel(app)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        // Because account is inactive, Super Admin user check must return false
        assertFalse(
            "Inactive or suspended account must NEVER have Super Admin access",
            checkViewModel.isSuperAdminUser()
        )
    }

    /**
     * Scenario 8: নতুন দোকানের Admin নিজের দোকানের সব অনুমোদিত ফিচার (Dashboard, পণ্য, বিক্রি, কাস্টমার, খরচ) ব্যবহার করতে পারে এবং ডেটা সম্পূর্ণ আলাদা থাকে।
     */
    @Test
    fun `scenario 8 - newly registered shop admin can use all store features with multi-tenant isolation`() = runTest(testDispatcher) {
        var registeredShopId: String? = null
        viewModel.registerNewShop(
            shopName = "ধানমন্ডি গ্রোসারি মার্ট",
            ownerName = "আব্দুর রহিম",
            phone = "01811223344",
            address = "ধানমন্ডি, ঢাকা",
            district = "ঢাকা",
            businessType = "মুদি ও জেনারেল স্টোর",
            pin = "5555",
            isPhoneVerified = true
        ) { success, _, shopId ->
            assertTrue(success)
            registeredShopId = shopId
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(registeredShopId)
        val currentShopAdmin = viewModel.currentUser.value
        assertNotNull(currentShopAdmin)
        assertEquals(UserRole.ADMIN, currentShopAdmin?.role)
        assertEquals(registeredShopId, currentShopAdmin?.shopId)

        // Store feature 1: Add product
        val newProduct = com.example.data.model.Product(
            id = "prod_dhanmondi_rice_1",
            name = "মিনিকেট চাল ২৫ কেজি",
            sku = "RICE-25KG",
            category = "খাদ্যপণ্য",
            purchasePrice = 1800.0,
            sellingPrice = 2000.0,
            currentStock = 50.0,
            alertStockLevel = 10.0,
            unit = "বস্তা"
        )
        db.productDao().insertProduct(newProduct)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        val loadedProduct = db.productDao().getProductById("prod_dhanmondi_rice_1")
        assertNotNull("Shop admin can add and view store products", loadedProduct)
        assertEquals("মিনিকেট চাল ২৫ কেজি", loadedProduct?.name)

        // Store feature 2: Add customer and check due
        val newCustomer = com.example.data.model.Customer(
            id = "cust_dhanmondi_1",
            name = "ফারহানা করিম",
            phone = "01788990011",
            address = "রোড #৪, ধানমন্ডি",
            totalBilled = 2000.0,
            totalPaid = 1500.0,
            currentDue = 500.0
        )
        db.customerDao().insertCustomer(newCustomer)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        val loadedCustomer = db.customerDao().getCustomerById("cust_dhanmondi_1")
        assertNotNull("Shop admin can manage customers", loadedCustomer)
        assertEquals(500.0, loadedCustomer?.currentDue ?: 0.0, 0.01)

        // Store feature 3: Add expense
        val newExpense = com.example.data.model.Expense(
            id = "exp_dhanmondi_1",
            salesmanId = currentShopAdmin!!.id,
            salesmanName = currentShopAdmin.name,
            category = com.example.data.model.ExpenseCategory.OTHER,
            source = com.example.data.model.ExpenseSource.COMPANY_CASH,
            amount = 1200.0,
            description = "দোকানের বিদ্যুৎ বিল",
            status = com.example.data.model.ExpenseStatus.APPROVED
        )
        db.expenseDao().insertExpense(newExpense)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        val loadedExpense = db.expenseDao().getExpenseById("exp_dhanmondi_1")
        assertNotNull("Shop admin can manage expenses", loadedExpense)
        assertEquals(1200.0, loadedExpense?.amount ?: 0.0, 0.01)

        // MULTI-TENANT ISOLATION: Confirm shop admin cannot access Super Admin features
        assertFalse("Shop admin cannot access Super Admin features", viewModel.isSuperAdminUser())
    }

    /**
     * Scenario 9: Google Sign-In এবং নতুন দোকানদারের রেজিস্ট্রেশন ফ্লো সম্পূর্ণ পৃথক ও সুরক্ষিত।
     */
    @Test
    fun `scenario 9 - Google Sign-In role verification distinguishes platform super admin from store admin`() = runTest(testDispatcher) {
        // Step A: Regular Google account logs in -> NOT Super Admin
        val regularGoogleUserEmail = "regular.shopowner@gmail.com"
        var regularRole: UserRole? = null
        viewModel.handleGoogleUserCredentials(
            uid = "google_regular_uid_101",
            email = regularGoogleUserEmail,
            displayName = "সাধারণ দোকানদার",
            phoneNumber = "01799887766"
        ) { _, _, role ->
            regularRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Regular Google account must NOT have super admin access", viewModel.isSuperAdminUser())

        // Step B: Super Admin Google account logs in -> GRANTED Super Admin
        var superAdminRole: UserRole? = null
        viewModel.handleGoogleUserCredentials(
            uid = "google_superadmin_uid_777",
            email = "ataurgonee4@gmail.com",
            displayName = "মোঃ আতাউর রহমান",
            phoneNumber = "01700000000"
        ) { _, _, role ->
            superAdminRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Authorized Google account (ataurgonee4@gmail.com) MUST have Super Admin access", viewModel.isSuperAdminUser())
        assertEquals("ataurgonee4@gmail.com", viewModel.getSuperAdminEmail())
    }

    // =========================================================================
    // MANDATORY TESTS SPECIFIED IN USER REQUEST
    // =========================================================================

    /**
     * Mandatory Test 1: অনুমোদিত Gmail দিয়ে লগইন করলে Super Admin বাটন দেখা যায়।
     */
    @Test
    fun `mandatory test 1 - authorized Gmail (ataurgonee4@gmail_com) shows Super Admin button`() = runTest(testDispatcher) {
        val superAdminEmail = "ataurgonee4@gmail.com"
        assertTrue("Email must be in authorized super admin allowlist", SuperAdminRepository.isSuperAdminEmail(superAdminEmail))

        var loginSuccess = false
        var roleResult: UserRole? = null
        viewModel.handleGoogleUserCredentials(
            uid = "firebase_superadmin_uid_001",
            email = superAdminEmail,
            displayName = "মোঃ আতাউর রহমান",
            phoneNumber = "01700000000"
        ) { success, _, role ->
            loginSuccess = success
            roleResult = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Super Admin login must succeed", loginSuccess)
        assertEquals(UserRole.ADMIN, roleResult)
        // CRITICAL CHECK: Super Admin button is visible
        assertTrue("Super Admin button must be shown for ataurgonee4@gmail.com", viewModel.isSuperAdminUser())
        assertEquals(superAdminEmail, viewModel.getSuperAdminEmail())
    }

    /**
     * Mandatory Test 2: নতুন দোকানদারের Gmail দিয়ে লগইন করলে নিজের দোকানের ড্যাশবোর্ড দেখা যায়, কিন্তু Super Admin বাটন দেখা যায় না।
     */
    @Test
    fun `mandatory test 2 - new shopkeeper Gmail login shows store dashboard and hides Super Admin button`() = runTest(testDispatcher) {
        var registeredShopId: String? = null
        val shopOwnerEmail = "kamal.fashion@gmail.com"

        viewModel.registerNewShop(
            shopName = "উত্তরা ফ্যাশন হাউজ",
            ownerName = "কামাল হোসেন",
            phone = "01755667788",
            address = "উত্তরা সেক্টর ৩, ঢাকা",
            district = "ঢাকা",
            businessType = "গার্মেন্টস ও ফ্যাশন",
            pin = "1234",
            isPhoneVerified = true,
            ownerEmail = shopOwnerEmail,
            ownerUid = "google_uid_kamal_99"
        ) { success, _, shopId ->
            assertTrue(success)
            registeredShopId = shopId
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(registeredShopId)

        // Logout
        viewModel.logout()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        // Shopkeeper logs in with their registered Gmail
        var loginSuccess = false
        var userRole: UserRole? = null
        viewModel.handleGoogleUserCredentials(
            uid = "google_uid_kamal_99",
            email = shopOwnerEmail,
            displayName = "কামাল হোসেন",
            phoneNumber = "01755667788"
        ) { success, _, role ->
            loginSuccess = success
            userRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Store owner Google login must succeed", loginSuccess)
        assertEquals("Store owner is assigned store ADMIN role", UserRole.ADMIN, userRole)
        assertEquals("Current user is active and belongs to the registered shop", registeredShopId, viewModel.currentUser.value?.shopId)
        assertEquals("কামাল হোসেন", viewModel.currentUser.value?.name)

        // CRITICAL CHECK: Super Admin button must NEVER be visible for new shopkeeper!
        assertFalse("Super Admin button must NOT be visible for store admin!", viewModel.isSuperAdminUser())
        assertEquals("", viewModel.getSuperAdminEmail())
    }

    /**
     * Mandatory Test 3: একই দোকানদার মোবাইল নম্বর দিয়ে লগইন করলেও একই দোকানের তথ্য দেখতে পান।
     */
    @Test
    fun `mandatory test 3 - same shopkeeper logging in with mobile number (PIN or OTP) sees exact same store data`() = runTest(testDispatcher) {
        // Register store
        var storeId: String? = null
        val ownerPhone = "01799112233"
        val ownerPin = "4321"

        viewModel.registerNewShop(
            shopName = "মিরপুর ইলেক্ট্রনিক্স",
            ownerName = "মোস্তাফিজুর রহমান",
            phone = ownerPhone,
            address = "মিরপুর ১০, ঢাকা",
            district = "ঢাকা",
            businessType = "ইলেকট্রনিক্স",
            pin = ownerPin,
            isPhoneVerified = true,
            ownerEmail = "mostafiz.elec@gmail.com",
            ownerUid = "uid_mostafiz_elec"
        ) { success, _, id ->
            assertTrue(success)
            storeId = id
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        // Store admin adds a product
        val sampleProduct = com.example.data.model.Product(
            id = "prod_fan_01",
            name = "বিআরবি সিলিং ফ্যান ৫৬ ইঞ্চি",
            sku = "FAN-56",
            category = "ইলেকট্রনিক্স",
            purchasePrice = 2800.0,
            sellingPrice = 3200.0,
            currentStock = 25.0,
            unit = "পিস"
        )
        db.productDao().insertProduct(sampleProduct)

        // Logout
        viewModel.logout()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        // Test 3A: Login with Mobile Number + OTP
        var otpLoginSuccess = false
        viewModel.loginWithPhoneOtp(
            phone = ownerPhone,
            otpCode = "1234"
        ) { success, _, _ ->
            otpLoginSuccess = success
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Mobile OTP login must succeed", otpLoginSuccess)
        assertEquals("User shopId matches exact registered store", storeId, viewModel.currentUser.value?.shopId)
        val productUnderOtp = db.productDao().getProductById("prod_fan_01")
        assertNotNull(productUnderOtp)
        assertEquals("বিআরবি সিলিং ফ্যান ৫৬ ইঞ্চি", productUnderOtp?.name)

        // Logout
        viewModel.logout()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        // Test 3B: Login with Mobile Number + PIN
        var pinLoginSuccess = false
        viewModel.login(ownerPhone, ownerPin) { success, _ ->
            pinLoginSuccess = success
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Mobile PIN login must succeed", pinLoginSuccess)
        assertEquals("User shopId matches exact registered store", storeId, viewModel.currentUser.value?.shopId)
        val productUnderPin = db.productDao().getProductById("prod_fan_01")
        assertNotNull(productUnderPin)
        assertEquals("বিআরবি সিলিং ফ্যান ৫৬ ইঞ্চি", productUnderPin?.name)
    }

    /**
     * Mandatory Test 4: Google Sign-In ও মোবাইল Login-এর কারণে duplicate shop বা duplicate sale তৈরি হয় না।
     */
    @Test
    fun `mandatory test 4 - Google Sign-In and mobile login do NOT create duplicate shop or duplicate sale`() = runTest(testDispatcher) {
        val phone = "01788776655"
        val pin = "5555"
        val email = "shopkeeper.unique@gmail.com"
        val googleUid = "google_uid_unique_555"
        var shopId: String? = null

        // 1. Register shop
        viewModel.registerNewShop(
            shopName = "বনশ্রী জেনারেল স্টোর",
            ownerName = "তারিকুল ইসলাম",
            phone = phone,
            address = "বনশ্রী, ঢাকা",
            district = "ঢাকা",
            businessType = "জেনারেল স্টোর",
            pin = pin,
            isPhoneVerified = true,
            ownerEmail = email,
            ownerUid = googleUid
        ) { success, _, id ->
            assertTrue(success)
            shopId = id
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        // 2. Count initial shops and users in database
        val initialUserCount = db.userDao().getUserCount()
        val initialShopCount = superAdminRepo.businesses.value.filter { it.ownerPhone == phone }.size
        assertEquals("Exactly 1 shop registered in SuperAdminRepository", 1, initialShopCount)

        // 3. Create a sale under mobile login
        val saleItem = com.example.data.model.SaleItem(
            invoiceNumber = "INV-20261027-001",
            productId = "prod_001",
            productName = "সাবান",
            quantity = 2.0,
            unitPrice = 75.0,
            purchasePriceAtSale = 60.0,
            totalPrice = 150.0
        )
        val sale = com.example.data.model.Sale(
            invoiceNumber = "INV-20261027-001",
            customerId = "c_walkin",
            customerName = "সাধারণ খদ্দের",
            customerPhone = "01700000000",
            salesmanId = viewModel.currentUser.value!!.id,
            salesmanName = viewModel.currentUser.value!!.name,
            subtotal = 150.0,
            totalAmount = 150.0,
            paidAmount = 150.0,
            dueAmount = 0.0
        )
        db.saleDao().insertSale(sale)
        db.saleDao().insertSaleItems(listOf(saleItem))

        // 4. Logout and Login via Google Sign-In with linked email
        viewModel.logout()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        var googleLoginOk = false
        viewModel.handleGoogleUserCredentials(
            uid = googleUid,
            email = email,
            displayName = "তারিকুল ইসলাম",
            phoneNumber = phone
        ) { success, _, _ ->
            googleLoginOk = success
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Google login must succeed with linked account", googleLoginOk)
        assertEquals("Shop ID must remain identical", shopId, viewModel.currentUser.value?.shopId)

        // 5. VERIFY: No duplicate shop created
        val finalShopCount = superAdminRepo.businesses.value.filter { it.ownerPhone == phone }.size
        assertEquals("No duplicate shop created in SuperAdminRepository", 1, finalShopCount)

        // 6. VERIFY: No duplicate sales created
        val allSales = db.saleDao().getSyncSales()
        val salesForInvoice = allSales.filter { it.invoiceNumber == "INV-20261027-001" }
        assertEquals("Exactly 1 sale exists; no duplicate sale created", 1, salesForInvoice.size)
    }

    /**
     * Mandatory Test 5: নতুন বা অননুমোদিত Gmail দিয়ে সরাসরি Admin Dashboard বা Super Admin Console খোলা যায় না।
     */
    @Test
    fun `mandatory test 5 - new or unauthorized Gmail cannot directly open Admin Dashboard or Super Admin Console`() = runTest(testDispatcher) {
        val unknownEmail = "random.unknown.user999@gmail.com"
        var loginSuccess = true
        var resultMessage = ""

        viewModel.handleGoogleUserCredentials(
            uid = "unknown_google_uid_999",
            email = unknownEmail,
            displayName = "অপরিচিত ব্যবহারকারী",
            phoneNumber = null
        ) { success, msg, _ ->
            loginSuccess = success
            resultMessage = msg
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Direct login MUST FAIL
        assertFalse("Unregistered Gmail direct login must be REJECTED", loginSuccess)
        assertTrue(
            "Message must inform user that account is not registered to any store",
            resultMessage.contains("কোনো নিবন্ধিত দোকানের সাথে যুক্ত নয়")
        )

        // 2. Current user session MUST be NULL
        assertNull("Current user must remain NULL", viewModel.currentUser.value)

        // 3. Super Admin console MUST be LOCKED
        assertFalse("Super Admin access must be DENIED", viewModel.isSuperAdminUser())
        assertEquals("", viewModel.getSuperAdminEmail())
    }

    /**
     * Mandatory Test 6: Salesman এবং অন্য দোকানদার কোনোভাবে Super Admin authorization পায় না।
     */
    @Test
    fun `mandatory test 6 - salesman and regular shopkeeper cannot obtain Super Admin authorization`() = runTest(testDispatcher) {
        // Part A: Salesman
        val salesman = User(
            id = "staff_salesman_10",
            name = "সেলসম্যান সুমন",
            phone = "01811002233",
            role = UserRole.SALESMAN,
            pinHash = repo.hashPin("1111"),
            shopId = "biz_store_abc",
            isActive = true
        )
        db.userDao().insertUser(salesman)

        viewModel.login("01811002233", "1111") { _, _ -> }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(UserRole.SALESMAN, viewModel.currentUser.value?.role)
        assertFalse("Salesman CANNOT be Super Admin", viewModel.isSuperAdminUser())

        // Try calling protected Super Admin repository method
        val salesmanAttempt = superAdminRepo.updateBusinessPlan(
            businessId = "biz_store_abc",
            planId = "plan_enterprise",
            durationDays = 30,
            actorUid = "staff_salesman_10",
            actorName = "সেলসম্যান সুমন",
            actorEmail = "sumon@shop.local"
        )
        assertTrue("Salesman calling Super Admin operation must be REJECTED", salesmanAttempt.isFailure)

        // Part B: Regular Shopkeeper
        val shopkeeper = User(
            id = "shopkeeper_monir",
            name = "মনির হোসেন",
            phone = "01722334455",
            role = UserRole.ADMIN,
            pinHash = repo.hashPin("2222"),
            shopId = "biz_monir_store",
            isActive = true
        )
        db.userDao().insertUser(shopkeeper)

        viewModel.login("01722334455", "2222") { _, _ -> }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("shopkeeper_monir", viewModel.currentUser.value?.id)
        assertFalse("Regular shopkeeper CANNOT be Platform Super Admin", viewModel.isSuperAdminUser())
    }

    /**
     * Mandatory Test 7: বিদ্যমান Super Admin UID, "admin_1" mapping এবং ব্যবসার ডেটা অক্ষত থাকে।
     */
    @Test
    fun `mandatory test 7 - existing Super Admin UID, admin_1 mapping and business data remain intact`() = runTest(testDispatcher) {
        // Pre-populate seed admin_1 and store
        val seedAdmin = User(
            id = "admin_1",
            name = "মোঃ আতাউর রহমান",
            phone = "01700000000",
            role = UserRole.ADMIN,
            shopId = "biz_smart_store_1",
            pinHash = repo.hashPin("1234"),
            isActive = true,
            accountStatus = UserAccountStatus.ACTIVE
        )
        db.userDao().insertUser(seedAdmin)

        // Pre-populate store business record
        val initialPlatformBusinesses = superAdminRepo.businesses.value
        assertTrue("Default seed businesses exist", initialPlatformBusinesses.isNotEmpty())

        // Super Admin Google Login
        var superAdminOk = false
        viewModel.handleGoogleUserCredentials(
            uid = "firebase_ataurgonee4_uid",
            email = "ataurgonee4@gmail.com",
            displayName = "মোঃ আতাউর রহমান",
            phoneNumber = "01700000000"
        ) { success, _, _ ->
            superAdminOk = success
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(superAdminOk)
        assertTrue("Super Admin access is active", viewModel.isSuperAdminUser())
        assertEquals("01700000000", viewModel.currentUser.value?.phone)
        assertEquals("biz_smart_store_1", viewModel.currentUser.value?.shopId)

        // Verify existing seed businesses in repository are intact
        val remainingBusinesses = superAdminRepo.businesses.value
        assertTrue("Business data is not wiped or corrupted", remainingBusinesses.any { it.id == "biz_smart_store_1" })
    }
}
