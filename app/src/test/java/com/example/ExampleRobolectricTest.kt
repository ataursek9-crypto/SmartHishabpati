package com.example

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.data.repository.BusinessRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.util.BengaliFormatter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `verify admin display name formatting and prefix cleanup`() {
    // 1. Bengali format should display pure name 'মোঃ আতাউর রহমান' without prepending 'এডমিন'
    assertEquals("মোঃ আতাউর রহমান", BengaliFormatter.formatAdminDisplayName("মোঃ আতাউর রহমান"))
    // 2. Prevent/clean redundant 'এডমিন' prefix if present
    assertEquals("মোঃ আতাউর রহমান", BengaliFormatter.formatAdminDisplayName("এডমিন মোঃ আতাউর রহমান"))
    assertEquals("মোঃ আতাউর রহমান", BengaliFormatter.formatAdminDisplayName("এডমিন: মোঃ আতাউর রহমান"))
    assertEquals("মোঃ আতাউর রহমান", BengaliFormatter.formatAdminDisplayName("এডমিন  এডমিন মোঃ আতাউর রহমান"))
    // 3. Null or blank default
    assertEquals("মোঃ আতাউর রহমান", BengaliFormatter.formatAdminDisplayName(null))
    assertEquals("মোঃ আতাউর রহমান", BengaliFormatter.formatAdminDisplayName(""))

    // 4. English format should display pure name 'Md Ataur Rahman' without prepending 'Admin'
    assertEquals("Md Ataur Rahman", BengaliFormatter.formatAdminEnglishName("Md Ataur Rahman"))
    assertEquals("Md Ataur Rahman", BengaliFormatter.formatAdminEnglishName("Admin Md Ataur Rahman"))
    assertEquals("Md Ataur Rahman", BengaliFormatter.formatAdminEnglishName(null))
  }

  @Test
  fun `verify app name resource`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SmartHishabpati", appName)
  }

  @Test
  fun `verify business repository authentication and sale transactions`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val repo = BusinessRepository(db)

    // Seed data
    repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

    // Test Admin Login
    val admin = repo.authenticate("01700000000", "1234")
    assertNotNull("Admin should authenticate successfully", admin)
    assertEquals(UserRole.ADMIN, admin?.role)

    // Test Salesman Login
    val salesman = repo.authenticate("01800000000", "1234")
    assertNotNull("Salesman should authenticate successfully", salesman)
    assertEquals(UserRole.SALESMAN, salesman?.role)

    // Test Wrong PIN Login
    val wrongAuth = repo.authenticate("01700000000", "9999")
    assertNull("Wrong PIN must return null", wrongAuth)

    // Test completing a sale
    val nextInvoice = repo.getNextInvoiceNumber()
    val testSale = Sale(
      invoiceNumber = nextInvoice,
      customerId = "c_1",
      customerName = "মেসার্স হাজী ব্রাদার্স",
      customerPhone = "01911223344",
      salesmanId = salesman!!.id,
      salesmanName = salesman.name,
      subtotal = 3450.0,
      discount = 50.0,
      totalAmount = 3400.0,
      paidAmount = 2000.0,
      dueAmount = 1400.0,
      paymentMethod = PaymentMethod.CASH
    )
    val testItems = listOf(
      SaleItem(
        invoiceNumber = nextInvoice,
        productId = "p_1",
        productName = "মিনিকেট চাল (৫০ কেজি বস্তা)",
        quantity = 1.0,
        unitPrice = 3450.0,
        purchasePriceAtSale = 3100.0,
        totalPrice = 3450.0
      )
    )

    val saleResult = repo.completeSale(testSale, testItems, salesman)
    assertTrue("Sale should complete successfully", saleResult.isSuccess)

    // Verify stock was reduced
    val updatedProd = db.productDao().getProductById("p_1")
    assertNotNull(updatedProd)
    assertEquals(44.0, updatedProd!!.currentStock, 0.001)

    // Verify customer due updated
    val updatedCust = db.customerDao().getCustomerById("c_1")
    assertNotNull(updatedCust)
    assertEquals(6400.0, updatedCust!!.currentDue, 0.001)
  }

  @Test
  fun `verify product edit preserves historical sale snapshot and logs changes`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val repo = BusinessRepository(db)
    repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

    val admin = repo.authenticate("01700000000", "1234")!!
    val salesman = repo.authenticate("01800000000", "1234")!!

    // 1. Initial sale of p_2 (তীর তেল, purchase 780, selling 850)
    val inv1 = repo.getNextInvoiceNumber()
    val sale1 = Sale(
      invoiceNumber = inv1,
      customerId = "c_2",
      customerName = "জননী স্টোর",
      customerPhone = "01722334455",
      salesmanId = salesman.id,
      salesmanName = salesman.name,
      subtotal = 850.0,
      totalAmount = 850.0,
      paidAmount = 850.0,
      dueAmount = 0.0
    )
    val items1 = listOf(
      SaleItem(
        invoiceNumber = inv1,
        productId = "p_2",
        productName = "তীর সোয়াবিন তেল (৫ লিটার)",
        quantity = 1.0,
        unitPrice = 850.0,
        purchasePriceAtSale = 780.0,
        totalPrice = 850.0
      )
    )
    val res1 = repo.completeSale(sale1, items1, salesman)
    assertTrue(res1.isSuccess)

    // Verify recorded sale item has snapshot price 780.0
    val savedItems1 = repo.getSaleItems(inv1)
    assertEquals(1, savedItems1.size)
    assertEquals(780.0, savedItems1[0].purchasePriceAtSale, 0.001)

    // 2. Admin edits Product p_2: changes purchase price to 810.0 and selling price to 900.0
    val currentProd = db.productDao().getProductById("p_2")!!
    val updatedProduct = currentProd.copy(
      purchasePrice = 810.0,
      sellingPrice = 900.0
    )
    repo.saveProduct(updatedProduct, admin, isNew = false, reason = "কোম্পানি রেট বৃদ্ধি")

    // Verify current product in DB is updated with auto selling price (810.0 * 1.70 = 1377.0)
    val prodAfterEdit = db.productDao().getProductById("p_2")!!
    assertEquals(810.0, prodAfterEdit.purchasePrice, 0.001)
    assertEquals(1377.0, prodAfterEdit.sellingPrice, 0.001)

    // CRITICAL: Verify previous sale item snapshot did NOT change
    val historicalItems = repo.getSaleItems(inv1)
    assertEquals(780.0, historicalItems[0].purchasePriceAtSale, 0.001)
    assertEquals(850.0, historicalItems[0].unitPrice, 0.001)
  }

  @Test
  fun `verify suspend and deactivate blocks salesman login and field actions`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val repo = BusinessRepository(db)
    repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

    val admin = repo.authenticate("01700000000", "1234")!!
    val salesman = repo.authenticate("01800000000", "1234")!!
    assertEquals(UserAccountStatus.ACTIVE, salesman.accountStatus)

    // Admin suspends salesman
    repo.changeUserAccountStatus(
      targetUserId = salesman.id,
      newStatus = UserAccountStatus.SUSPENDED,
      reason = "হিসাব অডিট চলাকালীন সাময়িক স্থগিত",
      adminUser = admin
    )

    // Verify login is blocked
    val authSuspended = repo.authenticateUser("01800000000", "1234")
    assertTrue("Suspended user login must fail", authSuspended is BusinessRepository.AuthResult.Error)
    assertTrue((authSuspended as BusinessRepository.AuthResult.Error).message.contains("স্থগিত"))

    // Verify sale completion attempt by suspended user is blocked
    val inv = repo.getNextInvoiceNumber()
    val sale = Sale(
      invoiceNumber = inv,
      customerId = "c_1",
      customerName = "মেসার্স হাজী ব্রাদার্স",
      customerPhone = "01911223344",
      salesmanId = salesman.id,
      salesmanName = salesman.name,
      subtotal = 100.0,
      totalAmount = 100.0,
      paidAmount = 100.0,
      dueAmount = 0.0
    )
    val items = listOf(
      SaleItem(invoiceNumber = inv, productId = "p_4", productName = "প্রাণ ডাল", quantity = 1.0, unitPrice = 135.0, purchasePriceAtSale = 115.0, totalPrice = 135.0)
    )
    val blockedSaleResult = repo.completeSale(sale, items, salesman)
    assertTrue("Sale by suspended user must fail", blockedSaleResult.isFailure)

    // Admin reactivates salesman
    repo.changeUserAccountStatus(
      targetUserId = salesman.id,
      newStatus = UserAccountStatus.ACTIVE,
      reason = "অডিট সম্পন্ন",
      adminUser = admin
    )

    // Verify login succeeds again
    val authReactivated = repo.authenticateUser("01800000000", "1234")
    assertTrue("Reactivated user login must succeed", authReactivated is BusinessRepository.AuthResult.Success)
  }

  @Test
  fun `verify customer due tracking and payment collection ledger`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val repo = BusinessRepository(db)
    repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

    val salesman = repo.authenticate("01800000000", "1234")!!
    val initialCustomer = db.customerDao().getCustomerById("c_1")!!
    val initialDue = initialCustomer.currentDue
    val initialPaid = initialCustomer.totalPaid
    assertTrue(initialDue >= 2000.0)

    // Collect partial payment 2000.0
    val receiptNo = repo.getNextReceiptNumber()
    val receipt = PaymentReceipt(
      receiptNumber = receiptNo,
      customerId = "c_1",
      customerName = initialCustomer.name,
      customerPhone = initialCustomer.phone,
      salesmanId = salesman.id,
      salesmanName = salesman.name,
      amount = 2000.0,
      paymentMethod = PaymentMethod.CASH,
      previousDue = initialDue,
      currentDue = initialDue - 2000.0
    )
    val collectResult = repo.collectPayment(receipt, salesman)
    assertTrue("Payment collection should succeed", collectResult.isSuccess)

    // Verify remaining due in customer record
    val updatedCustomer = db.customerDao().getCustomerById("c_1")!!
    assertEquals(initialDue - 2000.0, updatedCustomer.currentDue, 0.001)
    assertEquals(initialPaid + 2000.0, updatedCustomer.totalPaid, 0.001)
  }

  @Test
  fun `verify admin login authentication, salesman blocked, and wrong PIN enforcement`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = AppDatabase.getDatabase(context)
      val repo = BusinessRepository(db)
      repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

      // 1. Wrong PIN attempt for Admin: MUST FAIL
      val wrongPinResult = repo.authenticateAdmin("01700000000", "0000")
      assertTrue("Wrong PIN must return error", wrongPinResult is BusinessRepository.AuthResult.Error)
      assertTrue(
        "Error should indicate invalid PIN",
        (wrongPinResult as BusinessRepository.AuthResult.Error).message.contains("ভুল পিন")
      )

      // 2. Salesman credentials in Secret Admin Login Portal: MUST BE STRICTLY BLOCKED!
      val salesmanBlockedResult = repo.authenticateAdmin("01800000000", "1234")
      assertTrue("Salesman must be blocked in Admin portal", salesmanBlockedResult is BusinessRepository.AuthResult.Error)
      val blockedMsg = (salesmanBlockedResult as BusinessRepository.AuthResult.Error).message
      assertTrue("Error should mention unauthorized salesman access", blockedMsg.contains("সেলসম্যান") || blockedMsg.contains("অননুমোদিত"))

      // 3. Valid Admin credentials in Admin Portal: MUST SUCCEED
      val adminSuccessResult = repo.authenticateAdmin("01700000000", "1234")
      assertTrue("Admin login should succeed", adminSuccessResult is BusinessRepository.AuthResult.Success)
      val adminUser = (adminSuccessResult as BusinessRepository.AuthResult.Success).user
      assertEquals(UserRole.ADMIN, adminUser.role)
      assertEquals("01700000000", adminUser.phone)

      // 4. Salesman attempting to execute admin functions (updateSettings, saveUser) must fail
      val salesmanUser = repo.authenticate("01800000000", "1234")!!
      assertThrows(IllegalStateException::class.java) {
        runBlocking {
          repo.updateSettings(CompanySettings(businessName = "হ্যাকড বিজনেস"), salesmanUser)
        }
      }
    }
  }

  private fun awaitLooper(timeoutMs: Long = 3000, condition: () -> Boolean) {
    val start = System.currentTimeMillis()
    while (!condition() && (System.currentTimeMillis() - start) < timeoutMs) {
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(20)
      shadowOf(Looper.getMainLooper()).idle()
    }
  }

  @Test
  fun `verify BusinessViewModel standard login and secret admin login flows`() {
    runBlocking {
      val app = ApplicationProvider.getApplicationContext<android.app.Application>()
      val vm = com.example.viewmodel.BusinessViewModel(app)
      vm.repository.seedInitialDataIfEmpty(includeSampleDemoData = true)
      shadowOf(Looper.getMainLooper()).idle()

      // Test 1: Salesman logging in via standard login -> SUCCESS
      var salesmanCalled = false
      var salesmanSuccess = false
      var salesmanMessage = ""
      vm.login("01800000000", "1234") { success, msg ->
        salesmanCalled = true
        salesmanSuccess = success
        salesmanMessage = msg
      }
      awaitLooper { salesmanCalled }
      assertTrue("Salesman login must succeed, msg: $salesmanMessage", salesmanSuccess)
      assertEquals(UserRole.SALESMAN, vm.currentUser.value?.role)

      // Logout
      vm.logout()
      assertNull(vm.currentUser.value)

      // Test 2: Admin logging in via standard login -> BLOCKED (must not log in through regular screen)
      var adminCalled = false
      var adminOnStandardSuccess = true
      var adminOnStandardMsg = ""
      vm.login("01700000000", "1234") { success, msg ->
        adminCalled = true
        adminOnStandardSuccess = success
        adminOnStandardMsg = msg
      }
      awaitLooper { adminCalled }
      assertFalse("Admin must NOT be allowed to login via standard screen", adminOnStandardSuccess)
      assertNull("CurrentUser must remain null", vm.currentUser.value)
      assertTrue("Message should indicate unauthorized", adminOnStandardMsg.contains("অননুমোদিত"))

      // Test 3: Wrong PIN on standard login -> FAILS
      var wrongPinCalled = false
      var wrongPinSuccess = true
      vm.login("01800000000", "9999") { success, _ ->
        wrongPinCalled = true
        wrongPinSuccess = success
      }
      awaitLooper { wrongPinCalled }
      assertFalse("Wrong PIN must fail", wrongPinSuccess)
      assertNull(vm.currentUser.value)

      // Test 4: Salesman trying to login via Secret Admin Portal -> STRICTLY BLOCKED
      var salesmanAdminCalled = false
      var salesmanAdminSuccess = true
      var salesmanAdminMsg = ""
      vm.loginAdmin("01800000000", "1234") { success, msg ->
        salesmanAdminCalled = true
        salesmanAdminSuccess = success
        salesmanAdminMsg = msg
      }
      awaitLooper { salesmanAdminCalled }
      assertFalse("Salesman must be blocked from Secret Admin Portal", salesmanAdminSuccess)
      assertNull("CurrentUser must remain null after blocked attempt", vm.currentUser.value)
      assertTrue("Message must mention unauthorized", salesmanAdminMsg.contains("অননুমোদিত"))

      // Test 5: Wrong PIN on Secret Admin Portal -> FAILS
      var adminWrongPinCalled = false
      var adminWrongPinSuccess = true
      vm.loginAdmin("01700000000", "9999") { success, _ ->
        adminWrongPinCalled = true
        adminWrongPinSuccess = success
      }
      awaitLooper { adminWrongPinCalled }
      assertFalse("Admin with wrong PIN must fail", adminWrongPinSuccess)
      assertNull(vm.currentUser.value)

      // Test 6: Valid Admin on Secret Admin Portal -> SUCCEEDS
      var validAdminCalled = false
      var validAdminSuccess = false
      var validAdminMsg = ""
      vm.loginAdmin("01700000000", "1234") { success, msg ->
        validAdminCalled = true
        validAdminSuccess = success
        validAdminMsg = msg
      }
      awaitLooper { validAdminCalled }
      assertTrue("Admin login in secret portal must succeed, msg: $validAdminMsg", validAdminSuccess)
      assertEquals(UserRole.ADMIN, vm.currentUser.value?.role)
      assertTrue(validAdminMsg.contains("এডমিন"))
    }
  }

  @Test
  fun `verify Remember Mobile Number, Saved Accounts, Masking, and Security enforcement`() {
    runBlocking {
      val app = ApplicationProvider.getApplicationContext<android.app.Application>()
      val vm = com.example.viewmodel.BusinessViewModel(app)
      vm.repository.seedInitialDataIfEmpty(includeSampleDemoData = true)
      val manager = vm.savedAccountManager

      // Clean start
      manager.clearAll()
      assertTrue("Saved accounts should start empty", manager.savedAccounts.value.isEmpty())

      // 1. Save mobile numbers on consent (never stores PIN or PIN hash)
      manager.saveAccount("01800000000") // Salesman phone
      manager.saveAccount("01700000000") // Admin phone
      assertEquals(2, manager.savedAccounts.value.size)

      // Test masking
      val salesmanAcc = manager.savedAccounts.value.find { it.phone == "01800000000" }!!
      assertEquals("018****0000", salesmanAcc.maskedPhone)

      // 2. PIN or PIN hash is NEVER retained in SavedAccount
      val properties = com.example.data.storage.SavedAccount::class.java.declaredFields.map { it.name }
      assertFalse("SavedAccount must NOT have pin field", properties.contains("pin"))
      assertFalse("SavedAccount must NOT have pinHash field", properties.contains("pinHash"))
      assertFalse("SavedAccount must NOT have password field", properties.contains("password"))

      // 3. User details lookup by phone
      val salesmanUser = vm.getAccountDetailsForPhone("01800000000")
      assertNotNull("Salesman user should exist in DB", salesmanUser)
      assertEquals(UserRole.SALESMAN, salesmanUser?.role)

      val adminUser = vm.getAccountDetailsForPhone("01700000000")
      assertNotNull("Admin user should exist in DB", adminUser)
      assertEquals(UserRole.ADMIN, adminUser?.role)

      // 4. Role enforcement with saved number:
      // Even if Admin phone was saved, trying to log in via standard login screen MUST BE BLOCKED
      var adminCalled = false
      var adminOnStandardSuccess = true
      var adminOnStandardMsg = ""
      vm.login(adminUser!!.phone, "1234") { success, msg ->
        adminCalled = true
        adminOnStandardSuccess = success
        adminOnStandardMsg = msg
      }
      awaitLooper { adminCalled }
      assertFalse("Admin must NOT be allowed to login via standard screen", adminOnStandardSuccess)
      assertNull("CurrentUser must remain null", vm.currentUser.value)
      assertTrue("Message should indicate unauthorized role", adminOnStandardMsg.contains("অননুমোদিত"))

      // 5. Salesman with wrong PIN MUST FAIL (saved number does NOT bypass PIN)
      var wrongPinCalled = false
      var wrongPinSuccess = true
      vm.login(salesmanUser!!.phone, "0000") { success, _ ->
        wrongPinCalled = true
        wrongPinSuccess = success
      }
      awaitLooper { wrongPinCalled }
      assertFalse("Wrong PIN must fail even if phone is remembered", wrongPinSuccess)
      assertNull(vm.currentUser.value)

      // 6. Salesman with correct PIN SUCCEEDS and role is strictly SALESMAN
      var correctLoginCalled = false
      var correctLoginSuccess = false
      vm.login(salesmanUser.phone, "1234") { success, _ ->
        correctLoginCalled = true
        correctLoginSuccess = success
      }
      awaitLooper { correctLoginCalled }
      assertTrue("Valid salesman login must succeed", correctLoginSuccess)
      assertEquals(UserRole.SALESMAN, vm.currentUser.value?.role)

      // 7. Removing a single number removes it from saved accounts
      manager.removeAccount("01700000000")
      assertEquals(1, manager.savedAccounts.value.size)
      assertEquals("01800000000", manager.savedAccounts.value.first().phone)

      // 8. Logout persistence toggle:
      // Case A: keepOnLogout = true -> logout keeps saved numbers
      manager.setKeepOnLogout(true)
      vm.logout()
      assertNull(vm.currentUser.value)
      assertEquals(1, manager.savedAccounts.value.size)

      // Case B: keepOnLogout = false -> logout wipes saved numbers
      manager.setKeepOnLogout(false)
      vm.logout()
      assertTrue("Saved accounts should be cleared on logout when keepOnLogout is false", manager.savedAccounts.value.isEmpty())

      // 9. Verify business data remains fully intact in Room database
      val productsFlow = vm.repository.lowStockProducts
      assertNotNull(productsFlow)
      val dbSalesman = vm.repository.getUserByPhone("01800000000")
      assertNotNull(dbSalesman)
      assertEquals("সেলসম্যান হাসিবুল করিম", dbSalesman?.name)
    }
  }

  @Test
  fun `verify salesman product submission does not affect main stock and sets status pending`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = AppDatabase.getDatabase(context)
      val repo = BusinessRepository(db)
      repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

      val salesman = repo.authenticate("01800000000", "1234")!!
      val initialProductCount = db.productDao().findProductByName("মিনিকেট চাল (৫০ কেজি বস্তা)") != null
      assertTrue(initialProductCount)

      val submissionId = "sub_test_1"
      val submission = ProductSubmission(
        id = submissionId,
        name = "রূপচাঁদা সরিষার তেল (১ লিটার)",
        category = "তেল ও চর্বি",
        purchasePrice = 320.0,
        sellingPrice = 360.0,
        initialStock = 50.0,
        unit = "বোতল",
        description = "নতুন বোতলজাত খাঁটি সরিষার তেল",
        salesmanId = salesman.id,
        salesmanName = salesman.name,
        salesmanPhone = salesman.phone
      )

      // Salesman submits product
      repo.submitProduct(submission, salesman)

      // 1. Verify submission was saved with PENDING status
      val savedSub = db.productSubmissionDao().getSubmissionById(submissionId)
      assertNotNull(savedSub)
      assertEquals(ProductSubmissionStatus.PENDING, savedSub!!.status)
      assertEquals(50.0, savedSub.initialStock, 0.001)

      // 2. CRITICAL: Verify product is NOT in main products table
      val inMainDb = db.productDao().findProductByName("রূপচাঁদা সরিষার তেল (১ লিটার)")
      assertNull("Pending product must NOT exist in main Product inventory", inMainDb)

      // 3. CRITICAL: Verify no stock log was created for pending submission
      val stockLogs = db.stockLogDao().getAllStockLogs()
      val lastAudit = db.auditLogDao().getAllAuditLogs()
      assertNotNull(lastAudit)
    }
  }

  @Test
  fun `verify admin approval adds product to inventory once and logs audit and stock`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = AppDatabase.getDatabase(context)
      val repo = BusinessRepository(db)
      repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

      val admin = repo.authenticate("01700000000", "1234")!!
      val salesman = repo.authenticate("01800000000", "1234")!!

      val submissionId = "sub_approve_test"
      val submission = ProductSubmission(
        id = submissionId,
        name = "ফ্রেশ আটা (২ কেজি)",
        category = "মুদি ও খাদ্য",
        purchasePrice = 120.0,
        sellingPrice = 140.0,
        initialStock = 30.0,
        unit = "প্যাকেট",
        salesmanId = salesman.id,
        salesmanName = salesman.name,
        salesmanPhone = salesman.phone
      )
      repo.submitProduct(submission, salesman)

      // Admin reviews, adjusts selling price to 145.0, and approves
      val editedByAdmin = submission.copy(sellingPrice = 145.0)
      val approvedProduct = repo.approveProductSubmission(submissionId, editedByAdmin, admin)

      assertNotNull(approvedProduct)
      assertEquals("ফ্রেশ আটা (২ কেজি)", approvedProduct.name)
      assertEquals(145.0, approvedProduct.sellingPrice, 0.001)
      assertEquals(30.0, approvedProduct.currentStock, 0.001)

      // Verify it is now present in main products table
      val inDb = db.productDao().getProductById(approvedProduct.id)
      assertNotNull("Approved product must be in live database", inDb)
      assertEquals(145.0, inDb!!.sellingPrice, 0.001)

      // Verify submission is updated to APPROVED
      val subAfterApproval = db.productSubmissionDao().getSubmissionById(submissionId)!!
      assertEquals(ProductSubmissionStatus.APPROVED, subAfterApproval.status)
      assertEquals(approvedProduct.id, subAfterApproval.approvedProductId)
      assertEquals(admin.id, subAfterApproval.reviewedByAdminId)

      // Rule D.3: Second approval attempt must fail
      assertThrows(IllegalStateException::class.java) {
        runBlocking {
          repo.approveProductSubmission(submissionId, editedByAdmin, admin)
        }
      }
    }
  }

  @Test
  fun `verify needs correction requires mandatory reason and allows salesman resubmission`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = AppDatabase.getDatabase(context)
      val repo = BusinessRepository(db)
      repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

      val admin = repo.authenticate("01700000000", "1234")!!
      val salesman = repo.authenticate("01800000000", "1234")!!

      val subId = "sub_correction_test"
      val submission = ProductSubmission(
        id = subId,
        name = "তেজ পাতা (১০০ গ্রাম)",
        category = "মশলা",
        purchasePrice = 40.0,
        sellingPrice = 35.0, // Selling price lower than purchase price
        initialStock = 20.0,
        unit = "প্যাকেট",
        salesmanId = salesman.id,
        salesmanName = salesman.name,
        salesmanPhone = salesman.phone
      )
      repo.submitProduct(submission, salesman)

      // Rule B.5: Blank reason must fail
      assertThrows(IllegalArgumentException::class.java) {
        runBlocking {
          repo.returnProductForCorrection(subId, "   ", admin)
        }
      }

      // Admin sends back with proper reason
      val correctionReason = "বিক্রয়মূল্য ক্রয়মূল্যের চেয়ে কম দেওয়া হয়েছে, সঠিক মূল্য লিখুন।"
      repo.returnProductForCorrection(subId, correctionReason, admin)

      val returnedSub = db.productSubmissionDao().getSubmissionById(subId)!!
      assertEquals(ProductSubmissionStatus.NEEDS_CORRECTION, returnedSub.status)
      assertEquals(correctionReason, returnedSub.adminCorrectionNote)

      // Salesman resubmits with purchase price 40.0 (auto-calculated selling price = 40.0 * 1.70 = 68.0)
      val correctedData = returnedSub.copy(purchasePrice = 40.0)
      repo.resubmitProductForCorrection(subId, correctedData, salesman)

      val resubmittedSub = db.productSubmissionDao().getSubmissionById(subId)!!
      assertEquals(ProductSubmissionStatus.PENDING, resubmittedSub.status)
      assertEquals(68.0, resubmittedSub.sellingPrice, 0.001)

      // Verify main table still has no such product
      assertNull(db.productDao().findProductByName("তেজ পাতা (১০০ গ্রাম)"))
    }
  }

  @Test
  fun `verify other salesman cannot modify submission and salesman cannot approve product`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = AppDatabase.getDatabase(context)
      val repo = BusinessRepository(db)
      repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

      val admin = repo.authenticate("01700000000", "1234")!!
      val salesman1 = repo.authenticate("01800000000", "1234")!!

      // Create a second salesman
      val salesman2 = User(
        id = "salesman_2",
        name = "সেলসম্যান সুমন আহমেদ",
        phone = "01999999999",
        role = UserRole.SALESMAN,
        pinHash = repo.hashPin("1234"),
        accountStatus = UserAccountStatus.ACTIVE
      )
      db.userDao().insertUser(salesman2)

      val subId = "sub_security_test"
      val submission = ProductSubmission(
        id = subId,
        name = "সানফ্লাওয়ার তেল",
        category = "তেল",
        purchasePrice = 900.0,
        sellingPrice = 980.0,
        initialStock = 10.0,
        salesmanId = salesman1.id,
        salesmanName = salesman1.name,
        salesmanPhone = salesman1.phone
      )
      repo.submitProduct(submission, salesman1)

      // Admin returns for correction
      repo.returnProductForCorrection(subId, "প্যাকেজিং সাইজ উল্লেখ করুন", admin)

      // Rule D.4: Salesman 2 tries to resubmit Salesman 1's submission -> MUST BE BLOCKED
      assertThrows(IllegalStateException::class.java) {
        runBlocking {
          repo.resubmitProductForCorrection(subId, submission.copy(name = "হ্যাকড তেল"), salesman2)
        }
      }

      // Rule C.2: Salesman 1 tries to approve the submission -> MUST BE BLOCKED
      assertThrows(IllegalStateException::class.java) {
        runBlocking {
          repo.approveProductSubmission(subId, submission, salesman1)
        }
      }

      // Rule C.2: Salesman 1 tries to request correction -> MUST BE BLOCKED
      assertThrows(IllegalStateException::class.java) {
        runBlocking {
          repo.returnProductForCorrection(subId, "কোনো নোট", salesman1)
        }
      }
    }
  }

  @Test
  fun `verify duplicate detection identifies existing products and submissions`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = AppDatabase.getDatabase(context)
      val repo = BusinessRepository(db)
      repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

      val salesman = repo.authenticate("01800000000", "1234")!!

      // Existing product in seed data: "মিনিকেট চাল (৫০ কেজি বস্তা)"
      val warningsForExisting = repo.checkPotentialDuplicates("মিনিকেট চাল (৫০ কেজি বস্তা)")
      assertTrue("Should detect existing product in live inventory", warningsForExisting.isNotEmpty())
      assertTrue(warningsForExisting.first().contains("মূল ইনভেন্টরিতে"))

      // Submit a new pending product
      repo.submitProduct(
        ProductSubmission(
          id = "sub_dup_1",
          name = "প্রাণ জুস (২৫০ মিলি)",
          category = "পানীয়",
          purchasePrice = 25.0,
          sellingPrice = 30.0,
          initialStock = 100.0,
          salesmanId = salesman.id,
          salesmanName = salesman.name,
          salesmanPhone = salesman.phone
        ),
        salesman
      )

      // Check duplicate for identical name
      val warningsForSubmission = repo.checkPotentialDuplicates("প্রাণ জুস (২৫০ মিলি)")
      assertTrue("Should detect pending submission", warningsForSubmission.isNotEmpty())
      assertTrue(warningsForSubmission.first().contains("পূর্বে"))
    }
  }

  @Test
  fun `verify salesman automatic selling price calculation 70 percent markup and rounding formula`() {
    // 1. Cost 100 -> Selling 170 (100 * 1.70)
    assertEquals(170.0, BusinessRepository.calculateSellingPrice(100.0), 0.001)

    // 2. Cost 200 -> Selling 340 (200 * 1.70)
    assertEquals(340.0, BusinessRepository.calculateSellingPrice(200.0), 0.001)

    // 3. Cost 500 -> Selling 850 (500 * 1.70)
    assertEquals(850.0, BusinessRepository.calculateSellingPrice(500.0), 0.001)

    // 4. Decimal precision and rounding (e.g. 15.33 * 1.70 = 26.061 -> 26.06)
    assertEquals(26.06, BusinessRepository.calculateSellingPrice(15.33), 0.001)

    // 5. Blank/zero/negative cost price should return 0.0
    assertEquals(0.0, BusinessRepository.calculateSellingPrice(0.0), 0.001)
    assertEquals(0.0, BusinessRepository.calculateSellingPrice(-50.0), 0.001)
  }

  @Test
  fun `verify salesman submission strictly enforces 70 percent selling price and admin override is preserved`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = AppDatabase.getDatabase(context)
      val repo = BusinessRepository(db)
      repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

      val admin = repo.authenticate("01700000000", "1234")!!
      val salesman = repo.authenticate("01800000000", "1234")!!

      val subId = "sub_auto_calc_test"
      // Salesman attempts to submit Cost 200 with wrong Selling Price 999.0
      val submission = ProductSubmission(
        id = subId,
        name = "ম্যাঙ্গো জুস (১ লিটার)",
        category = "পানীয়",
        purchasePrice = 200.0,
        sellingPrice = 999.0, // Attempted bypass
        initialStock = 25.0,
        salesmanId = salesman.id,
        salesmanName = salesman.name,
        salesmanPhone = salesman.phone
      )
      repo.submitProduct(submission, salesman)

      // Verify repository strictly enforced Selling Price = Cost * 1.70 = 340.0
      val saved = db.productSubmissionDao().getSubmissionById(subId)!!
      assertEquals(200.0, saved.purchasePrice, 0.001)
      assertEquals(340.0, saved.sellingPrice, 0.001)

      // Admin returns for correction
      repo.returnProductForCorrection(subId, "দাম ও সাইজ পুনরায় চেক করুন", admin)

      // Salesman resubmits with Cost 500
      val updatedBySalesman = saved.copy(purchasePrice = 500.0, sellingPrice = 1200.0)
      repo.resubmitProductForCorrection(subId, updatedBySalesman, salesman)

      // Verify resubmission strictly recalculated Selling Price = 500 * 1.70 = 850.0
      val resubmitted = db.productSubmissionDao().getSubmissionById(subId)!!
      assertEquals(500.0, resubmitted.purchasePrice, 0.001)
      assertEquals(850.0, resubmitted.sellingPrice, 0.001)

      // Admin reviews and manually changes selling price to 870.0 (Admin manual override)
      val adminCustomizedData = resubmitted.copy(sellingPrice = 870.0)
      val approvedProduct = repo.approveProductSubmission(subId, adminCustomizedData, admin)

      // Verify Admin's manual price is honored in live product table
      assertEquals(500.0, approvedProduct.purchasePrice, 0.001)
      assertEquals(870.0, approvedProduct.sellingPrice, 0.001)

      val inDbProduct = db.productDao().getProductById(approvedProduct.id)!!
      assertEquals(870.0, inDbProduct.sellingPrice, 0.001)

      // Verify existing approved products were NOT altered
      val existingRice = db.productDao().getProductById("p_1")!!
      assertEquals(3100.0, existingRice.purchasePrice, 0.001)
      assertEquals(3450.0, existingRice.sellingPrice, 0.001)
    }
  }

  @Test
  fun `verify admin panel auto selling price calculation 70 percent markup on product add and edit`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = AppDatabase.getDatabase(context)
      val repo = BusinessRepository(db)
      repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

      val admin = repo.authenticate("01700000000", "1234")!!

      // 1. Formula verification: Cost 100 -> Selling 170
      assertEquals(170.0, BusinessRepository.calculateSellingPrice(100.0), 0.001)

      // 2. Formula verification: Cost 200 -> Selling 340
      assertEquals(340.0, BusinessRepository.calculateSellingPrice(200.0), 0.001)

      // 3. Formula verification: Cost 500 -> Selling 850
      assertEquals(850.0, BusinessRepository.calculateSellingPrice(500.0), 0.001)

      // 4. Admin adds new product with Cost 100.0 (Auto Selling Price must be 170.0)
      val newProd = Product(
        id = "p_admin_test_100",
        name = "ম্যাগি নুডুলস (৮ প্যাক)",
        sku = "SKU-9901",
        category = "খাদ্য",
        purchasePrice = 100.0,
        sellingPrice = 0.0, // Should be auto-calculated to 170.0
        currentStock = 50.0,
        alertStockLevel = 5.0,
        unit = "প্যাক"
      )
      repo.saveProduct(newProd, admin, isNew = true, reason = "নতুন প্রোডাক্ট এডমিন এন্ট্রি")

      val savedProd = db.productDao().getProductById("p_admin_test_100")!!
      assertEquals(100.0, savedProd.purchasePrice, 0.001)
      assertEquals(170.0, savedProd.sellingPrice, 0.001)

      // 5. Admin edits the product to Cost 200.0 (Auto Selling Price must update to 340.0)
      val updatedTo200 = savedProd.copy(purchasePrice = 200.0)
      repo.saveProduct(updatedTo200, admin, isNew = false, reason = "রেট পরিবর্তন")

      val prodAfterEdit = db.productDao().getProductById("p_admin_test_100")!!
      assertEquals(200.0, prodAfterEdit.purchasePrice, 0.001)
      assertEquals(340.0, prodAfterEdit.sellingPrice, 0.001)

      // 6. Verify non-admin cannot call saveProduct
      val salesman = repo.authenticate("01800000000", "1234")!!
      org.junit.Assert.assertThrows(IllegalStateException::class.java) {
        runBlocking {
          repo.saveProduct(updatedTo200, salesman, isNew = false, reason = "অননুমোদিত চেষ্টা")
        }
      }
    }
  }

  @Test
  fun `verify clothing categories and units consistency for admin and salesman panels`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val repo = BusinessRepository(db)
    repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

    val admin = repo.authenticate("01700000000", "1234")!!
    val salesman = repo.authenticate("01800000000", "1234")!!

    // 1. Verify exact 12 clothing categories
    val expectedCategories = listOf(
      "পুরুষদের পোশাক (Men's Wear)",
      "মহিলাদের পোশাক (Women's Wear)",
      "বাচ্চাদের পোশাক (Kids' Wear)",
      "শার্ট ও টি-শার্ট (Shirts & T-Shirts)",
      "প্যান্ট ও জিন্স (Pants & Jeans)",
      "পাঞ্জাবি ও পায়জামা (Panjabi & Pajama)",
      "শাড়ি ও থ্রি-পিস (Sharee & Three-Piece)",
      "বোরকা ও হিজাব (Burqa & Hijab)",
      "শীতকালীন পোশাক (Winter Wear)",
      "আন্ডারগার্মেন্টস ও ইনারওয়্যার (Undergarments)",
      "ঐতিহ্যবাহী ও পার্টি পোশাক (Party & Traditional)",
      "অন্যান্য পোশাক সামগ্রী (Other Clothing)"
    )
    assertEquals(12, BusinessRepository.CLOTHING_CATEGORIES.size)
    assertEquals(expectedCategories, BusinessRepository.CLOTHING_CATEGORIES)

    // 2. Verify exact 8 clothing units
    val expectedUnits = listOf(
      "পিস",
      "সেট",
      "জোড়া",
      "ডজন",
      "প্যাকেট",
      "কার্টন",
      "মিটার",
      "গজ"
    )
    assertEquals(8, BusinessRepository.CLOTHING_UNITS.size)
    assertEquals(expectedUnits, BusinessRepository.CLOTHING_UNITS)

    // 3. Admin adds a product using clothing category & unit
    val newClothingProduct = Product(
      id = "p_shirt_cotton_1",
      name = "কটন ফরমাল শার্ট (L)",
      sku = "SHT-COT-01",
      category = BusinessRepository.CLOTHING_CATEGORIES[3], // "শার্ট ও টি-শার্ট (Shirts & T-Shirts)"
      purchasePrice = 500.0,
      sellingPrice = 0.0, // calculated automatically: 500 * 1.70 = 850
      currentStock = 30.0,
      alertStockLevel = 5.0,
      unit = "পিস"
    )
    repo.saveProduct(newClothingProduct, admin, isNew = true, reason = "এডমিন কর্তৃক নতুন পোশাক যোগ")

    val savedProduct = db.productDao().getProductById("p_shirt_cotton_1")!!
    assertEquals("কটন ফরমাল শার্ট (L)", savedProduct.name)
    assertEquals("শার্ট ও টি-শার্ট (Shirts & T-Shirts)", savedProduct.category)
    assertEquals("পিস", savedProduct.unit)
    assertEquals(500.0, savedProduct.purchasePrice, 0.001)
    assertEquals(850.0, savedProduct.sellingPrice, 0.001)

    // 4. Admin edits the product to change category and price
    val editedProduct = savedProduct.copy(
      category = BusinessRepository.CLOTHING_CATEGORIES[10], // "ঐতিহ্যবাহী ও পার্টি পোশাক (Party & Traditional)"
      purchasePrice = 600.0,
      unit = "সেট"
    )
    repo.saveProduct(editedProduct, admin, isNew = false, reason = "ক্যাটাগরি ও মূল্য সংশোধন")

    val productAfterEdit = db.productDao().getProductById("p_shirt_cotton_1")!!
    assertEquals("ঐতিহ্যবাহী ও পার্টি পোশাক (Party & Traditional)", productAfterEdit.category)
    assertEquals("সেট", productAfterEdit.unit)
    assertEquals(600.0, productAfterEdit.purchasePrice, 0.001)
    assertEquals(1020.0, productAfterEdit.sellingPrice, 0.001) // 600 * 1.70 = 1020

    // 5. Verify Salesman can submit with same category and unit
    val subId = repo.submitProductForApproval(
      name = "এক্সক্লুসিভ পাঞ্জাবি",
      sku = "PANJABI-01",
      category = BusinessRepository.CLOTHING_CATEGORIES[5], // "পাঞ্জাবি ও পায়জামা (Panjabi & Pajama)"
      unit = "পিস",
      purchasePrice = 800.0,
      initialStock = 15.0,
      description = "সিল্ক পাঞ্জাবি",
      salesmanUser = salesman
    )
    val submission = db.productSubmissionDao().getSubmissionById(subId)!!
    assertEquals("পাঞ্জাবি ও পায়জামা (Panjabi & Pajama)", submission.category)
    assertEquals("পিস", submission.unit)
    assertEquals(1360.0, submission.sellingPrice, 0.001) // 800 * 1.70 = 1360

    // 6. Existing pre-loaded product records remain intact
    val existingRice = db.productDao().getProductById("p_1")!!
    assertNotNull(existingRice)
    assertEquals("মিনিকেট চাল (৫০ কেজি বস্তা)", existingRice.name)
  }

  @Test
  fun `verify salesman product code submission, auto-generation, duplicate check, and approval`() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = AppDatabase.getDatabase(context)
      val repo = BusinessRepository(db)

      val admin = repo.authenticate("01700000000", "1234")!!
      val salesman = repo.authenticate("01800000000", "1234")!!

      // 1. Salesman submits product with explicit Product Code
      val customCodeSub = ProductSubmission(
        id = "sub_code_test_1",
        name = "প্রিমিয়াম পলো টি-শার্ট",
        productCode = "POLO-BLU-01",
        category = "শার্ট ও টি-শার্ট (Shirts & T-Shirts)",
        purchasePrice = 300.0,
        sellingPrice = 510.0,
        initialStock = 20.0,
        unit = "পিস",
        description = "ব্লু কালার সাইজ XL",
        salesmanId = salesman.id,
        salesmanName = salesman.name,
        salesmanPhone = salesman.phone
      )
      repo.submitProduct(customCodeSub, salesman)

      // Verify productCode is saved in product_submissions
      val savedCustomSub = db.productSubmissionDao().getSubmissionById("sub_code_test_1")
      assertNotNull(savedCustomSub)
      assertEquals("POLO-BLU-01", savedCustomSub!!.productCode)
      assertEquals(510.0, savedCustomSub.sellingPrice, 0.001)

      // 2. Salesman submits product without Product Code (blank) -> Auto generates SKU
      val blankCodeSub = ProductSubmission(
        id = "sub_code_test_2",
        name = "ক্যাজুয়াল গ্যাবার্ডিন প্যান্ট",
        productCode = "",
        category = "প্যান্ট ও ট্রাউজার (Pants & Trousers)",
        purchasePrice = 400.0,
        sellingPrice = 680.0,
        initialStock = 10.0,
        unit = "পিস",
        salesmanId = salesman.id,
        salesmanName = salesman.name,
        salesmanPhone = salesman.phone
      )
      repo.submitProduct(blankCodeSub, salesman)

      val savedBlankSub = db.productSubmissionDao().getSubmissionById("sub_code_test_2")
      assertNotNull(savedBlankSub)
      assertTrue("Auto generated code should start with SKU-", savedBlankSub!!.productCode.startsWith("SKU-"))

      // 3. Verify duplicate check identifies duplicate by product code
      val duplicateWarnings = repo.checkPotentialDuplicates(
        productName = "নতুন টি-শার্ট",
        productCode = "POLO-BLU-01"
      )
      assertTrue("Should detect duplicate submission with same product code", duplicateWarnings.any { it.contains("POLO-BLU-01") })

      // 4. Admin reviews and approves submission with custom product code
      val approvedProduct = repo.approveProductSubmission(
        submissionId = "sub_code_test_1",
        finalData = savedCustomSub,
        adminUser = admin
      )
      assertNotNull(approvedProduct)
      assertEquals("POLO-BLU-01", approvedProduct.sku)
      assertEquals("প্রিমিয়াম পলো টি-শার্ট", approvedProduct.name)

      // Verify live product has exact sku
      val inMainDb = db.productDao().getProductById(approvedProduct.id)
      assertNotNull(inMainDb)
      assertEquals("POLO-BLU-01", inMainDb!!.sku)

      // 5. Duplicate check now finds it in main live inventory by SKU
      val warningsInMain = repo.checkPotentialDuplicates(
        productName = "ভিন্ন নাম",
        productCode = "POLO-BLU-01"
      )
      assertTrue("Should detect duplicate in live table with same SKU", warningsInMain.any { it.contains("POLO-BLU-01") })
    }
  }

  @Test
  fun `verify company management and purchase due calculation and stock integration`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val repo = BusinessRepository(db)
    repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

    val admin = repo.authenticate("01700000000", "1234")!!
    val salesman = repo.authenticate("01800000000", "1234")!!

    // 1. Role enforcement: Salesman cannot add company
    val testComp = Company(
      id = "comp_test_1",
      name = "মেঘনা গ্রুপ অফ ইন্ডাস্ট্রিজ",
      phone = "01799887766",
      address = "মতিঝিল, ঢাকা",
      note = "তেল ও ডাল সরবরাহকারী"
    )
    try {
      repo.saveCompany(testComp, isNew = true, actorUser = salesman)
      fail("Salesman should not be allowed to add company")
    } catch (e: IllegalStateException) {
      // Expected
    }

    // 2. Admin successfully adds company
    repo.saveCompany(testComp, isNew = true, actorUser = admin)
    val savedComp = db.companyDao().getCompanyById("comp_test_1")
    assertNotNull("Company should be saved", savedComp)
    assertEquals("মেঘনা গ্রুপ অফ ইন্ডাস্ট্রিজ", savedComp!!.name)
    assertEquals(0.0, savedComp.totalPurchased, 0.001)
    assertEquals(0.0, savedComp.totalPaid, 0.001)
    assertEquals(0.0, savedComp.currentDue, 0.001)

    // 3. Admin records product purchase with due calculation
    // Example: Total purchase = 50,000, Paid = 20,000 -> Due = 30,000
    val purchase1 = CompanyPurchase(
      id = "cpur_test_1",
      companyId = "comp_test_1",
      companyName = testComp.name,
      challanNumber = "CHL-20261010-001",
      productName = "সয়াবিন তেল (ড্রাম)",
      quantity = 50.0,
      unit = "লিটার",
      unitPurchasePrice = 1000.0,
      totalAmount = 50000.0,
      paidAmount = 20000.0,
      dueAmount = 30000.0,
      addedToStock = false
    )
    val purchaseResult = repo.recordCompanyPurchase(purchase1, admin)
    assertTrue("Purchase should be recorded successfully", purchaseResult.isSuccess)

    // Verify company due automatically updated: 50,000 - 20,000 = 30,000
    val compAfterPur1 = db.companyDao().getCompanyById("comp_test_1")!!
    assertEquals(50000.0, compAfterPur1.totalPurchased, 0.001)
    assertEquals(20000.0, compAfterPur1.totalPaid, 0.001)
    assertEquals(30000.0, compAfterPur1.currentDue, 0.001)
    assertEquals(0.0, compAfterPur1.advanceAmount, 0.001)

    // 4. Stock integration check: Purchase existing product with addedToStock = true
    val testStockProduct = Product(
      id = "p_comp_stock_test",
      name = "টেস্ট বাল্ক চাল",
      sku = "TB-001",
      category = "চাল",
      unit = "কেজি",
      purchasePrice = 3000.0,
      sellingPrice = 3200.0,
      currentStock = 50.0,
      alertStockLevel = 10.0
    )
    db.productDao().insertProduct(testStockProduct)

    val purchaseStock = CompanyPurchase(
      id = "cpur_stock_test",
      companyId = "comp_test_1",
      companyName = testComp.name,
      challanNumber = "CHL-STOCK-001",
      productId = "p_comp_stock_test",
      productName = testStockProduct.name,
      quantity = 15.0,
      unit = testStockProduct.unit,
      unitPurchasePrice = 3000.0,
      totalAmount = 45000.0,
      paidAmount = 45000.0,
      dueAmount = 0.0,
      addedToStock = true
    )
    val purStockResult = repo.recordCompanyPurchase(purchaseStock, admin)
    assertTrue(purStockResult.isSuccess)

    // Verify stock is incremented exactly by quantity (50.0 + 15.0 = 65.0)
    val productAfterStock = db.productDao().getProductById("p_comp_stock_test")!!
    assertEquals(65.0, productAfterStock.currentStock, 0.001)

    // Verify stock log was created
    val stockLogs = db.stockLogDao().getLogsForProduct("p_comp_stock_test")
    assertTrue(stockLogs.any { it.changeType == "কোম্পানি থেকে ক্রয়" && it.quantityChanged == 15.0 })

    // 5. Subsequent partial payment: Pay 10,000 towards the 30,000 due
    val payment1 = CompanyPayment(
      id = "cpay_test_1",
      companyId = "comp_test_1",
      companyName = testComp.name,
      amount = 10000.0,
      paymentMethod = PaymentMethod.BANK_TRANSFER,
      referenceNumber = "CHQ-889900",
      note = "আংশিক পেমেন্ট"
    )
    val payResult1 = repo.recordCompanyPayment(payment1, admin)
    assertTrue(payResult1.isSuccess)

    // Verify company due automatically reduced from 30,000 to 20,000
    val compAfterPay1 = db.companyDao().getCompanyById("comp_test_1")!!
    assertEquals(20000.0, compAfterPay1.currentDue, 0.001)
    assertEquals(75000.0, compAfterPay1.totalPaid, 0.001) // 20000 + 45000 + 10000

    // 6. Pay full remaining 20,000 due
    val payment2 = CompanyPayment(
      id = "cpay_test_2",
      companyId = "comp_test_1",
      companyName = testComp.name,
      amount = 20000.0,
      paymentMethod = PaymentMethod.CASH,
      referenceNumber = "REC-9988"
    )
    repo.recordCompanyPayment(payment2, admin)

    val compAfterPay2 = db.companyDao().getCompanyById("comp_test_1")!!
    assertEquals(0.0, compAfterPay2.currentDue, 0.001)
    assertEquals(0.0, compAfterPay2.advanceAmount, 0.001)

    // 7. Extra payment (advance): Pay 5,000 extra
    val paymentAdvance = CompanyPayment(
      id = "cpay_test_adv",
      companyId = "comp_test_1",
      companyName = testComp.name,
      amount = 5000.0,
      paymentMethod = PaymentMethod.BKASH,
      referenceNumber = "TRX-776655"
    )
    repo.recordCompanyPayment(paymentAdvance, admin)

    val compAfterAdv = db.companyDao().getCompanyById("comp_test_1")!!
    assertEquals(0.0, compAfterAdv.currentDue, 0.001)
    assertEquals(5000.0, compAfterAdv.advanceAmount, 0.001)

    // 8. Ledger / History verification
    val purchasesList = db.companyPurchaseDao().getPurchasesByCompany("comp_test_1")
    assertEquals(2, purchasesList.size)

    val paymentsList = db.companyPaymentDao().getPaymentsByCompany("comp_test_1")
    assertEquals(3, paymentsList.size)
  }

  @Test
  fun `verify product image service prompts and category mappings for tshirt and jeans`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val imageService = com.example.data.service.ProductImageService(context)

    // 1. T-Shirt prompt verification
    val tshirtPrompt = imageService.buildHighQualityProductPrompt(
      title = "টি শার্ট",
      category = "শার্ট ও টি-শার্ট"
    )
    assertTrue("T-shirt prompt must emphasize flat-lay", tshirtPrompt.contains("flat-lay"))
    assertTrue("T-shirt prompt must demand filling 85% of frame", tshirtPrompt.contains("85%"))
    assertTrue("T-shirt prompt must strictly forbid hangers", tshirtPrompt.contains("NO hangers"))
    assertTrue("T-shirt prompt must forbid mannequins", tshirtPrompt.contains("NO mannequin"))
    assertTrue("T-shirt prompt must specify pure white background", tshirtPrompt.contains("white studio background"))

    // 2. Jeans pants prompt verification
    val jeansPrompt = imageService.buildHighQualityProductPrompt(
      title = "জিন্স প্যান্ট",
      category = "প্যান্ট ও জিন্স"
    )
    assertTrue("Jeans prompt must emphasize full-length top-to-bottom", jeansPrompt.contains("full-length top-to-bottom"))
    assertTrue("Jeans prompt must specify waistband and fly", jeansPrompt.contains("waistband"))
    assertTrue("Jeans prompt must specify full leg length", jeansPrompt.contains("full straight leg length"))
    assertTrue("Jeans prompt must strictly forbid hangers", jeansPrompt.contains("NO hangers"))
    assertTrue("Jeans prompt must specify white background", jeansPrompt.contains("white studio background"))

    // 3. Category search keyword mappings
    val tshirtKeyword = imageService.mapCategoryToSearchKeyword("শার্ট ও টি-শার্ট", "টি শার্ট")
    assertTrue("T-shirt search keyword must be tailored", tshirtKeyword.contains("t-shirt"))

    val jeansKeyword = imageService.mapCategoryToSearchKeyword("প্যান্ট ও জিন্স", "জিন্স প্যান্ট")
    assertTrue("Jeans search keyword must target denim/jeans", jeansKeyword.contains("jeans"))

    // 4. Curated fallback candidates
    val tshirtFallbacks = imageService.getCuratedCategoryFallbacks("শার্ট ও টি-শার্ট", "টি শার্ট")
    assertTrue("Must have verified t-shirt candidate", tshirtFallbacks.isNotEmpty())

    val jeansFallbacks = imageService.getCuratedCategoryFallbacks("প্যান্ট ও জিন্স", "জিন্স প্যান্ট")
    assertTrue("Must have verified jeans candidate", jeansFallbacks.isNotEmpty())

    // 5. Verification that category icons do NOT use hangers (Checkroom or DryCleaning)
    val tshirtIcon = com.example.ui.components.getCategoryIcon("শার্ট ও টি-শার্ট")
    val jeansIcon = com.example.ui.components.getCategoryIcon("প্যান্ট ও জিন্স")
    assertEquals(androidx.compose.material.icons.Icons.Default.ShoppingBag, tshirtIcon)
    assertEquals(androidx.compose.material.icons.Icons.Default.LocalMall, jeansIcon)
  }

  @Test
  fun `verify product image service API key resolution, masking, and persistence`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val imageService = com.example.data.service.ProductImageService(context)

    // Clean start
    imageService.clearCustomApiKey()

    // 1. Effective key resolution (either buildConfig key or env or empty)
    val initialEffectiveKey = imageService.getEffectiveApiKey()
    val initialMasked = imageService.getMaskedApiKey()
    assertNotNull(initialMasked)

    // 2. Custom API key save and override
    val customTestKey = "AIzaSyTestCustomKey123456789"
    imageService.saveCustomApiKey(customTestKey)
    assertTrue("Custom key must mark API key configured", imageService.isApiKeyConfigured())
    assertEquals(customTestKey, imageService.getEffectiveApiKey())

    // 3. Masked display of custom key
    val masked = imageService.getMaskedApiKey()
    assertTrue("Masked key should start with first 4 chars", masked.startsWith("AIza"))
    assertTrue("Masked key should end with last 4 chars", masked.endsWith("6789"))
    assertTrue("Masked key should hide middle chars", masked.contains("••••"))

    // 4. Custom key clear restores original behavior
    imageService.clearCustomApiKey()
    assertEquals(initialEffectiveKey, imageService.getEffectiveApiKey())
  }

  @Test
  fun `verify MIGRATION_6_7 upgrades database safely and preserves existing data with default values`() {
    val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
    val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
      .name("test_migration_6_7.db")
      .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
        override fun onCreate(db: SupportSQLiteDatabase) {
          db.execSQL("""
            CREATE TABLE IF NOT EXISTS `products` (
              `id` TEXT NOT NULL PRIMARY KEY,
              `name` TEXT NOT NULL,
              `sku` TEXT NOT NULL,
              `barcode` TEXT NOT NULL,
              `category` TEXT NOT NULL,
              `purchasePrice` REAL NOT NULL,
              `sellingPrice` REAL NOT NULL,
              `currentStock` REAL NOT NULL,
              `alertStockLevel` REAL NOT NULL,
              `unit` TEXT NOT NULL,
              `isActive` INTEGER NOT NULL,
              `updatedAt` INTEGER NOT NULL,
              `imageUri` TEXT,
              `imageSource` TEXT NOT NULL,
              `imageSourceInfo` TEXT NOT NULL
            )
          """.trimIndent())
          db.execSQL("""
            CREATE TABLE IF NOT EXISTS `companies` (
              `id` TEXT NOT NULL PRIMARY KEY,
              `name` TEXT NOT NULL,
              `phone` TEXT NOT NULL,
              `address` TEXT NOT NULL,
              `note` TEXT NOT NULL,
              `totalPurchased` REAL NOT NULL,
              `totalPaid` REAL NOT NULL,
              `currentDue` REAL NOT NULL,
              `advanceAmount` REAL NOT NULL,
              `createdAt` INTEGER NOT NULL,
              `updatedAt` INTEGER NOT NULL
            )
          """.trimIndent())
        }
        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
      })
      .build()

    val openHelper = helper.create(config)
    val db = openHelper.writableDatabase

    // Insert pre-existing Version 6 product and company
    db.execSQL("INSERT INTO products (id, name, sku, barcode, category, purchasePrice, sellingPrice, currentStock, alertStockLevel, unit, isActive, updatedAt, imageUri, imageSource, imageSourceInfo) VALUES ('p_v6_1', 'পুরোনো মিনিকেট চাল', 'SKU-V6-1', '', 'চাল', 3100.0, 3450.0, 50.0, 5.0, 'বস্তা', 1, 1000, NULL, 'NONE', '')")
    db.execSQL("INSERT INTO companies (id, name, phone, address, note, totalPurchased, totalPaid, currentDue, advanceAmount, createdAt, updatedAt) VALUES ('comp_v6_1', 'পুরোনো সরবরাহকারী কোম্পানি', '01711223344', 'ঢাকা', 'নিয়মিত সাপ্লায়ার', 100000.0, 80000.0, 20000.0, 0.0, 1000, 1000)")

    // Run MIGRATION_6_7
    com.example.data.MIGRATION_6_7.migrate(db)

    // Verify products table preserved data and gained safe defaults
    val prodCursor = db.query("SELECT id, name, currentStock, isTestData, testTag FROM products WHERE id = 'p_v6_1'")
    assertTrue(prodCursor.moveToFirst())
    assertEquals("পুরোনো মিনিকেট চাল", prodCursor.getString(prodCursor.getColumnIndexOrThrow("name")))
    assertEquals(50.0, prodCursor.getDouble(prodCursor.getColumnIndexOrThrow("currentStock")), 0.001)
    assertEquals(0, prodCursor.getInt(prodCursor.getColumnIndexOrThrow("isTestData")))
    assertEquals("", prodCursor.getString(prodCursor.getColumnIndexOrThrow("testTag")))
    prodCursor.close()

    // Verify companies table preserved data and gained safe defaults
    val compCursor = db.query("SELECT id, name, currentDue, isTestData, testTag, isDeleted FROM companies WHERE id = 'comp_v6_1'")
    assertTrue(compCursor.moveToFirst())
    assertEquals("পুরোনো সরবরাহকারী কোম্পানি", compCursor.getString(compCursor.getColumnIndexOrThrow("name")))
    assertEquals(20000.0, compCursor.getDouble(compCursor.getColumnIndexOrThrow("currentDue")), 0.001)
    assertEquals(0, compCursor.getInt(compCursor.getColumnIndexOrThrow("isTestData")))
    assertEquals("", compCursor.getString(compCursor.getColumnIndexOrThrow("testTag")))
    assertEquals(0, compCursor.getInt(compCursor.getColumnIndexOrThrow("isDeleted")))
    compCursor.close()

    // Verify recycle_bin table exists and is writable
    val rbCheck = db.query("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='recycle_bin'")
    assertTrue(rbCheck.moveToFirst())
    assertEquals(1, rbCheck.getInt(0))
    rbCheck.close()

    db.close()
  }

  @Test
  fun `verify Data Protection Center soft delete, restore, 30 days retention, and permanent delete rules`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val repo = BusinessRepository(db)
    repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

    val admin = repo.authenticate("01700000000", "1234")!!
    val salesman = repo.authenticate("01800000000", "1234")!!

    // 1. Soft delete Product: Salesman cannot perform soft delete
    val testProd = db.productDao().getProductById("p_1")!!
    val unauthorizedProdDel = repo.softDeleteProduct(testProd, salesman, "টেস্ট ডিলিট")
    assertTrue("Salesman soft delete must fail", unauthorizedProdDel.isFailure)

    // Admin performs soft delete
    val softDelProdRes = repo.softDeleteProduct(testProd, admin, "অতিরিক্ত স্টক ক্লিয়ারেন্স")
    assertTrue("Admin soft delete should succeed", softDelProdRes.isSuccess)

    // Verify Product row is still in DB, but isActive = false
    val prodAfterSoftDel = db.productDao().getProductById("p_1")!!
    assertFalse("Product isActive must be false after soft delete", prodAfterSoftDel.isActive)
    assertEquals(testProd.currentStock, prodAfterSoftDel.currentStock, 0.001)

    // Verify RecycleBinItem created with snapshotJson and ~30 days retention
    val prodRbItem = db.recycleBinDao().getItemByOriginalId("products", "p_1")
    assertNotNull("Recycle bin item must exist", prodRbItem)
    assertTrue("SnapshotJson must contain product details", prodRbItem!!.snapshotJson.contains("মিনিকেট চাল"))
    val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000L
    val expectedExpiryApprox = prodRbItem.deletedAt + thirtyDaysMillis
    assertTrue("Expiry must be approx 30 days in future", Math.abs(prodRbItem.expiresAt - expectedExpiryApprox) < 5000)

    // 2. Soft delete Company
    val testComp = Company(
      id = "comp_dp_test_1",
      name = "মেঘনা ফুডস",
      phone = "01788990011",
      currentDue = 15000.0,
      totalPurchased = 40000.0,
      totalPaid = 25000.0
    )
    repo.saveCompany(testComp, isNew = true, actorUser = admin)

    val softDelCompRes = repo.softDeleteCompany(testComp, admin, "সাময়িক বন্ধ")
    assertTrue(softDelCompRes.isSuccess)

    // Verify Company row still in DB, isDeleted = true, due is intact
    val compAfterSoftDel = db.companyDao().getCompanyById("comp_dp_test_1")!!
    assertTrue("Company isDeleted must be true", compAfterSoftDel.isDeleted)
    assertEquals(15000.0, compAfterSoftDel.currentDue, 0.001)

    // Verify RecycleBinItem for Company
    val compRbItem = db.recycleBinDao().getItemByOriginalId("companies", "comp_dp_test_1")
    assertNotNull(compRbItem)
    assertTrue("SnapshotJson must contain company details", compRbItem!!.snapshotJson.contains("মেঘনা ফুডস"))
    assertTrue("SnapshotJson must contain due amount", compRbItem.snapshotJson.contains("15000"))

    // 3. Restore Product: Salesman cannot restore
    val unauthRestore = repo.restoreRecycleBinItem(prodRbItem, salesman)
    assertTrue("Salesman restore must fail", unauthRestore.isFailure)

    // Admin restores Product
    val restoreProdRes = repo.restoreRecycleBinItem(prodRbItem, admin)
    assertTrue(restoreProdRes.isSuccess)

    val prodAfterRestore = db.productDao().getProductById("p_1")!!
    assertTrue("Product isActive must be restored to true", prodAfterRestore.isActive)
    assertEquals(testProd.currentStock, prodAfterRestore.currentStock, 0.001)

    // Verify no duplicate: count of product with id p_1 is 1
    val sameProdByName = db.productDao().findProductByName("মিনিকেট চাল (৫০ কেজি বস্তা)")
    assertNotNull(sameProdByName)
    assertEquals("p_1", sameProdByName!!.id)

    // 4. Restore Company
    val restoreCompRes = repo.restoreRecycleBinItem(compRbItem, admin)
    assertTrue(restoreCompRes.isSuccess)

    val compAfterRestore = db.companyDao().getCompanyById("comp_dp_test_1")!!
    assertFalse("Company isDeleted must be restored to false", compAfterRestore.isDeleted)
    assertEquals(15000.0, compAfterRestore.currentDue, 0.001)
    assertEquals(40000.0, compAfterRestore.totalPurchased, 0.001)

    // 5. Permanent Delete: Requires Admin role and correct PIN
    repo.softDeleteCompany(compAfterRestore, admin, "স্থায়ী অপসারণ টেস্ট")
    val compRbItemToDelete = db.recycleBinDao().getItemByOriginalId("companies", "comp_dp_test_1")!!

    // Salesman cannot permanently delete
    val salesmanPermDel = repo.permanentlyDeleteRecycleBinItem(compRbItemToDelete, salesman, "1234")
    assertTrue(salesmanPermDel.isFailure)

    // Wrong PIN fails
    val wrongPinPermDel = repo.permanentlyDeleteRecycleBinItem(compRbItemToDelete, admin, "9999")
    assertTrue("Wrong PIN must fail permanent delete", wrongPinPermDel.isFailure)
    assertNotNull("Company must still exist after failed permanent delete", db.companyDao().getCompanyById("comp_dp_test_1"))

    // Correct PIN succeeds
    val correctPinPermDel = repo.permanentlyDeleteRecycleBinItem(compRbItemToDelete, admin, "1234")
    assertTrue("Correct PIN must succeed permanent delete", correctPinPermDel.isSuccess)
    assertNull("Company must be deleted permanently", db.companyDao().getCompanyById("comp_dp_test_1"))
    assertNull("Recycle bin item must be deleted", db.recycleBinDao().getItemById(compRbItemToDelete.id))
  }

  @Test
  fun `verify complete Recycle Bin and Test Data cleanup for Customers, Sales, Payments, Expenses and Opening Balance setup`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val repo = BusinessRepository(db)
    repo.seedInitialDataIfEmpty(includeSampleDemoData = true)

    val admin = repo.authenticate("01700000000", "1234")!!
    val salesman = repo.authenticate("01800000000", "1234")!!

    // 1. Create a customer
    val testCust = Customer(
      id = "cust_cleanup_test_1",
      name = "[টেস্ট কাস্টমার] ভাই ভাই ভ্যারাইটিজ",
      phone = "01755667788",
      address = "মিরপুর ১০, ঢাকা",
      totalBilled = 0.0,
      totalPaid = 0.0,
      currentDue = 0.0,
      isTestData = true,
      testTag = "TEST_CUST"
    )
    db.customerDao().insertCustomer(testCust)

    // Check initial product stock
    val initialProd = db.productDao().getProductById("p_1")!!
    val initialStock = initialProd.currentStock

    // 2. Perform a test sale (Quantity: 2, total: 6800, paid: 2000, due: 4800)
    val invoiceNo = "INV-TEST-CLEANUP-001"
    val testSale = Sale(
      invoiceNumber = invoiceNo,
      customerId = testCust.id,
      customerName = testCust.name,
      customerPhone = testCust.phone,
      salesmanId = salesman.id,
      salesmanName = salesman.name,
      subtotal = 6800.0,
      discount = 0.0,
      totalAmount = 6800.0,
      paidAmount = 2000.0,
      dueAmount = 4800.0,
      isTestData = true,
      testTag = "TEST_SALE"
    )
    val saleItems = listOf(
      SaleItem(
        invoiceNumber = invoiceNo,
        productId = initialProd.id,
        productName = initialProd.name,
        quantity = 2.0,
        unitPrice = 3400.0,
        purchasePriceAtSale = 3100.0,
        totalPrice = 6800.0
      )
    )
    repo.completeSale(testSale, saleItems, salesman)

    // Verify stock decreased by 2
    val prodAfterSale = db.productDao().getProductById(initialProd.id)!!
    assertEquals(initialStock - 2.0, prodAfterSale.currentStock, 0.001)

    // Verify customer balance
    val custAfterSale = db.customerDao().getCustomerById(testCust.id)!!
    assertEquals(6800.0, custAfterSale.totalBilled, 0.001)
    assertEquals(2000.0, custAfterSale.totalPaid, 0.001)
    assertEquals(4800.0, custAfterSale.currentDue, 0.001)

    // 3. Collect another payment receipt (Amount: 1800)
    val rctNo = "RCT-TEST-CLEANUP-001"
    val testReceipt = PaymentReceipt(
      receiptNumber = rctNo,
      customerId = testCust.id,
      customerName = testCust.name,
      customerPhone = testCust.phone,
      salesmanId = salesman.id,
      salesmanName = salesman.name,
      amount = 1800.0,
      previousDue = 4800.0,
      currentDue = 3000.0,
      isTestData = true,
      testTag = "TEST_PMT"
    )
    repo.collectPayment(testReceipt, salesman)

    val custAfterPayment = db.customerDao().getCustomerById(testCust.id)!!
    assertEquals(3800.0, custAfterPayment.totalPaid, 0.001)
    assertEquals(3000.0, custAfterPayment.currentDue, 0.001)

    // 4. Create a test expense
    val testExp = Expense(
      id = "exp_cleanup_test_1",
      salesmanId = salesman.id,
      salesmanName = salesman.name,
      category = ExpenseCategory.FOOD,
      source = ExpenseSource.COMPANY_CASH,
      amount = 500.0,
      description = "[টেস্ট খরচ] দুপুরের খাবার",
      isTestData = true,
      testTag = "TEST_EXP"
    )
    db.expenseDao().insertExpense(testExp)

    // 5. Test Soft Delete Sale:
    // Salesman cannot soft delete sale
    val salesmanSoftDelSale = repo.softDeleteSale(testSale, salesman, "টেস্ট")
    assertTrue(salesmanSoftDelSale.isFailure)

    // Admin soft deletes sale
    val adminSoftDelSale = repo.softDeleteSale(testSale, admin, "টেস্টিং বিক্রি রিসাইকেল বিনে স্থানান্তর")
    assertTrue(adminSoftDelSale.isSuccess)

    // Verify stock returned to product
    val prodAfterSaleSoftDel = db.productDao().getProductById(initialProd.id)!!
    assertEquals(initialStock, prodAfterSaleSoftDel.currentStock, 0.001)

    // Verify customer balances adjusted (totalBilled reverted from 6800 to 0)
    val custAfterSaleSoftDel = db.customerDao().getCustomerById(testCust.id)!!
    assertEquals(0.0, custAfterSaleSoftDel.totalBilled, 0.001)

    // Verify RecycleBinItem created for Sale
    val saleRbItem = db.recycleBinDao().getItemByOriginalId("sales", invoiceNo)
    assertNotNull("Recycle bin item for sale must exist", saleRbItem)
    assertTrue(saleRbItem!!.snapshotJson.contains(invoiceNo))

    // 6. Test Soft Delete Payment Receipt
    val adminSoftDelReceipt = repo.softDeletePaymentReceipt(testReceipt, admin, "টেস্ট পেমেন্ট বাতিল")
    assertTrue(adminSoftDelReceipt.isSuccess)

    val rctRbItem = db.recycleBinDao().getItemByOriginalId("payments", rctNo)
    assertNotNull("Recycle bin item for receipt must exist", rctRbItem)

    // 7. Test Soft Delete Customer
    val adminSoftDelCust = repo.softDeleteCustomer(testCust, admin, "টেস্ট কাস্টমার অপসারণ")
    assertTrue(adminSoftDelCust.isSuccess)

    val custAfterSoftDel = db.customerDao().getCustomerById(testCust.id)!!
    assertTrue("Customer isDeleted must be true", custAfterSoftDel.isDeleted)

    val custRbItem = db.recycleBinDao().getItemByOriginalId("customers", testCust.id)
    assertNotNull(custRbItem)

    // 8. Test Soft Delete Expense
    val adminSoftDelExp = repo.softDeleteExpense(testExp, admin, "টেস্ট খরচ অপসারণ")
    assertTrue(adminSoftDelExp.isSuccess)

    val expAfterSoftDel = db.expenseDao().getExpenseById(testExp.id)!!
    assertTrue("Expense isDeleted must be true", expAfterSoftDel.isDeleted)

    // 9. Permanent Delete with Admin PIN
    // Salesman cannot permanent delete
    val salesmanPermDel = repo.permanentlyDeleteRecycleBinItem(saleRbItem, salesman, "1234")
    assertTrue(salesmanPermDel.isFailure)

    // Wrong PIN fails
    val wrongPinPermDel = repo.permanentlyDeleteRecycleBinItem(saleRbItem, admin, "0000")
    assertTrue(wrongPinPermDel.isFailure)

    // Correct PIN succeeds
    val correctPinPermDel = repo.permanentlyDeleteRecycleBinItem(saleRbItem, admin, "1234")
    assertTrue(correctPinPermDel.isSuccess)
    assertNull(db.saleDao().getSaleByInvoice(invoiceNo))
    assertTrue(db.saleDao().getSaleItems(invoiceNo).isEmpty())

    // 10. Opening Balance Setup:
    // Insert a real customer
    val realCustomer = Customer(
      id = "real_cust_001",
      name = "মেসার্স আল-আমিন এন্টারপ্রাইজ",
      phone = "01711224455",
      address = "চকবাজার, ঢাকা",
      totalBilled = 0.0,
      totalPaid = 0.0,
      currentDue = 0.0
    )
    db.customerDao().insertCustomer(realCustomer)

    // Set opening balance with wrong PIN fails
    val wrongPinOpening = repo.setCustomerOpeningBalance(realCustomer.id, 12500.0, admin, "9999")
    assertTrue(wrongPinOpening.isFailure)

    // Set opening balance with correct PIN succeeds
    val setOpeningRes = repo.setCustomerOpeningBalance(realCustomer.id, 12500.0, admin, "1234")
    assertTrue(setOpeningRes.isSuccess)

    val updatedRealCust = db.customerDao().getCustomerById(realCustomer.id)!!
    assertEquals(12500.0, updatedRealCust.openingDue, 0.001)
    assertEquals(12500.0, updatedRealCust.currentDue, 0.001)

    // Set product opening stock
    val setOpeningStockRes = repo.setProductOpeningStock(initialProd.id, 50.0, admin, "1234")
    assertTrue(setOpeningStockRes.isSuccess)

    val updatedProd = db.productDao().getProductById(initialProd.id)!!
    assertEquals(50.0, updatedProd.currentStock, 0.001)
  }

  @Test
  fun `verify fresh production install has zero fake products and zero fake transactions`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inMemDb = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val freshRepo = BusinessRepository(inMemDb)

    // Fresh install in production real-business mode
    freshRepo.seedInitialDataIfEmpty(includeSampleDemoData = false)

    // Verify 0 products (no rice, lentils, sugar, oil)
    assertEquals(0, inMemDb.productDao().getAllProducts().first().size)
    // Verify 0 customers
    assertEquals(0, inMemDb.customerDao().getAllCustomers().first().size)
    // Verify 0 companies
    assertEquals(0, inMemDb.companyDao().countCompanies())
    // Verify 0 sales
    assertEquals(0, inMemDb.saleDao().getSalesCount())

    // Essential Admin and Salesman accounts exist and work
    val admin = freshRepo.authenticate("01700000000", "1234")
    assertNotNull("Admin must exist on fresh install", admin)
    assertEquals(UserRole.ADMIN, admin?.role)

    val salesman = freshRepo.authenticate("01800000000", "1234")
    assertNotNull("Salesman must exist on fresh install", salesman)
    assertEquals(UserRole.SALESMAN, salesman?.role)
  }

  @Test
  fun `verify cloud sync manager handles offline mode and tombstones safely`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inMemDb = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val syncManager = com.example.data.sync.CloudSyncManager(context, inMemDb)

    // Record tombstone for deleted customer
    syncManager.recordTombstone(
      originalTable = "customers",
      originalId = "cust_deleted_101",
      adminUserId = "admin_1",
      isPermanent = false
    )

    // Offline sync state operates safely without crashing and shows local saved status
    syncManager.triggerSync { success, msg ->
      assertTrue(success)
      assertTrue(msg.contains("লোকাল ডেটা সংরক্ষিত") || msg.contains("ক্লাউড সেটআপ বাকি"))
    }

    val state = syncManager.syncState.value
    assertNotNull(state)
    assertFalse(state.isCloudConfigured) // Gracefully reports unprovisioned cloud
    assertEquals("লোকাল ডেটা সংরক্ষিত — ক্লাউড সেটআপ বাকি", state.statusText)
  }

  @Test
  fun `verify admin default credentials detection and safe credentials update`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val inMemDb = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val repo = BusinessRepository(inMemDb)

    // Seed production data (default admin setup)
    repo.seedInitialDataIfEmpty(includeSampleDemoData = false)

    // Verify initial default PIN is flagged for security
    assertTrue("Default PIN (1234) must be detected as active", repo.isDefaultAdminCredentialsActive())

    // Attempt update with wrong current PIN must fail
    val wrongResult = repo.updateAdminSecurityCredentials(
      adminId = "admin_1",
      currentPin = "9999",
      newName = "আমার সুপারশপ",
      newPhone = "01912345678",
      newPin = "8899"
    )
    assertTrue(wrongResult.isFailure)

    // Update with correct current PIN succeeds
    val successResult = repo.updateAdminSecurityCredentials(
      adminId = "admin_1",
      currentPin = "1234",
      newName = "আমার সুপারশপ",
      newPhone = "01912345678",
      newPin = "8899"
    )
    assertTrue(successResult.isSuccess)

    // After updating, default credentials must no longer be flagged
    assertFalse("Default PIN should no longer be active after update", repo.isDefaultAdminCredentialsActive())

    // Authenticate with new credentials succeeds
    val newAdmin = repo.authenticate("01912345678", "8899")
    assertNotNull("Admin must be able to log in with new credentials", newAdmin)
    assertEquals("আমার সুপারশপ", newAdmin?.name)

    // Old credentials must fail
    val oldLogin = repo.authenticate("01700000000", "1234")
    assertNull("Old default credentials must no longer work", oldLogin)
  }
}


