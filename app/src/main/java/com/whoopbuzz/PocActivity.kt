package com.whoopbuzz

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
                fun buzz(p: String) {
                    val s = WhoopService.instance
                    if (s == null) Log.add("Avvia prima il servizio") else s.buzz(p, 1)
                }
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("WhoopBuzz", style = MaterialTheme.typography.headlineSmall)
                    Text("Lo strap deve essere associato al telefono. L'app WHOOP puo restare connessa.", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { launcher.launch(perms) }) { Text("Avvia servizio") }
                        OutlinedButton(onClick = { WhoopService.stop(this@PocActivity) }) { Text("Stop") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { buzz("A") }) { Text("Buzz A") }
                        Button(onClick = { buzz("B") }) { Text("Buzz B") }
                        Button(onClick = { buzz("C") }) { Text("Buzz C") }
                    }
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; Prefs.setToken(this@PocActivity, it) },
                        label = { Text("Token per broadcast (MacroDroid)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = {
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:$packageName"))
                        )
                    }) { Text("Escludi da ottimizzazione batteria") }
                    LazyColumn { items(Log.lines) { Text(it, fontSize = 11.sp) } }
                }
            }
        }
    }
}