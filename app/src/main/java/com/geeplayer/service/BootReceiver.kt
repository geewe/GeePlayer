package com.geeplayer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Boot completed, starting DLNA Receiver")
            val serviceIntent = Intent(context, ReceiverForegroundService::class.java).apply {
                action = ReceiverForegroundService.ACTION_START
            }
            context.startForegroundService(serviceIntent)
        }
    }

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            val pm = context.packageManager
            val componentName = android.content.ComponentName(context, BootReceiver::class.java)
            pm.setComponentEnabledSetting(
                componentName,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i("BootReceiver", "Boot receiver ${if (enabled) "enabled" else "disabled"}")
        }
    }
}
