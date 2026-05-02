package com.safestream.ai
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
object BlockEventLogger {
    private const val PREFS = "safestream_block_log"
    private const val KEY = "events"
    private val FMT = SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault())
    fun log(ctx: Context, ev: BlockEvent) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = load(p)
        val entry = JSONObject().apply { put("title",ev.title); put("channel",ev.channel); put("score",ev.score); put("reason",ev.reason); put("timestamp",ev.timestamp); put("time_str",FMT.format(Date(ev.timestamp))) }
        val updated = JSONArray().also { arr -> arr.put(entry); for (i in 0 until minOf(old.length(),199)) arr.put(old.getJSONObject(i)) }
        p.edit().putString(KEY,updated.toString()).apply()
    }
    fun getAll(ctx: Context): List<Map<String,Any>> {
        val arr = load(ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE))
        return (0 until arr.length()).map { i -> val o=arr.getJSONObject(i); mapOf("title" to o.optString("title"),"channel" to o.optString("channel"),"score" to o.optInt("score"),"reason" to o.optString("reason"),"timestamp" to o.optLong("timestamp"),"time_str" to o.optString("time_str")) }
    }
    fun clear(ctx: Context) = ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY).apply()
    private fun load(p: SharedPreferences): JSONArray {
        return try {
            JSONArray(p.getString(KEY, null) ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
    }
}
