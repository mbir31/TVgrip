package com.tvgrip.remote.protocol

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*

/**
 * Diagnostic tag for strict protocol tracing.
 */
private const val TAG = "TVGRIP_PAIRING"

sealed class PairingState {
    object Idle : PairingState()
    object Connecting : PairingState()
    object Handshaking : PairingState()
    object WaitingForTvPin : PairingState()
    object VerifyingPin : PairingState()
    object PairedSuccess : PairingState()
    data class Error(val message: String, val cause: Throwable? = null) : PairingState()
}

sealed class RemoteSessionState {
    object Disconnected : RemoteSessionState()
    object Connecting : RemoteSessionState()
    object Connected : RemoteSessionState()
    data class Error(val message: String) : RemoteSessionState()
}

/**
 * Security identity manager for generating and persisting self-signed X.509 KeyStore.
 */
class TvCertificateManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("tvgrip_keystore_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_KEY_CERT = "client_cert_b64"
        private const val PREF_KEY_PRIV = "client_priv_b64"
    }

    data class Identity(val keyPair: KeyPair, val certificate: X509Certificate)

    @Synchronized
    fun getOrCreateIdentity(): Identity {
        val savedCert = prefs.getString(PREF_KEY_CERT, null)
        val savedPriv = prefs.getString(PREF_KEY_PRIV, null)

        if (savedCert != null && savedPriv != null) {
            try {
                val certBytes = Base64.decode(savedCert, Base64.NO_WRAP)
                val privBytes = Base64.decode(savedPriv, Base64.NO_WRAP)

                val cf = CertificateFactory.getInstance("X.509")
                val cert = cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate

                val kf = KeyFactory.getInstance("RSA")
                val privKey = kf.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(privBytes))

                val keyPair = KeyPair(cert.publicKey, privKey)
                return Identity(keyPair, cert)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore identity, regenerating...", e)
            }
        }

        val generated = generateSelfSignedIdentity()
        val certB64 = Base64.encodeToString(generated.certificate.encoded, Base64.NO_WRAP)
        val privB64 = Base64.encodeToString(generated.keyPair.private.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putString(PREF_KEY_CERT, certB64)
            .putString(PREF_KEY_PRIV, privB64)
            .apply()

        return generated
    }

    private fun generateSelfSignedIdentity(): Identity {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048, SecureRandom())
        val keyPair = keyGen.generateKeyPair()

        // Generate X.509 v3 Self-Signed Certificate
        val cert = generateCertificateInternal(keyPair)
        return Identity(keyPair, cert)
    }

    private fun generateCertificateInternal(keyPair: KeyPair): X509Certificate {
        // Fallback standard X509 structure generation
        val now = System.currentTimeMillis()
        val startDate = Date(now - 24 * 60 * 60 * 1000L)
        val expiryDate = Date(now + (20 * 365 * 24 * 60 * 60 * 1000L)) // 20 years validity

        // Minimal ASN.1 Self-signed DER representation builder for Android environment
        // For production resilience across all Android versions, we generate a standard X509 certificate
        val name = "CN=atvremote-tvgrip, O=TVgrip, C=US"
        return X509CertHelper.createSelfSignedV3Cert(keyPair, name, startDate, expiryDate)
    }

    fun clearIdentity() {
        prefs.edit().clear().apply()
    }
}

/**
 * Android TV Remote v2 Cryptographic Pairing Session
 */
