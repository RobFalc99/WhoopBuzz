package com.whoopbuzz

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.whoopbuzz.ble.WhoopBleClient
import com.whoopbuzz.protocol.Whoop5Protocol

/**
 * Foreground service that keeps the BLE link to the bonded WHOOP and executes buzz requests.
 * Reconnects automatically with exponential backoff.
 */
@SuppressLint("MissingPermission")
class WhoopService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var client: WhoopBleClient
    private var ready = false
    private var running = false
    private var backoffMs = 2_000L
    private val pending = ArrayDeque<Pair<String, Int>>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        client = WhoopBleClient(
            this,
            log = { line -> Log.add(line) },
            onReady = { ready = true; backoffMs = 2_000L; flushPending(); update("Connesso") },
            onDisconnected = { ready = false; update("Disconnesso, riprovo..."); scheduleReconnect() },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            running = false
            client.disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat("Avvio...")
        intent?.getStringExtra(EXTRA_PATTERN)?.let {
            pending.add(it to (intent.getIntExtra(EXTRA_LOOPS, 1)))
        }
        if (!running) {
            running = true
            client.connectBonded()
        } else flushPending()
        return START_STICKY
    }

    fun buzz(pattern: String, loops: Int) {
        if (ready) client.sendBuzz(pattern, loops)
        else { pending.add(pattern to loops); Log.add("WHOOP non pronto: buzz in coda") }
    }

    private fun flushPending() {
        while (ready && pending.isNotEmpty()) pending.removeFirst().let { client.sendBuzz(it.first, it.second) }
    }

    private fun scheduleReconnect() {
        if (!running) return
        handler.postDelayed({ if (running && !ready) client.connectBonded() }, backoffMs)
        backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
    }

    private fun startForegroundCompat(text: String) {
        val n = notification(text)
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIF_ID, n)
    }

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "WhoopBuzz", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, PocActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("WhoopBuzz")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        client.disconnect()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.whoopbuzz.STOP"
        const val EXTRA_PATTERN = "pattern"
        const val EXTRA_LOOPS = "loops"
        private const val CHANNEL = "whoopbuzz"
        private const val NOTIF_ID = 1
        @Volatile var instance: WhoopService? = null

        fun start(c: Context) {
            c.startForegroundService(Intent(c, WhoopService::class.java))
        }

        fun stop(c: Context) {
            c.startService(Intent(c, WhoopService::class.java).setAction(ACTION_STOP))
        }
    }
}

/** In-memory log shared by the service and the UI. */
object Log {
    val lines = androidx.compose.runtime.mutableStateListOf<String>()
    fun add(line: String) {
        Handler(Looper.getMainLooper()).post { lines.add(0, line); if (lines.size > 200) lines.removeLast() }
    }
}