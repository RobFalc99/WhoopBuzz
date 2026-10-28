# WhoopBuzz

Vibrate a WHOOP 5.0 / MG strap from Android automations (Buzzkill -> MacroDroid -> WhoopBuzz).
Unofficial, not affiliated with WHOOP. Uses the community reverse-engineered BLE protocol (see docs/PROTOCOL_NOTES.md).

## Install
Open https://robfalc99.github.io/WhoopBuzz/ on the phone (or scan the QR) and install the APK.

## Setup
1. The strap must be bonded with the phone (the official WHOOP app can stay connected).
2. Open WhoopBuzz, set a **token**, tap **Avvia servizio**, and exclude the app from battery optimization.
3. Try **Buzz A / B / C** to pick the pattern you like (A and B are single pulses, C is the original double).

## Broadcast API
Action `com.whoopbuzz.BUZZ`, package `com.whoopbuzz`.

| Extra | Type | Notes |
|-------|------|-------|
| `token` | String | required, must equal the token set in the app |
| `pattern` | String | `A` (default), `B`, `C` |
| `loops` | Int | 1-5 |

Test from a PC:

    adb shell am broadcast -a com.whoopbuzz.BUZZ -p com.whoopbuzz --es token YOUR_TOKEN --es pattern A

### MacroDroid
Action **Send Intent** -> Target: *Broadcast*, Action `com.whoopbuzz.BUZZ`, Package `com.whoopbuzz`,
extras `token=YOUR_TOKEN`, `pattern=A`. In Buzzkill use the action that runs a MacroDroid macro.