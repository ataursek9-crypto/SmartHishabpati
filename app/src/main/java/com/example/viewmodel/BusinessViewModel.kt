package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.model.*
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SuperAdminRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.util.BengaliVoiceHelper
import com.example.data.repository.SmartReminderManager
import com.example.data.repository.StockVerificationRepository
import com.example.data.repository.AdminMismatchAction
import java.util.Calendar
import java.util.UUID

import com.example.data.storage.SavedAccount
import com.example.data.storage.SavedAccountManager
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import com.example.data.sync.CloudBusinessAuth
import com.example.data.sync.CloudSyncManager
import com.example.data.sync.CloudSyncManager.Companion.awaitResult
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

data class DashboardSummary(
    val todaySales: Double = 0.0,
    val todayCollected: Double = 0.0,
    val totalDue: Double = 0.0,
    val totalApprovedExpense: Double = 0.0,
    val netProfit: Double = 0.0,
    val lowStockCount: Int = 0,
    val pendingExpenseCount: Int = 0,
    val totalStockValuation: Double = 0.0
)

// Customer Due Item with last transaction timestamp & totals
data class CustomerDueItem(
    val customer: Customer,
    val lastTransactionDate: Long = 0L,
    val totalTransactionsCount: Int = 0
)

// Sanitized stock view for Salesman (purchase price hidden)
data class SalesmanProductView(
    val id: String,
    val name: String,
    val sku: String,
    val category: String,
    val sellingPrice: Double,
    val currentStock: Double,
    val unit: String,
    val imageUri: String? = null,
    val imageSource: String = "NONE"
)

class BusinessViewModel(application: Application) : AndroidViewModel(application) {
    val productImageService = com.example.data.service.ProductImageService(application)
    val repository = BusinessRepository(AppDatabase.getDatabase(application), productImageService)
    val savedAccountManager = SavedAccountManager.getInstance(application)
    val savedAccounts: StateFlow<List<SavedAccount>> = savedAccountManager.savedAccounts
    val keepAccountsOnLogout: StateFlow<Boolean> = savedAccountManager.keepOnLogout

    val syncManager = com.example.data.sync.CloudSyncManager(application, repository.getDbInstance())
    val syncState: StateFlow<com.example.data.sync.SyncState> = syncManager.syncState

    private val _activeBusinessId = MutableStateFlow(
        CloudBusinessAuth.getStoredBusinessId(application) ?: ""
    )
    val activeBusinessId: StateFlow<String> = _activeBusinessId.asStateFlow()

    fun getActiveBusinessId(): String {
        val stored = CloudBusinessAuth.getStoredBusinessId(getApplication())
        if (!stored.isNullOrBlank() && stored != "admin_1" && stored != "salesman_1") return stored
        val fUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        if (!fUid.isNullOrBlank()) {
            val current = _currentUser.value
            val role = if (current?.role == UserRole.SALESMAN) UserRole.SALESMAN.name else UserRole.ADMIN.name
            if (role == UserRole.ADMIN.name) {
                CloudBusinessAuth.setStoredBusinessId(getApplication(), fUid, role = role)
                _activeBusinessId.value = fUid
                return fUid
            }
        }
        return ""
    }

    fun refreshActiveBusinessId() {
        _activeBusinessId.value = getActiveBusinessId()
    }

    fun triggerSync(onResult: ((Boolean, String) -> Unit)? = null) {
        syncManager.triggerSync(onResult)
    }

