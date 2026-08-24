package com.example.core.network

import android.content.Context
import android.util.Log
import com.example.core.model.TvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed class PairingResult {
    data class CodePromptReceived(val promptMessage: String) : PairingResult()
    data class Success(val message: String = "Pairing successful") : PairingResult()
    data class Failed(val error: String) : PairingResult()
}

/**
 * Robust, production-grade Android TV Remote v2 Pairing Service (Polo Protocol).
 * 
 * Accurately implements the Google TV / Android TV Remote v2 Pairing State Machine:
 * 1. Mutual TLS Handshake on port 6467 with persistent client X.509 certificate.
 * 2. Send PairingRequest (service_name="androidtvremote", client_name="TVGrip").
 * 3. Receive PairingRequestAck / PairingOption from TV.
 * 4. Send PairingConfiguration (ENCODING_HEXADECIMAL / ENCODING_NUMERIC, symbol_length=6, role=ROLE_INPUT).
 * 5. Receive PairingConfigurationAck from TV -> TV displays 6-digit challenge code on screen.
 * 6. Compute SHA-256 Secret: Hash(ClientCert.DER + ServerCert.DER + CodeBytes).
 * 7. Send PairingSecret and receive PairingSecretAck (status 200 = Success).
 */
class TvPairingService(private val context: Context) {

    private val TAG = "TvPairingService"
    private var pairingSocket: SSLSocket? = null
    private var pairingOutput: OutputStream? = null
    private var pairingInput: InputStream? = null
    private var clientCert: X509Certificate? = null
    private var serverCert: X509Certificate? = null
    private var negotiatedEncodingType: Int = 3 // 3 = HEXADECIMAL, 2 = NUMERIC, 1 = ALPHANUMERIC

    companion object {
        const val STATUS_OK = 200
        const val STATUS_ERROR = 400
        const val STATUS_BAD_CONFIGURATION = 401
        const val STATUS_BAD_SECRET = 402

        const val ENCODING_TYPE_ALPHANUMERIC = 1
        const val ENCODING_TYPE_NUMERIC = 2
        const val ENCODING_TYPE_HEXADECIMAL = 3

        const val ROLE_TYPE_INPUT = 1
        const val ROLE_TYPE_OUTPUT = 2
    }

