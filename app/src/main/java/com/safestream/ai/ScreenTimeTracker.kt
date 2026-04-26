package com.safestream.ai
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.util.Calendar
object ScreenTimeTracker {
    private const val YT = "com.google.android.youtube"
    fun getYouTubeMinutesToday(ctx: Context): Int {
        if (!hasPermission(ctx)) return -1
        val um = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0) }
        val stats = um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,cal.timeInMillis,System.currentTimeMillis())
        val ms = stats?.filter { it.packageName==YT }?.sumOf { it.totalTimeInForeground } ?: 0L
        return (ms/1000/60).toInt()
    }
    fun hasPermission(ctx: Context): Boolean { val ao=ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager; return ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),ctx.packageName)==AppOpsManager.MODE_ALLOWED }
    fun openPermissionSettings(ctx: Context) = ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply { flags=Intent.FLAG_ACTIVITY_NEW_TASK })
    fun formatMinutes(mins: Int) = when { mins<0->"?" ; mins<60->"${mins}m" ; else->"${mins/60}h ${mins%60}m" }
}
