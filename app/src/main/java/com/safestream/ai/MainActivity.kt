package com.safestream.ai
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        try {
            val prefs = getSharedPreferences("safestream_prefs", Context.MODE_PRIVATE)
            findViewById<Button>(R.id.btn_toggle_service)?.setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            findViewById<Button>(R.id.btn_settings)?.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            findViewById<Button>(R.id.btn_clear_log)?.setOnClickListener {
                BlockEventLogger.clear(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
class BlockLogAdapter(private val ev: List<Map<String,Any>>) : androidx.recyclerview.widget.RecyclerView.Adapter<BlockLogAdapter.VH>() {
    class VH(v: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v)
    override fun onCreateViewHolder(p: android.view.ViewGroup, t: Int) = VH(android.view.LayoutInflater.from(p.context).inflate(R.layout.item_block_log,p,false))
    override fun getItemCount() = ev.size
    override fun onBindViewHolder(h: VH, i: Int) {}
}
