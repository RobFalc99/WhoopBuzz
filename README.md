# WhoopBuzz

Control the haptic motor of a **WHOOP 5.0 / MG** strap from Android:
- **Visual Rhythm Studio**: create, test, and save custom vibration patterns using human-readable blocks (`Normale`, `Pausa`, `Raffica`, `Medio`, `Corto`, `Tocco`).
- **Built-in Wrist Tools**:
  1. **Wrist Timer with Intermittent Alarm & Snooze** (vibrates repeatedly until stopped or snoozed +5m).
  2. **Pomodoro & Periodic Chime** (25/5m Focus/Break cycles + subtle wrist chime every N minutes).
  3. **Box Breathing Pacer (4-4-4-4)** (eyes-closed guided breathing on the wrist).
- **Automation Bridge**: trigger any rhythm from **MacroDroid**, **BuzzKill**, **Tasker**, or **any other Android app** via `com.whoopbuzz.BUZZ`.

> **Developer & AI Agent Reference**: See [`docs/WHOOP_HAPTIC_REFERENCE.md`](docs/WHOOP_HAPTIC_REFERENCE.md) for the complete BLE wire protocol, verified effect/pause table, rhythm catalog, and copy-paste integration code for other projects.

## Install
Open https://robfalc99.github.io/WhoopBuzz/ on the phone (or scan the QR in `docs/qr.png`) and install the APK.

## Broadcast API (MacroDroid / Tasker / Other Apps)
- **Action**: `com.whoopbuzz.BUZZ`
- **Package**: `com.whoopbuzz`
- **Extra `pattern`**: `single`, `double`, `triple`, `long`, `pulse`, `heartbeat`, `escalate`, `gentle`, `alarm`, or any custom name saved in the app.
- **Extra `token`**: optional (only required if you set a non-empty token in the app).