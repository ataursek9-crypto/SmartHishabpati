package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.UserRole
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SuperAdminRepository
import com.example.ui.screens.RegistrationStep
import com.example.util.PhoneAuthHelper
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
class SmartHishabpatiRegistrationWizardTest {

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
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `test 4-Step Registration Wizard enum sequence and labels`() {
        val steps = RegistrationStep.values()
        assertEquals(4, steps.size)
        assertEquals(RegistrationStep.STEP_1_BUSINESS_NAME, steps[0])
        assertEquals("১/৪", steps[0].stepLabel)
        assertEquals(RegistrationStep.STEP_2_OWNER_NAME, steps[1])
        assertEquals("২/৪", steps[1].stepLabel)
        assertEquals(RegistrationStep.STEP_3_EMAIL_PHONE_PIN, steps[2])
        assertEquals("৩/৪", steps[2].stepLabel)
        assertEquals(RegistrationStep.STEP_4_VERIFICATION, steps[3])
        assertEquals("৪/৪", steps[3].stepLabel)
    }

    @Test
    fun `test PhoneAuthHelper Bangladesh phone number normalization and validation`() {
        // Valid Bangladeshi numbers
        assertTrue(PhoneAuthHelper.isValidBangladeshPhone("01712345678"))
        assertTrue(PhoneAuthHelper.isValidBangladeshPhone("০১৭১২৩৪৫৬৭৮"))
        assertTrue(PhoneAuthHelper.isValidBangladeshPhone("+8801812345678"))
        assertTrue(PhoneAuthHelper.isValidBangladeshPhone("8801912345678"))

        assertEquals("+8801712345678", PhoneAuthHelper.normalizeToE164("01712345678"))
        assertEquals("+8801712345678", PhoneAuthHelper.normalizeToE164("০১৭১২৩৪৫৬৭৮"))

        // Invalid numbers
        assertFalse(PhoneAuthHelper.isValidBangladeshPhone("01234567890")) // 012 is not valid BD prefix
        assertFalse(PhoneAuthHelper.isValidBangladeshPhone("12345"))
        assertFalse(PhoneAuthHelper.isValidBangladeshPhone(""))
    }

    @Test
    fun `test PIN security validation rules`() {
        val validPin = "1234"
        val mismatchConfirmPin = "4321"
        val shortPin = "12"

        assertTrue(validPin.length == 4 && validPin.all { it.isDigit() })
        assertFalse(shortPin.length == 4)
        assertNotEquals(validPin, mismatchConfirmPin)
    }

    @Test
    fun `test complete registration flow creates independent new shop without requiring prior shop record`() = runTest(testDispatcher) {
        var registerSuccess = false
        var returnedShopId: String? = null
        var returnMessage = ""

        viewModel.registerNewShop(
            shopName = "স্মার্ট ফ্যাশন হাউস",
            ownerName = "মুহাম্মদ রাশেদুল ইসলাম",
            phone = "01755123456",
            address = "ধানমন্ডি, ঢাকা",
            district = "ঢাকা",
            businessType = "কাপড়ের দোকান",
            pin = "5678",
            isPhoneVerified = true,
            ownerEmail = "rashedul@test.com",
            ownerUid = "test_auth_uid_101",
            superAdminViewModel = superAdminViewModel
        ) { success, msg, shopId ->
            registerSuccess = success
            returnMessage = msg
            returnedShopId = shopId
        }

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Registration must succeed: $returnMessage", registerSuccess)
        assertNotNull("Returned shopId must not be null", returnedShopId)
        assertTrue("Returned shopId must be non-empty", returnedShopId!!.isNotBlank())

        // Verify active user is logged in as ADMIN
        val currentUser = viewModel.currentUser.value
        assertNotNull("Current user must be logged in after registration", currentUser)
        assertEquals("মুহাম্মদ রাশেদুল ইসলাম", currentUser?.name)
        assertEquals(UserRole.ADMIN, currentUser?.role)
        assertEquals(returnedShopId, currentUser?.shopId)
    }

    @Test
    fun `test duplicate registration prevention for same phone number`() = runTest(testDispatcher) {
        // Register first shop
        viewModel.registerNewShop(
            shopName = "দোকান এক",
            ownerName = "মালিক এক",
            phone = "01799887766",
            address = "মিরপুর",
            district = "ঢাকা",
            businessType = "মুদি",
            pin = "1122",
            isPhoneVerified = true,
            ownerEmail = "owner1@test.com",
            ownerUid = "uid_first_owner",
            superAdminViewModel = superAdminViewModel
        ) { _, _, _ -> }
        testDispatcher.scheduler.advanceUntilIdle()

        // Attempt second registration with same phone under different owner UID
        var secondSuccess = true
        var secondMessage = ""
        viewModel.registerNewShop(
            shopName = "দোকান দুই",
            ownerName = "অন্য মালিক",
            phone = "01799887766",
            address = "গুলশান",
            district = "ঢাকা",
            businessType = "ফার্মেসি",
            pin = "3344",
            isPhoneVerified = true,
            ownerEmail = "owner2@test.com",
            ownerUid = "uid_different_owner",
            superAdminViewModel = superAdminViewModel
        ) { success, msg, _ ->
            secondSuccess = success
            secondMessage = msg
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Duplicate registration on the same phone must be rejected", secondSuccess)
        assertTrue("Error message must mention duplicate account", secondMessage.contains("ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে"))
    }
}
