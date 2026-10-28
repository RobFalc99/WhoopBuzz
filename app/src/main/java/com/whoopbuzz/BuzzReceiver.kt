package com.whoopbuzz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whoopbuzz.protocol.PatternStoreCodec
import com.whoopbuzz.protocol.Whoop5Protocol

/**
 * Entry point for automation apps (MacroDroid/Tasker).
 * Broadcast action `com.whoopbuzz.BUZZ`, extras:
 * - token (required)
 * - pattern (saved custom pattern name or built-in single/double/triple/long/pulse)
 * - effects (optional raw "47,152,47", overrides pattern)
 * - loops (optional 0-5; defaults to the saved pattern's loops)
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
        val spec = intent.getStringExtra("effects")
            ?: intent.getStringExtra("pattern")
            ?: Whoop5Protocol.DEFAULT_PATTERN
        val explicitLoops: Int? = when {
            intent.hasExtra("loops") ->
                intent.getIntExtra("loops", intent.getStringExtra("loops")?.toIntOrNull() ?: 0)
            else -> null
        }
        val (resolvedSpec, resolvedLoops) =
            PatternStoreCodec.resolveRequest(Prefs.patterns(context), spec, explicitLoops)
        Log.add("Broadcast buzz: '$spec' -> effects=$resolvedSpec loops=$resolvedLoops")
        val svc = WhoopService.instance
        if (svc != null) svc.buzz(resolvedSpec, resolvedLoops)
        else try {
            context.startForegroundService(
                Intent(context, WhoopService::class.java)
                    .putExtra(WhoopService.EXTRA_PATTERN, resolvedSpec)
                    .putExtra(WhoopService.EXTRA_LOOPS, resolvedLoops)
            )
        } catch (e: Exception) {
            Log.add("Servizio non avviabile in background: aprilo dall'app (${e.javaClass.simpleName})")
        }
    }

    companion object { const val ACTION_BUZZ = "com.whoopbuzz.BUZZ" }
}