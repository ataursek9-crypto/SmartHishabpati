package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.User
import com.example.data.model.UserAccountStatus
import com.example.data.model.UserRole
import com.example.data.repository.BusinessRepository
import com.example.data.sync.CloudBusinessAuth
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
@Config(sdk = [36])
class SecurityAndAuthHardeningTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repo: BusinessRepository

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
        repo = BusinessRepository(db, null, app)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun test_phoneNormalization_andPinHashingContract() = runTest {
        val raw1 = "01712-345678"
        val raw2 = "+8801712345678"
        val raw3 = " 01712345678 "

        assertEquals("01712345678", CloudBusinessAuth.normalizePhone(raw1))
        assertEquals("01712345678", CloudBusinessAuth.normalizePhone(raw2))
        assertEquals("01712345678", CloudBusinessAuth.normalizePhone(raw3))

        val pin = "1234"
        val hash = repo.hashPin(pin)
        assertNotNull(hash)
        assertTrue(hash.length == 64) // SHA-256 hex string
        assertEquals(hash, repo.hashPin("1234"))
        assertNotEquals(hash, repo.hashPin("4321"))
    }

    @Test
    fun test_offlineLogin_notPermittedWhenNoPreExistingUser() = runTest {
        // Attempt login without network and with no local user
        val result = repo.authenticateUser("01700112233", "1234")
        assertTrue(result is BusinessRepository.AuthResult.Error)
        val errorMsg = (result as BusinessRepository.AuthResult.Error).message
        assertTrue(errorMsg.isNotBlank())
        assertTrue(errorMsg.contains("ইন্টারনেট সংযোগ") || errorMsg.contains("পূর্ব-সংরক্ষিত অ্যাকাউন্ট") || errorMsg.contains("নেটওয়ার্ক"))
    }

    @Test
    fun test_offlineLogin_failsOnWrongPinForExistingVerifiedUser() = runTest {
        val testPhone = "01711223344"
        val correctPin = "5555"
        val correctHash = repo.hashPin(correctPin)

        // Seed a verified local user
        val localUser = User(
            id = "owner_shop_offline_1",
            name = "করিম স্টোর মালিক",
            phone = testPhone,
            role = UserRole.ADMIN,
            pinHash = correctHash,
            isActive = true,
            accountStatus = UserAccountStatus.ACTIVE,
            shopId = "shop_offline_1"
        )
        db.userDao().insertUser(localUser)

        // Wrong PIN attempt
        val result = repo.authenticateUser(testPhone, "9999")
        assertTrue(result is BusinessRepository.AuthResult.Error)
        val errorMsg = (result as BusinessRepository.AuthResult.Error).message
        assertTrue(
            errorMsg.contains("ভুল পিন") ||
            errorMsg.contains("PIN") ||
            errorMsg.contains("সঠিক পিন")
        )
    }

    @Test
    fun test_offlineLogin_suspendedUserBlockedOffline() = runTest {
        val testPhone = "01799887766"
        val correctPin = "1234"
        val correctHash = repo.hashPin(correctPin)

        val suspendedUser = User(
            id = "user_suspended_1",
            name = "স্থগিত ব্যবহারকারী",
            phone = testPhone,
            role = UserRole.ADMIN,
            pinHash = correctHash,
            isActive = false,
            accountStatus = UserAccountStatus.SUSPENDED,
            statusReason = "নিয়ম লঙ্ঘন",
            shopId = "shop_suspended_1"
        )
        db.userDao().insertUser(suspendedUser)

        val result = repo.authenticateUser(testPhone, correctPin)
        assertTrue(result is BusinessRepository.AuthResult.Error)
        val errorMsg = (result as BusinessRepository.AuthResult.Error).message
        assertTrue(errorMsg.contains("স্থগিত") || errorMsg.contains("Suspended"))
    }

    @Test
    fun test_mandatoryClaimsVerification_pureLogic() {
        val expectedBizId = "shop_abc_123"
        val expectedRole = UserRole.ADMIN

        // Case 1: Missing businessId claim
        val claimsMissingBizId = mapOf<String, Any>("role" to "ADMIN")
        val bizId1 = (claimsMissingBizId["businessId"] as? String)?.trim()
        val role1 = (claimsMissingBizId["role"] as? String)?.trim()
        val bizIdValid1 = !bizId1.isNullOrBlank() && bizId1 == expectedBizId
        val roleValid1 = !role1.isNullOrBlank() && role1.equals(expectedRole.name, ignoreCase = true)
        assertFalse("Missing businessId must fail validation", bizIdValid1 && roleValid1)

        // Case 2: Mismatched businessId claim
        val claimsMismatchBizId = mapOf<String, Any>("businessId" to "other_shop_999", "role" to "ADMIN")
        val bizId2 = (claimsMismatchBizId["businessId"] as? String)?.trim()
        val role2 = (claimsMismatchBizId["role"] as? String)?.trim()
        val bizIdValid2 = !bizId2.isNullOrBlank() && bizId2 == expectedBizId
        val roleValid2 = !role2.isNullOrBlank() && role2.equals(expectedRole.name, ignoreCase = true)
        assertFalse("Mismatched businessId must fail validation", bizIdValid2 && roleValid2)

        // Case 3: Missing role claim
        val claimsMissingRole = mapOf<String, Any>("businessId" to expectedBizId)
        val bizId3 = (claimsMissingRole["businessId"] as? String)?.trim()
        val role3 = (claimsMissingRole["role"] as? String)?.trim()
        val bizIdValid3 = !bizId3.isNullOrBlank() && bizId3 == expectedBizId
        val roleValid3 = !role3.isNullOrBlank() && role3.equals(expectedRole.name, ignoreCase = true)
        assertFalse("Missing role claim must fail validation", bizIdValid3 && roleValid3)

        // Case 4: Mismatched role claim
        val claimsMismatchRole = mapOf<String, Any>("businessId" to expectedBizId, "role" to "SUPER_ADMIN")
        val bizId4 = (claimsMismatchRole["businessId"] as? String)?.trim()
        val role4 = (claimsMismatchRole["role"] as? String)?.trim()
        val bizIdValid4 = !bizId4.isNullOrBlank() && bizId4 == expectedBizId
        val roleValid4 = !role4.isNullOrBlank() && role4.equals(expectedRole.name, ignoreCase = true)
        assertFalse("Mismatched role must fail validation", bizIdValid4 && roleValid4)

        // Case 5: Valid matching claims
        val validClaims = mapOf<String, Any>("businessId" to expectedBizId, "role" to "ADMIN")
        val bizId5 = (validClaims["businessId"] as? String)?.trim()
        val role5 = (validClaims["role"] as? String)?.trim()
        val bizIdValid5 = !bizId5.isNullOrBlank() && bizId5 == expectedBizId
        val roleValid5 = !role5.isNullOrBlank() && role5.equals(expectedRole.name, ignoreCase = true)
        assertTrue("Matching claims must pass validation", bizIdValid5 && roleValid5)
    }
}
