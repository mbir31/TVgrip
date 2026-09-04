package com.example

import com.example.core.network.RemoteMessageDecoder
import com.example.core.network.RemoteMessageEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteMessageEncoderUnitTest {

    @Test
    fun `ime batch edit encodes counters for ascii text`() {
        val body = RemoteMessageEncoder.imeBatchEditMessage("Netflix", imeCounter = 3, imeFieldCounter = 7)
        val decoded = RemoteMessageDecoder.decode(body)
        assertTrue(decoded.hasImeBatchEdit)
        assertEquals(3, decoded.imeCounter)
        assertEquals(7, decoded.imeFieldCounter)
    }

    @Test
    fun `ime batch edit keeps unicode text in utf8`() {
        val text = "হ্যালো مرحبا こんにちは"
        val body = RemoteMessageEncoder.imeBatchEditMessage(text, imeCounter = 1, imeFieldCounter = 0)
        assertTrue(containsText(body, text))
        val decoded = RemoteMessageDecoder.decode(body)
        assertTrue(decoded.hasImeBatchEdit)
    }

    @Test
    fun `ime batch edit uses length minus one for start and end`() {
        val text = "abc"
        val body = RemoteMessageEncoder.imeBatchEditMessage(text, imeCounter = 0, imeFieldCounter = 0)
        // cursor = 2 appears twice as varint(2); both start and end are field 1/2.
        assertTrue(containsSubsequence(body, byteArrayOf(0x08, 0x02)))
        assertTrue(containsSubsequence(body, byteArrayOf(0x10, 0x02)))
    }

    private fun containsSubsequence(body: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > body.size) return false
        outer@ for (start in 0..body.size - needle.size) {
            for (i in needle.indices) {
                if (body[start + i] != needle[i]) continue@outer
            }
            return true
        }
        return false
    }

    private fun containsText(body: ByteArray, text: String): Boolean {
        val needle = text.toByteArray(Charsets.UTF_8)
        if (needle.isEmpty() || needle.size > body.size) return false
        outer@ for (start in 0..body.size - needle.size) {
            for (i in needle.indices) {
                if (body[start + i] != needle[i]) continue@outer
            }
            return true
        }
        return false
    }
}
