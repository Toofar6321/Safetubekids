package com.safestream.ai
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            Log.e("MainActivity", "Error initializing MainActivity", e)
        }
    }
}

class BlockLogAdapter(private val events: List<Map<String, Any>>) :
    RecyclerView.Adapter<BlockLogAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val logTextView: TextView? = itemView.findViewById(R.id.log_text)

        fun bind(eventData: Map<String, Any>) {
            logTextView?.text = eventData["event"]?.toString() ?: "Unknown Event"
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_block_log, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = events.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(events[position])
    }
}
