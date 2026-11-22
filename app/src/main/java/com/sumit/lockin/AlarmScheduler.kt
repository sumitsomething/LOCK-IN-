package com.sumit.lockin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.util.Calendar

object AlarmScheduler {
    fun sync(context: Context, payload: JSONObject) {
        val state = payload.optJSONObject("state") ?: return
        val library = payload.optJSONArray("library") ?: return
        cancelAll(context)
        if (!state.optBoolean("master", true)) {
            scheduleCustom(context, state)
            return
        }
        val times = state.optJSONArray("times") ?: return
        val enabled = state.optJSONArray("enabledSlots")
        val day = (state.optInt("day", 1).coerceIn(1, 100) - 1) * 5
        for (i in 0 until minOf(5, times.length())) {
            if (enabled != null && !enabled.optBoolean(i, true)) continue
            val item = library.optJSONObject(day + i) ?: continue
            schedule(context, "builtin", i, item.optString("text"), times.optString(i), i)
        }
        scheduleCustom(context, state)
    }

    private fun scheduleCustom(context: Context, state: JSONObject) {
        val custom = state.optJSONArray("custom") ?: return
        for (i in 0 until custom.length()) {
            val item = custom.optJSONObject(i) ?: continue
            if (item.optBoolean("on", true)) schedule(context, "custom", item.optLong("id").toInt(), item.optString("text"), item.optString("time", "20:00"), 100 + i)
        }
    }

    private fun schedule(context: Context, type: String, id: Int, message: String, time: String, requestCode: Int) {
        val parts = time.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("type", type); putExtra("id", id); putExtra("message", message); putExtra("time", time); putExtra("requestCode", requestCode)
        }
        val pending = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 23) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
            else alarms.setExact(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= 23) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
            else alarms.set(AlarmManager.RTC_WAKEUP, target.timeInMillis, pending)
        }
    }

    fun rescheduleNext(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        val id = intent.getIntExtra("id", 0)
        val message = intent.getStringExtra("message") ?: return
        val time = intent.getStringExtra("time") ?: return
        val requestCode = intent.getIntExtra("requestCode", id)
        val parts = time.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, 1)
        }
        val nextIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("type", type); putExtra("id", id); putExtra("message", message); putExtra("time", time); putExtra("requestCode", requestCode)
        }
        val pending = PendingIntent.getBroadcast(context, requestCode, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 23) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
        else alarms.setExact(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }

    fun cancelAll(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0..199) {
            val pending = PendingIntent.getBroadcast(context, i, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            if (pending != null) alarms.cancel(pending)
        }
    }

    fun recordDelivery(context: Context, intent: Intent) {
        if (intent.getStringExtra("type") != "builtin") {
            restore(context)
            return
        }
        val prefs = context.getSharedPreferences("lockin_native", Context.MODE_PRIVATE)
        val raw = prefs.getString("payload", null) ?: return
        try {
            val root = JSONObject(raw)
            val state = root.getJSONObject("state")
            val slot = intent.getIntExtra("id", -1)
            val sent = state.optJSONArray("sentToday") ?: org.json.JSONArray()
            var already = false
            for (i in 0 until sent.length()) if (sent.optInt(i) == slot) already = true
            if (!already && slot in 0..4) {
                sent.put(slot)
                state.put("sentToday", sent)
                state.put("completed", (state.optInt("completed") + 1).coerceAtMost(500))
                if (sent.length() >= 5) {
                    state.put("sentToday", org.json.JSONArray())
                    state.put("day", if (state.optInt("day", 1) >= 100) 1 else state.optInt("day", 1) + 1)
                }
                prefs.edit().putString("payload", root.toString()).apply()
                sync(context, root)
            } else restore(context)
        } catch (_: Exception) { }
    }

    fun restore(context: Context) {
        val payload = context.getSharedPreferences("lockin_native", Context.MODE_PRIVATE).getString("payload", null) ?: return
        try { sync(context, JSONObject(payload)) } catch (_: Exception) { }
    }
}
