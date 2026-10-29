package com.whoopbuzz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts WhoopService after device reboot if it was left enabled by the user. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.isServiceEnabled(context)) {
            try {
                WhoopService.start(context)
            } catch (e: Exception) {
                Log.add("Avvio al boot fallito: ${e.javaClass.simpleName}")
            }
        }
    }
}