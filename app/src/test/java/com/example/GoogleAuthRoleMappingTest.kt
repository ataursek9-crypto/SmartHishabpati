package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.User
import com.example.data.model.UserAccountStatus
import com.example.data.model.UserRole
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SuperAdminRepository
import com.example.viewmodel.BusinessViewModel
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
class GoogleAuthRoleMappingTest {

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
    fun `test unknown Google account is strictly rejected and never granted admin role`() = runTest(testDispatcher) {
        var callbackSuccess: Boolean? = null
        var callbackMsg: String? = null
        var callbackRole: UserRole? = null

        viewModel.handleGoogleUserCredentials(
            uid = "unknown_uid_123",
            email = "stranger@unknown-domain.com",
            displayName = "অচেনা ব্যক্তি",
            phoneNumber = null
        ) { success, msg, role ->
            callbackSuccess = success
            callbackMsg = msg
            callbackRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse("Unknown Google user must not be granted access", callbackSuccess == true)
        assertNull("Role must be null for unauthorized account", callbackRole)
        assertNull("Current active user must remain null", viewModel.currentUser.value)
        assertTrue("Error message must inform user that account is not authorized", callbackMsg!!.contains("অনুমোদিত"))
    }

    @Test
    fun `test primary business owner Google email is recognized and assigned ADMIN role`() = runTest(testDispatcher) {
        var callbackSuccess: Boolean? = null
        var callbackRole: UserRole? = null

        viewModel.handleGoogleUserCredentials(
            uid = "owner_google_uid_001",
            email = "ataurgonee4@gmail.com",
            displayName = "আতাউর রহমান",
            phoneNumber = "01700000000"
        ) { success, _, role ->
            callbackSuccess = success
            callbackRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("Primary business owner must succeed", callbackSuccess == true)
        assertEquals("Primary owner must be assigned ADMIN role", UserRole.ADMIN, callbackRole)
        assertEquals(UserRole.ADMIN, viewModel.currentUser.value?.role)
        assertEquals("owner_google_uid_001", viewModel.currentUser.value?.id)
    }

    @Test
    fun `test ataursek9@gmail com is recognized as primary business owner and assigned ADMIN role`() = runTest(testDispatcher) {
        var callbackSuccess: Boolean? = null
        var callbackRole: UserRole? = null

        viewModel.handleGoogleUserCredentials(
            uid = "owner_ataursek9_uid",
            email = "ataursek9@gmail.com",
            displayName = "আতাউর রহমান",
            phoneNumber = "01700000000"
        ) { success, _, role ->
            callbackSuccess = success
            callbackRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("ataursek9@gmail.com owner authentication must succeed", callbackSuccess == true)
        assertEquals("Must be assigned ADMIN role", UserRole.ADMIN, callbackRole)
        assertEquals(UserRole.ADMIN, viewModel.currentUser.value?.role)
        assertEquals("owner_ataursek9_uid", viewModel.currentUser.value?.id)
    }

    @Test
    fun `test linking Google account with valid local salesman PIN assigns SALESMAN role`() = runTest(testDispatcher) {
        val salesmanPinHash = repo.hashPin("5555")
        val localSalesman = User(
            id = "salesman_local_01",
            name = "করিম সেলস",
            phone = "01811223344",
            role = UserRole.SALESMAN,
            pinHash = salesmanPinHash,
            isActive = true,
            accountStatus = UserAccountStatus.ACTIVE
        )
        viewModel.repository.saveUserDirect(localSalesman)

        var linkSuccess: Boolean? = null
        var linkRole: UserRole? = null

        viewModel.linkGoogleCredentialsWithPin(
            uid = "google_salesman_uid_777",
            displayName = "করিম শেখ",
            phone = "01811223344",
            pin = "5555"
        ) { success, _, role ->
            linkSuccess = success
            linkRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("Linking with correct PIN must succeed", linkSuccess == true)
        assertEquals("Assigned role must be SALESMAN", UserRole.SALESMAN, linkRole)
        assertEquals("google_salesman_uid_777", viewModel.currentUser.value?.id)
        assertEquals(UserRole.SALESMAN, viewModel.currentUser.value?.role)
    }

    @Test
    fun `test linking Google account with wrong PIN fails and rejects login`() = runTest(testDispatcher) {
        val salesmanPinHash = repo.hashPin("5555")
        val localSalesman = User(
            id = "salesman_local_02",
            name = "করিম সেলস",
            phone = "01899887766",
            role = UserRole.SALESMAN,
            pinHash = salesmanPinHash,
            isActive = true,
            accountStatus = UserAccountStatus.ACTIVE
        )
        viewModel.repository.saveUserDirect(localSalesman)

        var linkSuccess: Boolean? = null

        viewModel.linkGoogleCredentialsWithPin(
            uid = "google_salesman_uid_888",
            displayName = "হ্যাকার",
            phone = "01899887766",
            pin = "wrong_pin"
        ) { success, _, _ ->
            linkSuccess = success
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse("Linking with incorrect PIN must fail", linkSuccess == true)
    }

    @Test
    fun `test phone normalization handles international prefix spaces and local formats`() {
        assertEquals("01811223344", com.example.data.sync.CloudBusinessAuth.normalizePhone("+8801811223344"))
        assertEquals("01811223344", com.example.data.sync.CloudBusinessAuth.normalizePhone("8801811223344"))
        assertEquals("01811223344", com.example.data.sync.CloudBusinessAuth.normalizePhone("01811-223344"))
        assertEquals("01811223344", com.example.data.sync.CloudBusinessAuth.normalizePhone(" 01811 223344 "))
        assertEquals("01700000000", com.example.data.sync.CloudBusinessAuth.normalizePhone("+8801700000000"))
    }

    @Test
    fun `test connectSalesmanToBusiness rejects blank inputs without network call`() {
        var callbackSuccess: Boolean? = null
        var callbackMsg: String? = null

        viewModel.connectSalesmanToBusiness(
            businessId = "",
            phone = "01811223344",
            pin = "1234"
        ) { success, msg ->
            callbackSuccess = success
            callbackMsg = msg
        }
        assertFalse("Blank business ID must be rejected", callbackSuccess == true)
        assertTrue("Error must mention business code", callbackMsg!!.contains("বিজনেস কোড"))
    }

    @Test
    fun `test all authorized Super Admin emails are recognized and grant super admin status`() = runTest(testDispatcher) {
        val authorizedEmails = listOf(
            "ataurgonee4@gmail.com",
            "ataursek9@gmail.com"
        )

        for (email in authorizedEmails) {
            assertTrue("Email $email must be recognized as authorized Super Admin", repo.isAuthorizedSuperAdminEmail(email))
        }

        // Test revoked emails
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
            assertFalse("Email $email must NOT be authorized as Super Admin", repo.isAuthorizedSuperAdminEmail(email))
            assertTrue("Email $email must be marked as revoked", repo.isRevokedSuperAdminEmail(email))
        }

        // Test login with authorized super admin
        val superAdminEmail = "ataurgonee4@gmail.com"
        var loginSuccess: Boolean? = null
        var loginRole: UserRole? = null

        viewModel.handleGoogleUserCredentials(
            uid = "super_admin_ataurgonee4_uid",
            email = superAdminEmail,
            displayName = "Super Admin Test",
            phoneNumber = null
        ) { success, _, role ->
            loginSuccess = success
            loginRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertTrue("Super Admin login must succeed", loginSuccess == true)
        assertEquals(UserRole.ADMIN, loginRole)
        assertTrue("viewModel.isSuperAdminUser() must be true for authorized email", viewModel.isSuperAdminUser())
        assertEquals(superAdminEmail, viewModel.getSuperAdminEmail())
    }

    @Test
    fun `test regular Google user is NOT granted Super Admin access`() = runTest(testDispatcher) {
        val regularEmail = "ordinary.shopkeeper@example.com"
        assertFalse(repo.isAuthorizedSuperAdminEmail(regularEmail))

        var loginSuccess: Boolean? = null
        var loginRole: UserRole? = null

        viewModel.handleGoogleUserCredentials(
            uid = "regular_shopkeeper_uid_99",
            email = regularEmail,
            displayName = "দোকানদার ভাই",
            phoneNumber = null
        ) { success, _, role ->
            loginSuccess = success
            loginRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse("Unlinked regular user should not log in directly without linking or registration", loginSuccess == true)
        assertFalse("viewModel.isSuperAdminUser() must be FALSE for regular user", viewModel.isSuperAdminUser())
    }
}

