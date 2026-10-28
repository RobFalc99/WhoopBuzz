package com.whoopbuzz.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class Whoop5ProtocolTest {
    @Test
    fun helloMatchesKnownClientHello() {
        // Static CLIENT_HELLO captured from a real WHOOP 5.0 (community RE).
        assertEquals("aa0108000001e67123019101363e5c8d", Whoop5Protocol.hello(seq = 1).toHex())
    }

    @Test
    fun buzzPayloadIs12Bytes() {
        val p = Whoop5Protocol.buzzPayload(1)
        assertEquals(12, p.size)
        assertEquals("012f98000000000000000001", p.toHex())
    }

    @Test
    fun buzzFrameIsConsistent() {
        val f = Whoop5Protocol.buzz(loops = 1, seq = 2)
        val declLen = (f[2].toInt() and 0xFF) or ((f[3].toInt() and 0xFF) shl 8)
        assertEquals(declLen + 8, f.size)
        assertEquals(0x23, f[8].toInt())
        assertEquals(0x13, f[10].toInt() and 0xFF)
    }
}