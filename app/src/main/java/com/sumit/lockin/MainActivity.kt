package com.sumit.lockin

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(NotificationBridge(this), "AndroidNotifications")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/lockin.html")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("lockin_reminders", "LOCKIN reminders", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Your scheduled personal reminders"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    class NotificationBridge(private val context: Context) {
        @JavascriptInterface fun getSavedState(): String = context.getSharedPreferences("lockin_native", Context.MODE_PRIVATE).getString("payload", "") ?: ""

        @JavascriptInterface fun sync(payload: String) {
            context.getSharedPreferences("lockin_native", Context.MODE_PRIVATE).edit().putString("payload", payload).apply()
            try { AlarmScheduler.sync(context, JSONObject(payload)) } catch (_: Exception) { }
        }

        @JavascriptInterface fun requestPermissions() {
            if (Build.VERSION.SDK_INT >= 31) {
                val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!manager.canScheduleExactAlarms()) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    }
}
