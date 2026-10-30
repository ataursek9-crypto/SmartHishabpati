package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ReminderItem
import com.example.data.model.ReminderPreference
import com.example.data.model.ReminderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE roleTarget = 'ADMIN' ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, scheduledTime ASC, createdAt DESC")
    fun getAdminRemindersFlow(): Flow<List<ReminderItem>>

    @Query("SELECT * FROM reminders WHERE roleTarget = 'SALESMAN' AND (targetUserId = :salesmanId OR targetUserId IS NULL) ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, scheduledTime ASC, createdAt DESC")
    fun getSalesmanRemindersFlow(salesmanId: String): Flow<List<ReminderItem>>

    @Query("SELECT * FROM reminders WHERE roleTarget = 'ADMIN' AND status = 'PENDING'")
    fun getAdminPendingRemindersFlow(): Flow<List<ReminderItem>>

    @Query("SELECT * FROM reminders WHERE roleTarget = 'SALESMAN' AND (targetUserId = :salesmanId OR targetUserId IS NULL) AND status = 'PENDING'")
    fun getSalesmanPendingRemindersFlow(salesmanId: String): Flow<List<ReminderItem>>

    @Query("SELECT COUNT(*) FROM reminders WHERE roleTarget = 'ADMIN' AND status = 'PENDING'")
    fun getAdminPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM reminders WHERE roleTarget = 'SALESMAN' AND (targetUserId = :salesmanId OR targetUserId IS NULL) AND status = 'PENDING'")
    fun getSalesmanPendingCountFlow(salesmanId: String): Flow<Int>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: String): ReminderItem?

    @Query("SELECT * FROM reminders WHERE deduplicationKey = :key LIMIT 1")
    suspend fun getReminderByDeduplicationKey(key: String): ReminderItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderItem>)

    @Update
    suspend fun updateReminder(reminder: ReminderItem)

    @Query("UPDATE reminders SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: ReminderStatus, completedAt: Long? = null)

    @Query("UPDATE reminders SET status = 'COMPLETED', completedAt = :time WHERE deduplicationKey = :key AND status = 'PENDING'")
    suspend fun completeByDeduplicationKey(key: String, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: String)

    @Query("DELETE FROM reminders WHERE deduplicationKey = :key")
    suspend fun deleteByDeduplicationKey(key: String)

    @Query("SELECT * FROM reminder_preferences WHERE userId = :userId LIMIT 1")
    suspend fun getPreferenceForUser(userId: String): ReminderPreference?

    @Query("SELECT * FROM reminder_preferences WHERE userId = :userId LIMIT 1")
    fun getPreferenceFlowForUser(userId: String): Flow<ReminderPreference?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePreference(preference: ReminderPreference)
}
