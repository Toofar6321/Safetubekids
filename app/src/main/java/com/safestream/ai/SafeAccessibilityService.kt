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

private const val TAG          = "SafeTubeKids"
private const val YT           = "com.google.android.youtube"
private const val APPROVED_KEY = "approved_videos"

private val HARD_BLOCK = listOf(
    "scary prank", "prank on sister", "prank on brother",
    "extreme challenge", "gone wrong", "needle injection",
    "dead bugs", "gross challenge", "killing", "murder",
    "horror", "demon", "stabbing", "shooting", "suicide",
    "jump scare", "graphic", "not for kids", "18+",
    "adult only", "explicit"
)

private val BLOCKED_CHANNELS = setOf(
    "KidsSuper777", "GrossKidz", "FamilyFunPacks"
)

private val SAFE_SUGGESTIONS = listOf(
    "Blippi Explores"        to "Blippi educational videos for kids",
    "Cocomelon Songs"        to "Cocomelon nursery rhymes",
    "Sesame Street"          to "Sesame Street official",
    "Nat Geo Kids"           to "National Geographic Kids animals",
    "SciShow Kids"           to "SciShow Kids science experiments",
    "Pinkfong Baby Shark"    to "Pinkfong Baby Shark kids songs",
    "Ms Rachel"              to "Ms Rachel Songs for Littles"
)

class SafeAccessibilityService : AccessibilityService() {

    private val scope        = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val main         = Handler(Looper.getMainLooper())
    private val timerHandler = Handler(Looper.getMainLooper())
    private val http         = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private var lastTitle          = ""
    private var lastTime           = 0L
    private val sessionCache       = mutableMapOf<String, SafetyResult>()
    private var blockCooldownUntil = 0L
    private var isShowingOverlay   = false

