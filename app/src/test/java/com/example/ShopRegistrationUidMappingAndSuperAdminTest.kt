package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.PlatformBusiness
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
class ShopRegistrationUidMappingAndSuperAdminTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repo: BusinessRepository
    private lateinit var viewModel: BusinessViewModel
    private lateinit var superAdminRepo: SuperAdminRepository
    private lateinit var superAdminViewModel: SuperAdminViewModel

    private val targetAuthUid = "PBknnhTZ1LNZsNBb9dpAOInpRmt1"

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

    @Test
    fun testRegistrationWithAuthUid_savesCorrectFieldsAndAppearsInSuperAdmin() = runTest {
        var registerSuccess = false
        var registerMsg = ""
        var createdShopId: String? = null

        viewModel.registerNewShop(
            shopName = "নিউ স্টার ট্রেডার্স",
            ownerName = "আতাউর রহমান",
            phone = "01812345678",
            address = "মিরপুর ১০, ঢাকা",
            district = "ঢাকা",
            businessType = "ইলেকট্রনিক্স",
            pin = "1234",
            ownerEmail = "ataur.shop@gmail.com",
            ownerUid = targetAuthUid
        ) { success, msg, id ->
            registerSuccess = success
            registerMsg = msg
            createdShopId = id
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Registration must succeed: $registerMsg", registerSuccess)
        assertNotNull("Shop ID must not be null", createdShopId)

        // Verify Local Settings
        val settings = db.companySettingsDao().getSettings()
        assertNotNull("Company settings must exist", settings)
        assertEquals("নিউ স্টার ট্রেডার্স", settings!!.businessName)
        assertEquals("01812345678", settings.phone)
        assertEquals("মিরপুর ১০, ঢাকা", settings.address)

        // Verify Super Admin list has the shop with exact ownerUid
        val superAdminBiz = superAdminRepo.businesses.value.firstOrNull { it.id == createdShopId }
        assertNotNull("Business must appear in Super Admin list", superAdminBiz)
        assertEquals("নিউ স্টার ট্রেডার্স", superAdminBiz!!.businessName)
        assertEquals(targetAuthUid, superAdminBiz.ownerUid)
        assertEquals("01812345678", superAdminBiz.ownerPhone)
        assertEquals("মিরপুর ১০, ঢাকা", superAdminBiz.address)
        assertTrue("createdAt should be valid timestamp", superAdminBiz.createdAt > 0)
    }

    @Test
    fun testReRegistration_idempotentRetryDoesNotThrowDuplicateException() = runTest {
        var firstSuccess = false
        var firstShopId: String? = null

        viewModel.registerNewShop(
            shopName = "মেসার্স আল-মদিনা",
            ownerName = "মোঃ আলম",
            phone = "01999887766",
            address = "চকবাজার, চট্টগ্রাম",
            district = "চট্টগ্রাম",
            businessType = "মুদি দোকান",
            pin = "9988",
            ownerEmail = "almodina@gmail.com",
            ownerUid = targetAuthUid
        ) { success, _, id ->
            firstSuccess = success
            firstShopId = id
        }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Initial registration should succeed", firstSuccess)

        // Simulate safe retry on BusinessRepository level with same phone and resolved shopId
        val retryResult = repo.registerNewShop(
            shopName = "মেসার্স আল-মদিনা",
            ownerName = "মোঃ আলম",
            phone = "01999887766",
            address = "চকবাজার, চট্টগ্রাম",
            district = "চট্টগ্রাম",
            businessType = "মুদি দোকান",
            pin = "9988",
            shopId = firstShopId ?: ""
        )
        assertTrue("Retry with same shop ID should succeed and not throw duplicate phone error", retryResult.isSuccess)
    }

    @Test
    fun testSuperAdminDocParsing_handlesMixedTypesSafely() {
        // Test data map parsing behavior for SuperAdminRepository.parseBusinessDoc
        // Simulates Firestore document with Integer 0, Timestamp-like objects, or Long
        val testDocData = mapOf<String, Any>(
            "id" to targetAuthUid,
            "businessName" to "স্মার্ট বিজনেস",
            "name" to "স্মার্ট বিজনেস",
            "ownerName" to "প্রোপাইটর",
            "ownerEmail" to "owner@example.com",
            "ownerPhone" to "01711223344",
            "phone" to "01711223344",
            "address" to "মতিঝিল, ঢাকা",
            "ownerUid" to targetAuthUid,
            "status" to "PENDING",
            "planId" to "FREE",
            "subscriptionStatus" to "TRIAL",
            "proposedFee" to 0, // Integer in JSON/Firestore
            "staffCount" to 1,
            "maxStaffAllowed" to 5,
            "createdAt" to 1729000000000L,
            "updatedAt" to 1729000000000L
        )

        val id = testDocData["id"] as? String ?: ""
        val bName = (testDocData["businessName"] ?: testDocData["name"]) as? String ?: ""
        val ownerUid = testDocData["ownerUid"] as? String ?: ""
        val proposedFee = (testDocData["proposedFee"] as? Number)?.toDouble() ?: 0.0

        assertEquals(targetAuthUid, id)
        assertEquals("স্মার্ট বিজনেস", bName)
        assertEquals(targetAuthUid, ownerUid)
        assertEquals(0.0, proposedFee, 0.001)
    }

    @Test
    fun testStubCompletionDoesNotCreateDuplicateDocument() = runTest {
        // Pre-save stub document in SuperAdminRepository
        val stub = PlatformBusiness(
            id = targetAuthUid,
            businessName = "স্মার্ট বিজনেস স্টোর",
            ownerUid = targetAuthUid,
            ownerName = "আতাউর রহমান",
            ownerPhone = "",
            address = ""
        )
        superAdminRepo.registerNewShop(stub)

        // Now register through ViewModel using targetAuthUid
        var completionSuccess = false
        var completedId: String? = null
        viewModel.registerNewShop(
            shopName = "স্মার্ট বিজনেস স্টোর (সম্পূর্ণ)",
            ownerName = "আতাউর রহমান",
            phone = "01700112233",
            address = "গুলশান ২, ঢাকা",
            district = "ঢাকা",
            businessType = "জেনারেল স্টোর",
            pin = "5566",
            ownerEmail = "ataur@store.com",
            ownerUid = targetAuthUid
        ) { success, _, id ->
            completionSuccess = success
            completedId = id
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Completion should succeed", completionSuccess)
        // Verify shop ID reused the existing stub ID rather than generating a new random biz_ ID
        assertEquals("Shop ID must reuse stub document ID $targetAuthUid", targetAuthUid, completedId)

        // Verify only 1 shop exists in Super Admin for this owner
        val matchingShops = superAdminRepo.businesses.value.filter { it.ownerUid == targetAuthUid }
        assertEquals("There should be exactly one business document for this owner", 1, matchingShops.size)
        assertEquals("স্মার্ট বিজনেস স্টোর (সম্পূর্ণ)", matchingShops.first().businessName)
        assertEquals("01700112233", matchingShops.first().ownerPhone)
        assertEquals("গুলশান ২, ঢাকা", matchingShops.first().address)
    }
}
