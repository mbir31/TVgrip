package com.example

import com.example.core.network.RemoteMessageDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Wire-level tests for the Android TV Remote v2 message decoder. They use the
 * same field numbers/wire tags as the tronikos/androidtvremote2 reference.
 */
class AndroidTvRemoteProtocolUnitTest {

    private fun varint(value: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (v and 0x7F.inv() != 0) {
            out.write(((v and 0x7F) or 0x80))
            v = v ushr 7
        }
        out.write(v and 0x7F)
        return out.toByteArray()
    }

    private fun tag(field: Int, wire: Int): ByteArray = varint((field shl 3) or wire)

    private fun lengthDelimited(field: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag(field, 2))
        out.write(varint(payload.size))
        out.write(payload)
        return out.toByteArray()
    }

    @Test
    fun `decodes remote configure code1`() {
        // RemoteMessage.remote_configure (field 1) -> RemoteConfigure.code1 = 615.
        val configurePayload = byteArrayOf(0x08) + varint(615)
        val parsed = RemoteMessageDecoder.decode(lengthDelimited(1, configurePayload))
        assertTrue(parsed.hasConfigure)
        assertEquals(615, parsed.configureCode1)
    }

    @Test
    fun `decodes remote start as nested boolean message`() {
        // RemoteMessage.remote_start (field 40) -> RemoteStart.started = true.
        val startPayload = byteArrayOf(0x08, 0x01)
        val parsed = RemoteMessageDecoder.decode(lengthDelimited(40, startPayload))
        assertTrue("remote_start must be detected", parsed.hasStart)
        assertTrue("remote_start.started must be parsed", parsed.startStarted)
    }

    @Test
    fun `decodes remote ping request`() {
        // RemoteMessage.remote_ping_request (field 8) -> RemotePingRequest.val1 = 99.
        val pingPayload = byteArrayOf(0x08) + varint(99)
        val parsed = RemoteMessageDecoder.decode(lengthDelimited(8, pingPayload))
        assertTrue(parsed.hasPingRequest)
        assertEquals(99, parsed.pingRequestVal1)
    }

    @Test
    fun `decodes remote ping response`() {
        // RemoteMessage.remote_ping_response (field 9) -> RemotePingResponse.val1 = 42.
        val pingPayload = byteArrayOf(0x08) + varint(42)
        val parsed = RemoteMessageDecoder.decode(lengthDelimited(9, pingPayload))
        assertTrue(parsed.hasPingResponse)
        assertEquals(42, parsed.pingResponseVal1)
    }

    @Test
    fun `decodes ime batch counters`() {
        // RemoteMessage.remote_ime_batch_edit (field 21); nested ime_counter=7,
        // field_counter=2.
        val imePayload = byteArrayOf(0x08, 0x07, 0x10, 0x02)
        val parsed = RemoteMessageDecoder.decode(lengthDelimited(21, imePayload))
        assertTrue(parsed.hasImeBatchEdit)
        assertEquals(7, parsed.imeCounter)
        assertEquals(2, parsed.imeFieldCounter)
    }

    @Test
    fun `ignores malformed data without throwing`() {
        val parsed = RemoteMessageDecoder.decode(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertFalse(parsed.hasConfigure)
        assertFalse(parsed.hasStart)
        assertFalse(parsed.hasPingRequest)
        assertFalse(parsed.hasPingResponse)
        assertFalse(parsed.hasImeBatchEdit)
    }
}
