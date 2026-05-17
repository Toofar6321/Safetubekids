package com.safestream.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
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

// ── Data classes ──────────────────────────────────────────────────────────────

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

// ── Constants ─────────────────────────────────────────────────────────────────

private const val TAG          = "SafeStreamAI"
private const val YT           = "com.google.android.youtube"
private const val APPROVED_KEY = "approved_videos"

private val HARD_BLOCK = listOf(
    "scary prank", "prank on sister", "prank on brother",
    "extreme challenge", "gone wrong",
    "needle injection", "dead bugs", "gross challenge",
    "killing", "murder", "horror", "demon",
    "stabbing", "shooting", "suicide", "jump scare"
)

private val BLOCKED_CHANNELS = setOf(
    "KidsSuper777", "GrossKidz", "FamilyFunPacks"
)

// Safe video suggestions shown on block screen
// Format: "Title" to "YouTube search query"
private val SAFE_SUGGESTIONS = listOf(
    "Blippi Explores" to "Blippi educational videos for kids",
    "Cocomelon Songs" to "Cocomelon nursery rhymes",
    "Sesame Street" to "Sesame Street official",
    "National Geographic Kids" to "Nat Geo Kids animals",
    "SciShow Kids" to "SciShow Kids science"
)

// ── Service ───────────────────────────────────────────────────────────────────

class SafeAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val main  = Handler(Looper.getMainLooper())
    private val http  = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private var lastTitle = ""
    private var lastTime  = 0L

    // In-memory cache (session only)
    private val sessionCache = mutableMapOf<String, SafetyResult>()

    private val wm: WindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }
    private var overlayView: View? = null
    private val timerHandler = Handler(Looper.getMainLooper())

    // ── Lifecycle ──────────────────────────────────────────────────────────

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

    // ── Event handler ──────────────────────────────────────────────────────

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

        // Tier 1: blocked channel
        if (BLOCKED_CHANNELS.any { channel.contains(it, ignoreCase = true) }) {
            block(title, channel, 0, "Channel permanently blocked")
            return
        }

        // Tier 2: hard keyword
        if (HARD_BLOCK.any { title.lowercase().contains(it) }) {
            block(title, channel, 5, "Contains blocked keyword")
            return
        }

        // Tier 3: approved video whitelist (persistent — never scanned again)
        if (isApproved(title)) {
            Log.d(TAG, "Approved (whitelist): $title")
            return
        }

        // Tier 4: session cache
        val cached = sessionCache[title]
        if (cached != null) {
            if (cached.score < threshold() || cached.verdict == "BLOCKED") {
                block(title, channel, cached.score,
                    cached.flags.firstOrNull() ?: "Previously blocked")
            }
            return
        }

        // Tier 5: Claude AI
        analyseWithClaude(title, channel)
    }

    // ── Approved video whitelist ───────────────────────────────────────────

    private fun isApproved(title: String): Boolean {
        val approved = prefs().getStringSet(APPROVED_KEY, emptySet()) ?: emptySet()
        return approved.contains(title.lowercase().trim())
    }

    fun approveVideo(title: String) {
        val p       = prefs()
        val current = p.getStringSet(APPROVED_KEY, mutableSetOf())?.toMutableSet()
            ?: mutableSetOf()
        current.add(title.lowercase().trim())
        p.edit().putStringSet(APPROVED_KEY, current).apply()
        Log.i(TAG, "Approved: $title")
    }

    // ── Claude AI ──────────────────────────────────────────────────────────

    private fun analyseWithClaude(title: String, channel: String) {
        scope.launch {
            try {
                val key = prefs().getString("claude_api_key", "") ?: ""
                if (key.isBlank()) {
                    block(title, channel, 0, "No API key — add one in Settings")
                    return@launch
                }

                val prompt = "You are a child safety moderator for kids aged 2-12.\n" +
                    "Video Title: $title\nChannel: $channel\n\n" +
                    "Rate this video for children. Reply ONLY with valid JSON:\n" +
                    "{\"safetyScore\":75,\"verdict\":\"SAFE\",\"flags\":[\"reason1\"],\"summary\":\"1 sentence\"}\n\n" +
                    "safetyScore: 0-100 (100=perfectly safe, 0=completely unsafe)\n" +
                    "verdict: SAFE, REVIEW, or BLOCKED\n" +
                    "flags: up to 3 short reasons if concerning, empty array if safe\n" +
                    "summary: one sentence explanation"

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
                sessionCache[title] = result
                Log.i(TAG, "Claude: \"$title\" -> ${result.verdict} (${result.score})")

                if (result.score < threshold() || result.verdict == "BLOCKED") {
                    block(title, channel, result.score,
                        if (result.flags.isNotEmpty())
                            result.flags.joinToString(", ")
                        else result.summary)
                } else {
                    // Auto-approve safe videos to save future API calls
                    if (result.score >= 80) {
                        approveVideo(title)
                    }
                }

            } catch (e: Exception) {
                Log.w(TAG, "Claude failed: ${e.message} — blocking")
                block(title, channel, 0, "AI check failed — blocked for safety")
            }
        }
    }

    // ── Block action ───────────────────────────────────────────────────────

    private fun block(title: String, channel: String, score: Int, reason: String) {
        main.post {
            // 1. Pause audio
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio.dispatchMediaKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))

            // 2. Go to HOME screen (fully exits YouTube, not just minimizes)
            performGlobalAction(GLOBAL_ACTION_HOME)

            // 3. Show block overlay after home action completes
            main.postDelayed({ showOverlay(title, reason) }, 500)
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

    // ── WindowManager overlay ──────────────────────────────────────────────

    private fun showOverlay(title: String, reason: String) {
        removeOverlay()

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_block, null)
        view.findViewById<TextView>(R.id.ov_title).text  = "\"$title\""
        view.findViewById<TextView>(R.id.ov_reason).text = reason

        // Countdown timer
        val timerTv = view.findViewById<TextView>(R.id.ov_timer)
        var seconds = 8
        val countdown = object : Runnable {
            override fun run() {
                if (seconds <= 0) { removeOverlay(); return }
                timerTv.text = "Auto-closing in ${seconds}s"
                seconds--
                timerHandler.postDelayed(this, 1_000)
            }
        }
        timerHandler.post(countdown)

        // Got it button
        view.findViewById<Button>(R.id.ov_btn_ok).setOnClickListener {
            timerHandler.removeCallbacksAndMessages(null)
            removeOverlay()
        }

        // Safe suggestion buttons
        val suggestion = SAFE_SUGGESTIONS.random()
        val btnSuggest = view.findViewById<Button>(R.id.ov_btn_suggest)
        btnSuggest.text = "Try: ${suggestion.first}"
        btnSuggest.setOnClickListener {
            timerHandler.removeCallbacksAndMessages(null)
            removeOverlay()
            // Open YouTube search for the safe suggestion
            val searchIntent = Intent(Intent.ACTION_VIEW).apply {
                val query = suggestion.second.replace(" ", "+")
                data = android.net.Uri.parse("https://www.youtube.com/results?search_query=$query")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { startActivity(searchIntent) } catch (e: Exception) {
                Log.e(TAG, "Could not open suggestion: ${e.message}")
            }
        }

        // WindowManager params
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

    private fun prefs(): SharedPreferences =
        getSharedPreferences("safestream_prefs", Context.MODE_PRIVATE)

    private fun threshold() = prefs().getInt("threshold", 75)
}
