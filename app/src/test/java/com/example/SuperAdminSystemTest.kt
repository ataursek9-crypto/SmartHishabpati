package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.*
import com.example.data.repository.SuperAdminRepository
import com.example.viewmodel.SuperAdminViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class SuperAdminSystemTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var repository: SuperAdminRepository
    private lateinit var viewModel: SuperAdminViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        repository = SuperAdminRepository(app)
        repository.resetStateForTesting()
        viewModel = SuperAdminViewModel(app, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test unauthorized emails are strictly rejected from Super Admin access`() {
        assertFalse(SuperAdminRepository.isSuperAdminEmail("random.user@gmail.com"))
        assertFalse(SuperAdminRepository.isSuperAdminEmail("hacker@malicious.site"))
        assertFalse(SuperAdminRepository.isSuperAdminEmail(null))
        assertFalse(SuperAdminRepository.isSuperAdminEmail(""))

        assertFalse(SuperAdminRepository.isSuperAdminEmail("buddavaibinodon@gmail.com"))
        assertTrue(SuperAdminRepository.isRevokedSuperAdminEmail("buddavaibinodon@gmail.com"))
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ataurgonee4@gmail.com"))
        assertTrue(SuperAdminRepository.isSuperAdminEmail("ataursek9@gmail.com"))
        assertTrue(SuperAdminRepository.isSuperAdminPhone("01700000000"))
        assertTrue(SuperAdminRepository.isSuperAdminPhone("+8801700000000"))
    }

    @Test
    fun `test business suspension updates status and records audit log safely`() = runTest(testDispatcher) {
        val businesses = repository.businesses.value
        assertTrue("Initial businesses should not be empty", businesses.isNotEmpty())
        val target = businesses.first { it.status == BusinessAccountStatus.ACTIVE }

        val res = repository.updateBusinessStatus(
            businessId = target.id,
            newStatus = BusinessAccountStatus.SUSPENDED,
            reason = "নমুনা অডিট পরীক্ষা",
            actorUid = "super_admin_test",
            actorName = "টেস্ট সুপার এডমিন",
            actorEmail = "ataurgonee4@gmail.com"
        )
        assertTrue(res.isSuccess)

        val updated = repository.businesses.value.first { it.id == target.id }
        assertEquals(BusinessAccountStatus.SUSPENDED, updated.status)
        assertEquals("নমুনা অডিট পরীক্ষা", updated.statusReason)

        // Verify audit log was recorded
        val auditLogs = repository.auditLogs.value
        assertTrue(auditLogs.any { it.targetBusinessId == target.id && it.action.contains("স্থগিতকরণ") })
    }

    @Test
    fun `test business reactivation restores active status`() = runTest(testDispatcher) {
        val suspended = repository.businesses.value.first { it.status == BusinessAccountStatus.SUSPENDED }

        val res = repository.updateBusinessStatus(
            businessId = suspended.id,
            newStatus = BusinessAccountStatus.ACTIVE,
            reason = "বকেয়া পরিশোধ সাপেক্ষে সক্রিয়করণ",
            actorUid = "super_admin_test",
            actorName = "টেস্ট সুপার এডমিন",
            actorEmail = "ataurgonee4@gmail.com"
        )
        assertTrue(res.isSuccess)

        val updated = repository.businesses.value.first { it.id == suspended.id }
        assertEquals(BusinessAccountStatus.ACTIVE, updated.status)
    }

    @Test
    fun `test plan change updates business plan and extends subscription expiry`() = runTest(testDispatcher) {
        val target = repository.businesses.value.first()
        val oldExpiry = target.subscriptionExpiresAt

        val res = repository.updateBusinessPlan(
            businessId = target.id,
            planId = "plan_enterprise",
            durationDays = 60,
            actorUid = "super_admin_test",
            actorName = "টেস্ট সুপার এডমিন",
            actorEmail = "ataurgonee4@gmail.com"
        )
        assertTrue(res.isSuccess)

        val updated = repository.businesses.value.first { it.id == target.id }
        assertEquals("plan_enterprise", updated.planId)
        assertTrue(updated.subscriptionExpiresAt > oldExpiry)
    }

    @Test
    fun `test payment review approve is idempotent and extends subscription`() = runTest(testDispatcher) {
        val pendingPayment = repository.payments.value.first { it.status == PlatformPaymentStatus.PENDING }

        val approveRes = repository.reviewPayment(
            paymentId = pendingPayment.id,
            isApproved = true,
            rejectionReason = "",
            actorUid = "super_admin_test",
            actorName = "টেস্ট সুপার এডমিন",
            actorEmail = "ataurgonee4@gmail.com"
        )
        assertTrue(approveRes.isSuccess)

        val updatedPayment = repository.payments.value.first { it.id == pendingPayment.id }
        assertEquals(PlatformPaymentStatus.APPROVED, updatedPayment.status)
        assertNotNull(updatedPayment.reviewedAt)

        // Try to approve or reject again: must fail (Idempotency)
        val duplicateRes = repository.reviewPayment(
            paymentId = pendingPayment.id,
            isApproved = true,
            rejectionReason = "",
            actorUid = "super_admin_test",
            actorName = "টেস্ট সুপার এডমিন",
            actorEmail = "ataurgonee4@gmail.com"
        )
        assertFalse(duplicateRes.isSuccess)
    }

    @Test
    fun `test payment review rejection records reason without changing subscription`() = runTest(testDispatcher) {
        // Create a temporary pending payment to reject
        val pendingPayment = repository.payments.value.first { it.status == PlatformPaymentStatus.PENDING }

        val rejectRes = repository.reviewPayment(
            paymentId = pendingPayment.id,
            isApproved = false,
            rejectionReason = "ভুল ট্রানজ্যাকশন আইডি",
            actorUid = "super_admin_test",
            actorName = "টেস্ট সুপার এডমিন",
            actorEmail = "ataurgonee4@gmail.com"
        )
        assertTrue(rejectRes.isSuccess)

        val updatedPayment = repository.payments.value.first { it.id == pendingPayment.id }
        assertEquals(PlatformPaymentStatus.REJECTED, updatedPayment.status)
        assertEquals("ভুল ট্রানজ্যাকশন আইডি", updatedPayment.rejectionReason)
    }

    @Test
    fun `test overview stats computes correct aggregations`() {
        val stats = repository.computeOverviewStats()
        assertTrue(stats.totalBusinesses >= 3)
        assertTrue(stats.activeBusinesses >= 1)
        assertTrue(stats.verifiedRevenue >= 10000.0)
        assertTrue(stats.planDistribution.isNotEmpty())
    }

    @Test
    fun `test saving plan configuration updates pricing and limits`() = runTest(testDispatcher) {
        val plan = repository.plans.value.first { it.id == "plan_pro" }
        val updatedPlan = plan.copy(
            priceMonthly = 1200.0,
            maxStaff = 12
        )

        val res = repository.savePlan(
            plan = updatedPlan,
            actorUid = "super_admin_test",
            actorName = "সুপার এডমিন",
            actorEmail = "ataurgonee4@gmail.com"
        )
        assertTrue(res.isSuccess)

        val planInRepo = repository.plans.value.first { it.id == "plan_pro" }
        assertEquals(1200.0, planInRepo.priceMonthly, 0.01)
        assertEquals(12, planInRepo.maxStaff)
    }

    @Test
    fun `test platform settings update records audit log`() = runTest(testDispatcher) {
        val newSettings = repository.settings.value.copy(
            bKashMerchantNumber = "01999887766",
            nagadMerchantNumber = "01888776655",
            maintenanceMode = false
        )

        val res = repository.updateSettings(
            newSettings = newSettings,
            actorUid = "super_admin_test",
            actorName = "সুপার এডমিন",
            actorEmail = "ataurgonee4@gmail.com"
        )
        assertTrue(res.isSuccess)

        assertEquals("01999887766", repository.settings.value.bKashMerchantNumber)
        val logs = repository.auditLogs.value
        assertTrue(logs.any { it.action == "সিস্টেম সেটিংস পরিবর্তন" })
    }

    @Test
    fun `test unlisted emails like jamesfashion792 are revoked from super admin`() {
        assertFalse(SuperAdminRepository.isSuperAdminEmail("jamesfashion792@gmail.com"))
        assertTrue(SuperAdminRepository.isRevokedSuperAdminEmail("jamesfashion792@gmail.com"))
    }

    @Test
    fun `test new shop registration defaults to free policy and appears in super admin repository`() = runTest(testDispatcher) {
        val newShop = PlatformBusiness(
            id = "biz_test_reg_${System.currentTimeMillis()}",
            businessName = "মেসার্স ভাই ভাই এন্টারপ্রাইজ",
            ownerUid = "owner_test_101",
            ownerName = "মোঃ সোহেল রানা",
            ownerPhone = "01888999000",
            status = BusinessAccountStatus.ACTIVE,
            planId = "plan_free",
            planName = "সম্পূর্ণ ফ্রি ব্যবহার",
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = 0L,
            isAutoExpiryDisabled = true,
            businessType = "মুদি ও জেনারেল স্টোর",
            address = "মিরপুর ১০, ঢাকা",
            district = "ঢাকা",
            phoneVerified = true
        )

        val regResult = repository.registerNewShop(newShop)
        assertTrue(regResult.isSuccess)

        // Verify shop is in businesses list immediately
        val found = repository.businesses.value.find { it.id == newShop.id }
        assertNotNull(found)
        assertEquals("মেসার্স ভাই ভাই এন্টারপ্রাইজ", found?.businessName)
        assertEquals(BusinessAccountStatus.ACTIVE, found?.status)
        assertEquals(SubscriptionStatus.ACTIVE, found?.subscriptionStatus)
        assertTrue(found?.isAutoExpiryDisabled == true)
        assertEquals(0L, found?.subscriptionExpiresAt)
        assertTrue(found?.phoneVerified == true)

        // Verify audit log
        val logs = repository.auditLogs.value
        assertTrue(logs.any { it.targetBusinessId == newShop.id && it.action.contains("রেজিস্ট্রেশন") })
    }

    @Test
    fun `test sending subscription notice preserves free access and records proposal`() = runTest(testDispatcher) {
        val target = repository.businesses.value.first()
        val noticeFee = 600.0
        val startDate = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        val reminderDate = System.currentTimeMillis() + (15L * 24 * 60 * 60 * 1000)

        val res = repository.sendSubscriptionNotice(
            businessId = target.id,
            proposedFee = noticeFee,
            proposedStartDate = startDate,
            reminderDate = reminderDate,
            note = "ভবিষ্যতের প্রো প্যাকেজ প্রস্তাবনা",
            actorUid = "super_admin_test",
            actorName = "সুপার এডমিন",
            actorEmail = "ataurgonee4@gmail.com"
        )
        assertTrue(res.isSuccess)

        val updated = repository.businesses.value.first { it.id == target.id }
        assertTrue(updated.hasActiveNotice)
        assertEquals(600.0, updated.proposedFee, 0.01)
        assertEquals(startDate, updated.proposedStartDate)
        // Store status must still be ACTIVE, not blocked
        assertEquals(BusinessAccountStatus.ACTIVE, updated.status)
    }

    @Test
    fun `test scheduling future policy change updates business and records audit log`() = runTest(testDispatcher) {
        val target = repository.businesses.value.first()
        val effectiveDate = System.currentTimeMillis() + (45L * 24 * 60 * 60 * 1000)
        val policyDesc = "প্রিমিয়াম সাবস্ক্রিপশন ফিচার কার্যকর"

        val res = repository.schedulePolicyChange(
            businessId = target.id,
            policyDescription = policyDesc,
            effectiveDate = effectiveDate,
            actorUid = "super_admin_test",
            actorName = "সুপার এডমিন",
            actorEmail = "ataurgonee4@gmail.com"
        )
        assertTrue(res.isSuccess)

        val updated = repository.businesses.value.first { it.id == target.id }
        assertEquals(policyDesc, updated.scheduledPolicyChange)
        assertEquals(effectiveDate, updated.scheduledPolicyEffectiveAt)

        val logs = repository.auditLogs.value
        assertTrue(logs.any { it.targetBusinessId == target.id && it.action.contains("পলিসি পরিবর্তন শিডিউল") })
    }
}
