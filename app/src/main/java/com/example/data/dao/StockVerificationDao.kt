package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.StockVerification
import com.example.data.model.StockVerificationSchedule
import kotlinx.coroutines.flow.Flow

@Dao
interface StockVerificationDao {

    // --- Stock Verification Records ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerification(verification: StockVerification): Long

    @Update
    suspend fun updateVerification(verification: StockVerification)

    @Query("SELECT * FROM stock_verifications WHERE id = :id LIMIT 1")
    suspend fun getVerificationById(id: String): StockVerification?

    @Query("SELECT * FROM stock_verifications ORDER BY verifiedAt DESC")
    fun getAllVerificationsFlow(): Flow<List<StockVerification>>

    @Query("SELECT * FROM stock_verifications ORDER BY verifiedAt DESC LIMIT :limit")
    fun getRecentVerificationsFlow(limit: Int = 150): Flow<List<StockVerification>>

    @Query("SELECT * FROM stock_verifications WHERE productId = :productId ORDER BY verifiedAt DESC")
    fun getVerificationsForProductFlow(productId: String): Flow<List<StockVerification>>

    @Query("SELECT * FROM stock_verifications WHERE productId = :productId ORDER BY verifiedAt DESC")
    suspend fun getVerificationsForProduct(productId: String): List<StockVerification>

    @Query("SELECT * FROM stock_verifications WHERE status = 'MISMATCH_PENDING' ORDER BY verifiedAt DESC")
    fun getPendingApprovalsFlow(): Flow<List<StockVerification>>

    @Query("SELECT * FROM stock_verifications WHERE verifierUserId = :userId ORDER BY verifiedAt DESC")
    fun getVerificationsByVerifierFlow(userId: String): Flow<List<StockVerification>>

    @Query("SELECT * FROM stock_verifications WHERE productId = :productId ORDER BY verifiedAt DESC LIMIT 1")
    suspend fun getLatestVerificationForProduct(productId: String): StockVerification?

    @Query("SELECT * FROM stock_verifications WHERE productId = :productId AND (status = 'MATCHED' OR status = 'APPROVED_ADJUSTED') ORDER BY verifiedAt DESC LIMIT 1")
    suspend fun getLatestCompletedVerificationForProduct(productId: String): StockVerification?

    @Query("SELECT * FROM stock_verifications WHERE productId = :productId AND status = 'MISMATCH_PENDING' LIMIT 1")
    suspend fun getPendingVerificationForProduct(productId: String): StockVerification?

    @Query("SELECT COUNT(*) FROM stock_verifications WHERE status = 'MISMATCH_PENDING'")
    fun getPendingApprovalCountFlow(): Flow<Int>

    // --- Stock Verification Schedules ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSchedule(schedule: StockVerificationSchedule)

    @Query("SELECT * FROM stock_verification_schedules WHERE productId = :productId LIMIT 1")
    suspend fun getScheduleForProduct(productId: String): StockVerificationSchedule?

    @Query("SELECT * FROM stock_verification_schedules")
    fun getAllSchedulesFlow(): Flow<List<StockVerificationSchedule>>

    @Query("SELECT * FROM stock_verification_schedules")
    suspend fun getAllSchedules(): List<StockVerificationSchedule>

    @Query("UPDATE stock_verification_schedules SET lastVerifiedAt = :lastVerifiedAt, nextDueAt = :nextDueAt, updatedAt = :updatedAt WHERE productId = :productId")
    suspend fun updateScheduleVerified(productId: String, lastVerifiedAt: Long, nextDueAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE stock_verification_schedules SET intervalDays = :intervalDays, nextDueAt = :nextDueAt, updatedAt = :updatedAt WHERE productId = :productId")
    suspend fun updateScheduleInterval(productId: String, intervalDays: Int, nextDueAt: Long, updatedAt: Long = System.currentTimeMillis())
}
