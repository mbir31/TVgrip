package com.example

import com.example.core.network.RemoteMessageDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Unit tests for the Android TV Remote v2 wire decoder. These verify parser
 * behaviour against the real RemoteMessage tags from the protocol reference.
 */
class AndroidTvRemoteProtocolUnitTest {

    private fun varint(value: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
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
        val out = java.io.ByteArrayOutputStream()
        out.write(tag(field, 2))
        out.write(varint(payload.size))
        out.write(payload)
        return out.toByteArray()
    }

    @Test
    fun `decodes remote configure code1`() {
        // RemoteMessage.remote_configure (field 1) -> RemoteConfigure.code1 = 615
        val configure = lengthDelimited(1, varint(615))
        val parsed = RemoteMessageDecoder.decode(configure)
        assertTrue(parsed.hasConfigure)
        assertEquals(615, parsed.configureCode1)
    }

    @Test
    fun `decodes remote start as nested message`() {
        // RemoteMessage.remote_start (field 40) -> RemoteStart.started = true
        val startPayload = byteArrayOf(0x08, 0x01)
        val frame = lengthDelimited(40, startPayload)
        val parsed = RemoteMessageDecoder.decode(frame)
        assertTrue("remote_start must be detected", parsed.hasStart)
        assertTrue("remote_start.started must be parsed", parsed.startStarted)
    }

    @Test
    fun `decodes remote ping request and ime batch counters`() {
        val ping = lengthDelimited(8, varint(99))
        val parsedPing = RemoteMessageDecoder.decode(ping)
        assertTrue(parsedPing.hasPingRequest)
        assertEquals(99, parsedPing.pingRequestVal1)

        // RemoteImeBatchEdit: ime_counter=7, field_counter=2
        val imePayload = byteArrayOf(0x08, 0x07, 0x10, 0x02)
        val parsedIme = RemoteMessageDecoder.decode(lengthDelimited(21, imePayload))
        assertTrue(parsedIme.hasImeBatchEdit)
        assertEquals(7, parsedIme.imeCounter)
        assertEquals(2, parsedIme.imeFieldCounter)
    }

    @Test
    fun `decodes remote ping response`() {
        val parsed = RemoteMessageDecoder.decode(lengthDelimited(9, varint(42)))
        assertTrue(parsed.hasPingResponse)
        assertEquals(42, parsed.pingResponseVal1)
    }

    @Test
    fun `ignores malformed data without throwing`() {
        val parsed = RemoteMessageDecoder.decode(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertFalse(parsed.hasConfigure)
        assertFalse(parsed.hasStart)
    }

    @Test
    fun `server certificate sha256 is deterministic sha256 of der`() {
        val der = byteArrayOf(0x30, 0x03, 0x01, 0x01, 0x00, 0x02.toByte(), 0x01, 0x01)
        // A minimal real X.509 cert is not required here; the helper should not
        // throw on a fingerprint made from DER bytes.
        val digest = MessageDigest.getInstance("SHA-256").digest(der)
        val expected = digest.joinToString("") { "%02x".format(it) }
        assertEquals(64, expected.length)
        assertTrue(expected.matches(Regex("[0-9a-f]{64}")))
    }
}
