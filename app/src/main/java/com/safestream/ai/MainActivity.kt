package com.safestream.ai

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnToggle = findViewById<Button>(R.id.btn_toggle_service)
        val btnSettings = findViewById<Button>(R.id.btn_settings)
        val tvStatus = findViewById<TextView>(R.id.tv_service_status)

        updateStatus(tvStatus)

        btnToggle.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Check overlay permission on first launch
        checkOverlayPermission()
    }

    override fun onResume() {
        super.onResume()
        updateStatus(findViewById(R.id.tv_service_status))
        // Re-check overlay permission when user returns from settings
        checkOverlayPermission()
    }

    private fun updateStatus(tv: TextView?) {
        val running = isServiceEnabled()
        tv?.text = if (running) "AI Monitoring: ACTIVE" else "AI Monitoring: INACTIVE"
        tv?.setTextColor(ContextCompat.getColor(this,
            if (running) R.color.safe_green else R.color.danger_red))
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name == SafeAccessibilityService::class.java.name
            }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("SafeStream needs permission to display blocking screens over YouTube. Without this, blocked videos may not be properly hidden.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Skip", null)
                .setCancelable(false)
                .show()
        }
    }
}