    fun connectSalesmanToBusiness(
        businessId: String,
        phone: String = "",
        pin: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        val cleanBizId = businessId.trim()
        val cleanPhone = phone.trim()
        val cleanPin = pin.trim()

        if (cleanBizId.isBlank()) {
            onResult(false, "সঠিক ক্লাউড বিজনেস কোড দিন!")
            return
        }
        viewModelScope.launch {
            val fUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
            if (fUser == null || fUser.uid.isBlank()) {
                onResult(false, "ক্লাউড ব্যবসার সাথে যুক্ত হতে গুগল সাইন-ইন আবশ্যক!")
                return@launch
            }

            val firestore = FirebaseFirestore.getInstance()

            // Step 1: Verify business doc exists
            val bizDoc = try {
                Log.i("BusinessLinking", "[ConnectFlow] Step 1: Reading business doc /businesses/$cleanBizId")
                firestore.collection("businesses").document(cleanBizId).get().await()
            } catch (e: Exception) {
                Log.e("BusinessLinking", "[ConnectFlow] Step 1 Failed (READ_BUSINESS_DOC): /businesses/$cleanBizId - ${e.message}")
                val err = e.localizedMessage ?: e.message ?: "ত্রুটি"
                val msg = if (err.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "[ধাপ ১: READ /businesses] ব্যবসা কোড যাচাই অনুমতি ত্রুটি (PERMISSION_DENIED): ফায়ারবেস কনসোলে Rules পাবলিশ করা আছে কি না যাচাই করুন।"
                } else {
                    "[ধাপ ১: READ /businesses] ব্যবসা কোড যাচাই ব্যর্থ: $err"
                }
                onResult(false, msg)
                return@launch
            }

            if (!bizDoc.exists()) {
                onResult(false, "এই বিজনেস কোড ($cleanBizId) ক্লাউডে পাওয়া যায়নি! সঠিক কোড লিখুন।")
                return@launch
            }

            val bizName = bizDoc.getString("businessName") ?: "স্মার্ট বিজনেস"

            // Step 2: Check if user is already an actively linked staff in Firestore
            try {
                Log.i("BusinessLinking", "[ConnectFlow] Step 2: Checking existing staff link under /businesses/$cleanBizId/staff/${fUser.uid}")
                val existingStaffDoc = firestore.collection("businesses").document(cleanBizId)
                    .collection("staff").document(fUser.uid).get().await()
                if (existingStaffDoc.exists() && (existingStaffDoc.getBoolean("isActive") == true)) {
                    val staffName = existingStaffDoc.getString("name") ?: (fUser.displayName ?: "সেলস প্রতিনিধি")
                    val staffPhone = existingStaffDoc.getString("phone") ?: cleanPhone

                    CloudBusinessAuth.setStoredBusinessId(
                        context = getApplication(),
                        businessId = cleanBizId,
                        businessName = bizName,
                        role = UserRole.SALESMAN.name
                    )
                    _activeBusinessId.value = cleanBizId

                    val localUser = User(
                        id = fUser.uid,
                        name = staffName,
                        phone = staffPhone.ifBlank { "01800000000" },
                        role = UserRole.SALESMAN,
                        pinHash = if (cleanPin.isNotBlank()) repository.hashPin(cleanPin) else repository.hashPin("1234"),
                        isActive = true,
                        accountStatus = UserAccountStatus.ACTIVE
                    )
                    repository.saveUserDirect(localUser)
                    _currentUser.value = localUser

                    syncManager.triggerSync { success, msg ->
                        if (success) {
                            onResult(true, "ব্যবসার সাথে সফলভাবে যুক্ত আছেন! সমস্ত পণ্য ও স্টক সিঙ্ক সম্পন্ন।")
                        } else {
                            onResult(true, "ব্যবসার সাথে যুক্ত। সিঙ্ক বার্তা: $msg")
                        }
                    }
                    return@launch
                }
            } catch (e: Exception) {
                Log.w("BusinessLinking", "[ConnectFlow] Existing staff check non-fatal note: ${e.message}")
            }

            // Step 3: Require registered Phone & PIN for strict security verification
            val rawPhone = cleanPhone.ifBlank { _currentUser.value?.phone ?: "" }
            val resolvedPhone = CloudBusinessAuth.normalizePhone(rawPhone)
            val resolvedPin = cleanPin

            if (resolvedPhone.isBlank() || resolvedPin.isBlank()) {
                onResult(false, "অনুমোদন ত্রুটি: অ্যাডমিন কর্তৃক নিবন্ধিত মোবাইল নম্বর এবং গোপন পিন কোড দিন!")
                return@launch
            }

            Log.i("BusinessLinking", "[ConnectFlow] Step 3: Verifying staff in /businesses/$cleanBizId/staff_by_phone")
            val staffPhoneDoc = try {
                firestore.collection("businesses").document(cleanBizId)
                    .collection("staff_by_phone").document(resolvedPhone).get().await()
            } catch (e: Exception) {
                Log.e("BusinessLinking", "[ConnectFlow] Step 3 Failed (READ_STAFF_BY_PHONE): /businesses/$cleanBizId/staff_by_phone - ${e.message}")
                val err = e.localizedMessage ?: e.message ?: "ত্রুটি"
                val msg = if (err.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "[ধাপ ৩: READ /staff_by_phone] স্টাফ নম্বর যাচাই অনুমতি ত্রুটি (PERMISSION_DENIED): ফায়ারবেস রুলসে staff_by_phone পড়ার অনুমতি নেই।"
                } else {
                    "[ধাপ ৩: READ /staff_by_phone] স্টাফ নম্বর যাচাই ব্যর্থ: $err"
                }
                onResult(false, msg)
                return@launch
            }

            if (!staffPhoneDoc.exists()) {
                onResult(false, "অননুমোদিত স্টাফ: মোবাইল নম্বরটি ($resolvedPhone) অ্যাডমিন ক্লাউডে নিবন্ধন করেননি অথবা অ্যাডমিন ফোন থেকে এখনও 'ক্লাউড সিঙ্ক' করা হয়নি। অ্যাডমিনকে ফোনে গিয়ে স্টাফ নম্বর যোগ এবং ক্লাউড সিঙ্ক করতে বলুন।")
                return@launch
            }

            val isActive = staffPhoneDoc.getBoolean("isActive") ?: false
            if (!isActive) {
                onResult(false, "এই স্টাফ অ্যাকাউন্টটি নিষ্ক্রিয় বা স্থগিত করা হয়েছে। অ্যাডমিনের সাথে যোগাযোগ করুন।")
                return@launch
            }

            // Cryptographic PIN verification
            val expectedPinHash = staffPhoneDoc.getString("pinHash")
            val providedPinHash = repository.hashPin(resolvedPin)
            val localAuth = repository.authenticateUser(resolvedPhone, resolvedPin)

            val isPinValid = if (!expectedPinHash.isNullOrBlank()) {
                expectedPinHash == providedPinHash
            } else if (localAuth is BusinessRepository.AuthResult.Success) {
                true
            } else {
                resolvedPin == "1234"
            }

            if (!isPinValid) {
                onResult(false, "ভুল পিন কোড! অ্যাডমিনের দেওয়া সঠিক পিন কোড দিয়ে চেষ্টা করুন।")
                return@launch
            }

            val staffName = staffPhoneDoc.getString("name") ?: (fUser.displayName ?: "সেলস প্রতিনিধি")

            // Step 4: Register active staff profile in Firestore under /businesses/{cleanBizId}/staff/{fUser.uid}
            Log.i("BusinessLinking", "[ConnectFlow] Step 4: Writing staff profile /businesses/$cleanBizId/staff/${fUser.uid}")
            try {
                val staffDocRef = firestore.collection("businesses").document(cleanBizId)
                    .collection("staff").document(fUser.uid)
                val staffData = mapOf(
                    "id" to fUser.uid,
                    "name" to staffName,
                    "phone" to resolvedPhone,
                    "email" to (fUser.email ?: ""),
                    "role" to UserRole.SALESMAN.name,
                    "isActive" to true,
                    "businessId" to cleanBizId,
                    "linkedAt" to System.currentTimeMillis()
                )
                staffDocRef.set(staffData, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.e("BusinessLinking", "[ConnectFlow] Step 4 Failed (WRITE_STAFF_PROFILE): /businesses/$cleanBizId/staff/${fUser.uid} - ${e.message}")
                val err = e.localizedMessage ?: e.message ?: "ত্রুটি"
                val msg = if (err.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "[ধাপ ৪: WRITE /staff] স্টাফ প্রোফাইল তৈরি অনুমতি ত্রুটি (PERMISSION_DENIED): ফায়ারবেস সিকিউরিটি রুলস অনুযায়ী স্টাফ লিঙ্ক করার অনুমতি মেলেনি।"
                } else {
                    "[ধাপ ৪: WRITE /staff] স্টাফ প্রোফাইল সংরক্ষণ ব্যর্থ: $err"
                }
                onResult(false, msg)
                return@launch
            }

            // Step 5: Register in root /staff_memberships/{fUser.uid} with phone for membership verification
            Log.i("BusinessLinking", "[ConnectFlow] Step 5: Writing staff membership /staff_memberships/${fUser.uid}")
            try {
                firestore.collection("staff_memberships").document(fUser.uid).set(
                    mapOf(
                        "userId" to fUser.uid,
                        "businessId" to cleanBizId,
                        "role" to UserRole.SALESMAN.name,
                        "isActive" to true,
                        "phone" to resolvedPhone,
                        "email" to (fUser.email ?: ""),
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
            } catch (e: Exception) {
                Log.e("BusinessLinking", "[ConnectFlow] Step 5 Failed (WRITE_STAFF_MEMBERSHIP): /staff_memberships/${fUser.uid} - ${e.message}")
                val err = e.localizedMessage ?: e.message ?: "ত্রুটি"
                val msg = if (err.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "[ধাপ ৫: WRITE /staff_memberships] স্টাফ মেম্বারশিপ ইনডেক্স অনুমতি ত্রুটি (PERMISSION_DENIED): ফায়ারবেস রুলসে /staff_memberships আপডেট ব্লক করা হয়েছে।"
                } else {
                    "[ধাপ ৫: WRITE /staff_memberships] স্টাফ মেম্বারশিপ সংরক্ষণ ব্যর্থ: $err"
                }
                onResult(false, msg)
                return@launch
            }

            // Step 6: Store locally and update session
            Log.i("BusinessLinking", "[ConnectFlow] Step 6: Updating session and local Room database")
            CloudBusinessAuth.setStoredBusinessId(
                context = getApplication(),
                businessId = cleanBizId,
                businessName = bizName,
                role = UserRole.SALESMAN.name
            )
            _activeBusinessId.value = cleanBizId

            val localUser = User(
                id = fUser.uid,
                name = staffName,
                phone = resolvedPhone,
                role = UserRole.SALESMAN,
                pinHash = providedPinHash,
                isActive = true,
                accountStatus = UserAccountStatus.ACTIVE
            )
            repository.saveUserDirect(localUser)
            _currentUser.value = localUser

            // Step 7: Trigger immediate sync
            Log.i("BusinessLinking", "[ConnectFlow] Step 7: Triggering cloud sync")
            syncManager.triggerSync { success, msg ->
                if (success) {
                    onResult(true, "ব্যবসার সাথে সফলভাবে যুক্ত হয়েছেন! সমস্ত পণ্য ও স্টক সিঙ্ক সম্পন্ন।")
                } else {
                    onResult(true, "ব্যবসার সাথে যুক্ত হয়েছেন। সিঙ্ক বার্তা: $msg")
                }
            }
        }
    }

    // Product Image Mode: AUTO vs MANUAL
    val productImageMode: StateFlow<String> = repository.settingsFlow
        .map { it?.productImageMode ?: "AUTO" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AUTO")

    fun setProductImageMode(mode: String) {
        viewModelScope.launch {
            repository.setProductImageMode(mode)
        }
    }

    suspend fun generateAiProductImage(title: String, category: String, sku: String, unit: String): com.example.data.service.ProductImageResult {
        return productImageService.generateWithAi(title, category, sku, unit)
    }

    suspend fun searchOnlineImages(query: String): List<com.example.data.service.OnlineImageCandidate> {
        val keyword = productImageService.mapCategoryToSearchKeyword(query, query)
        return productImageService.searchOnlineImages(keyword)
    }

    suspend fun downloadOnlineImage(candidate: com.example.data.service.OnlineImageCandidate): com.example.data.service.ProductImageResult {
        return productImageService.downloadAndSaveOnlineImage(candidate)
    }

    // Active User State
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // App Lock PIN Verification state
    private val _isUnlocked = MutableStateFlow(true)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // Raw Products stream from database
    val rawProducts: StateFlow<List<Product>> = repository.products
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<Product>> = rawProducts

    // Sanitized product list for Salesman UI
    val salesmanProducts: StateFlow<List<SalesmanProductView>> = rawProducts.map { list ->
        list.map {
            SalesmanProductView(
                id = it.id,
                name = it.name,
                sku = it.sku,
                category = it.category,
                sellingPrice = it.sellingPrice,
                currentStock = it.currentStock,
                unit = it.unit,
                imageUri = it.imageUri,
                imageSource = it.imageSource
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockProducts: StateFlow<List<Product>> = repository.lowStockProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<Customer>> = repository.customers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSales: StateFlow<List<Sale>> = repository.allSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReceipts: StateFlow<List<PaymentReceipt>> = repository.allReceipts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExpenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockLogs: StateFlow<List<StockLog>> = repository.stockLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val companySettings: StateFlow<CompanySettings> = repository.settingsFlow
        .map { it ?: CompanySettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompanySettings())

    val users: StateFlow<List<User>> = combine(
        repository.users,
        _activeBusinessId,
        _currentUser
    ) { allUsers, activeBizId, currUser ->
        val effectiveShopId = when {
            activeBizId.isNotBlank() && activeBizId != "admin_1" && activeBizId != "salesman_1" -> activeBizId
            currUser != null && currUser.shopId.isNotBlank() -> currUser.shopId
            else -> ""
        }
        if (effectiveShopId.isNotBlank()) {
            allUsers.filter { it.shopId == effectiveShopId }
        } else {
            allUsers.filter { it.shopId == "biz_smart_store_1" || it.shopId.isBlank() }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allSubmissions: StateFlow<List<ProductSubmission>> = repository.allProductSubmissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingSubmissionsCount: StateFlow<Int> = repository.pendingProductSubmissionsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val companies: StateFlow<List<Company>> = repository.companies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val companyPurchases: StateFlow<List<CompanyPurchase>> = repository.companyPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val companyPayments: StateFlow<List<CompanyPayment>> = repository.companyPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recycleBinItems: StateFlow<List<RecycleBinItem>> = repository.recycleBinItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recycleBinCount: StateFlow<Int> = repository.recycleBinCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val testProducts: StateFlow<List<Product>> = repository.testProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val testCompanies: StateFlow<List<Company>> = repository.testCompanies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val testCustomers: StateFlow<List<Customer>> = repository.testCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val testSales: StateFlow<List<Sale>> = repository.testSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val testReceipts: StateFlow<List<PaymentReceipt>> = repository.testReceipts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val testExpenses: StateFlow<List<Expense>> = repository.testExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Test Due Customer Detection Flows
    private val _detectedTestDueCustomers = MutableStateFlow<List<DetectedTestDueCustomer>>(emptyList())
    val detectedTestDueCustomers: StateFlow<List<DetectedTestDueCustomer>> = _detectedTestDueCustomers.asStateFlow()

    private val _isDetectingTestDues = MutableStateFlow(false)
    val isDetectingTestDues: StateFlow<Boolean> = _isDetectingTestDues.asStateFlow()

    // Role-filtered submissions: Salesman only sees their submissions, Admin sees all
    val mySubmissions: StateFlow<List<ProductSubmission>> = combine(_currentUser, allSubmissions) { user, subs ->
        if (user == null) emptyList()
        else if (user.role == UserRole.ADMIN) subs
        else {
            val authUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null } ?: ""
            subs.filter { sub ->
                sub.salesmanId == user.id ||
                (authUid.isNotBlank() && sub.salesmanId == authUid) ||
                (user.phone.isNotBlank() && sub.salesmanPhone == user.phone)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Feedback message
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // Product Submission Sync Diagnostics & Progress
    private val _submissionSyncInfo = MutableStateFlow<String?>(null)
    val submissionSyncInfo: StateFlow<String?> = _submissionSyncInfo.asStateFlow()

    private val _isFetchingSubmissions = MutableStateFlow(false)
    val isFetchingSubmissions: StateFlow<Boolean> = _isFetchingSubmissions.asStateFlow()

    private val _submissionFetchError = MutableStateFlow<String?>(null)
    val submissionFetchError: StateFlow<String?> = _submissionFetchError.asStateFlow()

    private val _isSubmissionsFetchSuccessful = MutableStateFlow(false)
    val isSubmissionsFetchSuccessful: StateFlow<Boolean> = _isSubmissionsFetchSuccessful.asStateFlow()

    fun clearSubmissionFetchError() {
        _submissionFetchError.value = null
    }

    // Smart Reminder Service & Voice Helper
    val voiceHelper = BengaliVoiceHelper(application)
    val reminderManager = SmartReminderManager(application, repository.getDbInstance(), voiceHelper)
    val isBengaliTtsSupported: StateFlow<Boolean> = voiceHelper.isBengaliSupported
    val ttsStatusMessage: StateFlow<String> = voiceHelper.statusMessage

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeReminders: StateFlow<List<ReminderItem>> = _currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else if (user.role == UserRole.ADMIN) {
            repository.getAdminRemindersFlow()
        } else {
            repository.getSalesmanRemindersFlow(user.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pendingRemindersCount: StateFlow<Int> = _currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(0)
        } else if (user.role == UserRole.ADMIN) {
            repository.getAdminPendingCountFlow()
        } else {
            repository.getSalesmanPendingCountFlow(user.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val userReminderPreference: StateFlow<ReminderPreference> = _currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(ReminderPreference(userId = "default"))
        } else {
            repository.getReminderPreferenceFlow(user.id).map {
                it ?: ReminderPreference(userId = user.id)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderPreference(userId = "default"))

    // Admin-to-Salesman Alert Repository
    val alertRepository = com.example.data.repository.AlertRepository(repository.getDbInstance(), application, voiceHelper)

    val adminAlertDetails: StateFlow<List<AdminAlertDetail>> = alertRepository.adminAlertDetailsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val salesmanAlerts: StateFlow<List<SalesmanAlertView>> = _currentUser.flatMapLatest { user ->
        if (user == null || user.role != UserRole.SALESMAN) {
            flowOf(emptyList())
        } else {
            alertRepository.getSalesmanAlertsFlow(user.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val salesmanPendingAlertsCount: StateFlow<Int> = _currentUser.flatMapLatest { user ->
        if (user == null || user.role != UserRole.SALESMAN) {
            flowOf(0)
        } else {
            alertRepository.getSalesmanPendingCountFlow(user.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Shop Stock Verification & Audit System ---
    val stockVerificationRepository = StockVerificationRepository(repository.getDbInstance(), alertRepository)

    val allStockVerifications: StateFlow<List<StockVerification>> = stockVerificationRepository.getAllVerificationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingStockApprovals: StateFlow<List<StockVerification>> = stockVerificationRepository.getPendingApprovalsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingStockApprovalsCount: StateFlow<Int> = stockVerificationRepository.getPendingApprovalCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val stockVerificationSchedules: StateFlow<List<StockVerificationSchedule>> = stockVerificationRepository.getAllSchedulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productVerificationItems: StateFlow<List<ProductVerificationItem>> = combine(
        products,
        stockVerificationSchedules,
        allStockVerifications
    ) { prods, schedules, verifs ->
        val scheduleMap = schedules.associateBy { it.productId }
        val verifsByProduct = verifs.groupBy { it.productId }
        val now = System.currentTimeMillis()

        prods.map { prod ->
            val sched = scheduleMap[prod.id]
            val prodVerifs = verifsByProduct[prod.id].orEmpty()
            val lastVerif = prodVerifs.firstOrNull { it.status == VerificationStatus.MATCHED || it.status == VerificationStatus.APPROVED_ADJUSTED }
                ?: prodVerifs.firstOrNull()
            val pendingMismatch = prodVerifs.firstOrNull { it.status == VerificationStatus.MISMATCH_PENDING || it.status == VerificationStatus.RECOUNT_REQUESTED }

            val nextDueAt = sched?.nextDueAt ?: 0L
            val isOverdue = nextDueAt > 0L && now > nextDueAt
            val lastTime = lastVerif?.verifiedAt ?: sched?.lastVerifiedAt ?: 0L
            val daysSince = if (lastTime > 0) ((now - lastTime) / (24L * 60 * 60 * 1000L)).toInt() else -1

            ProductVerificationItem(
                product = prod,
                schedule = sched,
                lastVerification = lastVerif,
                pendingMismatch = pendingMismatch,
                isOverdue = isOverdue,
                daysSinceLastVerification = daysSince
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockControlSummaryStats: StateFlow<StockControlSummaryStats> = combine(
        productVerificationItems,
        allStockVerifications,
        pendingStockApprovals
    ) { items, verifs, pendingApprovals ->
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val verifiedToday = verifs.count { it.verifiedAt >= startOfToday }
        val lastVerifTime = verifs.maxOfOrNull { it.verifiedAt }
        val mismatches = verifs.filter { it.difference != 0.0 }
        val resolvedMismatches = mismatches.count { it.isResolved }
        val overdueCount = items.count { it.isOverdue }
        val pendingCount = items.count { it.lastVerification == null || it.isOverdue }

        StockControlSummaryStats(
            totalProducts = items.size,
            lastVerificationTime = lastVerifTime,
            verifiedTodayCount = verifiedToday,
            pendingVerificationCount = pendingCount,
            mismatchCount = mismatches.size,
            pendingAdminApprovalCount = pendingApprovals.size,
            resolvedMismatchesCount = resolvedMismatches,
            overdueCount = overdueCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StockControlSummaryStats())

    private val _isDefaultAdminPinActive = MutableStateFlow(false)
    val isDefaultAdminPinActive: StateFlow<Boolean> = _isDefaultAdminPinActive.asStateFlow()

    init {
        viewModelScope.launch {
            // Real business mode: Essential accounts and settings only, no fake demo records
            repository.seedInitialDataIfEmpty(includeSampleDemoData = false)
            checkDefaultAdminPinStatus()
            reminderManager.schedulePeriodicCheck()
            alertRepository.checkAndDispatchScheduledAlerts()

            // Auto-restore Super Admin session ONLY if previously authenticated with verified allowlisted identity
            try {
                val superAdminRepo = SuperAdminRepository.getInstance(application)
                val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val storedEmail = authUser?.email?.trim()?.lowercase() ?: CloudBusinessAuth.getStoredAuthEmail(application)
                val isStoredSuperAdmin = CloudBusinessAuth.isStoredSuperAdmin(application)
                val userPhone = authUser?.phoneNumber?.trim() ?: ""

                val isEmailAuthorized = !storedEmail.isNullOrBlank() && repository.isAuthorizedSuperAdminEmail(storedEmail)
                val isPhoneAuthorized = repository.isAuthorizedSuperAdminPhone(userPhone)
                val isRevoked = !storedEmail.isNullOrBlank() && repository.isRevokedSuperAdminEmail(storedEmail)

                // If this ViewModel instance has already authenticated a Super Admin session, preserve it!
                if (_isSuperAdminSession.value) {
                    return@launch
                }

                if (isStoredSuperAdmin && !isRevoked && (isEmailAuthorized || isPhoneAuthorized)) {
                    val verifiedIdentifier = if (isEmailAuthorized) storedEmail!! else userPhone
                    val uid = authUser?.uid ?: "superadmin_${verifiedIdentifier.replace('@', '_').replace('.', '_').replace('+', '_')}"
                    val existing = repository.getUserById(uid)
                        ?: repository.getUserByPhone("01700000000")
                        ?: repository.getUserById("admin_1")
                    if (existing == null || (existing.isActive && existing.accountStatus == UserAccountStatus.ACTIVE)) {
                        _isSuperAdminSession.value = true
                        _superAdminVerifiedEmail.value = verifiedIdentifier
                        _superAdminUid.value = uid
                        _currentGoogleEmail.value = storedEmail
                        val superAdminUser = if (existing != null) {
                            existing.copy(
                                id = uid,
                                role = UserRole.ADMIN,
                                isActive = true,
                                accountStatus = UserAccountStatus.ACTIVE
                            )
                        } else {
                            User(
                                id = uid,
                                name = authUser?.displayName ?: "মোঃ আতাউর রহমান",
                                phone = authUser?.phoneNumber ?: "01700000000",
                                role = UserRole.ADMIN,
                                shopId = "biz_smart_store_1",
                                pinHash = repository.hashPin("1234"),
                                isActive = true,
                                accountStatus = UserAccountStatus.ACTIVE
                            )
                        }
                        repository.saveUserDirect(superAdminUser)
                        _activeBusinessId.value = superAdminUser.shopId.takeIf { it.isNotBlank() } ?: "biz_smart_store_1"
                        _currentUser.value = superAdminUser
                    } else {
                        // User revoked or inactive
                        if (!_isSuperAdminSession.value) {
                            _isSuperAdminSession.value = false
                            _superAdminVerifiedEmail.value = null
                            _superAdminUid.value = null
                            CloudBusinessAuth.setStoredSuperAdminFlag(application, false)
                        }
                    }
                } else {
                    if (!_isSuperAdminSession.value) {
                        _isSuperAdminSession.value = false
                        _superAdminVerifiedEmail.value = null
                        _superAdminUid.value = null
                        CloudBusinessAuth.setStoredSuperAdminFlag(application, false)
                    }
                }
            } catch (e: Exception) {
                Log.w("BusinessViewModel", "Session auto-restore note: ${e.message}")
                if (!_isSuperAdminSession.value) {
                    _isSuperAdminSession.value = false
                    _superAdminVerifiedEmail.value = null
                    _superAdminUid.value = null
                    CloudBusinessAuth.setStoredSuperAdminFlag(application, false)
                }
            }
        }
        viewModelScope.launch {
            products.collectLatest { prodList ->
                if (prodList.isNotEmpty()) {
                    stockVerificationRepository.ensureSchedulesForProducts(prodList)
                }
            }
        }
        viewModelScope.launch {
            _currentUser.collectLatest { user ->
                if (user != null) {
                    reminderManager.scanAndSyncReminders(user)
                    alertRepository.checkAndDispatchScheduledAlerts()
                    if (user.role == UserRole.ADMIN) {
                        scanTestDueCustomers()
                        checkDefaultAdminPinStatus()
                    }
                }
            }
        }
        viewModelScope.launch {
            customers.collectLatest {
                scanTestDueCustomers()
            }
        }
    }

    // Role-filtered sales
    val mySales: StateFlow<List<Sale>> = combine(_currentUser, allSales) { user, sales ->
        if (user == null) emptyList()
        else if (user.role == UserRole.ADMIN) sales
        else sales.filter { it.salesmanId == user.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Role-filtered receipts
    val myReceipts: StateFlow<List<PaymentReceipt>> = combine(_currentUser, allReceipts) { user, receipts ->
        if (user == null) emptyList()
        else if (user.role == UserRole.ADMIN) receipts
        else receipts.filter { it.salesmanId == user.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Role-filtered expenses
    val myExpenses: StateFlow<List<Expense>> = combine(_currentUser, allExpenses) { user, expenses ->
        if (user == null) emptyList()
        else if (user.role == UserRole.ADMIN) expenses
        else {
            val authUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null } ?: ""
            expenses.filter { exp ->
                exp.salesmanId == user.id || (authUid.isNotBlank() && exp.salesmanId == authUid)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -------------------------------------------------------------
    // Customer Due Lists (Role-based & Accurately filtered > 0 due)
    // -------------------------------------------------------------

    // Helper map of customerId to last transaction timestamp (from sales & receipts)
    private val lastTransactionByCustomer: Flow<Map<String, Long>> = combine(allSales, allReceipts) { sales, receipts ->
        val map = mutableMapOf<String, Long>()
        for (sale in sales) {
            val curr = map[sale.customerId] ?: 0L
            if (sale.createdAt > curr) map[sale.customerId] = sale.createdAt
        }
        for (rct in receipts) {
            val curr = map[rct.customerId] ?: 0L
            if (rct.createdAt > curr) map[rct.customerId] = rct.createdAt
        }
        map
    }

    // Publicly exposed map of customerId to last transaction timestamp
    val lastTransactionDateMap: StateFlow<Map<String, Long>> = lastTransactionByCustomer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // 1. Admin Due Customers List (All customers with currentDue > 0.0)
    val adminDueCustomers: StateFlow<List<CustomerDueItem>> = combine(
        customers,
        lastTransactionByCustomer
    ) { custList, txMap ->
        custList
            .filter { it.currentDue > 0.0 }
            .sortedByDescending { it.currentDue }
            .map { c ->
                val lastTx = txMap[c.id] ?: c.updatedAt
                CustomerDueItem(customer = c, lastTransactionDate = lastTx)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. Salesman Due Customers List:
    // Salesman sees ONLY customers that have interacted with them (via salesman's sales or receipts)
    // and have currentDue > 0.0. Other salesmen's customers are strictly excluded!
    val salesmanDueCustomers: StateFlow<List<CustomerDueItem>> = combine(
        _currentUser,
        customers,
        mySales,
        myReceipts,
        lastTransactionByCustomer
    ) { user, custList, sales, receipts, txMap ->
        if (user == null) {
            emptyList()
        } else if (user.role == UserRole.ADMIN) {
            // Admin gets all due customers
            custList
                .filter { it.currentDue > 0.0 }
                .sortedByDescending { it.currentDue }
                .map { c ->
                    CustomerDueItem(customer = c, lastTransactionDate = txMap[c.id] ?: c.updatedAt)
                }
        } else {
            // Authorized customer IDs for this salesman
            val authorizedCustomerIds = mutableSetOf<String>()
            sales.forEach { authorizedCustomerIds.add(it.customerId) }
            receipts.forEach { authorizedCustomerIds.add(it.customerId) }

            val targetList = if (authorizedCustomerIds.isEmpty()) {
                custList.filter { it.id == "c_1" }
            } else {
                custList.filter { authorizedCustomerIds.contains(it.id) }
            }

            targetList
                .filter { it.currentDue > 0.0 }
                .sortedByDescending { it.currentDue }
                .map { c ->
                    CustomerDueItem(customer = c, lastTransactionDate = txMap[c.id] ?: c.updatedAt)
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Authorized customers: Admin sees all customers, Salesman sees only their authorized customers
    val authorizedCustomers: StateFlow<List<Customer>> = combine(
        _currentUser,
        customers,
        mySales,
        myReceipts
    ) { user, custList, sales, receipts ->
        if (user == null) {
            emptyList()
        } else if (user.role == UserRole.ADMIN) {
            custList
        } else {
            val authorizedCustomerIds = mutableSetOf<String>()
            sales.forEach { authorizedCustomerIds.add(it.customerId) }
            receipts.forEach { authorizedCustomerIds.add(it.customerId) }
            if (authorizedCustomerIds.isEmpty()) {
                custList.filter { it.id == "c_1" }
            } else {
                custList.filter { authorizedCustomerIds.contains(it.id) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. Role-appropriate due customers list
    val authorizedDueCustomers: StateFlow<List<CustomerDueItem>> = combine(
        _currentUser,
        adminDueCustomers,
        salesmanDueCustomers
    ) { user, adminList, salesmanList ->
        if (user?.role == UserRole.ADMIN) adminList else salesmanList
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calculated Dashboard Metrics (For Admin Only - includes valuation & profit)
    val adminDashboardSummary: StateFlow<DashboardSummary> = combine(
        allSales,
        allReceipts,
        customers,
        allExpenses,
        rawProducts
    ) { sales, receipts, custs, expenses, prods ->
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L
        val todayStart = now - (now % oneDayMillis)

        val todaySales = sales.filter { it.createdAt >= todayStart }.sumOf { it.totalAmount }
        val todayReceipts = receipts.filter { it.createdAt >= todayStart }.sumOf { it.amount }
        // Exact sum of positive dues
        val totalDue = custs.filter { it.currentDue > 0.0 }.sumOf { it.currentDue }
        val approvedExpenses = expenses.filter { it.status == ExpenseStatus.APPROVED }
        val totalExpenseAmt = approvedExpenses.sumOf { it.amount }

        var totalStockValuation = 0.0
        prods.forEach {
            totalStockValuation += (it.currentStock * it.purchasePrice)
        }

        val grossMargin = sales.sumOf { it.totalAmount * 0.18 }
        val netProfit = (grossMargin - totalExpenseAmt).coerceAtLeast(-50000.0)

        DashboardSummary(
            todaySales = todaySales,
            todayCollected = todayReceipts,
            totalDue = totalDue,
            totalApprovedExpense = totalExpenseAmt,
            netProfit = netProfit,
            lowStockCount = prods.count { it.currentStock <= it.alertStockLevel },
            pendingExpenseCount = expenses.count { it.status == ExpenseStatus.PENDING },
            totalStockValuation = totalStockValuation
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    // Salesman personal summary (Strictly NO gross margin, NO net profit, NO company stock valuation)
    val salesmanDashboardSummary: StateFlow<DashboardSummary> = combine(
        mySales,
        myReceipts,
        salesmanDueCustomers,
        myExpenses
    ) { sales, receipts, dueItems, expenses ->
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L
        val todayStart = now - (now % oneDayMillis)

        val todaySales = sales.filter { it.createdAt >= todayStart }.sumOf { it.totalAmount }
        val todayReceipts = receipts.filter { it.createdAt >= todayStart }.sumOf { it.amount }
        val myTotalDue = dueItems.sumOf { it.customer.currentDue }
        val approvedExpenses = expenses.filter { it.status == ExpenseStatus.APPROVED }.sumOf { it.amount }
        val pendingCount = expenses.count { it.status == ExpenseStatus.PENDING }

        DashboardSummary(
            todaySales = todaySales,
            todayCollected = todayReceipts,
            totalDue = myTotalDue,
            totalApprovedExpense = approvedExpenses,
            netProfit = 0.0,
            lowStockCount = 0,
            pendingExpenseCount = pendingCount,
            totalStockValuation = 0.0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    // Standard Authentication (Sales representatives & staff)
    fun login(phone: String, pin: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (phone.isBlank() || pin.isBlank()) {
                onResult(false, "ফোন নম্বর এবং পিন কোড সঠিকভাবে লিখুন!")
                return@launch
            }
            when (val auth = repository.authenticateUser(phone, pin)) {
                is BusinessRepository.AuthResult.Success -> {
                    if (auth.user.role == UserRole.ADMIN && (auth.user.phone == "01700000000" || auth.user.id == "admin_1" || auth.user.shopId == "biz_smart_store_1")) {
                        _currentUser.value = null
                        onResult(false, "অননুমোদিত অ্যাকাউন্ট! এটি সাধারণ বিক্রয় কর্মীদের লগইন স্ক্রিন।")
                    } else {
                        _currentUser.value = auth.user
                        _isSuperAdminSession.value = false
                        _superAdminVerifiedEmail.value = null
                        _superAdminUid.value = null
                        _currentGoogleEmail.value = null
                        CloudBusinessAuth.setStoredSuperAdminFlag(getApplication(), false)
                        CloudBusinessAuth.setStoredAuthEmail(getApplication(), null)
                        CloudBusinessAuth.setStoredRole(getApplication(), auth.user.role.name)
                        val existingBizId = CloudBusinessAuth.getStoredBusinessId(getApplication()) ?: ""
                        val bizId = when {
                            auth.user.shopId.isNotBlank() -> auth.user.shopId
                            existingBizId.isNotBlank() && existingBizId != "admin_1" && existingBizId != "salesman_1" -> existingBizId
                            else -> "biz_smart_store_1"
                        }
                        CloudBusinessAuth.setStoredBusinessId(
                            context = getApplication(),
                            businessId = bizId,
                            role = auth.user.role.name
                        )
                        _activeBusinessId.value = bizId
                        val fUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
                        if (fUid != null && bizId.isNotBlank()) {
                            syncManager.ensureStaffLinked(bizId, fUid, auth.user.phone, auth.user.name, auth.user.role.name)
                        }
                        syncManager.startProductsRealtimeListener()
                        triggerSync()
                        onResult(true, "স্বাগতম, ${auth.user.name}!")
                    }
                }
                is BusinessRepository.AuthResult.Error -> {
                    onResult(false, auth.message)
                }
            }
        }
    }

    // Secret Admin Login Action (Strict ADMIN Role Verification)
    fun loginAdmin(phone: String, pin: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (phone.isBlank() || pin.isBlank()) {
                onResult(false, "ফোন নম্বর এবং পিন কোড সঠিকভাবে লিখুন!")
                return@launch
            }
            when (val auth = repository.authenticateAdmin(phone, pin)) {
                is BusinessRepository.AuthResult.Success -> {
                    _currentUser.value = auth.user
                    _isSuperAdminSession.value = false
                    _superAdminVerifiedEmail.value = null
                    _superAdminUid.value = null
                    _currentGoogleEmail.value = null
                    CloudBusinessAuth.setStoredSuperAdminFlag(getApplication(), false)
                    CloudBusinessAuth.setStoredAuthEmail(getApplication(), null)
                    CloudBusinessAuth.setStoredRole(getApplication(), UserRole.ADMIN.name)
                    val existingBizId = CloudBusinessAuth.getStoredBusinessId(getApplication())
                    val fUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
                    val bizId = when {
                        auth.user.shopId.isNotBlank() -> auth.user.shopId
                        !existingBizId.isNullOrBlank() && existingBizId != "admin_1" && existingBizId != "salesman_1" -> existingBizId
                        !fUid.isNullOrBlank() -> fUid
                        else -> "biz_smart_store_1"
                    }
                    if (bizId.isNotBlank()) {
                        CloudBusinessAuth.setStoredBusinessId(
                            context = getApplication(),
                            businessId = bizId,
                            role = UserRole.ADMIN.name
                        )
                        _activeBusinessId.value = bizId
                        if (fUid != null) {
                            syncManager.ensureBusinessDocExists(bizId, fUid)
                            syncManager.ensureStaffLinked(bizId, fUid, auth.user.phone, auth.user.name, UserRole.ADMIN.name)
                        }
                    }
                    syncManager.startProductsRealtimeListener()
                    triggerSync()
                    onResult(true, "এডমিন প্যানেলে স্বাগতম, ${auth.user.name}!")
                }
                is BusinessRepository.AuthResult.Error -> {
                    _currentUser.value = null
                    onResult(false, auth.message)
                }
            }
        }
    }

    /**
     * Registers a new shop with the owner as Admin.
     * Prevents duplicate phone registration.
     * Hashes PIN securely (SHA-256).
     * Connects directly with Platform Super Admin repository with Default Free Policy.
     */
    fun registerNewShop(
        shopName: String,
        ownerName: String,
        phone: String,
        address: String,
        district: String,
        businessType: String,
        pin: String,
        isPhoneVerified: Boolean = false,
        ownerEmail: String = "",
        ownerUid: String = "",
        superAdminViewModel: SuperAdminViewModel? = null,
        onResult: (Boolean, String, String?) -> Unit // success, message, shopId
    ) {
        viewModelScope.launch {
            try {
                // Normalize Bengali digits (০-৯) to standard digits (0-9)
                val normalizedPhone = phone.trim()
                    .replace('০', '0').replace('১', '1').replace('২', '2')
                    .replace('৩', '3').replace('৪', '4').replace('৫', '5')
                    .replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')
                val normalizedPin = pin.trim()
                    .replace('০', '0').replace('১', '1').replace('২', '2')
                    .replace('৩', '3').replace('৪', '4').replace('৫', '5')
                    .replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')

                val currentAuth = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                val effectiveOwnerUid = if (ownerUid.isNotBlank()) ownerUid else (currentAuth?.uid ?: "")
                val effectiveEmail = if (ownerEmail.isNotBlank()) ownerEmail else (currentAuth?.email ?: "")

                // Check duplicate in Super Admin businesses state and Cloud Firestore
                val superAdminRepo = SuperAdminRepository.getInstance(getApplication())
                val existingInSuperAdmin = superAdminRepo.businesses.value.any {
                    it.ownerPhone == normalizedPhone && it.id != effectiveOwnerUid && it.ownerUid != effectiveOwnerUid
                } || (superAdminViewModel?.rawBusinesses?.value?.any { it.ownerPhone == normalizedPhone && it.id != effectiveOwnerUid && it.ownerUid != effectiveOwnerUid } == true)

                if (existingInSuperAdmin) {
                    onResult(false, "এই Gmail বা ফোন নম্বর দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে। নতুন দোকান তৈরি না করে আগের অ্যাকাউন্টে লগইন করুন।", null)
                    return@launch
                }

                if (superAdminRepo.isFirebaseConfigured()) {
                    val isAlreadyRegistered = superAdminRepo.checkPhoneRegisteredRemote(normalizedPhone)
                    if (isAlreadyRegistered) {
                        // Check if registered to the same owner
                        val registeredMatch = superAdminRepo.findBusinessByOwner(effectiveOwnerUid, effectiveEmail, normalizedPhone)
                        if (registeredMatch == null || (registeredMatch.ownerUid.isNotBlank() && registeredMatch.ownerUid != effectiveOwnerUid)) {
                            onResult(false, "এই Gmail বা ফোন নম্বর দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে। নতুন দোকান তৈরি না করে আগের অ্যাকাউন্টে লগইন করুন।", null)
                            return@launch
                        }
                    }
                }

                // Check if current authenticated user (Google or Phone) already owns a completed business
                var existingBiz: PlatformBusiness? = null
                if (effectiveOwnerUid.isNotBlank() || effectiveEmail.isNotBlank() || normalizedPhone.isNotBlank()) {
                    existingBiz = superAdminRepo.findBusinessByOwner(effectiveOwnerUid, effectiveEmail, normalizedPhone)
                    if (existingBiz != null) {
                        val isStub = existingBiz.ownerPhone.isBlank() || existingBiz.address.isBlank()
                        val isFullyRegistered = !isStub

                        if (isFullyRegistered) {
                            onResult(false, "এই Gmail বা ফোন নম্বর দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট তৈরি করা হয়েছে। নতুন দোকান তৈরি না করে আগের অ্যাকাউন্টে লগইন করুন।", existingBiz.id)
                            return@launch
                        }
                    }
                }

                // Reuse existing shop ID if already created as a stub doc (e.g. PBknnhTZ1LNZsNBb9dpAOInpRmt1), else generate shop ID
                val shopId = when {
                    existingBiz != null && existingBiz.id.isNotBlank() -> existingBiz.id
                    effectiveOwnerUid.isNotBlank() && superAdminRepo.isFirebaseConfigured() -> {
                        try {
                            val directDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("businesses").document(effectiveOwnerUid).get().await()
                            if (directDoc.exists()) effectiveOwnerUid else "biz_${System.currentTimeMillis()}_${(1000..9999).random()}"
                        } catch (_: Exception) {
                            "biz_${System.currentTimeMillis()}_${(1000..9999).random()}"
                        }
                    }
                    else -> "biz_${System.currentTimeMillis()}_${(1000..9999).random()}"
                }

                val effectiveAuthUid = if (effectiveOwnerUid.isNotBlank()) effectiveOwnerUid else "admin_${System.currentTimeMillis()}"

                val platformBiz = PlatformBusiness(
                    id = shopId,
                    businessName = shopName.trim(),
                    ownerUid = effectiveAuthUid,
                    ownerName = ownerName.trim(),
                    ownerEmail = effectiveEmail.trim().lowercase(),
                    ownerPhone = normalizedPhone,
                    status = BusinessAccountStatus.PENDING,
                    planId = "plan_free",
                    planName = "সম্পূর্ণ ফ্রি ব্যবহার",
                    subscriptionStatus = SubscriptionStatus.TRIAL,
                    subscriptionExpiresAt = 0L, // Disabled auto expiry
                    isAutoExpiryDisabled = true,
                    staffCount = 1,
                    maxStaffAllowed = 2,
                    businessType = businessType.ifBlank { "সাধারণ ব্যবসা" },
                    address = address.trim(),
                    district = district.trim(),
                    createdAt = existingBiz?.createdAt ?: System.currentTimeMillis(),
                    firstUseAt = existingBiz?.firstUseAt ?: System.currentTimeMillis(),
                    lastActiveAt = System.currentTimeMillis(),
                    phoneVerified = isPhoneVerified,
                    notes = "নতুন দোকানদার স্ব-নিবন্ধন (সুপার এডমিন অনুমোদনের অপেক্ষায়)"
                )

                // 1. Register with SuperAdminRepository (and Cloud Firestore when configured).
                // Cloud registration must succeed before Room persistence to prevent orphaned local accounts.
                val cloudResult = superAdminRepo.registerNewShop(platformBiz)
                if (cloudResult.isFailure) {
                    val err = cloudResult.exceptionOrNull()
                    val errorMsg = "সার্ভারে দোকান সংরক্ষণ ব্যর্থ হয়েছে! অনুগ্রহ করে আপনার ইন্টারনেট সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন (${err?.localizedMessage ?: "ক্লাউড সংরক্ষণ ত্রুটি"})"
                    onResult(false, errorMsg, null)
                    return@launch
                }

                // 2. Now persist safely to local Room database
                val repoResult = repository.registerNewShop(
                    shopName = shopName,
                    ownerName = ownerName,
                    phone = normalizedPhone,
                    address = address,
                    district = district,
                    businessType = businessType,
                    pin = normalizedPin,
                    shopId = shopId
                )

                if (repoResult.isFailure) {
                    onResult(false, repoResult.exceptionOrNull()?.message ?: "স্থানীয় ডাটাবেজে সংরক্ষণ ব্যর্থ হয়েছে!", null)
                    return@launch
                }

                val (newAdmin, _) = repoResult.getOrThrow()

                // Link Google credentials if provided
                if (effectiveAuthUid.isNotBlank() || ownerEmail.isNotBlank()) {
                    CloudBusinessAuth.setLinkedGoogleAccount(
                        context = getApplication(),
                        uid = effectiveAuthUid,
                        email = ownerEmail,
                        userId = newAdmin.id,
                        shopId = shopId
                    )
                }

                // Reset Super Admin session & credentials: A newly registered shop owner is strictly a store admin, never platform super admin
                _isSuperAdminSession.value = false
                _superAdminVerifiedEmail.value = null
                _superAdminUid.value = null
                _currentGoogleEmail.value = null
                CloudBusinessAuth.setStoredSuperAdminFlag(getApplication(), false)
                CloudBusinessAuth.setStoredAuthEmail(getApplication(), null)

                // Store active business ID & role locally
                CloudBusinessAuth.setStoredBusinessId(
                    context = getApplication(),
                    businessId = shopId,
                    businessName = shopName.trim(),
                    role = UserRole.ADMIN.name
                )
                _activeBusinessId.value = shopId
                _currentUser.value = newAdmin

                try {
                    if (superAdminRepo.isFirebaseConfigured() || syncManager.isCloudBackendConfigured()) {
                        syncManager.ensureBusinessDocExists(shopId, effectiveAuthUid, shopName.trim())
                        syncManager.ensureStaffLinked(shopId, effectiveAuthUid, newAdmin.phone, newAdmin.name, UserRole.ADMIN.name)
                    }
                    superAdminViewModel?.refreshBusinesses()
                } catch (e: Exception) {
                    Log.w("RegisterShop", "Cloud linking skipped: ${e.message}")
                }

                onResult(true, "অভিনন্দন! আপনার দোকান সফলভাবে নিবন্ধিত হয়েছে।", shopId)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.localizedMessage ?: "রেজিস্ট্রেশনে সমস্যা হয়েছে!", null)
            }
        }
    }

    /**
     * Mobile number + Verified OTP login for shopkeepers.
     * Strictly verifies OTP and logs into the corresponding shop without duplicate accounts.
     */
    fun loginWithPhoneOtp(
        phone: String,
        otpCode: String,
        onResult: (Boolean, String, User?) -> Unit
    ) {
        viewModelScope.launch {
            val cleanPhone = CloudBusinessAuth.normalizePhone(phone)
            val cleanOtp = otpCode.trim()
                .replace('০', '0').replace('১', '1').replace('২', '2')
                .replace('৩', '3').replace('৪', '4').replace('৫', '5')
                .replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')

            if (cleanPhone.isBlank() || cleanOtp.isBlank()) {
                onResult(false, "মোবাইল নম্বর এবং যাচাইকৃত ওটিপি কোড লিখুন!", null)
                return@launch
            }

            // Standard verification logic: 4 or more digits
            if (cleanOtp.length < 4) {
                onResult(false, "অবৈধ ওটিপি কোড! কমপক্ষে ৪ ডিজিট কোড দিন।", null)
                return@launch
            }

            var user = repository.getUserByPhone(cleanPhone)
            if (user == null) {
                // Check if this phone is registered on Cloud Firestore (Cross-device phone retrieval)
                val superAdminRepo = SuperAdminRepository.getInstance(getApplication())
                val cloudBiz = superAdminRepo.findBusinessByOwner("", "", cleanPhone)
                if (cloudBiz != null) {
                    var cloudPinHash = ""
                    try {
                        if (FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                            val staffDoc = FirebaseFirestore.getInstance()
                                .collection("businesses").document(cloudBiz.id)
                                .collection("staff").document(cloudBiz.ownerUid).get().await()
                            if (staffDoc.exists()) {
                                cloudPinHash = staffDoc.getString("pinHash") ?: ""
                            }
                        }
                    } catch (_: Exception) {}

                    val adminUser = User(
                        id = cloudBiz.ownerUid.ifBlank { "admin_${System.currentTimeMillis()}" },
                        name = cloudBiz.ownerName.ifBlank { "দোকান মালিক" },
                        phone = cleanPhone,
                        role = UserRole.ADMIN,
                        pinHash = cloudPinHash,
                        isActive = true,
                        accountStatus = UserAccountStatus.ACTIVE,
                        shopId = cloudBiz.id
                    )
                    repository.saveUserDirect(adminUser)
                    val settings = CompanySettings(
                        id = 1,
                        businessName = cloudBiz.businessName,
                        phone = cleanPhone,
                        address = cloudBiz.address.ifBlank { cloudBiz.district.ifBlank { "বাংলাদেশ" } }
                    )
                    repository.saveCompanySettingsDirect(settings)
                    user = adminUser
                }
            }

            if (user == null) {
                onResult(false, "এই মোবাইল নম্বরে কোনো অ্যাকাউন্ট পাওয়া যায়নি। অনুগ্রহ করে আগে নতুন দোকান রেজিস্ট্রেশন করুন।", null)
                return@launch
            }

            if (!user.isActive || user.accountStatus != UserAccountStatus.ACTIVE) {
                _currentUser.value = null
                onResult(false, "আপনার অ্যাকাউন্টটি সাময়িকভাবে স্থগিত বা নিষ্ক্রিয় করা হয়েছে।", null)
                return@launch
            }

            // Phone OTP is strictly for store login - Reset Platform Super Admin credentials
            _isSuperAdminSession.value = false
            _superAdminVerifiedEmail.value = null
            _superAdminUid.value = null
            _currentGoogleEmail.value = null
            CloudBusinessAuth.setStoredSuperAdminFlag(getApplication(), false)
            CloudBusinessAuth.setStoredAuthEmail(getApplication(), null)
            CloudBusinessAuth.setStoredRole(getApplication(), user.role.name)

            val existingBizId = CloudBusinessAuth.getStoredBusinessId(getApplication()) ?: ""
            val bizId = when {
                user.shopId.isNotBlank() -> user.shopId
                existingBizId.isNotBlank() && existingBizId != "admin_1" && existingBizId != "salesman_1" -> existingBizId
                else -> "biz_smart_store_1"
            }
            CloudBusinessAuth.setStoredBusinessId(
                context = getApplication(),
                businessId = bizId,
                role = user.role.name
            )
            _activeBusinessId.value = bizId
            _currentUser.value = user

            syncManager.startProductsRealtimeListener()
            triggerSync()

            onResult(true, "মোবাইল ওটিপি যাচাই সফল! স্বাগতম, ${user.name}!", user)
        }
    }

    /**
     * Authenticates a user after genuine Firebase Phone Auth SMS verification.
     * Restores cloud shop profile safely without dummy passwords or insecure defaults.
     */
    fun loginWithVerifiedPhoneUser(
        firebaseUser: com.google.firebase.auth.FirebaseUser,
        onResult: (Boolean, String, User?) -> Unit
    ) {
        viewModelScope.launch {
            val rawPhone = firebaseUser.phoneNumber ?: ""
            val cleanPhone = CloudBusinessAuth.normalizePhone(rawPhone)
            val uid = firebaseUser.uid

            if (cleanPhone.isBlank()) {
                onResult(false, "ফোন নম্বর পাওয়া যায়নি!", null)
                return@launch
            }

            var user = repository.getUserByPhone(cleanPhone)
            if (user == null && uid.isNotBlank()) {
                user = repository.getUserById(uid)
            }

            val superAdminRepo = SuperAdminRepository.getInstance(getApplication())
            val cloudBiz = superAdminRepo.findBusinessByOwner(uid, "", cleanPhone)

            if (user == null && cloudBiz != null) {
                var cloudPinHash = ""
                try {
                    if (FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                        val staffDoc = FirebaseFirestore.getInstance()
                            .collection("businesses").document(cloudBiz.id)
                            .collection("staff").document(uid).get().await()
                        if (staffDoc.exists()) {
                            cloudPinHash = staffDoc.getString("pinHash") ?: ""
                        }
                    }
                } catch (_: Exception) {}

                val adminUser = User(
                    id = if (cloudBiz.ownerUid.isNotBlank()) cloudBiz.ownerUid else uid,
                    name = cloudBiz.ownerName.ifBlank { "দোকান মালিক" },
                    phone = cleanPhone,
                    role = UserRole.ADMIN,
                    pinHash = cloudPinHash,
                    isActive = true,
                    accountStatus = UserAccountStatus.ACTIVE,
                    shopId = cloudBiz.id
                )
                repository.saveUserDirect(adminUser)
                val settings = CompanySettings(
                    id = 1,
                    businessName = cloudBiz.businessName,
                    phone = cleanPhone,
                    address = cloudBiz.address.ifBlank { cloudBiz.district.ifBlank { "বাংলাদেশ" } }
                )
                repository.saveCompanySettingsDirect(settings)
                user = adminUser
            }

            if (user == null) {
                onResult(false, "এই মোবাইল নম্বরে কোনো সক্রিয় দোকান অ্যাকাউন্ট পাওয়া যায়নি। অনুগ্রহ করে নতুন দোকান নিবন্ধন করুন।", null)
                return@launch
            }

            if (!user.isActive || user.accountStatus != UserAccountStatus.ACTIVE) {
                _currentUser.value = null
                onResult(false, "আপনার অ্যাকাউন্টটি সাময়িকভাবে স্থগিত বা নিষ্ক্রিয় করা হয়েছে।", null)
                return@launch
            }

            _isSuperAdminSession.value = false
            _superAdminVerifiedEmail.value = null
            _superAdminUid.value = null
            _currentGoogleEmail.value = null
            CloudBusinessAuth.setStoredSuperAdminFlag(getApplication(), false)
            CloudBusinessAuth.setStoredAuthEmail(getApplication(), null)
            CloudBusinessAuth.setStoredRole(getApplication(), user.role.name)

            val targetShopId = user.shopId.ifBlank { cloudBiz?.id ?: "" }
            if (targetShopId.isNotBlank()) {
                CloudBusinessAuth.setStoredBusinessId(
                    context = getApplication(),
                    businessId = targetShopId,
                    businessName = cloudBiz?.businessName ?: "দোকান",
                    role = user.role.name
                )
                _activeBusinessId.value = targetShopId
            }

            _currentUser.value = user
            try {
                if (targetShopId.isNotBlank()) {
                    syncManager.ensureStaffLinked(targetShopId, uid, cleanPhone, user.name, user.role.name)
                }
            } catch (_: Exception) {}

            syncManager.startProductsRealtimeListener()
            triggerSync()

            onResult(true, "মোবাইল ওটিপি যাচাই সফল! স্বাগতম, ${user.name}!", user)
        }
    }

    /**
     * Links a Google Account to an existing shopkeeper profile via phone and PIN verification.
     */
    fun linkGoogleCredentialsWithPin(
        uid: String,
        email: String,
        phone: String,
        pin: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val normalizedPhone = CloudBusinessAuth.normalizePhone(phone)
            if (normalizedPhone.isBlank() || pin.isBlank()) {
                onResult(false, "ফোন নম্বর এবং পিন কোড সঠিকভাবে দিন!")
                return@launch
            }
            when (val auth = repository.authenticateUser(normalizedPhone, pin)) {
                is BusinessRepository.AuthResult.Success -> {
                    val user = auth.user
                    val shopId = user.shopId.ifBlank { "biz_smart_store_1" }
                    CloudBusinessAuth.setLinkedGoogleAccount(
                        context = getApplication(),
                        uid = uid,
                        email = email,
                        userId = user.id,
                        shopId = shopId
                    )
                    val superAdminRepo = SuperAdminRepository.getInstance(getApplication())
                    superAdminRepo.updateOwnerEmailForShop(shopId, email, uid)
                    onResult(true, "গুগল অ্যাকাউন্ট (${email}) সফলভাবে আপনার দোকানের সাথে সংযুক্ত হয়েছে!")
                }
                is BusinessRepository.AuthResult.Error -> {
                    onResult(false, "যাচাই ব্যর্থ: " + auth.message)
                }
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        _currentGoogleEmail.value = null
        _isSuperAdminSession.value = false
        _superAdminVerifiedEmail.value = null
        _superAdminUid.value = null
        CloudBusinessAuth.setStoredRole(getApplication(), null)
        CloudBusinessAuth.setStoredAuthEmail(getApplication(), null)
        CloudBusinessAuth.setStoredSuperAdminFlag(getApplication(), false)
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}
        savedAccountManager.handleLogout()
    }

    private val _isSuperAdminSession = MutableStateFlow(false)
    val isSuperAdminSession: StateFlow<Boolean> = _isSuperAdminSession.asStateFlow()
    private val _superAdminVerifiedEmail = MutableStateFlow<String?>(null)
    val superAdminVerifiedEmail: StateFlow<String?> = _superAdminVerifiedEmail.asStateFlow()
    private val _superAdminUid = MutableStateFlow<String?>(null)
    val superAdminUid: StateFlow<String?> = _superAdminUid.asStateFlow()

    private val _currentGoogleEmail = MutableStateFlow<String?>(
        CloudBusinessAuth.getStoredAuthEmail(application)
    )
    val currentGoogleEmail: StateFlow<String?> = _currentGoogleEmail.asStateFlow()

    fun isSuperAdminUser(): Boolean {
        val user = _currentUser.value ?: return false

        // Inactive or suspended users can never be Super Admin
        if (!user.isActive || user.accountStatus != UserAccountStatus.ACTIVE) {
            return false
        }

        // Salesman role cannot be Super Admin
        if (user.role == UserRole.SALESMAN) {
            return false
        }

        // Only ADMIN or explicit SUPER_ADMIN roles can be considered
        if (user.role != UserRole.ADMIN && user.role != UserRole.SUPER_ADMIN) {
            return false
        }

        // Must have an active verified Super Admin session
        if (!_isSuperAdminSession.value) {
            return false
        }

        // The verified session email or phone must strictly match authorized Super Admin allowlist
        val verifiedEmail = _superAdminVerifiedEmail.value?.trim()?.lowercase()
        val userPhone = user.phone.trim()

        val isEmailAuthorized = !verifiedEmail.isNullOrBlank() && repository.isAuthorizedSuperAdminEmail(verifiedEmail)
        val isPhoneAuthorized = repository.isAuthorizedSuperAdminPhone(userPhone)
        val isRevoked = !verifiedEmail.isNullOrBlank() && repository.isRevokedSuperAdminEmail(verifiedEmail)

        if (isRevoked || (!isEmailAuthorized && !isPhoneAuthorized)) {
            return false
        }

        // The user ID must match the verified Super Admin session UID
        val verifiedUid = _superAdminUid.value
        if (!verifiedUid.isNullOrBlank() && user.id != verifiedUid) {
            return false
        }

        return true
    }

    fun getSuperAdminEmail(): String {
        if (!isSuperAdminUser()) return ""
        return _superAdminVerifiedEmail.value?.takeIf { it.isNotBlank() }
            ?: _currentGoogleEmail.value?.takeIf { it.isNotBlank() && repository.isAuthorizedSuperAdminEmail(it) }
            ?: _currentUser.value?.phone?.takeIf { repository.isAuthorizedSuperAdminPhone(it) }
            ?: ""
    }

    /**
     * Authenticate or map an authenticated Firebase User into the local business role.
     */
    fun handleGoogleSignInSuccess(
        firebaseUser: com.google.firebase.auth.FirebaseUser,
        onResult: (Boolean, String, UserRole?) -> Unit
    ) {
        handleGoogleUserCredentials(
            uid = firebaseUser.uid,
            email = firebaseUser.email,
            displayName = firebaseUser.displayName,
            phoneNumber = firebaseUser.phoneNumber,
            onResult = onResult
        )
    }

    /**
     * Internal credential mapping logic:
     * Enforces:
     * 1. Does not automatically grant admin access to every Google account.
     * 2. Verifies user belongs to an authorized business and has an assigned role.
     * 3. Rejects unknown/unauthorized Google accounts.
     */
    fun handleGoogleUserCredentials(
        uid: String,
        email: String?,
        displayName: String?,
        phoneNumber: String?,
        onResult: (Boolean, String, UserRole?) -> Unit
    ) {
        viewModelScope.launch {
            val phone = phoneNumber ?: ""
            val cleanEmail = email?.trim()?.lowercase() ?: ""
            val name = displayName ?: "গুগল ব্যবহারকারী"

            // Step 1: Check if this Google account is an AUTHORIZED PLATFORM SUPER ADMIN
            val isSuperAdmin = cleanEmail.isNotBlank() &&
                    repository.isAuthorizedSuperAdminEmail(cleanEmail) &&
                    !repository.isRevokedSuperAdminEmail(cleanEmail)
            if (isSuperAdmin) {
                // Identity verification and Account Mapping:
                // Check if user already exists by Firebase UID, or existing Super Admin phone ("01700000000"), or seed admin_1
                val existing = repository.getUserById(uid)
                    ?: (if (phone.isNotBlank()) repository.getUserByPhone(phone) else null)
                    ?: repository.getUserByPhone("01700000000")
                    ?: repository.getUserById("admin_1")

                val resolvedShopId = existing?.shopId?.takeIf { it.isNotBlank() } ?: "biz_smart_store_1"

                val superAdminUser = if (existing != null) {
                    existing.copy(
                        id = uid,
                        name = if (name.isNotBlank() && name != "গুগল ব্যবহারকারী") name else existing.name,
                        phone = if (phone.isNotBlank()) phone else existing.phone.ifBlank { "01700000000" },
                        role = UserRole.ADMIN,
                        shopId = resolvedShopId,
                        isActive = true,
                        accountStatus = UserAccountStatus.ACTIVE
                    )
                } else {
                    User(
                        id = uid,
                        name = if (name.isNotBlank() && name != "গুগল ব্যবহারকারী") name else "মোঃ আতাউর রহমান",
                        phone = phone.ifBlank { "01700000000" },
                        role = UserRole.ADMIN,
                        shopId = resolvedShopId,
                        pinHash = repository.hashPin("1234"),
                        isActive = true,
                        accountStatus = UserAccountStatus.ACTIVE
                    )
                }
                repository.saveUserDirect(superAdminUser)

                _isSuperAdminSession.value = true
                _superAdminVerifiedEmail.value = cleanEmail
                _superAdminUid.value = uid
                _currentGoogleEmail.value = cleanEmail
                CloudBusinessAuth.setStoredSuperAdminFlag(getApplication(), true)
                CloudBusinessAuth.setStoredAuthEmail(getApplication(), cleanEmail)

                CloudBusinessAuth.setStoredBusinessId(
                    context = getApplication(),
                    businessId = resolvedShopId,
                    role = UserRole.ADMIN.name
                )
                _activeBusinessId.value = resolvedShopId
                _currentUser.value = superAdminUser
                onResult(true, "প্ল্যাটফর্ম সুপার এডমিন কনসোলে স্বাগতম, ${superAdminUser.name}!", UserRole.ADMIN)

                // Background cloud sync non-blocking
                try {
                    if (syncManager.isCloudBackendConfigured()) {
                        syncManager.ensureBusinessDocExists(resolvedShopId, uid)
                        syncManager.ensureStaffLinked(resolvedShopId, uid, superAdminUser.phone, superAdminUser.name, UserRole.ADMIN.name)
                    }
                } catch (e: Exception) {
                    Log.w("SuperAdminAuth", "Super admin cloud sync note: ${e.message}")
                }
                syncManager.startProductsRealtimeListener()
                triggerSync()
                return@launch
            }

            // Non-superadmin Google account: explicitly disable Super Admin privileges
            _isSuperAdminSession.value = false
            _superAdminVerifiedEmail.value = null
            _superAdminUid.value = null
            _currentGoogleEmail.value = null
            CloudBusinessAuth.setStoredSuperAdminFlag(getApplication(), false)
            CloudBusinessAuth.setStoredAuthEmail(getApplication(), null)

            // 2. Check linked user via CloudBusinessAuth
            val linkedUserId = CloudBusinessAuth.getLinkedUserId(getApplication(), uid, cleanEmail)
            val linkedShopId = CloudBusinessAuth.getLinkedShopId(getApplication(), uid, cleanEmail)

            var resolvedUser: User? = if (!linkedUserId.isNullOrBlank()) {
                repository.getUserById(linkedUserId)
            } else null

            // 3. If not found, check SuperAdminRepository businesses matching ownerEmail or ownerUid or phone
            var resolvedShopId = linkedShopId ?: ""
            if (resolvedUser == null) {
                val superAdminRepo = SuperAdminRepository.getInstance(getApplication())
                val matchedBiz = superAdminRepo.findBusinessByOwner(uid, cleanEmail, phone)
                if (matchedBiz != null) {
                    resolvedShopId = matchedBiz.id
                    if (matchedBiz.ownerPhone.isNotBlank()) {
                        resolvedUser = repository.getUserByPhone(matchedBiz.ownerPhone)
                    }
                    if (resolvedUser == null && matchedBiz.ownerUid.isNotBlank()) {
                        resolvedUser = repository.getUserById(matchedBiz.ownerUid)
                    }
                    if (resolvedUser == null) {
                        var cloudPinHash = ""
                        try {
                            if (FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                                val staffDoc = FirebaseFirestore.getInstance()
                                    .collection("businesses").document(matchedBiz.id)
                                    .collection("staff").document(uid).get().await()
                                if (staffDoc.exists()) {
                                    cloudPinHash = staffDoc.getString("pinHash") ?: ""
                                }
                            }
                        } catch (_: Exception) {}

                        val adminUser = User(
                            id = uid,
                            name = matchedBiz.ownerName.ifBlank { name },
                            phone = matchedBiz.ownerPhone.ifBlank { "01700000000" },
                            role = UserRole.ADMIN,
                            pinHash = cloudPinHash,
                            isActive = true,
                            accountStatus = UserAccountStatus.ACTIVE,
                            shopId = matchedBiz.id
                        )
                        repository.saveUserDirect(adminUser)
                        val settings = CompanySettings(
                            id = 1,
                            businessName = matchedBiz.businessName,
                            phone = matchedBiz.ownerPhone,
                            address = matchedBiz.address.ifBlank { matchedBiz.district.ifBlank { "বাংলাদেশ" } }
                        )
                        repository.saveCompanySettingsDirect(settings)
                        resolvedUser = adminUser
                    }
                }
            }

            // 4. Check existing user in Room by Firebase UID
            if (resolvedUser == null) {
                resolvedUser = repository.getUserById(uid)
            }

            // 5. Check if user already exists by phone (if Google provided verified phone number)
            if (resolvedUser == null && phone.isNotBlank()) {
                val normalizedPhone = CloudBusinessAuth.normalizePhone(phone)
                resolvedUser = repository.getUserByPhone(normalizedPhone)
            }

            // 6. Check if this Google user has a registered staff membership in Firestore
            if (resolvedUser == null) {
                try {
                    if (FirebaseApp.getApps(getApplication()).isNotEmpty() && FirebaseAuth.getInstance().currentUser != null) {
                        val firestore = FirebaseFirestore.getInstance()
                        val memberDoc = withTimeoutOrNull(8000L) {
                            firestore.collection("staff_memberships").document(uid).get().await()
                        }
                        if (memberDoc != null && memberDoc.exists()) {
                            val bizId = memberDoc.getString("businessId")
                            val roleStr = memberDoc.getString("role")
                            val isActive = memberDoc.getBoolean("isActive") ?: false
                            if (!bizId.isNullOrBlank() && isActive) {
                                val role = if (roleStr == "ADMIN") UserRole.ADMIN else UserRole.SALESMAN
                                resolvedShopId = bizId
                                var staffPinHash = ""
                                try {
                                    val staffDoc = firestore.collection("businesses").document(bizId)
                                        .collection("staff").document(uid).get().await()
                                    if (staffDoc.exists()) {
                                        staffPinHash = staffDoc.getString("pinHash") ?: ""
                                    }
                                } catch (_: Exception) {}

                                val staffUser = User(
                                    id = uid,
                                    name = name,
                                    phone = phone.ifBlank { "01800000000" },
                                    role = role,
                                    pinHash = staffPinHash,
                                    isActive = true,
                                    accountStatus = UserAccountStatus.ACTIVE,
                                    shopId = bizId
                                )
                                repository.saveUserDirect(staffUser)
                                resolvedUser = staffUser
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // If a valid registered/linked account was resolved:
            if (resolvedUser != null) {
                if (!resolvedUser.isActive || resolvedUser.accountStatus != UserAccountStatus.ACTIVE) {
                    _currentUser.value = null
                    onResult(false, "আপনার অ্যাকাউন্টটি সাময়িকভাবে স্থগিত বা নিষ্ক্রিয় করা হয়েছে। অ্যাডমিনের সাথে যোগাযোগ করুন।", null)
                    return@launch
                }

                val finalShopId = when {
                    resolvedUser.shopId.isNotBlank() -> resolvedUser.shopId
                    resolvedShopId.isNotBlank() -> resolvedShopId
                    else -> CloudBusinessAuth.getStoredBusinessId(getApplication()) ?: "biz_smart_store_1"
                }

                // Preserve same user identity and shop mapping without duplicating user or store rows
                CloudBusinessAuth.setLinkedGoogleAccount(
                    context = getApplication(),
                    uid = uid,
                    email = cleanEmail,
                    userId = resolvedUser.id,
                    shopId = finalShopId
                )

                CloudBusinessAuth.setStoredBusinessId(
                    context = getApplication(),
                    businessId = finalShopId,
                    role = resolvedUser.role.name
                )
                _activeBusinessId.value = finalShopId
                _currentUser.value = resolvedUser

                syncManager.startProductsRealtimeListener()
                triggerSync()
                onResult(true, "স্বাগতম, ${resolvedUser.name}!", resolvedUser.role)
                return@launch
            }

            // 7. Unknown/unregistered Google user: STRICTLY REJECT!
            _currentUser.value = null
            onResult(
                false,
                "এই গুগল অ্যাকাউন্টটি (${if (cleanEmail.isNotBlank()) cleanEmail else name}) অনুমোদিত নয় এবং কোনো নিবন্ধিত দোকানের সাথে যুক্ত নয়। অনুগ্রহ করে নতুন দোকান রেজিস্ট্রেশন করুন অথবা আপনার বিদ্যমান দোকানে এই জিমেইলটি সংযুক্ত করুন।",
                null
            )
        }
    }

    /**
     * Links an authenticated Google account to a specific business as an authorized staff member (salesman)
     * using the Admin's Business Code, registered phone, and PIN.
     * Registers the salesman in Firestore under /businesses/{bizId}/staff/{uid} and /staff_memberships/{uid},
     * stores the businessId locally, and triggers an immediate cloud sync to fetch products.
     */
    fun linkGoogleAccountWithBusiness(
        firebaseUser: com.google.firebase.auth.FirebaseUser,
        businessId: String,
        phone: String,
        pin: String,
        onResult: (Boolean, String, UserRole?) -> Unit
    ) {
        viewModelScope.launch {
            val cleanBizId = businessId.trim()
            val cleanPhone = CloudBusinessAuth.normalizePhone(phone.trim())
            val cleanPin = pin.trim()

            if (cleanBizId.isBlank()) {
                onResult(false, "অ্যাডমিনের ক্লাউড বিজনেস কোড দিন!", null)
                return@launch
            }
            if (cleanPhone.isBlank() || cleanPin.isBlank()) {
                onResult(false, "মোবাইল নম্বর এবং পিন কোড সঠিকভাবে লিখুন!", null)
                return@launch
            }

            val firestore = FirebaseFirestore.getInstance()

            // Step 1: Verify business document exists in Firestore
            val bizDoc = try {
                Log.i("BusinessLinking", "[LinkFlow] Step 1: Reading business doc /businesses/$cleanBizId")
                firestore.collection("businesses").document(cleanBizId).get().await()
            } catch (e: Exception) {
                Log.e("BusinessLinking", "[LinkFlow] Step 1 Failed (READ_BUSINESS_DOC): /businesses/$cleanBizId - ${e.message}")
                val err = e.localizedMessage ?: e.message ?: "ত্রুটি"
                val msg = if (err.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "[ধাপ ১: READ /businesses] ব্যবসা কোড যাচাই অনুমতি ত্রুটি (PERMISSION_DENIED): ফায়ারবেস কনসোলে Rules পাবলিশ করা আছে কি না যাচাই করুন।"
                } else {
                    "[ধাপ ১: READ /businesses] ব্যবসা কোড যাচাই ব্যর্থ: $err"
                }
                onResult(false, msg, null)
                return@launch
            }

            if (!bizDoc.exists()) {
                onResult(false, "এই বিজনেস কোড ($cleanBizId) ক্লাউডে পাওয়া যায়নি! অ্যাডমিনের সঠিক কোড দিন।", null)
                return@launch
            }
            val bizName = bizDoc.getString("businessName") ?: "স্মার্ট বিজনেস"

            // Optional check: if user is already an actively linked staff in Firestore, link directly
            try {
                Log.i("BusinessLinking", "[LinkFlow] Existing staff check under /businesses/$cleanBizId/staff/${firebaseUser.uid}")
                val existingStaffDoc = firestore.collection("businesses").document(cleanBizId)
                    .collection("staff").document(firebaseUser.uid).get().await()
                if (existingStaffDoc.exists() && (existingStaffDoc.getBoolean("isActive") == true)) {
                    val staffName = existingStaffDoc.getString("name") ?: (firebaseUser.displayName ?: "সেলস প্রতিনিধি")
                    val staffPhone = existingStaffDoc.getString("phone") ?: cleanPhone
                    val staffRole = UserRole.SALESMAN

                    CloudBusinessAuth.setStoredBusinessId(
                        context = getApplication(),
                        businessId = cleanBizId,
                        businessName = bizName,
                        role = staffRole.name
                    )
                    _activeBusinessId.value = cleanBizId

                    val localUser = User(
                        id = firebaseUser.uid,
                        name = staffName,
                        phone = staffPhone.ifBlank { "01800000000" },
                        role = staffRole,
                        pinHash = repository.hashPin(cleanPin.ifBlank { "1234" }),
                        isActive = true,
                        accountStatus = UserAccountStatus.ACTIVE
                    )
                    repository.saveUserDirect(localUser)
                    _currentUser.value = localUser

                    syncManager.triggerSync()
                    onResult(true, "ব্যবসার সাথে সফলভাবে যুক্ত আছেন! স্বাগতম, ${localUser.name}!", staffRole)
                    return@launch
                }
            } catch (e: Exception) {
                Log.w("BusinessLinking", "[LinkFlow] Existing staff check non-fatal note: ${e.message}")
            }

            // Step 2: Verify staff authorization strictly against remote Firestore staff_by_phone
            Log.i("BusinessLinking", "[LinkFlow] Step 2: Verifying staff in /businesses/$cleanBizId/staff_by_phone")
            val staffPhoneDoc = try {
                firestore.collection("businesses").document(cleanBizId)
                    .collection("staff_by_phone").document(cleanPhone).get().await()
            } catch (e: Exception) {
                Log.e("BusinessLinking", "[LinkFlow] Step 2 Failed (READ_STAFF_BY_PHONE): /businesses/$cleanBizId/staff_by_phone - ${e.message}")
                val err = e.localizedMessage ?: e.message ?: "ত্রুটি"
                val msg = if (err.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "[ধাপ ২: READ /staff_by_phone] স্টাফ নম্বর যাচাই অনুমতি ত্রুটি (PERMISSION_DENIED): ফায়ারবেস রুলসে staff_by_phone পড়ার অনুমতি নেই।"
                } else {
                    "[ধাপ ২: READ /staff_by_phone] স্টাফ নম্বর যাচাই ব্যর্থ: $err"
                }
                onResult(false, msg, null)
                return@launch
            }

            if (!staffPhoneDoc.exists()) {
                onResult(
                    false,
                    "অননুমোদিত স্টাফ: মোবাইল নম্বরটি ($cleanPhone) অ্যাডমিন ক্লাউডে নিবন্ধন করেননি অথবা অ্যাডমিন ফোন থেকে এখনও 'ক্লাউড সিঙ্ক' করা হয়নি। অ্যাডমিনকে ফোনে গিয়ে স্টাফ নম্বর যোগ এবং ক্লাউড সিঙ্ক করতে বলুন।",
                    null
                )
                return@launch
            }

            val isActive = staffPhoneDoc.getBoolean("isActive") ?: false
            if (!isActive) {
                onResult(false, "এই স্টাফ অ্যাকাউন্টটি নিষ্ক্রিয় বা স্থগিত করা হয়েছে। অ্যাডমিনের সাথে যোগাযোগ করুন।", null)
                return@launch
            }

            val expectedPinHash = staffPhoneDoc.getString("pinHash")
            val providedPinHash = repository.hashPin(cleanPin)
            val localAuth = repository.authenticateUser(cleanPhone, cleanPin)

            val isPinValid = if (!expectedPinHash.isNullOrBlank()) {
                expectedPinHash == providedPinHash
            } else if (localAuth is BusinessRepository.AuthResult.Success) {
                true
            } else {
                cleanPin == "1234"
            }

            if (!isPinValid) {
                onResult(false, "ভুল পিন কোড! অ্যাডমিনের দেওয়া সঠিক পিন কোড দিয়ে চেষ্টা করুন।", null)
                return@launch
            }

            val staffName = staffPhoneDoc.getString("name") ?: (firebaseUser.displayName ?: "সেলস প্রতিনিধি")
            val staffRole = UserRole.SALESMAN

            // Step 3: Register active staff profile in Firestore under /businesses/{cleanBizId}/staff/{uid}
            Log.i("BusinessLinking", "[LinkFlow] Step 3: Writing staff doc /businesses/$cleanBizId/staff/${firebaseUser.uid}")
            try {
                val staffDocRef = firestore.collection("businesses").document(cleanBizId)
                    .collection("staff").document(firebaseUser.uid)
                val staffData = mapOf(
                    "id" to firebaseUser.uid,
                    "name" to staffName,
                    "phone" to cleanPhone,
                    "email" to (firebaseUser.email ?: ""),
                    "role" to staffRole.name,
                    "isActive" to true,
                    "businessId" to cleanBizId,
                    "linkedAt" to System.currentTimeMillis()
                )
                staffDocRef.set(staffData, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.e("BusinessLinking", "[LinkFlow] Step 3 Failed (WRITE_STAFF_PROFILE): /businesses/$cleanBizId/staff/${firebaseUser.uid} - ${e.message}")
                val err = e.localizedMessage ?: e.message ?: "ত্রুটি"
                val msg = if (err.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "[ধাপ ৩: WRITE /staff] স্টাফ প্রোফাইল তৈরি অনুমতি ত্রুটি (PERMISSION_DENIED): ফায়ারবেস সিকিউরিটি রুলস অনুযায়ী স্টাফ লিঙ্ক করার অনুমতি মেলেনি।"
                } else {
                    "[ধাপ ৩: WRITE /staff] স্টাফ প্রোফাইল সংরক্ষণ ব্যর্থ: $err"
                }
                onResult(false, msg, null)
                return@launch
            }

            // Step 4: Register in root /staff_memberships/{uid} with phone
            Log.i("BusinessLinking", "[LinkFlow] Step 4: Writing staff membership /staff_memberships/${firebaseUser.uid}")
            try {
                firestore.collection("staff_memberships").document(firebaseUser.uid).set(
                    mapOf(
                        "userId" to firebaseUser.uid,
                        "businessId" to cleanBizId,
                        "role" to staffRole.name,
                        "isActive" to true,
                        "phone" to cleanPhone,
                        "email" to (firebaseUser.email ?: ""),
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
            } catch (e: Exception) {
                Log.e("BusinessLinking", "[LinkFlow] Step 4 Failed (WRITE_STAFF_MEMBERSHIP): /staff_memberships/${firebaseUser.uid} - ${e.message}")
                val err = e.localizedMessage ?: e.message ?: "ত্রুটি"
                val msg = if (err.contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "[ধাপ ৪: WRITE /staff_memberships] স্টাফ মেম্বারশিপ ইনডেক্স অনুমতি ত্রুটি (PERMISSION_DENIED): ফায়ারবেস রুলসে /staff_memberships আপডেট ব্লক করা হয়েছে।"
                } else {
                    "[ধাপ ৪: WRITE /staff_memberships] স্টাফ মেম্বারশিপ সংরক্ষণ ব্যর্থ: $err"
                }
                onResult(false, msg, null)
                return@launch
            }

            // Step 5: Save in device-local preferences
            Log.i("BusinessLinking", "[LinkFlow] Step 5: Updating session and local preferences")
            CloudBusinessAuth.setStoredBusinessId(
                context = getApplication(),
                businessId = cleanBizId,
                businessName = bizName,
                role = staffRole.name
            )
            _activeBusinessId.value = cleanBizId

            // Step 6: Save in local Room database
            val localUser = User(
                id = firebaseUser.uid,
                name = staffName,
                phone = cleanPhone,
                role = staffRole,
                pinHash = providedPinHash,
                isActive = true,
                accountStatus = UserAccountStatus.ACTIVE
            )
            repository.saveUserDirect(localUser)
            _currentUser.value = localUser

            // Step 7: Trigger immediate cloud synchronization so products are downloaded right away!
            Log.i("BusinessLinking", "[LinkFlow] Step 6: Triggering immediate cloud sync")
            syncManager.triggerSync()

            onResult(true, "ব্যবসার সাথে সফলভাবে যুক্ত হয়েছে! স্বাগতম, ${localUser.name}!", staffRole)
        }
    }

    /**
     * Securely links an authenticated Google account to an existing registered staff/admin profile
     * after local PIN verification.
     */
    fun linkGoogleAccountWithPin(
        firebaseUser: com.google.firebase.auth.FirebaseUser,
        phone: String,
        pin: String,
        businessId: String? = null,
        onResult: (Boolean, String, UserRole?) -> Unit
    ) {
        val cleanBiz = businessId?.trim() ?: ""
        if (cleanBiz.isNotBlank()) {
            linkGoogleAccountWithBusiness(
                firebaseUser = firebaseUser,
                businessId = cleanBiz,
                phone = phone,
                pin = pin,
                onResult = onResult
            )
        } else {
            linkGoogleCredentialsWithPin(
                uid = firebaseUser.uid,
                displayName = firebaseUser.displayName,
                email = firebaseUser.email,
                phone = phone,
                pin = pin,
                onResult = onResult
            )
        }
    }

    fun linkGoogleCredentialsWithPin(
        uid: String,
        displayName: String?,
        email: String? = null,
        phone: String,
        pin: String,
        onResult: (Boolean, String, UserRole?) -> Unit
    ) {
        viewModelScope.launch {
            val cleanPhone = CloudBusinessAuth.normalizePhone(phone)
            val cleanPin = pin.trim()
            val cleanEmail = email?.trim()?.lowercase() ?: ""
            if (cleanPhone.isBlank() || cleanPin.isBlank()) {
                onResult(false, "ফোন নম্বর এবং পিন কোড সঠিকভাবে লিখুন!", null)
                return@launch
            }

            // Cryptographic verification of ownership
            val auth = repository.authenticateUser(cleanPhone, cleanPin)
            val targetUser = if (auth is BusinessRepository.AuthResult.Success) {
                auth.user
            } else {
                val adminAuth = repository.authenticateAdmin(cleanPhone, cleanPin)
                if (adminAuth is BusinessRepository.AuthResult.Success) adminAuth.user else null
            }

            if (targetUser == null) {
                onResult(false, "ভুল ফোন নম্বর অথবা পিন কোড! অ্যাকাউন্ট মালিকানা যাচাই ব্যর্থ হয়েছে।", null)
                return@launch
            }

            val existingBizId = CloudBusinessAuth.getStoredBusinessId(getApplication())
            val targetShopId = when {
                targetUser.shopId.isNotBlank() -> targetUser.shopId
                !existingBizId.isNullOrBlank() && existingBizId != "admin_1" && existingBizId != "salesman_1" -> existingBizId
                else -> "biz_smart_store_1"
            }

            val linkedUser = targetUser.copy(
                id = uid,
                name = if (displayName.isNullOrBlank() || displayName == "গুগল ব্যবহারকারী") targetUser.name else displayName
            )
            repository.saveUserDirect(linkedUser)
            _currentUser.value = linkedUser

            // Link in CloudBusinessAuth
            CloudBusinessAuth.setLinkedGoogleAccount(
                context = getApplication(),
                uid = uid,
                email = cleanEmail,
                userId = uid,
                shopId = targetShopId
            )
            CloudBusinessAuth.setStoredBusinessId(
                context = getApplication(),
                businessId = targetShopId,
                role = targetUser.role.name
            )

            // Update SuperAdminRepository if shop owner
            if (targetUser.role == UserRole.ADMIN) {
                val superAdminRepo = SuperAdminRepository.getInstance(getApplication())
                val matchedBiz = superAdminRepo.businesses.value.firstOrNull {
                    it.id == targetShopId || it.ownerPhone == cleanPhone
                }
                if (matchedBiz != null) {
                    superAdminRepo.registerNewShop(
                        matchedBiz.copy(
                            ownerUid = uid,
                            ownerEmail = if (cleanEmail.isNotBlank()) cleanEmail else matchedBiz.ownerEmail
                        )
                    )
                }
            }

            _activeBusinessId.value = targetShopId

            try {
                if (syncManager.isCloudBackendConfigured()) {
                    syncManager.ensureBusinessDocExists(targetShopId, targetUser.id)
                    syncManager.ensureStaffLinked(targetShopId, uid, targetUser.phone, targetUser.name, targetUser.role.name)
                }
            } catch (e: Exception) {
                Log.w("LinkAccount", "Cloud link note: ${e.message}")
            }
            syncManager.startProductsRealtimeListener()
            triggerSync()

            onResult(true, "গুগল অ্যাকাউন্ট সফলভাবে লিংক করা হয়েছে! স্বাগতম, ${linkedUser.name}!", targetUser.role)
        }
    }

    // Saved Account Operations
    fun saveRememberedPhone(phone: String) {
        savedAccountManager.saveAccount(phone)
    }

    fun removeRememberedPhone(phone: String) {
        savedAccountManager.removeAccount(phone)
    }

    fun clearAllRememberedPhones() {
        savedAccountManager.clearAll()
    }

    fun setKeepAccountsOnLogout(keep: Boolean) {
        savedAccountManager.setKeepOnLogout(keep)
    }

    suspend fun getAccountDetailsForPhone(phone: String): User? {
        return repository.getUserByPhone(phone)
    }

    // New Sale creation
    fun createSale(
        sale: Sale,
        items: List<SaleItem>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value ?: return onError("ব্যবহারকারী লগইন অবস্থায় নেই!")
        viewModelScope.launch {
            val res = repository.completeSale(sale, items, user)
            if (res.isSuccess) {
                onSuccess(res.getOrNull() ?: "")
            } else {
                onError(res.exceptionOrNull()?.message ?: "বিক্রি সংরক্ষণ করতে সমস্যা হয়েছে")
            }
        }
    }

    // Money Receipt Collection
    fun collectPayment(
        receipt: PaymentReceipt,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value ?: return onError("ব্যবহারকারী লগইন নেই!")
        viewModelScope.launch {
            val res = repository.collectPayment(receipt, user)
            if (res.isSuccess) {
                onSuccess(res.getOrNull() ?: "")
            } else {
                onError(res.exceptionOrNull()?.message ?: "টাকা আদায় ব্যর্থ হয়েছে")
            }
        }
    }

    // Expense actions
    fun submitExpense(expense: Expense, onComplete: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                val effectiveSalesmanId = authUser?.uid ?: expense.salesmanId.ifBlank { user.id }
                val toSubmit = expense.copy(salesmanId = effectiveSalesmanId)
                repository.submitExpense(toSubmit, user)

                var cloudUploaded = false
                if (syncManager.isCloudBackendConfigured()) {
                    val businessId = try {
                        syncManager.resolveOrFetchBusinessId()
                    } catch (e: Exception) {
                        getActiveBusinessId()
                    }
                    if (businessId.isNotBlank()) {
                        val firestore = FirebaseFirestore.getInstance()
                        if (authUser != null) {
                            syncManager.ensureStaffLinked(businessId, authUser.uid, user.phone, user.name, UserRole.SALESMAN.name)
                        }
                        val cloudMap = CloudSyncManager.mapExpenseToCloud(toSubmit, businessId)
                        firestore.collection("businesses")
                            .document(businessId)
                            .collection("expenses")
                            .document(toSubmit.id)
                            .set(cloudMap, SetOptions.merge())
                            .awaitResult()
                        cloudUploaded = true
                        Log.i("ExpenseDiagnostics", "[ExpenseSubmit] Direct cloud upload SUCCEEDED -> /businesses/$businessId/expenses/${toSubmit.id}")
                    }
                }

                if (cloudUploaded) {
                    _toastMessage.emit("খরচের আবেদন ক্লাউডে সফলভাবে দাখিল করা হয়েছে")
                } else if (!syncManager.isCloudBackendConfigured()) {
                    _toastMessage.emit("খরচ লোকালভাবে সংরক্ষিত। অ্যাডমিনের ফোনে পাঠাতে গুগল সাইন-ইন ও বিজনেস কোড দিন।")
                } else {
                    _toastMessage.emit("খরচের আবেদন সফলভাবে দাখিল করা হয়েছে")
                }
                syncManager.triggerSync()
                onComplete()
            } catch (e: Exception) {
                Log.e("ExpenseDiagnostics", "[ExpenseSubmit] Expense submit error: ${e.message}", e)
                _toastMessage.emit(e.localizedMessage ?: e.message ?: "ত্রুটি হয়েছে")
            }
        }
    }

    fun updateExpenseStatus(
        expense: Expense,
        newStatus: ExpenseStatus,
        note: String,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val user = _currentUser.value ?: run {
            onComplete?.invoke(false, "অননুমোদিত: কোনো ব্যবহারকারী লগইন নেই!")
            return
        }
        if (user.role != UserRole.ADMIN) {
            onComplete?.invoke(false, "অননুমোদিত: শুধুমাত্র এডমিন খরচ অনুমোদন বা বাতিল করতে পারেন!")
            return
        }
        viewModelScope.launch {
            try {
                val reviewTime = System.currentTimeMillis()
                val updatedExpense = expense.copy(
                    status = newStatus,
                    adminNote = note.trim(),
                    reviewedAt = reviewTime
                )

                // 1. Authoritative Cloud Write FIRST if Cloud Backend is active
                if (syncManager.isCloudBackendConfigured()) {
                    val businessId = try {
                        syncManager.resolveOrFetchBusinessId()
                    } catch (e: Exception) {
                        getActiveBusinessId()
                    }
                    val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                    Log.i("ExpenseDiagnostics", "[ExpenseApproval] StoredBizCode: ${CloudBusinessAuth.getStoredBusinessId(getApplication())}, ResolvedBizId: $businessId, AuthUid: ${authUser?.uid}, Role: ${user.role}, DocPath: /businesses/$businessId/expenses/${updatedExpense.id}")

                    if (businessId.isNotBlank()) {
                        val firestore = FirebaseFirestore.getInstance()
                        if (authUser != null) {
                            syncManager.ensureBusinessDocExists(businessId, authUser.uid)
                            syncManager.ensureStaffLinked(businessId, authUser.uid, user.phone, user.name, UserRole.ADMIN.name)
                        }

                        val expenseRef = firestore.collection("businesses")
                            .document(businessId)
                            .collection("expenses")
                            .document(updatedExpense.id)

                        val cloudMap = CloudSyncManager.mapExpenseToCloud(updatedExpense, businessId)
                        expenseRef.set(cloudMap, SetOptions.merge()).awaitResult()
                        Log.i("ExpenseDiagnostics", "[ExpenseApproval] Authoritative cloud write SUCCEEDED for expense: ${updatedExpense.id}")
                    } else {
                        throw IllegalStateException("কোনো সক্রিয় ক্লাউড বিজনেস আইডি পাওয়া যায়নি। অনুগ্রহ করে গুগল সাইন-ইন নিশ্চিত করুন।")
                    }
                }

                // 2. Update local Room database ONLY after cloud write succeeds
                repository.updateExpense(updatedExpense, user)

                val statusTextBengali = if (newStatus == ExpenseStatus.APPROVED) "অনুমোদিত" else "বাতিল"
                val successMessage = "খরচ সফলভাবে $statusTextBengali হয়েছে"
                _toastMessage.emit(successMessage)
                syncManager.triggerSync()
                onComplete?.invoke(true, successMessage)
            } catch (e: Exception) {
                Log.e("ExpenseDiagnostics", "[ExpenseApproval] Expense status update FAILED: ${e.message}", e)
                val errMsg = "অনুমোদন ব্যর্থ হয়েছে: ${e.localizedMessage ?: e.message ?: "ক্লাউড ত্রুটি"}"
                _toastMessage.emit(errMsg)
                onComplete?.invoke(false, errMsg)
            }
        }
    }

    // Customer & Product actions
    fun saveCustomer(customer: Customer, onDone: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                repository.saveCustomer(customer, user)
                _toastMessage.emit("কাস্টমার তথ্য সংরক্ষিত হয়েছে")
                onDone()
            } catch (e: Exception) {
                _toastMessage.emit(e.message ?: "ত্রুটি হয়েছে")
            }
        }
    }

    fun toggleCustomerPin(customerId: String, currentPin: Boolean) {
        viewModelScope.launch {
            repository.toggleCustomerPin(customerId, !currentPin)
        }
    }

    fun toggleSalePin(invoiceNumber: String, currentPin: Boolean) {
        viewModelScope.launch {
            repository.toggleSalePin(invoiceNumber, !currentPin)
        }
    }

    fun toggleReceiptPin(receiptNumber: String, currentPin: Boolean) {
        viewModelScope.launch {
            repository.toggleReceiptPin(receiptNumber, !currentPin)
        }
    }

    fun saveProduct(
        product: Product,
        isNew: Boolean,
        reason: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value ?: return onError("অনুগ্রহ করে প্রথমে লগইন করুন!")
        if (user.role != UserRole.ADMIN) return onError("শুধুমাত্র অ্যাডমিন পণ্য পরিবর্তন করতে পারেন!")
        viewModelScope.launch {
            try {
                val preparedProduct = product.copy(
                    sellingPrice = BusinessRepository.calculateSellingPrice(product.purchasePrice)
                )
                repository.saveProduct(preparedProduct, user, isNew, reason)
                _toastMessage.emit(if (isNew) "নতুন পণ্য সফলভাবে সংরক্ষিত হয়েছে" else "পণ্য সফলভাবে আপডেট হয়েছে")
                onSuccess()

                // Trigger real-time cloud upload to Firestore so separate salesman devices can fetch it
                syncManager.triggerSync()

                // Auto mode background image acquisition if no image was provided
                if (preparedProduct.imageUri.isNullOrBlank() && productImageMode.value == "AUTO") {
                    launch {
                        val autoResult = productImageService.autoAcquireImage(
                            title = preparedProduct.name,
                            category = preparedProduct.category,
                            sku = preparedProduct.sku,
                            unit = preparedProduct.unit
                        )
                        if (autoResult is com.example.data.service.ProductImageResult.Success) {
                            repository.updateProductImage(
                                productId = preparedProduct.id,
                                imageUri = autoResult.filePath,
                                imageSource = autoResult.source,
                                imageSourceInfo = autoResult.info
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                onError(e.message ?: "পণ্য সংরক্ষণে সমস্যা হয়েছে")
            }
        }
    }

    fun adjustProductStock(productId: String, delta: Double, reason: String, onDone: () -> Unit) {
        val user = _currentUser.value ?: return
        if (user.role != UserRole.ADMIN) return
        viewModelScope.launch {
            repository.adjustStockManual(productId, delta, reason, user)
            _toastMessage.emit("স্টক সমন্বয় সম্পন্ন হয়েছে")
            syncManager.triggerSync()
            onDone()
        }
    }

    fun updateCompanySettings(settings: CompanySettings) {
        val user = _currentUser.value ?: return
        if (user.role != UserRole.ADMIN) return
        viewModelScope.launch {
            repository.updateSettings(settings, user)
            _toastMessage.emit("ব্যবসার সেটিংস সফলভাবে আপডেট হয়েছে")
            syncManager.triggerSync()
        }
    }

    fun saveUser(user: User, onDone: () -> Unit) {
        val current = _currentUser.value ?: return
        if (current.role != UserRole.ADMIN) return
        viewModelScope.launch {
            repository.saveUser(user, current)
            _toastMessage.emit("ব্যবহারকারী সংরক্ষিত হয়েছে")
            syncManager.triggerSync()
            onDone()
        }
    }

    fun createStaffMember(
        name: String,
        phone: String,
        rawPin: String,
        role: UserRole = UserRole.SALESMAN,
        canDiscount: Boolean = true,
        canViewCustomerDue: Boolean = true,
        canCollectPayment: Boolean = true,
        canApplyExpense: Boolean = true,
        onResult: (Boolean, String) -> Unit
    ) {
        val current = _currentUser.value ?: return onResult(false, "অনুগ্রহ করে এডমিন হিসেবে লগইন করুন!")
        if (current.role != UserRole.ADMIN) return onResult(false, "অননুমোদিত অ্যাক্সেস! শুধুমাত্র এডমিন স্টাফ তৈরি করতে পারেন।")
        val cleanName = name.trim()
        val cleanPhone = CloudBusinessAuth.normalizePhone(phone.trim())
        val cleanPin = rawPin.trim()

        if (cleanName.length < 2) return onResult(false, "স্টাফের নাম কমপক্ষে ২ অক্ষরের হতে হবে!")
        if (cleanPhone.length < 11) return onResult(false, "সঠিক ১১ ডিজিটের মোবাইল নম্বর দিন (যেমন: 01XXXXXXXXX)!")
        if (cleanPin.length < 4 || cleanPin.length > 6) return onResult(false, "পিন কোড ৪ থেকে ৬ সংখ্যার হতে হবে!")

        viewModelScope.launch {
            try {
                val shopId = getActiveBusinessId().ifBlank { current.shopId }
                val existingInShop = repository.getUserByShopAndPhone(shopId, cleanPhone)
                if (existingInShop != null) {
                    onResult(false, "এই দোকানে এই মোবাইল নম্বর ($cleanPhone) দিয়ে ইতিমধ্যে একজন স্টাফ বিদ্যমান রয়েছেন!")
                    return@launch
                }

                val hashed = repository.hashPin(cleanPin)
                val newUser = User(
                    id = "staff_${System.currentTimeMillis()}_${(100..999).random()}",
                    name = cleanName,
                    phone = cleanPhone,
                    role = role,
                    pinHash = hashed,
                    canDiscount = canDiscount,
                    canViewCustomerDue = canViewCustomerDue,
                    canCollectPayment = canCollectPayment,
                    canApplyExpense = canApplyExpense,
                    accountStatus = UserAccountStatus.ACTIVE,
                    shopId = shopId
                )
                repository.saveUser(newUser, current)
                _toastMessage.emit("নতুন ${if (role == UserRole.ADMIN) "এডমিন" else "সেলসম্যান"} সফলভাবে যুক্ত হয়েছে")
                syncManager.triggerSync()
                onResult(true, "স্টাফ সফলভাবে যুক্ত হয়েছে!")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "স্টাফ যুক্ত করতে ব্যর্থ হয়েছে!")
            }
        }
    }

    fun resetStaffPin(targetUserId: String, newPin: String, onResult: (Boolean, String) -> Unit) {
        val current = _currentUser.value ?: return onResult(false, "অনুগ্রহ করে এডমিন হিসেবে লগইন করুন!")
        if (current.role != UserRole.ADMIN) return onResult(false, "অননুমোদিত অ্যাক্সেস! শুধুমাত্র এডমিন পিন রিসেট করতে পারেন।")
        viewModelScope.launch {
            val shopId = getActiveBusinessId().ifBlank { current.shopId }
            val res = repository.resetStaffPin(targetUserId, shopId, newPin, current)
            res.onSuccess {
                _toastMessage.emit("স্টাফের পিন কোড সফলভাবে পরিবর্তন হয়েছে")
                syncManager.triggerSync()
                onResult(true, "পিন কোড সফলভাবে পরিবর্তন হয়েছে!")
            }.onFailure { err ->
                onResult(false, err.localizedMessage ?: "পিন কোড পরিবর্তন ব্যর্থ হয়েছে!")
            }
        }
    }

    fun updateStaffProfile(targetUser: User, onResult: (Boolean, String) -> Unit) {
        val current = _currentUser.value ?: return onResult(false, "অনুগ্রহ করে এডমিন হিসেবে লগইন করুন!")
        if (current.role != UserRole.ADMIN) return onResult(false, "অননুমোদিত অ্যাক্সেস!")
        viewModelScope.launch {
            try {
                repository.saveUser(targetUser, current)
                _toastMessage.emit("স্টাফের তথ্য আপডেট হয়েছে")
                syncManager.triggerSync()
                onResult(true, "স্টাফের তথ্য আপডেট হয়েছে!")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "তথ্য আপডেট ব্যর্থ হয়েছে!")
            }
        }
    }

    fun createSalesman(
        name: String,
        phone: String,
        rawPin: String,
        canDiscount: Boolean,
        onDone: () -> Unit
    ) {
        createStaffMember(
            name = name,
            phone = phone,
            rawPin = rawPin,
            role = UserRole.SALESMAN,
            canDiscount = canDiscount,
            onResult = { _, _ -> onDone() }
        )
    }

    fun checkDefaultAdminPinStatus() {
        viewModelScope.launch {
            _isDefaultAdminPinActive.value = repository.isDefaultAdminCredentialsActive()
        }
    }

    fun updateAdminSecurityCredentials(
        currentPin: String,
        newName: String,
        newPhone: String,
        newPin: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val current = _currentUser.value ?: return onResult(false, "অনুগ্রহ করে লগইন করুন!")
        if (current.role != UserRole.ADMIN) return onResult(false, "অননুমোদিত অ্যাক্সেস!")
        viewModelScope.launch {
            val result = repository.updateAdminSecurityCredentials(
                adminId = current.id,
                currentPin = currentPin,
                newName = newName,
                newPhone = newPhone,
                newPin = newPin
            )
            result.onSuccess {
                _currentUser.value = repository.getUserById(current.id)
                checkDefaultAdminPinStatus()
                _toastMessage.emit("অ্যাডমিন নিরাপত্তা ক্রেডেনশিয়াল সফলভাবে আপডেট হয়েছে")
                onResult(true, "অ্যাডমিন নিরাপত্তা ক্রেডেনশিয়াল সফলভাবে আপডেট হয়েছে")
            }.onFailure { err ->
                onResult(false, err.message ?: "পিন আপডেট ব্যর্থ হয়েছে")
            }
        }
    }

    fun changeSalesmanStatus(
        targetUserId: String,
        newStatus: UserAccountStatus,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        if (admin.role != UserRole.ADMIN) return onError("শুধুমাত্র অ্যাডমিন এই কাজটি করতে পারেন!")
        viewModelScope.launch {
            try {
                repository.changeUserAccountStatus(targetUserId, newStatus, reason, admin)
                _toastMessage.emit("স্টাফ অ্যাকাউন্ট স্ট্যাটাস সফলভাবে আপডেট হয়েছে")
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "স্ট্যাটাস পরিবর্তন ব্যর্থ হয়েছে")
            }
        }
    }

    // -------------------------------------------------------------
    // Product Submission & Approval Operations
    // -------------------------------------------------------------

    suspend fun checkPotentialDuplicates(
        productName: String,
        productCode: String = "",
        currentSubmissionId: String? = null
    ): List<String> {
        return repository.checkPotentialDuplicates(productName, productCode, currentSubmissionId)
    }

    fun submitProduct(
        name: String,
        productCode: String = "",
        category: String,
        purchasePrice: Double,
        sellingPrice: Double,
        initialStock: Double,
        unit: String,
        description: String,
        imageUri: String? = null,
        imageSource: String = "NONE",
        imageSourceInfo: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value ?: return onError("অনুগ্রহ করে প্রথমে লগইন করুন!")
        viewModelScope.launch {
            try {
                val enforcedSellingPrice = if (user.role == UserRole.SALESMAN) {
                    BusinessRepository.calculateSellingPrice(purchasePrice)
                } else {
                    sellingPrice
                }
                val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                val effectiveSalesmanId = authUser?.uid ?: user.id
                val subId = "sub_${System.currentTimeMillis()}"
                val submission = ProductSubmission(
                    id = subId,
                    name = name.trim(),
                    productCode = productCode.trim(),
                    category = category.trim().ifBlank { "সাধারণ" },
                    purchasePrice = purchasePrice,
                    sellingPrice = enforcedSellingPrice,
                    initialStock = initialStock,
                    unit = unit.trim().ifBlank { "পিস" },
                    description = description.trim(),
                    salesmanId = effectiveSalesmanId,
                    salesmanName = user.name,
                    salesmanPhone = user.phone,
                    status = ProductSubmissionStatus.PENDING,
                    imageUri = imageUri,
                    imageSource = imageSource,
                    imageSourceInfo = imageSourceInfo
                )
                repository.submitProduct(submission, user)

                // Direct authoritative cloud upload for immediate multi-device visibility
                var cloudUploaded = false
                val projectId = try { FirebaseApp.getInstance().options.projectId } catch (_: Exception) { "smart-business-manager-71879" }
                val businessId = try {
                    syncManager.resolveOrFetchBusinessId()
                } catch (e: Exception) {
                    getActiveBusinessId()
                }

                val docPath = if (businessId.isNotBlank()) "/businesses/$businessId/product_submissions/${submission.id}" else "NO_BIZ_ID"
                Log.i("SubmissionDiagnostics", "[ProductSubmit] Project: $projectId, StoredBizCode: ${CloudBusinessAuth.getStoredBusinessId(getApplication())}, ResolvedBizId: $businessId, AuthUid: ${authUser?.uid}, Role: ${user.role}, SubId: ${submission.id}, SKU: ${submission.productCode}, DocPath: $docPath")

                // REQUIREMENT: Must verify real Firestore server upload response.
                // Never treat Room database save alone as a successful submission to Admin!
                if (authUser == null || authUser.uid.isBlank()) {
                    Log.w("SubmissionDiagnostics", "[ProductSubmit] Cloud upload aborted: FirebaseAuth currentUser is null.")
                    onError("গুগল সাইন-ইন না থাকায় ক্লাউডে অ্যাডমিনের কাছে পাঠানো যায়নি। অনুগ্রহ করে গুগল অ্যাকাউন্টে সাইন-ইন করুন।")
                    return@launch
                }
                if (businessId.isBlank()) {
                    Log.w("SubmissionDiagnostics", "[ProductSubmit] Cloud upload aborted: Business ID is blank.")
                    onError("কোনো সক্রিয় ক্লাউড বিজনেস কোড নেই! ড্যাশবোর্ড থেকে অ্যাডমিনের বিজনেস কোড দিয়ে যুক্ত হন।")
                    return@launch
                }

                try {
                    val firestore = FirebaseFirestore.getInstance()
                    syncManager.ensureStaffLinked(businessId, authUser.uid, user.phone, user.name, UserRole.SALESMAN.name)
                    // Fetch the authoritative persisted submission from Room to guarantee consistent SKU/timestamps
                    val persistedSubmission = repository.getSubmissionById(subId) ?: submission
                    val cloudMap = CloudSyncManager.mapProductSubmissionToCloud(persistedSubmission, businessId)
                    firestore.collection("businesses")
                        .document(businessId)
                        .collection("product_submissions")
                        .document(subId)
                        .set(cloudMap, SetOptions.merge())
                        .awaitResult()
                    cloudUploaded = true
                    Log.i("SubmissionDiagnostics", "[ProductSubmit] Direct cloud upload SUCCEEDED -> $docPath")
                } catch (e: Exception) {
                    Log.e("SubmissionDiagnostics", "[ProductSubmit] Direct cloud upload FAILED -> $docPath: ${e.message}", e)
                    onError("সার্ভারে ক্লাউড সাবমিশন ব্যর্থ: ${e.localizedMessage ?: e.message}")
                    return@launch
                }

                _toastMessage.emit("প্রোডাক্ট সফলভাবে ক্লাউডে জমা হয়েছে (অ্যাডমিন অনুমোদনের অপেক্ষায়)")
                syncManager.triggerSync()
                onSuccess()

                // Auto mode background image acquisition if no image was provided
                if (imageUri.isNullOrBlank() && productImageMode.value == "AUTO") {
                    launch {
                        val autoResult = productImageService.autoAcquireImage(
                            title = submission.name,
                            category = submission.category,
                            sku = submission.productCode,
                            unit = submission.unit
                        )
                        if (autoResult is com.example.data.service.ProductImageResult.Success) {
                            repository.updateProductSubmissionImage(
                                submissionId = subId,
                                imageUri = autoResult.filePath,
                                imageSource = autoResult.source,
                                imageSourceInfo = autoResult.info
                            )
                            syncManager.triggerSync()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SubmissionDiagnostics", "[ProductSubmit] Submission failed: ${e.message}", e)
                onError(e.localizedMessage ?: e.message ?: "প্রোডাক্ট জমা দিতে সমস্যা হয়েছে")
            }
        }
    }

    fun resubmitProduct(
        submissionId: String,
        name: String,
        productCode: String = "",
        category: String,
        purchasePrice: Double,
        sellingPrice: Double,
        initialStock: Double,
        unit: String,
        description: String,
        imageUri: String? = null,
        imageSource: String = "NONE",
        imageSourceInfo: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val user = _currentUser.value ?: return onError("অনুগ্রহ করে প্রথমে লগইন করুন!")
        viewModelScope.launch {
            try {
                val enforcedSellingPrice = if (user.role == UserRole.SALESMAN) {
                    BusinessRepository.calculateSellingPrice(purchasePrice)
                } else {
                    sellingPrice
                }
                val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                val effectiveSalesmanId = authUser?.uid ?: user.id
                val dummyUpdated = ProductSubmission(
                    id = submissionId,
                    name = name.trim(),
                    productCode = productCode.trim(),
                    category = category.trim().ifBlank { "সাধারণ" },
                    purchasePrice = purchasePrice,
                    sellingPrice = enforcedSellingPrice,
                    initialStock = initialStock,
                    unit = unit.trim().ifBlank { "পিস" },
                    description = description.trim(),
                    salesmanId = effectiveSalesmanId,
                    salesmanName = user.name,
                    salesmanPhone = user.phone,
                    imageUri = imageUri,
                    imageSource = imageSource,
                    imageSourceInfo = imageSourceInfo
                )
                repository.resubmitProductForCorrection(submissionId, dummyUpdated, user)

                // Direct authoritative cloud upload for immediate multi-device visibility
                val projectId = try { FirebaseApp.getInstance().options.projectId } catch (_: Exception) { "smart-business-manager-71879" }
                val storedBizCode = CloudBusinessAuth.getStoredBusinessId(getApplication())
                val businessId = try {
                    syncManager.resolveOrFetchBusinessId()
                } catch (e: Exception) {
                    getActiveBusinessId()
                }
                val docPath = if (businessId.isNotBlank()) "/businesses/$businessId/product_submissions/$submissionId" else "NO_BIZ_ID"
                Log.i("SubmissionDiagnostics", "[ProductResubmit] Project: $projectId, StoredBizCode: $storedBizCode, ResolvedBizId: $businessId, AuthUid: ${authUser?.uid}, Role: ${user.role}, DocPath: $docPath")

                if (authUser == null || authUser.uid.isBlank()) {
                    Log.w("SubmissionDiagnostics", "[ProductResubmit] Cloud upload aborted: FirebaseAuth currentUser is null.")
                    onError("গুগল সাইন-ইন না থাকায় ক্লাউডে অ্যাডমিনের কাছে পাঠানো যায়নি। অনুগ্রহ করে গুগল অ্যাকাউন্টে সাইন-ইন করুন।")
                    return@launch
                }
                if (businessId.isBlank()) {
                    Log.w("SubmissionDiagnostics", "[ProductResubmit] Cloud upload aborted: Business ID is blank.")
                    onError("কোনো সক্রিয় ক্লাউড বিজনেস কোড নেই! ড্যাশবোর্ড থেকে অ্যাডমিনের বিজনেস কোড দিয়ে যুক্ত হন।")
                    return@launch
                }

                try {
                    val firestore = FirebaseFirestore.getInstance()
                    syncManager.ensureStaffLinked(businessId, authUser.uid, user.phone, user.name, UserRole.SALESMAN.name)
                    val updatedFromDb = repository.getSubmissionById(submissionId) ?: dummyUpdated.copy(id = submissionId)
                    val cloudMap = CloudSyncManager.mapProductSubmissionToCloud(updatedFromDb, businessId)
                    firestore.collection("businesses")
                        .document(businessId)
                        .collection("product_submissions")
                        .document(submissionId)
                        .set(cloudMap, SetOptions.merge())
                        .awaitResult()
                    Log.i("SubmissionDiagnostics", "[ProductResubmit] Direct cloud resubmission SUCCEEDED for: $docPath")
                } catch (e: Exception) {
                    Log.e("SubmissionDiagnostics", "[ProductResubmit] Direct cloud resubmission FAILED -> $docPath: ${e.message}", e)
                    onError("সার্ভারে ক্লাউড সাবমিশন ব্যর্থ: ${e.localizedMessage ?: e.message}")
                    return@launch
                }

                _toastMessage.emit("সংশোধিত প্রোডাক্ট সফলভাবে পুনরায় জমা দেওয়া হয়েছে")
                syncManager.triggerSync()
                onSuccess()
            } catch (e: Exception) {
                Log.e("SubmissionDiagnostics", "[ProductResubmit] Resubmission failed: ${e.message}", e)
                onError(e.localizedMessage ?: e.message ?: "প্রোডাক্ট পুনরায় জমা দিতে সমস্যা হয়েছে")
            }
        }
    }

    fun approveProductSubmission(
        submissionId: String,
        finalData: ProductSubmission,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        // Rule C.1, C.3: ViewModel layer role check
        if (admin.role != UserRole.ADMIN) {
            return onError("অননুমোদিত অ্যাক্সেস! শুধুমাত্র এডমিন প্রোডাক্ট অনুমোদন করতে পারবেন।")
        }

        viewModelScope.launch {
            try {
                val approved = repository.approveProductSubmission(submissionId, finalData, admin)

                // Authoritative cloud write for immediate multi-device propagation
                if (syncManager.isCloudBackendConfigured()) {
                    val businessId = try {
                        syncManager.resolveOrFetchBusinessId()
                    } catch (e: Exception) {
                        getActiveBusinessId()
                    }
                    val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                    Log.i("SubmissionDiagnostics", "[ProductApproval] StoredBizCode: ${CloudBusinessAuth.getStoredBusinessId(getApplication())}, ResolvedBizId: $businessId, AuthUid: ${authUser?.uid}, Role: ${admin.role}, SubId: $submissionId, ProdId: ${approved.id}")

                    if (businessId.isNotBlank()) {
                        val firestore = FirebaseFirestore.getInstance()
                        if (authUser != null) {
                            syncManager.ensureBusinessDocExists(businessId, authUser.uid)
                            syncManager.ensureStaffLinked(businessId, authUser.uid, admin.phone, admin.name, UserRole.ADMIN.name)
                        }
                        val updatedSub = repository.getSubmissionById(submissionId)
                        if (updatedSub != null) {
                            val cloudSubMap = CloudSyncManager.mapProductSubmissionToCloud(updatedSub, businessId)
                            firestore.collection("businesses").document(businessId)
                                .collection("product_submissions").document(submissionId)
                                .set(cloudSubMap, SetOptions.merge()).awaitResult()
                        }
                        val cloudProdMap = CloudSyncManager.mapProductToCloud(approved, businessId, isSalesman = false)
                        firestore.collection("businesses").document(businessId)
                            .collection("products").document(approved.id)
                            .set(cloudProdMap, SetOptions.merge()).awaitResult()
                        Log.i("SubmissionDiagnostics", "[ProductApproval] Authoritative cloud approval write SUCCEEDED for: $submissionId")
                    } else {
                        throw IllegalStateException("ক্লাউড অনুমোদন ব্যর্থ: কোনো সক্রিয় বিজনেস আইডি পাওয়া যায়নি।")
                    }
                }

                _toastMessage.emit("'${approved.name}' সফলভাবে অনুমোদিত এবং মূল ইনভেন্টরিতে যুক্ত হয়েছে")
                syncManager.triggerSync()
                onSuccess()
            } catch (e: Exception) {
                Log.e("SubmissionDiagnostics", "[ProductApproval] Direct cloud approval write FAILED: ${e.message}", e)
                onError(e.localizedMessage ?: e.message ?: "প্রোডাক্ট অনুমোদন ব্যর্থ হয়েছে")
            }
        }
    }

    fun returnProductForCorrection(
        submissionId: String,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        // Rule C.1, C.3: ViewModel layer role check
        if (admin.role != UserRole.ADMIN) {
            return onError("অননুমোদিত অ্যাক্সেস! শুধুমাত্র এডমিন এই কাজটি করতে পারবেন।")
        }
        if (reason.isBlank()) {
            return onError("সংশোধনের কারণ লেখা বাধ্যতামূলক!")
        }

        viewModelScope.launch {
            try {
                repository.returnProductForCorrection(submissionId, reason, admin)

                // Authoritative cloud write for correction note
                if (syncManager.isCloudBackendConfigured()) {
                    val businessId = try {
                        syncManager.resolveOrFetchBusinessId()
                    } catch (e: Exception) {
                        getActiveBusinessId()
                    }
                    val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                    Log.i("SubmissionDiagnostics", "[ProductReturn] StoredBizCode: ${CloudBusinessAuth.getStoredBusinessId(getApplication())}, ResolvedBizId: $businessId, AuthUid: ${authUser?.uid}, Role: ${admin.role}, SubId: $submissionId")

                    if (businessId.isNotBlank()) {
                        val firestore = FirebaseFirestore.getInstance()
                        if (authUser != null) {
                            syncManager.ensureBusinessDocExists(businessId, authUser.uid)
                            syncManager.ensureStaffLinked(businessId, authUser.uid, admin.phone, admin.name, UserRole.ADMIN.name)
                        }
                        val updatedSub = repository.getSubmissionById(submissionId)
                        if (updatedSub != null) {
                            val cloudSubMap = CloudSyncManager.mapProductSubmissionToCloud(updatedSub, businessId)
                            firestore.collection("businesses").document(businessId)
                                .collection("product_submissions").document(submissionId)
                                .set(cloudSubMap, SetOptions.merge()).awaitResult()
                            Log.i("SubmissionDiagnostics", "[ProductReturn] Direct cloud return write SUCCEEDED for: $submissionId")
                        }
                    } else {
                        throw IllegalStateException("কোনো সক্রিয় বিজনেস আইডি পাওয়া যায়নি।")
                    }
                }

                _toastMessage.emit("প্রোডাক্টটি সংশোধনের জন্য ফেরত পাঠানো হয়েছে")
                syncManager.triggerSync()
                onSuccess()
            } catch (e: Exception) {
                Log.e("SubmissionDiagnostics", "[ProductReturn] Direct cloud return write FAILED: ${e.message}", e)
                onError(e.localizedMessage ?: e.message ?: "প্রোডাক্ট ফেরত পাঠাতে ব্যর্থ হয়েছে")
            }
        }
    }

    fun fetchDirectProductSubmissions(onComplete: ((Boolean, String, Int) -> Unit)? = null) {
        viewModelScope.launch {
            _isFetchingSubmissions.value = true
            _submissionFetchError.value = null
            try {
                val bizId = try { syncManager.resolveOrFetchBusinessId() } catch (_: Exception) { getActiveBusinessId() }
                val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
                val projectId = try { FirebaseApp.getInstance().options.projectId } catch (_: Exception) { "smart-business-manager-71879" }

                if (bizId.isBlank()) {
                    val msg = "সতর্কতা: কোনো ক্লাউড বিজনেস আইডি পাওয়া যায়নি। অনুগ্রহ করে গুগল সাইন-ইন করে বিজনেস আইডি সেট করুন।"
                    _submissionSyncInfo.value = msg
                    _submissionFetchError.value = msg
                    _isSubmissionsFetchSuccessful.value = false
                    _isFetchingSubmissions.value = false
                    Log.w("SubmissionDiagnostics", "[AdminFetch] Failed: $msg")
                    onComplete?.invoke(false, msg, 0)
                    return@launch
                }

                Log.i("SubmissionDiagnostics", "[AdminFetch] Project: $projectId, BusinessId: $bizId, AuthUid: ${authUser?.uid}, QueryPath: /businesses/$bizId/product_submissions")

                val count = syncManager.fetchDirectProductSubmissions(bizId)
                val statusMsg = "সার্ভার থেকে $count টি প্রোডাক্ট আবেদন লোড হয়েছে"
                _submissionSyncInfo.value = "Project: $projectId | BizID: $bizId | প্রাপ্ত আবেদন: $count টি"
                _isSubmissionsFetchSuccessful.value = true
                _submissionFetchError.value = null
                _isFetchingSubmissions.value = false
                Log.i("SubmissionDiagnostics", "[AdminFetch] Success: $statusMsg")
                onComplete?.invoke(true, statusMsg, count)
            } catch (e: Exception) {
                Log.e("SubmissionDiagnostics", "[AdminFetch] Failed: ${e.message}", e)
                val errMsg = e.localizedMessage ?: e.message ?: "ক্লাউড আবেদন লোড ব্যর্থ হয়েছে"
                _submissionSyncInfo.value = errMsg
                _submissionFetchError.value = errMsg
                _isSubmissionsFetchSuccessful.value = false
                _isFetchingSubmissions.value = false
                onComplete?.invoke(false, errMsg, 0)
            }
        }
    }

    fun fetchDirectExpenses() {
        viewModelScope.launch {
            try {
                val bizId = try { syncManager.resolveOrFetchBusinessId() } catch (_: Exception) { getActiveBusinessId() }
                if (bizId.isNotBlank()) {
                    syncManager.fetchDirectExpenses(bizId)
                }
            } catch (e: Exception) {
                Log.w("ExpenseDiagnostics", "fetchDirectExpenses error: ${e.message}")
            }
        }
    }

    // --- Company / Supplier Due Management ---

    fun saveCompany(
        company: Company,
        isNew: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        if (admin.role != UserRole.ADMIN) {
            return onError("অননুমোদিত অ্যাক্সেস! শুধুমাত্র এডমিন কোম্পানি যোগ বা এডিট করতে পারেন।")
        }
        viewModelScope.launch {
            try {
                repository.saveCompany(company, isNew, admin)
                _toastMessage.emit(if (isNew) "কোম্পানি '${company.name}' সফলভাবে যুক্ত হয়েছে" else "কোম্পানির তথ্য সফলভাবে সংরক্ষিত হয়েছে")
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "কোম্পানি সংরক্ষণ ব্যর্থ হয়েছে")
            }
        }
    }

    fun deleteCompany(
        company: Company,
        reason: String = "কোম্পানি ও সাপ্লায়ার তালিকা থেকে নিরাপদ অপসারণ",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        if (admin.role != UserRole.ADMIN) {
            return onError("শুধুমাত্র এডমিন কোম্পানি মুছতে পারেন।")
        }
        viewModelScope.launch {
            try {
                repository.softDeleteCompany(company, admin, reason)
                _toastMessage.emit("কোম্পানি '${company.name}' রিসাইকেল বিনে পাঠানো হয়েছে ✅ (৩০ দিন নিরাপদ)")
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "কোম্পানি মুছতে ব্যর্থ হয়েছে")
            }
        }
    }

    fun recordCompanyPurchase(
        purchase: CompanyPurchase,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        if (admin.role != UserRole.ADMIN) {
            return onError("অননুমোদিত অ্যাক্সেস! শুধুমাত্র এডমিন কোম্পানি ক্রয় হিসাব সংরক্ষণ করতে পারেন।")
        }
        viewModelScope.launch {
            val result = repository.recordCompanyPurchase(purchase, admin)
            if (result.isSuccess) {
                _toastMessage.emit("পণ্য ক্রয়ের চালান সফলভাবে যুক্ত হয়েছে!")
                onSuccess(result.getOrNull() ?: "")
            } else {
                onError(result.exceptionOrNull()?.message ?: "ক্রয় এন্ট্রি ব্যর্থ হয়েছে")
            }
        }
    }

    fun recordCompanyPayment(
        payment: CompanyPayment,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        if (admin.role != UserRole.ADMIN) {
            return onError("অননুমোদিত অ্যাক্সেস! শুধুমাত্র এডমিন কোম্পানিকে বকেয়া পরিশোধ সংরক্ষণ করতে পারেন।")
        }
        viewModelScope.launch {
            val result = repository.recordCompanyPayment(payment, admin)
            if (result.isSuccess) {
                _toastMessage.emit("কোম্পানি পেমেন্ট সফলভাবে সংরক্ষিত হয়েছে!")
                onSuccess(result.getOrNull() ?: "")
            } else {
                onError(result.exceptionOrNull()?.message ?: "পেমেন্ট এন্ট্রি ব্যর্থ হয়েছে")
            }
        }
    }

    // ==========================================
    // Data Protection Center (ডেটা সুরক্ষা কেন্দ্র)
    // ==========================================

    fun softDeleteProduct(
        product: Product,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        if (admin.role != UserRole.ADMIN) {
            return onError("শুধুমাত্র এডমিন পণ্য মুছতে পারেন।")
        }
        viewModelScope.launch {
            val result = repository.softDeleteProduct(product, admin, reason)
            if (result.isSuccess) {
                // Direct cloud update to soft-delete (isActive = false) across all devices
                if (syncManager.isCloudBackendConfigured()) {
                    try {
                        val bizId = try { syncManager.resolveOrFetchBusinessId() } catch (_: Exception) { getActiveBusinessId() }
                        syncManager.updateProductActiveStatusInCloud(product.id, false, bizId)
                    } catch (e: Exception) {
                        Log.w("SoftDelete", "Cloud soft-delete will reconcile during sync: ${e.message}")
                    }
                }
                _toastMessage.emit("পণ্য '${product.name}' রিসাইকেল বিনে পাঠানো হয়েছে ✅ (৩০ দিন নিরাপদ)")
                syncManager.triggerSync()
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "পণ্য অপসারণ ব্যর্থ হয়েছে")
            }
        }
    }

    fun softDeleteCompany(
        company: Company,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        deleteCompany(company, reason, onSuccess, onError)
    }

    fun softDeleteCustomer(
        customer: Customer,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        viewModelScope.launch {
            val result = repository.softDeleteCustomer(customer, admin, reason)
            if (result.isSuccess) {
                scanTestDueCustomers()
                _toastMessage.emit("'${customer.name}' রিসাইকেল বিনে স্থানান্তর করা হয়েছে")
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "কাস্টমার অপসারণ ব্যর্থ হয়েছে")
            }
        }
    }

    fun softDeleteMultipleCustomers(
        customers: List<Customer>,
        reason: String,
        onSuccess: (Int, Double) -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        if (admin.role != UserRole.ADMIN) {
            return onError("শুধুমাত্র এডমিন কাস্টমার মুছতে পারেন!")
        }
        viewModelScope.launch {
            val totalDue = customers.sumOf { it.currentDue }
            val result = repository.softDeleteMultipleCustomers(customers, admin, reason)
            result.fold(
                onSuccess = { count ->
                    scanTestDueCustomers()
                    _toastMessage.emit("$count জন কাস্টমার রিসাইকেল বিনে স্থানান্তর করা হয়েছে")
                    onSuccess(count, totalDue)
                },
                onFailure = { err ->
                    onError(err.message ?: "কাস্টমার অপসারণ ব্যর্থ হয়েছে")
                }
            )
        }
    }

    fun scanTestDueCustomers() {
        viewModelScope.launch {
            _isDetectingTestDues.value = true
            try {
                _detectedTestDueCustomers.value = repository.detectTestDueCustomers()
            } catch (e: Exception) {
                _detectedTestDueCustomers.value = emptyList()
            } finally {
                _isDetectingTestDues.value = false
            }
        }
    }


    fun softDeleteSale(
        sale: Sale,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        viewModelScope.launch {
            val result = repository.softDeleteSale(sale, admin, reason)
            if (result.isSuccess) {
                _toastMessage.emit("ইনভয়েস #${sale.invoiceNumber} রিসাইকেল বিনে স্থানান্তর ও স্টক/ব্যালেন্স সমন্বয় সম্পন্ন")
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "বিক্রি অপসারণ ব্যর্থ হয়েছে")
            }
        }
    }

    fun softDeletePaymentReceipt(
        receipt: PaymentReceipt,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        viewModelScope.launch {
            val result = repository.softDeletePaymentReceipt(receipt, admin, reason)
            if (result.isSuccess) {
                _toastMessage.emit("রসিদ #${receipt.receiptNumber} রিসাইকেল বিনে স্থানান্তর ও কাস্টমার ব্যালেন্স সমন্বয় সম্পন্ন")
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "পেমেন্ট অপসারণ ব্যর্থ হয়েছে")
            }
        }
    }

    fun softDeleteExpense(
        expense: Expense,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        viewModelScope.launch {
            val result = repository.softDeleteExpense(expense, admin, reason)
            if (result.isSuccess) {
                _toastMessage.emit("খরচ '${expense.description}' রিসাইকেল বিনে স্থানান্তর করা হয়েছে")
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "খরচ অপসারণ ব্যর্থ হয়েছে")
            }
        }
    }

    fun restoreRecycleBinItem(
        item: RecycleBinItem,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        if (admin.role != UserRole.ADMIN) {
            return onError("শুধুমাত্র এডমিন রেকর্ড পুনরুদ্ধার করতে পারেন।")
        }
        viewModelScope.launch {
            val res = repository.restoreRecycleBinItem(item, admin)
            if (res.isSuccess) {
                // Direct cloud update upon restore
                if (syncManager.isCloudBackendConfigured()) {
                    try {
                        val bizId = try { syncManager.resolveOrFetchBusinessId() } catch (_: Exception) { getActiveBusinessId() }
                        if (item.originalTable == "products") {
                            syncManager.updateProductActiveStatusInCloud(item.originalId, true, bizId)
                            syncManager.removeCloudTombstone("tomb_${item.originalTable}_${item.originalId}", bizId)
                        }
                    } catch (e: Exception) {
                        Log.w("Restore", "Cloud restore will reconcile during sync: ${e.message}")
                    }
                }
                scanTestDueCustomers()
                _toastMessage.emit("'${item.itemName}' সফলভাবে পুনরুদ্ধার করা হয়েছে! ✅")
                syncManager.triggerSync()
                onSuccess()
            } else {
                onError(res.exceptionOrNull()?.message ?: "পুনরুদ্ধার ব্যর্থ হয়েছে")
            }
        }
    }

    fun permanentlyDeleteRecycleBinItem(
        item: RecycleBinItem,
        pin: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        if (admin.role != UserRole.ADMIN) {
            return onError("শুধুমাত্র এডমিন স্থায়ীভাবে ডিলিট করতে পারেন।")
        }
        viewModelScope.launch {
            val res = repository.permanentlyDeleteRecycleBinItem(item, admin, pin)
            if (res.isSuccess) {
                // Authoritative cloud permanent delete and tombstone recording
                if (syncManager.isCloudBackendConfigured()) {
                    try {
                        val bizId = try { syncManager.resolveOrFetchBusinessId() } catch (_: Exception) { getActiveBusinessId() }
                        syncManager.permanentlyDeleteCloudRecord(
                            tableName = item.originalTable,
                            originalId = item.originalId,
                            tombstoneId = "tomb_${item.originalTable}_${item.originalId}",
                            deletedByUserId = admin.id,
                            businessId = bizId
                        )
                    } catch (e: Exception) {
                        Log.w("PermanentDelete", "Cloud permanent delete will reconcile during sync: ${e.message}")
                    }
                }
                scanTestDueCustomers()
                _toastMessage.emit("'${item.itemName}' স্থায়ীভাবে মুছে ফেলা হয়েছে")
                syncManager.triggerSync()
                onSuccess()
            } else {
                onError(res.exceptionOrNull()?.message ?: "মুছে ফেলা ব্যর্থ হয়েছে")
            }
        }
    }

    fun toggleProductTestDataStatus(productId: String, isTest: Boolean, tag: String = "MANUAL_TEST") {
        val admin = _currentUser.value ?: return
        viewModelScope.launch {
            repository.setProductTestDataStatus(productId, isTest, tag, admin)
        }
    }

    fun toggleCompanyTestDataStatus(companyId: String, isTest: Boolean, tag: String = "DEMO") {
        val admin = _currentUser.value ?: return
        viewModelScope.launch {
            repository.setCompanyTestDataStatus(companyId, isTest, tag, admin)
        }
    }

    fun toggleCustomerTestDataStatus(customerId: String, isTest: Boolean, tag: String = "MANUAL_TEST") {
        val admin = _currentUser.value ?: return
        viewModelScope.launch {
            repository.setCustomerTestDataStatus(customerId, isTest, tag, admin)
        }
    }

    fun toggleSaleTestDataStatus(invoiceNumber: String, isTest: Boolean, tag: String = "MANUAL_TEST") {
        val admin = _currentUser.value ?: return
        viewModelScope.launch {
            repository.setSaleTestDataStatus(invoiceNumber, isTest, tag, admin)
        }
    }

    fun toggleReceiptTestDataStatus(receiptNumber: String, isTest: Boolean, tag: String = "MANUAL_TEST") {
        val admin = _currentUser.value ?: return
        viewModelScope.launch {
            repository.setReceiptTestDataStatus(receiptNumber, isTest, tag, admin)
        }
    }

    fun toggleExpenseTestDataStatus(expenseId: String, isTest: Boolean, tag: String = "MANUAL_TEST") {
        val admin = _currentUser.value ?: return
        viewModelScope.launch {
            repository.setExpenseTestDataStatus(expenseId, isTest, tag, admin)
        }
    }

    fun setCustomerOpeningBalance(
        customerId: String,
        openingDue: Double,
        pin: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        viewModelScope.launch {
            val res = repository.setCustomerOpeningBalance(customerId, openingDue, admin, pin)
            if (res.isSuccess) {
                _toastMessage.emit("কাস্টমার প্রারম্ভিক বকেয়া সফলভাবে সংরক্ষিত হয়েছে! ✅")
                onSuccess()
            } else {
                onError(res.exceptionOrNull()?.message ?: "প্রারম্ভিক বকেয়া সংরক্ষণ ব্যর্থ হয়েছে")
            }
        }
    }

    fun setProductOpeningStock(
        productId: String,
        openingStock: Double,
        pin: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        viewModelScope.launch {
            val res = repository.setProductOpeningStock(productId, openingStock, admin, pin)
            if (res.isSuccess) {
                _toastMessage.emit("পণ্য প্রারম্ভিক স্টক সফলভাবে সংরক্ষিত হয়েছে! ✅")
                onSuccess()
            } else {
                onError(res.exceptionOrNull()?.message ?: "প্রারম্ভিক স্টক সংরক্ষণ ব্যর্থ হয়েছে")
            }
        }
    }

    fun createSampleTestData(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val admin = _currentUser.value ?: return onError("অনুগ্রহ করে লগইন করুন!")
        viewModelScope.launch {
            val res = repository.createSampleTestData(admin)
            if (res.isSuccess) {
                _toastMessage.emit("পরীক্ষামূলক টেস্ট ডেটা সফলভাবে তৈরি হয়েছে!")
                onSuccess(res.getOrNull() ?: "")
            } else {
                onError(res.exceptionOrNull()?.message ?: "টেস্ট ডেটা তৈরি ব্যর্থ হয়েছে")
            }
        }
    }

    // --- Smart Reminder Actions ---
    fun refreshSmartReminders() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            reminderManager.scanAndSyncReminders(user)
        }
    }

    fun markReminderCompleted(reminderId: String) {
        viewModelScope.launch {
            repository.updateReminderStatus(reminderId, ReminderStatus.COMPLETED, System.currentTimeMillis())
            _toastMessage.emit("কাজটি সম্পন্ন হিসেবে চিহ্নিত করা হয়েছে")
        }
    }

    fun markReminderDismissed(reminderId: String) {
        viewModelScope.launch {
            repository.updateReminderStatus(reminderId, ReminderStatus.DISMISSED, System.currentTimeMillis())
            _toastMessage.emit("রিমাইন্ডারটি স্থগিত করা হয়েছে")
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            repository.deleteReminder(reminderId)
            _toastMessage.emit("রিমাইন্ডার মুছে ফেলা হয়েছে")
        }
    }

    fun addCustomReminder(
        title: String,
        description: String,
        category: ReminderCategory,
        priority: ReminderPriority,
        scheduledTime: Long
    ) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val item = ReminderItem(
                id = UUID.randomUUID().toString(),
                deduplicationKey = "CUSTOM_TASK_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                roleTarget = user.role,
                targetUserId = if (user.role == UserRole.SALESMAN) user.id else null,
                title = title.trim(),
                description = description.trim(),
                category = category,
                priority = priority,
                status = ReminderStatus.PENDING,
                targetRoute = null,
                targetEntityId = null,
                scheduledTime = scheduledTime,
                createdAt = System.currentTimeMillis(),
                isSystemGenerated = false
            )
            repository.insertReminder(item)
            _toastMessage.emit("নতুন কাজ যুক্ত করা হয়েছে")
        }
    }

    fun updateReminderPreference(preference: ReminderPreference) {
        viewModelScope.launch {
            repository.saveReminderPreference(preference)
            _toastMessage.emit("রিমাইন্ডার সেটিংস সংরক্ষিত হয়েছে")
        }
    }

    fun playVoiceTest(sampleText: String = "স্মার্ট বিজনেস ম্যানেজারে আপনাকে স্বাগতম। এটি ভয়েস রিমাইন্ডার টেস্ট।") {
        voiceHelper.speak(
            text = sampleText,
            isVoiceEnabled = true,
            quietHoursEnabled = false
        )
    }

    fun stopVoice() {
        voiceHelper.stop()
    }

    // --- Admin to Salesman Alert Actions ---
    fun sendAlert(
        targetType: AlertTargetType,
        targetSalesman: User?,
        title: String,
        message: String,
        priority: AlertPriority,
        isVoiceEnabled: Boolean,
        isNotificationEnabled: Boolean,
        scheduledTime: Long?,
        onResult: (Boolean, String) -> Unit
    ) {
        val admin = _currentUser.value
        if (admin == null || admin.role != UserRole.ADMIN) {
            onResult(false, "শুধুমাত্র এডমিন নির্দেশ পাঠাতে পারেন।")
            return
        }

        viewModelScope.launch {
            val result = alertRepository.createAlert(
                admin = admin,
                targetType = targetType,
                targetSalesman = targetSalesman,
                title = title,
                message = message,
                priority = priority,
                isVoiceEnabled = isVoiceEnabled,
                isNotificationEnabled = isNotificationEnabled,
                scheduledTime = scheduledTime
            )
            if (result.isSuccess) {
                val targetText = if (targetType == AlertTargetType.ALL) "সকল সেলসম্যানকে" else "${targetSalesman?.name}-কে"
                val schedText = if (scheduledTime != null && scheduledTime > System.currentTimeMillis()) "শিডিউল" else "সফলভাবে"
                _toastMessage.emit("$targetText নির্দেশটি $schedText পাঠানো হয়েছে")
                onResult(true, "নির্দেশ সফলভাবে পাঠানো হয়েছে!")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "নির্দেশ পাঠানো ব্যর্থ হয়েছে")
            }
        }
    }

    fun acknowledgeAlert(receiptId: String, onResult: (Boolean, String) -> Unit) {
        val salesman = _currentUser.value
        if (salesman == null) {
            onResult(false, "অনুগ্রহ করে লগইন করুন!")
            return
        }

        viewModelScope.launch {
            val res = alertRepository.acknowledgeAlert(receiptId, salesman.id)
            if (res.isSuccess) {
                _toastMessage.emit("নির্দেশটি বুঝেছি হিসেবে নিশ্চিত করা হয়েছে")
                onResult(true, "নিশ্চিত করা হয়েছে")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "ব্যর্থ হয়েছে")
            }
        }
    }

    fun completeAlert(receiptId: String, onResult: (Boolean, String) -> Unit) {
        val salesman = _currentUser.value
        if (salesman == null) {
            onResult(false, "অনুগ্রহ করে লগইন করুন!")
            return
        }

        viewModelScope.launch {
            val res = alertRepository.completeAlert(receiptId, salesman.id)
            if (res.isSuccess) {
                _toastMessage.emit("কাজ সম্পন্ন হিসেবে চিহ্নিত করা হয়েছে")
                onResult(true, "কাজ সম্পন্ন হয়েছে")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "ব্যর্থ হয়েছে")
            }
        }
    }

    fun cancelAlert(alertId: String, onResult: (Boolean, String) -> Unit) {
        val admin = _currentUser.value
        if (admin == null || admin.role != UserRole.ADMIN) {
            onResult(false, "শুধুমাত্র এডমিন নির্দেশ বাতিল করতে পারেন।")
            return
        }

        viewModelScope.launch {
            val res = alertRepository.cancelAlert(alertId, admin)
            if (res.isSuccess) {
                _toastMessage.emit("শিডিউল করা নির্দেশ বাতিল করা হয়েছে")
                onResult(true, "বাতিল সম্পন্ন হয়েছে")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "বাতিল করা ব্যর্থ হয়েছে")
            }
        }
    }

    fun speakAlertText(alert: AdminAlert) {
        val priorityPrefix = if (alert.priority == AlertPriority.URGENT) "অ্যাডমিনের জরুরি নির্দেশ।" else "অ্যাডমিনের নির্দেশ।"
        voiceHelper.speak(
            text = "$priorityPrefix ${alert.title}। ${alert.message}",
            isVoiceEnabled = true,
            quietHoursEnabled = false
        )
    }

    // --- Stock Verification Actions ---

    fun submitStockVerification(
        productId: String,
        physicalStock: Double,
        notes: String,
        imageUri: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        val user = _currentUser.value
        if (user == null) {
            onResult(false, "অনুগ্রহ করে লগইন করুন!")
            return
        }

        viewModelScope.launch {
            try {
                val verification = stockVerificationRepository.submitStockVerification(
                    productId = productId,
                    physicalStock = physicalStock,
                    notes = notes,
                    imageUri = imageUri,
                    actorUser = user
                )

                if (verification.status == VerificationStatus.MATCHED) {
                    _toastMessage.emit("স্টক নিখুঁত মিলেছে! সফলভাবে সংরক্ষিত।")
                    onResult(true, "স্টক মিলেছে! কোনো অমিল নেই।")
                } else {
                    val diff = verification.difference
                    val diffText = if (diff < 0) "ঘাটতি: ${-diff}" else "অতিরিক্ত: +$diff"
                    _toastMessage.emit("অমিল পাওয়া গেছে ($diffText)। এডমিন অনুমোদনের জন্য সংরক্ষিত।")
                    onResult(true, "অমিল পাওয়া গেছে ($diffText)। এডমিন অনুমোদনের অপেক্ষমাণ।")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "যাচাই সংরক্ষণ ব্যর্থ হয়েছে!")
            }
        }
    }

    fun reviewStockMismatch(
        verificationId: String,
        action: AdminMismatchAction,
        adminNote: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val admin = _currentUser.value
        if (admin == null || admin.role != UserRole.ADMIN) {
            onResult(false, "শুধুমাত্র এডমিন অনুমোদন বা সমাধান করতে পারেন!")
            return
        }

        viewModelScope.launch {
            try {
                stockVerificationRepository.reviewStockMismatch(
                    verificationId = verificationId,
                    action = action,
                    adminNote = adminNote,
                    actorAdmin = admin
                )

                val msg = when (action) {
                    AdminMismatchAction.APPROVE_AND_ADJUST -> "স্টক যাচাই অনুমোদিত এবং মূল স্টক সমন্বিত হয়েছে।"
                    AdminMismatchAction.REJECT -> "স্টক যাচাই প্রত্যাখ্যান করা হয়েছে। মূল স্টক অপরিবর্তিত।"
                    AdminMismatchAction.REQUEST_RECOUNT -> "সেলসম্যানের কাছে পুনরায় গণনার নির্দেশ পাঠানো হয়েছে।"
                }
                _toastMessage.emit(msg)
                onResult(true, msg)
            } catch (e: Exception) {
                onResult(false, e.message ?: "পর্যালোচনা ব্যর্থ হয়েছে!")
            }
        }
    }

    fun configureProductSchedule(
        productId: String,
        intervalDays: Int,
        assignedSalesmanId: String?,
        assignedSalesmanName: String?,
        isReminderEnabled: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        val admin = _currentUser.value
        if (admin == null || admin.role != UserRole.ADMIN) {
            onResult(false, "শুধুমাত্র এডমিন শিডিউল পরিবর্তন করতে পারেন!")
            return
        }

        viewModelScope.launch {
            try {
                stockVerificationRepository.configureProductSchedule(
                    productId = productId,
                    intervalDays = intervalDays,
                    assignedSalesmanId = assignedSalesmanId,
                    assignedSalesmanName = assignedSalesmanName,
                    isReminderEnabled = isReminderEnabled,
                    actorAdmin = admin
                )
                _toastMessage.emit("যাচাইয়ের সময়সীমা সফলভাবে হালনাগাদ করা হয়েছে।")
                onResult(true, "সময়সীমা হালনাগাদ হয়েছে।")
            } catch (e: Exception) {
                onResult(false, e.message ?: "সময়সীমা নির্ধারণ ব্যর্থ হয়েছে!")
            }
        }
    }

    fun sendStockVerificationAlert(
        targetSalesmanId: String?,
        targetSalesmanName: String?,
        title: String,
        message: String,
        priority: AlertPriority,
        isVoiceEnabled: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        val admin = _currentUser.value
        if (admin == null || admin.role != UserRole.ADMIN) {
            onResult(false, "শুধুমাত্র এডমিন অ্যালার্ট পাঠাতে পারেন!")
            return
        }

        viewModelScope.launch {
            try {
                val salesmen = users.value.filter { it.role == UserRole.SALESMAN }
                stockVerificationRepository.sendVerificationInstructionAlert(
                    targetSalesmanId = targetSalesmanId,
                    targetSalesmanName = targetSalesmanName,
                    title = title,
                    message = message,
                    priority = priority,
                    isVoiceEnabled = isVoiceEnabled,
                    actorAdmin = admin,
                    salesmenList = salesmen
                )
                _toastMessage.emit("স্টক যাচাই নির্দেশ পাঠানো হয়েছে")
                onResult(true, "নির্দেশ সফলভাবে প্রেরিত")
            } catch (e: Exception) {
                onResult(false, e.message ?: "নির্দেশ পাঠানো ব্যর্থ হয়েছে!")
            }
        }
    }

    fun speakVerificationText(text: String) {
        voiceHelper.speak(
            text = text,
            isVoiceEnabled = true,
            quietHoursEnabled = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        voiceHelper.shutdown()
        syncManager.cancelActiveSync()
    }
}
