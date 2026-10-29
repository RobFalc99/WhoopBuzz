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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.whoopbuzz.ble.WhoopBleClient

/**
 * Foreground service keeping the BLE link to the bonded WHOOP and running:
 * - One-shot buzz requests (UI & Broadcast API)
 * - Wrist Timer with intermittent repeating alarm & Snooze
 * - Pomodoro Focus/Break timer & Periodic Chime
 * - Box Breathing (4-4-4-4) wrist pacer
 */
@SuppressLint("MissingPermission")
class WhoopService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var client: WhoopBleClient
    private var ready = false
    private var running = false
    private var backoffMs = 2_000L
    private val pending = ArrayDeque<Pair<String, Int>>()

    // Timer & intermittent alarm state
    private var timerTickRunnable: Runnable? = null
    private var alarmLoopRunnable: Runnable? = null
    private var alarmStopRunnable: Runnable? = null
    private var currentTimerAlarmSpec = "alarm"
    private var currentAlarmMaxSec = 30

    // Chime / Pomodoro / Breathing runnables
    private var chimeRunnable: Runnable? = null
    private var pomodoroRunnable: Runnable? = null
    private var breathingRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppState.serviceRunning.value = true
        client = WhoopBleClient(
            this,
            log = { line -> Log.add(line) },
            onReady = {
                ready = true
                AppState.bleConnected.value = true
                backoffMs = 2_000L
                flushPending()
                refreshNotification()
            },
            onDisconnected = {
                ready = false
                AppState.bleConnected.value = false
                refreshNotification()
                scheduleReconnect()
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAllTools()
                running = false
                AppState.serviceRunning.value = false
                AppState.bleConnected.value = false
                client.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_ALARM -> {
                stopAlarm()
                return START_STICKY
            }
            ACTION_SNOOZE_ALARM -> {
                snoozeAlarm(5)
                return START_STICKY
            }
        }
        startForegroundCompat(statusText())
        intent?.getStringExtra(EXTRA_PATTERN)?.let {
            pending.add(it to (intent.getIntExtra(EXTRA_LOOPS, 0)))
        }
        if (!running) {
            running = true
            client.connectBonded()
        } else flushPending()
        return START_STICKY
    }

    fun buzz(pattern: String, loops: Int = 0) {
        if (ready) client.sendBuzz(pattern, loops)
        else { pending.add(pattern to loops); Log.add("WHOOP non pronto: buzz in coda") }
    }

    // --- 1. Wrist Timer + Intermittent Alarm + Snooze ---
    fun startTimer(seconds: Int, alarmSpec: String = "alarm", maxRingSec: Int = 30) {
        stopAlarm()
        timerTickRunnable?.let { handler.removeCallbacks(it) }
        currentTimerAlarmSpec = alarmSpec
        currentAlarmMaxSec = maxRingSec.coerceIn(5, 120)
        AppState.timerRemainingSec.value = seconds.coerceAtLeast(1)
        AppState.timerRunning.value = true
        val r = object : Runnable {
            override fun run() {
                val rem = AppState.timerRemainingSec.value - 1
                if (rem <= 0) {
                    AppState.timerRemainingSec.value = 0
                    AppState.timerRunning.value = false
                    startRepeatingAlarm(currentTimerAlarmSpec, currentAlarmMaxSec)
                } else {
                    AppState.timerRemainingSec.value = rem
                    handler.postDelayed(this, 1_000L)
                }
            }
        }
        timerTickRunnable = r
        handler.postDelayed(r, 1_000L)
        refreshNotification()
    }

    fun pauseTimer() {
        timerTickRunnable?.let { handler.removeCallbacks(it) }
        AppState.timerRunning.value = false
        refreshNotification()
    }

    fun resetTimer(defaultSec: Int = 300) {
        pauseTimer()
        stopAlarm()
        AppState.timerRemainingSec.value = defaultSec
        refreshNotification()
    }

    fun startRepeatingAlarm(patternSpec: String = "alarm", maxSeconds: Int = 30) {
        stopAlarm()
        AppState.alarmRinging.value = true
        refreshNotification()
        val loop = object : Runnable {
            override fun run() {
                if (!AppState.alarmRinging.value) return
                buzz(patternSpec, 0)
                handler.postDelayed(this, 2_600L)
            }
        }
        alarmLoopRunnable = loop
        loop.run()
        val stopper = Runnable { stopAlarm() }
        alarmStopRunnable = stopper
        handler.postDelayed(stopper, maxSeconds * 1_000L)
    }

    fun stopAlarm() {
        alarmLoopRunnable?.let { handler.removeCallbacks(it) }
        alarmStopRunnable?.let { handler.removeCallbacks(it) }
        alarmLoopRunnable = null
        alarmStopRunnable = null
        if (AppState.alarmRinging.value) {
            AppState.alarmRinging.value = false
            refreshNotification()
        }
    }

    fun snoozeAlarm(minutes: Int = 5) {
        stopAlarm()
        startTimer(minutes * 60, currentTimerAlarmSpec, currentAlarmMaxSec)
        Log.add("Snooze attivato: +$minutes min")
    }

    // --- 2. Periodic Chime (every N minutes) ---
    fun setPeriodicChime(intervalMinutes: Int, patternSpec: String = "gentle") {
        chimeRunnable?.let { handler.removeCallbacks(it) }
        chimeRunnable = null
        AppState.chimeIntervalMin.value = intervalMinutes
        if (intervalMinutes <= 0) {
            refreshNotification()
            return
        }
        val r = object : Runnable {
            override fun run() {
                buzz(patternSpec, 0)
                Log.add("Chime periodico (${intervalMinutes}m)")
                handler.postDelayed(this, intervalMinutes * 60_000L)
            }
        }
        chimeRunnable = r
        handler.postDelayed(r, intervalMinutes * 60_000L)
        refreshNotification()
    }

    // --- 3. Pomodoro (25m Focus / 5m Break) ---
    fun startPomodoro(focusMin: Int = 25, breakMin: Int = 5) {
        stopPomodoro()
        var isFocus = true
        AppState.pomodoroPhase.value = "Focus (${focusMin}m)"
        AppState.pomodoroRemainingSec.value = focusMin * 60
        buzz("double", 0)
        val r = object : Runnable {
            override fun run() {
                val rem = AppState.pomodoroRemainingSec.value - 1
                if (rem <= 0) {
                    isFocus = !isFocus
                    if (isFocus) {
                        AppState.pomodoroPhase.value = "Focus (${focusMin}m)"
                        AppState.pomodoroRemainingSec.value = focusMin * 60
                        buzz("double", 0)
                    } else {
                        AppState.pomodoroPhase.value = "Pausa (${breakMin}m)"
                        AppState.pomodoroRemainingSec.value = breakMin * 60
                        buzz("pulse", 0)
                    }
                } else {
                    AppState.pomodoroRemainingSec.value = rem
                }
                handler.postDelayed(this, 1_000L)
            }
        }
        pomodoroRunnable = r
        handler.postDelayed(r, 1_000L)
        refreshNotification()
    }

    fun stopPomodoro() {
        pomodoroRunnable?.let { handler.removeCallbacks(it) }
        pomodoroRunnable = null
        AppState.pomodoroPhase.value = ""
        AppState.pomodoroRemainingSec.value = 0
        refreshNotification()
    }

    // --- 4. Box Breathing Pacer (4s Inhale - 4s Hold - 4s Exhale - 4s Hold) ---
    fun startBoxBreathing(durationMinutes: Int = 2) {
        stopBoxBreathing()
        val phases = listOf(
            "Inspira (4s)" to "1",
            "Trattieni (4s)" to "14",
            "Espira (4s)" to "47",
            "Pausa (4s)" to "1",
        )
        var stepIdx = 0
        var totalRemainingSec = durationMinutes * 60
        AppState.breathingRemainingSec.value = totalRemainingSec
        val r = object : Runnable {
            override fun run() {
                if (totalRemainingSec <= 0) {
                    stopBoxBreathing()
                    buzz("double", 0)
                    return
                }
                if (totalRemainingSec % 4 == 0) {
                    val (label, spec) = phases[stepIdx % phases.size]
                    AppState.breathingPhase.value = label
                    buzz(spec, 0)
                    stepIdx++
                }
                totalRemainingSec--
                AppState.breathingRemainingSec.value = totalRemainingSec
                handler.postDelayed(this, 1_000L)
            }
        }
        breathingRunnable = r
        r.run()
    }

    fun stopBoxBreathing() {
        breathingRunnable?.let { handler.removeCallbacks(it) }
        breathingRunnable = null
        AppState.breathingPhase.value = ""
        AppState.breathingRemainingSec.value = 0
    }

    private fun stopAllTools() {
        pauseTimer()
        stopAlarm()
        setPeriodicChime(0)
        stopPomodoro()
        stopBoxBreathing()
    }

    private fun flushPending() {
        while (ready && pending.isNotEmpty()) pending.removeFirst().let { client.sendBuzz(it.first, it.second) }
    }

    private fun scheduleReconnect() {
        if (!running) return
        handler.postDelayed({ if (running && !ready) client.connectBonded() }, backoffMs)
        backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
    }

    private fun statusText(): String = when {
        AppState.alarmRinging.value -> "⏰ Allarme Timer in corso! Tocca Ferma o Snooze"
        !ready -> "In attesa del WHOOP..."
        AppState.timerRunning.value -> "Connesso • Timer attivo"
        AppState.pomodoroPhase.value.isNotEmpty() -> "Connesso • ${AppState.pomodoroPhase.value}"
        AppState.chimeIntervalMin.value > 0 -> "Connesso • Chime ogni ${AppState.chimeIntervalMin.value}m"
        else -> "Connesso al WHOOP (zero-telemetria)"
    }

    private fun startForegroundCompat(text: String) {
        val n = notification(text)
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIF_ID, n)
    }

    private fun refreshNotification() {
        if (!running) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(statusText()))
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "WhoopBuzz", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, PocActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("WhoopBuzz")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
        if (AppState.alarmRinging.value) {
            val stopPi = PendingIntent.getService(
                this, 1, Intent(this, WhoopService::class.java).setAction(ACTION_STOP_ALARM),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val snoozePi = PendingIntent.getService(
                this, 2, Intent(this, WhoopService::class.java).setAction(ACTION_SNOOZE_ALARM),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(Notification.Action.Builder(null, "FERMA", stopPi).build())
            builder.addAction(Notification.Action.Builder(null, "SNOOZE +5m", snoozePi).build())
        }
        return builder.build()
    }

    override fun onDestroy() {
        stopAllTools()
        running = false
        AppState.serviceRunning.value = false
        AppState.bleConnected.value = false
        handler.removeCallbacksAndMessages(null)
        client.disconnect()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.whoopbuzz.STOP"
        const val ACTION_STOP_ALARM = "com.whoopbuzz.STOP_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.whoopbuzz.SNOOZE_ALARM"
        const val EXTRA_PATTERN = "pattern"
        const val EXTRA_LOOPS = "loops"
        private const val CHANNEL = "whoopbuzz"
        private const val NOTIF_ID = 1
        @Volatile var instance: WhoopService? = null

        fun start(c: Context) {
            Prefs.setServiceEnabled(c, true)
            c.startForegroundService(Intent(c, WhoopService::class.java))
        }

        fun stop(c: Context) {
            Prefs.setServiceEnabled(c, false)
            c.startService(Intent(c, WhoopService::class.java).setAction(ACTION_STOP))
        }
    }
}

/** Reactive state shared between WhoopService and the Compose UI. */
object AppState {
    val serviceRunning = mutableStateOf(false)
    val bleConnected = mutableStateOf(false)
    val alarmRinging = mutableStateOf(false)
    val timerRemainingSec = mutableStateOf(300)
    val timerRunning = mutableStateOf(false)
    val chimeIntervalMin = mutableStateOf(0)
    val pomodoroPhase = mutableStateOf("")
    val pomodoroRemainingSec = mutableStateOf(0)
    val breathingPhase = mutableStateOf("")
    val breathingRemainingSec = mutableStateOf(0)
}

/** In-memory log shared by the service and the UI. */
object Log {
    val lines = mutableStateListOf<String>()
    fun add(line: String) {
        Handler(Looper.getMainLooper()).post { lines.add(0, line); if (lines.size > 200) lines.removeLast() }
    }
}