    private val wm: WindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }
    private var overlayView: View? = null

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
        Log.i(TAG, "SafeTube Kids connected")
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
        // Don't scan while block screen is showing or during cooldown
        if (isShowingOverlay) return
        if (System.currentTimeMillis() < blockCooldownUntil) return

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
            block(title, channel, 0, "Channel is blocked"); return
        }

        // Tier 2: hard keyword — instant, no API call
        if (HARD_BLOCK.any { title.lowercase().contains(it) }) {
            block(title, channel, 5, "Contains unsafe keyword"); return
        }

        // Tier 3: approved whitelist — never scan again
        if (isApproved(title)) {
            Log.d(TAG, "Whitelisted: $title"); return
        }

        // Tier 4: session cache
        val cached = sessionCache[title]
        if (cached != null) {
            if (cached.score < threshold() || cached.verdict == "BLOCKED") {
                block(title, channel, cached.score,
                    cached.flags.firstOrNull() ?: "Previously flagged")
            }
            return
        }

        // Tier 5: Claude AI
        analyseWithClaude(title, channel)
    }

    // ── Approved whitelist ─────────────────────────────────────────────────

    private fun isApproved(title: String): Boolean {
        val set = prefs().getStringSet(APPROVED_KEY, emptySet()) ?: emptySet()
        return set.contains(title.lowercase().trim())
    }

    private fun approveVideo(title: String) {
        val p = prefs()
        val set = (p.getStringSet(APPROVED_KEY, mutableSetOf()) ?: mutableSetOf()).toMutableSet()
        set.add(title.lowercase().trim())
        p.edit().putStringSet(APPROVED_KEY, set).apply()
        Log.i(TAG, "Auto-approved: $title")
    }

    // ── Claude AI ──────────────────────────────────────────────────────────

    private fun analyseWithClaude(title: String, channel: String) {
        scope.launch {
            try {
                val key = prefs().getString("claude_api_key", "") ?: ""
                if (key.isBlank()) {
                    block(title, channel, 0, "No API key — tap Settings to add one")
                    return@launch
                }

                val prompt = "Child safety moderator for kids aged 2-12.\n" +
                    "Title: $title\nChannel: $channel\n" +
                    "Reply ONLY with JSON: " +
                    "{\"safetyScore\":75,\"verdict\":\"SAFE\",\"flags\":[],\"summary\":\"ok\"}\n" +
                    "safetyScore 0-100. verdict: SAFE/REVIEW/BLOCKED. " +
                    "flags: up to 3 short reasons if unsafe."

                val body = JSONObject().apply {
                    put("model", "claude-sonnet-4-20250514")
                    put("max_tokens", 150)
                    put("messages", JSONArray().put(
                        JSONObject().apply {
                            put("role", "user"); put("content", prompt)
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

                val raw = resp.body?.string() ?: throw IOException("Empty response")
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
                Log.i(TAG, "AI: \"$title\" → ${result.verdict} (${result.score})")

                if (result.score < threshold() || result.verdict == "BLOCKED") {
                    block(title, channel, result.score,
                        if (result.flags.isNotEmpty()) result.flags.joinToString(" • ")
                        else result.summary.ifBlank { "Not suitable for kids" })
                } else if (result.score >= 80) {
                    approveVideo(title)
                }

            } catch (e: Exception) {
                Log.w(TAG, "AI failed: ${e.message}")
                // Fail open — don't block if AI is unavailable
                // Change to block() if you prefer fail-closed
                Log.w(TAG, "Allowing video due to AI error (fail-open mode)")
            }
        }
    }

    // ── Block ──────────────────────────────────────────────────────────────

    private fun block(title: String, channel: String, score: Int, reason: String) {
        // Set cooldown immediately so we don't re-block during the action sequence
        blockCooldownUntil = System.currentTimeMillis() + 12_000

        main.post {
            // 1. Kill audio
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_STOP))

            // 2. Press BACK 4x rapidly to close mini-player + fullscreen + video + app
            performGlobalAction(GLOBAL_ACTION_BACK)
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 150)
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 300)
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 450)

            // 3. HOME to guarantee YouTube is gone
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 650)

            // 4. Show block screen
            main.postDelayed({ showOverlay(title, reason) }, 950)
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

    // ── Overlay ────────────────────────────────────────────────────────────

    private fun showOverlay(title: String, reason: String) {
        removeOverlay()
        isShowingOverlay = true

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_block, null)
        view.findViewById<TextView>(R.id.ov_title).text  = "\"$title\""
        view.findViewById<TextView>(R.id.ov_reason).text = reason

        // Countdown
        val timerTv = view.findViewById<TextView>(R.id.ov_timer)
        var seconds  = 8
        val countdown = object : Runnable {
            override fun run() {
                if (seconds <= 0) { dismissOverlay(); return }
                timerTv.text = "Closing in ${seconds}s"
                seconds--
                timerHandler.postDelayed(this, 1_000)
            }
        }
        timerHandler.post(countdown)

        // Got it
        view.findViewById<Button>(R.id.ov_btn_ok).setOnClickListener {
            dismissOverlay()
        }

        // Safe suggestion
        val suggestion  = SAFE_SUGGESTIONS.random()
        val btnSuggest  = view.findViewById<Button>(R.id.ov_btn_suggest)
        btnSuggest.text = "▶ Try: ${suggestion.first}"
        btnSuggest.setOnClickListener {
            dismissOverlay()
            // Extended cooldown so the suggested video isn't immediately blocked
            blockCooldownUntil = System.currentTimeMillis() + 20_000
            val query  = suggestion.second.replace(" ", "+")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data  = android.net.Uri.parse("https://www.youtube.com/results?search_query=$query")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { startActivity(intent) } catch (e: Exception) { Log.e(TAG, "Suggestion error: ${e.message}") }
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
        } catch (e: Exception) {
            Log.e(TAG, "Overlay error: ${e.message}")
            isShowingOverlay = false
            Toast.makeText(this, "SafeTube Kids: Blocked \"$title\"", Toast.LENGTH_LONG).show()
        }
    }

    private fun dismissOverlay() {
        timerHandler.removeCallbacksAndMessages(null)
        removeOverlay()
        // Reset so next video scans fresh
        lastTitle = ""
        lastTime  = 0L
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
        isShowingOverlay = false
    }

    private fun prefs(): SharedPreferences =
        getSharedPreferences("safestream_prefs", Context.MODE_PRIVATE)

    private fun threshold() = prefs().getInt("threshold", 65)
}
