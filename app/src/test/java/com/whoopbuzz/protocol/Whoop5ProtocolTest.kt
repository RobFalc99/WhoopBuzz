package com.whoopbuzz.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class Whoop5ProtocolTest {
    @Test
    fun helloMatchesKnownClientHello() {
        assertEquals("aa0108000001e67123019101363e5c8d", Whoop5Protocol.hello(seq = 1).toHex())
    }

    @Test
    fun patternCMatchesOriginalDoubleBuzzPayload() {
        val p = Whoop5Protocol.buzzPayload("C", 1)
        assertEquals(12, p.size)
        assertEquals("012f98000000000000000001", p.toHex())
    }

    @Test
    fun patternAIsSingleEffect() {
        assertEquals("012f00000000000000000001", Whoop5Protocol.buzzPayload("A", 1).toHex())
    }

    @Test
    fun unknownPatternFallsBackToDefaultAndLoopsAreClamped() {
        assertEquals(Whoop5Protocol.buzzPayload("A", 1).toHex(), Whoop5Protocol.buzzPayload("zz", 1).toHex())
        assertEquals(255, Whoop5Protocol.buzzPayload("A", 999).last().toInt() and 0xFF)
        assertEquals(1, Whoop5Protocol.buzzPayload("A", 0).last().toInt())
    }

    @Test
    fun buzzFrameIsConsistent() {
        val f = Whoop5Protocol.buzz("A", 1, seq = 2)
        val declLen = (f[2].toInt() and 0xFF) or ((f[3].toInt() and 0xFF) shl 8)
        assertEquals(declLen + 8, f.size)
        assertEquals(0x23, f[8].toInt())
        assertEquals(0x13, f[10].toInt() and 0xFF)
    }
}