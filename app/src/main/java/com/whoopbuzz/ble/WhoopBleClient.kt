package com.whoopbuzz.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.whoopbuzz.protocol.Whoop5Protocol
import com.whoopbuzz.protocol.toHex
import java.util.UUID

/**
 * Minimal PoC client for a WHOOP 5.0/MG: scan -> connect -> hello -> buzz.
 * Caller must hold BLUETOOTH_SCAN / BLUETOOTH_CONNECT before calling.
 */
@SuppressLint("MissingPermission")
class WhoopBleClient(
    context: Context,
    private val log: (String) -> Unit,
    private val onReady: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var gatt: BluetoothGatt? = null
    private var cmdChar: BluetoothGattCharacteristic? = null
    private var seq = 1
    private val pendingNotify = ArrayDeque<BluetoothGattCharacteristic>()

    val isConnected get() = cmdChar != null

    private val scanCb: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            log("Trovato: ${result.device.name ?: "?"} ${result.device.address}")
            stopScan()
            connect(result.device)
        }
        override fun onScanFailed(errorCode: Int) = log("Scan fallita: $errorCode")
    }

    /** Connect straight to the already-bonded WHOOP (no scan): a connected strap does not advertise. */
    fun connectBonded() {
        val dev = adapter.bondedDevices.firstOrNull { it.name?.startsWith("WHOOP", ignoreCase = true) == true }
        if (dev == null) {
            log("Nessun WHOOP associato (${adapter.bondedDevices.size} dispositivi associati): provo la scansione")
            startScan()
            return
        }
        log("WHOOP associato: ${dev.name} ${dev.address}")
        connect(dev)
    }

    fun startScan() {
        val byService = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(UUID.fromString(Whoop5Protocol.SERVICE_UUID))).build()
        val byName = ScanFilter.Builder().setDeviceName("WHOOP").build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        log("Scansione WHOOP 5.0/MG...")
        adapter.bluetoothLeScanner.startScan(listOf(byService), settings, scanCb)
    }

    fun stopScan() = adapter.bluetoothLeScanner.stopScan(scanCb)

    private fun connect(device: BluetoothDevice) {
        log("Connessione a ${device.address}...")
        gatt = device.connectGatt(appContext, false, gattCb, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCb: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            log("Stato connessione: status=$status state=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                cmdChar = null
                writeQueue.clear(); writing = false
                g.close()
                if (gatt === g) gatt = null
                onDisconnected()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(UUID.fromString(Whoop5Protocol.SERVICE_UUID))
            if (svc == null) { log("Servizio WHOOP 5 non trovato"); return }
            cmdChar = svc.getCharacteristic(UUID.fromString(Whoop5Protocol.COMMAND_CHAR_UUID))
            Whoop5Protocol.NOTIFY_CHAR_UUIDS.forEach { id ->
                svc.getCharacteristic(UUID.fromString(id))?.let { pendingNotify.add(it) }
            }
            log("Servizi trovati, abilito notifiche (${pendingNotify.size})")
            nextNotify(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            nextNotify(g)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            log("Write ${c.uuid.toString().take(8)} status=$status")
            writing = false
            pumpQueue()
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            log("RX ${c.uuid.toString().take(8)}: ${c.value?.toHex()}")
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            log("RX ${c.uuid.toString().take(8)}: ${value.toHex()}")
        }
    }

    private fun nextNotify(g: BluetoothGatt) {
        val c = pendingNotify.removeFirstOrNull()
        if (c == null) {
            log("Pronto.")
            sendHello()
            onReady()
            return
        }
        g.setCharacteristicNotification(c, true)
        val cccd = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        if (cccd == null) { nextNotify(g); return }
        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
    }

    private val writeQueue = ArrayDeque<Pair<ByteArray, String>>()
    private var writing = false

    private fun send(frame: ByteArray, what: String) {
        if (gatt == null || cmdChar == null) { log("Non connesso"); return }
        writeQueue.add(frame to what)
        pumpQueue()
    }

    /** One outstanding GATT write at a time; a confirmed write also triggers bonding if needed. */
    private fun pumpQueue() {
        if (writing) return
        val g = gatt; val c = cmdChar
        val next = writeQueue.removeFirstOrNull() ?: return
        if (g == null || c == null) return
        log("TX ${next.second}: ${next.first.toHex()}")
        writing = true
        g.writeCharacteristic(c, next.first, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }

    fun sendHello() = send(Whoop5Protocol.hello(seq++), "hello")
    fun sendBuzz(pattern: String = Whoop5Protocol.DEFAULT_PATTERN, loops: Int = 1) =
        send(Whoop5Protocol.buzz(pattern, loops, seq++), "buzz $pattern x$loops")
    fun disconnect() {
        val g = gatt; gatt = null; cmdChar = null
        writeQueue.clear(); writing = false
        g?.disconnect(); g?.close()
    }
}