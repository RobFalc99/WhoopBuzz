# WHOOP 5.0 / MG Haptic Engine — Developer & AI Agent Reference

> **Purpose of this document**: Canonical knowledge base for controlling the vibration motor (haptics) of a **WHOOP 5.0 / MG** strap from any Android (Kotlin/Java/Flutter) application.
> If another project in `C:\Users\rf199\Progetti` needs to trigger vibrations on the user's WHOOP strap, read this file first.

---

## 1. Two Ways to Trigger WHOOP Haptics from Another App

### Option A — Via `WhoopBuzz` Broadcast API (Recommended, 5 lines of code, zero BLE setup)
If `WhoopBuzz` (`com.whoopbuzz`) is installed and running on the phone, **any app** (Kotlin, Flutter, React Native, Tasker, MacroDroid) can trigger a vibration by sending an Android explicit broadcast Intent:

#### Kotlin / Android
```kotlin
fun buzzWhoop(context: Context, pattern: String = "double", token: String = "") {
    val intent = Intent("com.whoopbuzz.BUZZ").apply {
        setPackage("com.whoopbuzz")
        putExtra("pattern", pattern) // e.g. "single", "double", "pulse", "heartbeat", "alarm", or custom name
        if (token.isNotEmpty()) putExtra("token", token)
    }
    context.sendBroadcast(intent)
}
```

#### Raw custom sequence from external app
```kotlin
val intent = Intent("com.whoopbuzz.BUZZ").apply {
    setPackage("com.whoopbuzz")
    putExtra("effects", "14,152,15,152,52") // Up to 8 comma-separated effect/pause bytes
    putExtra("loops", 0)                    // 0 = normal duration, 1..5 = extended duration
}
context.sendBroadcast(intent)
```

---

### Option B — Standalone BLE Integration (Copy 2 files into your project)
If you want your new app to talk directly to the WHOOP 5.0/MG over BLE without `WhoopBuzz` installed:
1. Copy these two self-contained files from this repository:
   - `app/src/main/java/com/whoopbuzz/protocol/Whoop5Protocol.kt` (zero Android dependencies, pure JVM frame encoder + CRC16-Modbus + CRC32)
   - `app/src/main/java/com/whoopbuzz/ble/WhoopBleClient.kt` (low-power Android GATT client)
2. Add permissions to `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
   <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
   ```
3. **CRITICAL BLE RULE — Do NOT scan for an already-connected WHOOP**:
   - Because the official WHOOP app keeps an active BLE connection to the strap 24/7, the strap **does not advertise** while connected.
   - Instead, query `bluetoothAdapter.bondedDevices`, find the device whose `name.startsWith("WHOOP", ignoreCase = true)`, and call `device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)`.
   - Android multiplexes the existing ACL link across both the official WHOOP app and your app simultaneously.
4. **CRITICAL BATTERY RULE — Zero-Telemetry Mode**:
   - Do **NOT** enable notifications (`setCharacteristicNotification`) on `fd4b0003`, `fd4b0004`, `fd4b0005`, or `fd4b0007` if you only need haptics. Those channels stream 1 Hz heart rate and historical data; subscribing to them wakes up your app's CPU every second.
   - Only write to `fd4b0002-cce1-4033-93ce-002d5875f58a`.

---

## 2. Wire Protocol Specification (WHOOP 5.0 / MG "Puffin")

### GATT UUIDs
| Role | UUID |
|---|---|
| Primary Service | `fd4b0001-cce1-4033-93ce-002d5875f58a` |
| Command Write Characteristic (`WRITE_TYPE_DEFAULT`) | `fd4b0002-cce1-4033-93ce-002d5875f58a` |

