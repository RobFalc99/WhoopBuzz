package com.whoopbuzz.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class Whoop5ProtocolTest {
    @Test
    fun helloMatchesKnownClientHello() {
        assertEquals("aa0108000001e67123019101363e5c8d", Whoop5Protocol.hello(seq = 1).toHex())
    }

    @Test
    fun legacyPatternCMatchesOriginalPayload() {
        assertEquals("012f98000000000000000001", Whoop5Protocol.buzzPayload("C", 1).toHex())
    }

    @Test
    fun parseEffectsHandlesSeparatorsAndGarbage() {
        assertEquals(listOf(47, 152, 47), Whoop5Protocol.parseEffects("47,152, 47"))
        assertEquals(listOf(1, 2), Whoop5Protocol.parseEffects("1;x;2"))
        assertEquals(8, Whoop5Protocol.parseEffects("1,2,3,4,5,6,7,8,9,10").size)
        assertEquals(255, Whoop5Protocol.parseEffects("999").single())
    }

    @Test
    fun resolveNamesAndFallback() {
        assertEquals(listOf(47), Whoop5Protocol.resolve("SINGLE"))
        assertEquals(listOf(47, 152, 47), Whoop5Protocol.resolve("double"))
        assertEquals(listOf(47, 152, 47, 152, 47), Whoop5Protocol.resolve("triple"))
        assertEquals(listOf(47, 47, 47), Whoop5Protocol.resolve("long"))
        assertEquals(listOf(52), Whoop5Protocol.resolve("pulse"))
        assertEquals(listOf(47), Whoop5Protocol.resolve("nonsense"))
        assertEquals(listOf(14, 15), Whoop5Protocol.resolve("14,15"))
    }

    @Test
    fun hapticStepFormattingIsHumanReadable() {
        assertEquals("Normale → Pausa → Normale", HapticStep.formatSteps("47,152,47", 0))
        assertEquals("Raffica  •  Durata estesa", HapticStep.formatSteps("52", 1))
    }

    @Test
    fun patternStoreCodecRoundTripAndUpsert() {
        val initial = PatternStoreCodec.decode(null)
        assertEquals(5, initial.size)
        val updated = PatternStoreCodec.upsert(initial, SavedPattern("vip", "52, 152, 47", 1))
        assertEquals(6, updated.size)
        val replaced = PatternStoreCodec.upsert(updated, SavedPattern("VIP", "52,152,52", 2))
        assertEquals(6, replaced.size)
        val encoded = PatternStoreCodec.encode(replaced)
        val decoded = PatternStoreCodec.decode(encoded)
        assertEquals(replaced, decoded)
        val (eff, loops) = PatternStoreCodec.resolveRequest(decoded, "vip", null)
        assertEquals("52,152,52", eff)
        assertEquals(2, loops)
    }
}