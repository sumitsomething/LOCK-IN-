package com.sumit.lockin

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: return
        val id = intent.getIntExtra("id", 0)
        val notification = NotificationCompat.Builder(context, "lockin_reminders")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("LOCKIN")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val notificationId = if (intent.getStringExtra("type") == "custom") id else 500 + id
        context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
        AlarmScheduler.rescheduleNext(context, intent)
    }
}
