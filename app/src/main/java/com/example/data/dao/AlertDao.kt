package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AdminAlert
import com.example.data.model.SalesmanAlertReceipt
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AdminAlert)

    @Update
    suspend fun updateAlert(alert: AdminAlert)

    @Query("SELECT * FROM admin_alerts ORDER BY createdAt DESC")
    fun getAllAlertsFlow(): Flow<List<AdminAlert>>

    @Query("SELECT * FROM admin_alerts WHERE id = :id LIMIT 1")
    suspend fun getAlertById(id: String): AdminAlert?

    @Query("UPDATE admin_alerts SET isCancelled = 1 WHERE id = :alertId")
    suspend fun cancelAlert(alertId: String)

    @Query("SELECT * FROM admin_alerts WHERE isScheduled = 1 AND isCancelled = 0 AND scheduledTime <= :currentTime")
    suspend fun getDueScheduledAlerts(currentTime: Long): List<AdminAlert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: SalesmanAlertReceipt)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipts(receipts: List<SalesmanAlertReceipt>)

    @Update
    suspend fun updateReceipt(receipt: SalesmanAlertReceipt)

    @Query("SELECT * FROM salesman_alert_receipts WHERE alertId = :alertId")
    fun getReceiptsForAlertFlow(alertId: String): Flow<List<SalesmanAlertReceipt>>

    @Query("SELECT * FROM salesman_alert_receipts WHERE alertId = :alertId")
    suspend fun getReceiptsForAlert(alertId: String): List<SalesmanAlertReceipt>

    @Query("SELECT * FROM salesman_alert_receipts")
    fun getAllReceiptsFlow(): Flow<List<SalesmanAlertReceipt>>

    @Query("SELECT * FROM salesman_alert_receipts WHERE salesmanId = :salesmanId ORDER BY deliveredAt DESC")
    fun getReceiptsForSalesmanFlow(salesmanId: String): Flow<List<SalesmanAlertReceipt>>

    @Query("SELECT * FROM salesman_alert_receipts WHERE id = :id LIMIT 1")
    suspend fun getReceiptById(id: String): SalesmanAlertReceipt?

    @Query("UPDATE salesman_alert_receipts SET isAcknowledged = 1, acknowledgedAt = :time WHERE id = :receiptId")
    suspend fun acknowledgeReceipt(receiptId: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE salesman_alert_receipts SET isCompleted = 1, completedAt = :time WHERE id = :receiptId")
    suspend fun completeReceipt(receiptId: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE salesman_alert_receipts SET notifiedAt = :time WHERE id = :receiptId")
    suspend fun markReceiptNotified(receiptId: String, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM admin_alerts WHERE id = :id")
    suspend fun deleteAlert(id: String)

    @Query("DELETE FROM salesman_alert_receipts WHERE alertId = :alertId")
    suspend fun deleteReceiptsForAlert(alertId: String)
}