class TvPairingSession(
    private val host: String,
    private val port: Int = 6467,
    private val clientIdentity: TvCertificateManager.Identity,
    private val clientName: String = "TVgrip Remote"
) {
    private var sslSocket: SSLSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var serverCert: X509Certificate? = null

    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startPairing() {
        sessionScope.launch {
            try {
                _pairingState.value = PairingState.Connecting
                Log.d(TAG, "PairingRequest START -> Connecting to $host:$port")

                val sslContext = SSLContext.getInstance("TLSv1.2")
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    setKeyEntry("client", clientIdentity.keyPair.private, "".toCharArray(), arrayOf(clientIdentity.certificate))
                }
                kmf.init(ks, "".toCharArray())

                // TrustManager that captures the TV's peer certificate for challenge verification
                val trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        if (!chain.isNullOrEmpty()) {
                            serverCert = chain[0]
                        }
                    }
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }

                sslContext.init(kmf.keyManagers, arrayOf(trustManager), SecureRandom())
                val factory = sslContext.socketFactory

                val socket = factory.createSocket() as SSLSocket
                socket.tcpNoDelay = true
                socket.soTimeout = 15000 // 15s handshake timeout
                socket.connect(InetSocketAddress(host, port), 10000)
                socket.startHandshake()

                sslSocket = socket
                inputStream = socket.inputStream
                outputStream = socket.outputStream

                Log.d(TAG, "TLS Handshake complete with TV")
                _pairingState.value = PairingState.Handshaking

                // Step 1: Send PairingRequest
                val requestMsg = ProtocolWire.buildPairingRequest(clientName = clientName, serviceName = "com.google.android.tv.remote")
                ProtocolWire.writeDelimited(outputStream!!, requestMsg)
                Log.d(TAG, "PairingRequest SENT")

                // Step 2: Receive PairingRequestAck
                val ackMsg = ProtocolWire.readDelimited(inputStream!!)
                Log.d(TAG, "PairingRequestAck RECEIVED: status=${ackMsg.status}")
                if (ackMsg.status != 200) {
                    throw IllegalStateException("Pairing rejected at RequestAck stage: ${ackMsg.status}")
                }

                // Step 3: Send PairingOption
                val optionMsg = ProtocolWire.buildPairingOption()
                ProtocolWire.writeDelimited(outputStream!!, optionMsg)
                Log.d(TAG, "Options SENT")

                // Step 4: Receive PairingOption response from TV
                val tvOptionMsg = ProtocolWire.readDelimited(inputStream!!)
                Log.d(TAG, "Options RECEIVED: status=${tvOptionMsg.status}")

                // Step 5: Send PairingConfiguration
                val configMsg = ProtocolWire.buildPairingConfiguration()
                ProtocolWire.writeDelimited(outputStream!!, configMsg)
                Log.d(TAG, "Configuration SENT")

                // Step 6: Receive PairingConfigurationAck -> Physical TV prompts PIN now!
                val configAck = ProtocolWire.readDelimited(inputStream!!)
                Log.d(TAG, "ConfigurationAck RECEIVED: status=${configAck.status}")
                if (configAck.status != 200) {
                    throw IllegalStateException("Pairing rejected at ConfigurationAck: ${configAck.status}")
                }

                Log.d(TAG, "WAITING_FOR_TV_PIN")
                _pairingState.value = PairingState.WaitingForTvPin

            } catch (e: Exception) {
                Log.e(TAG, "Pairing failed during initialization handshake: ${e.message}", e)
                _pairingState.value = PairingState.Error("Handshake failed: ${e.localizedMessage}", e)
                close()
            }
        }
    }

    fun submitPin(pinCode: String) {
        sessionScope.launch {
            try {
                if (_pairingState.value !is PairingState.WaitingForTvPin) {
                    Log.w(TAG, "Submit PIN ignored: state is not WAITING_FOR_TV_PIN")
                    return@launch
                }

                _pairingState.value = PairingState.VerifyingPin
                Log.d(TAG, "Calculating cryptographic secret for entered PIN...")

                val clientCert = clientIdentity.certificate
                val sCert = serverCert ?: throw IllegalStateException("Server Certificate missing")

                val computedSecret = CryptoUtils.computePairingSecret(clientCert, sCert, pinCode)
                
                // Step 7: Send Secret
                val secretMsg = ProtocolWire.buildPairingSecret(computedSecret)
                ProtocolWire.writeDelimited(outputStream!!, secretMsg)
                Log.d(TAG, "Secret SENT")

                // Step 8: Receive SecretAck
                val secretAck = ProtocolWire.readDelimited(inputStream!!)
                Log.d(TAG, "SecretAck RECEIVED: status=${secretAck.status}")

                if (secretAck.status == 200) {
                    Log.d(TAG, "PAIRING SUCCESS")
                    _pairingState.value = PairingState.PairedSuccess
                } else {
                    Log.w(TAG, "Secret rejected by TV. Status: ${secretAck.status}")
                    _pairingState.value = PairingState.Error("Incorrect PIN or Secret mismatch. Code: ${secretAck.status}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying PIN: ${e.message}", e)
                _pairingState.value = PairingState.Error("PIN verification error: ${e.localizedMessage}", e)
            } finally {
                close()
            }
        }
    }

    fun close() {
        try {
            inputStream?.close()
            outputStream?.close()
            sslSocket?.close()
        } catch (ignored: Exception) {}
        sessionScope.cancel()
    }
}

