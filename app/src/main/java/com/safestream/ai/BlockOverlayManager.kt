package com.safestream.ai

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BlockOverlayManager(private val context: Context) {

    private var windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    fun showBlock(title: String, channel: String, score: Int, reason: String) {
        handler.post {
            if (overlayView != null) hideBlock()

            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#F2000000"))
                setPadding(60, 60, 60, 60)
            }

            val icon = TextView(context).apply {
                text = "🛑"
                textSize = 80f
                gravity = Gravity.CENTER
            }

            val titleView = TextView(context).apply {
                text = "Blocked by SafeStream"
                setTextColor(Color.WHITE)
                textSize = 28f
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 20)
            }

            val reasonView = TextView(context).apply {
                text = "Reason: $reason"
                setTextColor(Color.parseColor("#FF6B6B"))
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(40, 0, 40, 20)
            }

            val detailView = TextView(context).apply {
                text = "Video: $title\nChannel: $channel\nSafety Score: $score"
                setTextColor(Color.LTGRAY)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(40, 0, 40, 60)
            }

            val dismissButton = Button(context).apply {
                text = "Dismiss"
                setOnClickListener { hideBlock() }
            }

            layout.addView(icon)
            layout.addView(titleView)
            layout.addView(reasonView)
            layout.addView(detailView)
            layout.addView(dismissButton)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            try {
                windowManager.addView(layout, params)
                overlayView = layout

                handler.postDelayed({ hideBlock() }, 10000)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hideBlock() {
        handler.post {
            overlayView?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                overlayView = null
            }
        }
    }
}
