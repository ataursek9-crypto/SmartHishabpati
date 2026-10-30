package com.example.data.sync

enum class SyncStatus(val labelBn: String) {
    SYNCED("সিঙ্ক সম্পন্ন"),
    SYNCING("সিঙ্ক হচ্ছে..."),
    PENDING("সিঙ্ক বাকি"),
    LOCAL_SAVED("ক্লাউড সেটআপ বাকি"),
    OFFLINE("অফলাইন মোড"),
    ERROR("সিঙ্ক ব্যর্থ")
}

data class SyncState(
    val status: SyncStatus = SyncStatus.LOCAL_SAVED,
    val statusText: String = "লোকাল ডেটা সংরক্ষিত — ক্লাউড সেটআপ বাকি",
    val pendingCount: Int = 0,
    val lastSyncTime: Long = System.currentTimeMillis(),
    val isCloudConfigured: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Tombstone marker to track deleted records across multiple devices.
 * Prevents stale devices from re-uploading or resurrecting deleted items.
 */
data class Tombstone(
    val id: String,
    val tableName: String,
    val originalId: String,
    val deletedAt: Long = System.currentTimeMillis(),
    val isPermanent: Boolean = false,
    val deletedByUserId: String = ""
)