/**
 * Protocol Wire serialization and Framing helpers.
 * Handles Varint32 length-delimited protobuf parsing.
 */
object ProtocolWire {

    data class ParsedMessage(
        val status: Int = 0,
        val protocolVersion: Int = 2,
        val rawPayload: ByteArray = byteArrayOf()
    )

    fun writeDelimited(out: OutputStream, bytes: ByteArray) {
        writeVarint32(out, bytes.size)
        out.write(bytes)
        out.flush()
    }

    fun readDelimited(inputStream: InputStream): ParsedMessage {
        val length = readVarint32(inputStream)
        if (length <= 0 || length > 65536) {
            throw IllegalStateException("Invalid frame length received: $length")
        }
        val buffer = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val count = inputStream.read(buffer, totalRead, length - totalRead)
            if (count < 0) throw IllegalStateException("Socket closed mid-frame")
            totalRead += count
        }
        return parseOuterMessage(buffer)
    }

    fun buildPairingRequest(clientName: String, serviceName: String): ByteArray {
        val out = ByteArrayOutputStream()
        // Status = 200 (field 1 = (1 << 3) | 0 = 0x08 -> 200 = 0xC8, 0x01)
        writeTag(out, 1, 0); writeVarint32(out, 200)
        // Protocol Version = 2 (field 2 = (2 << 3) | 0 = 0x10 -> 2)
        writeTag(out, 2, 0); writeVarint32(out, 2)
        
        // PairingRequest submessage (field 10 = (10 << 3) | 2 = 0x52)
        val sub = ByteArrayOutputStream()
        // service_name (1: string)
        writeTag(sub, 1, 2); writeString(sub, serviceName)
        // client_name (2: string)
        writeTag(sub, 2, 2); writeString(sub, clientName)
        // role (3: RoleType = ROLE_TYPE_INPUT = 1)
        writeTag(sub, 3, 0); writeVarint32(sub, 1)

        writeTag(out, 10, 2)
        writeVarint32(out, sub.size())
        out.write(sub.toByteArray())
        return out.toByteArray()
    }

    fun buildPairingOption(): ByteArray {
        val out = ByteArrayOutputStream()
        writeTag(out, 1, 0); writeVarint32(out, 200)
        writeTag(out, 2, 0); writeVarint32(out, 2)

        // PairingOption submessage (field 20 = (20 << 3) | 2 = 0xA2, 0x01)
        val sub = ByteArrayOutputStream()
        // preferred_encodings: EncodingType = ENCODING_TYPE_HEXADECIMAL (3), symbol_length = 6
        val enc = ByteArrayOutputStream()
        writeTag(enc, 1, 0); writeVarint32(enc, 3) // HEXADECIMAL
        writeTag(enc, 2, 0); writeVarint32(enc, 6)

        writeTag(sub, 3, 2)
        writeVarint32(sub, enc.size())
        sub.write(enc.toByteArray())

        writeTag(out, 20, 2)
        writeVarint32(out, sub.size())
        out.write(sub.toByteArray())
        return out.toByteArray()
    }

    fun buildPairingConfiguration(): ByteArray {
        val out = ByteArrayOutputStream()
        writeTag(out, 1, 0); writeVarint32(out, 200)
        writeTag(out, 2, 0); writeVarint32(out, 2)

        // PairingConfiguration submessage (field 30 = (30 << 3) | 2 = 0xF2, 0x01)
        val sub = ByteArrayOutputStream()
        // encoding: type=3, length=6
        val enc = ByteArrayOutputStream()
        writeTag(enc, 1, 0); writeVarint32(enc, 3)
        writeTag(enc, 2, 0); writeVarint32(enc, 6)

        writeTag(sub, 2, 2)
        writeVarint32(sub, enc.size())
        sub.write(enc.toByteArray())

        // client_role = 1
        writeTag(sub, 3, 0); writeVarint32(sub, 1)

        writeTag(out, 30, 2)
        writeVarint32(out, sub.size())
        out.write(sub.toByteArray())
        return out.toByteArray()
    }

    fun buildPairingSecret(secretBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        writeTag(out, 1, 0); writeVarint32(out, 200)
        writeTag(out, 2, 0); writeVarint32(out, 2)

        // PairingSecret submessage (field 40 = (40 << 3) | 2 = 0xC2, 0x02)
        val sub = ByteArrayOutputStream()
        writeTag(sub, 1, 0); writeVarint32(sub, 200)
        writeTag(sub, 2, 2)
        writeVarint32(sub, secretBytes.size)
        sub.write(secretBytes)

        writeTag(out, 40, 2)
        writeVarint32(out, sub.size())
        out.write(sub.toByteArray())
        return out.toByteArray()
    }

    private fun parseOuterMessage(bytes: ByteArray): ParsedMessage {
        var offset = 0
        var status = 200
        var version = 2

        while (offset < bytes.size) {
            val tagAndWire = readVarintFromBytes(bytes, offset)
            offset = tagAndWire.second
            val wireType = tagAndWire.first and 0x07
            val fieldNumber = tagAndWire.first ushr 3

            when (wireType) {
                0 -> { // Varint
                    val valuePair = readVarintFromBytes(bytes, offset)
                    offset = valuePair.second
                    if (fieldNumber == 1) status = valuePair.first
                    if (fieldNumber == 2) version = valuePair.first
                }
                2 -> { // Length delimited
                    val lenPair = readVarintFromBytes(bytes, offset)
                    offset = lenPair.second
                    val len = lenPair.first
                    offset += len
                }
                else -> break
            }
        }
        return ParsedMessage(status, version, bytes)
    }

    private fun writeTag(out: OutputStream, fieldNumber: Int, wireType: Int) {
        writeVarint32(out, (fieldNumber shl 3) or wireType)
    }

    private fun writeString(out: OutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeVarint32(out, bytes.size)
        out.write(bytes)
    }

    private fun writeVarint32(out: OutputStream, value: Int) {
        var v = value
        while ((v and 0xFFFFFF80.toInt()) != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
    }

    private fun readVarint32(inputStream: InputStream): Int {
        var result = 0
        var shift = 0
        while (shift < 32) {
            val b = inputStream.read()
            if (b == -1) throw IllegalStateException("Unexpected EOF reading Varint")
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        throw IllegalStateException("Malformed Varint")
    }

    private fun readVarintFromBytes(bytes: ByteArray, startOffset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var offset = startOffset
        while (shift < 32 && offset < bytes.size) {
            val b = bytes[offset++].toInt()
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) return Pair(result, offset)
            shift += 7
        }
        return Pair(result, offset)
    }
}

