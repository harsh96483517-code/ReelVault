package com.reelvault.app

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import java.io.File

// FragmentActivity (a ComponentActivity) is required by the fingerprint prompt.
class MainActivity : FragmentActivity() {
    private val vm: MainVm by viewModels()
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crash = File(filesDir, "crash.txt")
        if (crash.exists()) { showCrash(crash); return }
        enableEdgeToEdge()
        setContent {
            val prefs by vm.prefs.collectAsState()
            ReelVaultTheme(prefs.theme) {
                if (AppLock.locked) UnlockPanel("ReelVault", "Draw your pattern", onSuccess = { AppLock.locked = false })
                else MainScreen(vm)
            }
        }
    }

    override fun onStop() { super.onStop(); stoppedAt = SystemClock.elapsedRealtime() }

    override fun onStart() {
        super.onStart()
        if (AppLock.isSet && stoppedAt > 0 && !AppLock.locked) {
            val skip = AppLock.consumeBypass()
            if (!skip && SystemClock.elapsedRealtime() - stoppedAt >= AppLock.timeoutMs) AppLock.locked = true
        }
    }

    // Hide the app preview in "recent apps" when a lock is set.
    override fun onPause() { super.onPause(); if (AppLock.isSet) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    override fun onResume() { super.onResume(); refreshSecure() }

    /** Block screenshots/recents preview while the lock screen or hidden vault is showing. */
    fun refreshSecure() {
        if (AppLock.isSet && (AppLock.locked || AppLock.vaultOpen)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** Plain Android views (no Compose) so it works even if Compose is what crashed. */
    private fun showCrash(f: File) {
        val text = f.readText()
        val tv = TextView(this).apply {
            this.text = "ReelVault crashed last time.\nScreenshot this or tap Copy and send it.\n\n" + text.take(7000)
            setTextColor(Color.WHITE); textSize = 11f; setTextIsSelectable(true); setPadding(32, 32, 32, 32)
        }
        val scroll = ScrollView(this).apply { addView(tv) }
        fun btn(label: String, click: () -> Unit) = Button(this).apply { this.text = label; setOnClickListener { click() } }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); fitsSystemWindows = true
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(btn("Copy error") {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("crash", text))
                Toast.makeText(this@MainActivity, "Copied", Toast.LENGTH_SHORT).show()
            })
            addView(btn("Retry in safe mode (no blur)") {
                getSharedPreferences("rv", MODE_PRIVATE).edit().putBoolean("safe", true).apply()
                SafeMode.on = true; f.delete(); recreate()
            })
            addView(btn("Retry normally") { f.delete(); recreate() })
        }
        setContentView(root)
    }
}
