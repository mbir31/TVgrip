package com.example.core.network

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Protocol Buffers wire definition for Android TV Remote v2 Polo Pairing Protocol.
 * 
 * Schema reference (polo.proto):
 * 
 * enum RoleType { ROLE_TYPE_UNKNOWN = 0; ROLE_TYPE_INPUT = 1; ROLE_TYPE_OUTPUT = 2; }
 * enum EncodingType { ENCODING_TYPE_UNKNOWN = 0; ENCODING_TYPE_ALPHANUMERIC = 1; ENCODING_TYPE_NUMERIC = 2; ENCODING_TYPE_HEXADECIMAL = 3; ENCODING_TYPE_QRCODE = 4; }
 * 
 * message Encoding {
 *     optional EncodingType type = 1;
 *     optional uint32 symbol_length = 2;
 * }
 * 
 * message Options {
 *     repeated Encoding input_encodings = 1;
 *     repeated Encoding output_encodings = 2;
 *     optional RoleType preferred_role = 3;
 * }
 * 
 * message PairingRequest {
 *     optional string service_name = 1;
 *     optional string client_name = 2;
 * }
 * 
 * message PairingRequestAck {
 *     optional string server_name = 1;
 * }
 * 
 * message Configuration {
 *     optional Encoding encoding = 1;
 *     optional RoleType client_role = 2;
 * }
 * 
 * message ConfigurationAck {}
 * 
 * message Secret {
 *     optional bytes secret = 1;
 * }
 * 
 * message SecretAck {
 *     optional bytes secret = 1;
 * }
 * 
 * message OuterMessage {
 *     enum Status { STATUS_OK = 200; STATUS_ERROR = 400; STATUS_BAD_CONFIGURATION = 401; STATUS_BAD_SECRET = 402; }
 *     optional uint32 protocol_version = 1 [default = 2];
 *     optional Status status = 2 [default = STATUS_OK];
 *     optional PairingRequest pairing_request = 10;
 *     optional PairingRequestAck pairing_request_ack = 11;
 *     optional Options options = 20;
 *     optional Configuration configuration = 30;
 *     optional ConfigurationAck configuration_ack = 31;
 *     optional Secret secret = 40;
 *     optional SecretAck secret_ack = 41;
 * }
 */
object PoloProtocol {

    enum class Status(val value: Int) {
        STATUS_OK(200),
        STATUS_ERROR(400),
        STATUS_BAD_CONFIGURATION(401),
        STATUS_BAD_SECRET(402);

        companion object {
            fun fromValue(v: Int): Status = entries.firstOrNull { it.value == v } ?: STATUS_ERROR
        }
    }

    enum class RoleType(val value: Int) {
        ROLE_TYPE_UNKNOWN(0),
        ROLE_TYPE_INPUT(1),
        ROLE_TYPE_OUTPUT(2);

        companion object {
            fun fromValue(v: Int): RoleType = entries.firstOrNull { it.value == v } ?: ROLE_TYPE_UNKNOWN
        }
    }

    enum class EncodingType(val value: Int) {
        ENCODING_TYPE_UNKNOWN(0),
        ENCODING_TYPE_ALPHANUMERIC(1),
        ENCODING_TYPE_NUMERIC(2),
        ENCODING_TYPE_HEXADECIMAL(3),
        ENCODING_TYPE_QRCODE(4);

        companion object {
            fun fromValue(v: Int): EncodingType = entries.firstOrNull { it.value == v } ?: ENCODING_TYPE_UNKNOWN
        }
    }

    data class Encoding(
        val type: EncodingType = EncodingType.ENCODING_TYPE_HEXADECIMAL,
        val symbolLength: Int = 6
    ) {
        fun encode(out: OutputStream) {
            ProtobufWriter.writeVarintField(out, 1, type.value.toLong())
            ProtobufWriter.writeVarintField(out, 2, symbolLength.toLong())
        }

        companion object {
            fun decode(bytes: ByteArray): Encoding {
                var type = EncodingType.ENCODING_TYPE_UNKNOWN
                var symbolLength = 6
                val input = ByteArrayInputStream(bytes)
                val reader = ProtobufReader(input)
                while (reader.hasNextTag()) {
                    val (field, wire) = reader.readTag()
                    when (field) {
                        1 -> type = EncodingType.fromValue(reader.readVarint().toInt())
                        2 -> symbolLength = reader.readVarint().toInt()
                        else -> reader.skipField(wire)
                    }
                }
                return Encoding(type, symbolLength)
            }
        }
    }

