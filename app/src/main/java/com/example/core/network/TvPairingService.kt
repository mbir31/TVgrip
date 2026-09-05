package com.example.core.network

import android.content.Context
import android.util.Log
import com.example.core.model.TvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

sealed class PairingResult {
    data class CodePromptReceived(val promptMessage: String) : PairingResult()
    data class Success(
        val message: String = "Pairing successful",
        val serverCertSha256: String? = null
    ) : PairingResult()
    data class Failed(val error: String) : PairingResult()
}

/**
 * Returns the lowercase SHA-256 of the DER-encoded peer certificate. Used to pin
 * the Android TV server certificate so a later remote session can verify it is
 * the same TV that was paired.
 */
fun serverCertificateSha256(cert: X509Certificate): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(cert.encoded)
        .joinToString("") { "%02x".format(it) }
}

/**
 * Proven Android TV Remote v2 Pairing Service (Polo Protocol), rewired to follow
 * the reliable state-machine architecture of tronikos/androidtvremote2 while
 * keeping TVGrip's Kotlin/Compose structure.
 *
 * Strict message sequence (device always drives the response; the socket stays
 * open between start and finish):
 *
 *   1.  TLS (mutual, client cert CN=TVGrip) connect on port 6467
 *   2.  OUT  PairingRequest (service_name="atvremote", client_name="TVGrip")
 *   3.  IN   PairingRequestAck
 *   4.  OUT  Options (preferred_role=INPUT, input_encodings=[HEXADECIMAL, 6])
 *   5.  IN   Options
 *   6.  OUT  Configuration (encoding=HEXADECIMAL/6, client_role=INPUT)
 *   7.  IN   ConfigurationAck  -> TV displays the 6-digit hex PIN
 *   8.  (UI shows the prompt; user types the PIN)
 *   9.  OUT  Secret (SHA-256 over client/server modulus+exponent + PIN remainder)
 *   10. IN   SecretAck        -> PAIRING_SUCCESS
 *
 * Key robustness fixes vs the previous implementation:
 *  - The client TLS identity is guaranteed initialized (via SslCertificateManager)
 *    BEFORE any pairing command is written, so a not-initialized TLS context can
 *    never reach the wire.
 *  - A single owned [PairingSession] is the source of truth for the socket and
 *    the captured server cert; concurrent/duplicate calls are serialized and
 *    stale sessions are torn down via an attempt counter.
 *  - Every read/write is bounded by a socket timeout and the whole handshake has
 *    an overall timeout, so pairing can never hang the UI indefinitely.
 */
class TvPairingService(private val context: Context) {

    private val TAG = "TvPairingService"

    private val pairingPort = 6467
    private val connectTimeoutMs = 8000
    private val handshakeReadTimeoutMs = 15000
    private val totalHandshakeTimeoutMs = 45000L

    // Guards the owned session so startPairing / confirmPairingCode / disconnect
    // can never interleave on a half-initialized or duplicate session.
    private val sessionLock = Any()
    @Volatile
    private var session: PairingSession? = null

    // Monotonic attempt id; any in-flight flow whose id no longer matches bails out,
    // which prevents a stale pairing handshake from corrupting a newer one.
    private val attemptCounter = AtomicInteger(0)

    // ----------------------------------------------------------------- lifecycle

