package com.attendance.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDateTime
import java.time.ZoneId

class SalesReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "daily_sales_reminder_channel"
        private const val NOTIFICATION_ID = 2002

        fun scheduleExact10PMRminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val now = LocalDateTime.now()
            var target10PM = now.withHour(22).withMinute(0).withSecond(0).withNano(0)

            // If it's already past 10:00 PM today, set it for 10:00 PM tomorrow
            if (now.isAfter(target10PM)) {
                target10PM = target10PM.plusDays(1)
            }

            val triggerMillis = target10PM.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val intent = Intent(context, SalesReminderReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 100, intent, flags)

            // Wake up CPU from deep sleep at 10:00:00 PM sharp
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        // If device rebooted, re-arm the exact alarm
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            scheduleExact10PMRminder(context)
            return
        }

        // Show reminder notification
        showNotification(context)

        // Automatically arm next day's 10:00 PM alarm
        scheduleExact10PMRminder(context)
    }

    private fun showNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Sales Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you every night at 10 PM to log daily sales"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Daily Sales Reminder")
            .setContentText("10:00 PM - Please log today's sales and bill records.")
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
