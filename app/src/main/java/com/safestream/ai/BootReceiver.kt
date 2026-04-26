package com.safestream.ai
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Log.i("SafeStream", "Booted")
    }
}
