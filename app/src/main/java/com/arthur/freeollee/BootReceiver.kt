package com.arthur.freeollee

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            val prefs = context.getSharedPreferences("data", Context.MODE_PRIVATE)
            val addr = prefs.getString("device_address", null)

            if (addr != null) {

                val serviceIntent = Intent(context, BleService::class.java)
                serviceIntent.putExtra("device_address", addr)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}