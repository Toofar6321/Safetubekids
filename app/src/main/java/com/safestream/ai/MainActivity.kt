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

    private lateinit var tvStatus     : TextView
    private lateinit var tvBlockCount : TextView
    private lateinit var btnEnable    : Button

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

        tvStatus     = findViewById(R.id.tv_status)
        tvBlockCount = findViewById(R.id.tv_block_count)
        btnEnable    = findViewById(R.id.btn_enable)

        btnEnable.setOnClickListener {
            if (!hasOverlayPermission()) {
                // Step 1: get overlay permission first
                showOverlayPermissionDialog()
            } else if (!isServiceEnabled()) {
                // Step 2: enable accessibility service
                showEnableDialog()
            } else {
                showDisableDialog()
            }
        }

        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val filter = IntentFilter().apply {
            addAction("com.safestream.BLOCK")
            addAction("com.safestream.STATUS")
        }
        ContextCompat.registerReceiver(
            this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshCount()

        // Auto-prompt if overlay permission missing
        if (!hasOverlayPermission() && isServiceEnabled()) {
            showOverlayPermissionDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    // ── Status ─────────────────────────────────────────────────────────────

    private fun updateStatus() {
        val overlayOk  = hasOverlayPermission()
        val serviceOn  = isServiceEnabled()

        when {
            serviceOn && overlayOk -> {
                tvStatus.text = "AI Monitoring: ACTIVE"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.safe_green))
                btnEnable.text = "Disable Monitoring"
            }
            serviceOn && !overlayOk -> {
                tvStatus.text = "Needs overlay permission"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.amber))
                btnEnable.text = "Grant Overlay Permission"
            }
            else -> {
                tvStatus.text = "AI Monitoring: INACTIVE"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
                btnEnable.text = "Enable Monitoring"
            }
        }
    }

    private fun refreshCount() {
        tvBlockCount.text = "Videos blocked: ${BlockEventLogger.getCount(this)}"
    }

    // ── Permission checks ──────────────────────────────────────────────────

    private fun hasOverlayPermission(): Boolean =
        Settings.canDrawOverlays(this)

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
            it.resolveInfo.serviceInfo.name == SafeAccessibilityService::class.java.name
        }
    }

    // ── Dialogs ────────────────────────────────────────────────────────────

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Allow Display Over Other Apps")
            .setMessage(
                "SafeStream needs permission to show the block screen over YouTube.\n\n" +
                "On the next screen:\n" +
                "1. Find SafeStream AI\n" +
                "2. Toggle ON"
            )
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun showEnableDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable SafeStream AI")
            .setMessage(
                "On the next screen:\n\n" +
                "1. Find SafeStream AI\n" +
                "2. Tap it\n" +
                "3. Toggle ON\n" +
                "4. Tap Allow"
            )
            .setPositiveButton("Open Accessibility Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDisableDialog() {
        AlertDialog.Builder(this)
            .setTitle("Disable SafeStream?")
            .setMessage("Go to Accessibility Settings to turn it off.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
