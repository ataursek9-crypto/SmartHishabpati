package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.data.repository.BusinessRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TestDueCustomerDetectionAndRecycleTest {

    private fun createCleanDb(context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun `verify test and sample seed due detection identifies correct categories and total due`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createCleanDb(context)
        val repo = BusinessRepository(db)

        repo.seedInitialDataIfEmpty(includeSampleDemoData = true)
        val admin = (repo.authenticateUser("01700000000", "1234") as BusinessRepository.AuthResult.Success).user

        // 1. Initial seeded demo customers: c_1 (due 5000) and c_3 (due 8000) = 13,000
        // Let's also add explicit test customers and a second duplicate/sample set so total due reaches exactly 26,000
        val testCust1 = Customer(
            id = "test_cust_101",
            name = "[টেস্ট কাস্টমার] করিম স্টোর",
            phone = "01888111222",
            address = "ধানমন্ডি, ঢাকা",
            totalBilled = 10000.0,
            totalPaid = 2000.0,
            currentDue = 8000.0,
            isTestData = true,
            testTag = "TEST_CUSTOMER"
        )
        val testCust2 = Customer(
            id = "test_cust_102",
            name = "[টেস্ট] ডেমো সুপারশপ",
            phone = "01888111333",
            address = "মিরপুর, ঢাকা",
            totalBilled = 5000.0,
            totalPaid = 0.0,
            currentDue = 5000.0,
            isTestData = true,
            testTag = "MANUAL_TEST"
        )
        // A real customer with legitimate due and opening balance
        val realCust = Customer(
            id = "real_cust_201",
            name = "মেসার্স রফিক অ্যান্ড সন্জ",
            phone = "01755998877",
            address = "কারওয়ান বাজার, ঢাকা",
            totalBilled = 25000.0,
            totalPaid = 21000.0,
            currentDue = 4000.0,
            openingDue = 4000.0,
            isTestData = false,
            testTag = ""
        )

        db.customerDao().insertCustomer(testCust1)
        db.customerDao().insertCustomer(testCust2)
        db.customerDao().insertCustomer(realCust)

        // Calculate total due from all active customers:
        // c_1 (5000) + c_3 (8000) + testCust1 (8000) + testCust2 (5000) = 26,000 (Test/Sample) + realCust (4000) = 30,000 total
        val allActive = db.customerDao().getAllCustomers().first()
        val testAndSampleDues = allActive
            .filter { it.id in listOf("c_1", "c_3", "test_cust_101", "test_cust_102") }
            .sumOf { it.currentDue }
        assertEquals(26000.0, testAndSampleDues, 0.001)

        // 2. Run detection
        val detected = repo.detectTestDueCustomers()
        assertTrue(detected.isNotEmpty())

        val detectedTest1 = detected.firstOrNull { it.customer.id == "test_cust_101" }
        assertNotNull(detectedTest1)
        assertEquals(DueCustomerCategory.CONFIRMED_TEST, detectedTest1!!.category)

        val detectedSeedC1 = detected.firstOrNull { it.customer.id == "c_1" }
        assertNotNull(detectedSeedC1)
        assertEquals(DueCustomerCategory.SAMPLE_SEED_DATA, detectedSeedC1!!.category)

        val detectedSeedC3 = detected.firstOrNull { it.customer.id == "c_3" }
        assertNotNull(detectedSeedC3)
        assertEquals(DueCustomerCategory.SAMPLE_SEED_DATA, detectedSeedC3!!.category)

        val detectedReal = detected.firstOrNull { it.customer.id == "real_cust_201" }
        assertNotNull(detectedReal)
        // Must strictly be classified as NEEDS_MANUAL_REVIEW
        assertEquals(DueCustomerCategory.NEEDS_MANUAL_REVIEW, detectedReal!!.category)
        assertEquals(4000.0, detectedReal.customer.openingDue, 0.001)
    }

    @Test
    fun `verify soft delete moves customer to recycle bin and updates dashboard active total due`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createCleanDb(context)
        val repo = BusinessRepository(db)

        repo.seedInitialDataIfEmpty(includeSampleDemoData = true)
        val admin = (repo.authenticateUser("01700000000", "1234") as BusinessRepository.AuthResult.Success).user

        val initialCusts = db.customerDao().getAllCustomers().first()
        val initialDue = initialCusts.filter { it.currentDue > 0 }.sumOf { it.currentDue }
        assertTrue(initialDue >= 13000.0)

        // Soft delete c_1 (5000.0)
        val c1 = initialCusts.first { it.id == "c_1" }
        val res = repo.softDeleteCustomer(c1, admin, "টেস্ট কাস্টমার অপসারণ")
        assertTrue(res.isSuccess)

        // Verify c_1 is now excluded from active customers
        val activeAfter = db.customerDao().getAllCustomers().first()
        assertNull(activeAfter.firstOrNull { it.id == "c_1" })

        val dueAfter = activeAfter.filter { it.currentDue > 0 }.sumOf { it.currentDue }
        assertEquals(initialDue - 5000.0, dueAfter, 0.001)

        // Verify c_1 exists in active recycle bin
        val rbItems = db.recycleBinDao().getActiveRecycleBinItems().first()
        val rbItem = rbItems.firstOrNull { it.originalId == "c_1" }
        assertNotNull(rbItem)
        assertEquals("customers", rbItem!!.originalTable)
        assertEquals("SAMPLE_SEED", rbItem.testTag)
        assertTrue(rbItem.isTestData)
    }

    @Test
    fun `verify restoring from recycle bin brings back customer balance and dashboard total due`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createCleanDb(context)
        val repo = BusinessRepository(db)

        repo.seedInitialDataIfEmpty(includeSampleDemoData = true)
        val admin = (repo.authenticateUser("01700000000", "1234") as BusinessRepository.AuthResult.Success).user

        val initialCusts = db.customerDao().getAllCustomers().first()
        val c3 = initialCusts.first { it.id == "c_3" } // due 8000.0

        // Soft delete
        val delRes = repo.softDeleteCustomer(c3, admin, "টেস্ট পরিচ্ছন্নকরণ")
        assertTrue(delRes.isSuccess)

        val rbItem = db.recycleBinDao().getActiveRecycleBinItems().first().first { it.originalId == "c_3" }

        // Restore
        val restoreRes = repo.restoreRecycleBinItem(rbItem, admin)
        assertTrue(restoreRes.isSuccess)

        // Verify c_3 is active again with intact balance
        val restoredCust = db.customerDao().getCustomerById("c_3")
        assertNotNull(restoredCust)
        assertFalse(restoredCust!!.isDeleted)
        assertEquals(8000.0, restoredCust.currentDue, 0.001)

        val activeList = db.customerDao().getAllCustomers().first()
        assertNotNull(activeList.firstOrNull { it.id == "c_3" })
    }

    @Test
    fun `verify permanent delete requires correct admin pin and rejects wrong pin`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createCleanDb(context)
        val repo = BusinessRepository(db)

        repo.seedInitialDataIfEmpty(includeSampleDemoData = true)
        val admin = (repo.authenticateUser("01700000000", "1234") as BusinessRepository.AuthResult.Success).user

        val testCust = Customer(
            id = "test_perm_del_1",
            name = "[টেস্ট] স্থায়ী মোছার টেস্ট",
            phone = "01999888777",
            currentDue = 1500.0,
            isTestData = true
        )
        db.customerDao().insertCustomer(testCust)

        // Soft delete first
        repo.softDeleteCustomer(testCust, admin, "টেস্ট কাস্টমার")
        val rbItem = db.recycleBinDao().getActiveRecycleBinItems().first().first { it.originalId == "test_perm_del_1" }

        // 1. Attempt permanent delete with wrong PIN -> Must Fail
        val failRes = repo.permanentlyDeleteRecycleBinItem(rbItem, admin, "9999")
        assertTrue(failRes.isFailure)
        assertTrue(failRes.exceptionOrNull()?.message?.contains("এডমিন পিন সঠিক নয়") == true)

        // Verify customer still exists in DB
        assertNotNull(db.customerDao().getCustomerById("test_perm_del_1"))

        // 2. Attempt permanent delete with correct PIN "1234" -> Must Succeed
        val successRes = repo.permanentlyDeleteRecycleBinItem(rbItem, admin, "1234")
        assertTrue(successRes.isSuccess)

        // Verify customer permanently deleted from DB
        assertNull(db.customerDao().getCustomerById("test_perm_del_1"))
    }

    @Test
    fun `verify batch soft delete removes all 26000 test and sample dues at once`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createCleanDb(context)
        val repo = BusinessRepository(db)

        repo.seedInitialDataIfEmpty(includeSampleDemoData = true)
        val admin = (repo.authenticateUser("01700000000", "1234") as BusinessRepository.AuthResult.Success).user

        // Add 2 test customers so total test due is exactly 26,000 (c_1=5000, c_3=8000, t1=8000, t2=5000)
        val t1 = Customer(id = "t_1", name = "[টেস্ট] এন্টারপ্রাইজ ১", phone = "01700112233", currentDue = 8000.0, isTestData = true)
        val t2 = Customer(id = "t_2", name = "[টেস্ট] এন্টারপ্রাইজ ২", phone = "01700112244", currentDue = 5000.0, isTestData = true)
        val real = Customer(id = "real_1", name = "আসল ব্যবসায়ী রফিক", phone = "01799887766", currentDue = 3500.0, openingDue = 3500.0, isTestData = false)

        db.customerDao().insertCustomer(t1)
        db.customerDao().insertCustomer(t2)
        db.customerDao().insertCustomer(real)

        // Detect
        val detected = repo.detectTestDueCustomers()
        val toRecycle = detected
            .filter { it.category != DueCustomerCategory.NEEDS_MANUAL_REVIEW }
            .map { it.customer }

        val testDueSum = toRecycle.sumOf { it.currentDue }
        assertEquals(26000.0, testDueSum, 0.001)

        // Batch soft delete
        val batchRes = repo.softDeleteMultipleCustomers(toRecycle, admin, "পুরোনো টেস্ট বকেয়া পরিচ্ছন্নকরণ")
        assertTrue(batchRes.isSuccess)
        assertEquals(4, batchRes.getOrNull())

        // Active customers remaining: only real customer!
        val remainingActive = db.customerDao().getAllCustomers().first()
        assertEquals(2, remainingActive.size) // c_2 (due 0) and real_1 (due 3500)
        val remainingDue = remainingActive.filter { it.currentDue > 0 }.sumOf { it.currentDue }
        assertEquals(3500.0, remainingDue, 0.001)

        // Real customer opening due is intact
        val realCustomerInDb = db.customerDao().getCustomerById("real_1")
        assertNotNull(realCustomerInDb)
        assertEquals(3500.0, realCustomerInDb!!.openingDue, 0.001)
        assertEquals(3500.0, realCustomerInDb.currentDue, 0.001)
    }
}
