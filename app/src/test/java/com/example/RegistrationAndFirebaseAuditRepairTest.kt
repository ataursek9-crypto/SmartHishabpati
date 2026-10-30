package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SuperAdminRepository
import com.example.util.PhoneAuthHelper
import com.example.viewmodel.BusinessViewModel
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
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
class RegistrationAndFirebaseAuditRepairTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repo: BusinessRepository
    private lateinit var viewModel: BusinessViewModel
    private lateinit var superAdminRepo: SuperAdminRepository

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

    @Test
    fun testBangladeshPhoneNormalizationAndValidation() {
        // Standard 11 digit mobile number
        val normalized1 = PhoneAuthHelper.normalizeToE164("01712345678")
        assertEquals("+8801712345678", normalized1)
        assertTrue(PhoneAuthHelper.isValidBangladeshPhone(normalized1))

        // Bengali numerals
        val normalizedBengali = PhoneAuthHelper.normalizeToE164("০১৭১২৩৪৫৬৭৮")
        assertEquals("+8801712345678", normalizedBengali)
        assertTrue(PhoneAuthHelper.isValidBangladeshPhone(normalizedBengali))

        // International with country code
        val normalizedWithCode = PhoneAuthHelper.normalizeToE164("+8801812345678")
        assertEquals("+8801812345678", normalizedWithCode)
        assertTrue(PhoneAuthHelper.isValidBangladeshPhone(normalizedWithCode))

        // Invalid numbers
        assertFalse(PhoneAuthHelper.isValidBangladeshPhone("012345"))
        assertFalse(PhoneAuthHelper.isValidBangladeshPhone("01234567890")) // 012 is invalid operator prefix in BD
    }

    @Test
    fun testFirebaseErrorTranslationToBengali() {
        val invalidCodeEx = FirebaseAuthInvalidCredentialsException("INVALID_CODE", "Bad OTP code")
        val translatedInvalid = PhoneAuthHelper.translateFirebaseError(invalidCodeEx)
        assertTrue("Expected Bengali error for invalid code", translatedInvalid.contains("সঠিক নয়"))

        val quotaEx = FirebaseTooManyRequestsException("Quota exceeded")
        val translatedQuota = PhoneAuthHelper.translateFirebaseError(quotaEx)
        assertTrue("Expected Bengali error for quota limit", translatedQuota.contains("এসএমএস কোটা") || translatedQuota.contains("স্থগিত"))
    }

    @Test
    fun testTrialSubscriptionStatusIntegrity() {
        val biz = PlatformBusiness(
            id = "biz_trial_test_101",
            businessName = "টেস্ট শপ ট্রায়াল",
            ownerUid = "firebase_uid_test_1",
            ownerName = "মোঃ টেস্ট মালিক",
            ownerPhone = "01711223344",
            status = BusinessAccountStatus.PENDING,
            subscriptionStatus = SubscriptionStatus.TRIAL,
            planId = "plan_free"
        )
        assertEquals(SubscriptionStatus.TRIAL, biz.subscriptionStatus)
        assertEquals("TRIAL", biz.subscriptionStatus.name)
        assertTrue("TRIAL should be a valid SubscriptionStatus enum value", SubscriptionStatus.values().contains(SubscriptionStatus.TRIAL))
    }

    @Test
    fun testDuplicatePhoneRegistrationPrevention() = runTest(testDispatcher) {
        var result1Success = false
        var result1Msg = ""
        viewModel.registerNewShop(
            shopName = "দোকান এক",
            ownerName = "মালিক এক",
            phone = "01755667788",
            address = "মিরপুর",
            district = "ঢাকা",
            businessType = "মুদি দোকান",
            pin = "1234",
            ownerUid = "uid_owner_1"
        ) { success, msg, _ ->
            result1Success = success
            result1Msg = msg
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("First shop registration should succeed: $result1Msg", result1Success)

        // Attempting to register another shop with identical phone number
        var result2Success = false
        var result2Msg = ""
        viewModel.registerNewShop(
            shopName = "দোকান দুই",
            ownerName = "মালিক দুই",
            phone = "01755667788",
            address = "গুলশান",
            district = "ঢাকা",
            businessType = "ফার্মেসি",
            pin = "5678",
            ownerUid = "uid_owner_2"
        ) { success, msg, _ ->
            result2Success = success
            result2Msg = msg
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("Second shop with duplicate phone must be rejected", result2Success)
        assertTrue("Error must mention duplicate account", result2Msg.contains("ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে"))
    }

    @Test
    fun testDuplicateOwnerRegistrationPrevention() = runTest {
        var result1Success = false
        viewModel.registerNewShop(
            shopName = "প্রাইমারি স্টোর",
            ownerName = "করিম মিয়া",
            phone = "01811223344",
            address = "ধানমন্ডি",
            district = "ঢাকা",
            businessType = "ইলেকট্রনিক্স",
            pin = "4321",
            ownerEmail = "karim.owner@gmail.com",
            ownerUid = "firebase_uid_karim_999"
        ) { success, _, _ ->
            result1Success = success
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("First registration must succeed", result1Success)

        // Second registration attempt using the same owner UID and email
        var result2Success = false
        var result2Msg = ""
        viewModel.registerNewShop(
            shopName = "দ্বিতীয় স্টোর",
            ownerName = "করিম মিয়া",
            phone = "01999887766", // Different phone
            address = "বনানী",
            district = "ঢাকা",
            businessType = "গার্মেন্টস",
            pin = "9876",
            ownerEmail = "karim.owner@gmail.com",
            ownerUid = "firebase_uid_karim_999" // Same authenticated owner UID
        ) { success, msg, _ ->
            result2Success = success
            result2Msg = msg
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("Second shop with same authenticated owner UID must be rejected to prevent duplicate shops", result2Success)
        assertTrue("Error must notify existing registered shop", result2Msg.contains("ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে"))
    }

    @Test
    fun testPinSecurityHashing() = runTest {
        val plainPin = "54321"
        val hash = repo.hashPin(plainPin)

        assertNotEquals("PIN must never be stored as plain text", plainPin, hash)
        assertEquals("SHA-256 hash length must be 64 characters", 64, hash.length)
        // Consistent hashing
        assertEquals(hash, repo.hashPin(plainPin))
        // Different PIN generates different hash
        assertNotEquals(hash, repo.hashPin("12345"))
    }

    @Test
    fun testTwoShopDataIsolation() = runTest {
        // Register Shop 1
        var shop1Id: String? = null
        viewModel.registerNewShop(
            shopName = "শপ এ",
            ownerName = "মালিক এ",
            phone = "01711111111",
            address = "উত্তরা",
            district = "ঢাকা",
            businessType = "মুদি",
            pin = "1111",
            ownerUid = "uid_owner_a"
        ) { success, _, id ->
            if (success) shop1Id = id
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(shop1Id)

        val shop2Id = "biz_shop_b_202"

        // Insert staff for Shop 2
        db.userDao().insertUser(
            User(
                id = "salesman_shop_2",
                name = "সেলসম্যান শপ ২",
                phone = "01822222222",
                role = UserRole.SALESMAN,
                pinHash = repo.hashPin("1234"),
                shopId = shop2Id
            )
        )

        // Verify Shop 1 staff list only contains Shop 1 users
        val shop1Users = db.userDao().getActiveUsersByShopList(shop1Id!!)
        assertEquals(1, shop1Users.size)
        assertEquals("মালিক এ", shop1Users.first().name)

        // Verify Shop 2 staff list only contains Shop 2 users
        val shop2Users = db.userDao().getActiveUsersByShopList(shop2Id)
        assertEquals(1, shop2Users.size)
        assertEquals("সেলসম্যান শপ ২", shop2Users.first().name)

        // Isolation: Shop 1 staff list has no Shop 2 users
        assertFalse(shop1Users.any { it.shopId == shop2Id })
    }

    @Test
    fun testSuperAdminAuthorizationRule() {
        // Authorized Master identities
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ataurgonee4@gmail.com"))
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ataursek9@gmail.com"))
        assertTrue(SuperAdminRepository.isSuperAdminPhone("01700000000"))
        assertTrue(SuperAdminRepository.isSuperAdminPhone("+8801700000000"))

        // BusinessRepository delegation
        assertTrue(repo.isAuthorizedSuperAdminEmail("ataurgonee4@gmail.com"))
        assertTrue(repo.isAuthorizedSuperAdminEmail("ataursek9@gmail.com"))
        assertTrue(repo.isAuthorizedSuperAdminPhone("01700000000"))

        // Explicitly revoked email
        assertFalse(SuperAdminRepository.isSuperAdminEmail("buddavaibinodon@gmail.com"))
        assertTrue(SuperAdminRepository.isRevokedSuperAdminEmail("buddavaibinodon@gmail.com"))
        assertFalse(repo.isAuthorizedSuperAdminEmail("buddavaibinodon@gmail.com"))
        assertTrue(repo.isRevokedSuperAdminEmail("buddavaibinodon@gmail.com"))

        // Unauthorized emails
        assertFalse(SuperAdminRepository.isSuperAdminEmail("hacker@example.com"))
        assertFalse(SuperAdminRepository.isSuperAdminEmail("random.shop@gmail.com"))
        assertFalse(SuperAdminRepository.isSuperAdminEmail(null))
        assertFalse(SuperAdminRepository.isSuperAdminEmail(""))
    }

    @Test
    fun testPhoneAuthDisabledProviderAndSmsRegionErrorTranslation() {
        val rawFirebaseError = "This operation is not allowed. This may be because the given sign-in provider is disabled for this Firebase project. Enable it in the Firebase console, under the sign-in method tab of the Auth section. [SMS unable to be sent until this region enabled by the app developer.]"
        val exception = Exception(rawFirebaseError)

        // 1. Detection helper
        assertTrue(PhoneAuthHelper.isProviderOrSmsRegionDisabledMessage(rawFirebaseError))
        assertTrue(PhoneAuthHelper.isProviderOrSmsRegionDisabled(exception))

        // 2. Clear Bengali translation with actionable steps
        val translated = PhoneAuthHelper.translateFirebaseError(exception)
        assertTrue(translated.contains("Phone প্রোভাইডার বন্ধ"))
        assertTrue(translated.contains("SMS Region Policy"))
        assertTrue(translated.contains("Google অ্যাকাউন্ট"))
        assertTrue(translated.contains("+880"))
    }
}
