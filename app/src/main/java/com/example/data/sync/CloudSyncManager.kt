package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.model.*
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CloudSyncManager handles bidirectional, offline-first synchronization between local Room database
 * and Firebase Cloud Firestore with tenant business isolation, idempotent transactions, and deletion tombstones.
 */
class CloudSyncManager(
    private val context: Context,
    private val db: AppDatabase
) {
    companion object {
        const val TAG = "CloudSyncManager"

        fun isAuthorizedAdminEmail(email: String): Boolean {
            val clean = email.trim().lowercase()
            return clean == "buddavaibinodon@gmail.com" ||
                   clean == "ataursek9@gmail.com" || clean == "ataurgonee4@gmail.com" ||
                   clean == "support@smartbiz.local" || clean == "gonee8666@gmail.com" ||
                   clean == "ataulgonee786@gmail.com" || clean == "ataurpos@gmail.com" ||
                   clean == "ataur50305@gmail.com" || clean == "a48268993@gmail.com"
        }

        /**
         * Converts an asynchronous Google Play Services / Firebase Task into a suspending coroutine.
         */
        suspend fun <T> Task<T>.awaitResult(): T =
            suspendCancellableCoroutine { continuation ->
                addOnSuccessListener { result ->
                    continuation.resume(result)
                }
                addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
            }

        // --- Sanitization & Security Mappers ---

        fun mapProductToCloud(product: Product, businessId: String, isSalesman: Boolean): Map<String, Any> {
            val map = mutableMapOf<String, Any>(
                "id" to product.id,
                "name" to product.name,
                "sku" to product.sku,
                "barcode" to product.barcode,
                "category" to product.category,
                "sellingPrice" to product.sellingPrice,
                "currentStock" to product.currentStock,
                "alertStockLevel" to product.alertStockLevel,
                "unit" to product.unit,
                "isActive" to product.isActive,
                "updatedAt" to product.updatedAt,
                "businessId" to businessId,
                "imageUri" to (product.imageUri ?: ""),
                "imageSource" to product.imageSource,
                "imageSourceInfo" to product.imageSourceInfo
            )
            // Confidential purchase cost price is restricted from salesmen
            if (!isSalesman) {
                map["purchasePrice"] = product.purchasePrice
            }
            return map
        }

        fun mapCustomerToCloud(customer: Customer, businessId: String): Map<String, Any> {
            return mapOf(
                "id" to customer.id,
                "name" to customer.name,
                "phone" to customer.phone,
                "address" to customer.address,
                "totalBilled" to customer.totalBilled,
                "totalPaid" to customer.totalPaid,
                "currentDue" to customer.currentDue,
                "openingDue" to customer.openingDue,
                "isDeleted" to customer.isDeleted,
                "updatedAt" to customer.updatedAt,
                "businessId" to businessId
            )
        }

        fun mapSaleToCloud(sale: Sale, items: List<SaleItem>, businessId: String): Map<String, Any> {
            val itemsList = items.map { item ->
                mapOf(
                    "productId" to item.productId,
                    "productName" to item.productName,
                    "quantity" to item.quantity,
                    "unitPrice" to item.unitPrice,
                    "purchasePriceAtSale" to item.purchasePriceAtSale,
                    "totalPrice" to item.totalPrice
                )
            }
            return mapOf(
                "invoiceNumber" to sale.invoiceNumber,
                "customerId" to sale.customerId,
                "customerName" to sale.customerName,
                "customerPhone" to sale.customerPhone,
                "salesmanId" to sale.salesmanId,
                "salesmanName" to sale.salesmanName,
                "subtotal" to sale.subtotal,
                "discount" to sale.discount,
                "totalAmount" to sale.totalAmount,
                "paidAmount" to sale.paidAmount,
                "dueAmount" to sale.dueAmount,
                "paymentMethod" to sale.paymentMethod.name,
                "isDeleted" to sale.isDeleted,
                "items" to itemsList,
                "createdAt" to sale.createdAt,
                "businessId" to businessId
            )
        }

        fun mapSaleToCloud(sale: Sale, businessId: String): Map<String, Any> {
            return mapSaleToCloud(sale, emptyList(), businessId)
        }

        fun mapReceiptToCloud(receipt: PaymentReceipt, businessId: String): Map<String, Any> {
            return mapOf(
                "receiptNumber" to receipt.receiptNumber,
                "invoiceNumber" to receipt.invoiceNumber,
                "customerId" to receipt.customerId,
                "customerName" to receipt.customerName,
                "customerPhone" to receipt.customerPhone,
                "salesmanId" to receipt.salesmanId,
                "salesmanName" to receipt.salesmanName,
                "amount" to receipt.amount,
                "previousDue" to receipt.previousDue,
                "currentDue" to receipt.currentDue,
                "paymentMethod" to receipt.paymentMethod.name,
                "note" to receipt.note,
                "isDeleted" to receipt.isDeleted,
                "createdAt" to receipt.createdAt,
                "businessId" to businessId
            )
        }

        fun mapExpenseToCloud(expense: Expense, businessId: String): Map<String, Any> {
            val map = mutableMapOf<String, Any>(
                "id" to expense.id,
                "salesmanId" to expense.salesmanId,
                "salesmanName" to expense.salesmanName,
                "category" to expense.category.name,
                "source" to expense.source.name,
                "amount" to expense.amount,
                "description" to expense.description,
                "status" to expense.status.name,
                "adminNote" to expense.adminNote,
                "isDeleted" to expense.isDeleted,
                "createdAt" to expense.createdAt,
                "businessId" to businessId
            )
            expense.reviewedAt?.let { map["reviewedAt"] = it }
            return map
        }

        fun mapProductSubmissionToCloud(sub: ProductSubmission, businessId: String): Map<String, Any> {
            val map = mutableMapOf<String, Any>(
                "id" to sub.id,
                "name" to sub.name,
                "productCode" to sub.productCode,
                "category" to sub.category,
                "purchasePrice" to sub.purchasePrice,
                "sellingPrice" to sub.sellingPrice,
                "initialStock" to sub.initialStock,
                "unit" to sub.unit,
                "description" to sub.description,
                "salesmanId" to sub.salesmanId,
                "salesmanName" to sub.salesmanName,
                "salesmanPhone" to sub.salesmanPhone,
                "status" to sub.status.name,
                "adminCorrectionNote" to sub.adminCorrectionNote,
                "imageSource" to sub.imageSource,
                "imageSourceInfo" to sub.imageSourceInfo,
                "createdAt" to sub.createdAt,
                "updatedAt" to sub.updatedAt,
                "businessId" to businessId
            )
            sub.approvedProductId?.let { map["approvedProductId"] = it }
            sub.reviewedByAdminId?.let { map["reviewedByAdminId"] = it }
            sub.reviewedByAdminName?.let { map["reviewedByAdminName"] = it }
            sub.reviewedAt?.let { map["reviewedAt"] = it }
            sub.imageUri?.let { uri ->
                if (uri.length <= 1000) {
                    map["imageUri"] = uri
                }
            }
            return map
        }

        fun mapTombstoneToCloud(tombstone: Tombstone, businessId: String): Map<String, Any> {
            return mapOf(
                "id" to tombstone.id,
                "tableName" to tombstone.tableName,
                "originalId" to tombstone.originalId,
                "deletedAt" to tombstone.deletedAt,
                "isPermanent" to tombstone.isPermanent,
                "deletedByUserId" to tombstone.deletedByUserId,
                "businessId" to businessId
            )
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun cancelActiveSync() {
        scope.coroutineContext.cancelChildren()
    }
    private val _syncState = MutableStateFlow(
        SyncState(
            status = if (isCloudBackendConfigured()) SyncStatus.SYNCED else SyncStatus.LOCAL_SAVED,
            statusText = if (isCloudBackendConfigured()) "সিঙ্ক সম্পন্ন" else "লোকাল ডেটা সংরক্ষিত — ক্লাউড সেটআপ বাকি",
            isCloudConfigured = isCloudBackendConfigured()
        )
    )
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val isSyncing = AtomicBoolean(false)

    /**
     * Checks whether Firebase is properly configured on device and an authenticated user session is active.
     */
    fun isCloudBackendConfigured(): Boolean {
        return try {
            val apps = FirebaseApp.getApps(context)
            if (apps.isEmpty()) {
                val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
                return resId != 0
            }
            val authUser = FirebaseAuth.getInstance().currentUser
            authUser != null && authUser.uid.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }


    /**
     * Checks if current active session belongs to a salesman (staff) rather than admin (owner).
     */
    suspend fun isCurrentActiveSalesman(): Boolean {
        // 1. Explicitly stored active session role in persistent preferences
        val storedRole = CloudBusinessAuth.getStoredRole(context)
        if (storedRole == UserRole.ADMIN.name) {
            return false
        }
        if (storedRole == UserRole.SALESMAN.name) {
            return true
        }

        // 2. Check current authenticated Firebase user
        val authUser = try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }
        if (authUser != null) {
            val email = authUser.email?.trim()?.lowercase() ?: ""
            if (isAuthorizedAdminEmail(email)) {
                CloudBusinessAuth.setStoredRole(context, UserRole.ADMIN.name)
                return false
            }

            val storedBizId = CloudBusinessAuth.getStoredBusinessId(context)
            if (!storedBizId.isNullOrBlank() && authUser.uid == storedBizId) {
                CloudBusinessAuth.setStoredRole(context, UserRole.ADMIN.name)
                return false
            }

            val localUser = db.userDao().getUserById(authUser.uid)
            if (localUser != null) {
                CloudBusinessAuth.setStoredRole(context, localUser.role.name)
                return localUser.role == UserRole.SALESMAN
            }

            // Salesman connected to another business ID
            if (!storedBizId.isNullOrBlank() && authUser.uid != storedBizId) {
                CloudBusinessAuth.setStoredRole(context, UserRole.SALESMAN.name)
                return true
            }
        }

        // 3. Check local users in Room
        val allUsers = db.userDao().getAllActiveUsersList()
        val nonDefaultUsers = allUsers.filter { it.id != "admin_1" && it.id != "salesman_1" && it.isActive }
        if (nonDefaultUsers.isNotEmpty()) {
            val activeUser = nonDefaultUsers.first()
            CloudBusinessAuth.setStoredRole(context, activeUser.role.name)
            return activeUser.role == UserRole.SALESMAN
        }

        // Check seeded or any active user role
        val activeSalesman = allUsers.firstOrNull { it.role == UserRole.SALESMAN && it.isActive }
        val activeAdmin = allUsers.firstOrNull { it.role == UserRole.ADMIN && it.isActive }
        if (activeSalesman != null && activeAdmin == null) {
            CloudBusinessAuth.setStoredRole(context, UserRole.SALESMAN.name)
            return true
        }

        // 4. If storedBizId is configured and no Admin is actively logged in, treat as salesman
        val activeBizId = CloudBusinessAuth.getStoredBusinessId(context)
        if (!activeBizId.isNullOrBlank() && (authUser == null || authUser.uid != activeBizId)) {
            return true
        }

        return false
    }

    /**
     * Resolves the secure multi-tenant business ID for cloud isolation under /businesses/{businessId}/.
     * Enforces strict authentication: never returns a fallback ID when unauthenticated.
     */
    fun resolveBusinessId(): String {
        if (FirebaseApp.getApps(context).isEmpty()) {
            throw IllegalStateException("ফায়ারবেস কনফিগারেশন অনুপস্থিত (google-services.json প্রয়োজন)।")
        }
        val authUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        } ?: throw IllegalStateException("ক্লাউড সিঙ্ক করার জন্য গুগল একাউন্টে প্রমাণীকরণ (Authentication) আবশ্যক। ফলব্যাক আইডি ব্যবহার নিষিদ্ধ।")

        if (authUser.uid.isBlank()) {
            throw IllegalStateException("অবৈধ ইউজার আইডি। ক্লাউড সিঙ্ক বাতিল করা হয়েছে।")
        }

        // 1. Prioritize explicitly stored active multi-tenant Business ID
        val storedBizId = CloudBusinessAuth.getStoredBusinessId(context)
        if (!storedBizId.isNullOrBlank()) {
            return storedBizId
        }

        val email = authUser.email?.trim()?.lowercase() ?: ""
        val storedRole = CloudBusinessAuth.getStoredRole(context)
        val isOwner = isAuthorizedAdminEmail(email) ||
                storedRole == UserRole.ADMIN.name

        if (isOwner) {
            CloudBusinessAuth.setStoredBusinessId(context, authUser.uid, role = UserRole.ADMIN.name)
            return authUser.uid
        }

        throw IllegalStateException("অননুমোদিত স্টাফ ডিভাইস: কোনো অনুমোদিত ক্লাউড বিজনেস আইডি পাওয়া যায়নি। অনুগ্রহ করে অ্যাডমিনের বিজনেস আইডি দিয়ে কানেক্ট করুন।")
    }

    suspend fun resolveOrFetchBusinessId(): String {
        if (FirebaseApp.getApps(context).isEmpty()) {
            throw IllegalStateException("ফায়ারবেস কনফিগারেশন অনুপস্থিত (google-services.json প্রয়োজন)।")
        }
        val authUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        } ?: throw IllegalStateException("ক্লাউড সিঙ্ক করার জন্য গুগল একাউন্টে প্রমাণীকরণ আবশ্যক।")

        // 1. Prioritize explicitly stored active multi-tenant Business ID
        val storedBizId = CloudBusinessAuth.getStoredBusinessId(context)
        if (!storedBizId.isNullOrBlank()) {
            return storedBizId
        }

        val email = authUser.email?.trim()?.lowercase() ?: ""
        val storedRole = CloudBusinessAuth.getStoredRole(context)
        val isOwner = isAuthorizedAdminEmail(email) ||
                storedRole == UserRole.ADMIN.name

        // Attempt remote lookup in Firestore businesses by ownerUid, ownerEmail or registered_phones
        try {
            val firestore = FirebaseFirestore.getInstance()

            // 2a. Check direct document by authUser.uid (e.g. PBknnhTZ1LNZsNBb9dpAOInpRmt1)
            try {
                val directDoc = firestore.collection("businesses").document(authUser.uid).get().awaitResult()
                if (directDoc.exists()) {
                    val foundBizId = directDoc.id
                    CloudBusinessAuth.setStoredBusinessId(context, foundBizId, role = UserRole.ADMIN.name)
                    return foundBizId
                }
            } catch (e: Exception) {
                Log.w(TAG, "resolveOrFetchBusinessId direct doc check note: ${e.message}")
            }

            // 2b. Query by ownerUid
            val uidSnap = firestore.collection("businesses")
                .whereEqualTo("ownerUid", authUser.uid)
                .limit(1)
                .get()
                .awaitResult()
            val uidDoc = uidSnap.documents.firstOrNull()
            if (uidDoc != null && uidDoc.id.isNotBlank()) {
                val foundBizId = uidDoc.id
                CloudBusinessAuth.setStoredBusinessId(context, foundBizId, role = UserRole.ADMIN.name)
                return foundBizId
            }

            // 2b. Query by ownerEmail
            if (email.isNotBlank()) {
                val emailSnap = firestore.collection("businesses")
                    .whereEqualTo("ownerEmail", email)
                    .limit(1)
                    .get()
                    .awaitResult()
                val emailDoc = emailSnap.documents.firstOrNull()
                if (emailDoc != null && emailDoc.id.isNotBlank()) {
                    val foundBizId = emailDoc.id
                    CloudBusinessAuth.setStoredBusinessId(context, foundBizId, role = UserRole.ADMIN.name)
                    return foundBizId
                }
            }

            // 2c. Query by phone if available
            val rawPhone = authUser.phoneNumber?.trim() ?: ""
            if (rawPhone.isNotBlank()) {
                val localPhone = CloudBusinessAuth.normalizePhone(rawPhone)
                val e164Phone = CloudBusinessAuth.normalizeToE164(rawPhone)
                val phonesToSearch = listOf(rawPhone, localPhone, e164Phone).distinct().filter { it.isNotBlank() }
                for (p in phonesToSearch) {
                    try {
                        val phoneDoc = firestore.collection("registered_phones").document(p).get().awaitResult()
                        if (phoneDoc.exists()) {
                            val foundBizId = phoneDoc.getString("businessId")
                            if (!foundBizId.isNullOrBlank()) {
                                CloudBusinessAuth.setStoredBusinessId(context, foundBizId, role = UserRole.ADMIN.name)
                                return foundBizId
                            }
                        }
                    } catch (pe: Exception) {
                        Log.w(TAG, "resolveOrFetchBusinessId phone lookup note for $p: ${pe.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveOrFetchBusinessId remote businesses lookup non-fatal: ${e.message}")
        }

        // Attempt remote lookup in Firestore staff_memberships/{authUser.uid}
        try {
            val firestore = FirebaseFirestore.getInstance()
            val doc = firestore.collection("staff_memberships").document(authUser.uid).get().awaitResult()
            if (doc.exists()) {
                val bizId = doc.getString("businessId")
                val isActive = doc.getBoolean("isActive") ?: false
                if (!bizId.isNullOrBlank() && isActive) {
                    CloudBusinessAuth.setStoredBusinessId(context, bizId, role = UserRole.SALESMAN.name)
                    return bizId
                }
            }
        } catch (_: Exception) {}

        if (isOwner) {
            CloudBusinessAuth.setStoredBusinessId(context, authUser.uid, role = UserRole.ADMIN.name)
            return authUser.uid
        }

        throw IllegalStateException("অননুমোদিত স্টাফ ডিভাইস: কোনো অনুমোদিত ক্লাউড বিজনেস আইডি পাওয়া যায়নি। অনুগ্রহ করে অ্যাডমিনের বিজনেস আইডি দিয়ে কানেক্ট করুন।")
    }

    /**
     * Executes a full synchronization cycle synchronously.
     * Returns the updated SyncState.
     */
    suspend fun executeSyncCycle(): SyncState {
        val cloudConfigured = isCloudBackendConfigured()
        if (!cloudConfigured) {
            val pendingCount = countPendingLocalRecords()
            val hasFirebaseApp = try { FirebaseApp.getApps(context).isNotEmpty() } catch (e: Exception) { false }
            val statusText = if (!hasFirebaseApp) {
                "লোকাল ডেটা সংরক্ষিত — ক্লাউড সেটআপ বাকি"
            } else {
                "লোকাল ডেটা সংরক্ষিত — গুগল একাউন্টে সাইন-ইন প্রয়োজন"
            }
            val state = SyncState(
                status = SyncStatus.LOCAL_SAVED,
                statusText = statusText,
                pendingCount = pendingCount,
                lastSyncTime = System.currentTimeMillis(),
                isCloudConfigured = false
            )
            _syncState.value = state
            return state
        }

        // Execute real cloud synchronization
        performCloudSync()

        // Maintain live snapshot observation for real-time multi-device sync
        startProductsRealtimeListener()

        val state = SyncState(
            status = SyncStatus.SYNCED,
            statusText = "সিঙ্ক সম্পন্ন",
            pendingCount = 0,
            lastSyncTime = System.currentTimeMillis(),
            isCloudConfigured = true
        )
        _syncState.value = state
        return state
    }

    private var productsListenerRegistration: ListenerRegistration? = null
    private var submissionsListenerRegistration: ListenerRegistration? = null
    private var expensesListenerRegistration: ListenerRegistration? = null

    /**
     * Attaches real-time snapshot listeners to products, product_submissions, and expenses.
     * Ensures Admin reviews, salesman submissions, and expenses reflect on all authorized devices immediately.
     */
    fun startProductsRealtimeListener() {
        scope.launch {
            try {
                val apps = FirebaseApp.getApps(context)
                if (apps.isEmpty()) return@launch
                val authUser = FirebaseAuth.getInstance().currentUser ?: return@launch
                if (authUser.uid.isBlank()) return@launch

                val businessId = try { resolveBusinessId() } catch (_: Exception) {
                    try { resolveOrFetchBusinessId() } catch (_: Exception) {
                        CloudBusinessAuth.getStoredBusinessId(context) ?: return@launch
                    }
                }
                if (businessId.isBlank()) return@launch

                val isSalesman = isCurrentActiveSalesman()
                if (!isSalesman) {
                    ensureBusinessDocExists(businessId, authUser.uid)
                }

                val firestore = FirebaseFirestore.getInstance()

                // 1. Products Listener
                productsListenerRegistration?.remove()
                productsListenerRegistration = firestore.collection("businesses")
                    .document(businessId)
                    .collection("products")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Products realtime listener warning: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            scope.launch {
                                val currentSalesman = isCurrentActiveSalesman()
                                for (doc in snapshot.documents) {
                                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                                    if (!data.containsKey("id")) data["id"] = doc.id
                                    applyRemoteProductMap(data, currentSalesman)
                                }
                            }
                        }
                    }

                // 2. Product Submissions Listener (for Admin review and Salesman status feedback)
                submissionsListenerRegistration?.remove()
                submissionsListenerRegistration = firestore.collection("businesses")
                    .document(businessId)
                    .collection("product_submissions")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Submissions realtime listener warning: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            Log.i(TAG, "[RealtimeSubmissions] Received ${snapshot.size()} submission documents from /businesses/$businessId/product_submissions")
                            scope.launch {
                                for (doc in snapshot.documents) {
                                    applyRemoteSubmissionDoc(doc)
                                }
                            }
                        }
                    }

                // 3. Expenses Listener (for Admin approval and Salesman status feedback)
                expensesListenerRegistration?.remove()
                expensesListenerRegistration = firestore.collection("businesses")
                    .document(businessId)
                    .collection("expenses")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w(TAG, "Expenses realtime listener warning: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            Log.i(TAG, "[RealtimeExpenses] Received ${snapshot.size()} expense documents from /businesses/$businessId/expenses")
                            scope.launch {
                                for (doc in snapshot.documents) {
                                    applyRemoteExpenseDoc(doc)
                                }
                            }
                        }
                    }

                Log.i(TAG, "Realtime listeners started for business: $businessId")
            } catch (e: Exception) {
                Log.w(TAG, "Could not start realtime listeners: ${e.message}")
            }
        }
    }

    suspend fun ensureBusinessDocExists(businessId: String, authUid: String, businessName: String = "স্মার্ট বিজনেস", phone: String = "") {
        if (businessId.isBlank() || authUid.isBlank() || businessId == "admin_1" || businessId == "salesman_1") return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val bizRef = firestore.collection("businesses").document(businessId)
            val snapshot = bizRef.get().awaitResult()
            val cleanPhone = if (phone.isNotBlank()) CloudBusinessAuth.normalizePhone(phone) else (db.companySettingsDao().getSettings()?.phone?.let { CloudBusinessAuth.normalizePhone(it) } ?: "")
            if (!snapshot.exists()) {
                val bizData = mutableMapOf<String, Any>(
                    "id" to businessId,
                    "ownerUid" to authUid,
                    "businessName" to businessName,
                    "name" to businessName,
                    "status" to "PENDING",
                    "planId" to "plan_free",
                    "planName" to "সম্পূর্ণ ফ্রি ব্যবহার",
                    "subscriptionStatus" to "TRIAL",
                    "createdAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )
                if (cleanPhone.isNotBlank()) {
                    bizData["ownerPhone"] = cleanPhone
                    bizData["phone"] = cleanPhone
                }
                bizRef.set(bizData, SetOptions.merge()).awaitResult()
                Log.i(TAG, "Created root business doc: /businesses/$businessId with owner: $authUid")
            } else {
                val updates = mutableMapOf<String, Any>("updatedAt" to System.currentTimeMillis())
                val existingOwner = snapshot.getString("ownerUid")
                if (existingOwner.isNullOrBlank()) {
                    updates["ownerUid"] = authUid
                    Log.i(TAG, "Updated root business doc ownerUid on /businesses/$businessId: $authUid")
                }
                if (cleanPhone.isNotBlank() && snapshot.getString("ownerPhone").isNullOrBlank()) {
                    updates["ownerPhone"] = cleanPhone
                    updates["phone"] = cleanPhone
                }
                bizRef.set(updates, SetOptions.merge()).awaitResult()
            }
            // Ensure owner membership index exists
            ensureStaffLinked(businessId, authUid, role = UserRole.ADMIN.name, name = "এডমিন")
        } catch (e: Exception) {
            Log.w(TAG, "ensureBusinessDocExists non-fatal note: ${e.message}")
        }
    }

    suspend fun ensureStaffLinked(
        businessId: String,
        authUid: String,
        phone: String = "",
        name: String = "",
        role: String = UserRole.SALESMAN.name
    ) {
        if (businessId.isBlank() || authUid.isBlank() || businessId == "admin_1" || businessId == "salesman_1") return
        try {
            val firestore = FirebaseFirestore.getInstance()
            // 1. Ensure staff document exists under /businesses/{businessId}/staff/{authUid}
            val staffRef = firestore.collection("businesses").document(businessId).collection("staff").document(authUid)
            val staffSnap = staffRef.get().awaitResult()
            if (!staffSnap.exists()) {
                val staffData = mapOf(
                    "id" to authUid,
                    "name" to name.ifBlank { if (role == UserRole.ADMIN.name) "অ্যাডমিন" else "সেলস প্রতিনিধি" },
                    "phone" to phone,
                    "role" to role,
                    "isActive" to true,
                    "businessId" to businessId,
                    "linkedAt" to System.currentTimeMillis()
                )
                staffRef.set(staffData, SetOptions.merge()).awaitResult()
                Log.i(TAG, "ensureStaffLinked: Created /businesses/$businessId/staff/$authUid ($role)")
            }

            // 2. Ensure root /staff_memberships/{authUid} index exists
            val memberRef = firestore.collection("staff_memberships").document(authUid)
            val memberSnap = memberRef.get().awaitResult()
            if (!memberSnap.exists()) {
                val memberData = mapOf(
                    "userId" to authUid,
                    "businessId" to businessId,
                    "role" to role,
                    "isActive" to true,
                    "phone" to phone,
                    "updatedAt" to System.currentTimeMillis()
                )
                memberRef.set(memberData, SetOptions.merge()).awaitResult()
                Log.i(TAG, "ensureStaffLinked: Created /staff_memberships/$authUid ($role)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "ensureStaffLinked note: ${e.message}")
        }
    }

    suspend fun fetchDirectProductSubmissions(businessId: String? = null): Int {
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.w(TAG, "[fetchDirectProductSubmissions] FirebaseApp is not initialized.")
            return 0
        }
        val bizId = businessId?.trim()?.ifBlank { null } ?: try { resolveOrFetchBusinessId() } catch (e: Exception) {
            CloudBusinessAuth.getStoredBusinessId(context) ?: ""
        }
        if (bizId.isBlank()) {
            Log.w(TAG, "[fetchDirectProductSubmissions] Active businessId is empty, skipping direct fetch.")
            return 0
        }
        val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        val authUid = authUser?.uid ?: "unauthenticated"
        val queryPath = "/businesses/$bizId/product_submissions"
        Log.i(TAG, "[fetchDirectProductSubmissions] Querying Firestore path: $queryPath (AuthUid: $authUid, Email: ${authUser?.email})")

        if (authUser == null || authUid.isBlank() || authUid == "unauthenticated") {
            val unauthMsg = "Firebase Authentication প্রয়োজন: আপনি ক্লাউডে সাইন-ইন করেননি। ক্লাউড থেকে আবেদন লোড করার জন্য Google Sign-In আবশ্যক। (Path: $queryPath, Business ID: $bizId)"
            Log.e(TAG, "[fetchDirectProductSubmissions] Aborted: $unauthMsg")
            throw IllegalStateException(unauthMsg)
        }

        // Proactively link business document and staff membership so Firestore security rules permit reading
        try {
            ensureBusinessDocExists(bizId, authUid)
            ensureStaffLinked(bizId, authUid, role = UserRole.ADMIN.name, name = authUser.displayName ?: "অ্যাডমিন")
        } catch (linkEx: Exception) {
            Log.w(TAG, "[fetchDirectProductSubmissions] Pre-fetch staff linking warning: ${linkEx.message}")
        }

        return try {
            val firestore = FirebaseFirestore.getInstance()
            val collection = firestore.collection("businesses").document(bizId).collection("product_submissions")
            val snapshot = try {
                collection.get(com.google.firebase.firestore.Source.SERVER).awaitResult()
            } catch (e: Exception) {
                Log.w(TAG, "[fetchDirectProductSubmissions] Server fetch fallback: ${e.message}")
                collection.get().awaitResult()
            }
            val docs = snapshot.documents
            Log.i(TAG, "[fetchDirectProductSubmissions] Direct fetch returned ${docs.size} docs from $queryPath")
            for (doc in docs) {
                applyRemoteSubmissionDoc(doc)
            }
            docs.size
        } catch (e: Exception) {
            Log.e(TAG, "[fetchDirectProductSubmissions] Direct fetch failed at $queryPath | BusinessId: $bizId | AuthUid: $authUid | Email: ${authUser.email} | Error: ${e.message}", e)
            val errMsg = if (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                "অনুমতি ত্রুটি (PERMISSION_DENIED): এই বিজনেস আইডির ($bizId) প্রোডাক্ট আবেদন পড়ার অনুমতি নেই। UID: $authUid, Email: ${authUser.email}। অনুগ্রহ করে নিশ্চিত করুন আপনি সঠিক অ্যাডমিন অ্যাকাউন্টে লগইন করেছেন।"
            } else {
                e.localizedMessage ?: e.message ?: "ক্লাউড থেকে আবেদন লোড ব্যর্থ হয়েছে"
            }
            throw Exception(errMsg, e)
        }
    }

    suspend fun fetchDirectExpenses(businessId: String? = null) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val bizId = businessId?.trim()?.ifBlank { null } ?: try { resolveOrFetchBusinessId() } catch (_: Exception) {
            CloudBusinessAuth.getStoredBusinessId(context) ?: ""
        }
        if (bizId.isBlank()) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val collection = firestore.collection("businesses").document(bizId).collection("expenses")
            val snapshot = try {
                collection.get(com.google.firebase.firestore.Source.SERVER).awaitResult()
            } catch (e: Exception) {
                Log.w(TAG, "[fetchDirectExpenses] Server fetch fallback: ${e.message}")
                collection.get().awaitResult()
            }
            Log.i(TAG, "[fetchDirectExpenses] Direct fetch returned ${snapshot.size()} docs from /businesses/$bizId/expenses")
            for (doc in snapshot.documents) {
                applyRemoteExpenseDoc(doc)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[fetchDirectExpenses] Direct fetch failed: ${e.message}", e)
        }
    }

    fun stopProductsRealtimeListener() {
        productsListenerRegistration?.remove()
        productsListenerRegistration = null
        submissionsListenerRegistration?.remove()
        submissionsListenerRegistration = null
        expensesListenerRegistration?.remove()
        expensesListenerRegistration = null
    }

    /**
     * Atomically records a sale and deducts product stock in Cloud Firestore.
     * Prevents race conditions and multi-device double deduction.
     */
    suspend fun deductCloudStockForSale(invoiceNumber: String, items: List<SaleItem>, businessId: String) {
        if (!isCloudBackendConfigured() || businessId.isBlank()) return
        val firestore = FirebaseFirestore.getInstance()
        val batch = firestore.batch()
        val now = System.currentTimeMillis()

        for (item in items) {
            if (item.productId.isBlank() || item.quantity <= 0) continue
            val prodDocRef = firestore.collection("businesses").document(businessId).collection("products").document(item.productId)
            batch.update(prodDocRef, mapOf(
                "currentStock" to FieldValue.increment(-item.quantity),
                "updatedAt" to now
            ))
        }
        try {
            batch.commit().awaitResult()
            Log.i(TAG, "Atomic cloud stock deduction committed for sale: $invoiceNumber")
        } catch (e: Exception) {
            Log.w(TAG, "Atomic cloud stock deduction non-fatal note: ${e.message}")
        }
    }

    /**
     * Updates product active status (soft-delete / restore) directly in Cloud Firestore.
     */
    suspend fun updateProductActiveStatusInCloud(productId: String, isActive: Boolean, businessId: String? = null) {
        if (!isCloudBackendConfigured() || productId.isBlank()) return
        val bizId = businessId?.trim()?.ifBlank { null } ?: try { resolveOrFetchBusinessId() } catch (_: Exception) {
            CloudBusinessAuth.getStoredBusinessId(context) ?: ""
        }
        if (bizId.isBlank()) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("businesses").document(bizId)
                .collection("products").document(productId)
                .update(
                    mapOf(
                        "isActive" to isActive,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).awaitResult()
            Log.i(TAG, "updateProductActiveStatusInCloud: Product $productId marked isActive=$isActive on /businesses/$bizId/products")
        } catch (e: Exception) {
            Log.w(TAG, "updateProductActiveStatusInCloud non-fatal note: ${e.message}")
        }
    }

    /**
     * Permanently deletes a record document from Cloud Firestore and writes a permanent Tombstone marker.
     */
    suspend fun permanentlyDeleteCloudRecord(
        tableName: String,
        originalId: String,
        tombstoneId: String,
        deletedByUserId: String,
        businessId: String? = null
    ) {
        if (!isCloudBackendConfigured() || tableName.isBlank() || originalId.isBlank()) return
        val bizId = businessId?.trim()?.ifBlank { null } ?: try { resolveOrFetchBusinessId() } catch (_: Exception) {
            CloudBusinessAuth.getStoredBusinessId(context) ?: ""
        }
        if (bizId.isBlank()) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            val batch = firestore.batch()

            // 1. Delete document from target collection
            val targetDocRef = firestore.collection("businesses").document(bizId)
                .collection(tableName).document(originalId)
            batch.delete(targetDocRef)

            // 2. Write permanent Tombstone marker
            val tombstoneRef = firestore.collection("businesses").document(bizId)
                .collection("tombstones").document(tombstoneId)
            val tombstoneData = mapOf(
                "id" to tombstoneId,
                "tableName" to tableName,
                "originalId" to originalId,
                "deletedAt" to System.currentTimeMillis(),
                "isPermanent" to true,
                "deletedByUserId" to deletedByUserId,
                "businessId" to bizId
            )
            batch.set(tombstoneRef, tombstoneData, SetOptions.merge())

            batch.commit().awaitResult()
            Log.i(TAG, "permanentlyDeleteCloudRecord: Deleted /businesses/$bizId/$tableName/$originalId and created tombstone $tombstoneId")
        } catch (e: Exception) {
            Log.w(TAG, "permanentlyDeleteCloudRecord non-fatal note: ${e.message}")
        }
    }

    /**
     * Removes a Tombstone marker from Cloud Firestore upon item restoration.
     */
    suspend fun removeCloudTombstone(tombstoneId: String, businessId: String? = null) {
        if (!isCloudBackendConfigured() || tombstoneId.isBlank()) return
        val bizId = businessId?.trim()?.ifBlank { null } ?: try { resolveOrFetchBusinessId() } catch (_: Exception) {
            CloudBusinessAuth.getStoredBusinessId(context) ?: ""
        }
        if (bizId.isBlank()) return
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("businesses").document(bizId)
                .collection("tombstones").document(tombstoneId)
                .delete().awaitResult()
            Log.i(TAG, "removeCloudTombstone: Removed /businesses/$bizId/tombstones/$tombstoneId")
        } catch (e: Exception) {
            Log.w(TAG, "removeCloudTombstone non-fatal note: ${e.message}")
        }
    }

    /**
     * Triggers an offline-first sync cycle.
     * If cloud backend is configured, it performs real bidirectional Firestore sync.
     * If cloud backend is pending setup, it safely records local counts without reporting false sync success.
     */
    fun triggerSync(onComplete: ((Boolean, String) -> Unit)? = null) {
        if (!isSyncing.compareAndSet(false, true)) {
            onComplete?.invoke(false, "ইতিমধ্যেই সিঙ্ক প্রক্রিয়া চলমান রয়েছে...")
            return
        }

        scope.launch {
            try {
                _syncState.value = _syncState.value.copy(
                    status = SyncStatus.SYNCING,
                    statusText = "সিঙ্ক হচ্ছে..."
                )

                val state = executeSyncCycle()
                if (onComplete != null) {
                    withContext(Dispatchers.Main) {
                        onComplete(
                            state.isCloudConfigured,
                            if (state.isCloudConfigured) "ক্লাউড সিঙ্ক সফলভাবে সম্পন্ন হয়েছে!" else state.statusText
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed: ${e.message}", e)
                _syncState.value = _syncState.value.copy(
                    status = SyncStatus.ERROR,
                    statusText = "সিঙ্ক ব্যর্থ: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}",
                    errorMessage = e.message
                )
                if (onComplete != null) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, e.localizedMessage ?: "সিঙ্ক ব্যর্থ")
                    }
                }
            } finally {
                isSyncing.set(false)
            }
        }
    }

    suspend fun countPendingLocalRecords(): Int {
        val sales = db.saleDao().getRealSalesCount()
        val customers = db.customerDao().getRealCustomersCount()
        val products = db.productDao().getRealProductsCount()
        val receipts = db.paymentReceiptDao().getRealReceiptsCount()
        val expenses = db.expenseDao().getRealExpensesCount()
        return sales + customers + products + receipts + expenses
    }

    /**
     * Executes real bidirectional synchronization with Firestore.
     */
    suspend fun performCloudSync() {
        if (FirebaseApp.getApps(context).isEmpty()) {
            throw IllegalStateException("ফায়ারবেস কনফিগারেশন অনুপস্থিত (google-services.json প্রয়োজন)।")
        }

        val firestore = FirebaseFirestore.getInstance()
        val businessId = resolveOrFetchBusinessId()
        val isSalesman = isCurrentActiveSalesman()

        // 0. Synchronize Business Profile & Staff Membership
        try {
            syncBusinessAndStaff(firestore, businessId, isSalesman)
        } catch (e: Exception) {
            Log.w(TAG, "Business profile & staff sync non-fatal note: ${e.message}")
        }

        // 1. Synchronize Deletion Tombstones first to protect against resurrecting deleted items
        try {
            syncTombstones(firestore, businessId, isSalesman)
        } catch (e: Exception) {
            Log.w(TAG, "Tombstones sync non-fatal note: ${e.message}")
        }

        // 2. Synchronize Products with stock and cost protection
        syncProducts(firestore, businessId, isSalesman)

        // 3. Synchronize Customers and due balances
        syncCustomers(firestore, businessId)

        // 4. Synchronize Sales invoices (idempotent, no duplicates)
        syncSales(firestore, businessId)

        // 5. Synchronize Payment Receipts (idempotent, no duplicates)
        syncPayments(firestore, businessId)

        // 6. Synchronize Expenses (download first, role-protected upload)
        syncExpenses(firestore, businessId, isSalesman)

        // 7. Synchronize Product Submissions (download first, role-protected upload)
        syncProductSubmissions(firestore, businessId, isSalesman)
    }

    private suspend fun syncBusinessAndStaff(firestore: FirebaseFirestore, businessId: String, isSalesman: Boolean) {
        val bizDocRef = firestore.collection("businesses").document(businessId)

        if (!isSalesman) {
            val authUser = FirebaseAuth.getInstance().currentUser
            val settings = db.companySettingsDao().getSettings() ?: CompanySettings()
            val bizData = mapOf(
                "id" to businessId,
                "ownerUid" to (authUser?.uid ?: businessId),
                "businessName" to settings.businessName,
                "phone" to settings.phone,
                "address" to settings.address,
                "currencySymbol" to settings.currencySymbol,
                "updatedAt" to System.currentTimeMillis()
            )
            bizDocRef.set(bizData, SetOptions.merge()).awaitResult()

            // Admin syncs active staff to Firestore under /businesses/{businessId}/staff/
            val activeUsers = db.userDao().getAllActiveUsersList()
            for (user in activeUsers) {
                if (user.role == UserRole.SALESMAN) {
                    val cleanPhone = CloudBusinessAuth.normalizePhone(user.phone)
                    val staffData = mapOf(
                        "id" to user.id,
                        "name" to user.name,
                        "phone" to cleanPhone,
                        "role" to user.role.name,
                        "isActive" to (user.isActive && user.accountStatus == UserAccountStatus.ACTIVE),
                        "canDiscount" to user.canDiscount,
                        "canViewCustomerDue" to user.canViewCustomerDue,
                        "canCollectPayment" to user.canCollectPayment,
                        "canApplyExpense" to user.canApplyExpense,
                        "businessId" to businessId,
                        "pinHash" to user.pinHash,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    bizDocRef.collection("staff").document(user.id).set(staffData, SetOptions.merge()).awaitResult()
                    if (cleanPhone.isNotBlank()) {
                        bizDocRef.collection("staff_by_phone").document(cleanPhone).set(staffData, SetOptions.merge()).awaitResult()
                    }
                }
            }
        } else {
            // Salesman fetches business profile and syncs business name
            try {
                val bizDoc = bizDocRef.get().awaitResult()
                if (bizDoc.exists()) {
                    val bName = bizDoc.getString("businessName")
                    val bPhone = bizDoc.getString("phone")
                    if (!bName.isNullOrBlank()) {
                        val currentSettings = db.companySettingsDao().getSettings() ?: CompanySettings()
                        db.companySettingsDao().insertOrUpdate(
                            currentSettings.copy(
                                businessName = bName,
                                phone = bPhone ?: currentSettings.phone
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            // Salesman fetches own staff record to sync permissions configured by Admin
            val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
            if (authUser != null) {
                try {
                    val staffDoc = bizDocRef.collection("staff").document(authUser.uid).get().awaitResult()
                    if (staffDoc.exists()) {
                        applyRemoteStaffDoc(staffDoc)
                    } else {
                        val localSalesman = db.userDao().getUserById(authUser.uid)
                        val phoneToSearch = localSalesman?.phone?.let { CloudBusinessAuth.normalizePhone(it) }
                        if (!phoneToSearch.isNullOrBlank()) {
                            val phoneDoc = bizDocRef.collection("staff_by_phone").document(phoneToSearch).get().awaitResult()
                            if (phoneDoc.exists()) {
                                applyRemoteStaffDoc(phoneDoc)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private suspend fun applyRemoteStaffDoc(doc: DocumentSnapshot) {
        val data = doc.data ?: return
        val id = doc.getString("id") ?: doc.id
        val name = doc.getString("name") ?: ""
        val phone = doc.getString("phone") ?: ""
        val roleStr = doc.getString("role") ?: UserRole.SALESMAN.name
        val role = try { UserRole.valueOf(roleStr) } catch (_: Exception) { UserRole.SALESMAN }
        val isActive = doc.getBoolean("isActive") ?: true
        val canDiscount = doc.getBoolean("canDiscount") ?: true
        val canViewCustomerDue = doc.getBoolean("canViewCustomerDue") ?: true
        val canCollectPayment = doc.getBoolean("canCollectPayment") ?: true
        val canApplyExpense = doc.getBoolean("canApplyExpense") ?: true
        val pinHash = doc.getString("pinHash") ?: ""

        val existingUser = db.userDao().getUserById(id)
        if (existingUser != null) {
            val updated = existingUser.copy(
                name = if (name.isNotBlank()) name else existingUser.name,
                phone = if (phone.isNotBlank()) phone else existingUser.phone,
                role = role,
                isActive = isActive,
                accountStatus = if (isActive) UserAccountStatus.ACTIVE else UserAccountStatus.DEACTIVATED,
                canDiscount = canDiscount,
                canViewCustomerDue = canViewCustomerDue,
                canCollectPayment = canCollectPayment,
                canApplyExpense = canApplyExpense
            )
            db.userDao().updateUser(updated)
        } else {
            val newUser = User(
                id = id,
                name = if (name.isNotBlank()) name else "Salesman",
                phone = phone,
                role = role,
                pinHash = pinHash,
                isActive = isActive,
                accountStatus = if (isActive) UserAccountStatus.ACTIVE else UserAccountStatus.DEACTIVATED,
                canDiscount = canDiscount,
                canViewCustomerDue = canViewCustomerDue,
                canCollectPayment = canCollectPayment,
                canApplyExpense = canApplyExpense
            )
            db.userDao().insertUser(newUser)
        }
    }

    private suspend fun syncTombstones(firestore: FirebaseFirestore, businessId: String, isSalesman: Boolean) {
        val collection = firestore.collection("businesses").document(businessId).collection("tombstones")

        // Upload local tombstones (Only Admin can create/update tombstones)
        if (!isSalesman) {
            val localTombstones = db.recycleBinDao().getSyncTombstones()
            for (item in localTombstones) {
                val isPerm = item.itemType == "PERMANENT_TOMBSTONE" || item.deleteReason.contains("Tombstone", ignoreCase = true)
                val tombId = "tomb_${item.originalTable}_${item.originalId}"
                val tombstone = Tombstone(
                    id = tombId,
                    tableName = item.originalTable,
                    originalId = item.originalId,
                    deletedAt = item.deletedAt,
                    isPermanent = isPerm,
                    deletedByUserId = item.deletedByAdminId
                )
                val data = mapTombstoneToCloud(tombstone, businessId)
                collection.document(tombstone.id).set(data, SetOptions.merge()).awaitResult()

                // If permanent tombstone, also ensure the document in the target collection is deleted from Firestore
                if (isPerm) {
                    try {
                        firestore.collection("businesses").document(businessId)
                            .collection(item.originalTable).document(item.originalId)
                            .delete().awaitResult()
                        Log.i(TAG, "Tombstone sync: Deleted /businesses/$businessId/${item.originalTable}/${item.originalId} from Firestore")
                    } catch (delEx: Exception) {
                        Log.w(TAG, "Tombstone sync doc delete non-fatal note: ${delEx.message}")
                    }
                }
            }
        }

        // Download remote tombstones
        val snapshot = collection.get().awaitResult()
        for (doc in snapshot.documents) {
            val origTable = doc.getString("tableName") ?: continue
            val origId = doc.getString("originalId") ?: continue
            val tombId = doc.id
            val isPermanent = doc.getBoolean("isPermanent") ?: false
            val existing = db.recycleBinDao().getItemByOriginalId(origTable, origId)
            if (existing == null) {
                db.recycleBinDao().insertItem(
                    RecycleBinItem(
                        id = tombId,
                        originalTable = origTable,
                        originalId = origId,
                        itemName = "মুছে ফেলা রেকর্ড ($origTable #$origId)",
                        itemType = if (isPermanent) "PERMANENT_TOMBSTONE" else "TOMBSTONE",
                        itemDetails = "ক্লাউড থেকে প্রাপ্ত টম্বস্টোন",
                        snapshotJson = "{}",
                        deletedByAdminId = doc.getString("deletedByUserId") ?: "",
                        deletedByAdminName = "Remote Device",
                        deleteReason = if (isPermanent) "PERMANENT_TOMBSTONE" else "ক্লাউড টম্বস্টোন সিঙ্ক",
                        deletedAt = doc.getLong("deletedAt") ?: System.currentTimeMillis(),
                        isRestored = false
                    )
                )
                // If it's permanent, delete the local record immediately from Room as well!
                if (isPermanent && origTable == "products") {
                    db.productDao().deleteProductPermanently(origId)
                }
            } else if (isPermanent && existing.itemType != "PERMANENT_TOMBSTONE") {
                db.recycleBinDao().updateItem(
                    existing.copy(
                        itemType = "PERMANENT_TOMBSTONE",
                        deleteReason = "PERMANENT_TOMBSTONE",
                        isRestored = false
                    )
                )
                if (origTable == "products") {
                    db.productDao().deleteProductPermanently(origId)
                }
            }
        }
    }

    suspend fun applyRemoteProductMap(data: Map<String, Any?>, isSalesman: Boolean) {
        val id = data["id"] as? String ?: return
        val tombstone = db.recycleBinDao().getItemByOriginalId("products", id)
        if (tombstone != null && !tombstone.isRestored) {
            return
        }

        val name = data["name"] as? String ?: return
        val sku = data["sku"] as? String ?: ""
        val barcode = data["barcode"] as? String ?: ""
        val category = data["category"] as? String ?: "সাধারণ"
        val sellingPrice = (data["sellingPrice"] as? Number)?.toDouble() ?: 0.0
        val currentStock = (data["currentStock"] as? Number)?.toDouble() ?: 0.0
        val alertStockLevel = (data["alertStockLevel"] as? Number)?.toDouble() ?: 5.0
        val unit = data["unit"] as? String ?: "পিস"
        val isActive = data["isActive"] as? Boolean ?: true
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val remotePurchasePrice = (data["purchasePrice"] as? Number)?.toDouble()
        val imageUri = (data["imageUri"] as? String)?.takeIf { it.isNotBlank() }
        val imageSource = data["imageSource"] as? String ?: "NONE"
        val imageSourceInfo = data["imageSourceInfo"] as? String ?: ""

        val localProduct = db.productDao().getProductById(id)
        if (localProduct != null) {
            // Last-write-wins by timestamp
            if (updatedAt >= localProduct.updatedAt) {
                val merged = localProduct.copy(
                    name = name,
                    sku = sku,
                    barcode = barcode,
                    category = category,
                    sellingPrice = sellingPrice,
                    currentStock = currentStock,
                    alertStockLevel = alertStockLevel,
                    unit = unit,
                    isActive = isActive,
                    updatedAt = updatedAt,
                    imageUri = imageUri ?: localProduct.imageUri,
                    imageSource = if (imageSource != "NONE") imageSource else localProduct.imageSource,
                    imageSourceInfo = if (imageSourceInfo.isNotBlank()) imageSourceInfo else localProduct.imageSourceInfo,
                    // Purchase price protection: salesman never overwrites or gets exposed to purchase cost
                    purchasePrice = if (isSalesman) (localProduct.purchasePrice.takeIf { it > 0 } ?: 0.0) else (remotePurchasePrice ?: localProduct.purchasePrice)
                )
                db.productDao().updateProduct(merged)
            }
        } else {
            // If the product does not exist locally and remote isActive is false, do not insert it into active stock
            if (!isActive) {
                return
            }
            val newProduct = Product(
                id = id,
                name = name,
                sku = sku,
                barcode = barcode,
                category = category,
                purchasePrice = if (isSalesman) 0.0 else (remotePurchasePrice ?: 0.0),
                sellingPrice = sellingPrice,
                currentStock = currentStock,
                alertStockLevel = alertStockLevel,
                unit = unit,
                isActive = isActive,
                updatedAt = updatedAt,
                imageUri = imageUri,
                imageSource = imageSource,
                imageSourceInfo = imageSourceInfo,
                isTestData = false
            )
            db.productDao().insertProduct(newProduct)
        }
    }

    private suspend fun syncProducts(firestore: FirebaseFirestore, businessId: String, isSalesman: Boolean) {
        val collection = firestore.collection("businesses").document(businessId).collection("products")

        // 1. Upload local real products: STRICTLY OWNER / ADMIN ONLY
        if (!isSalesman) {
            val localProducts = db.productDao().getSyncProducts()
            for (product in localProducts) {
                if (product.isTestData) continue
                val tomb = db.recycleBinDao().getItemByOriginalId("products", product.id)
                if (tomb != null && !tomb.isRestored && tomb.itemType == "PERMANENT_TOMBSTONE") {
                    continue
                }
                val data = mapProductToCloud(product, businessId, isSalesman = false)
                collection.document(product.id).set(data, SetOptions.merge()).awaitResult()
            }
        }

        // 2. Download and merge remote products: BOTH ADMIN AND SALESMAN DOWNLOAD
        val snapshot = collection.get().awaitResult()
        for (doc in snapshot.documents) {
            val data = doc.data?.toMutableMap() ?: mutableMapOf()
            if (!data.containsKey("id")) data["id"] = doc.id
            applyRemoteProductMap(data, isSalesman)
        }
    }

    private suspend fun syncCustomers(firestore: FirebaseFirestore, businessId: String) {
        val collection = firestore.collection("businesses").document(businessId).collection("customers")

        // 1. Upload local real customers
        val localCustomers = db.customerDao().getSyncCustomers()
        for (customer in localCustomers) {
            val data = mapCustomerToCloud(customer, businessId)
            collection.document(customer.id).set(data, SetOptions.merge()).awaitResult()
        }

        // 2. Download remote customers
        val snapshot = collection.get().awaitResult()
        for (doc in snapshot.documents) {
            val id = doc.getString("id") ?: doc.id
            val tombstone = db.recycleBinDao().getItemByOriginalId("customers", id)
            if (tombstone != null && !tombstone.isRestored) {
                continue
            }

            val name = doc.getString("name") ?: continue
            val phone = doc.getString("phone") ?: ""
            val address = doc.getString("address") ?: ""
            val totalBilled = doc.getDouble("totalBilled") ?: 0.0
            val totalPaid = doc.getDouble("totalPaid") ?: 0.0
            val currentDue = doc.getDouble("currentDue") ?: 0.0
            val openingDue = doc.getDouble("openingDue") ?: 0.0
            val isDeleted = doc.getBoolean("isDeleted") ?: false
            val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

            val localCustomer = db.customerDao().getCustomerById(id)
            if (localCustomer != null) {
                if (updatedAt >= localCustomer.updatedAt) {
                    val merged = localCustomer.copy(
                        name = name,
                        phone = phone,
                        address = address,
                        totalBilled = totalBilled,
                        totalPaid = totalPaid,
                        currentDue = currentDue,
                        openingDue = openingDue,
                        isDeleted = isDeleted,
                        updatedAt = updatedAt
                    )
                    db.customerDao().updateCustomer(merged)
                }
            } else {
                val newCustomer = Customer(
                    id = id,
                    name = name,
                    phone = phone,
                    address = address,
                    totalBilled = totalBilled,
                    totalPaid = totalPaid,
                    currentDue = currentDue,
                    openingDue = openingDue,
                    isDeleted = isDeleted,
                    updatedAt = updatedAt,
                    isTestData = false
                )
                db.customerDao().insertCustomer(newCustomer)
            }
        }
    }

    private suspend fun syncSales(firestore: FirebaseFirestore, businessId: String) {
        val collection = firestore.collection("businesses").document(businessId).collection("sales")

        // 1. Upload local sales with items
        val localSales = db.saleDao().getSyncSales()
        for (sale in localSales) {
            val items = db.saleDao().getSaleItems(sale.invoiceNumber)
            val data = mapSaleToCloud(sale, items, businessId)
            collection.document(sale.invoiceNumber).set(data, SetOptions.merge()).awaitResult()
        }

        // 2. Download remote sales (idempotent, never duplicates)
        val snapshot = collection.get().awaitResult()
        for (doc in snapshot.documents) {
            val invoiceNumber = doc.getString("invoiceNumber") ?: doc.id
            val tombstone = db.recycleBinDao().getItemByOriginalId("sales", invoiceNumber)
            if (tombstone != null && !tombstone.isRestored) {
                continue
            }

            val existing = db.saleDao().getSaleByInvoice(invoiceNumber)
            if (existing == null) {
                val customerId = doc.getString("customerId") ?: ""
                val customerName = doc.getString("customerName") ?: ""
                val customerPhone = doc.getString("customerPhone") ?: ""
                val salesmanId = doc.getString("salesmanId") ?: ""
                val salesmanName = doc.getString("salesmanName") ?: ""
                val subtotal = doc.getDouble("subtotal") ?: 0.0
                val discount = doc.getDouble("discount") ?: 0.0
                val totalAmount = doc.getDouble("totalAmount") ?: 0.0
                val paidAmount = doc.getDouble("paidAmount") ?: 0.0
                val dueAmount = doc.getDouble("dueAmount") ?: 0.0
                val paymentMethodStr = doc.getString("paymentMethod") ?: PaymentMethod.CASH.name
                val method = try { PaymentMethod.valueOf(paymentMethodStr) } catch (e: Exception) { PaymentMethod.CASH }
                val isDeleted = doc.getBoolean("isDeleted") ?: false
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                val newSale = Sale(
                    invoiceNumber = invoiceNumber,
                    customerId = customerId,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    salesmanId = salesmanId,
                    salesmanName = salesmanName,
                    subtotal = subtotal,
                    discount = discount,
                    totalAmount = totalAmount,
                    paidAmount = paidAmount,
                    dueAmount = dueAmount,
                    paymentMethod = method,
                    isDeleted = isDeleted,
                    createdAt = createdAt,
                    isTestData = false
                )
                db.saleDao().insertSale(newSale)

                // Insert associated line items if present
                val rawItems = doc.get("items") as? List<Map<String, Any?>>
                if (rawItems != null && rawItems.isNotEmpty()) {
                    val itemsList = rawItems.mapNotNull { itemMap ->
                        val pId = itemMap["productId"] as? String ?: return@mapNotNull null
                        val pName = itemMap["productName"] as? String ?: ""
                        val qty = (itemMap["quantity"] as? Number)?.toDouble() ?: 0.0
                        val uPrice = (itemMap["unitPrice"] as? Number)?.toDouble() ?: 0.0
                        val pPurchase = (itemMap["purchasePriceAtSale"] as? Number)?.toDouble() ?: 0.0
                        val tPrice = (itemMap["totalPrice"] as? Number)?.toDouble() ?: (qty * uPrice)
                        SaleItem(
                            invoiceNumber = invoiceNumber,
                            productId = pId,
                            productName = pName,
                            quantity = qty,
                            unitPrice = uPrice,
                            purchasePriceAtSale = pPurchase,
                            totalPrice = tPrice
                        )
                    }
                    if (itemsList.isNotEmpty()) {
                        db.saleDao().insertSaleItems(itemsList)
                    }
                }
            }
        }
    }

    private suspend fun syncPayments(firestore: FirebaseFirestore, businessId: String) {
        val collection = firestore.collection("businesses").document(businessId).collection("payments")

        // 1. Upload local payments
        val localReceipts = db.paymentReceiptDao().getSyncReceipts()
        for (receipt in localReceipts) {
            val data = mapReceiptToCloud(receipt, businessId)
            collection.document(receipt.receiptNumber).set(data, SetOptions.merge()).awaitResult()
        }

        // 2. Download remote payments (idempotent, never duplicates)
        val snapshot = collection.get().awaitResult()
        for (doc in snapshot.documents) {
            val receiptNumber = doc.getString("receiptNumber") ?: doc.id
            val tombstone = db.recycleBinDao().getItemByOriginalId("payments", receiptNumber)
            if (tombstone != null && !tombstone.isRestored) {
                continue
            }

            val existing = db.paymentReceiptDao().getReceiptByNumber(receiptNumber)
            if (existing == null) {
                val invoiceNumber = doc.getString("invoiceNumber") ?: ""
                val customerId = doc.getString("customerId") ?: ""
                val customerName = doc.getString("customerName") ?: ""
                val salesmanId = doc.getString("salesmanId") ?: ""
                val amount = doc.getDouble("amount") ?: 0.0
                val paymentMethodStr = doc.getString("paymentMethod") ?: PaymentMethod.CASH.name
                val method = try { PaymentMethod.valueOf(paymentMethodStr) } catch (e: Exception) { PaymentMethod.CASH }
                val isDeleted = doc.getBoolean("isDeleted") ?: false
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                val customerPhone = doc.getString("customerPhone") ?: ""
                val salesmanName = doc.getString("salesmanName") ?: ""
                val previousDue = doc.getDouble("previousDue") ?: 0.0
                val currentDue = doc.getDouble("currentDue") ?: 0.0
                val note = doc.getString("note") ?: ""

                val newReceipt = PaymentReceipt(
                    receiptNumber = receiptNumber,
                    invoiceNumber = invoiceNumber,
                    customerId = customerId,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    salesmanId = salesmanId,
                    salesmanName = salesmanName,
                    amount = amount,
                    previousDue = previousDue,
                    currentDue = currentDue,
                    paymentMethod = method,
                    note = note,
                    isDeleted = isDeleted,
                    createdAt = createdAt,
                    isTestData = false
                )
                db.paymentReceiptDao().insertReceipt(newReceipt)
            }
        }
    }

    private suspend fun syncExpenses(firestore: FirebaseFirestore, businessId: String, isSalesman: Boolean) {
        val collection = firestore.collection("businesses").document(businessId).collection("expenses")
        Log.i(TAG, "[syncExpenses] Starting sync for /businesses/$businessId/expenses (isSalesman=$isSalesman)")

        // 1. Download remote expenses FIRST so we have latest server approvals and status
        var remoteExpenseDocs: List<DocumentSnapshot> = emptyList()
        var remoteFetchSucceeded = false
        try {
            val snapshot = collection.get().awaitResult()
            remoteExpenseDocs = snapshot.documents
            remoteFetchSucceeded = true
            Log.i(TAG, "[syncExpenses] Downloaded ${snapshot.size()} expenses from /businesses/$businessId/expenses")
            for (doc in snapshot.documents) {
                applyRemoteExpenseDoc(doc)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[syncExpenses] Download remote expenses failed: ${e.message}", e)
        }

        val remoteExpensesById = remoteExpenseDocs.associateBy { it.id }

        // 2. If admin, upload local authoritative reviews/approvals/rejections or newly created admin expenses
        if (!isSalesman) {
            val localExpenses = db.expenseDao().getSyncExpenses()
            for (expense in localExpenses) {
                // ABSOLUTE RULE: Admin must NEVER upload an expense with status PENDING!
                // An Admin only approves or rejects expenses, or creates approved expenses.
                // An unreviewed PENDING expense belongs to a salesman submission and must NEVER be uploaded by admin.
                if (expense.status == ExpenseStatus.PENDING) {
                    continue
                }

                val remoteDoc = remoteExpensesById[expense.id]
                val remoteStatusStr = remoteDoc?.getString("status")
                val remoteReviewedAt = remoteDoc?.getLong("reviewedAt") ?: 0L

                // Only upload if doc does not exist remotely, or status changed remotely,
                // or local reviewedAt is newer than remote reviewedAt
                val shouldUpload = remoteDoc == null ||
                        remoteStatusStr != expense.status.name ||
                        (expense.reviewedAt != null && remoteReviewedAt < expense.reviewedAt)

                if (shouldUpload) {
                    try {
                        val data = mapExpenseToCloud(expense, businessId)
                        collection.document(expense.id).set(data, SetOptions.merge()).awaitResult()
                        Log.i(TAG, "[syncExpenses] Admin uploaded authoritative expense ${expense.id} with status ${expense.status}")
                    } catch (e: Exception) {
                        Log.e(TAG, "[syncExpenses] Admin review upload failed for ${expense.id}: ${e.message}", e)
                    }
                }
            }
        }

        // 3. If salesman: ONLY upload local PENDING expenses that DO NOT already exist in cloud!
        if (isSalesman) {
            val localExpenses = db.expenseDao().getSyncExpenses()
            for (expense in localExpenses) {
                // Salesman NEVER re-uploads approved, rejected, or deleted expenses
                if (expense.status != ExpenseStatus.PENDING || expense.isDeleted) {
                    continue
                }
                // If it already exists in Firestore, NEVER re-upload from salesman to prevent overwriting cloud approvals
                if (remoteExpensesById.containsKey(expense.id)) {
                    continue
                }
                // If remote fetch failed, do not blindly upload all local records to avoid overwriting cloud state
                if (!remoteFetchSucceeded) {
                    continue
                }
                try {
                    val data = mapExpenseToCloud(expense, businessId)
                    collection.document(expense.id).set(data, SetOptions.merge()).awaitResult()
                    Log.i(TAG, "[syncExpenses] Salesman uploaded new offline expense ${expense.id} to /businesses/$businessId/expenses")
                } catch (e: Exception) {
                    Log.e(TAG, "[syncExpenses] Salesman expense upload failed for ${expense.id}: ${e.message}", e)
                }
            }
        }
    }

    suspend fun applyRemoteExpenseDoc(doc: DocumentSnapshot) {
        val data = doc.data?.toMutableMap() ?: mutableMapOf()
        if (!data.containsKey("id")) data["id"] = doc.id
        applyRemoteExpenseMap(data)
    }

    suspend fun applyRemoteExpenseMap(data: Map<String, Any?>) {
        val id = data["id"] as? String ?: return
        val tombstone = db.recycleBinDao().getItemByOriginalId("expenses", id)
        if (tombstone != null && !tombstone.isRestored) {
            return
        }

        val salesmanId = data["salesmanId"] as? String ?: ""
        val salesmanName = data["salesmanName"] as? String ?: ""
        val categoryStr = data["category"] as? String ?: ExpenseCategory.OTHER.name
        val category = try { ExpenseCategory.valueOf(categoryStr) } catch (e: Exception) { ExpenseCategory.OTHER }
        val sourceStr = data["source"] as? String ?: ExpenseSource.COMPANY_CASH.name
        val source = try { ExpenseSource.valueOf(sourceStr) } catch (e: Exception) { ExpenseSource.COMPANY_CASH }
        val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
        val description = data["description"] as? String ?: ""
        val statusStr = data["status"] as? String ?: ExpenseStatus.PENDING.name
        val status = try { ExpenseStatus.valueOf(statusStr) } catch (e: Exception) { ExpenseStatus.PENDING }
        val adminNote = data["adminNote"] as? String ?: ""
        val isDeleted = data["isDeleted"] as? Boolean ?: false
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val reviewedAt = (data["reviewedAt"] as? Number)?.toLong()

        val existing = db.expenseDao().getExpenseById(id)
        if (existing == null) {
            val newExpense = Expense(
                id = id,
                salesmanId = salesmanId,
                salesmanName = salesmanName,
                category = category,
                source = source,
                amount = amount,
                description = description,
                status = status,
                adminNote = adminNote,
                isDeleted = isDeleted,
                createdAt = createdAt,
                reviewedAt = reviewedAt,
                isTestData = false
            )
            db.expenseDao().insertExpense(newExpense)
        } else {
            // Guard: If locally reviewed (APPROVED or REJECTED),
            // never allow a stale remote PENDING snapshot to overwrite or downgrade it!
            val isLocalReviewed = existing.status != ExpenseStatus.PENDING
            val isRemotePending = status == ExpenseStatus.PENDING
            if (isLocalReviewed && isRemotePending) {
                Log.w(TAG, "[applyRemoteExpenseMap] Blocked stale remote PENDING overwrite for reviewed expense: $id")
                return
            }
            if (existing.status == ExpenseStatus.APPROVED && status == ExpenseStatus.PENDING) {
                Log.w(TAG, "[applyRemoteExpenseMap] Blocked attempt to revert APPROVED expense: $id to PENDING")
                return
            }
            if (existing.reviewedAt != null && reviewedAt != null && reviewedAt < existing.reviewedAt) {
                Log.w(TAG, "[applyRemoteExpenseMap] Blocked older review timestamp overwrite for expense: $id")
                return
            }
            if (existing.status != ExpenseStatus.PENDING && reviewedAt == null && status == ExpenseStatus.PENDING) {
                Log.w(TAG, "[applyRemoteExpenseMap] Blocked unreviewed payload for reviewed expense: $id")
                return
            }

            // Update local expense if status, note, review timestamp, amount, description or deletion state changed remotely
            if (existing.status != status ||
                existing.adminNote != adminNote ||
                existing.reviewedAt != reviewedAt ||
                existing.isDeleted != isDeleted ||
                existing.amount != amount ||
                existing.description != description) {
                val updatedExpense = existing.copy(
                    salesmanName = if (salesmanName.isNotBlank()) salesmanName else existing.salesmanName,
                    category = category,
                    source = source,
                    amount = amount,
                    description = description,
                    status = status,
                    adminNote = adminNote,
                    reviewedAt = reviewedAt ?: existing.reviewedAt,
                    isDeleted = isDeleted
                )
                db.expenseDao().updateExpense(updatedExpense)
                Log.i(TAG, "[applyRemoteExpenseMap] Updated local expense $id to status $status")
            }
        }
    }

    private suspend fun syncProductSubmissions(firestore: FirebaseFirestore, businessId: String, isSalesman: Boolean) {
        val collection = firestore.collection("businesses").document(businessId).collection("product_submissions")
        Log.i(TAG, "[syncProductSubmissions] Starting sync for /businesses/$businessId/product_submissions (isSalesman=$isSalesman)")

        // 1. Download remote product submissions FIRST
        var remoteSubmissionDocs: List<DocumentSnapshot> = emptyList()
        try {
            val snapshot = collection.get().awaitResult()
            remoteSubmissionDocs = snapshot.documents
            Log.i(TAG, "[syncProductSubmissions] Downloaded ${snapshot.size()} remote submissions from /businesses/$businessId/product_submissions")
            for (doc in snapshot.documents) {
                applyRemoteSubmissionDoc(doc)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[syncProductSubmissions] Download remote submissions failed: ${e.message}", e)
        }

        val remoteSubmissionsById = remoteSubmissionDocs.associateBy { it.id }

        // 2. Synchronize local submissions with Firestore
        val localSubmissions = db.productSubmissionDao().getSyncSubmissions()
        for (sub in localSubmissions) {
            val remoteDoc = remoteSubmissionsById[sub.id]
            val remoteStatusStr = remoteDoc?.getString("status")
            val remoteUpdatedAt = remoteDoc?.getLong("updatedAt") ?: 0L

            when (sub.status) {
                ProductSubmissionStatus.PENDING -> {
                    // Rule: Any device holding a PENDING submission that is not yet in cloud,
                    // or locally updated, must upload it so Admin can review it.
                    val shouldUpload = remoteDoc == null ||
                            (remoteStatusStr == ProductSubmissionStatus.PENDING.name && sub.updatedAt > remoteUpdatedAt)
                    if (shouldUpload) {
                        try {
                            val data = mapProductSubmissionToCloud(sub, businessId)
                            collection.document(sub.id).set(data, SetOptions.merge()).awaitResult()
                            Log.i(TAG, "[syncProductSubmissions] Uploaded pending submission '${sub.name}' (${sub.id}) to /businesses/$businessId/product_submissions")
                        } catch (e: Exception) {
                            Log.e(TAG, "[syncProductSubmissions] Upload failed for pending submission '${sub.name}' (${sub.id}): ${e.message}", e)
                        }
                    }
                }
                ProductSubmissionStatus.APPROVED -> {
                    // Admin authoritative approval upload
                    if (!isSalesman) {
                        val shouldUpload = remoteDoc == null ||
                                remoteStatusStr != sub.status.name ||
                                remoteUpdatedAt < sub.updatedAt
                        if (shouldUpload) {
                            try {
                                val data = mapProductSubmissionToCloud(sub, businessId)
                                collection.document(sub.id).set(data, SetOptions.merge()).awaitResult()
                                Log.i(TAG, "[syncProductSubmissions] Admin uploaded authoritative approval for submission ${sub.id}")
                            } catch (e: Exception) {
                                Log.e(TAG, "[syncProductSubmissions] Admin approval upload failed for ${sub.id}: ${e.message}", e)
                            }
                        }
                    }
                }
                ProductSubmissionStatus.NEEDS_CORRECTION -> {
                    // Either admin returned it for correction, or salesman uploaded revised item
                    val shouldUpload = remoteDoc == null ||
                            remoteStatusStr != sub.status.name ||
                            remoteUpdatedAt < sub.updatedAt
                    if (shouldUpload) {
                        try {
                            val data = mapProductSubmissionToCloud(sub, businessId)
                            collection.document(sub.id).set(data, SetOptions.merge()).awaitResult()
                            Log.i(TAG, "[syncProductSubmissions] Uploaded correction state for submission ${sub.id}")
                        } catch (e: Exception) {
                            Log.e(TAG, "[syncProductSubmissions] Correction state upload failed for ${sub.id}: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    suspend fun applyRemoteSubmissionDoc(doc: DocumentSnapshot) {
        val data = doc.data?.toMutableMap() ?: mutableMapOf()
        if (!data.containsKey("id")) data["id"] = doc.id
        applyRemoteSubmissionMap(data)
    }

    suspend fun applyRemoteSubmissionMap(data: Map<String, Any?>) {
        val id = data["id"] as? String ?: return
        val name = data["name"] as? String ?: ""
        if (name.isBlank()) return
        val productCode = data["productCode"] as? String ?: ""
        val category = data["category"] as? String ?: "সাধারণ"
        val purchasePrice = (data["purchasePrice"] as? Number)?.toDouble() ?: 0.0
        val sellingPrice = (data["sellingPrice"] as? Number)?.toDouble() ?: 0.0
        val initialStock = (data["initialStock"] as? Number)?.toDouble() ?: 0.0
        val unit = data["unit"] as? String ?: "পিস"
        val description = data["description"] as? String ?: ""
        val salesmanId = data["salesmanId"] as? String ?: ""
        val salesmanName = data["salesmanName"] as? String ?: ""
        val salesmanPhone = data["salesmanPhone"] as? String ?: ""
        val statusStr = data["status"] as? String ?: ProductSubmissionStatus.PENDING.name
        val status = try { ProductSubmissionStatus.valueOf(statusStr) } catch (e: Exception) { ProductSubmissionStatus.PENDING }
        val adminCorrectionNote = data["adminCorrectionNote"] as? String ?: ""
        val approvedProductId = data["approvedProductId"] as? String
        val reviewedByAdminId = data["reviewedByAdminId"] as? String
        val reviewedByAdminName = data["reviewedByAdminName"] as? String
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val reviewedAt = (data["reviewedAt"] as? Number)?.toLong()
        val imageUri = data["imageUri"] as? String
        val imageSource = data["imageSource"] as? String ?: "NONE"
        val imageSourceInfo = data["imageSourceInfo"] as? String ?: ""

        val existing = db.productSubmissionDao().getSubmissionById(id)
        if (existing == null) {
            val newSub = ProductSubmission(
                id = id,
                name = name,
                productCode = productCode,
                category = category,
                purchasePrice = purchasePrice,
                sellingPrice = sellingPrice,
                initialStock = initialStock,
                unit = unit,
                description = description,
                salesmanId = salesmanId,
                salesmanName = salesmanName,
                salesmanPhone = salesmanPhone,
                status = status,
                adminCorrectionNote = adminCorrectionNote,
                approvedProductId = approvedProductId,
                reviewedByAdminId = reviewedByAdminId,
                reviewedByAdminName = reviewedByAdminName,
                createdAt = createdAt,
                updatedAt = updatedAt,
                reviewedAt = reviewedAt,
                imageUri = imageUri,
                imageSource = imageSource,
                imageSourceInfo = imageSourceInfo
            )
            db.productSubmissionDao().insertSubmission(newSub)
        } else {
            // Guard: If locally APPROVED by Admin, never let a stale remote PENDING snapshot revert it!
            val isLocalApproved = existing.status == ProductSubmissionStatus.APPROVED
            val isRemotePending = status == ProductSubmissionStatus.PENDING
            if (isLocalApproved && isRemotePending) {
                return
            }

            // Update if status, admin notes, review data, product fields, or updatedAt changed remotely
            if (existing.status != status ||
                existing.adminCorrectionNote != adminCorrectionNote ||
                existing.approvedProductId != approvedProductId ||
                existing.reviewedAt != reviewedAt ||
                existing.purchasePrice != purchasePrice ||
                existing.sellingPrice != sellingPrice ||
                existing.initialStock != initialStock ||
                existing.name != name ||
                existing.productCode != productCode ||
                existing.category != category ||
                existing.unit != unit ||
                existing.description != description ||
                existing.updatedAt < updatedAt) {
                val updatedSub = existing.copy(
                    name = name,
                    productCode = productCode,
                    category = category,
                    purchasePrice = purchasePrice,
                    sellingPrice = sellingPrice,
                    initialStock = initialStock,
                    unit = unit,
                    description = description,
                    status = status,
                    adminCorrectionNote = adminCorrectionNote,
                    approvedProductId = approvedProductId,
                    reviewedByAdminId = reviewedByAdminId,
                    reviewedByAdminName = reviewedByAdminName,
                    reviewedAt = reviewedAt ?: existing.reviewedAt,
                    updatedAt = maxOf(existing.updatedAt, updatedAt),
                    imageUri = imageUri ?: existing.imageUri,
                    imageSource = if (imageSource != "NONE") imageSource else existing.imageSource,
                    imageSourceInfo = if (imageSourceInfo.isNotBlank()) imageSourceInfo else existing.imageSourceInfo
                )
                db.productSubmissionDao().updateSubmission(updatedSub)
            }
        }
    }

    /**
     * Record a persistent deletion tombstone in Room's recycle_bin.
     * Prevents any other authorized device from resurrecting this deleted item.
     */
    suspend fun recordTombstone(
        originalTable: String,
        originalId: String,
        adminUserId: String,
        isPermanent: Boolean = false
    ) {
        val tombstone = Tombstone(
            id = "tomb_${originalTable}_${originalId}",
            tableName = originalTable,
            originalId = originalId,
            deletedAt = System.currentTimeMillis(),
            isPermanent = isPermanent,
            deletedByUserId = adminUserId
        )

        val existing = db.recycleBinDao().getItemByOriginalId(originalTable, originalId)
        if (existing == null) {
            db.recycleBinDao().insertItem(
                RecycleBinItem(
                    id = tombstone.id,
                    originalTable = originalTable,
                    originalId = originalId,
                    itemName = "মুছে ফেলা রেকর্ড ($originalTable #$originalId)",
                    itemType = "TOMBSTONE",
                    itemDetails = "টম্বস্টোন — স্থায়ীভাবে মুছে ফেলার রেকর্ড",
                    snapshotJson = "{}",
                    deletedByAdminId = adminUserId,
                    deletedByAdminName = "Admin",
                    deleteReason = if (isPermanent) "স্থায়ীভাবে মুছে ফেলা হয়েছে (Tombstone)" else "রিসাইকেল বিনে স্থানান্তর",
                    deletedAt = tombstone.deletedAt,
                    isRestored = false
                )
            )
        }
        Log.d(TAG, "Recorded persistent deletion tombstone for $originalTable #$originalId")
    }
}

/**
 * Manages device-local storage of the verified multi-tenant business identity.
 * Prevents hardcoding or fallback IDs, while ensuring salesmen on separate devices
 * resolve to the exact business authorized by the owner.
 */
object CloudBusinessAuth {
    private const val PREFS_NAME = "smart_biz_cloud_auth_prefs"
    private const val KEY_BUSINESS_ID = "active_cloud_business_id"
    private const val KEY_BUSINESS_NAME = "active_cloud_business_name"
    private const val KEY_USER_ROLE = "active_cloud_user_role"
    private const val KEY_AUTH_EMAIL = "active_cloud_auth_email"
    private const val KEY_IS_SUPER_ADMIN = "active_cloud_is_super_admin"

    fun isStoredSuperAdmin(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_SUPER_ADMIN, false)
    }

    fun setStoredSuperAdminFlag(context: Context, isSuperAdmin: Boolean) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (isSuperAdmin) {
            editor.putBoolean(KEY_IS_SUPER_ADMIN, true)
        } else {
            editor.remove(KEY_IS_SUPER_ADMIN)
        }
        editor.apply()
    }

    fun getStoredAuthEmail(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AUTH_EMAIL, null)?.trim()?.ifBlank { null }
    }

    fun setStoredAuthEmail(context: Context, email: String?) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (email.isNullOrBlank()) {
            editor.remove(KEY_AUTH_EMAIL)
        } else {
            editor.putString(KEY_AUTH_EMAIL, email.trim().lowercase())
        }
        editor.apply()
    }

    fun getStoredBusinessId(context: Context): String? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BUSINESS_ID, null)?.trim()?.ifBlank { null }
        if (raw == "admin_1" || raw == "salesman_1") return null
        return raw
    }

    fun getStoredRole(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_USER_ROLE, null)?.trim()?.ifBlank { null }
    }

    fun getStoredBusinessName(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BUSINESS_NAME, null)?.trim()?.ifBlank { null }
    }

    fun setStoredBusinessId(context: Context, businessId: String, businessName: String? = null, role: String? = null) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val clean = businessId.trim()
        if (clean.isNotBlank() && clean != "admin_1" && clean != "salesman_1") {
            editor.putString(KEY_BUSINESS_ID, clean)
        }
        if (!businessName.isNullOrBlank()) editor.putString(KEY_BUSINESS_NAME, businessName.trim())
        if (!role.isNullOrBlank()) editor.putString(KEY_USER_ROLE, role.trim())
        editor.apply()
    }

    fun convertBengaliDigits(raw: String): String {
        return raw.trim()
            .replace('০', '0').replace('১', '1').replace('২', '2')
            .replace('৩', '3').replace('৪', '4').replace('৫', '5')
            .replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')
    }

    fun normalizePhone(raw: String): String {
        val converted = convertBengaliDigits(raw)
        val digits = converted.replace(Regex("[^0-9+]"), "").trim()
        return when {
            digits.startsWith("+880") -> "0" + digits.substring(4)
            digits.startsWith("880") -> "0" + digits.substring(3)
            digits.startsWith("+88") -> digits.substring(3)
            digits.startsWith("88") && digits.length > 11 -> digits.substring(2)
            digits.length == 10 && digits.startsWith("1") -> "0$digits"
            else -> digits
        }
    }

    fun normalizeToE164(raw: String): String {
        val local = normalizePhone(raw)
        return when {
            local.startsWith("+880") -> local
            local.startsWith("01") && local.length == 11 -> "+88$local"
            local.startsWith("1") && local.length == 10 -> "+880$local"
            local.startsWith("+") -> local
            else -> if (local.isNotBlank()) "+880$local" else ""
        }
    }

    fun matchesPhone(phone1: String, phone2: String): Boolean {
        val p1 = normalizePhone(phone1)
        val p2 = normalizePhone(phone2)
        if (p1.isNotBlank() && p1 == p2) return true
        val e1 = normalizeToE164(phone1)
        val e2 = normalizeToE164(phone2)
        if (e1.isNotBlank() && e1 == e2) return true
        val last10_1 = p1.takeLast(10)
        val last10_2 = p2.takeLast(10)
        return last10_1.length == 10 && last10_1 == last10_2
    }

    fun setStoredRole(context: Context, role: String?) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (role.isNullOrBlank()) {
            editor.remove(KEY_USER_ROLE)
        } else {
            editor.putString(KEY_USER_ROLE, role.trim())
        }
        editor.apply()
    }

    fun setLinkedGoogleAccount(context: Context, uid: String, email: String?, userId: String, shopId: String) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val cleanUid = uid.trim()
        val cleanEmail = email?.trim()?.lowercase() ?: ""
        if (cleanUid.isNotBlank()) {
            editor.putString("linked_uid_user_$cleanUid", userId)
            editor.putString("linked_uid_shop_$cleanUid", shopId)
        }
        if (cleanEmail.isNotBlank()) {
            editor.putString("linked_email_user_$cleanEmail", userId)
            editor.putString("linked_email_shop_$cleanEmail", shopId)
        }
        editor.apply()
    }

    fun getLinkedUserId(context: Context, uid: String, email: String?): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanUid = uid.trim()
        val cleanEmail = email?.trim()?.lowercase() ?: ""
        val byUid = if (cleanUid.isNotBlank()) prefs.getString("linked_uid_user_$cleanUid", null) else null
        if (!byUid.isNullOrBlank()) return byUid
        val byEmail = if (cleanEmail.isNotBlank()) prefs.getString("linked_email_user_$cleanEmail", null) else null
        return byEmail?.ifBlank { null }
    }

    fun getLinkedShopId(context: Context, uid: String, email: String?): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanUid = uid.trim()
        val cleanEmail = email?.trim()?.lowercase() ?: ""
        val byUid = if (cleanUid.isNotBlank()) prefs.getString("linked_uid_shop_$cleanUid", null) else null
        if (!byUid.isNullOrBlank()) return byUid
        val byEmail = if (cleanEmail.isNotBlank()) prefs.getString("linked_email_shop_$cleanEmail", null) else null
        return byEmail?.ifBlank { null }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

