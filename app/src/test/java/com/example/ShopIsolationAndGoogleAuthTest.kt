package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.UserAccountStatus
import com.example.data.model.UserRole
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SuperAdminRepository
import com.example.util.GoogleSignInHelper
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
class ShopIsolationAndGoogleAuthTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repo: BusinessRepository
    private lateinit var viewModel: BusinessViewModel

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
        SuperAdminRepository.getInstance(app).resetStateForTesting()
        repo = BusinessRepository(db)
        viewModel = BusinessViewModel(app)
    }

    @After
    fun tearDown() {
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        viewModel.syncManager.cancelActiveSync()
        com.example.data.sync.CloudBusinessAuth.clear(app)
        SuperAdminRepository.getInstance(app).resetStateForTesting()
        AppDatabase.setInstanceForTesting(null)
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `test newly registered shop starts with only the owner admin in staff list`() = runTest(testDispatcher) {
        var registerSuccess: Boolean? = null
        var registeredShopId: String? = null
        var registerMsg: String? = null

        viewModel.registerNewShop(
            shopName = "নিউ ফ্যাশন স্টোর",
            ownerName = "মোহাম্মদ রহিম",
            phone = "01755555555",
            address = "মিরপুর ১০",
            district = "ঢাকা",
            businessType = "গার্মেন্টস ও ফ্যাশন",
            pin = "4321",
            isPhoneVerified = true
        ) { success, msg, shopId ->
            registerSuccess = success
            registerMsg = msg
            registeredShopId = shopId
        }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("Registration must succeed: $registerMsg", registerSuccess == true)
        assertNotNull("ShopId must be generated", registeredShopId)

        // Check active business and current user
        assertEquals(registeredShopId, viewModel.activeBusinessId.value)
        val currentUser = viewModel.currentUser.value
        assertNotNull("Current user must be logged in as owner admin", currentUser)
        assertEquals("মোহাম্মদ রহিম", currentUser!!.name)
        assertEquals("01755555555", currentUser.phone)
        assertEquals(UserRole.ADMIN, currentUser.role)
        assertEquals(registeredShopId, currentUser.shopId)

        // Verify users list only contains the 1 new owner, not previous shop's 01700000000 or 01800000000
        val staffList = viewModel.users.value
        assertEquals("Staff list for newly registered shop must contain exactly 1 member (the owner)", 1, staffList.size)
        assertEquals("01755555555", staffList[0].phone)
        assertEquals(registeredShopId, staffList[0].shopId)

        // Legacy default admin / salesman must not be in this shop's staff
        assertFalse(
            "Legacy default admin (01700000000) must not appear in new shop's staff",
            staffList.any { it.phone == "01700000000" }
        )
        assertFalse(
            "Legacy default salesman (01800000000) must not appear in new shop's staff",
            staffList.any { it.phone == "01800000000" }
        )
    }

    @Test
    fun `test strict staff isolation between two shops`() = runTest(testDispatcher) {
        // Register Shop 1
        var shop1Id: String? = null
        viewModel.registerNewShop(
            shopName = "দোকান এক",
            ownerName = "মালিক এক",
            phone = "01711111111",
            address = "ধানমন্ডি",
            district = "ঢাকা",
            businessType = "মুদি দোকান",
            pin = "1111",
            isPhoneVerified = true
        ) { _, _, id -> shop1Id = id }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Owner 1 adds a salesman to Shop 1
        var addStaff1Success = false
        viewModel.createStaffMember(
            name = "সেলসম্যান এক",
            phone = "01811111111",
            rawPin = "2222",
            role = UserRole.SALESMAN,
            canDiscount = true,
            canViewCustomerDue = true,
            canCollectPayment = true,
            canApplyExpense = false
        ) { success, _ -> addStaff1Success = success }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("Staff addition in shop 1 should succeed", addStaff1Success)

        // Now Register Shop 2
        var shop2Id: String? = null
        viewModel.registerNewShop(
            shopName = "দোকান দুই",
            ownerName = "মালিক দুই",
            phone = "01722222222",
            address = "গুলশান",
            district = "ঢাকা",
            businessType = "ইলেকট্রনিক্স",
            pin = "2222",
            isPhoneVerified = true
        ) { _, _, id -> shop2Id = id }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertNotEquals("Shop 1 and Shop 2 must have distinct IDs", shop1Id, shop2Id)
        assertEquals("Active shop must now be Shop 2", shop2Id, viewModel.activeBusinessId.value)

        // Verify Shop 2 staff list only contains Owner 2
        val shop2Staff = viewModel.users.value
        assertEquals("Shop 2 must only have 1 staff initially", 1, shop2Staff.size)
        assertEquals("01722222222", shop2Staff[0].phone)

        // Ensure Shop 1's owner and salesman are NOT in Shop 2
        assertFalse(shop2Staff.any { it.phone == "01711111111" })
        assertFalse(shop2Staff.any { it.phone == "01811111111" })

        // Check database queries directly by shopId
        val shop1UsersInDb = viewModel.repository.getUsersByShopFlow(shop1Id!!).first()
        val shop2UsersInDb = viewModel.repository.getUsersByShopFlow(shop2Id!!).first()

        assertEquals("Shop 1 in DB has Owner 1 + Salesman 1", 2, shop1UsersInDb.size)
        assertEquals("Shop 2 in DB has only Owner 2", 1, shop2UsersInDb.size)
    }

    @Test
    fun `test duplicate phone number within same shop is rejected`() = runTest(testDispatcher) {
        viewModel.registerNewShop(
            shopName = "টেস্ট শপ",
            ownerName = "মালিক",
            phone = "01799999999",
            address = "উত্তরা",
            district = "ঢাকা",
            businessType = "জেনারেল স্টোর",
            pin = "1234",
            isPhoneVerified = true
        ) { _, _, _ -> }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Try adding staff with same phone number
        var addDuplicateSuccess: Boolean? = null
        var errorMessage: String? = null
        viewModel.createStaffMember(
            name = "ডুপ্লিকেট কর্মী",
            phone = "01799999999",
            rawPin = "5678",
            role = UserRole.SALESMAN,
            canDiscount = false,
            canViewCustomerDue = false,
            canCollectPayment = false,
            canApplyExpense = false
        ) { success, msg ->
            addDuplicateSuccess = success
            errorMessage = msg
        }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse("Adding staff with existing phone number must fail", addDuplicateSuccess == true)
        assertTrue("Error message must indicate duplicate phone number", errorMessage!!.contains("বিদ্যমান"))
    }

    @Test
    fun `test staff PIN reset and profile update`() = runTest(testDispatcher) {
        viewModel.registerNewShop(
            shopName = "টাইলস সেন্টার",
            ownerName = "মালিক সুমন",
            phone = "01788888888",
            address = "যাত্রাবাড়ী",
            district = "ঢাকা",
            businessType = "হার্ডওয়্যার",
            pin = "1111",
            isPhoneVerified = true
        ) { _, _, _ -> }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Add a salesman
        viewModel.createStaffMember(
            name = "করিম শেখ",
            phone = "01888888888",
            rawPin = "0000",
            role = UserRole.SALESMAN,
            canDiscount = false,
            canViewCustomerDue = false,
            canCollectPayment = false,
            canApplyExpense = false
        ) { _, _ -> }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val staff = viewModel.users.value.first { it.phone == "01888888888" }

        // Test PIN reset
        var resetSuccess = false
        viewModel.resetStaffPin(staff.id, "9999") { success, _ -> resetSuccess = success }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("PIN reset must succeed", resetSuccess)

        // Test Profile & Permission update
        val updatedStaff = staff.copy(
            name = "করিম শেখ (সিনিয়র)",
            role = UserRole.ADMIN,
            canDiscount = true,
            canViewCustomerDue = true
        )
        var updateSuccess = false
        viewModel.updateStaffProfile(updatedStaff) { success, _ -> updateSuccess = success }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("Staff profile update must succeed", updateSuccess)
        val refreshed = viewModel.users.value.first { it.id == staff.id }
        assertEquals("করিম শেখ (সিনিয়র)", refreshed.name)
        assertEquals(UserRole.ADMIN, refreshed.role)
        assertTrue(refreshed.canDiscount)
        assertTrue(refreshed.canViewCustomerDue)
    }

    @Test
    fun `test authorized Super Admin ataursek9@gmail_com is recognized`() = runTest(testDispatcher) {
        // Verify SuperAdminRepository recognition
        assertTrue(
            "ataursek9@gmail.com must be recognized as Super Admin in repository",
            SuperAdminRepository.isSuperAdminEmail("ataursek9@gmail.com")
        )
        assertTrue(
            "ataursek9@gmail.com uppercase must also be recognized case-insensitively",
            SuperAdminRepository.isSuperAdminEmail("ATAURSEK9@GMAIL.COM")
        )
        assertTrue(
            "BusinessRepository must recognize ataursek9@gmail.com as super admin",
            viewModel.repository.isAuthorizedSuperAdminEmail("ataursek9@gmail.com")
        )
        assertFalse(
            "jamesfashion792@gmail.com must be revoked from Super Admin in repository",
            SuperAdminRepository.isSuperAdminEmail("jamesfashion792@gmail.com")
        )
        assertTrue(
            "jamesfashion792@gmail.com must be marked as revoked",
            SuperAdminRepository.isRevokedSuperAdminEmail("jamesfashion792@gmail.com")
        )

        // Test login through handleGoogleUserCredentials
        var loginSuccess: Boolean? = null
        var userRole: UserRole? = null
        viewModel.handleGoogleUserCredentials(
            uid = "super_admin_google_uid_ataursek9",
            email = "ataursek9@gmail.com",
            displayName = "Ataur Sek Super Admin",
            phoneNumber = "01700000000"
        ) { success, _, role ->
            loginSuccess = success
            userRole = role
        }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("Super Admin Google Sign-In must succeed", loginSuccess == true)
        assertEquals("Role must be ADMIN", UserRole.ADMIN, userRole)
        assertNotNull("Current user must be set", viewModel.currentUser.value)
    }

    @Test
    fun `test ordinary shop owner cannot access Platform Super Admin Console`() {
        val ordinaryShopOwnerEmail = "shopowner123@gmail.com"
        assertFalse(
            "Ordinary shop owner email must not have Super Admin access",
            SuperAdminRepository.isSuperAdminEmail(ordinaryShopOwnerEmail)
        )
        assertFalse(
            "Ordinary shop owner email must not be recognized as super admin in BusinessRepository",
            repo.isAuthorizedSuperAdminEmail(ordinaryShopOwnerEmail)
        )
    }

    @Test
    fun `test GoogleSignInHelper Bengali error messages`() {
        val netErr = GoogleSignInHelper.getBengaliErrorMessage(java.net.UnknownHostException("Unable to resolve host"))
        assertTrue("Network error must give clear Bengali message", netErr.contains("ইন্টারনেট সংযোগ"))

        val timeoutErr = GoogleSignInHelper.getBengaliErrorMessage(java.net.SocketTimeoutException("Timeout"))
        assertTrue("Timeout must give clear Bengali message", timeoutErr.contains("টাইমআউট"))

        val cancelErr = GoogleSignInHelper.getBengaliErrorMessage(Exception("Activity cancelled by user"))
        assertTrue("Cancellation must give clear Bengali message", cancelErr.contains("বাতিল"))
    }

    @Test
    fun `test GoogleSignInHelper signing fingerprints and Web Client ID alignment`() {
        assertEquals(
            "APK_SIGNING_SHA1 must match GoogleSignInHelper constant",
            GoogleSignInHelper.APK_SIGNING_SHA1,
            GoogleSignInHelper.APK_SIGNING_SHA1
        )
        assertTrue(
            "APK_SIGNING_SHA1 must be a valid 20-byte colon-separated SHA-1 hex string",
            GoogleSignInHelper.APK_SIGNING_SHA1.matches(Regex("^([0-9A-F]{2}:){19}[0-9A-F]{2}$"))
        )
        assertEquals(
            "APK_SIGNING_SHA256 must match GoogleSignInHelper constant",
            GoogleSignInHelper.APK_SIGNING_SHA256,
            GoogleSignInHelper.APK_SIGNING_SHA256
        )
        assertTrue(
            "APK_SIGNING_SHA256 must be a valid 32-byte colon-separated SHA-256 hex string",
            GoogleSignInHelper.APK_SIGNING_SHA256.matches(Regex("^([0-9A-F]{2}:){31}[0-9A-F]{2}$"))
        )
        assertEquals(
            "APPLICATION_ID must match the package name com.aistudio.smartbiz.mgr",
            "com.aistudio.smartbiz.mgr",
            GoogleSignInHelper.APPLICATION_ID
        )
        assertEquals(
            "DEFAULT_WEB_CLIENT_ID must match google-services.json client_type 3 Web Client ID",
            "484040397249-h2tfr5q8pgu65ncl9fgaslcbdsvj9kkj.apps.googleusercontent.com",
            GoogleSignInHelper.DEFAULT_WEB_CLIENT_ID
        )

        val diag = GoogleSignInHelper.getDiagnosticInfo(app)
        assertNotNull("DiagnosticInfo must not be null", diag)
        assertTrue("DiagnosticInfo webClientId must be populated", diag.webClientId.isNotBlank())
        assertTrue("DiagnosticInfo actualSha1 must be populated", diag.actualSha1.isNotBlank())
    }
}
