package com.safestream.ai

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus    : TextView
    private lateinit var tvCount     : TextView
    private lateinit var btnEnable   : Button

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                "com.safestream.BLOCK"  -> refreshCount()
                "com.safestream.STATUS" -> updateStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        tvStatus  = findViewById(R.id.tv_status)
        tvCount   = findViewById(R.id.tv_block_count)
        btnEnable = findViewById(R.id.btn_enable)

        btnEnable.setOnClickListener { handleEnableTap() }

        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val filter = IntentFilter().apply {
            addAction("com.safestream.BLOCK")
            addAction("com.safestream.STATUS")
        }
        ContextCompat.registerReceiver(this, receiver, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshCount()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    private fun handleEnableTap() {
        when {
            !hasOverlay()  -> promptOverlay()
            !hasService()  -> promptService()
            else           -> promptDisable()
        }
    }

    private fun updateStatus() {
        val overlay  = hasOverlay()
        val service  = hasService()
        val apiKey   = getSharedPreferences("safestream_prefs", Context.MODE_PRIVATE)
            .getString("claude_api_key", "").orEmpty().isNotBlank()

        when {
            service && overlay && apiKey -> {
                tvStatus.text = "Protection: ACTIVE"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.safe_green))
                btnEnable.text = "Protection is ON"
            }
            service && overlay -> {
                tvStatus.text = "Add API key in Settings"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.amber))
                btnEnable.text = "Open Settings"
            }
            service -> {
                tvStatus.text = "Grant overlay permission"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.amber))
                btnEnable.text = "Grant Permission"
            }
            else -> {
                tvStatus.text = "Protection: INACTIVE"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
                btnEnable.text = "Enable Protection"
            }
        }
    }

    private fun refreshCount() {
        tvCount.text = "Videos blocked: ${BlockEventLogger.getCount(this)}"
    }

    private fun hasOverlay() = Settings.canDrawOverlays(this)

    private fun hasService(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
            it.resolveInfo.serviceInfo.name == SafeAccessibilityService::class.java.name
        }
    }

    private fun promptOverlay() {
        AlertDialog.Builder(this)
            .setTitle("Step 1 of 2 — Allow Overlay")
            .setMessage("SafeTube Kids needs to show a block screen over YouTube.\n\n" +
                "On the next screen:\n1. Find SafeTube Kids\n2. Toggle ON")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
            .setNegativeButton("Later", null).show()
    }

    private fun promptService() {
        AlertDialog.Builder(this)
            .setTitle("Step 2 of 2 — Enable in Accessibility")
            .setMessage("On the next screen:\n\n" +
                "1. Scroll to Downloaded Apps\n" +
                "2. Tap SafeTube Kids\n" +
                "3. Toggle ON\n" +
                "4. Tap Allow")
            .setPositiveButton("Open Accessibility") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Later", null).show()
    }

    private fun promptDisable() {
        AlertDialog.Builder(this)
            .setTitle("Disable Protection?")
            .setMessage("This will stop SafeTube Kids from protecting your child on YouTube.")
            .setPositiveButton("Open Accessibility") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null).show()
    }
}
