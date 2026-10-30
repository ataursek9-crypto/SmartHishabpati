package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.example.data.sync.CloudBusinessAuth
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * SuperAdminRepository coordinates platform-level business management, subscription plans,
 * payment verifications, and audit logging with strict multi-tenant isolation.
 */
class SuperAdminRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
) {
    companion object {
        private const val TAG = "SuperAdminRepo"

        @Volatile
        var disableFirebaseForTesting: Boolean = false

        // Explicitly authorized Super Admin Gmail accounts
        val AUTHORIZED_SUPER_ADMIN_EMAILS = setOf(
            "ataurgonee4@gmail.com",
            "ataursek9@gmail.com"
        )

        // Explicitly authorized Super Admin mobile phone numbers
        val AUTHORIZED_SUPER_ADMIN_PHONES = setOf(
            "01700000000",
            "+8801700000000"
        )

        // Revoked accounts - strictly stripped of platform Super Admin privileges
        val REVOKED_SUPER_ADMIN_EMAILS = setOf(
            "buddavaibinodon@gmail.com",
            "support@smartbiz.local",
            "gonee8666@gmail.com",
            "ataulgonee786@gmail.com",
            "ataurpos@gmail.com",
            "ataur50305@gmail.com",
            "a48268993@gmail.com",
            "jamesfashion792@gmail.com"
        )

        fun isSuperAdminEmail(email: String?): Boolean {
            if (email.isNullOrBlank()) return false
            val clean = email.trim().lowercase()
            if (REVOKED_SUPER_ADMIN_EMAILS.contains(clean)) return false
            return AUTHORIZED_SUPER_ADMIN_EMAILS.contains(clean)
        }

        fun isRevokedSuperAdminEmail(email: String?): Boolean {
            if (email.isNullOrBlank()) return false
            return REVOKED_SUPER_ADMIN_EMAILS.contains(email.trim().lowercase())
        }

        fun isSuperAdminPhone(phone: String?): Boolean {
            if (phone.isNullOrBlank()) return false
            val clean = phone.replace(Regex("[^0-9+]"), "").trim()
            val normalized = when {
                clean.startsWith("+880") -> "0" + clean.substring(4)
                clean.startsWith("880") -> "0" + clean.substring(3)
                else -> clean
            }
            return AUTHORIZED_SUPER_ADMIN_PHONES.contains(normalized) || AUTHORIZED_SUPER_ADMIN_PHONES.contains(clean)
        }

        fun isSuperAdminIdentity(email: String?, phone: String? = null): Boolean {
            if (isRevokedSuperAdminEmail(email)) return false
            return isSuperAdminEmail(email) || isSuperAdminPhone(phone)
        }

        fun getDefaultPlans(): List<SubscriptionPlan> {
            return listOf(
                SubscriptionPlan(
                    id = "plan_free",
                    code = "FREE",
                    name = "Free Perpetual",
                    nameBn = "সম্পূর্ণ ফ্রি ব্যবহার",
                    descriptionBn = "নতুন ব্যবসার জন্য আজীবন বিনামূল্যে সকল মৌলিক ফিচার।",
                    priceMonthly = 0.0,
                    priceYearly = 0.0,
                    maxStaff = 2,
                    maxProducts = 100,
                    features = listOf(
                        "বর্তমান পলিসি: সম্পূর্ণ ফ্রি ব্যবহার",
                        "দৈনিক বেচাকেনা ও রসিদ তৈরি",
                        "কাস্টমার বাকি হিসাব খাতা",
                        "মৌলিক লাভ-ক্ষতি প্রতিবেদন",
                        "১ জন অ্যাডমিন ও ১ জন সেলসম্যান",
                        "কোনো জোরপূর্বক মেয়াদোত্তীর্ণ বা অটো-পেমেন্ট নেই"
                    ),
                    isPopular = false,
                    isActive = true
                ),
                SubscriptionPlan(
                    id = "plan_basic",
                    code = "BASIC",
                    name = "Basic Shop",
                    nameBn = "বেসিক ব্যবসা",
                    descriptionBn = "ছোট ও মাঝারি দোকান পরিচালনার জন্য আদর্শ।",
                    priceMonthly = 500.0,
                    priceYearly = 5000.0,
                    maxStaff = 3,
                    maxProducts = 500,
                    features = listOf(
                        "বেসিক সকল ফিচার",
                        "রিসাইকেল বিন ও ডিলিট হিস্ট্রি",
                        "কাস্টমার এসএমএস নোটিফিকেশন",
                        "পিডিএফ রসিদ ও শেয়ারিং",
                        "সর্বোচ্চ ৩ জন কর্মী"
                    ),
                    isPopular = false,
                    isActive = true
                ),
                SubscriptionPlan(
                    id = "plan_pro",
                    code = "PRO",
                    name = "Pro Store",
                    nameBn = "প্রো স্টোর",
                    descriptionBn = "দ্রুত বর্ধনশীল শপ ও পাইকারি ব্যবসার জন্য সেরা পছন্দ।",
                    priceMonthly = 1000.0,
                    priceYearly = 10000.0,
                    maxStaff = 8,
                    maxProducts = 5000,
                    features = listOf(
                        "ক্লাউড ব্যাকআপ ও রিয়েলটাইম সিঙ্ক",
                        "কোম্পানি ক্রয় ও বাকি ব্যবস্থাপনা",
                        "স্মার্ট রিমাইন্ডার ও বকেয়া অ্যালার্ট",
                        "স্টক ভেরিফিকেশন ও অডিট লগ",
                        "সর্বোচ্চ ৮ জন কর্মী"
                    ),
                    isPopular = true,
                    isActive = true
                ),
                SubscriptionPlan(
                    id = "plan_enterprise",
                    code = "ENTERPRISE",
                    name = "Enterprise VIP",
                    nameBn = "এন্টারপ্রাইজ / আনলিমিটেড",
                    descriptionBn = "বৃহৎ শপিং মল ও চেইন শপের জন্য পূর্ণাঙ্গ সমাধান।",
                    priceMonthly = 2000.0,
                    priceYearly = 20000.0,
                    maxStaff = 100,
                    maxProducts = 100000,
                    features = listOf(
                        "সকল আনলিমিটেড ফিচার",
                        "AI বিজনেস অ্যাসিস্ট্যান্ট পূর্ণ অ্যাক্সেস",
                        "অগ্রাধিকারমূলক ভিআইপি সাপোর্ট",
                        "কাস্টমাইজড রিপোর্ট ও অটো সিঙ্ক"
                    ),
                    isPopular = false,
                    isActive = true
                )
            )
        }

        private val _sharedBusinesses = MutableStateFlow<List<PlatformBusiness>>(emptyList())
        private val _sharedPlans = MutableStateFlow<List<SubscriptionPlan>>(getDefaultPlans())
        private val _sharedPayments = MutableStateFlow<List<PlatformPayment>>(emptyList())
        private val _sharedAuditLogs = MutableStateFlow<List<PlatformAuditLog>>(emptyList())
        private val _sharedSettings = MutableStateFlow(PlatformSystemSettings())

        @Volatile
        private var INSTANCE: SuperAdminRepository? = null

        fun getInstance(context: Context): SuperAdminRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SuperAdminRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _businesses get() = _sharedBusinesses
    val businesses: StateFlow<List<PlatformBusiness>> = _sharedBusinesses.asStateFlow()

    private val _plans get() = _sharedPlans
    val plans: StateFlow<List<SubscriptionPlan>> = _sharedPlans.asStateFlow()

    private val _payments get() = _sharedPayments
    val payments: StateFlow<List<PlatformPayment>> = _sharedPayments.asStateFlow()

    private val _auditLogs get() = _sharedAuditLogs
    val auditLogs: StateFlow<List<PlatformAuditLog>> = _sharedAuditLogs.asStateFlow()

    private val _settings get() = _sharedSettings
    val settings: StateFlow<PlatformSystemSettings> = _sharedSettings.asStateFlow()

    private fun serializeBusiness(b: PlatformBusiness): org.json.JSONObject {
        val obj = org.json.JSONObject()
        obj.put("id", b.id)
        obj.put("businessName", b.businessName)
        obj.put("ownerUid", b.ownerUid)
        obj.put("ownerName", b.ownerName)
        obj.put("ownerEmail", b.ownerEmail)
        obj.put("ownerPhone", b.ownerPhone)
        obj.put("status", b.status.name)
        obj.put("statusReason", b.statusReason)
        obj.put("planId", b.planId)
        obj.put("planName", b.planName)
        obj.put("subscriptionStatus", b.subscriptionStatus.name)
        obj.put("subscriptionExpiresAt", b.subscriptionExpiresAt)
        obj.put("staffCount", b.staffCount)
        obj.put("maxStaffAllowed", b.maxStaffAllowed)
        obj.put("createdAt", b.createdAt)
        obj.put("firstUseAt", b.firstUseAt)
        obj.put("lastActiveAt", b.lastActiveAt)
        obj.put("notes", b.notes)
        obj.put("businessType", b.businessType)
        obj.put("address", b.address)
        obj.put("district", b.district)
        obj.put("isAutoExpiryDisabled", b.isAutoExpiryDisabled)
        obj.put("activeDaysCount", b.activeDaysCount)
        obj.put("phoneVerified", b.phoneVerified)
        obj.put("proposedFee", b.proposedFee)
        if (b.proposedStartDate != null) obj.put("proposedStartDate", b.proposedStartDate)
        if (b.noticeReminderDate != null) obj.put("noticeReminderDate", b.noticeReminderDate)
        obj.put("subscriptionNoticeNote", b.subscriptionNoticeNote)
        obj.put("hasActiveNotice", b.hasActiveNotice)
        obj.put("scheduledPolicyChange", b.scheduledPolicyChange)
        if (b.scheduledPolicyEffectiveAt != null) obj.put("scheduledPolicyEffectiveAt", b.scheduledPolicyEffectiveAt)
        return obj
    }

    private fun deserializeBusiness(obj: org.json.JSONObject): PlatformBusiness {
        return PlatformBusiness(
            id = obj.optString("id", ""),
            businessName = obj.optString("businessName", ""),
            ownerUid = obj.optString("ownerUid", ""),
            ownerName = obj.optString("ownerName", ""),
            ownerEmail = obj.optString("ownerEmail", ""),
            ownerPhone = obj.optString("ownerPhone", ""),
            status = try { BusinessAccountStatus.valueOf(obj.optString("status", BusinessAccountStatus.ACTIVE.name)) } catch (_: Exception) { BusinessAccountStatus.ACTIVE },
            statusReason = obj.optString("statusReason", ""),
            planId = obj.optString("planId", "plan_free"),
            planName = obj.optString("planName", "সম্পূর্ণ ফ্রি ব্যবহার"),
            subscriptionStatus = try { SubscriptionStatus.valueOf(obj.optString("subscriptionStatus", SubscriptionStatus.ACTIVE.name)) } catch (_: Exception) { SubscriptionStatus.ACTIVE },
            subscriptionExpiresAt = obj.optLong("subscriptionExpiresAt", 0L),
            staffCount = obj.optInt("staffCount", 1),
            maxStaffAllowed = obj.optInt("maxStaffAllowed", 2),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            firstUseAt = obj.optLong("firstUseAt", obj.optLong("createdAt", System.currentTimeMillis())),
            lastActiveAt = obj.optLong("lastActiveAt", System.currentTimeMillis()),
            notes = obj.optString("notes", ""),
            businessType = obj.optString("businessType", "সাধারণ ব্যবসা"),
            address = obj.optString("address", ""),
            district = obj.optString("district", ""),
            isAutoExpiryDisabled = obj.optBoolean("isAutoExpiryDisabled", true),
            activeDaysCount = obj.optInt("activeDaysCount", 1),
            phoneVerified = obj.optBoolean("phoneVerified", false),
            proposedFee = obj.optDouble("proposedFee", 0.0),
            proposedStartDate = if (obj.has("proposedStartDate") && !obj.isNull("proposedStartDate")) obj.optLong("proposedStartDate") else null,
            noticeReminderDate = if (obj.has("noticeReminderDate") && !obj.isNull("noticeReminderDate")) obj.optLong("noticeReminderDate") else null,
            subscriptionNoticeNote = obj.optString("subscriptionNoticeNote", ""),
            hasActiveNotice = obj.optBoolean("hasActiveNotice", false),
            scheduledPolicyChange = obj.optString("scheduledPolicyChange", ""),
            scheduledPolicyEffectiveAt = if (obj.has("scheduledPolicyEffectiveAt") && !obj.isNull("scheduledPolicyEffectiveAt")) obj.optLong("scheduledPolicyEffectiveAt") else null
        )
    }

    private fun saveBusinessesLocally(list: List<PlatformBusiness>) {
        try {
            val sp = context.getSharedPreferences("super_admin_businesses_store", Context.MODE_PRIVATE)
            val arr = org.json.JSONArray()
            list.forEach { b -> arr.put(serializeBusiness(b)) }
            sp.edit().putString("businesses_json", arr.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "saveBusinessesLocally error: ${e.message}")
        }
    }

    private fun loadBusinessesLocally(): List<PlatformBusiness> {
        return try {
            val sp = context.getSharedPreferences("super_admin_businesses_store", Context.MODE_PRIVATE)
            val jsonStr = sp.getString("businesses_json", null) ?: return emptyList()
            val arr = org.json.JSONArray(jsonStr)
            val result = mutableListOf<PlatformBusiness>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(deserializeBusiness(obj))
            }
            result
        } catch (e: Exception) {
            Log.w(TAG, "loadBusinessesLocally error: ${e.message}")
            emptyList()
        }
    }

    init {
        // Initialize in-memory seed data for offline & initial preview
        seedInitialPlatformData()
        // If Firebase is available, listen to remote collections
        initRemoteListeners()
    }

    fun isFirebaseConfigured(): Boolean {
        if (disableFirebaseForTesting) return false
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun isUserSignedIn(): Boolean {
        return try {
            FirebaseAuth.getInstance().currentUser != null
        } catch (_: Exception) {
            false
        }
    }

    private fun seedInitialPlatformData() {
        if (_businesses.value.isNotEmpty()) return

        val persisted = loadBusinessesLocally()
        if (persisted.isNotEmpty()) {
            _businesses.value = persisted
            return
        }

        val seedBusinesses = listOf(
            PlatformBusiness(
                id = "biz_smart_store_1",
                businessName = "স্মার্ট জেনারেল স্টোর",
                ownerUid = "admin_1",
                ownerName = "মোঃ আতাউর রহমান",
                ownerEmail = "ataurgonee4@gmail.com",
                ownerPhone = "01700000000",
                status = BusinessAccountStatus.ACTIVE,
                planId = "plan_pro",
                planName = "প্রো স্টোর",
                subscriptionStatus = SubscriptionStatus.ACTIVE,
                subscriptionExpiresAt = System.currentTimeMillis() + (45L * 24 * 60 * 60 * 1000),
                staffCount = 3,
                maxStaffAllowed = 8,
                notes = "প্রাথমিক প্রধান ব্যবসা প্রতিষ্ঠান"
            ),
            PlatformBusiness(
                id = "biz_fashion_house_2",
                businessName = "আল-নূর ফ্যাশন হাউস",
                ownerUid = "owner_usr_2",
                ownerName = "মাহমুদুল হাসান",
                ownerEmail = "alnoor.fashion@gmail.com",
                ownerPhone = "01911223344",
                status = BusinessAccountStatus.ACTIVE,
                planId = "plan_basic",
                planName = "বেসিক ব্যবসা",
                subscriptionStatus = SubscriptionStatus.ACTIVE,
                subscriptionExpiresAt = System.currentTimeMillis() + (15L * 24 * 60 * 60 * 1000),
                staffCount = 2,
                maxStaffAllowed = 3,
                notes = "রেডিমেড পোশাকের পাইকারি দোকান"
            ),
            PlatformBusiness(
                id = "biz_dhaka_traders_3",
                businessName = "ঢাকা পাইকারি ট্রেডার্স",
                ownerUid = "owner_usr_3",
                ownerName = "শামীম রেজা",
                ownerEmail = "dhaka.traders99@gmail.com",
                ownerPhone = "01688776655",
                status = BusinessAccountStatus.SUSPENDED,
                statusReason = "সাবস্ক্রিপশন ফি বকেয়া ও নীতি লঙ্ঘনের অভিযোগ পর্যালোচনাধীন",
                planId = "plan_basic",
                planName = "বেসিক ব্যবসা",
                subscriptionStatus = SubscriptionStatus.EXPIRED,
                subscriptionExpiresAt = System.currentTimeMillis() - (5L * 24 * 60 * 60 * 1000),
                staffCount = 2,
                maxStaffAllowed = 3,
                notes = "সাময়িক স্থগিত অ্যাকাউন্ট"
            )
        )
        _businesses.value = seedBusinesses

        val seedPayments = listOf(
            PlatformPayment(
                id = "pay_bkash_881920",
                businessId = "biz_fashion_house_2",
                businessName = "আল-নূর ফ্যাশন হাউস",
                amount = 1000.0,
                currency = "BDT",
                paymentMethod = PlatformPaymentMethod.BKASH,
                transactionReference = "9K8A77L91B",
                senderNumber = "01911223344",
                status = PlatformPaymentStatus.PENDING,
                targetPlanId = "plan_pro",
                durationMonths = 1,
                submittedAt = System.currentTimeMillis() - (2L * 60 * 60 * 1000),
                notes = "প্রো প্যাকেজে আপগ্রেড করার জন্য bKash সেন্ড মানি"
            ),
            PlatformPayment(
                id = "pay_nagad_772110",
                businessId = "biz_smart_store_1",
                businessName = "স্মার্ট জেনারেল স্টোর",
                amount = 10000.0,
                currency = "BDT",
                paymentMethod = PlatformPaymentMethod.NAGAD,
                transactionReference = "NGD8823190A",
                senderNumber = "01700000000",
                status = PlatformPaymentStatus.APPROVED,
                targetPlanId = "plan_pro",
                durationMonths = 12,
                submittedAt = System.currentTimeMillis() - (5L * 24 * 60 * 60 * 1000),
                reviewedAt = System.currentTimeMillis() - (4L * 24 * 60 * 60 * 1000),
                reviewedByUid = "super_admin_system",
                reviewedByName = "সুপার এডমিন",
                notes = "বার্ষিক সাবস্ক্রিপশন পরিশোধিত"
            )
        )
        _payments.value = seedPayments

        val seedLogs = listOf(
            PlatformAuditLog(
                id = "log_init_1",
                actorUid = "super_admin_system",
                actorName = "সিস্টেম",
                actorEmail = "support@smartbiz.local",
                action = "সিস্টেম ইনিশিয়ালাইজেশন",
                targetBusinessId = "GLOBAL",
                targetBusinessName = "প্ল্যাটফর্ম",
                details = "সুপার এডমিন কন্ট্রোল ও সাবস্ক্রিপশন পলিসি সক্রিয় করা হয়েছে।"
            ),
            PlatformAuditLog(
                id = "log_pay_approve_2",
                actorUid = "super_admin_system",
                actorName = "সুপার এডমিন",
                actorEmail = "support@smartbiz.local",
                action = "পেমেন্ট অনুমোদন ও সাবস্ক্রিপশন নবায়ন",
                targetBusinessId = "biz_smart_store_1",
                targetBusinessName = "স্মার্ট জেনারেল স্টোর",
                details = "১০,০০০ টাকা নাগাদ পেমেন্ট (Trx: NGD8823190A) অনুমোদিত। মেয়াদ ১২ মাস বৃদ্ধি করা হয়েছে।"
            )
        )
        _auditLogs.value = seedLogs
    }

    private var businessesListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    @androidx.annotation.VisibleForTesting
    fun resetStateForTesting() {
        disableFirebaseForTesting = true
        businessesListenerRegistration?.remove()
        businessesListenerRegistration = null
        _sharedBusinesses.value = emptyList()
        _sharedPayments.value = emptyList()
        _sharedAuditLogs.value = emptyList()
        _sharedPlans.value = getDefaultPlans()
        try {
            context.getSharedPreferences("super_admin_businesses_store", Context.MODE_PRIVATE).edit().clear().commit()
        } catch (_: Exception) {}
        seedInitialPlatformData()
    }

    fun parseBusinessDoc(doc: com.google.firebase.firestore.DocumentSnapshot): PlatformBusiness? {
        return try {
            val id = doc.id
            val name = doc.getString("businessName")
                ?: doc.getString("name")
                ?: doc.getString("shopName")
                ?: "ব্যবসা #$id"
            val ownerUid = doc.getString("ownerUid") ?: doc.getString("uid") ?: id
            val ownerName = doc.getString("ownerName") ?: doc.getString("name") ?: "অ্যাডমিন"
            val ownerEmail = doc.getString("ownerEmail") ?: doc.getString("email") ?: ""
            val ownerPhone = doc.getString("phone") ?: doc.getString("ownerPhone") ?: ""
            val statusStr = doc.getString("status") ?: "ACTIVE"
            val status = try { BusinessAccountStatus.valueOf(statusStr) } catch (_: Exception) { BusinessAccountStatus.ACTIVE }
            val planId = doc.getString("planId") ?: "plan_free"
            val planName = doc.getString("planName") ?: "সম্পূর্ণ ফ্রি ব্যবহার"
            val subStatusStr = doc.getString("subscriptionStatus") ?: "ACTIVE"
            val subStatus = try { SubscriptionStatus.valueOf(subStatusStr) } catch (_: Exception) { SubscriptionStatus.ACTIVE }

            val expiresAt = (doc.get("subscriptionExpiresAt") as? Number)?.toLong()
                ?: (doc.get("subscriptionExpiresAt") as? com.google.firebase.Timestamp)?.toDate()?.time
                ?: 0L
            val staffCount = (doc.get("staffCount") as? Number)?.toInt() ?: 1
            val maxStaff = (doc.get("maxStaffAllowed") as? Number)?.toInt() ?: 2
            val createdAt = (doc.get("createdAt") as? Number)?.toLong()
                ?: (doc.get("createdAt") as? com.google.firebase.Timestamp)?.toDate()?.time
                ?: System.currentTimeMillis()
            val lastActiveAt = (doc.get("lastActiveAt") as? Number)?.toLong()
                ?: (doc.get("lastActiveAt") as? com.google.firebase.Timestamp)?.toDate()?.time
                ?: System.currentTimeMillis()
            val businessType = doc.getString("businessType") ?: "সাধারণ ব্যবসা"
            val address = doc.getString("address") ?: ""
            val district = doc.getString("district") ?: ""
            val isAutoExpiryDisabled = doc.getBoolean("isAutoExpiryDisabled") ?: true
            val activeDaysCount = (doc.get("activeDaysCount") as? Number)?.toInt() ?: 1
            val proposedFee = (doc.get("proposedFee") as? Number)?.toDouble() ?: 0.0
            val proposedStartDate = (doc.get("proposedStartDate") as? Number)?.toLong()
                ?: (doc.get("proposedStartDate") as? com.google.firebase.Timestamp)?.toDate()?.time
            val noticeReminderDate = (doc.get("noticeReminderDate") as? Number)?.toLong()
                ?: (doc.get("noticeReminderDate") as? com.google.firebase.Timestamp)?.toDate()?.time
            val subscriptionNoticeNote = doc.getString("subscriptionNoticeNote") ?: ""
            val hasActiveNotice = doc.getBoolean("hasActiveNotice") ?: (proposedFee > 0.0)
            val scheduledPolicyChange = doc.getString("scheduledPolicyChange") ?: ""
            val scheduledPolicyEffectiveAt = (doc.get("scheduledPolicyEffectiveAt") as? Number)?.toLong()
                ?: (doc.get("scheduledPolicyEffectiveAt") as? com.google.firebase.Timestamp)?.toDate()?.time

            PlatformBusiness(
                id = id,
                businessName = name,
                ownerUid = ownerUid,
                ownerName = ownerName,
                ownerEmail = ownerEmail,
                ownerPhone = ownerPhone,
                status = status,
                statusReason = doc.getString("statusReason") ?: "",
                planId = planId,
                planName = planName,
                subscriptionStatus = subStatus,
                subscriptionExpiresAt = expiresAt,
                staffCount = staffCount,
                maxStaffAllowed = maxStaff,
                createdAt = createdAt,
                lastActiveAt = lastActiveAt,
                notes = doc.getString("notes") ?: "",
                businessType = businessType,
                address = address,
                district = district,
                isAutoExpiryDisabled = isAutoExpiryDisabled,
                activeDaysCount = activeDaysCount,
                phoneVerified = doc.getBoolean("phoneVerified") ?: false,
                proposedFee = proposedFee,
                proposedStartDate = proposedStartDate,
                noticeReminderDate = noticeReminderDate,
                subscriptionNoticeNote = subscriptionNoticeNote,
                hasActiveNotice = hasActiveNotice,
                scheduledPolicyChange = scheduledPolicyChange,
                scheduledPolicyEffectiveAt = scheduledPolicyEffectiveAt
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseBusinessDoc error on doc ${doc.id}: ${e.message}")
            null
        }
    }

    /**
     * Actively fetches all registered shops from Cloud Firestore backend.
     * Merges remote businesses with existing and updates local persistence.
     * Accessible exclusively to authorized Super Admin identities (ataurgonee4@gmail.com, ataursek9@gmail.com, 01700000000).
     */
    suspend fun refreshBusinessesFromRemote(): Result<List<PlatformBusiness>> {
        if (!isFirebaseConfigured()) {
            return Result.success(_businesses.value)
        }
        val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        if (authUser == null) {
            val msg = "ফায়ারবেস অথেনটিকেশন অনুপস্থিত: ক্লাউড থেকে সকল দোকানের তথ্য দেখতে গুগল সাইন-ইন বা ফোন ওটিপি লগইন সম্পন্ন করুন।"
            Log.w(TAG, "refreshBusinessesFromRemote: $msg")
            return Result.failure(IllegalStateException("NO_FIREBASE_AUTH: $msg"))
        }
        val currentEmail = authUser.email?.trim()?.lowercase() ?: ""
        val currentPhone = authUser.phoneNumber?.trim() ?: ""
        val isAuthorized = isSuperAdminIdentity(currentEmail, currentPhone)
        if (!isAuthorized) {
            val identifier = currentEmail.ifBlank { currentPhone }
            val msg = "অননুমোদিত অ্যাকাউন্ট ($identifier): শুধুমাত্র অনুমোদিত সুপার এডমিন (ataurgonee4@gmail.com, ataursek9@gmail.com, 01700000000) থেকে প্ল্যাটফর্মের সকল দোকানের তথ্য দেখা সম্ভব।"
            Log.w(TAG, "refreshBusinessesFromRemote: $msg")
            return Result.failure(SecurityException("UNAUTHORIZED_SUPER_ADMIN: $msg"))
        }
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("businesses").get().await()
            val remoteList = snapshot.documents.mapNotNull { doc ->
                parseBusinessDoc(doc)
            }
            if (remoteList.isNotEmpty()) {
                val current = _businesses.value.toMutableList()
                // Clear initial static mock seeds when authoritative cloud records exist
                current.removeAll { it.id == "biz_fashion_house_2" || it.id == "biz_dhaka_traders_3" }
                remoteList.forEach { remote ->
                    val idx = current.indexOfFirst {
                        it.id == remote.id ||
                        (it.ownerUid.isNotBlank() && it.ownerUid == remote.ownerUid) ||
                        (it.ownerPhone.isNotBlank() && it.ownerPhone == remote.ownerPhone)
                    }
                    if (idx >= 0) {
                        current[idx] = remote
                    } else {
                        current.add(0, remote)
                    }
                }
                _businesses.value = current
                saveBusinessesLocally(current)
                startRemoteListeners()
                Result.success(current)
            } else {
                startRemoteListeners()
                Result.success(_businesses.value)
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshBusinessesFromRemote failed: ${e.message}")
            Result.failure(e)
        }
    }

    private var auditLogsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var paymentsListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    fun startRemoteListeners() {
        if (!isFirebaseConfigured()) return
        val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        val currentEmail = authUser?.email?.trim()?.lowercase() ?: ""
        val currentPhone = authUser?.phoneNumber?.trim() ?: ""
        if (authUser == null || !isSuperAdminIdentity(currentEmail, currentPhone)) return

        try {
            val firestore = FirebaseFirestore.getInstance()
            // 1. Remote Businesses Listener
            businessesListenerRegistration?.remove()
            businessesListenerRegistration = firestore.collection("businesses").addSnapshotListener { snapshot, err ->
                if (err != null) {
                    Log.w(TAG, "businessesListener live update notice: ${err.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val remoteList = snapshot.documents.mapNotNull { doc -> parseBusinessDoc(doc) }
                    if (remoteList.isNotEmpty()) {
                        val current = _businesses.value.toMutableList()
                        current.removeAll { it.id == "biz_fashion_house_2" || it.id == "biz_dhaka_traders_3" }
                        remoteList.forEach { remote ->
                            val idx = current.indexOfFirst {
                                it.id == remote.id || (it.ownerPhone == remote.ownerPhone && remote.ownerPhone.isNotBlank())
                            }
                            if (idx >= 0) {
                                current[idx] = remote
                            } else {
                                current.add(0, remote)
                            }
                        }
                        _businesses.value = current
                        saveBusinessesLocally(current)
                    }
                }
            }

            // 2. Remote Audit Logs Listener
            auditLogsListenerRegistration?.remove()
            auditLogsListenerRegistration = firestore.collection("platform_audit_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snapshot, err ->
                    if (err != null || snapshot == null) return@addSnapshotListener
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            PlatformAuditLog(
                                id = doc.id,
                                actorUid = doc.getString("actorUid") ?: "",
                                actorName = doc.getString("actorName") ?: "অ্যাডমিন",
                                actorEmail = doc.getString("actorEmail") ?: "",
                                action = doc.getString("action") ?: "",
                                targetBusinessId = doc.getString("targetBusinessId") ?: "",
                                targetBusinessName = doc.getString("targetBusinessName") ?: "",
                                details = doc.getString("details") ?: "",
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (list.isNotEmpty()) {
                        _auditLogs.value = list
                    }
                }

            // 3. Remote Payments Listener
            paymentsListenerRegistration?.remove()
            paymentsListenerRegistration = firestore.collection("platform_payments")
                .orderBy("submittedAt", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener { snapshot, err ->
                    if (err != null || snapshot == null) return@addSnapshotListener
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val methodStr = doc.getString("paymentMethod") ?: "BKASH"
                            val method = try { PlatformPaymentMethod.valueOf(methodStr) } catch (_: Exception) { PlatformPaymentMethod.BKASH }
                            val statusStr = doc.getString("status") ?: "PENDING"
                            val status = try { PlatformPaymentStatus.valueOf(statusStr) } catch (_: Exception) { PlatformPaymentStatus.PENDING }

                            PlatformPayment(
                                id = doc.id,
                                businessId = doc.getString("businessId") ?: "",
                                businessName = doc.getString("businessName") ?: "",
                                amount = doc.getDouble("amount") ?: 0.0,
                                currency = doc.getString("currency") ?: "BDT",
                                paymentMethod = method,
                                transactionReference = doc.getString("transactionReference") ?: "",
                                senderNumber = doc.getString("senderNumber") ?: "",
                                status = status,
                                targetPlanId = doc.getString("targetPlanId") ?: "plan_pro",
                                durationMonths = (doc.getLong("durationMonths") ?: 1).toInt(),
                                submittedAt = doc.getLong("submittedAt") ?: System.currentTimeMillis(),
                                reviewedAt = doc.getLong("reviewedAt"),
                                reviewedByUid = doc.getString("reviewedByUid"),
                                reviewedByName = doc.getString("reviewedByName"),
                                rejectionReason = doc.getString("rejectionReason") ?: "",
                                notes = doc.getString("notes") ?: ""
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (list.isNotEmpty()) {
                        _payments.value = list
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "startRemoteListeners exception: ${e.message}")
        }
    }

    private fun initRemoteListeners() {
        if (!isFirebaseConfigured()) return
        try {
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                val authUser = auth.currentUser
                val currentEmail = authUser?.email?.trim()?.lowercase() ?: ""
                val currentPhone = authUser?.phoneNumber?.trim() ?: ""
                if (authUser != null && isSuperAdminIdentity(currentEmail, currentPhone)) {
                    startRemoteListeners()
                }
            }
            // Also run once synchronously for current state if already authenticated
            val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
            val currentEmail = authUser?.email?.trim()?.lowercase() ?: ""
            val currentPhone = authUser?.phoneNumber?.trim() ?: ""
            if (authUser != null && isSuperAdminIdentity(currentEmail, currentPhone)) {
                startRemoteListeners()
            }
        } catch (e: Exception) {
            Log.w(TAG, "initRemoteListeners note: ${e.message}")
        }
    }

    /**
     * Updates business account status (ACTIVE, SUSPENDED, DEACTIVATED).
     * Strictly records an audit log. Never deletes business data upon suspension.
     */
    suspend fun updateBusinessStatus(
        businessId: String,
        newStatus: BusinessAccountStatus,
        reason: String,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ): Result<Unit> {
        if (!isSuperAdminEmail(actorEmail)) {
            return Result.failure(SecurityException("অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।"))
        }
        val currentList = _businesses.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == businessId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("ব্যবসা প্রতিষ্ঠানটি খুঁজে পাওয়া যায়নি!"))
        }

        val target = currentList[index]
        val updated = target.copy(
            status = newStatus,
            statusReason = reason,
            lastActiveAt = System.currentTimeMillis()
        )
        currentList[index] = updated
        _businesses.value = currentList
        saveBusinessesLocally(currentList)

        // Record Audit Log
        val actionTitle = when (newStatus) {
            BusinessAccountStatus.PENDING -> "ব্যবসা প্রতিষ্ঠান অপেক্ষমাণ তালিকায় স্থানান্তর"
            BusinessAccountStatus.ACTIVE -> "ব্যবসা প্রতিষ্ঠান অনুমোদন ও সক্রিয়করণ"
            BusinessAccountStatus.SUSPENDED -> "ব্যবসা প্রতিষ্ঠান সাময়িক স্থগিতকরণ"
            BusinessAccountStatus.DEACTIVATED -> "ব্যবসা প্রতিষ্ঠান নিষ্ক্রিয়করণ"
        }
        recordAuditLog(
            actorUid = actorUid,
            actorName = actorName,
            actorEmail = actorEmail,
            action = actionTitle,
            targetBusinessId = businessId,
            targetBusinessName = target.businessName,
            details = "স্ট্যাটাস পরিবর্তন: $newStatus। কারণ: ${reason.ifBlank { "সুপার এডমিন কর্তৃক সম্পাদিত" }}"
        )

        // Write to Firestore if configured
        if (isFirebaseConfigured()) {
            try {
                FirebaseFirestore.getInstance().collection("businesses").document(businessId)
                    .set(
                        mapOf(
                            "status" to newStatus.name,
                            "statusReason" to reason,
                            "lastActiveAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    ).await()
            } catch (e: Exception) {
                Log.w(TAG, "updateBusinessStatus remote note: ${e.message}")
            }
        }
        return Result.success(Unit)
    }

    /**
     * Updates a business's subscription plan and extends expiry.
     */
    suspend fun updateBusinessPlan(
        businessId: String,
        planId: String,
        durationDays: Int,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ): Result<Unit> {
        if (!isSuperAdminEmail(actorEmail)) {
            return Result.failure(SecurityException("অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।"))
        }
        val plan = _plans.value.find { it.id == planId }
            ?: return Result.failure(IllegalArgumentException("নির্বাচিত প্ল্যানটি পাওয়া যায়নি!"))

        val currentList = _businesses.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == businessId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("ব্যবসা প্রতিষ্ঠানটি খুঁজে পাওয়া যায়নি!"))
        }

        val target = currentList[index]
        val newExpiry = maxOf(System.currentTimeMillis(), target.subscriptionExpiresAt) + (durationDays.toLong() * 24 * 60 * 60 * 1000)

        val updated = target.copy(
            planId = plan.id,
            planName = plan.nameBn,
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = newExpiry,
            maxStaffAllowed = plan.maxStaff,
            lastActiveAt = System.currentTimeMillis()
        )
        currentList[index] = updated
        _businesses.value = currentList
        saveBusinessesLocally(currentList)

        recordAuditLog(
            actorUid = actorUid,
            actorName = actorName,
            actorEmail = actorEmail,
            action = "প্ল্যান আপগ্রেড / পরিবর্তন",
            targetBusinessId = businessId,
            targetBusinessName = target.businessName,
            details = "নতুন প্ল্যান: ${plan.nameBn}। মেয়াদ বৃদ্ধি: $durationDays দিন।"
        )

        if (isFirebaseConfigured()) {
            try {
                FirebaseFirestore.getInstance().collection("businesses").document(businessId)
                    .set(
                        mapOf(
                            "planId" to plan.id,
                            "planName" to plan.nameBn,
                            "subscriptionStatus" to SubscriptionStatus.ACTIVE.name,
                            "subscriptionExpiresAt" to newExpiry,
                            "maxStaffAllowed" to plan.maxStaff,
                            "lastActiveAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    ).await()
            } catch (e: Exception) {
                Log.w(TAG, "updateBusinessPlan remote note: ${e.message}")
            }
        }
        return Result.success(Unit)
    }

    /**
     * Reviews a manual bKash/Nagad subscription payment.
     * Approving immediately extends the subscription. Rejection records the reason.
     * Idempotent: already processed payments cannot be duplicated.
     */
    suspend fun reviewPayment(
        paymentId: String,
        isApproved: Boolean,
        rejectionReason: String,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ): Result<Unit> {
        if (!isSuperAdminEmail(actorEmail)) {
            return Result.failure(SecurityException("অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।"))
        }
        val currentPayments = _payments.value.toMutableList()
        val index = currentPayments.indexOfFirst { it.id == paymentId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("পেমেন্ট রেকর্ডটি পাওয়া যায়নি!"))
        }

        val payment = currentPayments[index]
        if (payment.status != PlatformPaymentStatus.PENDING) {
            return Result.failure(IllegalStateException("এই পেমেন্টটি ইতিমধ্যে পর্যালোচনা করা হয়েছে (${payment.status})!"))
        }

        val newStatus = if (isApproved) PlatformPaymentStatus.APPROVED else PlatformPaymentStatus.REJECTED
        val reviewedPayment = payment.copy(
            status = newStatus,
            reviewedAt = System.currentTimeMillis(),
            reviewedByUid = actorUid,
            reviewedByName = actorName,
            rejectionReason = rejectionReason
        )
        currentPayments[index] = reviewedPayment
        _payments.value = currentPayments

        if (isApproved) {
            // Extend target business subscription safely
            val durationDays = payment.durationMonths * 30
            updateBusinessPlan(
                businessId = payment.businessId,
                planId = payment.targetPlanId,
                durationDays = durationDays,
                actorUid = actorUid,
                actorName = actorName,
                actorEmail = actorEmail
            )
            recordAuditLog(
                actorUid = actorUid,
                actorName = actorName,
                actorEmail = actorEmail,
                action = "পেমেন্ট অনুমোদন ও সাবস্ক্রিপশন নবায়ন",
                targetBusinessId = payment.businessId,
                targetBusinessName = payment.businessName,
                details = "পেমেন্ট #${payment.id} (${payment.amount} ${payment.currency}, Trx: ${payment.transactionReference}) সফলভাবে অনুমোদিত হয়েছে।"
            )
        } else {
            recordAuditLog(
                actorUid = actorUid,
                actorName = actorName,
                actorEmail = actorEmail,
                action = "পেমেন্ট বাতিলকরণ",
                targetBusinessId = payment.businessId,
                targetBusinessName = payment.businessName,
                details = "পেমেন্ট #${payment.id} বাতিল করা হয়েছে। কারণ: ${rejectionReason.ifBlank { "সুপার এডমিন কর্তৃক যাচাইকরণে ত্রুটি" }}"
            )
        }

        if (isFirebaseConfigured()) {
            try {
                FirebaseFirestore.getInstance().collection("platform_payments").document(paymentId)
                    .set(
                        mapOf(
                            "status" to newStatus.name,
                            "reviewedAt" to System.currentTimeMillis(),
                            "reviewedByUid" to actorUid,
                            "reviewedByName" to actorName,
                            "rejectionReason" to rejectionReason
                        ),
                        SetOptions.merge()
                    ).await()
            } catch (e: Exception) {
                Log.w(TAG, "reviewPayment remote note: ${e.message}")
            }
        }
        return Result.success(Unit)
    }

    /**
     * Saves or updates a subscription plan definition.
     */
    suspend fun savePlan(
        plan: SubscriptionPlan,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ): Result<Unit> {
        if (!isSuperAdminEmail(actorEmail)) {
            return Result.failure(SecurityException("অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।"))
        }
        val currentPlans = _plans.value.toMutableList()
        val index = currentPlans.indexOfFirst { it.id == plan.id }
        if (index >= 0) {
            currentPlans[index] = plan
        } else {
            currentPlans.add(plan)
        }
        _plans.value = currentPlans

        recordAuditLog(
            actorUid = actorUid,
            actorName = actorName,
            actorEmail = actorEmail,
            action = "সাবস্ক্রিপশন প্ল্যান কনফিগারেশন",
            targetBusinessId = "GLOBAL",
            targetBusinessName = "প্ল্যাটফর্ম পলিসি",
            details = "প্ল্যান '${plan.nameBn}' আপডেট করা হয়েছে। মাসিক মূল্য: ৳${plan.priceMonthly}, কর্মী সীমা: ${plan.maxStaff}"
        )

        if (isFirebaseConfigured()) {
            try {
                FirebaseFirestore.getInstance().collection("platform_plans").document(plan.id)
                    .set(plan, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w(TAG, "savePlan remote note: ${e.message}")
            }
        }
        return Result.success(Unit)
    }

    /**
     * Records a secure audit log for Super Admin operations.
     */
    suspend fun recordAuditLog(
        actorUid: String,
        actorName: String,
        actorEmail: String,
        action: String,
        targetBusinessId: String,
        targetBusinessName: String,
        details: String
    ) {
        val log = PlatformAuditLog(
            id = "log_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            actorUid = actorUid,
            actorName = actorName,
            actorEmail = actorEmail,
            action = action,
            targetBusinessId = targetBusinessId,
            targetBusinessName = targetBusinessName,
            details = details,
            timestamp = System.currentTimeMillis()
        )
        val list = _auditLogs.value.toMutableList()
        list.add(0, log)
        _auditLogs.value = list

        if (isFirebaseConfigured()) {
            try {
                FirebaseFirestore.getInstance().collection("platform_audit_logs").document(log.id)
                    .set(log).await()
            } catch (e: Exception) {
                Log.w(TAG, "recordAuditLog remote note: ${e.message}")
            }
        }
    }

    /**
     * Updates platform system settings.
     */
    suspend fun updateSettings(
        newSettings: PlatformSystemSettings,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ): Result<Unit> {
        if (!isSuperAdminEmail(actorEmail)) {
            return Result.failure(SecurityException("অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।"))
        }
        _settings.value = newSettings
        recordAuditLog(
            actorUid = actorUid,
            actorName = actorName,
            actorEmail = actorEmail,
            action = "সিস্টেম সেটিংস পরিবর্তন",
            targetBusinessId = "GLOBAL",
            targetBusinessName = "প্ল্যাটফর্ম",
            details = "বিকাশ মার্চেন্ট: ${newSettings.bKashMerchantNumber}, নগদ: ${newSettings.nagadMerchantNumber}, মেইনটেন্যান্স: ${newSettings.maintenanceMode}"
        )

        if (isFirebaseConfigured()) {
            try {
                FirebaseFirestore.getInstance().collection("platform_settings").document("global_config")
                    .set(newSettings, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w(TAG, "updateSettings remote note: ${e.message}")
            }
        }
        return Result.success(Unit)
    }

    /**
     * Registers a new shop in the Super Admin repository and syncs to cloud if connected.
     * Guaranteed to appear in the Super Admin Console immediately.
     */
    suspend fun checkPhoneRegisteredRemote(phone: String): Boolean {
        if (!isFirebaseConfigured() || phone.isBlank()) return false
        val localPhone = CloudBusinessAuth.normalizePhone(phone)
        val e164Phone = CloudBusinessAuth.normalizeToE164(phone)
        val candidates = listOf(localPhone, e164Phone, phone.trim()).distinct().filter { it.isNotBlank() }
        val firestore = FirebaseFirestore.getInstance()
        for (candidate in candidates) {
            try {
                val exists = kotlinx.coroutines.withTimeoutOrNull(2500L) {
                    val doc = firestore.collection("registered_phones").document(candidate).get().await()
                    doc.exists()
                } ?: false
                if (exists) return true
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    // Document exists in registered_phones and is access-restricted by security rules
                    return true
                }
                Log.w(TAG, "checkPhoneRegisteredRemote check note: ${e.message}")
            }
        }
        return false
    }

    suspend fun findBusinessByOwner(ownerUid: String, ownerEmail: String = "", phone: String = ""): PlatformBusiness? {
        val cleanUid = ownerUid.trim()
        val cleanEmail = ownerEmail.trim().lowercase()
        val localPhone = if (phone.isNotBlank()) CloudBusinessAuth.normalizePhone(phone) else ""
        val e164Phone = if (phone.isNotBlank()) CloudBusinessAuth.normalizeToE164(phone) else ""
        val rawPhone = phone.trim()

        // 1. Check in-memory state
        val localMatch = _businesses.value.firstOrNull {
            (cleanUid.isNotBlank() && (it.id == cleanUid || it.ownerUid == cleanUid)) ||
            (cleanEmail.isNotBlank() && it.ownerEmail.equals(cleanEmail, ignoreCase = true)) ||
            (localPhone.isNotBlank() && CloudBusinessAuth.matchesPhone(it.ownerPhone, localPhone)) ||
            (rawPhone.isNotBlank() && (it.ownerPhone == rawPhone || it.ownerPhone.endsWith(rawPhone.takeLast(10))))
        }
        if (localMatch != null) return localMatch

        // 2. Query Firestore if configured and signed in
        if (isFirebaseConfigured() && isUserSignedIn()) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                if (cleanUid.isNotBlank()) {
                    // Direct lookup by document ID (supports UID-keyed business docs)
                    try {
                        val directDoc = firestore.collection("businesses").document(cleanUid).get().await()
                        if (directDoc.exists()) {
                            val parsed = parseBusinessDoc(directDoc)
                            if (parsed != null) return parsed
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "findBusinessByOwner direct doc lookup note: ${e.message}")
                    }

                    try {
                        val querySnap = firestore.collection("businesses")
                            .whereEqualTo("ownerUid", cleanUid)
                            .limit(1)
                            .get()
                            .await()
                        val foundDoc = querySnap.documents.firstOrNull()
                        if (foundDoc != null) {
                            val parsed = parseBusinessDoc(foundDoc)
                            if (parsed != null) return parsed
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "findBusinessByOwner query ownerUid note: ${e.message}")
                    }
                }
                if (cleanEmail.isNotBlank()) {
                    try {
                        val querySnapEmail = firestore.collection("businesses")
                            .whereEqualTo("ownerEmail", cleanEmail)
                            .limit(1)
                            .get()
                            .await()
                        val foundDoc = querySnapEmail.documents.firstOrNull()
                        if (foundDoc != null) {
                            val parsed = parseBusinessDoc(foundDoc)
                            if (parsed != null) return parsed
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "findBusinessByOwner query ownerEmail note: ${e.message}")
                    }
                }
                if (localPhone.isNotBlank() || e164Phone.isNotBlank() || rawPhone.isNotBlank()) {
                    val phoneCandidates = listOf(localPhone, e164Phone, rawPhone).distinct().filter { it.isNotBlank() }
                    for (candidate in phoneCandidates) {
                        try {
                            val phoneDoc = firestore.collection("registered_phones").document(candidate).get().await()
                            if (phoneDoc.exists()) {
                                val bizId = phoneDoc.getString("businessId")
                                if (!bizId.isNullOrBlank()) {
                                    val bizDoc = firestore.collection("businesses").document(bizId).get().await()
                                    if (bizDoc.exists()) {
                                        val parsed = parseBusinessDoc(bizDoc)
                                        if (parsed != null) return parsed
                                    }
                                }
                            }
                        } catch (pe: Exception) {
                            Log.w(TAG, "findBusinessByOwner registered_phones candidate $candidate note: ${pe.message}")
                        }
                    }
                    // For authenticated store owners or Super Admins, check phone and ownerPhone fields
                    for (phoneToTest in phoneCandidates) {
                        try {
                            val queryOwnerPhone = firestore.collection("businesses")
                                .whereEqualTo("ownerPhone", phoneToTest)
                                .limit(1)
                                .get()
                                .await()
                            val foundDoc = queryOwnerPhone.documents.firstOrNull()
                            if (foundDoc != null) {
                                val parsed = parseBusinessDoc(foundDoc)
                                if (parsed != null) return parsed
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "findBusinessByOwner query ownerPhone note: ${e.message}")
                        }
                        try {
                            val queryPhone = firestore.collection("businesses")
                                .whereEqualTo("phone", phoneToTest)
                                .limit(1)
                                .get()
                                .await()
                            val foundDoc = queryPhone.documents.firstOrNull()
                            if (foundDoc != null) {
                                val parsed = parseBusinessDoc(foundDoc)
                                if (parsed != null) return parsed
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "findBusinessByOwner query phone field note: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "findBusinessByOwner query error: ${e.message}")
            }
        }
        return null
    }

    suspend fun registerNewShop(business: PlatformBusiness): Result<PlatformBusiness> {
        val rawPhone = business.ownerPhone.trim()
        val localPhone = CloudBusinessAuth.normalizePhone(rawPhone)
        val e164Phone = CloudBusinessAuth.normalizeToE164(rawPhone)
        val effectivePhone = localPhone.ifBlank { rawPhone }

        if (isFirebaseConfigured()) {
            try {
                val firestore = FirebaseFirestore.getInstance()

                // 1. Prepare business document data with backward-compatible phone and ownerPhone fields
                val data = mutableMapOf<String, Any?>(
                    "id" to business.id,
                    "name" to business.businessName,
                    "businessName" to business.businessName,
                    "ownerUid" to business.ownerUid,
                    "ownerName" to business.ownerName,
                    "ownerEmail" to business.ownerEmail.trim().lowercase(),
                    "ownerPhone" to effectivePhone,
                    "phone" to effectivePhone,
                    "status" to business.status.name,
                    "statusReason" to business.statusReason,
                    "planId" to business.planId,
                    "planName" to business.planName,
                    "subscriptionStatus" to business.subscriptionStatus.name,
                    "subscriptionExpiresAt" to business.subscriptionExpiresAt,
                    "staffCount" to business.staffCount,
                    "maxStaffAllowed" to business.maxStaffAllowed,
                    "createdAt" to business.createdAt,
                    "updatedAt" to System.currentTimeMillis(),
                    "firstUseAt" to business.firstUseAt,
                    "lastActiveAt" to System.currentTimeMillis(),
                    "businessType" to business.businessType,
                    "address" to business.address,
                    "district" to business.district,
                    "isAutoExpiryDisabled" to business.isAutoExpiryDisabled,
                    "activeDaysCount" to business.activeDaysCount,
                    "phoneVerified" to business.phoneVerified,
                    "scheduledPolicyChange" to business.scheduledPolicyChange,
                    "notes" to business.notes,
                    "proposedFee" to 0.0
                )
                if (business.scheduledPolicyEffectiveAt != null) {
                    data["scheduledPolicyEffectiveAt"] = business.scheduledPolicyEffectiveAt
                }

                // 2. Prepare phone indexing data
                val phoneDocData = mapOf(
                    "phone" to effectivePhone,
                    "businessId" to business.id,
                    "businessName" to business.businessName,
                    "ownerName" to business.ownerName,
                    "ownerUid" to business.ownerUid,
                    "createdAt" to business.createdAt,
                    "updatedAt" to System.currentTimeMillis()
                )

                // 3. Atomically write the business doc AND the phone index via WriteBatch
                val writeSuccess = kotlinx.coroutines.withTimeoutOrNull(20000L) {
                    val batch = firestore.batch()
                    val bizDocRef = firestore.collection("businesses").document(business.id)
                    batch.set(bizDocRef, data, SetOptions.merge())

                    if (effectivePhone.isNotBlank()) {
                        val localPhoneRef = firestore.collection("registered_phones").document(effectivePhone)
                        batch.set(localPhoneRef, phoneDocData, SetOptions.merge())
                        if (e164Phone.isNotBlank() && e164Phone != effectivePhone) {
                            val e164PhoneRef = firestore.collection("registered_phones").document(e164Phone)
                            batch.set(e164PhoneRef, phoneDocData, SetOptions.merge())
                        }
                    }

                    batch.commit().await()
                    true
                }
                if (writeSuccess == null) {
                    val timeoutMsg = "সার্ভারে দোকান সংরক্ষণে সময় বেশি লেগেছে (টাইমআউট)। অনুগ্রহ করে ইন্টারনেট সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন।"
                    Log.e(TAG, "registerNewShop timeout: $timeoutMsg")
                    return Result.failure(java.util.concurrent.TimeoutException(timeoutMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "registerNewShop server save failed: ${e.message}", e)
                val translatedError = translateRegistrationError(e)
                return Result.failure(Exception(translatedError, e))
            }
        }

        // Successfully written to server or server not configured -> update local memory and cache
        val currentList = _businesses.value.toMutableList()
        val existingIndex = currentList.indexOfFirst {
            it.id == business.id ||
            (it.ownerUid.isNotBlank() && it.ownerUid == business.ownerUid) ||
            (it.ownerPhone == business.ownerPhone && business.ownerPhone.isNotBlank())
        }
        if (existingIndex >= 0) {
            currentList[existingIndex] = business
        } else {
            currentList.add(0, business)
        }
        _businesses.value = currentList
        saveBusinessesLocally(currentList)

        try {
            recordAuditLog(
                actorUid = business.ownerUid,
                actorName = business.ownerName,
                actorEmail = business.ownerEmail.ifBlank { "shop_owner" },
                action = "নতুন দোকান রেজিস্ট্রেশন",
                targetBusinessId = business.id,
                targetBusinessName = business.businessName,
                details = "দোকান: ${business.businessName}, মালিক: ${business.ownerName}, মোবাইল: ${business.ownerPhone}, ধরন: ${business.businessType}, ঠিকানা: ${business.address} (পলিসি: সম্পূর্ণ ফ্রি ব্যবহার)"
            )
        } catch (_: Exception) {}

        return Result.success(business)
    }

    private fun translateRegistrationError(e: Throwable): String {
        val msg = e.localizedMessage ?: e.message ?: ""
        return when {
            msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "ফায়ারবেস ক্লাউড অনুমতি ত্রুটি (PERMISSION_DENIED): আপনার গুগল বা মোবাইল একাউন্টে দোকান নিবন্ধনের উপযুক্ত পারমিশন পাওয়া যায়নি। অনুগ্রহ করে সাইন-ইন যাচাই করে পুনরায় চেষ্টা করুন।"
            msg.contains("UNAVAILABLE", ignoreCase = true) || msg.contains("network", ignoreCase = true) ->
                "নেটওয়ার্ক সংযোগ বিচ্ছিন্ন! অনুগ্রহ করে আপনার ইন্টারনেট সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন।"
            msg.contains("ALREADY_EXISTS", ignoreCase = true) ->
                "এই মোবাইল নম্বর বা আইডি দিয়ে ইতিমধ্যে একটি দোকান নিবন্ধিত রয়েছে।"
            else -> "সার্ভারে দোকান সংরক্ষণ ব্যর্থ হয়েছে: $msg"
        }
    }

    fun updateOwnerEmailForShop(shopId: String, email: String, uid: String? = null) {
        val currentList = _businesses.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == shopId }
        if (index >= 0) {
            val old = currentList[index]
            val updated = old.copy(
                ownerEmail = email.trim().lowercase(),
                ownerUid = if (!uid.isNullOrBlank()) uid else old.ownerUid
            )
            currentList[index] = updated
            _businesses.value = currentList
            saveBusinessesLocally(currentList)
        }
    }

    /**
     * Sends or updates a subscription proposal / notice to a shop.
     * Strictly acts as an informative notice or proposal without cutting or interrupting free access.
     */
    suspend fun sendSubscriptionNotice(
        businessId: String,
        proposedFee: Double,
        proposedStartDate: Long?,
        reminderDate: Long?,
        note: String,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ): Result<Unit> {
        if (!isSuperAdminEmail(actorEmail)) {
            return Result.failure(SecurityException("অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।"))
        }
        val currentList = _businesses.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == businessId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("ব্যবসা প্রতিষ্ঠানটি খুঁজে পাওয়া যায়নি!"))
        }

        val target = currentList[index]
        val updated = target.copy(
            proposedFee = proposedFee,
            proposedStartDate = proposedStartDate,
            noticeReminderDate = reminderDate,
            subscriptionNoticeNote = note,
            hasActiveNotice = true,
            lastActiveAt = System.currentTimeMillis()
        )
        currentList[index] = updated
        _businesses.value = currentList
        saveBusinessesLocally(currentList)

        val startDateStr = if (proposedStartDate != null) {
            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("bn", "BD")).format(java.util.Date(proposedStartDate))
        } else "উন্মুক্ত"
        val reminderDateStr = if (reminderDate != null) {
            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("bn", "BD")).format(java.util.Date(reminderDate))
        } else "নির্ধারিত নয়"

        recordAuditLog(
            actorUid = actorUid,
            actorName = actorName,
            actorEmail = actorEmail,
            action = "সাবস্ক্রিপশন ফি নোটিশ প্রেরণ",
            targetBusinessId = businessId,
            targetBusinessName = target.businessName,
            details = "প্রস্তাবিত ফি: ৳$proposedFee, কার্যকরের তারিখ: $startDateStr, রিমাইন্ডার: $reminderDateStr, নোট: $note (দোকানের ফ্রি অ্যাক্সেস পূর্ণ বহাল থাকবে)"
        )

        if (isFirebaseConfigured()) {
            try {
                FirebaseFirestore.getInstance().collection("businesses").document(businessId)
                    .set(
                        mapOf(
                            "proposedFee" to proposedFee,
                            "proposedStartDate" to proposedStartDate,
                            "noticeReminderDate" to reminderDate,
                            "subscriptionNoticeNote" to note,
                            "hasActiveNotice" to true,
                            "lastActiveAt" to System.currentTimeMillis()
                        ),
                        SetOptions.merge()
                    ).await()
            } catch (e: Exception) {
                Log.w(TAG, "sendSubscriptionNotice remote note: ${e.message}")
            }
        }
        return Result.success(Unit)
    }

    /**
     * Schedules a future subscription policy change for a business.
     * Does not revoke or interrupt the store's current active free access.
     */
    suspend fun schedulePolicyChange(
        businessId: String,
        policyDescription: String,
        effectiveDate: Long,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ): Result<Unit> {
        if (!isSuperAdminEmail(actorEmail)) {
            return Result.failure(SecurityException("অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।"))
        }
        val currentList = _businesses.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == businessId }
        if (index == -1) {
            return Result.failure(IllegalArgumentException("দোকানটি খুঁজে পাওয়া যায়নি!"))
        }

        val target = currentList[index]
        val updated = target.copy(
            scheduledPolicyChange = policyDescription,
            scheduledPolicyEffectiveAt = effectiveDate,
            lastActiveAt = System.currentTimeMillis()
        )
        currentList[index] = updated
        _businesses.value = currentList
        saveBusinessesLocally(currentList)

        val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("bn", "BD")).format(java.util.Date(effectiveDate))
        recordAuditLog(
            actorUid = actorUid,
            actorName = actorName,
            actorEmail = actorEmail,
            action = "পলিসি পরিবর্তন শিডিউল",
            targetBusinessId = businessId,
            targetBusinessName = target.businessName,
            details = "দোকান: ${target.businessName}, শিডিউলকৃত পলিসি: '$policyDescription', কার্যকরের তারিখ: $dateStr"
        )

        if (isFirebaseConfigured()) {
            try {
                FirebaseFirestore.getInstance().collection("businesses").document(businessId)
                    .set(
                        mapOf(
                            "scheduledPolicyChange" to policyDescription,
                            "scheduledPolicyEffectiveAt" to effectiveDate,
                            "lastActiveAt" to updated.lastActiveAt
                        ),
                        SetOptions.merge()
                    ).await()
            } catch (e: Exception) {
                Log.w(TAG, "schedulePolicyChange remote write: ${e.message}")
            }
        }
        return Result.success(Unit)
    }

    /**
     * Generates aggregated platform overview metrics.
     */
    fun computeOverviewStats(): PlatformOverviewStats {
        val bList = _businesses.value
        val pList = _payments.value

        val totalB = bList.size
        val pendingB = bList.count { it.status == BusinessAccountStatus.PENDING }
        val activeB = bList.count { it.status == BusinessAccountStatus.ACTIVE }
        val suspendedB = bList.count { it.status == BusinessAccountStatus.SUSPENDED }

        val now = System.currentTimeMillis()
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000

        val activeSubs = bList.count {
            it.subscriptionStatus == SubscriptionStatus.ACTIVE &&
            (it.isAutoExpiryDisabled || it.subscriptionExpiresAt > now)
        }
        val expiringSubs = bList.count { !it.isAutoExpiryDisabled && it.subscriptionExpiresAt in now..(now + sevenDaysMs) }
        val expiredSubs = bList.count { !it.isAutoExpiryDisabled && it.subscriptionExpiresAt < now }

        val pendingPay = pList.count { it.status == PlatformPaymentStatus.PENDING }
        val verifiedRev = pList.filter { it.status == PlatformPaymentStatus.APPROVED }.sumOf { it.amount }

        val planDist = bList.groupBy { it.planName }.mapValues { it.value.size }

        return PlatformOverviewStats(
            totalBusinesses = totalB,
            pendingBusinesses = pendingB,
            activeBusinesses = activeB,
            suspendedBusinesses = suspendedB,
            activeSubscriptions = activeSubs,
            expiringSubscriptions = expiringSubs,
            expiredSubscriptions = expiredSubs,
            pendingPaymentsCount = pendingPay,
            verifiedRevenue = verifiedRev,
            planDistribution = planDist,
            isCloudConnected = isFirebaseConfigured(),
            lastUpdated = now
        )
    }

    /**
     * Performs a dry-run audit of Super Admin authorizations and revocations.
     * Generates a detailed audit trail without modifying database rows.
     */
    fun performDryRunAudit(localUsers: List<User> = emptyList()): List<SuperAdminAuditRecord> {
        val records = mutableListOf<SuperAdminAuditRecord>()

        // 1. Authorized Identities (Preserved)
        records.add(
            SuperAdminAuditRecord(
                identifier = "ataurgonee4@gmail.com",
                previousRole = "SUPER_ADMIN",
                newRole = "SUPER_ADMIN",
                status = "AUTHORIZED_SUPER_ADMIN_PRESERVED",
                revocationReason = "অনুমোদিত প্ল্যাটফর্ম মূল সুপার এডমিন জিমেইল অ্যাকাউন্ট (Allowlist)",
                preservedData = "সকল প্ল্যাটফর্ম প্রশাসন, দোকানের তালিকা এবং অনুমোদন অধিকার সম্পূর্ণ অক্ষত"
            )
        )
        records.add(
            SuperAdminAuditRecord(
                identifier = "ataursek9@gmail.com",
                previousRole = "SUPER_ADMIN",
                newRole = "SUPER_ADMIN",
                status = "AUTHORIZED_SUPER_ADMIN_PRESERVED",
                revocationReason = "অনুমোদিত প্ল্যাটফর্ম সুপার এডমিন জিমেইল অ্যাকাউন্ট (Allowlist)",
                preservedData = "সকল প্ল্যাটফর্ম প্রশাসন ও ড্যাশবোর্ড অধিকার বহাল"
            )
        )
        records.add(
            SuperAdminAuditRecord(
                identifier = "01700000000",
                previousRole = "SUPER_ADMIN",
                newRole = "SUPER_ADMIN",
                status = "AUTHORIZED_SUPER_ADMIN_PRESERVED",
                revocationReason = "অনুমোদিত প্ল্যাটফর্ম সুপার এডমিন মোবাইল নম্বর (Allowlist)",
                preservedData = "সুপার এডমিন ও প্রাথমিক স্টোর অ্যাডমিন অধিকার বহাল"
            )
        )

        // 2. Revoked Identities (Excluded and demoted to regular shop roles without deleting data)
        REVOKED_SUPER_ADMIN_EMAILS.forEach { revokedEmail ->
            val matchingLocalUser = localUsers.firstOrNull {
                it.name.contains(revokedEmail, ignoreCase = true) || it.phone == revokedEmail
            }
            records.add(
                SuperAdminAuditRecord(
                    identifier = revokedEmail,
                    previousRole = "SUPER_ADMIN (EXPERIMENTAL / LEGACY)",
                    newRole = if (matchingLocalUser != null) matchingLocalUser.role.name else "ADMIN (SHOP_LEVEL)",
                    status = "SUPER_ADMIN_PRIVILEGE_REVOKED",
                    revocationReason = if (revokedEmail == "buddavaibinodon@gmail.com") {
                        "ইউজারের সুনির্দিষ্ট নির্দেশ অনুযায়ী buddavaibinodon@gmail.com এর সুপার এডমিন অধিকার প্রত্যাহার করা হয়েছে।"
                    } else {
                        "অননুমোদিত পরীক্ষামূলক একাউন্ট; শুধুমাত্র নির্ধারিত অনুমোদিত তালিকায় থাকা একাউন্ট সুপার এডমিন হতে পারবে।"
                    },
                    preservedData = "দোকানের সকল ডেটা, পণ্যের স্টক, বিক্রয় রশিদ ও কাস্টমার তালিকা ১০০% সংরক্ষিত"
                )
            )
        }

        return records
    }

    /**
     * Executes safe revocation migration:
     * 1. Demotes unauthorized Super Admins in local Room DB to regular store Admin.
     * 2. Clears SharedPreferences flags for revoked emails.
     * 3. Syncs revocation status to Firestore /platform_superadmins registry.
     * Never deletes business documents, sales, products, or customer records.
     */
    suspend fun executeSafeRevocationMigration(database: com.example.data.AppDatabase?): List<SuperAdminAuditRecord> {
        val localUsers = try { database?.userDao()?.getAllActiveUsersList() ?: emptyList() } catch (_: Exception) { emptyList() }
        val auditRecords = performDryRunAudit(localUsers)

        // 1. Local Database migration: demote any revoked users from SUPER_ADMIN to ADMIN
        database?.let { db ->
            localUsers.forEach { user ->
                if (user.role == UserRole.SUPER_ADMIN) {
                    val isStillSuperAdmin = isSuperAdminPhone(user.phone)
                    if (!isStillSuperAdmin) {
                        val demoted = user.copy(role = UserRole.ADMIN)
                        try {
                            db.userDao().updateUser(demoted)
                            Log.i(TAG, "Migrated user ${user.id} (${user.phone}) to ADMIN")
                        } catch (e: Exception) {
                            Log.w(TAG, "User demote failed: ${e.message}")
                        }
                    }
                }
            }
        }

        // 2. SharedPreferences sync
        try {
            val storedEmail = com.example.data.sync.CloudBusinessAuth.getStoredAuthEmail(context)
            if (!storedEmail.isNullOrBlank() && (isRevokedSuperAdminEmail(storedEmail) || !isSuperAdminEmail(storedEmail))) {
                com.example.data.sync.CloudBusinessAuth.setStoredSuperAdminFlag(context, false)
                Log.i(TAG, "Cleared stored Super Admin flag for $storedEmail")
            }
        } catch (e: Exception) {
            Log.w(TAG, "SharedPrefs revocation cleanup error: ${e.message}")
        }

        // 3. Remote Firestore registry synchronization
        if (isFirebaseConfigured() && isUserSignedIn()) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                // Mark revoked accounts in platform_superadmins registry
                REVOKED_SUPER_ADMIN_EMAILS.forEach { email ->
                    val docId = "revoked_${email.replace('@', '_').replace('.', '_')}"
                    val revocationData = mapOf(
                        "email" to email,
                        "role" to "ADMIN",
                        "isActive" to false,
                        "isSuperAdmin" to false,
                        "revokedAt" to System.currentTimeMillis(),
                        "revocationReason" to "Super Admin privilege revoked per platform allowlist policy"
                    )
                    firestore.collection("platform_superadmins").document(docId)
                        .set(revocationData, SetOptions.merge())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Remote revocation registry note: ${e.message}")
            }
        }

        return auditRecords
    }
}

/**
 * Data model for Super Admin authorization audit log and migration reports.
 */
data class SuperAdminAuditRecord(
    val identifier: String,
    val previousRole: String,
    val newRole: String,
    val status: String,
    val revocationReason: String,
    val preservedData: String
)

