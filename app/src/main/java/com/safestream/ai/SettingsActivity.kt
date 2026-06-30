package com.safestream.ai

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("safestream_prefs", Context.MODE_PRIVATE)
    }

    // maxAge stored as minAge of AgeRating enum
    // All Ages=0, 6+=6, 8+=8, 13+=13
    // "Block 8+ content" means maxAge=6 (allow up to 6+)
    private val AGE_OPTIONS = listOf(
        Triple(R.id.rb_all_ages,    0,  "All Ages only — strictest"),
        Triple(R.id.rb_six_plus,    6,  "Up to 6+ — recommended for young kids"),
        Triple(R.id.rb_eight_plus,  8,  "Up to 8+ — older kids"),
        Triple(R.id.rb_thirteen_plus,13,"Up to 13+ — teens")
    )

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        val etKey    = findViewById<EditText>(R.id.et_api_key)
        val btnSave  = findViewById<Button>(R.id.btn_save)
        val rgAge    = findViewById<RadioGroup>(R.id.rg_age_rating)
        val btnClear = findViewById<Button>(R.id.btn_clear)

        // Restore API key
        etKey.setText(prefs.getString("claude_api_key", ""))

        // Restore age selection
        val savedMaxAge = prefs.getInt("max_age_rating", 6)
        for ((rbId, age, _) in AGE_OPTIONS) {
            if (age == savedMaxAge) {
                findViewById<RadioButton>(rbId)?.isChecked = true
                break
            }
        }

        btnSave.setOnClickListener {
            val key = etKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("claude_api_key", key).apply()
            Toast.makeText(this, "API key saved ✓", Toast.LENGTH_SHORT).show()
        }

        rgAge.setOnCheckedChangeListener { _, checkedId ->
            for ((rbId, age, label) in AGE_OPTIONS) {
                if (rbId == checkedId) {
                    prefs.edit().putInt("max_age_rating", age).apply()
                    Toast.makeText(this, "Filter: $label", Toast.LENGTH_SHORT).show()
                    break
                }
            }
        }

        btnClear.setOnClickListener {
            BlockEventLogger.clear(this)
            // Also clear approved video whitelist
            prefs.edit().remove("approved_videos").apply()
            Toast.makeText(this, "History and approved list cleared", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
