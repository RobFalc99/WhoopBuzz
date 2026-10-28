package com.whoopbuzz

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whoopbuzz.ble.WhoopBleClient

class PocActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val logs = remember { mutableStateListOf<String>() }
                val client = remember {
                    WhoopBleClient(this) { line -> runOnUiThread { logs.add(0, line) } }
                }
                val perms = if (Build.VERSION.SDK_INT >= 31)
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { res ->
                    if (res.values.all { it }) client.connectBonded() else logs.add(0, "Permessi negati")
                }
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("WhoopBuzz PoC", style = MaterialTheme.typography.headlineSmall)
                    Text("Lo strap deve essere associato al telefono. L'app WHOOP puo restare connessa.", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { launcher.launch(perms) }) { Text("Connetti") }
                        Button(onClick = { client.sendHello() }) { Text("Hello") }
                        Button(onClick = { client.sendBuzz(1) }) { Text("Buzz") }
                        Button(onClick = { client.disconnect() }) { Text("Stop") }
                    }
                    LazyColumn { items(logs) { Text(it, fontSize = 11.sp) } }
                }
            }
        }
    }
}