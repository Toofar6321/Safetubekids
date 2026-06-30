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

// ── Age rating system ─────────────────────────────────────────────────────────
// Instead of 0-100, Claude returns an age rating
// We block anything above the parent's chosen max age

enum class AgeRating(val label: String, val minAge: Int) {
    ALL_AGES("All Ages", 0),
    SIX_PLUS("6+", 6),
    EIGHT_PLUS("8+", 8),
    THIRTEEN_PLUS("13+", 13),
    BLOCKED("Blocked", 99)
}

fun parseAgeRating(raw: String): AgeRating {
    return when (raw.trim().lowercase()) {
        "all ages", "allages", "all_ages" -> AgeRating.ALL_AGES
        "6+", "six_plus", "6plus"         -> AgeRating.SIX_PLUS
        "8+", "eight_plus", "8plus"       -> AgeRating.EIGHT_PLUS
        "13+", "thirteen_plus", "13plus"  -> AgeRating.THIRTEEN_PLUS
        "blocked", "block"                -> AgeRating.BLOCKED
        else                               -> AgeRating.EIGHT_PLUS  // default: cautious
    }
}

data class SafetyResult(
    val rating: AgeRating,
    val flags: List<String>,
    val summary: String
)

data class BlockEvent(
    val title: String,
    val channel: String,
    val rating: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

private const val TAG          = "SafeTubeKids"
private const val YT           = "com.google.android.youtube"
private const val APPROVED_KEY = "approved_videos"
private const val MAX_AGE_KEY  = "max_age_rating"  // stored as AgeRating.minAge

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
    "Blippi Explores"     to "Blippi educational videos for kids",
    "Cocomelon Songs"     to "Cocomelon nursery rhymes",
    "Sesame Street"       to "Sesame Street official",
    "Nat Geo Kids"        to "National Geographic Kids animals",
    "SciShow Kids"        to "SciShow Kids science experiments",
    "Pinkfong Baby Shark" to "Pinkfong Baby Shark kids songs",
    "Ms Rachel"           to "Ms Rachel Songs for Littles"
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != YT) return
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
            block(title, channel, AgeRating.BLOCKED, "Channel is blocked")
            return
        }

        // Tier 2: hard keyword
        if (HARD_BLOCK.any { title.lowercase().contains(it) }) {
            block(title, channel, AgeRating.BLOCKED, "Contains unsafe keyword")
            return
        }

        // Tier 3: approved whitelist
        if (isApproved(title)) {
            Log.d(TAG, "Whitelisted: $title")
            return
        }

        // Tier 4: session cache
        val cached = sessionCache[title]
        if (cached != null) {
            if (shouldBlock(cached.rating)) {
                block(title, channel, cached.rating,
                    cached.flags.firstOrNull() ?: "Previously flagged")
            }
            return
        }

        // Tier 5: Claude AI
        analyseWithClaude(title, channel)
    }

    private fun shouldBlock(rating: AgeRating): Boolean {
        val maxAge = prefs().getInt(MAX_AGE_KEY, 6)  // default: block 8+ content
        return rating.minAge > maxAge
    }

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

    private fun analyseWithClaude(title: String, channel: String) {
        scope.launch {
            try {
                val key = prefs().getString("claude_api_key", "") ?: ""
                if (key.isBlank()) {
                    block(title, channel, AgeRating.BLOCKED, "No API key — tap Settings to add one")
                    return@launch
                }

                val prompt = "You are a child safety moderator.\n" +
                    "Title: $title\nChannel: $channel\n\n" +
                    "Assign an age rating and reply ONLY with this JSON:\n" +
                    "{\"ageRating\":\"All Ages\",\"flags\":[],\"summary\":\"ok\"}\n\n" +
                    "ageRating must be exactly one of: All Ages, 6+, 8+, 13+, Blocked\n" +
                    "flags: up to 3 short reasons if not All Ages (empty array if safe)\n" +
                    "summary: one sentence\n\n" +
                    "All Ages = safe for babies and up\n" +
                    "6+ = mild cartoon action, nothing scary\n" +
                    "8+ = some mature themes, mild violence\n" +
                    "13+ = teen content, strong language, adult themes\n" +
                    "Blocked = adult content, extreme violence, inappropriate"

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

                val json   = JSONObject(text)
                val rating = parseAgeRating(json.optString("ageRating", "8+"))
                val flags  = mutableListOf<String>()
                json.optJSONArray("flags")?.let { arr ->
                    for (i in 0 until arr.length()) flags.add(arr.getString(i))
                }

                val result = SafetyResult(rating, flags, json.optString("summary", ""))
                sessionCache[title] = result
                Log.i(TAG, "AI: \"$title\" → ${rating.label}")

                if (shouldBlock(rating)) {
                    block(title, channel, rating,
                        if (flags.isNotEmpty()) flags.joinToString(" • ")
                        else result.summary.ifBlank { "Rated ${rating.label} — not suitable" })
                } else if (rating == AgeRating.ALL_AGES) {
                    approveVideo(title)
                }

            } catch (e: Exception) {
                Log.w(TAG, "AI failed: ${e.message} — allowing video")
                // Fail open on error
            }
        }
    }

    private fun block(title: String, channel: String, rating: AgeRating, reason: String) {
        blockCooldownUntil = System.currentTimeMillis() + 8_000

        main.post {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_MEDIA_STOP))

            performGlobalAction(GLOBAL_ACTION_BACK)
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 150)
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 300)
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 450)
            main.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 650)
            main.postDelayed({ showOverlay(title, rating, reason) }, 950)
        }

        BlockEventLogger.log(this, BlockEvent(title, channel, rating.label, reason))
        sendBroadcast(Intent("com.safestream.BLOCK").apply {
            putExtra("title",     title)
            putExtra("channel",   channel)
            putExtra("rating",    rating.label)
            putExtra("reason",    reason)
            putExtra("timestamp", System.currentTimeMillis())
        })
    }

    private fun showOverlay(title: String, rating: AgeRating, reason: String) {
        removeOverlay()
        isShowingOverlay = true

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_block, null)
        view.findViewById<TextView>(R.id.ov_title).text  = "\"$title\""
        view.findViewById<TextView>(R.id.ov_reason).text = "Rated ${rating.label} — $reason"

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

        view.findViewById<Button>(R.id.ov_btn_ok).setOnClickListener {
            dismissOverlay()
        }

        val suggestion  = SAFE_SUGGESTIONS.random()
        val btnSuggest  = view.findViewById<Button>(R.id.ov_btn_suggest)
        btnSuggest.text = "▶ Try: ${suggestion.first}"
        btnSuggest.setOnClickListener {
            dismissOverlay()
            // Short cooldown — just enough to open YouTube without re-blocking
            blockCooldownUntil = System.currentTimeMillis() + 5_000
            val query  = suggestion.second.replace(" ", "+")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data  = android.net.Uri.parse("https://www.youtube.com/results?search_query=$query")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { startActivity(intent) } catch (e: Exception) {
                Log.e(TAG, "Suggestion error: ${e.message}")
            }
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
            Toast.makeText(this, "Blocked: $title (${rating.label})", Toast.LENGTH_LONG).show()
        }
    }

    private fun dismissOverlay() {
        timerHandler.removeCallbacksAndMessages(null)
        removeOverlay()
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
}
