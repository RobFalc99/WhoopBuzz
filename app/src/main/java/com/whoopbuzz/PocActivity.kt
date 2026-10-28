package com.whoopbuzz

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whoopbuzz.protocol.SavedPattern
import com.whoopbuzz.protocol.Whoop5Protocol

class PocActivity : ComponentActivity() {
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var token by remember { mutableStateOf(Prefs.token(this)) }
                var savedPatterns by remember { mutableStateOf(Prefs.patterns(this)) }
                var editName by remember { mutableStateOf("") }
                var labEffects by remember { mutableStateOf("47,152,47") }
                var labLoops by remember { mutableStateOf("0") }
                var sweepStart by remember { mutableStateOf("1") }
                var sweepEnd by remember { mutableStateOf("30") }
                val sweepHandler = remember { Handler(Looper.getMainLooper()) }
                DisposableEffect(Unit) { onDispose { sweepHandler.removeCallbacksAndMessages(null) } }

                val perms = buildList {
                    if (Build.VERSION.SDK_INT >= 31) {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    } else add(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray()
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { res ->
                    if (res.values.all { it }) WhoopService.start(this) else Log.add("Permessi negati")
                }
                fun buzz(spec: String, loops: Int = 0) {
                    val s = WhoopService.instance
                    if (s == null) Log.add("Avvia prima il servizio") else s.buzz(spec, loops)
                }
                fun appendEffect(id: String) {
                    val cur = labEffects.trim().trimEnd(',')
                    val count = Whoop5Protocol.parseEffects(cur).size
                    if (count >= 8) {
                        Log.add("Massimo 8 valori per pattern")
                        return
                    }
                    labEffects = if (cur.isEmpty()) id else "$cur,$id"
                }
                fun runSweep(items: List<Pair<String, String>>, delayMs: Long) {
                    sweepHandler.removeCallbacksAndMessages(null)
                    if (WhoopService.instance == null) { Log.add("Avvia prima il servizio"); return }
                    items.forEachIndexed { idx, (label, spec) ->
                        sweepHandler.postDelayed({
                            Log.add(">>> SWEEP $label ($spec)")
                            WhoopService.instance?.buzz(spec, 0)
                        }, idx * delayMs)
                    }
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("WhoopBuzz", style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { launcher.launch(perms) }) { Text("Avvia servizio") }
                        OutlinedButton(onClick = { WhoopService.stop(this@PocActivity) }) { Text("Stop") }
                        OutlinedButton(onClick = {
                            startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    .setData(Uri.parse("package:$packageName"))
                            )
                        }) { Text("Batteria") }
                    }

                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("I miei Pattern (tocca ▶ per vibrare)", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = {
                            savedPatterns = Prefs.resetDefaultPatterns(this@PocActivity)
                            Log.add("Pattern default ripristinati")
                        }) { Text("Ripristina default", fontSize = 11.sp) }
                    }
                    savedPatterns.forEach { p ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.bodyMedium)
                                    Text("effetti: ${p.effects}  •  loops: ${p.loops}", fontSize = 11.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(onClick = { buzz(p.effects, p.loops) }) { Text("▶", fontSize = 12.sp) }
                                    OutlinedButton(onClick = {
                                        editName = p.name
                                        labEffects = p.effects
                                        labLoops = p.loops.toString()
                                    }) { Text("✎", fontSize = 12.sp) }
                                    OutlinedButton(onClick = {
                                        savedPatterns = Prefs.deletePattern(this@PocActivity, p.name)
                                        Log.add("Eliminato '${p.name}'")
                                    }) { Text("✕", fontSize = 12.sp) }
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Text("Crea / Modifica Pattern", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Nome (es. vip, casa)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = labLoops,
                            onValueChange = { labLoops = it },
                            label = { Text("Loops") },
                            singleLine = true,
                            modifier = Modifier.width(76.dp),
                        )
                    }
                    OutlinedTextField(
                        value = labEffects,
                        onValueChange = { labEffects = it },
                        label = { Text("Effetti (max 8 byte separati da virgola)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf(
                            "+47 breve" to "47",
                            "+152 pausa" to "152",
                            "+52 4x micro" to "52",
                            "+15 medio" to "15",
                            "+14 corto" to "14",
                            "+1 micro" to "1",
                        ).forEach { (label, id) ->
                            OutlinedButton(onClick = { appendEffect(id) }) { Text(label, fontSize = 11.sp) }
                        }
                        TextButton(onClick = { labEffects = "" }) { Text("Svuota", fontSize = 11.sp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { buzz(labEffects, labLoops.toIntOrNull() ?: 0) }) {
                            Text("Prova")
                        }
                        Button(onClick = {
                            val name = editName.trim()
                            val parsed = Whoop5Protocol.parseEffects(labEffects)
                            if (name.isEmpty() || parsed.isEmpty()) {
                                Log.add("Inserisci un nome e almeno un effetto valido")
                            } else {
                                val item = SavedPattern(name, parsed.joinToString(","), (labLoops.toIntOrNull() ?: 0).coerceIn(0, 5))
                                savedPatterns = Prefs.upsertPattern(this@PocActivity, item)
                                Log.add("Salvato pattern '${item.name}' -> ${item.effects} (loops=${item.loops})")
                            }
                        }) { Text("Salva pattern") }
                    }

                    HorizontalDivider()
                    Text("Sweep esplorazione ID / Pause", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = sweepStart, onValueChange = { sweepStart = it },
                            label = { Text("Da") }, singleLine = true, modifier = Modifier.width(68.dp),
                        )
                        OutlinedTextField(
                            value = sweepEnd, onValueChange = { sweepEnd = it },
                            label = { Text("A") }, singleLine = true, modifier = Modifier.width(68.dp),
                        )
                        Button(onClick = {
                            val a = (sweepStart.toIntOrNull() ?: 1).coerceIn(1, 255)
                            val b = (sweepEnd.toIntOrNull() ?: a).coerceIn(a, 255)
                            runSweep((a..b).map { "ID $it" to "$it" }, 2500L)
                        }) { Text("Sweep ID", fontSize = 11.sp) }
                        Button(onClick = {
                            val pauses = (130..200 step 5).map { "P=$it" to "47,$it,47" }
                            runSweep(pauses, 3000L)
                        }) { Text("Sweep Pause", fontSize = 11.sp) }
                        OutlinedButton(onClick = {
                            sweepHandler.removeCallbacksAndMessages(null)
                            Log.add("Sweep fermato")
                        }) { Text("Stop", fontSize = 11.sp) }
                    }

                    HorizontalDivider()
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; Prefs.setToken(this@PocActivity, it) },
                        label = { Text("Token per broadcast (MacroDroid: extra pattern=<nome>)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Log", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.fillMaxWidth().height(180.dp)) {
                        items(Log.lines) { Text(it, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}