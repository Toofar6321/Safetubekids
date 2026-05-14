package com.safestream.ai

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy {
        getSharedPreferences("safestream_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        val etKey   = findViewById<EditText>(R.id.et_api_key)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val seek    = findViewById<SeekBar>(R.id.seek_threshold)
        val tvLabel = findViewById<TextView>(R.id.tv_threshold)
        val btnClear = findViewById<Button>(R.id.btn_clear)

        etKey.setText(prefs.getString("claude_api_key", ""))
        seek.progress = prefs.getInt("threshold", 75)
        tvLabel.text = "Balanced (${seek.progress})"

        btnSave.setOnClickListener {
            prefs.edit().putString("claude_api_key",
                etKey.text.toString().trim()).apply()
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, v: Int, f: Boolean) {
                val label = when {
                    v >= 85 -> "Strict"
                    v >= 65 -> "Balanced"
                    else    -> "Relaxed"
                }
                tvLabel.text = "$label ($v)"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                prefs.edit().putInt("threshold", sb.progress).apply()
            }
        })

        btnClear.setOnClickListener {
            BlockEventLogger.clear(this)
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
