package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SuperAdminRepository
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
class CompleteShopRegistrationAndSuperAdminSyncCUJTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repo: BusinessRepository
    private lateinit var viewModel: BusinessViewModel
    private lateinit var superAdminRepo: SuperAdminRepository
    private lateinit var superAdminViewModel: SuperAdminViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        com.example.data.sync.CloudBusinessAuth.clear(app)
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
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        viewModel.syncManager.cancelActiveSync()
        com.example.data.sync.CloudBusinessAuth.clear(app)
        superAdminRepo.resetStateForTesting()
        db.close()
        AppDatabase.setInstanceForTesting(null)
        Dispatchers.resetMain()
    }

    // CUJ 1: New shop cloud registration with permanent fields
    @Test
    fun cuj01_newShopCloudRegistration_savesPermanentFields() = runTest {
        var registeredSuccess = false
        var registeredShopId: String? = null
        var responseMsg = ""

        viewModel.registerNewShop(
            shopName = "মেসার্স ভাই ভাই এন্টারপ্রাইজ",
            ownerName = "মোঃ রফিকুল ইসলাম",
            phone = "01722334455",
            address = "চকবাজার",
            district = "ঢাকা",
            businessType = "পাইকারি ট্রেডার্স",
            pin = "4321",
            ownerEmail = "rafiq.enterprise@gmail.com",
            ownerUid = "firebase_uid_rafiq_01"
        ) { success, msg, id ->
            registeredSuccess = success
            responseMsg = msg
            registeredShopId = id
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Registration must succeed: $responseMsg", registeredSuccess)
        assertNotNull("Generated Shop ID must not be null", registeredShopId)
        assertTrue("Shop ID must have permanent biz_ prefix", registeredShopId!!.startsWith("biz_"))

        // Verify stored in SuperAdminRepository
        val foundInSuperAdmin = superAdminRepo.businesses.value.firstOrNull { it.id == registeredShopId }
        assertNotNull("Newly registered shop must immediately exist in SuperAdmin businesses list", foundInSuperAdmin)
        assertEquals("মেসার্স ভাই ভাই এন্টারপ্রাইজ", foundInSuperAdmin!!.businessName)
        assertEquals("firebase_uid_rafiq_01", foundInSuperAdmin.ownerUid)
        assertEquals("rafiq.enterprise@gmail.com", foundInSuperAdmin.ownerEmail)
        assertEquals("01722334455", foundInSuperAdmin.ownerPhone)
        assertEquals("plan_free", foundInSuperAdmin.planId)
        assertTrue("Created timestamp must be recent", foundInSuperAdmin.createdAt > 0)
    }

    // CUJ 2: Super Admin Dynamic Shop Listing
    @Test
    fun cuj02_superAdminPanel_listsAllRegisteredShopsDynamically() = runTest {
        // Register two distinct shops
        viewModel.registerNewShop(
            shopName = "দোকান এক",
            ownerName = "মালিক এক",
            phone = "01811223344",
            address = "উত্তরা",
            district = "ঢাকা",
            businessType = "মুদি",
            pin = "1111",
            ownerUid = "uid_shop_1"
        ) { _, _, _ -> }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.registerNewShop(
            shopName = "দোকান দুই",
            ownerName = "মালিক দুই",
            phone = "01922334455",
            address = "ধানমন্ডি",
            district = "ঢাকা",
            businessType = "ফার্মেসি",
            pin = "2222",
            ownerUid = "uid_shop_2"
        ) { _, _, _ -> }
        testDispatcher.scheduler.advanceUntilIdle()

        superAdminViewModel.refreshBusinesses()
        testDispatcher.scheduler.advanceUntilIdle()

        val list = superAdminRepo.businesses.value
        assertTrue("Super Admin businesses list must contain Shop 1", list.any { it.businessName == "দোকান এক" })
        assertTrue("Super Admin businesses list must contain Shop 2", list.any { it.businessName == "দোকান দুই" })
    }

    // CUJ 3: Super Admin Allowlist & Revocation Verification
    @Test
    fun cuj03_superAdminSecurityAllowlistAndRevocation() {
        // Authorized Allowlist
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ataurgonee4@gmail.com"))
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ataursek9@gmail.com"))
        assertTrue(SuperAdminRepository.isSuperAdminPhone("01700000000"))
        assertTrue(SuperAdminRepository.isSuperAdminPhone("+8801700000000"))

        // Revoked identities
        val revoked = listOf(
            "buddavaibinodon@gmail.com",
            "support@smartbiz.local",
            "gonee8666@gmail.com",
            "ataulgonee786@gmail.com",
            "ataurpos@gmail.com",
            "ataur50305@gmail.com",
            "a48268993@gmail.com",
            "jamesfashion792@gmail.com"
        )
        for (email in revoked) {
            assertFalse("Revoked email '$email' must NOT have Super Admin privileges", SuperAdminRepository.isSuperAdminEmail(email))
            assertTrue("Revoked email '$email' must be recognized as revoked", SuperAdminRepository.isRevokedSuperAdminEmail(email))
        }

        // Random non-admin accounts
        assertFalse(SuperAdminRepository.isSuperAdminEmail("random.user@gmail.com"))
        assertFalse(SuperAdminRepository.isSuperAdminEmail("shopkeeper123@gmail.com"))
    }

    // CUJ 4: Newly registered shopkeeper NEVER receives Super Admin privileges
    @Test
    fun cuj04_newShopkeeperNeverGetsSuperAdminPrivileges() = runTest {
        viewModel.registerNewShop(
            shopName = "সাধারণ দোকান",
            ownerName = "আব্দুল হক",
            phone = "01611223344",
            address = "মিরপুর",
            district = "ঢাকা",
            businessType = "মুদি",
            pin = "1234",
            ownerEmail = "abdul.haq@gmail.com",
            ownerUid = "uid_abdul_haq"
        ) { _, _, _ -> }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("New shopkeeper must not have active super admin session", viewModel.isSuperAdminSession.value)
        assertNull("New shopkeeper must not have super admin verified email", viewModel.superAdminVerifiedEmail.value)
        assertFalse("New shopkeeper must not have super admin flag in storage", com.example.data.sync.CloudBusinessAuth.isStoredSuperAdmin(app))
    }

    // CUJ 5: Duplicate registration prevention with exact Bengali error message
    @Test
    fun cuj05_duplicateRegistrationPrevention_exactBengaliMessage() = runTest {
        // Step 1: Register primary shop
        viewModel.registerNewShop(
            shopName = "মাস্টার স্টোর",
            ownerName = "সোহেল রানা",
            phone = "01788990011",
            address = "যাত্রাবাড়ী",
            district = "ঢাকা",
            businessType = "জেনারেল",
            pin = "1234",
            ownerEmail = "sohel.rana@gmail.com",
            ownerUid = "uid_sohel_primary"
        ) { _, _, _ -> }
        testDispatcher.scheduler.advanceUntilIdle()

        // Step 2: Attempt duplicate registration with same phone
        var dupPhoneSuccess = true
        var dupPhoneMsg = ""
        viewModel.registerNewShop(
            shopName = "ক্লোন স্টোর ফোন",
            ownerName = "সোহেল রানা",
            phone = "01788990011", // Duplicate phone
            address = "যাত্রাবাড়ী",
            district = "ঢাকা",
            businessType = "জেনারেল",
            pin = "9999",
            ownerEmail = "different.email@gmail.com",
            ownerUid = "uid_different"
        ) { success, msg, _ ->
            dupPhoneSuccess = success
            dupPhoneMsg = msg
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Duplicate phone registration must be blocked", dupPhoneSuccess)
        val expectedExactMessage = "এই Gmail বা ফোন নম্বর দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে। নতুন দোকান তৈরি না করে আগের অ্যাকাউন্টে লগইন করুন।"
        assertEquals("Duplicate phone must show exact Bengali guidance message", expectedExactMessage, dupPhoneMsg)

        // Step 3: Attempt duplicate registration with same email and UID
        var dupEmailSuccess = true
        var dupEmailMsg = ""
        viewModel.registerNewShop(
            shopName = "ক্লোন স্টোর জিমেইল",
            ownerName = "সোহেল রানা",
            phone = "01911998877", // Different phone
            address = "যাত্রাবাড়ী",
            district = "ঢাকা",
            businessType = "জেনারেল",
            pin = "9999",
            ownerEmail = "sohel.rana@gmail.com", // Duplicate email
            ownerUid = "uid_sohel_primary"       // Duplicate UID
        ) { success, msg, _ ->
            dupEmailSuccess = success
            dupEmailMsg = msg
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Duplicate email/UID registration must be blocked", dupEmailSuccess)
        assertEquals("Duplicate email must show exact Bengali guidance message", expectedExactMessage, dupEmailMsg)
    }

    // CUJ 6: Cross-Device Shop Retrieval via Google Sign-In
    @Test
    fun cuj06_crossDeviceShopRetrieval_viaGoogleSignIn() = runTest {
        // Simulate Shop registered on Phone A
        val initialShopId = "biz_cross_device_google_1"
        val registeredBiz = PlatformBusiness(
            id = initialShopId,
            businessName = "আল-আমিন ভ্যারাইটিজ",
            ownerUid = "google_uid_alamin_777",
            ownerName = "মোঃ আল আমিন",
            ownerEmail = "alamin.store@gmail.com",
            ownerPhone = "01733445566",
            status = BusinessAccountStatus.ACTIVE,
            planId = "plan_free"
        )
        superAdminRepo.registerNewShop(registeredBiz)

        // Clear local database to simulate fresh app install on Phone B
        db.userDao().deleteAllUsers()
        com.example.data.sync.CloudBusinessAuth.clear(app)
        assertNull(repo.getUserById("google_uid_alamin_777"))

        // Simulate Google Sign-In on Phone B
        var signInSuccess = false
        var userRole: UserRole? = null
        viewModel.handleGoogleUserCredentials(
            uid = "google_uid_alamin_777",
            email = "alamin.store@gmail.com",
            displayName = "মোঃ আল আমিন",
            phoneNumber = "01733445566"
        ) { success, _, role ->
            signInSuccess = success
            userRole = role
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Google sign-in on fresh device must succeed by retrieving registered shop", signInSuccess)
        assertEquals(UserRole.ADMIN, userRole)

        // Verify local user and active business ID recovered without duplicating store
        val recoveredUser = repo.getUserById("google_uid_alamin_777")
        assertNotNull("User must be reconstructed in local Room database", recoveredUser)
        assertEquals(initialShopId, recoveredUser!!.shopId)
        assertEquals(initialShopId, viewModel.getActiveBusinessId())
        assertEquals("আল-আমিন ভ্যারাইটিজ", db.companySettingsDao().getSettings()?.businessName)
    }

    // CUJ 7: Cross-Device Shop Retrieval via Phone OTP
    @Test
    fun cuj07_crossDeviceShopRetrieval_viaPhoneOtp() = runTest {
        // Simulate Shop registered on Phone A
        val initialShopId = "biz_cross_device_phone_2"
        val registeredBiz = PlatformBusiness(
            id = initialShopId,
            businessName = "নিউ ঢাকা ডিপার্টমেন্টাল",
            ownerUid = "phone_uid_dhaka_888",
            ownerName = "কামরুল ইসলাম",
            ownerEmail = "kamrul.dhaka@gmail.com",
            ownerPhone = "01855667788",
            status = BusinessAccountStatus.ACTIVE,
            planId = "plan_free"
        )
        superAdminRepo.registerNewShop(registeredBiz)

        // Clear local database on Phone B
        db.userDao().deleteAllUsers()
        com.example.data.sync.CloudBusinessAuth.clear(app)
        assertNull(repo.getUserByPhone("01855667788"))

        // Phone OTP Login on Phone B
        var otpLoginSuccess = false
        var loggedUser: User? = null
        viewModel.loginWithPhoneOtp(
            phone = "01855667788",
            otpCode = "123456"
        ) { success, _, user ->
            otpLoginSuccess = success
            loggedUser = user
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Phone OTP login on fresh device must succeed by finding cloud registered business", otpLoginSuccess)
        assertNotNull("Logged in user must not be null", loggedUser)
        assertEquals(initialShopId, loggedUser!!.shopId)
        assertEquals(initialShopId, viewModel.getActiveBusinessId())
        assertEquals("নিউ ঢাকা ডিপার্টমেন্টাল", db.companySettingsDao().getSettings()?.businessName)
    }

    // CUJ 8: Multi-device sync & financial idempotency
    @Test
    fun cuj08_financialIdempotency_preventsDuplicateInvoices() = runTest {
        val shopId = "biz_idempotency_shop_9"
        val invoiceNo = "INV-20261028-001"

        val sale1 = Sale(
            invoiceNumber = invoiceNo,
            customerId = "cust_1",
            customerName = "Customer 1",
            customerPhone = "01700000001",
            salesmanId = "salesman_1",
            salesmanName = "Salesman 1",
            subtotal = 1500.0,
            totalAmount = 1500.0,
            paidAmount = 1500.0,
            dueAmount = 0.0,
            createdAt = System.currentTimeMillis()
        )

        // Save invoice first time
        db.saleDao().insertSale(sale1)
        val countAfterFirst = db.saleDao().getRealSalesCount()
        assertEquals(1, countAfterFirst)

        // Attempt duplicate sync write with identical invoice number (REPLACE or ignore)
        db.saleDao().insertSale(sale1)
        val countAfterSecond = db.saleDao().getRealSalesCount()
        assertEquals("Duplicate invoice number must not create duplicate sales row", 1, countAfterSecond)
    }

    // CUJ 9: Tenant isolation between shops
    @Test
    fun cuj09_tenantIsolation_betweenTwoShops() = runTest {
        val shopA = "biz_tenant_a"
        val shopB = "biz_tenant_b"

        val userA = User(id = "admin_a", name = "Admin A", phone = "01711111112", role = UserRole.ADMIN, pinHash = "pin_hash_a", shopId = shopA)
        val userB = User(id = "admin_b", name = "Admin B", phone = "01722222223", role = UserRole.ADMIN, pinHash = "pin_hash_b", shopId = shopB)

        db.userDao().insertUser(userA)
        db.userDao().insertUser(userB)

        val usersInA = db.userDao().getActiveUsersByShopList(shopA)
        assertEquals(1, usersInA.size)
        assertEquals("admin_a", usersInA.first().id)

        val usersInB = db.userDao().getActiveUsersByShopList(shopB)
        assertEquals(1, usersInB.size)
        assertEquals("admin_b", usersInB.first().id)
    }
}
