package com.safestream.ai
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SafetyResult(val score: Int, val verdict: String, val ageRating: String, val flags: List<String>, val summary: String, val recommendation: String)
data class BlockEvent(val title: String, val channel: String, val score: Int, val reason: String, val timestamp: Long = System.currentTimeMillis())

private const val TAG = "SafeStreamAI"
private const val YT = "com.google.android.youtube"
private val HARD = listOf("scary prank","prank on sister","prank on brother","extreme challenge","gone wrong","needle injection","dead bugs","killing","murder","horror","demon","stabbing","shooting")
private val CHANNELS = setOf("KidsSuper777","GrossKidz","FamilyFunPacks","DadReacts")

class SafeAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val main = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
    private var lastTitle = ""; private var lastTime = 0L
    private val cache = mutableMapOf<String, SafetyResult>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames = arrayOf(YT)
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        sendBroadcast(Intent("com.safestream.STATUS").putExtra("status","RUNNING"))
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel(); sendBroadcast(Intent("com.safestream.STATUS").putExtra("status","STOPPED")) }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != YT) return
        val root = rootInActiveWindow ?: return
        val title = listOf("$YT:id/title","$YT:id/reel_title","$YT:id/mini_title")
            .flatMap { root.findAccessibilityNodeInfosByViewId(it) }
            .firstOrNull()?.text?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: return
        val channel = root.findAccessibilityNodeInfosByViewId("$YT:id/channel_name").firstOrNull()?.text?.toString()?.trim() ?: "Unknown"
        val now = System.currentTimeMillis()
        if (title == lastTitle && now - lastTime < 5000) return
        lastTitle = title; lastTime = now
        sendBroadcast(Intent("com.safestream.DETECTION").putExtra("title",title).putExtra("channel",channel))
        if (CHANNELS.any { channel.contains(it,ignoreCase=true) }) { block(title,channel,0,"Channel blocked"); return }
        if (HARD.any { title.lowercase().contains(it) }) { block(title,channel,5,"Blocked keyword"); return }
        cache[title]?.let { if (it.score < thresh() || it.verdict=="BLOCKED") block(title,channel,it.score,it.flags.firstOrNull()?:"Previously blocked"); return }
        scope.launch {
            try {
                val key = getSharedPreferences("safestream_prefs",MODE_PRIVATE).getString("claude_api_key","") ?: ""
                if (key.isBlank()) throw IOException("No API key")
                val prompt = "Child safety moderator for kids 2-12.\nTitle: $title\nChannel: $channel\nReturn ONLY JSON no markdown: {\"safetyScore\":50,\"verdict\":\"SAFE\",\"ageRating\":\"All Ages\",\"flags\":[],\"summary\":\"ok\",\"recommendation\":\"allow\"}"
                val body = JSONObject().apply { put("model","claude-sonnet-4-20250514"); put("max_tokens",400); put("messages",JSONArray().put(JSONObject().apply{put("role","user");put("content",prompt)})) }.toString().toRequestBody("application/json".toMediaType())
                val resp = http.newCall(Request.Builder().url("https://api.anthropic.com/v1/messages").addHeader("x-api-key",key).addHeader("anthropic-version","2023-06-01").post(body).build()).execute()
                val raw = resp.body?.string() ?: throw IOException("Empty")
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val text = JSONObject(raw).getJSONArray("content").getJSONObject(0).getString("text").replace("```json","").replace("```","").trim()
                val json = JSONObject(text)
                val flags = mutableListOf<String>().also { list -> json.optJSONArray("flags")?.let { for (i in 0 until it.length()) list.add(it.getString(i)) } }
                val result = SafetyResult(json.optInt("safetyScore",50),json.optString("verdict","REVIEW"),json.optString("ageRating","?"),flags,json.optString("summary",""),json.optString("recommendation",""))
                cache[title] = result
                if (result.score < thresh() || result.verdict=="BLOCKED") block(title,channel,result.score,result.flags.firstOrNull()?:result.summary)
            } catch (e: Exception) { Log.w(TAG,"Claude failed: ${e.message}"); block(title,channel,0,"AI unavailable") }
        }
    }

    private fun block(title: String, channel: String, score: Int, reason: String) {
        main.post {
            performGlobalAction(GLOBAL_ACTION_BACK)
            Toast.makeText(this,"SafeStream: Blocked",Toast.LENGTH_SHORT).show()
            startActivity(Intent(this,BlockOverlayActivity::class.java).apply { flags=Intent.FLAG_ACTIVITY_NEW_TASK; putExtra("title",title); putExtra("channel",channel); putExtra("score",score); putExtra("reason",reason) })
        }
        BlockEventLogger.log(this,BlockEvent(title,channel,score,reason))
        sendBroadcast(Intent("com.safestream.BLOCK").apply { putExtra("title",title); putExtra("channel",channel); putExtra("score",score); putExtra("reason",reason); putExtra("timestamp",System.currentTimeMillis()) })
    }

    private fun thresh() = getSharedPreferences("safestream_prefs",MODE_PRIVATE).getInt("threshold",75)
}
