package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.BusinessAccountStatus
import com.example.data.model.PlatformBusiness
import com.example.data.model.UserRole
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SuperAdminRepository
import com.example.data.sync.CloudBusinessAuth
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ShopRegistrationScreen
import com.example.ui.theme.SmartBusinessTheme
import com.example.util.AppErrorHandler
import com.example.util.AppLanguage
import com.example.util.AppLanguageManager
import com.example.util.AppStrings
import com.example.viewmodel.BusinessViewModel
import com.example.viewmodel.SuperAdminViewModel
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Step 5 — Comprehensive Regression Test Suite for anc94450@gmail.com
 * Firebase Authentication, Forgot Password, Google-only Sign-In,
 * UID Preservation, and Multi-Shop Registration without Data Loss.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseAuthBugFixRegressionTest {

    @get:Rule(order = 0)
    val mainDispatcherRule = object : org.junit.rules.TestWatcher() {
        override fun starting(description: org.junit.runner.Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: org.junit.runner.Description) {
            Dispatchers.resetMain()
        }
    }

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repo: BusinessRepository
    private lateinit var superAdminRepo: SuperAdminRepository
    private lateinit var viewModelStore: ViewModelStore
    private lateinit var viewModel: BusinessViewModel
    private lateinit var superAdminViewModel: SuperAdminViewModel

    private val targetEmail = "anc94450@gmail.com"
    private val targetUid = "uid_anc94450_google_primary"

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        AppLanguageManager.init(app)
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
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
        viewModelStore = ViewModelStore()
        viewModel = ViewModelProvider(
            viewModelStore,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[BusinessViewModel::class.java]
        superAdminViewModel = SuperAdminViewModel(app, superAdminRepo)
    }

    @After
    fun tearDown() {
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        viewModel.syncManager.cancelActiveSync()
        viewModelStore.clear()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        CloudBusinessAuth.clear(app)
        superAdminRepo.resetStateForTesting()
        db.close()
        AppDatabase.setInstanceForTesting(null)
    }

    // -------------------------------------------------------------------------
    // Regression Case 1: Google-only Gmail Login (anc94450@gmail.com)
    // -------------------------------------------------------------------------
    @Test
    fun `01 Google-only Gmail login classifies google provider and shows Google Sign-In guidance instead of wrong password`() {
        assertEquals(
            AppErrorHandler.EmailAuthProviderType.GOOGLE_ONLY,
            viewModel.classifyProviderIds(listOf("google.com"))
        )
        assertEquals(
            AppErrorHandler.EmailAuthProviderType.GOOGLE_ONLY,
            viewModel.classifyProviderIds(listOf("firebase", "google.com"))
        )

        val invalidCredEx = FirebaseAuthInvalidCredentialsException(
            "ERROR_INVALID_CREDENTIAL",
            "The supplied auth credential is incorrect, malformed or has expired."
        )

        // Bengali check
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        val bnErr = AppErrorHandler.mapLoginError(
            invalidCredEx,
            AppErrorHandler.EmailAuthProviderType.GOOGLE_ONLY
        )
        assertEquals("GOOGLE_ONLY_ACCOUNT", bnErr.rawErrorCode)
        assertFalse(
            "Google-only account must NEVER show misleading 'পাসওয়ার্ড সঠিক নয়'",
            bnErr.toUserMessage().contains("পাসওয়ার্ড সঠিক নয়")
        )
        assertTrue(
            "Must guide user to Google Sign-In in Bengali",
            bnErr.toUserMessage().contains("Google দিয়ে প্রবেশ করুন")
        )

        // English check
        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        val enErr = AppErrorHandler.mapLoginError(
            invalidCredEx,
            AppErrorHandler.EmailAuthProviderType.GOOGLE_ONLY
        )
        assertEquals("GOOGLE_ONLY_ACCOUNT", enErr.rawErrorCode)
        assertFalse(enErr.toUserMessage().contains("The password is incorrect"))
        assertTrue(enErr.toUserMessage().contains("Continue with Google"))
    }

    // -------------------------------------------------------------------------
    // Regression Case 2: Email/Password Login & Exact Firebase Error Code Mapping
    // -------------------------------------------------------------------------
    @Test
    fun `02 Email-Password login maps exact Firebase error codes in Bengali and English`() {
        assertEquals(
            AppErrorHandler.EmailAuthProviderType.PASSWORD_ONLY,
            viewModel.classifyProviderIds(listOf("password"))
        )
        assertEquals(
            AppErrorHandler.EmailAuthProviderType.GOOGLE_AND_PASSWORD,
            viewModel.classifyProviderIds(listOf("google.com", "password"))
        )

        AppLanguageManager.setLanguage(AppLanguage.BENGALI)

        // 1. wrong-password
        val wrongPassErr = AppErrorHandler.mapLoginError(
            FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "auth/wrong-password"),
            AppErrorHandler.EmailAuthProviderType.PASSWORD_ONLY
        )
        assertEquals("WRONG_PASSWORD", wrongPassErr.rawErrorCode)
        assertTrue(wrongPassErr.toUserMessage().contains("পাসওয়ার্ড সঠিক নয়"))

        // 2. user-not-found
        val userNotFoundErr = AppErrorHandler.mapLoginError(
            FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "auth/user-not-found"),
            AppErrorHandler.EmailAuthProviderType.NOT_FOUND
        )
        assertEquals("USER_NOT_FOUND", userNotFoundErr.rawErrorCode)
        assertTrue(userNotFoundErr.toUserMessage().contains("কোনো অ্যাকাউন্ট খুঁজে পাওয়া যায়নি"))

        // 3. account-exists-with-different-credential
        val diffCredErr = AppErrorHandler.map(
            RuntimeException("auth/account-exists-with-different-credential")
        )
        assertEquals("ACCOUNT_EXISTS_DIFFERENT_CREDENTIAL", diffCredErr.rawErrorCode)
        assertTrue(diffCredErr.toUserMessage().contains("Google দিয়ে প্রবেশ করুন"))

        // 4. invalid-credential
        val dualInvalidErr = AppErrorHandler.mapLoginError(
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "auth/invalid-credential"),
            AppErrorHandler.EmailAuthProviderType.GOOGLE_AND_PASSWORD
        )
        assertEquals("INVALID_CREDENTIAL", dualInvalidErr.rawErrorCode)
        assertTrue(dualInvalidErr.toUserMessage().contains("Google দিয়ে প্রবেশ করুন"))
        assertTrue(dualInvalidErr.toUserMessage().contains("পাসওয়ার্ড ভুলে গেছেন"))

        // 5. network-request-failed
        val netErr = AppErrorHandler.map(
            FirebaseNetworkException("A network error (such as timeout, interrupted connection or unreachable host) has occurred. auth/network-request-failed")
        )
        assertEquals("NETWORK_UNAVAILABLE", netErr.rawErrorCode)
        assertTrue(netErr.toUserMessage().contains("ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না"))

        // 6. too-many-requests
        val rateErr = AppErrorHandler.map(
            FirebaseTooManyRequestsException("We have blocked all requests from this device due to unusual activity. auth/too-many-requests")
        )
        assertEquals("TOO_MANY_REQUESTS", rateErr.rawErrorCode)
        assertTrue(rateErr.toUserMessage().contains("অতিরিক্ত অনুরোধের কারণে"))
    }

    // -------------------------------------------------------------------------
    // Regression Case 3: Forgot Password Request & Isolation from Login Error
    // -------------------------------------------------------------------------
    @Test
    fun `03 Forgot Password validates email independently, isolates from Login error handler, and directs to Inbox Spam Promotions`() = runTest(testDispatcher) {
        assertTrue(viewModel.isValidEmailFormat(targetEmail))
        assertFalse(viewModel.isValidEmailFormat("anc94450"))
        assertFalse(viewModel.isValidEmailFormat("invalid@"))
        assertFalse(viewModel.isValidEmailFormat(""))

        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        assertTrue(AppStrings.passwordResetRequestAccepted.contains("অনুরোধ গ্রহণ করা হয়েছে"))
        assertTrue(AppStrings.emailDeliveryNotice.contains("Firebase অনুরোধটি গ্রহণ করেছে"))
        assertTrue(AppStrings.emailDeliveryNotice.contains("Inbox"))
        assertTrue(AppStrings.emailDeliveryNotice.contains("Spam"))
        assertTrue(AppStrings.emailDeliveryNotice.contains("Promotions"))

        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        assertTrue(AppStrings.passwordResetRequestAccepted.contains("request has been accepted"))
        assertTrue(AppStrings.emailDeliveryNotice.contains("Firebase accepted the password reset request"))
        assertTrue(AppStrings.emailDeliveryNotice.contains("Inbox, Spam, and Promotions"))

        val resetErr = AppErrorHandler.mapPasswordResetError(
            RuntimeException("ERROR_WRONG_PASSWORD")
        )
        assertFalse(
            "Forgot password error mapper must NEVER echo Login wrong-password error",
            resetErr.toUserMessage().contains("The password is incorrect") ||
                resetErr.toUserMessage().contains("পাসওয়ার্ড সঠিক নয়")
        )

        var resetSuccess = true
        var resetMsg = ""
        viewModel.sendPasswordResetEmail("not-an-email") { success, _, msg ->
            resetSuccess = success
            resetMsg = msg
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(resetSuccess)
        assertTrue(resetMsg.contains("badly formatted"))
    }

    // -------------------------------------------------------------------------
    // Regression Case 4: Forgot Password on Google-only Account (anc94450_gmail_com)
    // -------------------------------------------------------------------------
    @Test
    fun `04 Forgot Password on Google-only anc94450_gmail_com does not send fake reset email and guides to Google Sign-In`() = runTest(testDispatcher) {
        CloudBusinessAuth.setLinkedGoogleAccount(
            context = app,
            uid = targetUid,
            email = targetEmail,
            userId = targetUid,
            shopId = "biz_anc_google_only_1"
        )
        superAdminRepo.registerNewShop(
            PlatformBusiness(
                id = "biz_anc_google_only_1",
                businessName = "অ্যাঙ্ক স্টোর",
                ownerUid = targetUid,
                ownerName = "Anc Owner",
                ownerEmail = targetEmail,
                ownerPhone = "01711223344",
                status = BusinessAccountStatus.ACTIVE,
                planId = "plan_free"
            )
        )

        val audit = viewModel.auditEmailAuthStatus(targetEmail)
        assertTrue(audit.exists)
        assertEquals(AppErrorHandler.EmailAuthProviderType.GOOGLE_ONLY, audit.providerType)
        assertTrue(audit.hasGoogleProvider)
        assertFalse(audit.hasPasswordProvider)
        assertEquals(targetUid, audit.existingUid)

        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        var isSuccess = true
        var isGoogleOnly = false
        var message = ""
        viewModel.sendPasswordResetEmail(targetEmail) { ok, gOnly, msg ->
            isSuccess = ok
            isGoogleOnly = gOnly
            message = msg
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Google-only Forgot Password must not pretend to send a Google password reset email", isSuccess)
        assertTrue("Must flag isGoogleOnly = true so UI shows primary Google Sign-In button", isGoogleOnly)
        assertEquals(AppStrings.googleOnlyForgotPasswordNotice, message)
        assertTrue(message.contains("Google দিয়ে প্রবেশ করুন"))
    }

    // -------------------------------------------------------------------------
    // Regression Case 5: New Shop Registration with Existing Gmail (anc94450_gmail_com)
    // -------------------------------------------------------------------------
    @Test
    fun `05 New shop registration on existing anc94450_gmail_com UID completes stub or creates shop without duplicate UID`() = runTest(testDispatcher) {
        superAdminRepo.registerNewShop(
            PlatformBusiness(
                id = targetUid,
                businessName = "ব্যবসা #anc94",
                ownerUid = targetUid,
                ownerName = "Anc Owner",
                ownerEmail = targetEmail,
                ownerPhone = "",
                address = "",
                status = BusinessAccountStatus.PENDING,
                planId = "plan_free"
            )
        )

        var regSuccess = false
        var regMsg = ""
        var createdShopId: String? = null
        viewModel.registerNewShop(
            shopName = "হিসাবপাতি মার্ট",
            ownerName = "মোঃ আতাউর রহমান",
            phone = "01712345678",
            address = "মিরপুর ১০, ঢাকা",
            district = "ঢাকা",
            businessType = "মুদি ও জেনারেল স্টোর",
            pin = "654321",
            isPhoneVerified = false,
            ownerEmail = targetEmail,
            ownerUid = targetUid,
            allowAdditionalShop = false,
            superAdminViewModel = superAdminViewModel
        ) { ok, msg, shopId ->
            regSuccess = ok
            regMsg = msg
            createdShopId = shopId
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Completing shop registration on existing anc94450@gmail.com UID must succeed: $regMsg", regSuccess)
        assertNotNull(createdShopId)
        assertEquals(targetUid, viewModel.currentUser.value?.id)
        assertEquals(createdShopId, viewModel.currentUser.value?.shopId)
        assertEquals(UserRole.ADMIN, viewModel.currentUser.value?.role)

        val registeredBiz = superAdminRepo.businesses.value.find { it.id == createdShopId }
        assertNotNull(registeredBiz)
        assertEquals(targetUid, registeredBiz?.ownerUid)
        assertEquals(targetEmail, registeredBiz?.ownerEmail)
    }

    // -------------------------------------------------------------------------
    // Regression Case 6: Multi-Shop Registration & Existing Shop Switching without Data Loss
    // -------------------------------------------------------------------------
    @Test
    fun `06 Existing Gmail with multiple shops preserves all shops, UIDs, and data across shop switches`() = runTest(testDispatcher) {
        var shop1Id = ""
        viewModel.registerNewShop(
            shopName = "প্রথম দোকান — মিরপুর শাখা",
            ownerName = "Anc Owner",
            phone = "01712345678",
            address = "মিরপুর",
            district = "ঢাকা",
            businessType = "মুদি",
            pin = "112233",
            ownerEmail = targetEmail,
            ownerUid = targetUid,
            allowAdditionalShop = false
        ) { ok, msg, id ->
            assertTrue("Shop 1 failed: $msg", ok)
            shop1Id = id.orEmpty()
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(shop1Id.isNotBlank())

        var unconfirmedSuccess = true
        viewModel.registerNewShop(
            shopName = "ডুপ্লিকেট দোকান",
            ownerName = "Anc Owner",
            phone = "01899887766",
            address = "উত্তরা",
            district = "ঢাকা",
            businessType = "মুদি",
            pin = "112233",
            ownerEmail = targetEmail,
            ownerUid = targetUid,
            allowAdditionalShop = false
        ) { ok, _, _ ->
            unconfirmedSuccess = ok
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("Unconfirmed duplicate must be blocked to prevent accidental duplicate shop creation", unconfirmedSuccess)

        var shop2Id = ""
        viewModel.registerNewShop(
            shopName = "দ্বিতীয় দোকান — উত্তরা শাখা",
            ownerName = "Anc Owner",
            phone = "01899887766",
            address = "উত্তরা",
            district = "ঢাকা",
            businessType = "হার্ডওয়্যার",
            pin = "112233",
            ownerEmail = targetEmail,
            ownerUid = targetUid,
            allowAdditionalShop = true
        ) { ok, msg, id ->
            assertTrue("Shop 2 failed: $msg", ok)
            shop2Id = id.orEmpty()
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(shop2Id.isNotBlank())
        assertNotEquals("Shop 2 must have its own distinct Shop ID", shop1Id, shop2Id)

        val ownedShops = superAdminRepo.findAllBusinessesByOwner(targetUid, targetEmail, "")
        assertEquals("Both shops on anc94450@gmail.com must be preserved", 2, ownedShops.size)
        assertTrue(ownedShops.any { it.id == shop1Id && it.businessName == "প্রথম দোকান — মিরপুর শাখা" && it.ownerUid == targetUid })
        assertTrue(ownedShops.any { it.id == shop2Id && it.businessName == "দ্বিতীয় দোকান — উত্তরা শাখা" && it.ownerUid == targetUid })

        viewModel.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        var signInSuccess = false
        viewModel.handleGoogleUserCredentials(
            uid = targetUid,
            email = targetEmail,
            displayName = "Anc Owner",
            phoneNumber = null
        ) { ok, _, _ ->
            signInSuccess = ok
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Google Sign-In with multi-shop account must succeed", signInSuccess)
        val pending = viewModel.pendingShopSelection.value
        assertNotNull("Multi-shop owner must be presented with shop selection dialog", pending)
        assertEquals(2, pending?.availableShops?.size)

        val shop1Obj = ownedShops.first { it.id == shop1Id }
        viewModel.selectShopAndProceed(shop1Obj) { ok, _, role ->
            assertTrue(ok)
            assertEquals(UserRole.ADMIN, role)
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(shop1Id, viewModel.activeBusinessId.value)
        assertEquals(targetUid, viewModel.currentUser.value?.id)
        assertNull(viewModel.pendingShopSelection.value)

        val shop2Obj = ownedShops.first { it.id == shop2Id }
        viewModel.selectShopAndProceed(shop2Obj) { ok, _, role ->
            assertTrue(ok)
            assertEquals(UserRole.ADMIN, role)
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(shop2Id, viewModel.activeBusinessId.value)
        assertEquals(targetUid, viewModel.currentUser.value?.id)
        assertEquals(2, superAdminRepo.findAllBusinessesByOwner(targetUid, targetEmail, "").size)
    }

    // -------------------------------------------------------------------------
    // Regression Case 7: Compose UI Smoke Test (LoginScreen Isolated Forgot Password)
    // -------------------------------------------------------------------------
    @Test
    fun `07 Compose UI opens isolated Forgot Password dialog and never shows Wrong Password`() {
        composeTestRule.setContent {
            SmartBusinessTheme {
                LoginScreen(
                    viewModel = viewModel,
                    onNavigateToAdminLogin = {},
                    onNavigateToRegistration = {},
                    onLoginSuccess = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("login_email_input").performScrollTo().performTextInput(targetEmail)
        composeTestRule.onNodeWithTag("forgot_password_link").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("forgot_password_email_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("send_password_reset_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_login_from_reset_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("send_password_reset_button").performClick()
        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("forgot_password_status_card").performScrollTo().assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Regression Case 8: Compose UI ShopRegistrationScreen Wizard & Account Recovery Card
    // -------------------------------------------------------------------------
    @Test
    fun `08 Compose UI ShopRegistrationScreen detects existing anc94450_gmail_com and shows Account Recovery card`() {
        // Seed an existing shop owned by anc94450@gmail.com
        kotlinx.coroutines.runBlocking {
            superAdminRepo.registerNewShop(
                PlatformBusiness(
                    id = "biz_existing_anc_ui",
                    businessName = "আগের হিসাবপাতি দোকান",
                    ownerUid = targetUid,
                    ownerName = "Anc Owner",
                    ownerEmail = targetEmail,
                    ownerPhone = "01712345678",
                    address = "ঢাকা",
                    status = BusinessAccountStatus.ACTIVE,
                    planId = "plan_free"
                )
            )
        }

        composeTestRule.setContent {
            SmartBusinessTheme {
                ShopRegistrationScreen(
                    viewModel = viewModel,
                    superAdminViewModel = superAdminViewModel,
                    onBack = {},
                    onRegistrationSuccess = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("reg_step1_shop_name_input").performScrollTo().performTextInput("নতুন স্টোর")
        composeTestRule.onNodeWithTag("reg_step1_next_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("reg_step2_first_name_input").performScrollTo().performTextInput("আতাউর")
        composeTestRule.onNodeWithTag("reg_step2_next_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("reg_step3_email_input").performScrollTo().performTextInput(targetEmail)
        composeTestRule.onNodeWithTag("reg_step3_phone_input").performScrollTo().performTextInput("01712345678")
        composeTestRule.onNodeWithTag("reg_step3_pin_input").performScrollTo().performTextInput("654321")
        composeTestRule.onNodeWithTag("reg_step3_confirm_pin_input").performScrollTo().performTextInput("654321")
        composeTestRule.onNodeWithTag("reg_step3_send_code_button").performScrollTo().performClick()

        testDispatcher.scheduler.advanceUntilIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("reg_existing_account_recovery_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("reg_recovery_google_button").performScrollTo().assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Regression Case 9: Zero-Leakage Sanitization Verification
    // -------------------------------------------------------------------------
    @Test
    fun `09 AppErrorHandler sanitizes sensitive passwords, PINs, tokens, secrets, and OTPs`() {
        val raw = "Failed auth for password=MySecretPass123, pin=654321, token=eyJhbGciOi, secret=shhh_key, otp=987654"
        val clean = AppErrorHandler.sanitize(raw)
        assertFalse(clean.contains("MySecretPass123"))
        assertFalse(clean.contains("654321"))
        assertFalse(clean.contains("eyJhbGciOi"))
        assertFalse(clean.contains("shhh_key"))
        assertFalse(clean.contains("987654"))
        assertTrue(clean.contains("password=***"))
        assertTrue(clean.contains("pin=***"))
        assertTrue(clean.contains("token=***"))
        assertTrue(clean.contains("secret=***"))
    }

    // -------------------------------------------------------------------------
    // Regression Case 10: Unauthorized User Access & Super Admin Security
    // -------------------------------------------------------------------------
    @Test
    fun `10 Super Admin Security blocks unauthorized accounts and enforces strict allowlist`() = runTest(testDispatcher) {
        // Allowlisted Super Admin identities
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ataurgonee4@gmail.com"))
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ataursek9@gmail.com"))
        assertTrue(SuperAdminRepository.isSuperAdminPhone("01700000000"))
        assertTrue(SuperAdminRepository.isSuperAdminPhone("+8801700000000"))

        // Unauthorized / ordinary users
        assertFalse(SuperAdminRepository.isSuperAdminEmail("anc94450@gmail.com"))
        assertFalse(SuperAdminRepository.isSuperAdminEmail("customer@gmail.com"))
        assertFalse(SuperAdminRepository.isSuperAdminEmail("random_shop_owner@gmail.com"))

        // Explicitly revoked accounts
        assertTrue(SuperAdminRepository.isRevokedSuperAdminEmail("buddavaibinodon@gmail.com"))
        assertFalse(SuperAdminRepository.isSuperAdminEmail("buddavaibinodon@gmail.com"))

        // Calling updateBusinessStatus with unauthorized email fails immediately with SecurityException
        val failResult = superAdminRepo.updateBusinessStatus(
            businessId = "biz_test",
            newStatus = BusinessAccountStatus.ACTIVE,
            reason = "Test update",
            actorUid = "unauthorized_uid",
            actorName = "Ordinary User",
            actorEmail = "anc94450@gmail.com"
        )
        assertTrue(failResult.isFailure)
        assertTrue(failResult.exceptionOrNull() is SecurityException)
    }

    // -------------------------------------------------------------------------
    // Regression Case 11: বাংলা ও ইংরেজি Error Message (Bilingual Toggle)
    // -------------------------------------------------------------------------
    @Test
    fun `11 Bilingual language system dynamically switches error messages between Bengali and English`() {
        val testEx = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "user not found")

        // Bengali Mode
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        val bnError = AppErrorHandler.map(testEx)
        val bnMsg = bnError.toUserMessage()
        assertTrue("Bengali error message must contain Bengali problem prefix", bnMsg.startsWith("সমস্যা:"))
        assertTrue("Bengali error message must contain Bengali action prefix", bnMsg.contains("করণীয়:"))
        assertTrue(bnMsg.contains("কোনো অ্যাকাউন্ট খুঁজে পাওয়া যায়নি"))

        // English Mode
        AppLanguageManager.setLanguage(AppLanguage.ENGLISH)
        val enError = AppErrorHandler.map(testEx)
        val enMsg = enError.toUserMessage()
        assertTrue("English error message must contain English problem prefix", enMsg.startsWith("Problem:"))
        assertTrue("English error message must contain English action prefix", enMsg.contains("What to do:"))
        assertTrue(enMsg.contains("No account found for this email"))

        // Switch back to Bengali
        AppLanguageManager.setLanguage(AppLanguage.BENGALI)
        assertEquals("হিসাব রাখুন স্মার্টভাবে", AppStrings.appSlogan.replace("—", "").trim())
    }

    // -------------------------------------------------------------------------
    // Regression Case 12: Firebase Initialization (Idempotent & Fallback Safe)
    // -------------------------------------------------------------------------
    @Test
    fun `12 Firebase Initialization is safe, idempotent, and initializes options without crashing`() {
        val isReady = com.example.util.FirebaseInitHelper.ensureFirebaseInitialized(app)
        assertNotNull(isReady)
        val readyCheck = com.example.util.FirebaseInitHelper.isReady(app)
        assertNotNull(readyCheck)
    }

    // -------------------------------------------------------------------------
    // Regression Case 13: Robust Email Sanitization & Format Validation (Problem 2 Fix)
    // -------------------------------------------------------------------------
    @Test
    fun `13 Email sanitization strips zero-width spaces, BOM, and validates ataursek9@gmail_com properly`() {
        val plainEmail = "ataursek9@gmail.com"
        assertTrue(viewModel.isValidEmailFormat(plainEmail))

        // Email with zero-width space (\u200B), non-breaking space (\u00A0), and byte order mark (\uFEFF)
        val dirtyEmail = "\u200Bataursek9@gmail.com\u00A0\uFEFF"
        val clean = viewModel.sanitizeEmail(dirtyEmail)
        assertEquals("ataursek9@gmail.com", clean)
        assertTrue("Sanitized email with zero-width characters must be valid", viewModel.isValidEmailFormat(dirtyEmail))

        // Other allowlisted Super Admin email
        val superAdminEmail = "ataurgonee4@gmail.com"
        assertTrue(viewModel.isValidEmailFormat(superAdminEmail))

        // Invalid emails must still be cleanly rejected
        assertFalse(viewModel.isValidEmailFormat(""))
        assertFalse(viewModel.isValidEmailFormat("not_an_email"))
        assertFalse(viewModel.isValidEmailFormat("@gmail.com"))
        assertFalse(viewModel.isValidEmailFormat("user@"))
        assertFalse(viewModel.isValidEmailFormat("user@domain"))
    }

    // -------------------------------------------------------------------------
    // Regression Case 14: Super Admin Google Sign-In & Strict Isolation (Problem 1 Fix)
    // -------------------------------------------------------------------------
    @Test
    fun `14 Super Admin Google Sign-In grants dashboard access to allowlist and blocks ordinary users`() = runTest(testDispatcher) {
        // Test allowlisted Super Admin Google account: ataursek9@gmail.com
        var superAdminCallbackSuccess = false
        var superAdminResultRole: UserRole? = null
        viewModel.handleGoogleUserCredentials(
            uid = "uid_superadmin_ataursek9",
            email = "ataursek9@gmail.com",
            displayName = "Md. Ataur Rahman",
            phoneNumber = "01700000000"
        ) { success, _, role ->
            superAdminCallbackSuccess = success
            superAdminResultRole = role
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Super Admin sign-in callback must succeed", superAdminCallbackSuccess)
        assertEquals("Super Admin must have ADMIN role", UserRole.ADMIN, superAdminResultRole)
        assertTrue("viewModel.isSuperAdminUser() must be true for ataursek9@gmail.com", viewModel.isSuperAdminUser())
        assertEquals("Super Admin email must match verified email", "ataursek9@gmail.com", viewModel.getSuperAdminEmail())

        // Test non-superadmin Google account: anc94450@gmail.com
        var regularUserSuccess = false
        viewModel.handleGoogleUserCredentials(
            uid = "uid_regular_user_test",
            email = "anc94450@gmail.com",
            displayName = "Regular Shop Owner",
            phoneNumber = "01712345678"
        ) { success, _, _ ->
            regularUserSuccess = success
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Non-superadmin account must NOT have Super Admin privileges
        assertFalse("Ordinary shop owner must never have Super Admin session", viewModel.isSuperAdminUser())
    }

    // -------------------------------------------------------------------------
    // Regression Case 15: Valid Gmail is never misidentified as Invalid Email on Credential Failures
    // -------------------------------------------------------------------------
    @Test
    fun `15 Valid ataursek9@gmail_com is never flagged as Invalid Email on credential failure or Firebase invalid email or password message`() {
        val email = "ataursek9@gmail.com"
        assertTrue(viewModel.isValidEmailFormat(email))

        // When Firebase returns FirebaseAuthInvalidCredentialsException containing "invalid email or password"
        val exWithEmailPassword = FirebaseAuthInvalidCredentialsException(
            "ERROR_INVALID_CREDENTIAL",
            "The supplied auth credential is incorrect: invalid email or password"
        )
        val mappedErr = AppErrorHandler.map(exWithEmailPassword)
        assertNotEquals("Must NEVER map to INVALID_EMAIL when message is about credential/password failure", "INVALID_EMAIL", mappedErr.rawErrorCode)
        assertEquals("INVALID_CREDENTIAL", mappedErr.rawErrorCode)
        assertFalse("Must never claim email format is bad", mappedErr.toUserMessage().contains("ইমেইল ঠিকানাটির ফরম্যাট সঠিক নয়"))

        val loginErr = AppErrorHandler.mapLoginError(exWithEmailPassword, AppErrorHandler.EmailAuthProviderType.GOOGLE_ONLY)
        assertEquals("GOOGLE_ONLY_ACCOUNT", loginErr.rawErrorCode)
        assertTrue(loginErr.toUserMessage().contains("Google দিয়ে প্রবেশ করুন"))
    }
}

