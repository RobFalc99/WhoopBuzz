package com.whoopbuzz

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.whoopbuzz.protocol.HapticStep
import com.whoopbuzz.protocol.SavedPattern
import com.whoopbuzz.protocol.Whoop5Protocol

private val WhoopColorScheme = darkColorScheme(
    primary = Color(0xFF00E676),
    onPrimary = Color(0xFF00210B),
    primaryContainer = Color(0xFF005227),
    onPrimaryContainer = Color(0xFF74FF9E),
    secondary = Color(0xFF4FC3F7),
    background = Color(0xFF0F1318),
    surface = Color(0xFF181E26),
    surfaceVariant = Color(0xFF222A35),
    onBackground = Color(0xFFECEFF4),
    onSurface = Color(0xFFECEFF4),
    onSurfaceVariant = Color(0xFFB0BEC5),
)

class PocActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = WhoopColorScheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WhoopBuzzApp(this)
                }
            }
        }
    }

    fun isBatteryIgnored(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    fun openBatteryOptimizationSettings() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(direct)
                return
            } catch (_: Exception) {
                // Fallback below
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }
}

private fun formatMmSs(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhoopBuzzApp(activity: PocActivity) {
    var currentTab by remember { mutableIntStateOf(0) }
    var batteryIgnored by remember { mutableStateOf(activity.isBatteryIgnored()) }
    var savedPatterns by remember { mutableStateOf(Prefs.patterns(activity)) }
    var token by remember { mutableStateOf(Prefs.token(activity)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryIgnored = activity.isBatteryIgnored()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val perms = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.all { it }) WhoopService.start(activity) else Log.add("Permessi negati")
    }

    fun ensureServiceAndRun(block: (WhoopService) -> Unit) {
        val svc = WhoopService.instance
        if (svc != null) {
            block(svc)
        } else {
            WhoopService.start(activity)
            Toast.makeText(activity, "Servizio avviato: ripremi tra un istante", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(label: String, value: String) {
        val cm = activity.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(activity, "Copiato: $value", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Text("🎛️", fontSize = 18.sp) },
                    label = { Text("Ritmi") },
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Text("⏱️", fontSize = 18.sp) },
                    label = { Text("Strumenti") },
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Text("📖", fontSize = 18.sp) },
                    label = { Text("Guida & Link") },
                )
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top Status Header Card
            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val dotColor = when {
                                AppState.bleConnected.value -> Color(0xFF00E676)
                                AppState.serviceRunning.value -> Color(0xFFFFB300)
                                else -> Color(0xFFEF5350)
                            }
                            Box(Modifier.size(12.dp).clip(CircleShape).background(dotColor))
                            Column {
                                Text("WhoopBuzz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                val sub = when {
                                    AppState.bleConnected.value -> "WHOOP 5.0/MG connesso • Zero-Telemetria"
                                    AppState.serviceRunning.value -> "Servizio attivo • Ricerca WHOOP..."
                                    else -> "Servizio fermo • Premi Avvia"
                                }
                                Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!AppState.serviceRunning.value) {
                            Button(onClick = { launcher.launch(perms) }) { Text("Avvia") }
                        } else {
                            OutlinedButton(onClick = { WhoopService.stop(activity) }) { Text("Stop") }
                        }
                    }

                    // Battery Optimization Banner / Button
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { activity.openBatteryOptimizationSettings() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (batteryIgnored) "⚡ Batteria: Senza restrizioni (OK)"
                                else "⚠️ Batteria: Ottimizzazione attiva (Tocca qui)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (batteryIgnored) Color(0xFF00E676) else Color(0xFFFFCA28),
                            )
                            Text(
                                if (batteryIgnored) "Il servizio non verrà chiuso in background. Tocca per aprire impostazioni."
                                else "Tocca per consentire a WhoopBuzz di restare attivo a schermo spento.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Apri ›", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Active alarm banner if ringing
                    if (AppState.alarmRinging.value) {
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C)),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("⏰ ALLARME IN CORSO!", fontWeight = FontWeight.Bold, color = Color.White)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { WhoopService.instance?.stopAlarm() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    ) { Text("FERMA") }
                                    OutlinedButton(
                                        onClick = { WhoopService.instance?.snoozeAlarm(5) },
                                    ) { Text("Snooze +5m", color = Color.White) }
                                }
                            }
                        }
                    }
                }
            }

            when (currentTab) {
                0 -> RhythmsTab(
                    activity = activity,
                    savedPatterns = savedPatterns,
                    onPatternsChanged = { savedPatterns = it },
                    onBuzz = { spec, loops -> ensureServiceAndRun { it.buzz(spec, loops) } },
                    onCopy = ::copyToClipboard,
                )
                1 -> ToolsTab(
                    savedPatterns = savedPatterns,
                    ensureServiceAndRun = ::ensureServiceAndRun,
                )
                2 -> GuideAndAutomationTab(
                    activity = activity,
                    token = token,
                    onTokenChange = { token = it; Prefs.setToken(activity, it) },
                    savedPatterns = savedPatterns,
                    onCopy = ::copyToClipboard,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RhythmsTab(
    activity: PocActivity,
    savedPatterns: List<SavedPattern>,
    onPatternsChanged: (List<SavedPattern>) -> Unit,
    onBuzz: (String, Int) -> Unit,
    onCopy: (String, String) -> Unit,
) {
    var editName by remember { mutableStateOf("") }
    var steps by remember {
        mutableStateOf(listOf(HapticStep.NORMAL.code, HapticStep.PAUSE.code, HapticStep.NORMAL.code))
    }
    var extendedDuration by remember { mutableStateOf(false) }

    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Catalogo & I miei Ritmi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Tocca ▶ per provarlo al polso, o il nome per copiarlo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    onPatternsChanged(Prefs.resetDefaultPatterns(activity))
                    Toast.makeText(activity, "Catalogo completo (9 ritmi) caricato", Toast.LENGTH_SHORT).show()
                }) { Text("Carica 9 preset", fontSize = 11.sp) }
            }

            savedPatterns.forEach { p ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(1f).clickable { onCopy("pattern", p.name) },
                        ) {
                            Text("${p.name}  📋", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                HapticStep.formatSteps(p.effects, p.loops),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(onClick = { onBuzz(p.effects, p.loops) }) { Text("▶", fontSize = 12.sp) }
                            OutlinedButton(onClick = {
                                editName = p.name
                                steps = Whoop5Protocol.parseEffects(p.effects)
                                extendedDuration = p.loops > 0
                            }) { Text("✎", fontSize = 12.sp) }
                            OutlinedButton(onClick = {
                                onPatternsChanged(Prefs.deletePattern(activity, p.name))
                            }) { Text("✕", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }

    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Studio Compositore Ritmi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("Nome del ritmo (es. vip, casa, mamma)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Tocca i blocchi per comporre la sequenza (max 8):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HapticStep.entries.forEach { step ->
                    OutlinedButton(
                        onClick = { if (steps.size < 8) steps = steps + step.code },
                        enabled = steps.size < 8,
                    ) { Text("+ ${step.label}", fontSize = 12.sp) }
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Passi (tocca un passo per rimuoverlo):", fontSize = 11.sp)
                        Row {
                            TextButton(
                                onClick = { if (steps.isNotEmpty()) steps = steps.dropLast(1) },
                                enabled = steps.isNotEmpty(),
                            ) { Text("⌫ Indietro", fontSize = 11.sp) }
                            TextButton(
                                onClick = { steps = emptyList() },
                                enabled = steps.isNotEmpty(),
                            ) { Text("Svuota", fontSize = 11.sp) }
                        }
                    }
                    if (steps.isEmpty()) {
                        Text("Nessun passo selezionato", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            steps.forEachIndexed { idx, code ->
                                FilterChip(
                                    selected = true,
                                    onClick = { steps = steps.toMutableList().also { it.removeAt(idx) } },
                                    label = { Text("${HapticStep.labelFor(code)} ✕", fontSize = 11.sp) },
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !extendedDuration,
                    onClick = { extendedDuration = false },
                    label = { Text("Durata normale", fontSize = 11.sp) },
                )
                FilterChip(
                    selected = extendedDuration,
                    onClick = { extendedDuration = true },
                    label = { Text("Durata estesa", fontSize = 11.sp) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onBuzz(steps.joinToString(","), if (extendedDuration) 1 else 0) },
                    enabled = steps.isNotEmpty(),
                ) { Text("Prova al polso") }
                Button(
                    onClick = {
                        val name = editName.trim()
                        if (name.isNotEmpty() && steps.isNotEmpty()) {
                            val item = SavedPattern(name, steps.joinToString(","), if (extendedDuration) 1 else 0)
                            onPatternsChanged(Prefs.upsertPattern(activity, item))
                            Toast.makeText(activity, "Salvato '${item.name}'", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = steps.isNotEmpty() && editName.isNotBlank(),
                ) { Text("Salva ritmo") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolsTab(
    savedPatterns: List<SavedPattern>,
    ensureServiceAndRun: ((WhoopService) -> Unit) -> Unit,
) {
    var selectedAlarmPattern by remember { mutableStateOf("alarm") }
    var maxRingSec by remember { mutableIntStateOf(30) }

    // 1. Wrist Timer with Intermittent Alarm & Snooze
    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("⏰ Timer da Polso con Snooze", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Alla scadenza il WHOOP vibra a intermittenza finché non premi Ferma o Snooze (+5m), oppure fino al tempo massimo scelto.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    formatMmSs(AppState.timerRemainingSec.value),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    "10s (test)" to 10,
                    "1m" to 60,
                    "3m" to 180,
                    "5m" to 300,
                    "10m" to 600,
                    "15m" to 900,
                    "30m" to 1800,
                ).forEach { (label, sec) ->
                    OutlinedButton(onClick = {
                        AppState.timerRemainingSec.value = sec
                    }) { Text(label, fontSize = 11.sp) }
                }
            }

            Text("Durata massima vibrazione continua:", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10, 20, 30, 60).forEach { sec ->
                    FilterChip(
                        selected = maxRingSec == sec,
                        onClick = { maxRingSec = sec },
                        label = { Text("${sec}s", fontSize = 11.sp) },
                    )
                }
            }

            Text("Ritmo sveglia:", fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val names = (listOf("alarm", "long", "pulse", "double") + savedPatterns.map { it.name }).distinct()
                names.forEach { name ->
                    FilterChip(
                        selected = selectedAlarmPattern == name,
                        onClick = { selectedAlarmPattern = name },
                        label = { Text(name, fontSize = 11.sp) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!AppState.timerRunning.value) {
                    Button(onClick = {
                        val patternEff = savedPatterns.firstOrNull { it.name == selectedAlarmPattern }?.effects ?: selectedAlarmPattern
                        ensureServiceAndRun {
                            it.startTimer(AppState.timerRemainingSec.value, patternEff, maxRingSec)
                        }
                    }) { Text("Avvia Timer") }
                } else {
                    Button(onClick = { WhoopService.instance?.pauseTimer() }) { Text("Pausa") }
                }
                OutlinedButton(onClick = {
                    WhoopService.instance?.resetTimer(300) ?: run { AppState.timerRemainingSec.value = 300 }
                }) { Text("Reset") }
                if (AppState.alarmRinging.value) {
                    Button(
                        onClick = { WhoopService.instance?.stopAlarm() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                    ) { Text("FERMA") }
                    OutlinedButton(onClick = { WhoopService.instance?.snoozeAlarm(5) }) {
                        Text("Snooze +5m")
                    }
                }
            }
        }
    }

    // 2. Pomodoro & Periodic Chime
    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🍅 Pomodoro & Segnatempo Periodico", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Ciclo Focus 25m / Pausa 5m automatico, oppure un tocco discreto ogni X minuti per postura, acqua o percezione del tempo.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Sessione Pomodoro (25m / 5m)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        if (AppState.pomodoroPhase.value.isEmpty()) "Inattivo"
                        else "${AppState.pomodoroPhase.value} • ${formatMmSs(AppState.pomodoroRemainingSec.value)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (AppState.pomodoroPhase.value.isEmpty()) {
                    Button(onClick = { ensureServiceAndRun { it.startPomodoro(25, 5) } }) {
                        Text("Avvia Pomodoro")
                    }
                } else {
                    OutlinedButton(onClick = { WhoopService.instance?.stopPomodoro() }) {
                        Text("Ferma")
                    }
                }
            }

            HorizontalDivider()
            Text("Segnatempo discreto (Chime ogni X minuti):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Spento", 5 to "Ogni 5m", 15 to "Ogni 15m", 30 to "Ogni 30m", 60 to "Ogni 60m").forEach { (min, label) ->
                    FilterChip(
                        selected = AppState.chimeIntervalMin.value == min,
                        onClick = { ensureServiceAndRun { it.setPeriodicChime(min, "gentle") } },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
        }
    }

    // 3. Guided Box Breathing (4-4-4-4)
    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🧘 Respirazione Guidata al Polso (4-4-4-4)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Chiudi gli occhi e segui il ritmo sul polso ogni 4 secondi: Tocco (Inspira) → Corto (Trattieni) → Normale (Espira) → Tocco (Pausa).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (AppState.breathingPhase.value.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${AppState.breathingPhase.value}  •  ${formatMmSs(AppState.breathingRemainingSec.value)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (AppState.breathingPhase.value.isEmpty()) {
                    Button(onClick = { ensureServiceAndRun { it.startBoxBreathing(1) } }) { Text("1 min") }
                    Button(onClick = { ensureServiceAndRun { it.startBoxBreathing(2) } }) { Text("2 min") }
                    Button(onClick = { ensureServiceAndRun { it.startBoxBreathing(5) } }) { Text("5 min") }
                } else {
                    OutlinedButton(onClick = { WhoopService.instance?.stopBoxBreathing() }) {
                        Text("Ferma Respirazione")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuideAndAutomationTab(
    activity: PocActivity,
    token: String,
    onTokenChange: (String) -> Unit,
    savedPatterns: List<SavedPattern>,
    onCopy: (String, String) -> Unit,
) {
    var showLogs by remember { mutableStateOf(false) }

    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🔗 Parametri Rapidi per MacroDroid / Altre App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Tocca un pulsante per copiarlo negli appunti e incollarlo in MacroDroid:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(onClick = { onCopy("Action", BuzzReceiver.ACTION_BUZZ) }) {
                    Text("📋 Action: com.whoopbuzz.BUZZ", fontSize = 11.sp)
                }
                OutlinedButton(onClick = { onCopy("Package", activity.packageName) }) {
                    Text("📋 Package: com.whoopbuzz", fontSize = 11.sp)
                }
                Button(onClick = {
                    val firstPattern = savedPatterns.firstOrNull()?.name ?: "single"
                    activity.sendBroadcast(
                        Intent(BuzzReceiver.ACTION_BUZZ)
                            .setPackage(activity.packageName)
                            .putExtra("pattern", firstPattern)
                            .putExtra("token", token)
                    )
                }) { Text("Prova Broadcast", fontSize = 11.sp) }
            }
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                label = { Text("Token sicurezza (opzionale, lascia vuoto per non richiederlo)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🎛️ Come funzionano i blocchi dei Ritmi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("• Tocco: micro-impulso singolo leggerissimo.", fontSize = 12.sp)
            Text("• Corto / Normale / Medio: singolo impulso di intensità e corpo crescenti.", fontSize = 12.sp)
            Text("• Raffica: 4 micro-colpi velocissimi in sequenza (ottimo per chiamate importanti).", fontSize = 12.sp)
            Text("• Pausa: inserisce uno stacco netto tra due vibrazioni. Se metti più impulsi vicini SENZA Pausa (es. Normale → Normale → Normale), lo strap li fonde in un'unica vibrazione lunga!", fontSize = 12.sp)
        }
    }

    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🤖 Guida passo-passo MacroDroid & BuzzKill", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("1. In MacroDroid crea una nuova Macro e nel blocco blu Azioni (Actions) premi + → Connettività → Invia Intent (Send Intent).", fontSize = 12.sp)
            Text("2. Imposta Destinazione su Broadcast, incolla Action (com.whoopbuzz.BUZZ) e Package (com.whoopbuzz).", fontSize = 12.sp)
            Text("3. In Extra 1 scrivi come Nome parametro: pattern  e come Valore il nome del ritmo (es. double, pulse, heartbeat, alarm o uno creato da te).", fontSize = 12.sp)
            Text("4. Nel blocco rosso Attivatori (Triggers) scegli quando farlo vibrare:\n   • Posizione → Geofence (quando arrivi in un luogo)\n   • Chiamate/SMS → Chiamata in arrivo (dai contatti importanti)\n   • Eventi Dispositivo → Notifica ricevuta (messaggi da una persona)", fontSize = 12.sp)
            Text("5. Usi già BuzzKill? Nelle regole di BuzzKill puoi attivare una macro di MacroDroid come azione, oppure lasciare che MacroDroid intercetti le notifiche direttamente.", fontSize = 12.sp)
            Text("6. Sviluppi un'altra app? Nel repo trovi docs/WHOOP_HAPTIC_REFERENCE.md con il codice pronto per richiamare WhoopBuzz o integrare il protocollo BLE in qualsiasi progetto.", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }

    TextButton(onClick = { showLogs = !showLogs }) {
        Text(if (showLogs) "Nascondi log tecnico" else "Mostra log tecnico", fontSize = 11.sp)
    }
    if (showLogs) {
        LazyColumn(Modifier.fillMaxWidth().height(150.dp)) {
            items(Log.lines) { Text(it, fontSize = 11.sp) }
        }
    }
}
