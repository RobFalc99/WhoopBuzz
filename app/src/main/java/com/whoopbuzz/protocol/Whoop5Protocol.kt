package com.whoopbuzz.protocol

import java.util.zip.CRC32

/**
 * WHOOP 5.0 / MG ("puffin") wire protocol: frame builder + haptic payload.
 * Byte layouts come from community reverse engineering (noop / goose); see docs/.
 * Pure Kotlin (no Android types) so it is unit-testable on the JVM.
 */
object Whoop5Protocol {
    const val SERVICE_UUID = "fd4b0001-cce1-4033-93ce-002d5875f58a"
    const val COMMAND_CHAR_UUID = "fd4b0002-cce1-4033-93ce-002d5875f58a"
    val NOTIFY_CHAR_UUIDS = listOf(
        "fd4b0003-cce1-4033-93ce-002d5875f58a",
        "fd4b0004-cce1-4033-93ce-002d5875f58a",
        "fd4b0005-cce1-4033-93ce-002d5875f58a",
        "fd4b0007-cce1-4033-93ce-002d5875f58a",
    )

    const val TYPE_COMMAND = 0x23
    const val CMD_GET_HELLO = 0x91
    /** RUN_HAPTIC_PATTERN_MAVERICK: one-shot buzz on a 5.0/MG strap. */
    const val CMD_RUN_HAPTIC_MAVERICK = 0x13
    /** STOP_HAPTICS. */
    const val CMD_STOP_HAPTICS = 122

    /** Named haptic patterns (effect ids). A/B are single effects, C is the original two-pulse pair. */
    val PATTERNS: Map<String, List<Int>> = mapOf(
        "A" to listOf(47),
        "B" to listOf(152),
        "C" to listOf(47, 152),
    )
    const val DEFAULT_PATTERN = "A"

    /** Payload of CMD_RUN_HAPTIC_MAVERICK: [rev=1][8 effects][loopControl u16=0][overallLoop]. */
    fun buzzPayload(effects: List<Int>, loops: Int): ByteArray {
        val eff = ByteArray(8) { i -> (effects.getOrElse(i) { 0 } and 0xFF).toByte() }
        return byteArrayOf(0x01) + eff + byteArrayOf(0x00, 0x00, loops.coerceIn(1, 255).toByte())
    }

    fun buzzPayload(pattern: String, loops: Int): ByteArray =
        buzzPayload(PATTERNS[pattern.uppercase()] ?: PATTERNS.getValue(DEFAULT_PATTERN), loops)
    fun crc16Modbus(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0xFFFF
        for (i in from until to) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 == 1) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc and 0xFFFF
    }

    fun crc32(data: ByteArray): Long = CRC32().also { it.update(data) }.value

    /**
     * Build a command frame:
     * `AA 01 declLen(u16 LE) 00 01 crc16(u16 LE) | [0x23 seq cmd params..] zero-padded to x4 | crc32`.
     * declLen = padded body size + 4 (CRC32 trailer).
     */
    fun command(cmd: Int, seq: Int, params: ByteArray = ByteArray(0)): ByteArray {
        var body = byteArrayOf(TYPE_COMMAND.toByte(), seq.toByte(), cmd.toByte()) + params
        val pad = (4 - body.size % 4) % 4
        body += ByteArray(pad)
        val declLen = body.size + 4
        val header = byteArrayOf(
            0xAA.toByte(), 0x01,
            (declLen and 0xFF).toByte(), ((declLen shr 8) and 0xFF).toByte(),
            0x00, 0x01,
        )
        val h16 = crc16Modbus(header)
        val c32 = crc32(body)
        return header +
            byteArrayOf((h16 and 0xFF).toByte(), ((h16 shr 8) and 0xFF).toByte()) +
            body +
            byteArrayOf(
                (c32 and 0xFF).toByte(), ((c32 shr 8) and 0xFF).toByte(),
                ((c32 shr 16) and 0xFF).toByte(), ((c32 shr 24) and 0xFF).toByte(),
            )
    }

    /** Session opener: GET_HELLO with param 0x01 (identical to the known static CLIENT_HELLO). */
    fun hello(seq: Int = 1): ByteArray = command(CMD_GET_HELLO, seq, byteArrayOf(0x01))

    fun buzz(pattern: String = DEFAULT_PATTERN, loops: Int = 1, seq: Int = 2): ByteArray =
        command(CMD_RUN_HAPTIC_MAVERICK, seq, buzzPayload(pattern, loops))
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }