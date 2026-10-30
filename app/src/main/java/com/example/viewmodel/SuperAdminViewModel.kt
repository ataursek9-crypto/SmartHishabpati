package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.SuperAdminRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SuperAdminUiState(
    val selectedTab: SuperAdminTab = SuperAdminTab.OVERVIEW,
    val searchQuery: String = "",
    val businessStatusFilter: BusinessAccountStatus? = null,
    val paymentStatusFilter: PlatformPaymentStatus? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

enum class SuperAdminTab {
    OVERVIEW,       // ওভারভিউ ড্যাশবোর্ড
    BUSINESSES,     // ব্যবসা প্রতিষ্ঠানসমূহ
    PLANS,          // প্ল্যান ও সাবস্ক্রিপশন
    PAYMENTS,       // পেমেন্ট অনুমোদন
    AUDIT_LOGS,     // প্ল্যাটফর্ম অডিট লগ
    SETTINGS        // সিস্টেম সেটিংস ও হেলথ
}

class SuperAdminViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: SuperAdminRepository = SuperAdminRepository.getInstance(application)
) : AndroidViewModel(application) {

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SuperAdminViewModel::class.java)) {
                        return SuperAdminViewModel(application, SuperAdminRepository.getInstance(application)) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }

    private val _uiState = MutableStateFlow(SuperAdminUiState())
    val uiState: StateFlow<SuperAdminUiState> = _uiState.asStateFlow()

    val rawBusinesses = repository.businesses
    val plans = repository.plans
    val rawPayments = repository.payments
    val rawAuditLogs = repository.auditLogs
    val settings = repository.settings

    // Computed Overview Stats
    val overviewStats: StateFlow<PlatformOverviewStats> = combine(
        rawBusinesses,
        rawPayments
    ) { _, _ ->
        repository.computeOverviewStats()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repository.computeOverviewStats())

    // Filtered businesses based on search and status filter
    val filteredBusinesses: StateFlow<List<PlatformBusiness>> = combine(
        rawBusinesses,
        _uiState
    ) { list, state ->
        val query = state.searchQuery.trim().lowercase()
        list.filter { b ->
            val matchesQuery = query.isEmpty() ||
                    b.businessName.lowercase().contains(query) ||
                    b.ownerName.lowercase().contains(query) ||
                    b.ownerPhone.contains(query) ||
                    b.id.lowercase().contains(query) ||
                    b.businessType.lowercase().contains(query) ||
                    b.address.lowercase().contains(query) ||
                    b.district.lowercase().contains(query)

            val matchesStatus = state.businessStatusFilter == null || b.status == state.businessStatusFilter

            matchesQuery && matchesStatus
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered payments
    val filteredPayments: StateFlow<List<PlatformPayment>> = combine(
        rawPayments,
        _uiState
    ) { list, state ->
        if (state.paymentStatusFilter == null) {
            list
        } else {
            list.filter { it.status == state.paymentStatusFilter }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered audit logs
    val filteredAuditLogs: StateFlow<List<PlatformAuditLog>> = combine(
        rawAuditLogs,
        _uiState
    ) { list, state ->
        val query = state.searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            list
        } else {
            list.filter { log ->
                log.action.lowercase().contains(query) ||
                        log.targetBusinessName.lowercase().contains(query) ||
                        log.actorName.lowercase().contains(query) ||
                        log.details.lowercase().contains(query)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: SuperAdminTab) {
        _uiState.update { it.copy(selectedTab = tab, searchQuery = "") }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setBusinessStatusFilter(status: BusinessAccountStatus?) {
        _uiState.update { it.copy(businessStatusFilter = status) }
    }

    fun setPaymentStatusFilter(status: PlatformPaymentStatus?) {
        _uiState.update { it.copy(paymentStatusFilter = status) }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    /**
     * Actively fetches all registered shops from Cloud Firestore backend and merges into UI state.
     */
    fun refreshBusinesses(onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.startRemoteListeners()
            val result = repository.refreshBusinessesFromRemote()
            val msg = if (result.isSuccess) {
                val count = result.getOrNull()?.size ?: repository.businesses.value.size
                "ক্লাউড থেকে $count টি দোকানের তথ্য সফলভাবে আপডেট হয়েছে।"
            } else {
                val ex = result.exceptionOrNull()
                val raw = ex?.message ?: ""
                when {
                    raw.startsWith("NO_FIREBASE_AUTH:") -> {
                        "গুগল সাইন-ইন আবশ্যক: ক্লাউড থেকে সকল দোকানের তথ্য দেখতে অনুমোদিত সুপার এডমিন গুগল একাউন্টে সাইন-ইন নিশ্চিত করুন।"
                    }
                    raw.startsWith("UNAUTHORIZED_EMAIL:") -> {
                        raw.removePrefix("UNAUTHORIZED_EMAIL:").trim()
                    }
                    raw.contains("PERMISSION_DENIED", ignoreCase = true) -> {
                        val authEmail = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email } catch (_: Exception) { null }
                        "অনুমোদন ত্রুটি (PERMISSION_DENIED): সাইন-ইন করা একাউন্ট '${authEmail ?: "সাইন-ইন নেই"}' ক্লাউড পড়ার অনুমতি পায়নি। অনুগ্রহ করে Firebase Console-এ (Project: smart-business-manager-71879) firestore.rules পাবলিশ করা আছে কি না নিশ্চিত করুন।"
                    }
                    else -> {
                        "দোকানের তথ্য লোড করতে সমস্যা হয়েছে: ${ex?.localizedMessage ?: "নেটওয়ার্ক ত্রুটি"}"
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = if (result.isSuccess) msg else null,
                    errorMessage = if (result.isFailure) msg else null
                )
            }
            onComplete?.invoke(result.isSuccess, msg)
        }
    }

    /**
     * Suspend a business with reason and audit log.
     */
    fun suspendBusiness(
        business: PlatformBusiness,
        reason: String,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ) {
        if (!SuperAdminRepository.isSuperAdminEmail(actorEmail)) {
            _uiState.update { it.copy(errorMessage = "অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.updateBusinessStatus(
                businessId = business.id,
                newStatus = BusinessAccountStatus.SUSPENDED,
                reason = reason,
                actorUid = actorUid,
                actorName = actorName,
                actorEmail = actorEmail
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = if (res.isSuccess) "'${business.businessName}' সফলভাবে স্থগিত করা হয়েছে।" else null,
                    errorMessage = res.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * Reactivate a suspended business.
     */
    fun activateBusiness(
        business: PlatformBusiness,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ) {
        if (!SuperAdminRepository.isSuperAdminEmail(actorEmail)) {
            _uiState.update { it.copy(errorMessage = "অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.updateBusinessStatus(
                businessId = business.id,
                newStatus = BusinessAccountStatus.ACTIVE,
                reason = "সুপার এডমিন কর্তৃক পুনর্সক্রিয়",
                actorUid = actorUid,
                actorName = actorName,
                actorEmail = actorEmail
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = if (res.isSuccess) "'${business.businessName}' সক্রিয় করা হয়েছে।" else null,
                    errorMessage = res.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * Change a business subscription plan or extend days.
     */
    fun changeBusinessPlan(
        business: PlatformBusiness,
        planId: String,
        durationDays: Int,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ) {
        if (!SuperAdminRepository.isSuperAdminEmail(actorEmail)) {
            _uiState.update { it.copy(errorMessage = "অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.updateBusinessPlan(
                businessId = business.id,
                planId = planId,
                durationDays = durationDays,
                actorUid = actorUid,
                actorName = actorName,
                actorEmail = actorEmail
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = if (res.isSuccess) "'${business.businessName}'-এর প্ল্যান সফলভাবে আপডেট করা হয়েছে।" else null,
                    errorMessage = res.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * Send or update a subscription fee proposal notice to a business without cutting free access.
     */
    fun sendSubscriptionNotice(
        business: PlatformBusiness,
        proposedFee: Double,
        proposedStartDate: Long?,
        reminderDate: Long?,
        note: String,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ) {
        if (!SuperAdminRepository.isSuperAdminEmail(actorEmail)) {
            _uiState.update { it.copy(errorMessage = "অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.sendSubscriptionNotice(
                businessId = business.id,
                proposedFee = proposedFee,
                proposedStartDate = proposedStartDate,
                reminderDate = reminderDate,
                note = note,
                actorUid = actorUid,
                actorName = actorName,
                actorEmail = actorEmail
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = if (res.isSuccess) "'${business.businessName}'-এর জন্য সাবস্ক্রিপশন নোটিশ সফলভাবে প্রেরণ করা হয়েছে।" else null,
                    errorMessage = res.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * Schedule a future subscription policy change for a business.
     */
    fun schedulePolicyChange(
        business: PlatformBusiness,
        policyDescription: String,
        effectiveDate: Long,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ) {
        if (!SuperAdminRepository.isSuperAdminEmail(actorEmail)) {
            _uiState.update { it.copy(errorMessage = "অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.schedulePolicyChange(
                businessId = business.id,
                policyDescription = policyDescription,
                effectiveDate = effectiveDate,
                actorUid = actorUid,
                actorName = actorName,
                actorEmail = actorEmail
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = if (res.isSuccess) "'${business.businessName}'-এর ভবিষ্যৎ পলিসি পরিবর্তন সফলভাবে শিডিউল করা হয়েছে।" else null,
                    errorMessage = res.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * Review bKash/Nagad manual payment: approve or reject.
     */
    fun reviewPayment(
        payment: PlatformPayment,
        isApproved: Boolean,
        rejectionReason: String,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ) {
        if (!SuperAdminRepository.isSuperAdminEmail(actorEmail)) {
            _uiState.update { it.copy(errorMessage = "অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.reviewPayment(
                paymentId = payment.id,
                isApproved = isApproved,
                rejectionReason = rejectionReason,
                actorUid = actorUid,
                actorName = actorName,
                actorEmail = actorEmail
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = if (res.isSuccess) {
                        if (isApproved) "পেমেন্ট অনুমোদিত ও সাবস্ক্রিপশন সফলভাবে নবায়ন হয়েছে!"
                        else "পেমেন্ট বাতিল করা হয়েছে।"
                    } else null,
                    errorMessage = res.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * Save/Edit subscription plan limits and prices.
     */
    fun savePlan(
        plan: SubscriptionPlan,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ) {
        if (!SuperAdminRepository.isSuperAdminEmail(actorEmail)) {
            _uiState.update { it.copy(errorMessage = "অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.savePlan(plan, actorUid, actorName, actorEmail)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = if (res.isSuccess) "প্ল্যান '${plan.nameBn}' সফলভাবে সংরক্ষিত হয়েছে।" else null,
                    errorMessage = res.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * Update system settings.
     */
    fun saveSettings(
        newSettings: PlatformSystemSettings,
        actorUid: String,
        actorName: String,
        actorEmail: String
    ) {
        if (!SuperAdminRepository.isSuperAdminEmail(actorEmail)) {
            _uiState.update { it.copy(errorMessage = "অননুমোদিত অ্যাক্সেস! শুধুমাত্র প্ল্যাটফর্ম সুপার এডমিন এই অপারেশন সম্পাদন করতে পারেন।") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = repository.updateSettings(newSettings, actorUid, actorName, actorEmail)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = if (res.isSuccess) "সিস্টেম সেটিংস সফলভাবে সংরক্ষিত হয়েছে।" else null,
                    errorMessage = res.exceptionOrNull()?.message
                )
            }
        }
    }
}
