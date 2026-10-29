package com.whoopbuzz.protocol

import java.util.zip.CRC32

/** Human-readable building blocks for composing haptic sequences without raw numbers. */
enum class HapticStep(val label: String, val code: Int) {
    NORMAL("Normale", 47),
    PAUSE("Pausa", 152),
    BURST("Raffica", 52),
    MEDIUM("Medio", 15),
    SHORT("Corto", 14),
    TAP("Tocco", 1);

    companion object {
        fun labelFor(code: Int): String =
            entries.firstOrNull { it.code == code }?.label ?: if (code >= 128) "Pausa" else "Impulso"

        fun formatSteps(effectsSpec: String, loops: Int = 0): String {
            val steps = Whoop5Protocol.parseEffects(effectsSpec).map { labelFor(it) }
            val base = if (steps.isEmpty()) "—" else steps.joinToString(" → ")
            return if (loops > 0) "$base  •  Durata estesa" else base
        }
    }
}

/** User-defined or preset haptic pattern persisted by name. */
data class SavedPattern(
    val name: String,
    val effects: String,
    val loops: Int = 0,
)

/**
 * Pure JVM codec + resolver for user-saved patterns (`name|effects|loops` per line).
 */
object PatternStoreCodec {
    val DEFAULT_PATTERNS: List<SavedPattern> = listOf(
        SavedPattern("single", "47", 0),
        SavedPattern("double", "47,152,47", 0),
        SavedPattern("triple", "47,152,47,152,47", 0),
        SavedPattern("long", "47,47,47", 0),
        SavedPattern("pulse", "52", 0),
    )

    fun encode(list: List<SavedPattern>): String =
        list.joinToString("\n") { p ->
            val safeName = p.name.replace("|", "").replace("\n", "").trim()
            val safeEff = Whoop5Protocol.parseEffects(p.effects).joinToString(",")
            "$safeName|$safeEff|${p.loops.coerceIn(0, 5)}"
        }

    fun decode(raw: String?): List<SavedPattern> {
        if (raw.isNullOrBlank()) return DEFAULT_PATTERNS
        val parsed = raw.lines().mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 2) return@mapNotNull null
            val name = parts[0].trim()
            val eff = Whoop5Protocol.parseEffects(parts[1]).joinToString(",")
            val loops = parts.getOrNull(2)?.trim()?.toIntOrNull()?.coerceIn(0, 5) ?: 0
            if (name.isEmpty() || eff.isEmpty()) null else SavedPattern(name, eff, loops)
        }
        return parsed.ifEmpty { DEFAULT_PATTERNS }
    }

    fun upsert(list: List<SavedPattern>, item: SavedPattern): List<SavedPattern> {
        val cleanName = item.name.replace("|", "").trim()
        val cleanEff = Whoop5Protocol.parseEffects(item.effects).joinToString(",")
        if (cleanName.isEmpty() || cleanEff.isEmpty()) return list
        val normalized = SavedPattern(cleanName, cleanEff, item.loops.coerceIn(0, 5))
        val idx = list.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
        return if (idx >= 0) list.toMutableList().also { it[idx] = normalized } else list + normalized
    }

    fun delete(list: List<SavedPattern>, name: String): List<SavedPattern> =
        list.filterNot { it.name.equals(name.trim(), ignoreCase = true) }

    fun resolveRequest(
        saved: List<SavedPattern>,
        spec: String,
        explicitLoops: Int? = null,
    ): Pair<String, Int> {
        val trimmed = spec.trim()
        val match = saved.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        return if (match != null) {
            match.effects to (explicitLoops ?: match.loops).coerceIn(0, 5)
        } else {
            trimmed to (explicitLoops ?: 0).coerceIn(0, 5)
        }
    }
}

/**
 * WHOOP 5.0 / MG ("puffin") wire protocol: frame builder + haptic payload.
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
    const val CMD_RUN_HAPTIC_MAVERICK = 0x13
    const val CMD_STOP_HAPTICS = 122

    const val VERIFIED_PAUSE = 152

    val PATTERNS: Map<String, List<Int>> = mapOf(
        "single" to listOf(47),
        "double" to listOf(47, VERIFIED_PAUSE, 47),
        "triple" to listOf(47, VERIFIED_PAUSE, 47, VERIFIED_PAUSE, 47),
        "long" to listOf(47, 47, 47),
        "pulse" to listOf(52),
        "A" to listOf(47),
        "B" to listOf(152),
        "C" to listOf(47, 152),
    )
    const val DEFAULT_PATTERN = "single"

    fun parseEffects(spec: String): List<Int> =
        spec.split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }.map { it.coerceIn(0, 255) }.take(8)

    fun resolve(spec: String): List<Int> {
        val named = PATTERNS.entries.firstOrNull { it.key.equals(spec.trim(), ignoreCase = true) }?.value
        return named ?: parseEffects(spec).ifEmpty { PATTERNS.getValue(DEFAULT_PATTERN) }
    }

    fun buzzPayload(effects: List<Int>, loops: Int): ByteArray {
        val eff = ByteArray(8) { i -> (effects.getOrElse(i) { 0 } and 0xFF).toByte() }
        return byteArrayOf(0x01) + eff + byteArrayOf(0x00, 0x00, loops.coerceIn(0, 255).toByte())
    }

    fun buzzPayload(spec: String, loops: Int): ByteArray = buzzPayload(resolve(spec), loops)

    fun crc16Modbus(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0xFFFF
        for (i in from until to) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 == 1) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc and 0xFFFF
    }

    fun crc32(data: ByteArray): Long = CRC32().also { it.update(data) }.value

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

    fun hello(seq: Int = 1): ByteArray = command(CMD_GET_HELLO, seq, byteArrayOf(0x01))

    fun buzz(pattern: String = DEFAULT_PATTERN, loops: Int = 0, seq: Int = 2): ByteArray =
        command(CMD_RUN_HAPTIC_MAVERICK, seq, buzzPayload(pattern, loops))
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }