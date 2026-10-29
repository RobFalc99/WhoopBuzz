package com.whoopbuzz

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whoopbuzz.protocol.HapticStep
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
                var steps by remember {
                    mutableStateOf(listOf(HapticStep.NORMAL.code, HapticStep.PAUSE.code, HapticStep.NORMAL.code))
                }
                var extendedDuration by remember { mutableStateOf(false) }
                var showLogs by remember { mutableStateOf(false) }

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
                fun copyToClipboard(label: String, value: String) {
                    val cm = getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText(label, value))
                    Toast.makeText(this, "Copiato: $value", Toast.LENGTH_SHORT).show()
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
                        Text("I miei Pattern (tocca il nome per copiarlo)", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = {
                            savedPatterns = Prefs.resetDefaultPatterns(this@PocActivity)
                            Log.add("Pattern predefiniti ripristinati")
                        }) { Text("Ripristina predefiniti", fontSize = 11.sp) }
                    }
                    savedPatterns.forEach { p ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier.weight(1f).clickable { copyToClipboard("pattern", p.name) },
                                ) {
                                    Text("${p.name}  📋", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        HapticStep.formatSteps(p.effects, p.loops),
                                        fontSize = 11.sp,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(onClick = { buzz(p.effects, p.loops) }) { Text("▶", fontSize = 12.sp) }
                                    OutlinedButton(onClick = {
                                        editName = p.name
                                        steps = Whoop5Protocol.parseEffects(p.effects)
                                        extendedDuration = p.loops > 0
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
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nome del pattern (es. vip, casa, mamma)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Aggiungi passo (max 8):", fontSize = 12.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        HapticStep.entries.forEach { step ->
                            OutlinedButton(
                                onClick = {
                                    if (steps.size < 8) steps = steps + step.code
                                },
                                enabled = steps.size < 8,
                            ) { Text("+ ${step.label}", fontSize = 11.sp) }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Sequenza (tocca un passo per rimuoverlo):", fontSize = 11.sp)
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
                                Text("Nessun passo selezionato", fontSize = 12.sp)
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    steps.forEachIndexed { idx, code ->
                                        FilterChip(
                                            selected = true,
                                            onClick = {
                                                steps = steps.toMutableList().also { it.removeAt(idx) }
                                            },
                                            label = { Text("${HapticStep.labelFor(code)} ✕", fontSize = 11.sp) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                            onClick = {
                                buzz(steps.joinToString(","), if (extendedDuration) 1 else 0)
                            },
                            enabled = steps.isNotEmpty(),
                        ) { Text("Prova al polso") }
                        Button(
                            onClick = {
                                val name = editName.trim()
                                if (name.isEmpty() || steps.isEmpty()) {
                                    Log.add("Inserisci un nome e almeno un passo")
                                } else {
                                    val item = SavedPattern(
                                        name = name,
                                        effects = steps.joinToString(","),
                                        loops = if (extendedDuration) 1 else 0,
                                    )
                                    savedPatterns = Prefs.upsertPattern(this@PocActivity, item)
                                    Log.add("Salvato pattern '${item.name}'")
                                }
                            },
                            enabled = steps.isNotEmpty() && editName.isNotBlank(),
                        ) { Text("Salva pattern") }
                    }

                    HorizontalDivider()
                    Text("Collega a MacroDroid (Invia Intent → Broadcast)", style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedButton(onClick = { copyToClipboard("Action", BuzzReceiver.ACTION_BUZZ) }) {
                            Text("📋 Action: com.whoopbuzz.BUZZ", fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { copyToClipboard("Package", packageName) }) {
                            Text("📋 Package: com.whoopbuzz", fontSize = 11.sp)
                        }
                        Button(onClick = {
                            val firstPattern = savedPatterns.firstOrNull()?.name ?: "single"
                            sendBroadcast(
                                Intent(BuzzReceiver.ACTION_BUZZ)
                                    .setPackage(packageName)
                                    .putExtra("pattern", firstPattern)
                                    .putExtra("token", token)
                            )
                        }) { Text("Test Broadcast", fontSize = 11.sp) }
                    }
                    Text(
                        "In MacroDroid aggiungi l'extra 'pattern' con il nome del pattern (es. double, pulse, vip).",
                        fontSize = 11.sp,
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; Prefs.setToken(this@PocActivity, it) },
                        label = { Text("Token opzionale (lascia vuoto per non richiederlo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { showLogs = !showLogs }) {
                        Text(if (showLogs) "Nascondi stato tecnico" else "Mostra stato tecnico", fontSize = 11.sp)
                    }
                    if (showLogs) {
                        LazyColumn(Modifier.fillMaxWidth().height(150.dp)) {
                            items(Log.lines) { Text(it, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}