### Frame Envelope
```text
Offset  Size   Field          Description
0       1      SOF            0xAA
1       1      Format         0x01
2..3    2      declLength     uint16 LE = paddedBody.size + 4 (includes CRC32 trailer)
4..5    2      Header Flags   0x00, 0x01
6..7    2      Header CRC16   CRC16-Modbus (poly 0xA001, init 0xFFFF, reflected) over bytes 0..5
8..N    4*k    Padded Body    [0x23 (TYPE_COMMAND), seq, cmdId, ...params] zero-padded to multiple of 4 bytes
N..N+3  4      Payload CRC32  Standard IEEE 802.3 / zlib CRC32 (uint32 LE) over Padded Body (bytes 8..N)
```

### Session Handshake (`GET_HELLO` = `0x91`)
Send once right after service discovery before sending haptic commands:
- Command ID: `0x91` (`145`), payload: `[0x01]`
- With `seq = 1`, the full 16-byte frame is `aa0108000001e67123019101363e5c8d`.

### Haptic Command (`RUN_HAPTIC_PATTERN_MAVERICK` = `0x13`)
- Command ID: `0x13` (`19`)
- 12-byte parameter payload:
  ```text
  Byte 0:     0x01 (Revision 1)
  Bytes 1..8: effectSlot[0..7] (8 bytes of waveform effect IDs or pause bytes, unused slots = 0x00)
  Bytes 9..10: 0x00, 0x00 (uint16 LE per-effect loop control)
  Byte 11:    overallLoop (0 = single play, 1..255 = extends/Sustains vibration without gap)
  ```

---

## 3. Hardware-Verified Effect & Pause Table (Tested on real WHOOP 5.0 / MG)

| UI Block Name | Byte Value | Hex | Real Hardware Behavior on Wrist |
|---|---:|---|---|
| **Tocco** (`TAP`) | `1` | `0x01` | Ultra-short micro tap (crisp click) |
| **Corto** (`SHORT`) | `14` | `0x0E` | Short, distinct buzz |
| **Normale** (`NORMAL`) | `47` | `0x2F` | Standard notification buzz (longer than 14, shorter than 15) |
| **Medio** (`MEDIUM`) | `15` | `0x0F` | Medium-strong fuller buzz |
| **Raffica** (`BURST`) | `52` | `0x34` | 4 ultra-fast micro-pulses in rapid succession |
| **Pausa** (`PAUSE`) | `152` | `0x98` | Clean silent gap (~240 ms) between adjacent effects |

### Important Hardware Quirks Discovered During Testing
1. **Adjacent effects without `152` fuse together**: e.g. `47,47,47` produces one continuous 2–3 second long vibration.
2. **To get distinct pulses, always separate them with `152`**: e.g. `47,152,47` produces two clean short buzzes with a pause in between.
3. **Not all values >= 128 act as silent pauses**: e.g. `148` triggered a long vibration instead of a pause. Stick to **`152`** for reliable gaps.
4. **`loops` (Byte 11) extends duration without adding a gap**: `47` with `loops = 0` is a single buzz; `47` with `loops = 1` is a single longer continuous buzz. Use `loops = 0` by default.

---

## 4. Ready-to-Use Rhythm Catalog

| Pattern Name | Effect Sequence (Bytes) | Human Sequence | Best Use Case |
|---|---|---|---|
| `single` | `47` | Normale | Standard notification |
| `double` | `47,152,47` | Normale → Pausa → Normale | Messages / Arrival at location |
| `triple` | `47,152,47,152,47` | Normale → Pausa → Normale → Pausa → Normale | High-priority alert |
| `long` | `47,47,47` | Normale → Normale → Normale (fusi) | Long continuous buzz (~2.5s) |
| `pulse` | `52` | Raffica (4x micro) | VIP call / Immediate attention |
| `heartbeat` | `14,152,15` | Corto → Pausa → Medio | Da-dum heartbeat rhythm |
| `escalate` | `1,152,14,152,47,152,15` | Tocco → Corto → Normale → Medio | Rising intensity alert |
| `gentle` | `1,152,1` | Tocco → Pausa → Tocco | Subtle chime / posture / breathing |
| `alarm` | `47,47,152,52` | Lungo → Pausa → Raffica | Timer expired / Wake alarm |