package com.safestream.ai
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
class SettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("safestream_prefs",Context.MODE_PRIVATE) }
    override fun onCreate(s: Bundle?) {
        super.onCreate(s); setContentView(R.layout.activity_settings); supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val etKey=findViewById<TextInputEditText>(R.id.et_api_key); val etName=findViewById<TextInputEditText>(R.id.et_child_name); val etTime=findViewById<TextInputEditText>(R.id.et_time_limit)
        etKey.setText(prefs.getString("claude_api_key","")); etName.setText(prefs.getString("child_name","Kiddo")); etTime.setText(prefs.getInt("time_limit_min",60).toString())
        findViewById<MaterialButton>(R.id.btn_save_api_key).setOnClickListener{prefs.edit().putString("claude_api_key",etKey.text.toString().trim()).apply();Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show()}
        findViewById<MaterialButton>(R.id.btn_save_name).setOnClickListener{prefs.edit().putString("child_name",etName.text.toString().trim()).apply();Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show()}
        findViewById<MaterialButton>(R.id.btn_save_time).setOnClickListener{prefs.edit().putInt("time_limit_min",etTime.text.toString().toIntOrNull()?:60).apply();Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show()}
        findViewById<MaterialButton>(R.id.btn_clear_log).setOnClickListener{BlockEventLogger.clear(this);Toast.makeText(this,"Cleared",Toast.LENGTH_SHORT).show()}
    }
    override fun onSupportNavigateUp():Boolean{onBackPressedDispatcher.onBackPressed();return true}
}