    data class Options(
        val preferredRole: RoleType = RoleType.ROLE_TYPE_INPUT,
        val inputEncodings: List<Encoding> = listOf(Encoding(EncodingType.ENCODING_TYPE_HEXADECIMAL, 6)),
        val outputEncodings: List<Encoding> = emptyList()
    ) {
        fun encode(out: OutputStream) {
            inputEncodings.forEach { enc ->
                val encBytes = ByteArrayOutputStream().apply { enc.encode(this) }.toByteArray()
                ProtobufWriter.writeLengthDelimitedField(out, 1, encBytes)
            }
            outputEncodings.forEach { enc ->
                val encBytes = ByteArrayOutputStream().apply { enc.encode(this) }.toByteArray()
                ProtobufWriter.writeLengthDelimitedField(out, 2, encBytes)
            }
            ProtobufWriter.writeVarintField(out, 3, preferredRole.value.toLong())
        }

        companion object {
            fun decode(bytes: ByteArray): Options {
                var role = RoleType.ROLE_TYPE_UNKNOWN
                val inEncodings = mutableListOf<Encoding>()
                val outEncodings = mutableListOf<Encoding>()
                val input = ByteArrayInputStream(bytes)
                val reader = ProtobufReader(input)
                while (reader.hasNextTag()) {
                    val (field, wire) = reader.readTag()
                    when (field) {
                        1 -> inEncodings.add(Encoding.decode(reader.readLengthDelimited()))
                        2 -> outEncodings.add(Encoding.decode(reader.readLengthDelimited()))
                        3 -> role = RoleType.fromValue(reader.readVarint().toInt())
                        else -> reader.skipField(wire)
                    }
                }
                return Options(role, inEncodings, outEncodings)
            }
        }
    }

    data class PairingRequest(
        val serviceName: String = "atvremote",
        val clientName: String = "TVGrip"
    ) {
        fun encode(out: OutputStream) {
            ProtobufWriter.writeStringField(out, 1, serviceName)
            ProtobufWriter.writeStringField(out, 2, clientName)
        }

        companion object {
            fun decode(bytes: ByteArray): PairingRequest {
                var sName = ""
                var cName = ""
                val input = ByteArrayInputStream(bytes)
                val reader = ProtobufReader(input)
                while (reader.hasNextTag()) {
                    val (field, wire) = reader.readTag()
                    when (field) {
                        1 -> sName = reader.readString()
                        2 -> cName = reader.readString()
                        else -> reader.skipField(wire)
                    }
                }
                return PairingRequest(sName, cName)
            }
        }
    }

    data class PairingRequestAck(
        val serverName: String = ""
    ) {
        fun encode(out: OutputStream) {
            if (serverName.isNotEmpty()) {
                ProtobufWriter.writeStringField(out, 1, serverName)
            }
        }

        companion object {
            fun decode(bytes: ByteArray): PairingRequestAck {
                var sName = ""
                val input = ByteArrayInputStream(bytes)
                val reader = ProtobufReader(input)
                while (reader.hasNextTag()) {
                    val (field, wire) = reader.readTag()
                    when (field) {
                        1 -> sName = reader.readString()
                        else -> reader.skipField(wire)
                    }
                }
                return PairingRequestAck(sName)
            }
        }
    }

    data class Configuration(
        val encoding: Encoding = Encoding(EncodingType.ENCODING_TYPE_HEXADECIMAL, 6),
        val clientRole: RoleType = RoleType.ROLE_TYPE_INPUT
    ) {
        fun encode(out: OutputStream) {
            val encBytes = ByteArrayOutputStream().apply { encoding.encode(this) }.toByteArray()
            ProtobufWriter.writeLengthDelimitedField(out, 1, encBytes)
            ProtobufWriter.writeVarintField(out, 2, clientRole.value.toLong())
        }

        companion object {
            fun decode(bytes: ByteArray): Configuration {
                var enc = Encoding()
                var role = RoleType.ROLE_TYPE_UNKNOWN
                val input = ByteArrayInputStream(bytes)
                val reader = ProtobufReader(input)
                while (reader.hasNextTag()) {
                    val (field, wire) = reader.readTag()
                    when (field) {
                        1 -> enc = Encoding.decode(reader.readLengthDelimited())
                        2 -> role = RoleType.fromValue(reader.readVarint().toInt())
                        else -> reader.skipField(wire)
                    }
                }
                return Configuration(enc, role)
            }
        }
    }

