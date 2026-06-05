package com.geeplayer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
}