/**
 * Cryptographic helper for PIN digest hashing according to Android TV Remote v2 specs.
 */
object CryptoUtils {

    /**
     * Computes the SHA-256 Digest of:
     * (Client Certificate Modulus & Exponent Hash || Server Certificate Modulus & Exponent Hash || PIN Alpha/Hex)
     */
    fun computePairingSecret(
        clientCert: X509Certificate,
        serverCert: X509Certificate,
        pin: String
    ): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")

        // 1. Hash of client certificate subjectPublicKeyInfo
        val clientPubHash = MessageDigest.getInstance("SHA-256").digest(clientCert.publicKey.encoded)
        // 2. Hash of server certificate subjectPublicKeyInfo
        val serverPubHash = MessageDigest.getInstance("SHA-256").digest(serverCert.publicKey.encoded)

        // 3. Hex decoding of PIN string (PIN displayed on TV screen, typically 6 hex characters)
        val normalizedPin = pin.trim().uppercase()
        val pinBytes = if (normalizedPin.length % 2 == 0 && normalizedPin.matches(Regex("^[0-9A-F]+$"))) {
            hexToBytes(normalizedPin)
        } else {
            normalizedPin.toByteArray(Charsets.UTF_8)
        }

        md.update(clientPubHash)
        md.update(serverPubHash)
        md.update(pinBytes)

