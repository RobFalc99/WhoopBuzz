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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PocActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var token by remember { mutableStateOf(Prefs.token(this)) }
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
                fun buzz(p: String, loops: Int = 0) {
                    val s = WhoopService.instance
                    if (s == null) Log.add("Avvia prima il servizio") else s.buzz(p, loops)
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
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("single", "double", "triple", "long", "pulse").forEach { name ->
                            Button(onClick = { buzz(name, 0) }) { Text(name, fontSize = 11.sp) }
                        }
                    }
                    Text("Laboratorio & Sweep", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = labEffects, onValueChange = { labEffects = it },
                            label = { Text("Effetti") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = labLoops, onValueChange = { labLoops = it },
                            label = { Text("Loops") }, singleLine = true, modifier = Modifier.width(72.dp),
                        )
                        Button(onClick = { buzz(labEffects, labLoops.toIntOrNull() ?: 0) }) { Text("Invia") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1", "14", "15", "47", "52").forEach { id ->
                            OutlinedButton(onClick = { buzz(id, labLoops.toIntOrNull() ?: 0) }) { Text(id) }
                        }
                    }
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
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; Prefs.setToken(this@PocActivity, it) },
                        label = { Text("Token per broadcast (MacroDroid)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn { items(Log.lines) { Text(it, fontSize = 11.sp) } }
                }
            }
        }
    }
}