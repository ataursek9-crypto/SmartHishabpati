package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.ReminderItem
import com.example.data.model.ReminderPriority

object SmartNotificationHelper {
    const val CHANNEL_ID = "smart_business_reminders_channel"
    private const val CHANNEL_NAME = "স্মার্ট ব্যবসায়িক রিমাইন্ডার"
    private const val CHANNEL_DESC = "স্টক সংকট, খরচ অনুমোদন, বকেয়া তাগাদা ও হিসাবের সতর্কবার্তা"

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun sendNotification(context: Context, reminder: ReminderItem) {
        if (!hasNotificationPermission(context)) return

        initNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_TARGET_ROUTE", reminder.targetRoute)
            putExtra("EXTRA_REMINDER_ID", reminder.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            reminder.deduplicationKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priorityLevel = when (reminder.priority) {
            ReminderPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
            ReminderPriority.MEDIUM -> NotificationCompat.PRIORITY_DEFAULT
            ReminderPriority.LOW -> NotificationCompat.PRIORITY_LOW
        }

        val smallIcon = android.R.drawable.ic_dialog_info

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(reminder.title)
            .setContentText(reminder.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.description))
            .setPriority(priorityLevel)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(reminder.deduplicationKey.hashCode(), builder.build())
        } catch (_: SecurityException) {
            // Gracefully ignore if permission was revoked in background
        }
    }
}