        return md.digest()
    }

    private fun hexToBytes(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}

/**
 * ASN.1 Structure Generator for Self-Signed X.509 v3 Certificates without external BouncyCastle dependencies.
 */
object X509CertHelper {
    fun createSelfSignedV3Cert(
        keyPair: KeyPair,
        distinguishedName: String,
        notBefore: Date,
        notAfter: Date
    ): X509Certificate {
        // Build self-signed X.509 v3 structure
        val rsaPubKey = keyPair.public as java.security.interfaces.RSAPublicKey
        val modulus = rsaPubKey.modulus
        val publicExponent = rsaPubKey.publicExponent
        val serialNumber = BigInteger(64, SecureRandom()).abs()

        val tbsCert = ByteArrayOutputStream()
        // TBS Certificate structure
        val tbs = buildTBSCertificate(serialNumber, distinguishedName, notBefore, notAfter, modulus, publicExponent)
        
        // Sign TBS with RSA SHA256
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(tbs)
        val signature = signer.sign()

        // Full X509 Certificate DER = SEQUENCE { TBS, AlgorithmIdentifier, BIT STRING signature }
        val finalCertDer = encodeSequence(
            tbs,
            encodeAlgorithmId("1.2.840.113549.1.1.11"), // sha256WithRSAEncryption
            encodeBitString(signature)
        )

        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(ByteArrayInputStream(finalCertDer)) as X509Certificate
    }

    private fun buildTBSCertificate(
        serial: BigInteger,
        dn: String,
        start: Date,
        end: Date,
        modulus: BigInteger,
        exponent: BigInteger
    ): ByteArray {
        val version = byteArrayOf(0xA0.toByte(), 0x03, 0x02, 0x01, 0x02) // v3
        val serialDer = encodeInteger(serial)
        val sigAlg = encodeAlgorithmId("1.2.840.113549.1.1.11")
        val issuer = encodeName(dn)
        val validity = encodeValidity(start, end)
        val subject = encodeName(dn)
        val spki = encodeRSAPublicKey(modulus, exponent)

        return encodeSequence(version, serialDer, sigAlg, issuer, validity, subject, spki)
    }

    private fun encodeSequence(vararg elements: ByteArray): ByteArray {
        val totalLen = elements.sumOf { it.size }
        val out = ByteArrayOutputStream()
        out.write(0x30) // SEQUENCE
        writeLength(out, totalLen)
        for (el in elements) out.write(el)
        return out.toByteArray()
    }

    private fun encodeInteger(bi: BigInteger): ByteArray {
        val bytes = bi.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(0x02) // INTEGER
        writeLength(out, bytes.size)
        out.write(bytes)
        return out.toByteArray()
    }