    class ConfigurationAck {
        fun encode(out: OutputStream) {
            // ConfigurationAck is empty message
        }

        companion object {
            fun decode(bytes: ByteArray): ConfigurationAck = ConfigurationAck()
        }
    }

    data class Secret(
        val secret: ByteArray
    ) {
        fun encode(out: OutputStream) {
            ProtobufWriter.writeLengthDelimitedField(out, 1, secret)
        }

        companion object {
            fun decode(bytes: ByteArray): Secret {
                var sec = ByteArray(0)
                val input = ByteArrayInputStream(bytes)
                val reader = ProtobufReader(input)
                while (reader.hasNextTag()) {
                    val (field, wire) = reader.readTag()
                    when (field) {
                        1 -> sec = reader.readLengthDelimited()
                        else -> reader.skipField(wire)
                    }
                }
                return Secret(sec)
            }
        }
    }

    data class SecretAck(
        val secret: ByteArray = ByteArray(0)
    ) {
        fun encode(out: OutputStream) {
            if (secret.isNotEmpty()) {
                ProtobufWriter.writeLengthDelimitedField(out, 1, secret)
            }
        }

        companion object {
            fun decode(bytes: ByteArray): SecretAck {
                var sec = ByteArray(0)
                val input = ByteArrayInputStream(bytes)
                val reader = ProtobufReader(input)
                while (reader.hasNextTag()) {
                    val (field, wire) = reader.readTag()
                    when (field) {
                        1 -> sec = reader.readLengthDelimited()
                        else -> reader.skipField(wire)
                    }
                }
                return SecretAck(sec)
            }
        }
    }

    data class OuterMessage(
        val protocolVersion: Int = 2,
        val status: Status = Status.STATUS_OK,
        val pairingRequest: PairingRequest? = null,
        val pairingRequestAck: PairingRequestAck? = null,
        val options: Options? = null,
        val configuration: Configuration? = null,
        val configurationAck: ConfigurationAck? = null,
        val secret: Secret? = null,
        val secretAck: SecretAck? = null
    ) {
        fun encode(): ByteArray {
            val body = ByteArrayOutputStream()
            ProtobufWriter.writeVarintField(body, 1, protocolVersion.toLong())
            ProtobufWriter.writeVarintField(body, 2, status.value.toLong())

            pairingRequest?.let {
                val bytes = ByteArrayOutputStream().apply { it.encode(this) }.toByteArray()
                ProtobufWriter.writeLengthDelimitedField(body, 10, bytes)
            }
            pairingRequestAck?.let {
                val bytes = ByteArrayOutputStream().apply { it.encode(this) }.toByteArray()
                ProtobufWriter.writeLengthDelimitedField(body, 11, bytes)
            }
            options?.let {
                val bytes = ByteArrayOutputStream().apply { it.encode(this) }.toByteArray()
                ProtobufWriter.writeLengthDelimitedField(body, 20, bytes)
            }
            configuration?.let {
                val bytes = ByteArrayOutputStream().apply { it.encode(this) }.toByteArray()
                ProtobufWriter.writeLengthDelimitedField(body, 30, bytes)
            }
            configurationAck?.let {
                val bytes = ByteArrayOutputStream().apply { it.encode(this) }.toByteArray()
                ProtobufWriter.writeLengthDelimitedField(body, 31, bytes)
            }
            secret?.let {
                val bytes = ByteArrayOutputStream().apply { it.encode(this) }.toByteArray()
                ProtobufWriter.writeLengthDelimitedField(body, 40, bytes)
            }
            secretAck?.let {
                val bytes = ByteArrayOutputStream().apply { it.encode(this) }.toByteArray()
                ProtobufWriter.writeLengthDelimitedField(body, 41, bytes)
            }

            val rawBytes = body.toByteArray()
            val framed = ByteArrayOutputStream()
            ProtobufWriter.writeVarint(framed, rawBytes.size.toLong())
            framed.write(rawBytes)
            return framed.toByteArray()
        }

        companion object {
            fun decode(bytes: ByteArray): OuterMessage {
                var version = 2
                var status = Status.STATUS_OK
                var pReq: PairingRequest? = null
                var pReqAck: PairingRequestAck? = null
                var opts: Options? = null
                var config: Configuration? = null
                var configAck: ConfigurationAck? = null
                var sec: Secret? = null
                var secAck: SecretAck? = null

                val input = ByteArrayInputStream(bytes)
                val reader = ProtobufReader(input)
                while (reader.hasNextTag()) {
                    val (field, wire) = reader.readTag()
                    when (field) {
                        1 -> version = reader.readVarint().toInt()
                        2 -> status = Status.fromValue(reader.readVarint().toInt())
                        10 -> pReq = PairingRequest.decode(reader.readLengthDelimited())
                        11 -> pReqAck = PairingRequestAck.decode(reader.readLengthDelimited())
                        20 -> opts = Options.decode(reader.readLengthDelimited())
                        30 -> config = Configuration.decode(reader.readLengthDelimited())
                        31 -> configAck = ConfigurationAck.decode(reader.readLengthDelimited())
                        40 -> sec = Secret.decode(reader.readLengthDelimited())
                        41 -> secAck = SecretAck.decode(reader.readLengthDelimited())
                        else -> reader.skipField(wire)
                    }
                }
                return OuterMessage(
                    protocolVersion = version,
                    status = status,
                    pairingRequest = pReq,
                    pairingRequestAck = pReqAck,
                    options = opts,
                    configuration = config,
                    configurationAck = configAck,
                    secret = sec,
                    secretAck = secAck
                )
            }
        }
    }

