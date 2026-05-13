package com.safestream.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SafetyResult(
    val score: Int,
    val verdict: String,
    val flags: List<String>,
    val summary: String
)

data class BlockEvent(
    val title: String,
    val channel: String,
    val score: Int,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

private const val TAG = "SafeStreamAI"
private const val YT  = "com.google.android.youtube"

private val HARD_BLOCK = listOf(
    "scary prank", "prank on sister", "prank on brother",
    "extreme challenge", "gone wrong",
    "needle injection", "dead bugs", "gross challenge",
    "killing", "murder", "horror", "demon",
    "stabbing", "shooting", "suicide"
)

private val BLOCKED_CHANNELS = setOf(
    "KidsSuper777", "GrossKidz", "FamilyFunPacks"
)

class SafeAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val main  = Handler(Looper.getMainLooper())
    private val http  = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private var lastTitle = ""
    private var lastTime  = 0L
    private val cache     = mutableMapOf<String, SafetyResult>()

    private val wm: WindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }
    private var overlayView: View? = null
    private val timerHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes          = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                                  AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames        = arrayOf(YT)
            feedbackType        = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags               = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.i(TAG, "SafeStream connected")
        sendBroadcast(Intent("com.safestream.STATUS").putExtra("status", "RUNNING"))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        removeOverlay()
        sendBroadcast(Intent("com.safestream.STATUS").putExtra("status", "STOPPED"))
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != YT) return
        val root = rootInActiveWindow ?: return

        var title = ""
        for (id in listOf("$YT:id/title", "$YT:id/reel_title", "$YT:id/mini_title")) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) {
                val t = nodes[0].text?.toString()?.trim() ?: continue
                if (t.isNotBlank()) { title = t; break }
            }
        }
        if (title.isBlank()) return

        val channel = root.findAccessibilityNodeInfosByViewId("$YT:id/channel_name")
            .firstOrNull()?.text?.toString()?.trim() ?: "Unknown"

        val now = System.currentTimeMillis()
        if (title == lastTitle && now - lastTime < 5_000) return
        lastTitle = title
        lastTime  = now

        Log.d(TAG, "Video: \"$title\" | $channel")
        sendBroadcast(Intent("com.safestream.DETECTION")
            .putExtra("title", title).putExtra("channel", channel))

        if (BLOCKED_CHANNELS.any { channel.contains(it, ignoreCase = true) }) {
            block(title, channel, 0, "Channel permanently blocked")
            return
        }

        if (HARD_BLOCK.any { title.lowercase().contains(it) }) {
            block(title, channel, 5, "Contains blocked keyword")
            return
        }

        val cached = cache[title]
        if (cached != null) {
            if (cached.score < threshold() || cached.verdict == "BLOCKED") {
                block(title, channel, cached.score,
                    cached.flags.firstOrNull() ?: "Previously blocked")
            }
            return
        }

        analyseWithClaude(title, channel)
    }

    private fun analyseWithClaude(title: String, channel: String) {
        scope.launch {
            try {
                val key = prefs().getString("claude_api_key", "") ?: ""
                if (key.isBlank()) {
                    block(title, channel, 0, "No API key — add one in Settings")
                    return@launch
                }

                val prompt = "You are a child safety moderator for kids aged 2-12.\n" +
                    "Video Title: $title\nChannel: $channel\n" +
                    "Reply ONLY with valid JSON, no markdown:\n" +
                    "{\"safetyScore\":75,\"verdict\":\"SAFE\",\"flags\":[],\"summary\":\"ok\"}"

                val body = JSONObject().apply {
                    put("model", "claude-sonnet-4-20250514")
                    put("max_tokens", 200)
                    put("messages", JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        }
                    ))
                }.toString().toRequestBody("application/json".toMediaType())

                val resp = http.newCall(
                    Request.Builder()
                        .url("https://api.anthropic.com/v1/messages")
                        .addHeader("x-api-key", key)
                        .addHeader("anthropic-version", "2023-06-01")
                        .post(body).build()
                ).execute()

                val raw = resp.body?.string() ?: throw IOException("Empty")
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")

                val text = JSONObject(raw)
                    .getJSONArray("content").getJSONObject(0).getString("text")
                    .replace("```json", "").replace("```", "").trim()

                val json  = JSONObject(text)
                val flags = mutableListOf<String>()
                json.optJSONArray("flags")?.let { arr ->
                    for (i in 0 until arr.length()) flags.add(arr.getString(i))
                }

                val result = SafetyResult(
                    json.optInt("safetyScore", 50),
                    json.optString("verdict", "REVIEW"),
                    flags,
                    json.optString("summary", "")
                )
                cache[title] = result
                Log.i(TAG, "Claude: \"$title\" -> ${result.verdict} (${result.score})")

                if (result.score < threshold() || result.verdict == "BLOCKED") {
                    block(title, channel, result.score,
                        result.flags.firstOrNull() ?: result.summary)
                }

            } catch (e: Exception) {
                Log.w(TAG, "Claude failed: ${e.message} — blocking")
                block(title, channel, 0, "AI check failed — blocked for safety")
            }
        }
    }

    private fun block(title: String, channel: String, score: Int, reason: String) {
        main.post {
            performGlobalAction(GLOBAL_ACTION_BACK)
            main.postDelayed({ showOverlay(title, reason) }, 400)
        }
        BlockEventLogger.log(this, BlockEvent(title, channel, score, reason))
        sendBroadcast(Intent("com.safestream.BLOCK").apply {
            putExtra("title",     title)
            putExtra("channel",   channel)
            putExtra("score",     score)
            putExtra("reason",    reason)
            putExtra("timestamp", System.currentTimeMillis())
        })
    }

    private fun showOverlay(title: String, reason: String) {
        removeOverlay()

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_block, null)
        view.findViewById<TextView>(R.id.ov_title).text  = "\"$title\""
        view.findViewById<TextView>(R.id.ov_reason).text = reason

        val timerTv = view.findViewById<TextView>(R.id.ov_timer)
        var seconds = 6
        val countdown = object : Runnable {
            override fun run() {
                if (seconds <= 0) { removeOverlay(); return }
                timerTv.text = "Auto-closing in ${seconds}s"
                seconds--
                timerHandler.postDelayed(this, 1_000)
            }
        }
        timerHandler.post(countdown)

        view.findViewById<Button>(R.id.ov_btn_ok).setOnClickListener {
            timerHandler.removeCallbacksAndMessages(null)
            removeOverlay()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            wm.addView(view, params)
            overlayView = view
            Log.i(TAG, "Overlay shown: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Overlay failed: ${e.message}")
            Toast.makeText(this, "Blocked: $title", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeOverlay() {
        timerHandler.removeCallbacksAndMessages(null)
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun prefs() = getSharedPreferences("safestream_prefs", MODE_PRIVATE)
    private fun threshold() = prefs().getInt("threshold", 75)
}
