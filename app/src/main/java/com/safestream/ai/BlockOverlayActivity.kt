package com.safestream.ai

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BlockOverlayActivity : AppCompatActivity() {

    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_block_overlay)

        val title  = intent.getStringExtra("title")  ?: "This video"
        val score  = intent.getIntExtra("score", 0)
        val reason = intent.getStringExtra("reason") ?: "Not suitable for kids"

        findViewById<TextView>(R.id.tv_blocked_title).text  = "\"$title\" was blocked"
        findViewById<TextView>(R.id.tv_blocked_reason).text = reason
        findViewById<TextView>(R.id.tv_blocked_score).text  = "Safety score: $score / 100"

        findViewById<Button>(R.id.btn_got_it).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btn_safe_videos).setOnClickListener {
            val ytk = packageManager
                .getLaunchIntentForPackage("com.google.android.apps.youtube.kids")
                ?: Intent(this, MainActivity::class.java)
            startActivity(ytk)
            finish()
        }

        var n = 6
        val tv = findViewById<TextView>(R.id.tv_countdown)
        val r = object : Runnable {
            override fun run() {
                if (n <= 0) { finish(); return }
                tv.text = "Auto-closing in ${n}s"
                n--
                h.postDelayed(this, 1000)
            }
        }
        h.post(r)
        h.postDelayed({ finish() }, 6000)
    }

    override fun onDestroy() {
        super.onDestroy()
        h.removeCallbacksAndMessages(null)
    }
}
