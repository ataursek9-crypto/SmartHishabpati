package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SuperAdminRepository
import com.example.viewmodel.BusinessViewModel
import com.example.viewmodel.SuperAdminTab
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
@Config(manifest = Config.NONE, sdk = [33])
class StrictSuperAdminAllowlistAndMigrationTest {

    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: BusinessRepository
    private lateinit var superAdminRepo: SuperAdminRepository
    private lateinit var viewModel: BusinessViewModel
    private lateinit var superAdminViewModel: SuperAdminViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        com.example.data.sync.CloudBusinessAuth.clear(application)
        try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        AppDatabase.setInstanceForTesting(database)
        repository = BusinessRepository(database)
        superAdminRepo = SuperAdminRepository.getInstance(application)
        superAdminRepo.resetStateForTesting()

        viewModel = BusinessViewModel(application)
        superAdminViewModel = SuperAdminViewModel(application, superAdminRepo)
    }

    @After
    fun tearDown() {
        com.example.data.sync.CloudBusinessAuth.clear(application)
        try { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
        superAdminRepo.resetStateForTesting()
        AppDatabase.setInstanceForTesting(null)
        database.close()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `test strict Super Admin allowlist for authorized Gmail and phone`() {
        // Authorized Gmails
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ataurgonee4@gmail.com"))
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ataursek9@gmail.com"))
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ATAURGONEE4@GMAIL.COM"))
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ATAURSEK9@GMAIL.COM"))

        // Authorized Mobile Phone
        assertTrue(SuperAdminRepository.isSuperAdminPhone("01700000000"))
        assertTrue(SuperAdminRepository.isSuperAdminPhone("+8801700000000"))
        assertTrue(SuperAdminRepository.isSuperAdminPhone("8801700000000"))

        // BusinessRepository delegation
        assertTrue(repository.isAuthorizedSuperAdminEmail("ataurgonee4@gmail.com"))
        assertTrue(repository.isAuthorizedSuperAdminEmail("ataursek9@gmail.com"))
        assertTrue(repository.isAuthorizedSuperAdminPhone("01700000000"))
    }

    @Test
    fun `test strict revocation of buddavaibinodon and unauthorized accounts`() {
        val revokedEmails = listOf(
            "buddavaibinodon@gmail.com",
            "support@smartbiz.local",
            "gonee8666@gmail.com",
            "ataulgonee786@gmail.com",
            "ataurpos@gmail.com",
            "ataur50305@gmail.com",
            "a48268993@gmail.com",
            "jamesfashion792@gmail.com"
        )

        for (email in revokedEmails) {
            assertFalse(
                "Revoked email $email must NOT have Super Admin rights",
                SuperAdminRepository.isSuperAdminEmail(email)
            )
            assertTrue(
                "Revoked email $email must be marked in revoked set",
                SuperAdminRepository.isRevokedSuperAdminEmail(email)
            )
            assertFalse(
                "BusinessRepository must NOT recognize revoked email $email",
                repository.isAuthorizedSuperAdminEmail(email)
            )
            assertTrue(
                "BusinessRepository must identify revoked email $email",
                repository.isRevokedSuperAdminEmail(email)
            )
        }

        // Random or shop owner emails
        assertFalse(SuperAdminRepository.isSuperAdminEmail("regular.shopkeeper@gmail.com"))
        assertFalse(SuperAdminRepository.isSuperAdminPhone("01711223344"))
    }

    @Test
    fun `test dry-run audit report generates full justification and preserves shop records`() {
        val auditRecords = superAdminRepo.performDryRunAudit()
        assertTrue("Audit records must not be empty", auditRecords.isNotEmpty())

        val ataurgonee = auditRecords.firstOrNull { it.identifier == "ataurgonee4@gmail.com" }
        assertNotNull(ataurgonee)
        assertEquals("SUPER_ADMIN", ataurgonee?.newRole)
        assertEquals("AUTHORIZED_SUPER_ADMIN_PRESERVED", ataurgonee?.status)

        val ataursek = auditRecords.firstOrNull { it.identifier == "ataursek9@gmail.com" }
        assertNotNull(ataursek)
        assertEquals("SUPER_ADMIN", ataursek?.newRole)

        val phoneRecord = auditRecords.firstOrNull { it.identifier == "01700000000" }
        assertNotNull(phoneRecord)
        assertEquals("SUPER_ADMIN", phoneRecord?.newRole)

        val buddavai = auditRecords.firstOrNull { it.identifier == "buddavaibinodon@gmail.com" }
        assertNotNull(buddavai)
        assertEquals("SUPER_ADMIN_PRIVILEGE_REVOKED", buddavai?.status)
        assertTrue(buddavai?.revocationReason?.contains("buddavaibinodon@gmail.com") == true)
        assertTrue(buddavai?.preservedData?.contains("১০০% সংরক্ষিত") == true)
    }

    @Test
    fun `test safe revocation migration preserves shop data while stripping super admin privilege`() = runTest(testDispatcher) {
        // Create a simulated local user that previously had SUPER_ADMIN role with revoked email
        val legacyUser = User(
            id = "usr_legacy_buddavai",
            name = "Buddavai Shop Admin (buddavaibinodon@gmail.com)",
            phone = "01755554444",
            role = UserRole.SUPER_ADMIN,
            shopId = "biz_buddavai_store_99",
            pinHash = repository.hashPin("1234"),
            isActive = true,
            accountStatus = UserAccountStatus.ACTIVE
        )
        database.userDao().insertUser(legacyUser)

        val records = superAdminRepo.executeSafeRevocationMigration(database)
        assertTrue(records.isNotEmpty())

        // Verify the user was safely migrated to regular ADMIN, NOT deleted!
        val migratedUser = database.userDao().getUserById("usr_legacy_buddavai")
        assertNotNull("User record must NOT be deleted", migratedUser)
        assertEquals("Role must be demoted to ADMIN", UserRole.ADMIN, migratedUser?.role)
        assertEquals("Shop ID must remain completely intact", "biz_buddavai_store_99", migratedUser?.shopId)
        assertEquals("Phone must remain intact", "01755554444", migratedUser?.phone)
    }

    @Test
    fun `test newly registered shop via Google Sign-In appears in Super Admin registration list`() = runTest(testDispatcher) {
        val newShopPhone = "01855667788"
        val newShopName = "আল-আমিন জেনারেল স্টোর"
        val ownerGoogleUid = "google_auth_uid_alamin_999"
        val ownerGoogleEmail = "alamin.store99@gmail.com"

        var regSuccess = false
        var registeredShopId: String? = null

        viewModel.registerNewShop(
            shopName = newShopName,
            ownerName = "মোঃ আল-আমিন",
            phone = newShopPhone,
            address = "মিরপুর ১০, ঢাকা",
            district = "ঢাকা",
            businessType = "মুদি ও জেনারেল স্টোর",
            pin = "5678",
            isPhoneVerified = false,
            ownerEmail = ownerGoogleEmail,
            ownerUid = ownerGoogleUid,
            superAdminViewModel = superAdminViewModel
        ) { success, _, shopId ->
            regSuccess = success
            registeredShopId = shopId
        }

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("Shop registration must succeed", regSuccess)
        assertNotNull("Registered shop ID must not be null", registeredShopId)

        // Verify the new shop is in SuperAdminRepository
        val allBusinesses = superAdminRepo.businesses.value
        val foundInSuperAdmin = allBusinesses.firstOrNull { it.id == registeredShopId }
        assertNotNull("Newly registered shop must appear in SuperAdmin businesses list", foundInSuperAdmin)
        assertEquals(newShopName, foundInSuperAdmin?.businessName)
        assertEquals(newShopPhone, foundInSuperAdmin?.ownerPhone)
        assertEquals(BusinessAccountStatus.PENDING, foundInSuperAdmin?.status)
        assertEquals(ownerGoogleEmail, foundInSuperAdmin?.ownerEmail)

        // Verify findBusinessByOwner resolves the shop by Google UID and email
        val byUid = superAdminRepo.findBusinessByOwner(ownerGoogleUid, "")
        assertNotNull("Shop must be resolvable by Google UID", byUid)
        assertEquals(registeredShopId, byUid?.id)

        val byEmail = superAdminRepo.findBusinessByOwner("", ownerGoogleEmail)
        assertNotNull("Shop must be resolvable by owner email", byEmail)
        assertEquals(registeredShopId, byEmail?.id)
    }

    @Test
    fun `test new shopkeeper with Google Sign-In NEVER gets Super Admin access`() = runTest(testDispatcher) {
        val shopPhone = "01944556677"
        val shopName = "নূর ফ্যাশন"
        val googleUid = "google_uid_new_owner_77"
        val googleEmail = "new.shopkeeper@gmail.com"

        // 1. Register shop
        var regSuccess = false
        var regMsg: String? = null
        var createdShopId: String? = null
        viewModel.registerNewShop(
            shopName = shopName,
            ownerName = "নূর ইসলাম",
            phone = shopPhone,
            address = "উত্তরা, ঢাকা",
            district = "ঢাকা",
            businessType = "কাপড়ের দোকান ও টেইলার্স",
            pin = "4321",
            isPhoneVerified = false,
            ownerEmail = googleEmail,
            ownerUid = googleUid,
            superAdminViewModel = superAdminViewModel
        ) { success, msg, shopId ->
            regSuccess = success
            regMsg = msg
            createdShopId = shopId
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("Registration failed: $regMsg", regSuccess)

        // 2. Simulate logging in with Google credentials
        var loginSuccess: Boolean? = null
        var resolvedRole: UserRole? = null
        viewModel.handleGoogleUserCredentials(
            uid = googleUid,
            email = googleEmail,
            displayName = "নূর ইসলাম",
            phoneNumber = shopPhone
        ) { success, _, role ->
            loginSuccess = success
            resolvedRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("Google Sign-In should successfully log into shopkeeper's store", loginSuccess == true)
        assertEquals("Role must be regular store ADMIN, not platform super admin", UserRole.ADMIN, resolvedRole)

        // 3. Strict verification: Super Admin rights must be completely denied
        assertFalse("viewModel.isSuperAdminUser() must be FALSE for new shopkeeper", viewModel.isSuperAdminUser())
        assertFalse("isSuperAdminSession must be FALSE", viewModel.isSuperAdminSession.value)
        assertEquals("getSuperAdminEmail() must be empty for regular shopkeeper", "", viewModel.getSuperAdminEmail())

        // 4. Multi-tenant isolation: shopkeeper belongs strictly to their own shop
        val currentUser = viewModel.currentUser.value
        assertNotNull(currentUser)
        assertEquals(createdShopId, currentUser?.shopId)
        assertNotEquals("Must not map to primary platform shop", "biz_smart_store_1", currentUser?.shopId)
    }
}
