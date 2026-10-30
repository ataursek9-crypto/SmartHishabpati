package com.example

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.UserRole
import com.example.data.repository.SuperAdminRepository
import com.example.data.sync.CloudBusinessAuth
import com.example.util.GoogleSignInHelper
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleSignInSafetyAndCrashPreventionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var viewModel: BusinessViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        CloudBusinessAuth.clear(app)
        val directExecutor = java.util.concurrent.Executor { it.run() }
        db = androidx.room.Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        AppDatabase.setInstanceForTesting(db)
        SuperAdminRepository.getInstance(app).resetStateForTesting()
        viewModel = BusinessViewModel(app)
    }

    @After
    fun tearDown() {
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        viewModel.syncManager.cancelActiveSync()
        CloudBusinessAuth.clear(app)
        SuperAdminRepository.getInstance(app).resetStateForTesting()
        AppDatabase.setInstanceForTesting(null)
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testWebClientIdResolution() {
        val clientId = GoogleSignInHelper.getWebClientId(app)
        assertNotNull("Web Client ID must not be null", clientId)
        assertTrue("Web Client ID must not be blank", clientId.isNotBlank())
        assertTrue("Web Client ID must contain client ID prefix", clientId.contains("484040397249"))
        assertTrue("Web Client ID must end with apps.googleusercontent.com", clientId.endsWith(".apps.googleusercontent.com"))
    }

    @Test
    fun testSigningFingerprintResolution() {
        val sha1 = GoogleSignInHelper.getSigningSha1(app)
        assertNotNull(sha1)
        assertTrue("SHA1 must have colon-separated format", sha1.contains(":"))
        val parts = sha1.split(":")
        assertEquals("SHA1 must have 20 bytes", 20, parts.size)

        val sha256 = GoogleSignInHelper.getSigningSha256(app)
        assertNotNull(sha256)
        assertTrue("SHA256 must have colon-separated format", sha256.contains(":"))
        val sha256Parts = sha256.split(":")
        assertEquals("SHA256 must have 32 bytes", 32, sha256Parts.size)
    }

    @Test
    fun testBengaliErrorMessages() {
        val netErr = com.google.firebase.FirebaseNetworkException("Network error")
        val netMsg = GoogleSignInHelper.getBengaliErrorMessage(netErr)
        assertTrue("Network error must mention internet in Bengali", netMsg.contains("ইন্টারনেট"))

        val cancelErr = androidx.credentials.exceptions.GetCredentialCancellationException("User cancelled")
        val cancelMsg = GoogleSignInHelper.getBengaliErrorMessage(cancelErr)
        assertTrue("Cancel error must mention cancelled in Bengali", cancelMsg.contains("বাতিল"))

        val noCredErr = androidx.credentials.exceptions.NoCredentialException("No accounts found")
        val noCredMsg = GoogleSignInHelper.getBengaliErrorMessage(noCredErr)
        assertTrue("NoCredential error must mention Google account in Bengali", noCredMsg.contains("গুগল একাউন্ট"))
    }

    @Test
    fun testFinishingActivityHandlingDoesNotCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        activity.finish() // Mark as finishing

        var errorReported: String? = null
        GoogleSignInHelper.launchGoogleSignIn(
            context = activity,
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            onAuthSuccess = { fail("Should not succeed on destroyed activity") },
            onCancelled = { fail("Should not cancel on destroyed activity") },
            onError = { err -> errorReported = err }
        )

        assertNotNull("Should safely report error without throwing unhandled exception", errorReported)
        assertTrue("Error should mention activity or system error", errorReported!!.contains("অ্যাক্টিভিটি") || errorReported!!.contains("ত্রুটি"))
    }

    @Test
    fun testNonActivityContextHandlingDoesNotCrash() {
        var errorReported: String? = null
        GoogleSignInHelper.launchGoogleSignIn(
            context = app.applicationContext, // Application context, not Activity
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            onAuthSuccess = { fail("Should not succeed with non-activity context") },
            onCancelled = { fail("Should not cancel with non-activity context") },
            onError = { err -> errorReported = err }
        )

        assertNotNull("Should safely report error without throwing exception", errorReported)
        assertTrue("Error should mention activity is missing", errorReported!!.contains("অ্যাক্টিভিটি"))
    }

    @Test
    fun testUnknownGoogleAccountStrictlyRejected() = runTest(testDispatcher) {
        var callbackSuccess: Boolean? = null
        var callbackMsg: String? = null
        var callbackRole: UserRole? = null

        viewModel.handleGoogleUserCredentials(
            uid = "fake_unauthorized_user_id_456",
            email = "intruder@malicious.com",
            displayName = "অচেনা আক্রমণকারী",
            phoneNumber = null
        ) { success, msg, role ->
            callbackSuccess = success
            callbackMsg = msg
            callbackRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertFalse("Unauthorized Google user must not succeed", callbackSuccess == true)
        assertNull("Role must be null for unauthorized user", callbackRole)
        assertNull("Current active user must remain null", viewModel.currentUser.value)
        assertTrue("Error message must inform user that account is not authorized", callbackMsg!!.contains("অনুমোদিত"))
    }

    @Test
    fun testRevokedGoogleAccountCannotLoginAsSuperAdmin() = runTest(testDispatcher) {
        val revokedEmail = "a48268993@gmail.com"
        assertFalse("Revoked email must not have super admin authorization", viewModel.repository.isAuthorizedSuperAdminEmail(revokedEmail))
        assertTrue("Revoked email must be in revoked list", viewModel.repository.isRevokedSuperAdminEmail(revokedEmail))

        var callbackSuccess: Boolean? = null
        var callbackRole: UserRole? = null

        viewModel.handleGoogleUserCredentials(
            uid = "revoked_user_uid_123",
            email = revokedEmail,
            displayName = "Revoked User",
            phoneNumber = null
        ) { success, _, role ->
            callbackSuccess = success
            callbackRole = role
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Revoked super admin must not log in directly without shop association", callbackSuccess == true)
        assertFalse("Super admin privileges must not be granted", viewModel.isSuperAdminUser())
    }
}