    /**
     * Initiates the pairing handshake on port 6467.
     * Completes steps 1-5, causing the TV to display the pairing code on its screen.
     */
    suspend fun startPairing(device: TvDevice): PairingResult {
        return withContext(Dispatchers.IO) {
            try {
                disconnect()
                val pairingPort = if (device.port == 6466 || device.port == 0) 6467 else device.port
                Log.d(TAG, "Starting Android TV Remote v2 pairing handshake to ${device.host}:$pairingPort")

                val keyManagerFactory = SslCertificateManager.getOrCreateKeyManagerFactory(context)
                clientCert = SslCertificateManager.getClientCertificate(context)
                if (clientCert == null) {
                    return@withContext PairingResult.Failed("Failed to initialize client TLS certificate.")
                }

                val sslContext = SSLContext.getInstance("TLS")
                var capturedServerCert: X509Certificate? = null

                val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {
                        if (certs.isNotEmpty()) {
                            capturedServerCert = certs[0]
                        }
                    }
                })
                sslContext.init(keyManagerFactory.keyManagers, trustAll, SecureRandom())

                val rawSocket = Socket()
                rawSocket.connect(InetSocketAddress(device.host, pairingPort), 8000)
                rawSocket.tcpNoDelay = true
                rawSocket.soTimeout = 12000

                val sslSocket = sslContext.socketFactory.createSocket(
                    rawSocket,
                    device.host,
                    pairingPort,
                    true
                ) as SSLSocket
                sslSocket.startHandshake()

                // Extract peer certificate if not already set by trust manager
                if (capturedServerCert == null) {
                    val peerCerts = sslSocket.session.peerCertificates
                    if (peerCerts.isNotEmpty() && peerCerts[0] is X509Certificate) {
                        capturedServerCert = peerCerts[0] as X509Certificate
                    }
                }
                serverCert = capturedServerCert

                pairingSocket = sslSocket
                val out = sslSocket.getOutputStream()
                val input = sslSocket.getInputStream()
                pairingOutput = out
                pairingInput = input

                Log.d(TAG, "TLS handshake successful. Client Cert: ${clientCert?.subjectDN}, Server Cert: ${serverCert?.subjectDN}")

                // Step 1: Send PairingRequest
                Log.d(TAG, "Step 1: Sending PairingRequest packet...")
                sendPairingRequest(out, clientName = "TVGrip", serviceName = "androidtvremote")

                // Step 2: Read TV's PairingResponse (PairingRequestAck & PairingOption)
                Log.d(TAG, "Step 2: Awaiting PairingResponse from TV...")
                val responseMsg = readAndParseMessage(input)
                Log.d(TAG, "Received message from TV: status=${responseMsg.status}, hasReqAck=${responseMsg.hasPairingRequestAck}, hasOptions=${responseMsg.hasPairingOption}, preferredEncoding=${responseMsg.preferredEncodingType}")

                if (responseMsg.status != STATUS_OK && responseMsg.status != 1) {
                    disconnect()
                    return@withContext PairingResult.Failed("TV rejected pairing request (status ${responseMsg.status}).")
                }

                negotiatedEncodingType = responseMsg.preferredEncodingType

                // Step 3: Send PairingConfiguration
                Log.d(TAG, "Step 3: Sending PairingConfiguration (encoding=$negotiatedEncodingType, length=6)...")
                sendPairingConfiguration(out, encodingType = negotiatedEncodingType, symbolLength = 6)

                // Step 4: Read TV's ConfigurationAck
                Log.d(TAG, "Step 4: Awaiting ConfigurationAck from TV...")
                val configAck = readAndParseMessage(input)
                Log.d(TAG, "Received ConfigurationAck from TV: status=${configAck.status}, hasConfigAck=${configAck.hasPairingConfigurationAck}")

                if (configAck.status != STATUS_OK && configAck.status != 1) {
                    disconnect()
                    return@withContext PairingResult.Failed("TV rejected pairing configuration (status ${configAck.status}).")
                }

                Log.d(TAG, "Pairing challenge triggered successfully! TV is displaying pairing code on screen.")
                PairingResult.CodePromptReceived("Enter the pairing code displayed on your TV screen")
            } catch (e: Exception) {
                Log.e(TAG, "Pairing initiation failed with exception: ${e.message}", e)
                disconnect()
                PairingResult.Failed("Could not trigger pairing code on TV: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    /**
     * Submits the user-entered PIN to complete authentication with the TV.
     */
    suspend fun confirmPairingCode(code: String): PairingResult {
        return withContext(Dispatchers.IO) {
            try {
                val cleanCode = code.trim().uppercase()
                val out = pairingOutput
                val input = pairingInput
                val cCert = clientCert
                val sCert = serverCert

                if (out == null || input == null || cCert == null || sCert == null) {
                    disconnect()
                    return@withContext PairingResult.Failed("Pairing session expired or not connected. Please restart pairing.")
                }

                Log.d(TAG, "Computing SHA-256 Secret for code '$cleanCode' (Encoding=$negotiatedEncodingType)...")

                // Determine secret payload bytes based on negotiated encoding
                val secretBytes: ByteArray = if (negotiatedEncodingType == ENCODING_TYPE_HEXADECIMAL) {
                    parseHexOrUtf8(cleanCode)
                } else {
                    cleanCode.toByteArray(Charsets.UTF_8)
                }

                // Compute Secret SHA-256 Digest: SHA256(ClientCert.DER + ServerCert.DER + SecretBytes)
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(cCert.encoded)
                digest.update(sCert.encoded)
                digest.update(secretBytes)
                val hashBytes = digest.digest()

                Log.d(TAG, "Step 5: Sending PairingSecret (${hashBytes.size} bytes)...")
                sendPairingSecret(out, hashBytes)

                Log.d(TAG, "Step 6: Awaiting PairingSecretAck from TV...")
                val secretAck = readAndParseMessage(input)
                Log.d(TAG, "Received SecretAck from TV: status=${secretAck.status}, hasSecretAck=${secretAck.hasPairingSecretAck}")

                if (secretAck.status == STATUS_OK || secretAck.status == 1 || secretAck.hasPairingSecretAck) {
                    Log.d(TAG, "TV pairing completed and verified successfully!")
                    disconnect()
                    PairingResult.Success("TV successfully paired and authenticated!")
                } else {
                    Log.w(TAG, "TV rejected pairing secret with status ${secretAck.status}")
                    disconnect()
                    PairingResult.Failed("Pairing code incorrect or expired (Status ${secretAck.status}). Please try again.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pairing secret verification failed: ${e.message}", e)
                disconnect()
                PairingResult.Failed("Failed to confirm pairing code: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    private fun parseHexOrUtf8(code: String): ByteArray {
        return try {
            val hexClean = code.filter { it in "0123456789ABCDEFabcdef" }
            if (hexClean.length % 2 == 0 && hexClean.isNotEmpty()) {
                hexClean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } else {
                code.toByteArray(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            code.toByteArray(Charsets.UTF_8)
        }
    }

    /**
     * Constructs and sends PairingRequest in standard Protobuf format.
     */
    private fun sendPairingRequest(out: OutputStream, clientName: String, serviceName: String) {
        val reqInner = ByteArrayOutputStream()
        writeStringField(reqInner, 1, serviceName) // service_name
        writeStringField(reqInner, 2, clientName)  // client_name
        val reqBytes = reqInner.toByteArray()

        val msg = ByteArrayOutputStream()
        writeVarintField(msg, 1, 2) // protocol_version = 2
        writeVarintField(msg, 2, STATUS_OK.toLong()) // status = 200
        writeLengthDelimitedField(msg, 10, reqBytes) // pairing_request (field 10)
        val msgBytes = msg.toByteArray()

        val packet = ByteArrayOutputStream()
        writeVarint(packet, msgBytes.size.toLong())
        packet.write(msgBytes)

        out.write(packet.toByteArray())
        out.flush()
    }

    /**
     * Constructs and sends PairingConfiguration in standard Protobuf format.
     */
    private fun sendPairingConfiguration(out: OutputStream, encodingType: Int, symbolLength: Int) {
        val encInner = ByteArrayOutputStream()
        writeVarintField(encInner, 1, encodingType.toLong()) // type
        writeVarintField(encInner, 2, symbolLength.toLong()) // symbol_length
        val encBytes = encInner.toByteArray()

        val configInner = ByteArrayOutputStream()
        writeLengthDelimitedField(configInner, 1, encBytes) // encoding
        writeVarintField(configInner, 2, ROLE_TYPE_INPUT.toLong()) // client_role = ROLE_TYPE_INPUT (1)
        val configBytes = configInner.toByteArray()

        val msg = ByteArrayOutputStream()
        writeVarintField(msg, 1, 2) // protocol_version = 2
        writeVarintField(msg, 2, STATUS_OK.toLong()) // status = 200
        writeLengthDelimitedField(msg, 30, configBytes) // pairing_configuration (field 30)
        val msgBytes = msg.toByteArray()

        val packet = ByteArrayOutputStream()
        writeVarint(packet, msgBytes.size.toLong())
        packet.write(msgBytes)

        out.write(packet.toByteArray())
        out.flush()
    }

    /**
     * Constructs and sends PairingSecret in standard Protobuf format.
     */
    private fun sendPairingSecret(out: OutputStream, secretHash: ByteArray) {
        val secretInner = ByteArrayOutputStream()
        writeLengthDelimitedField(secretInner, 1, secretHash) // secret bytes
        val secretBytes = secretInner.toByteArray()

        val msg = ByteArrayOutputStream()
        writeVarintField(msg, 1, 2) // protocol_version = 2
        writeVarintField(msg, 2, STATUS_OK.toLong()) // status = 200
        writeLengthDelimitedField(msg, 40, secretBytes) // pairing_secret (field 40)
        val msgBytes = msg.toByteArray()

        val packet = ByteArrayOutputStream()
        writeVarint(packet, msgBytes.size.toLong())
        packet.write(msgBytes)

        out.write(packet.toByteArray())
        out.flush()
    }

    /**
     * Reads a varint-framed message from the stream and parses its fields.
     */
    private fun readAndParseMessage(input: InputStream): ParsedPairingMessage {
        val payload = readFramedPacket(input)
        return parseProtobufPairingMessage(payload)
    }

    private fun readFramedPacket(input: InputStream): ByteArray {
        var length = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b == -1) throw EOFException("End of stream reading length prefix")
            length = length or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift > 28) throw IllegalArgumentException("Varint message length too large")
        }

        val buffer = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(buffer, totalRead, length - totalRead)
            if (read == -1) throw EOFException("End of stream reading payload of size $length")
            totalRead += read
        }
        return buffer
    }

    private fun parseProtobufPairingMessage(data: ByteArray): ParsedPairingMessage {
        var version = 2
        var status = STATUS_OK
        var hasReqAck = false
        var hasOption = false
        var preferredEncoding = ENCODING_TYPE_HEXADECIMAL
        var hasConfigAck = false
        var hasSecretAck = false

        var index = 0
        while (index < data.size) {
            var tag = 0
            var shift = 0
            while (index < data.size) {
                val b = data[index++].toInt() and 0xFF
                tag = tag or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07

            when (wireType) {
                0 -> { // Varint
                    var value = 0L
                    shift = 0
                    while (index < data.size) {
                        val b = data[index++].toLong() and 0xFFL
                        value = value or ((b and 0x7FL) shl shift)
                        if ((b and 0x80L) == 0L) break
                        shift += 7
                    }
                    if (fieldNumber == 1) version = value.toInt()
                    if (fieldNumber == 2) status = value.toInt()
                }
                2 -> { // Length Delimited
                    var len = 0
                    shift = 0
                    while (index < data.size) {
                        val b = data[index++].toInt() and 0xFF
                        len = len or ((b and 0x7F) shl shift)
                        if ((b and 0x80) == 0) break
                        shift += 7
                    }
                    val subBytes = if (index + len <= data.size) {
                        data.copyOfRange(index, index + len)
                    } else ByteArray(0)
                    index += len

                    when (fieldNumber) {
                        11 -> hasReqAck = true
                        20 -> {
                            hasOption = true
                            preferredEncoding = extractEncodingFromOptions(subBytes)
                        }
                        31 -> hasConfigAck = true
                        41 -> hasSecretAck = true
                    }
                }
                1 -> index += 8
                5 -> index += 4
                else -> break
            }
        }

        return ParsedPairingMessage(
            protocolVersion = version,
            status = status,
            hasPairingRequestAck = hasReqAck,
            hasPairingOption = hasOption,
            preferredEncodingType = preferredEncoding,
            hasPairingConfigurationAck = hasConfigAck,
            hasPairingSecretAck = hasSecretAck
        )
    }

    private fun extractEncodingFromOptions(data: ByteArray): Int {
        var foundEncoding = ENCODING_TYPE_HEXADECIMAL
        var index = 0
        while (index < data.size) {
            val b = data[index++].toInt() and 0xFF
            val fieldNumber = b ushr 3
            val wireType = b and 0x07
            if (wireType == 2) {
                var len = 0
                var shift = 0
                while (index < data.size) {
                    val lb = data[index++].toInt() and 0xFF
                    len = len or ((lb and 0x7F) shl shift)
                    if ((lb and 0x80) == 0) break
                    shift += 7
                }
                val sub = if (index + len <= data.size) data.copyOfRange(index, index + len) else ByteArray(0)
                index += len
                if (sub.isNotEmpty() && sub[0] == 0x08.toByte() && sub.size >= 2) {
                    val encType = sub[1].toInt()
                    if (encType in 1..3) {
                        foundEncoding = encType
                    }
                }
            } else if (wireType == 0) {
                while (index < data.size && (data[index++].toInt() and 0x80) != 0) {}
            } else {
                break
            }
        }
        return foundEncoding
    }

    // --- Protobuf serialization utilities ---

    private fun writeVarint(out: OutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    private fun writeTag(out: OutputStream, fieldNumber: Int, wireType: Int) {
        writeVarint(out, ((fieldNumber shl 3) or wireType).toLong())
    }

    private fun writeVarintField(out: OutputStream, fieldNumber: Int, value: Long) {
        writeTag(out, fieldNumber, 0)
        writeVarint(out, value)
    }

    private fun writeLengthDelimitedField(out: OutputStream, fieldNumber: Int, bytes: ByteArray) {
        writeTag(out, fieldNumber, 2)
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeStringField(out: OutputStream, fieldNumber: Int, str: String) {
        writeLengthDelimitedField(out, fieldNumber, str.toByteArray(Charsets.UTF_8))
    }

    fun disconnect() {
        try {
            pairingOutput?.close()
            pairingInput?.close()
            pairingSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Socket cleanup error: ${e.message}")
        } finally {
            pairingOutput = null
            pairingInput = null
            pairingSocket = null
            serverCert = null
            clientCert = null
        }
    }
}

private data class ParsedPairingMessage(
    val protocolVersion: Int = 2,
    val status: Int = 200,
    val hasPairingRequestAck: Boolean = false,
    val hasPairingOption: Boolean = false,
    val preferredEncodingType: Int = 3,
    val hasPairingConfigurationAck: Boolean = false,
    val hasPairingSecretAck: Boolean = false
)
