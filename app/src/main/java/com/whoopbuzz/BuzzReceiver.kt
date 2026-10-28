package com.whoopbuzz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whoopbuzz.protocol.Whoop5Protocol

/**
 * Entry point for automation apps (MacroDroid/Tasker).
 * Broadcast action `com.whoopbuzz.BUZZ`, extras: token (required), effects "47,152,47" or pattern (single/double/long), loops (0-5).
 */
class BuzzReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BUZZ) return
        val saved = Prefs.token(context)
        val given = intent.getStringExtra("token") ?: ""
        if (saved.isEmpty() || given != saved) {
            Log.add("Broadcast rifiutato: token mancante o errato")
            return
        }
        val pattern = intent.getStringExtra("effects") ?: intent.getStringExtra("pattern") ?: Whoop5Protocol.DEFAULT_PATTERN
        val loops = intent.getIntExtra("loops", intent.getStringExtra("loops")?.toIntOrNull() ?: 1).coerceIn(0, 5)
        Log.add("Broadcast buzz: pattern=$pattern loops=$loops")
        val svc = WhoopService.instance
        if (svc != null) svc.buzz(pattern, loops)
        else try {
            context.startForegroundService(
                Intent(context, WhoopService::class.java)
                    .putExtra(WhoopService.EXTRA_PATTERN, pattern)
                    .putExtra(WhoopService.EXTRA_LOOPS, loops)
            )
        } catch (e: Exception) {
            Log.add("Servizio non avviabile in background: aprilo dall'app (${e.javaClass.simpleName})")
        }
    }

    companion object { const val ACTION_BUZZ = "com.whoopbuzz.BUZZ" }
}