    object ProtobufWriter {
        fun writeVarint(out: OutputStream, value: Long) {
            var v = value
            while (v and 0x7FL.inv() != 0L) {
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
            out.write((v and 0x7F).toInt())
        }

        fun writeTag(out: OutputStream, fieldNumber: Int, wireType: Int) {
            writeVarint(out, ((fieldNumber shl 3) or wireType).toLong())
        }

        fun writeVarintField(out: OutputStream, fieldNumber: Int, value: Long) {
            writeTag(out, fieldNumber, 0)
            writeVarint(out, value)
        }

        fun writeLengthDelimitedField(out: OutputStream, fieldNumber: Int, bytes: ByteArray) {
            writeTag(out, fieldNumber, 2)
            writeVarint(out, bytes.size.toLong())
            out.write(bytes)
        }

        fun writeStringField(out: OutputStream, fieldNumber: Int, str: String) {
            writeLengthDelimitedField(out, fieldNumber, str.toByteArray(Charsets.UTF_8))
        }
    }

    class ProtobufReader(private val input: InputStream) {
        fun hasNextTag(): Boolean = input.available() > 0

        fun readTag(): Pair<Int, Int> {
            val tag = readVarint().toInt()
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07
            return Pair(fieldNumber, wireType)
        }

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = input.read()
                if (b == -1) throw EOFException("End of stream reading varint")
                result = result or ((b.toLong() and 0x7FL) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
                if (shift > 64) throw IllegalArgumentException("Varint too long")
            }
            return result
        }

        fun readLengthDelimited(): ByteArray {
            val len = readVarint().toInt()
            val buffer = ByteArray(len)
            var totalRead = 0
            while (totalRead < len) {
                val read = input.read(buffer, totalRead, len - totalRead)
                if (read == -1) throw EOFException("Unexpected EOF reading length-delimited payload ($totalRead/$len)")
                totalRead += read
            }
            return buffer
        }

        fun readString(): String = String(readLengthDelimited(), Charsets.UTF_8)

        fun skipField(wireType: Int) {
            when (wireType) {
                0 -> readVarint()
                1 -> { // 64-bit
                    val b = ByteArray(8)
                    input.read(b)
                }
                2 -> { // Length delimited
                    val len = readVarint().toInt()
                    val b = ByteArray(len)
                    input.read(b)
                }
                5 -> { // 32-bit
                    val b = ByteArray(4)
                    input.read(b)
                }
                else -> {}
            }
        }
    }

    fun readFramedMessage(input: InputStream): OuterMessage {
        var length = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b == -1) throw EOFException("End of stream reading OuterMessage length prefix")
            length = length or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift > 28) throw IllegalArgumentException("Varint message length exceeds maximum limit")
        }

        val buffer = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(buffer, totalRead, length - totalRead)
            if (read == -1) throw EOFException("End of stream reading OuterMessage payload of size $length")
            totalRead += read
        }
        return OuterMessage.decode(buffer)
    }
}