    /**
     * Ensures the client TLS identity exists and is usable. Safe to call before
     * [startPairing]; [startPairing] also calls it internally. Returns true when a
     * correctly initialized pairing/connection session is ready, so callers can
     * guarantee an initialized session BEFORE any pairing command is sent.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val ok = SslCertificateManager.ensureInitialized(context)
            val ready = ok && SslCertificateManager.getClientCertificate(context) != null
            if (ready) Log.d(TAG, "Pairing identity initialized (client cert ready).")
            ready
        } catch (e: Exception) {
            Log.e(TAG, "Pairing initialization failed: ${e.message}", e)
            false
        }
    }

    suspend fun startPairing(device: TvDevice): PairingResult = withContext(Dispatchers.IO) {
        val myAttempt = attemptCounter.incrementAndGet()
        synchronized(sessionLock) {
            session?.closeQuietly()
            session = null
        }

        try {
            // 1) GUARANTEE a correctly initialized TLS identity BEFORE any command.
            if (!SslCertificateManager.ensureInitialized(context) ||
                SslCertificateManager.getClientCertificate(context) == null
            ) {
                return@withContext PairingResult.Failed(
                    "Pairing session is not initialized: the client TLS certificate could not be prepared. " +
                        "Reopen TVGrip and try again."
                )
            }

            val host = device.host
            if (host.isBlank()) {
                return@withContext PairingResult.Failed("The discovered TV has no network address.")
            }

            // 2) Build the pairing TLS context (mutual TLS; captures TV cert).
            val sslContext: SSLContext = SslCertificateManager.buildPairingSslContext(context)

            withTimeout(totalHandshakeTimeoutMs) {
                // 3) Connect + TLS handshake.
                val rawSocket = Socket()
                try {
                    Log.d(TAG, "CONNECTING_PAIRING to $host:$pairingPort")
                    rawSocket.connect(InetSocketAddress(host, pairingPort), connectTimeoutMs)
                } catch (e: Exception) {
                    Log.e(TAG, "Connection to $host:$pairingPort failed: ${e.message}")
                    return@withTimeout PairingResult.Failed(
                        "Could not connect to the TV pairing service on port 6467 (${e.localizedMessage ?: e.message}).\n\n" +
                            "Troubleshooting:\n" +
                            "• Confirm the TV is on and on the same Wi-Fi network.\n" +
                            "• Disable Client/AP Isolation on your router."
                    )
                }
                rawSocket.tcpNoDelay = true
                rawSocket.soTimeout = handshakeReadTimeoutMs

                val sslSocket = sslContext.socketFactory.createSocket(
                    rawSocket,
                    host,
                    pairingPort,
                    true
                ) as SSLSocket
                sslSocket.soTimeout = handshakeReadTimeoutMs
                sslSocket.startHandshake()

                // Invalidate if a newer attempt started while we were connecting.
                if (myAttempt != attemptCounter.get()) {
                    sslSocket.closeQuietly()
                    return@withTimeout PairingResult.Failed("Pairing was superseded by a newer request.")
                }

                val serverCert = SslCertificateManager.capturedServerCert
                if (serverCert == null) {
                    sslSocket.closeQuietly()
                    return@withTimeout PairingResult.Failed(
                        "The TV did not present a TLS certificate during pairing."
                    )
                }
                val serverCertSha = serverCertificateSha256(serverCert)
                Log.d(TAG, "TLS_CONNECTED on port $pairingPort. Server fingerprint: $serverCertSha")

                val output: OutputStream = sslSocket.outputStream
                val input: InputStream = sslSocket.inputStream

                // 4) PairingRequest
                Log.d(TAG, "[OUT] PairingRequest: service_name=atvremote, client_name=TVGrip")
                output.write(
                    PoloProtocol.OuterMessage(
                        protocolVersion = 2,
                        status = PoloProtocol.Status.STATUS_OK,
                        pairingRequest = PoloProtocol.PairingRequest(
                            serviceName = "atvremote",
                            clientName = "TVGrip"
                        )
                    ).encode()
                )
                output.flush()

                // 5) PairingRequestAck
                val ackMsg = PoloProtocol.readFramedMessage(input)
                Log.d(TAG, "[IN] PairingRequestAck: status=${ackMsg.status}, hasAck=${ackMsg.pairingRequestAck != null}")
                if (ackMsg.status != PoloProtocol.Status.STATUS_OK && ackMsg.pairingRequestAck == null) {
                    sslSocket.closeQuietly()
                    return@withTimeout PairingResult.Failed("The TV rejected the pairing request (status ${ackMsg.status}).")
                }

                // 6) Options (client -> server), then read TV Options.
                Log.d(TAG, "[OUT] Options: preferred_role=ROLE_TYPE_INPUT, input_encodings=[HEXADECIMAL, 6]")
                output.write(
                    PoloProtocol.OuterMessage(
                        protocolVersion = 2,
                        status = PoloProtocol.Status.STATUS_OK,
                        options = PoloProtocol.Options(
                            preferredRole = PoloProtocol.RoleType.ROLE_TYPE_INPUT,
                            inputEncodings = listOf(
                                PoloProtocol.Encoding(
                                    type = PoloProtocol.EncodingType.ENCODING_TYPE_HEXADECIMAL,
                                    symbolLength = 6
                                )
                            )
                        )
                    ).encode()
                )
                output.flush()

                val tvOptionsMsg = PoloProtocol.readFramedMessage(input)
                Log.d(TAG, "[IN] Options: status=${tvOptionsMsg.status}, hasOptions=${tvOptionsMsg.options != null}")

                // 7) Configuration (client -> server), then read ConfigurationAck.
                Log.d(TAG, "[OUT] Configuration: encoding=HEXADECIMAL/6, client_role=ROLE_TYPE_INPUT")
                output.write(
                    PoloProtocol.OuterMessage(
                        protocolVersion = 2,
                        status = PoloProtocol.Status.STATUS_OK,
                        configuration = PoloProtocol.Configuration(
                            encoding = PoloProtocol.Encoding(
                                type = PoloProtocol.EncodingType.ENCODING_TYPE_HEXADECIMAL,
                                symbolLength = 6
                            ),
                            clientRole = PoloProtocol.RoleType.ROLE_TYPE_INPUT
                        )
                    ).encode()
                )
                output.flush()

                val configAckMsg = PoloProtocol.readFramedMessage(input)
                Log.d(TAG, "[IN] ConfigurationAck: status=${configAckMsg.status}, hasConfigAck=${configAckMsg.configurationAck != null}")
                if (configAckMsg.status != PoloProtocol.Status.STATUS_OK && configAckMsg.configurationAck == null) {
                    sslSocket.closeQuietly()
                    return@withTimeout PairingResult.Failed(
                        "The TV rejected the pairing configuration (status ${configAckMsg.status})."
                    )
                }

                if (myAttempt != attemptCounter.get()) {
                    sslSocket.closeQuietly()
                    return@withTimeout PairingResult.Failed("Pairing was superseded by a newer request.")
                }

                // 8) Keep the socket + captured server cert alive for confirmPairingCode().
                Log.d(TAG, "WAITING_FOR_TV_CODE — TV is now displaying the pairing PIN.")
                synchronized(sessionLock) {
                    session = PairingSession(
                        socket = sslSocket,
                        input = input,
                        output = output,
                        device = device,
                        serverCert = serverCert,
                        serverCertSha256 = serverCertSha
                    )
                }
                return@withTimeout PairingResult.CodePromptReceived(
                    "Enter the 6-character code shown on your TV screen"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pairing handshake failed: ${e.message}", e)
            synchronized(sessionLock) { session?.closeQuietly(); session = null }
            PairingResult.Failed("Could not trigger pairing code on TV: ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Submits the user-entered 6-character PIN and completes the cryptographic Polo
     * handshake on the SAME socket opened by [startPairing].
     */
    suspend fun confirmPairingCode(code: String): PairingResult = withContext(Dispatchers.IO) {
        val mySession = synchronized(sessionLock) { session }
        if (mySession == null || mySession.closed) {
            return@withContext PairingResult.Failed("Pairing session expired. Please restart pairing.")
        }
        try {
            val cleanCode = code.trim().uppercase()
            if (cleanCode.length != 6 || !cleanCode.all { it in "0123456789ABCDEF" }) {
                return@withContext PairingResult.Failed(
                    "Invalid code format. Enter the exact 6 hexadecimal characters shown on your TV."
                )
            }

            val clientCert = SslCertificateManager.getClientCertificate(context)
                ?: return@withContext PairingResult.Failed("Client TLS certificate is missing; please restart pairing.")
            val serverCert = mySession.serverCert

            val secret = computePairingSecret(clientCert, serverCert, cleanCode)

            // Verify the secret's first byte matches the PIN's leading two hex chars.
            val expectedFirstByte = cleanCode.substring(0, 2).toInt(16).toByte()
            if (secret[0] != expectedFirstByte) {
                Log.w(TAG, "PIN signature mismatch: hash[0]=${secret[0]}, expected=$expectedFirstByte")
                return@withContext PairingResult.Failed(
                    "Invalid pairing code. The PIN entered does not match the TV's cryptographic challenge."
                )
            }

            withTimeout(handshakeReadTimeoutMs.toLong()) {
                Log.d(TAG, "[OUT] Secret: sending SHA-256 digest")
                mySession.output.write(
                    PoloProtocol.OuterMessage(
                        protocolVersion = 2,
                        status = PoloProtocol.Status.STATUS_OK,
                        secret = PoloProtocol.Secret(secret = secret)
                    ).encode()
                )
                mySession.output.flush()

                val secretAckMsg = PoloProtocol.readFramedMessage(mySession.input)
                Log.d(TAG, "[IN] SecretAck: status=${secretAckMsg.status}, hasSecretAck=${secretAckMsg.secretAck != null}")

                if (secretAckMsg.status == PoloProtocol.Status.STATUS_OK || secretAckMsg.secretAck != null) {
                    Log.d(TAG, "PAIRING_SUCCESS — TV successfully paired and authenticated.")
                    val fingerprint = mySession.serverCertSha256
                    synchronized(sessionLock) { session?.closeQuietly(); session = null }
                    PairingResult.Success("TV successfully paired and verified!", fingerprint)
                } else {
                    Log.w(TAG, "TV rejected pairing secret with status ${secretAckMsg.status}")
                    synchronized(sessionLock) { session?.closeQuietly(); session = null }
                    PairingResult.Failed("The TV rejected the pairing secret (status ${secretAckMsg.status}). Try again.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error confirming pairing PIN: ${e.message}", e)
            synchronized(sessionLock) { session?.closeQuietly(); session = null }
            PairingResult.Failed("Failed to confirm pairing code: ${e.localizedMessage ?: e.message}")
        }
    }

    /** Tears down any in-flight pairing and invalidates it. */
    fun disconnect() {
        attemptCounter.incrementAndGet()
        synchronized(sessionLock) {
            session?.closeQuietly()
            session = null
        }
        Log.d(TAG, "Pairing service disconnected.")
    }

    // ----------------------------------------------------------- crypto helpers

    /**
     * Computes the Android TV Remote v2 pairing secret EXACTLY as
     * tronikos/androidtvremote2 does:
     *
     *   SHA-256( hexUp(clientModulus)
     *          + "0" + hexUp(clientExponent)
     *          + hexUp(serverModulus)
     *          + "0" + hexUp(serverExponent)
     *          + code[2:] )      // last 4 hex chars of the 6-char PIN
     *
     * The leading "0" on each exponent forces an even-length hex string (so it
     * decodes to a whole number of bytes); the first byte of the digest must
     * equal the PIN's leading two hex characters.
     */
    internal fun computePairingSecret(
        clientCert: X509Certificate,
        serverCert: X509Certificate,
        code: String
    ): ByteArray {
        val clientPub = clientCert.publicKey as? RSAPublicKey
            ?: throw IllegalArgumentException("Client certificate public key is not RSA.")
        val serverPub = serverCert.publicKey as? RSAPublicKey
            ?: throw IllegalArgumentException("TV server certificate public key is not RSA.")

        val md = MessageDigest.getInstance("SHA-256")
        md.update(hexToBytes(modulusHex(clientPub.modulus)))
        md.update(hexToBytes("0" + clientPub.publicExponent.toString(16).uppercase()))
        md.update(hexToBytes(modulusHex(serverPub.modulus)))
        md.update(hexToBytes("0" + serverPub.publicExponent.toString(16).uppercase()))
        md.update(hexToBytes(code.substring(2))) // 4 hex chars -> 2 bytes
        return md.digest()
    }

    private fun modulusHex(modulus: BigInteger): String {
        // Uppercase, minimal (no leading zeros, no 0x prefix) — matches tronikos.
        val hex = modulus.toString(16).uppercase()
        // Ensure even length so it decodes to a whole number of bytes.
        return if (hex.length % 2 == 0) hex else "0$hex"
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filter { it in "0123456789ABCDEFabcdef" }
        check(clean.length % 2 == 0) { "Hex string has odd length: $hex" }
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            result[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }

    // ------------------------------------------------------------- session type

    private class PairingSession(
        val socket: SSLSocket,
        val input: InputStream,
        val output: OutputStream,
        val device: TvDevice,
        val serverCert: X509Certificate,
        val serverCertSha256: String
    ) {
        @Volatile
        var closed: Boolean = false
            private set

        fun closeQuietly() {
            if (closed) return
            closed = true
            runCatching { output.close() }
            runCatching { input.close() }
            runCatching { socket.close() }
        }
    }
}

private fun java.net.Socket.closeQuietly() = runCatching { close() }
