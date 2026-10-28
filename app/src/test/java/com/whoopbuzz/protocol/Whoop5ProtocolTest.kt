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
    fun loopsZeroAllowedAndClamped() {
        assertEquals(0, Whoop5Protocol.buzzPayload("single", 0).last().toInt())
        assertEquals(255, Whoop5Protocol.buzzPayload("single", 999).last().toInt() and 0xFF)
        assertEquals(12, Whoop5Protocol.buzzPayload("single", 0).size)
    }

    @Test
    fun buzzFrameIsConsistent() {
        val f = Whoop5Protocol.buzz("single", 0, seq = 2)
        val declLen = (f[2].toInt() and 0xFF) or ((f[3].toInt() and 0xFF) shl 8)
        assertEquals(declLen + 8, f.size)
        assertEquals(0x13, f[10].toInt() and 0xFF)
    }
}