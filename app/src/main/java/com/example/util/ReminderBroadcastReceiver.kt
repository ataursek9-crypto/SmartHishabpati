package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.data.repository.SmartReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_CHECK_REMINDERS -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val reminderManager = SmartReminderManager(context, db)
                        reminderManager.schedulePeriodicCheck()

                        // Check and dispatch any scheduled admin-to-salesman alerts
                        val alertRepo = com.example.data.repository.AlertRepository(db, context)
                        alertRepo.checkAndDispatchScheduledAlerts()

                        // Get pending admin alerts for system notification
                        val pendingReminders = db.reminderDao().getReminderByDeduplicationKey("EXPENSE_APPROVAL_PENDING")
                        if (pendingReminders != null && pendingReminders.status.name == "PENDING") {
                            SmartNotificationHelper.sendNotification(context, pendingReminders)
                        }
                    } catch (_: Exception) {
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_CHECK_REMINDERS = "com.example.smartbiz.ACTION_CHECK_REMINDERS"
    }
}