    private fun encodeBitString(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x03) // BIT STRING
        writeLength(out, bytes.size + 1)
        out.write(0x00) // unused bits count
        out.write(bytes)
        return out.toByteArray()
    }

    private fun encodeAlgorithmId(oid: String): ByteArray {
        val oidBytes = encodeOid(oid)
        val out = ByteArrayOutputStream()
        out.write(0x30)
        writeLength(out, oidBytes.size + 2)
        out.write(oidBytes)
        out.write(0x05); out.write(0x00) // NULL parameters
        return out.toByteArray()
    }

    private fun encodeOid(oidStr: String): ByteArray {
        val parts = oidStr.split(".").map { it.toInt() }
        val out = ByteArrayOutputStream()
        out.write(0x06)
        val content = ByteArrayOutputStream()
        content.write(parts[0] * 40 + parts[1])
        for (i in 2 until parts.size) {
            writeVarintOid(content, parts[i])
        }
        writeLength(out, content.size())
        out.write(content.toByteArray())
        return out.toByteArray()
    }

    private fun writeVarintOid(out: OutputStream, value: Int) {
        var v = value
        val stack = mutableListOf<Int>()
        stack.add(v and 0x7F)
        v = v ushr 7
        while (v > 0) {
            stack.add((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        for (i in stack.indices.reversed()) {
            out.write(stack[i])
        }
    }

    private fun encodeName(name: String): ByteArray {
        // Minimal Common Name encoding
        val cnValue = name.substringAfter("CN=").substringBefore(",")
        val cnBytes = cnValue.toByteArray(Charsets.UTF_8)
        val cnOid = encodeOid("2.5.4.3") // commonName
        val strDer = ByteArrayOutputStream().apply {
            write(0x0C) // UTF8String
            writeLength(this, cnBytes.size)
            write(cnBytes)
        }.toByteArray()

        val atvSeq = encodeSequence(cnOid, strDer)
        val set = ByteArrayOutputStream().apply {
            write(0x31) // SET
            writeLength(this, atvSeq.size)
            write(atvSeq)
        }.toByteArray()

        return encodeSequence(set)
    }

    private fun encodeValidity(start: Date, end: Date): ByteArray {
        val sdf = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val sBytes = sdf.format(start).toByteArray(Charsets.US_ASCII)
        val eBytes = sdf.format(end).toByteArray(Charsets.US_ASCII)

        val sDer = ByteArrayOutputStream().apply {
            write(0x17) // UTCTime
            writeLength(this, sBytes.size)
            write(sBytes)
        }.toByteArray()

        val eDer = ByteArrayOutputStream().apply {
            write(0x17)
            writeLength(this, eBytes.size)
            write(eBytes)
        }.toByteArray()

        return encodeSequence(sDer, eDer)
    }

    private fun encodeRSAPublicKey(modulus: BigInteger, exponent: BigInteger): ByteArray {
        val rsaSeq = encodeSequence(encodeInteger(modulus), encodeInteger(exponent))
        val bitString = encodeBitString(rsaSeq)
        val algId = encodeAlgorithmId("1.2.840.113549.1.1.1") // rsaEncryption
        return encodeSequence(algId, bitString)
    }

    private fun writeLength(out: OutputStream, len: Int) {
        if (len < 128) {
            out.write(len)
        } else if (len < 256) {
            out.write(0x81)
            out.write(len)
        } else {
            out.write(0x82)
            out.write((len ushr 8) and 0xFF)
            out.write(len and 0xFF)
        }
    }
}

/**
 * Remote Control Session (Port 6466) - Used after successful pairing.
 */
class TvRemoteSession(
    private val host: String,
    private val port: Int = 6466,
    private val clientIdentity: TvCertificateManager.Identity
) {
    private var sslSocket: SSLSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val _sessionState = MutableStateFlow<RemoteSessionState>(RemoteSessionState.Disconnected)
    val sessionState: StateFlow<RemoteSessionState> = _sessionState.asStateFlow()

    private val sessionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        sessionScope.launch {
            try {
                _sessionState.value = RemoteSessionState.Connecting
                val sslContext = SSLContext.getInstance("TLSv1.2")
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    setKeyEntry("client", clientIdentity.keyPair.private, "".toCharArray(), arrayOf(clientIdentity.certificate))
                }
                kmf.init(ks, "".toCharArray())

                val tm = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }

                sslContext.init(kmf.keyManagers, arrayOf(tm), SecureRandom())
                val socket = sslContext.socketFactory.createSocket() as SSLSocket
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), 8000)
                socket.startHandshake()

                sslSocket = socket
                inputStream = socket.inputStream
                outputStream = socket.outputStream

                Log.d(TAG, "REMOTE CONNECTED to $host:$port")
                _sessionState.value = RemoteSessionState.Connected

                // Configure Remote Session (send RemoteConfigure)
                sendConfiguration()

                // Keepalive loop & reader
                while (isActive) {
                    val msg = ProtocolWire.readDelimited(inputStream!!)
                    // Respond to ping if received
                    // RemotePingResponse
                }
            } catch (e: Exception) {
                Log.e(TAG, "Remote session error: ${e.message}", e)
                _sessionState.value = RemoteSessionState.Error("Session disconnected: ${e.localizedMessage}")
                disconnect()
            }
        }
    }

    private fun sendConfiguration() {
        // OuterMessage containing RemoteConfigure
        val out = ByteArrayOutputStream()
        // field 1 = RemoteConfigure (1: supported_device_info)
        val config = ByteArrayOutputStream()
        // Device info submessage
        val info = ByteArrayOutputStream()
        // model_name
        info.write(byteArrayOf(0x0A, 0x07)); info.write("TVgrip".toByteArray())
        
        config.write(byteArrayOf(0x0A))
        config.write(info.size())
        config.write(info.toByteArray())

        out.write(byteArrayOf(0x0A))
        out.write(config.size())
        out.write(config.toByteArray())

        ProtocolWire.writeDelimited(outputStream!!, out.toByteArray())
    }

    fun sendKey(keyCode: Int, direction: KeyDirection = KeyDirection.SHORT) {
        sessionScope.launch {
            try {
                if (outputStream == null) return@launch
                // RemoteKeyInject message: key_code = keyCode, action = direction
                val keyInject = ByteArrayOutputStream()
                // key_code (1: varint)
                keyInject.write(byteArrayOf(0x08))
                keyInject.write(keyCode)
                // direction (2: varint) (START=1, END=2, SHORT=3)
                keyInject.write(byteArrayOf(0x10))
                keyInject.write(direction.value)

                val outer = ByteArrayOutputStream()
                outer.write(byteArrayOf(0x12)) // field 2: RemoteKeyInject
                outer.write(keyInject.size())
                outer.write(keyInject.toByteArray())

                ProtocolWire.writeDelimited(outputStream!!, outer.toByteArray())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send keycode $keyCode", e)
            }
        }
    }

    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            sslSocket?.close()
        } catch (ignored: Exception) {}
        sessionScope.cancel()
        _sessionState.value = RemoteSessionState.Disconnected
    }
}

enum class KeyDirection(val value: Int) {
    START(1),
    END(2),
    SHORT(3)
}

/**
 * Standard Android TV Remote Keycodes
 */
object TvKeys {
    const val KEYCODE_DPAD_UP = 19
    const val KEYCODE_DPAD_DOWN = 20
    const val KEYCODE_DPAD_LEFT = 21
    const val KEYCODE_DPAD_RIGHT = 22
    const val KEYCODE_DPAD_CENTER = 23
    const val KEYCODE_BACK = 4
    const val KEYCODE_HOME = 3
    const val KEYCODE_VOLUME_UP = 24
    const val KEYCODE_VOLUME_DOWN = 25
    const val KEYCODE_VOLUME_MUTE = 164
    const val KEYCODE_POWER = 26
}
