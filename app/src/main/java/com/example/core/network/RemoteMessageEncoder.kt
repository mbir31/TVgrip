package com.example.core.network

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Builds Android TV Remote v2 RemoteMessage bodies in a small, testable helper.
 *
 * The returned byte arrays are message bodies (without the varint length
 * prefix); the protocol layer writes that length prefix when it sends the
 * frame. This keeps the framing helper independent of the wire encoders used
 * elsewhere in the app.
 */
object RemoteMessageEncoder {

    /**
     * remote_ime_batch_edit (field 21) with a single insert edit containing the
     * supplied text. Matches tronikos/androidtvremote2.remote.send_text:
     * start/end = text.length - 1, insert = 1, and the current IME counters.
     */
    fun imeBatchEditMessage(text: String, imeCounter: Int, imeFieldCounter: Int): ByteArray {
        val cursor = text.length - 1
        val imeObject = ByteArrayOutputStream().apply {
            writeVarintField(this, 1, cursor.toLong()) // start
            writeVarintField(this, 2, cursor.toLong()) // end
            writeStringField(this, 3, text)            // value
        }.toByteArray()
        val editInfo = ByteArrayOutputStream().apply {
            writeVarintField(this, 1, 1L)                    // insert
            writeLengthDelimitedField(this, 2, imeObject)    // text_field_status
        }.toByteArray()
        val batch = ByteArrayOutputStream().apply {
            writeVarintField(this, 1, imeCounter.toLong())       // ime_counter
            writeVarintField(this, 2, imeFieldCounter.toLong())  // field_counter
            writeLengthDelimitedField(this, 3, editInfo)         // edit_info
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            writeLengthDelimitedField(this, 21, batch) // remote_ime_batch_edit
        }.toByteArray()
    }

    private fun writeVarint(out: OutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    private fun writeVarintField(out: OutputStream, fieldNumber: Int, value: Long) {
        writeVarint(out, ((fieldNumber shl 3) or 0).toLong())
        writeVarint(out, value)
    }

    private fun writeLengthDelimitedField(out: OutputStream, fieldNumber: Int, bytes: ByteArray) {
        writeVarint(out, ((fieldNumber shl 3) or 2).toLong())
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeStringField(out: OutputStream, fieldNumber: Int, value: String) {
        writeLengthDelimitedField(out, fieldNumber, value.toByteArray(Charsets.UTF_8))
    }
}
