package com.example.core.network

import android.content.Context
import android.util.Log
import com.example.core.model.TvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
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
 * Protocol-correct Android TV Remote v2 Pairing Service (Polo Protocol).
 * 
 * Strict Message Sequence:
 * 1. Mutual TLS connection on port 6467 (using client certificate CN=atvremote).
 * 2. OUT: PairingRequest (service_name="atvremote", client_name="TVGrip")
 * 3. IN:  PairingRequestAck
 * 4. OUT: Options (preferred_role=ROLE_TYPE_INPUT, input_encodings=[HEXADECIMAL, 6])
 * 5. IN:  Options
 * 6. OUT: Configuration (encoding=HEXADECIMAL, 6, client_role=ROLE_TYPE_INPUT)
 * 7. IN:  ConfigurationAck -> TV displays 6-digit hex PIN
 * 8. State transitions to WAITING_FOR_TV_CODE
 * 9. Cryptographic secret computation:
 *    SHA-256(client_modulus + client_exponent + server_modulus + server_exponent + PIN_bytes[2:])
 *    Verification: hashResult[0] == PIN_bytes[0..1]
 * 10. OUT: Secret (hashResult)
 * 11. IN:  SecretAck
 * 12. PAIRING_SUCCESS -> Remote session connects on port 6466
 */
class TvPairingService(private val context: Context) {

    private val TAG = "TvPairingService"
    private var pairingSocket: SSLSocket? = null
    private var clientCert: X509Certificate? = null
    private var serverCert: X509Certificate? = null
    private var negotiatedEncoding: PoloProtocol.EncodingType = PoloProtocol.EncodingType.ENCODING_TYPE_HEXADECIMAL
    private var negotiatedSymbolLength: Int = 6

    suspend fun startPairing(device: TvDevice): PairingResult {
        return withContext(Dispatchers.IO) {
            try {
                disconnect()
                val pairingPort = 6467
                Log.d(TAG, "CONNECTING_PAIRING to ${device.host}:$pairingPort")

                val keyManagerFactory = SslCertificateManager.getOrCreateKeyManagerFactory(context)
                clientCert = SslCertificateManager.getClientCertificate(context)
                if (clientCert == null) {
                    return@withContext PairingResult.Failed("Failed to initialize client TLS certificate.")
                }

                val sslContext = SSLContext.getInstance("TLS")
                var capturedServerCert: X509Certificate? = null

                val trustManager = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {
                        if (certs.isNotEmpty()) {
                            capturedServerCert = certs[0]
                        }
                    }
                })
                sslContext.init(keyManagerFactory.keyManagers, trustManager, SecureRandom())

                val rawSocket = Socket()
                try {
                    rawSocket.connect(InetSocketAddress(device.host, pairingPort), 8000)
                } catch (e: Exception) {
                    Log.e(TAG, "Connection to ${device.host}:$pairingPort failed: ${e.message}")
                    return@withContext PairingResult.Failed(
                        "Could not connect to TV pairing service on port 6467 (${e.localizedMessage ?: e.message}).\n\n" +
                        "Troubleshooting:\n" +
                        "• Confirm the TV is on and connected to the same Wi-Fi network.\n" +
                        "• Ensure Client Isolation / AP Isolation is disabled on your router."
                    )
                }
                rawSocket.tcpNoDelay = true
                rawSocket.soTimeout = 20000

                val sslSocket = sslContext.socketFactory.createSocket(
                    rawSocket,
                    device.host,
                    pairingPort,
                    true
                ) as SSLSocket
                sslSocket.startHandshake()

                if (capturedServerCert == null) {
                    val peerCerts = sslSocket.session.peerCertificates
                    if (peerCerts.isNotEmpty() && peerCerts[0] is X509Certificate) {
                        capturedServerCert = peerCerts[0] as X509Certificate
                    }
                }
                serverCert = capturedServerCert
                pairingSocket = sslSocket

                Log.d(TAG, "TLS_CONNECTED on port $pairingPort. Client: ${clientCert?.subjectDN}, Server: ${serverCert?.subjectDN}")

                val output = sslSocket.getOutputStream()
                val input = sslSocket.getInputStream()

                // ==========================================
                // PHASE 1: PAIRING REQUEST
                // ==========================================
                Log.d(TAG, "[OUT] PairingRequest: service_name=atvremote, client_name=TVGrip")
                val pairingRequestMsg = PoloProtocol.OuterMessage(
                    protocolVersion = 2,
                    status = PoloProtocol.Status.STATUS_OK,
                    pairingRequest = PoloProtocol.PairingRequest(
                        serviceName = "atvremote",
                        clientName = "TVGrip"
                    )
                )
                output.write(pairingRequestMsg.encode())
                output.flush()

                val ackMsg = PoloProtocol.readFramedMessage(input)
                Log.d(TAG, "[IN] PairingRequestAck: status=${ackMsg.status}, hasAck=${ackMsg.pairingRequestAck != null}")
                if (ackMsg.status != PoloProtocol.Status.STATUS_OK && ackMsg.pairingRequestAck == null) {
                    disconnect()
                    return@withContext PairingResult.Failed("TV rejected pairing request with status ${ackMsg.status}.")
                }

                // ==========================================
                // PHASE 2: OPTIONS NEGOTIATION (CLIENT OPTIONS)
                // ==========================================
                Log.d(TAG, "[OUT] Options: preferred_role=ROLE_TYPE_INPUT, input_encodings=[HEXADECIMAL, 6]")
                val clientOptionsMsg = PoloProtocol.OuterMessage(
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
                )
                output.write(clientOptionsMsg.encode())
                output.flush()

                // ==========================================
                // PHASE 3: RECEIVE TV OPTIONS
                // ==========================================
                val tvOptionsMsg = PoloProtocol.readFramedMessage(input)
                Log.d(TAG, "[IN] Options: status=${tvOptionsMsg.status}, hasOptions=${tvOptionsMsg.options != null}")
                if (tvOptionsMsg.status != PoloProtocol.Status.STATUS_OK && tvOptionsMsg.options == null) {
                    disconnect()
                    return@withContext PairingResult.Failed("TV rejected options negotiation (status ${tvOptionsMsg.status}).")
                }

                val tvOptions = tvOptionsMsg.options
                if (tvOptions != null) {
                    val matchingEncoding = tvOptions.inputEncodings.firstOrNull {
                        it.type == PoloProtocol.EncodingType.ENCODING_TYPE_HEXADECIMAL
                    } ?: tvOptions.inputEncodings.firstOrNull()

                    if (matchingEncoding != null) {
                        negotiatedEncoding = matchingEncoding.type
                        negotiatedSymbolLength = matchingEncoding.symbolLength.coerceIn(4, 16)
                    }
                }

                // ==========================================
                // PHASE 4: SEND CONFIGURATION
                // ==========================================
                Log.d(TAG, "[OUT] Configuration: encoding=$negotiatedEncoding, symbol_length=$negotiatedSymbolLength, client_role=ROLE_TYPE_INPUT")
                val configMsg = PoloProtocol.OuterMessage(
                    protocolVersion = 2,
                    status = PoloProtocol.Status.STATUS_OK,
                    configuration = PoloProtocol.Configuration(
                        encoding = PoloProtocol.Encoding(
                            type = negotiatedEncoding,
                            symbolLength = negotiatedSymbolLength
                        ),
                        clientRole = PoloProtocol.RoleType.ROLE_TYPE_INPUT
                    )
                )
                output.write(configMsg.encode())
                output.flush()

                // ==========================================
                // PHASE 5: PAIRING CODE TRIGGER & CONFIG ACK
                // ==========================================
                val configAckMsg = PoloProtocol.readFramedMessage(input)
                Log.d(TAG, "[IN] ConfigurationAck: status=${configAckMsg.status}, hasConfigAck=${configAckMsg.configurationAck != null}")
                if (configAckMsg.status != PoloProtocol.Status.STATUS_OK && configAckMsg.configurationAck == null) {
                    disconnect()
                    return@withContext PairingResult.Failed("TV rejected pairing configuration (status ${configAckMsg.status}).")
                }

                Log.d(TAG, "WAITING_FOR_TV_CODE — TV is now displaying the pairing PIN.")
                PairingResult.CodePromptReceived("Enter the 6-character code shown on your TV screen")
            } catch (e: Exception) {
                Log.e(TAG, "Pairing handshake failed: ${e.message}", e)
                disconnect()
                PairingResult.Failed("Could not trigger pairing code on TV: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    /**
     * Submits the user-entered 6-character PIN and completes the cryptographic Polo handshake.
     */
    suspend fun confirmPairingCode(code: String): PairingResult {
        return withContext(Dispatchers.IO) {
            try {
                val cleanCode = code.trim().uppercase()
                if (cleanCode.length != 6 || !cleanCode.all { it in "0123456789ABCDEF" }) {
                    return@withContext PairingResult.Failed("Invalid code format. Please enter the exact 6 hexadecimal characters shown on your TV.")
                }

                val socket = pairingSocket
                val cCert = clientCert
                val sCert = serverCert

                if (socket == null || cCert == null || sCert == null || socket.isClosed) {
                    disconnect()
                    return@withContext PairingResult.Failed("Pairing session expired. Please restart pairing.")
                }

                val clientPubKey = cCert.publicKey as? RSAPublicKey
                    ?: return@withContext PairingResult.Failed("Invalid client RSA public key.")
                val serverPubKey = sCert.publicKey as? RSAPublicKey
                    ?: return@withContext PairingResult.Failed("Invalid TV server RSA public key.")

                // ==========================================
                // PHASE 7: CORRECT POLO PAIRING SECRET CALCULATION
                // ==========================================
                val clientModulus = clientPubKey.modulus.toUnsignedByteArray()
                val clientExponent = clientPubKey.publicExponent.toUnsignedByteArray()
                val serverModulus = serverPubKey.modulus.toUnsignedByteArray()
                val serverExponent = serverPubKey.publicExponent.toUnsignedByteArray()

                // Code remainder bytes (excluding the first two characters)
                val codeRemainderHex = cleanCode.substring(2)
                val codeRemainderBytes = hexToBytes(codeRemainderHex)

                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(clientModulus)
                digest.update(clientExponent)
                digest.update(serverModulus)
                digest.update(serverExponent)
                digest.update(codeRemainderBytes)
                val hashResult = digest.digest()

                val expectedFirstByte = cleanCode.substring(0, 2).toInt(16).toByte()
                if (hashResult[0] != expectedFirstByte) {
                    Log.w(TAG, "PIN signature verification failed: hash[0]=${hashResult[0]}, expected=$expectedFirstByte")
                    return@withContext PairingResult.Failed("Invalid pairing code. The PIN entered does not match the TV's cryptographic challenge.")
                }

                // ==========================================
                // PHASE 8: SEND SECRET
                // ==========================================
                Log.d(TAG, "[OUT] Secret: sending SHA-256 digest")
                val output = socket.getOutputStream()
                val input = socket.getInputStream()

                val secretMsg = PoloProtocol.OuterMessage(
                    protocolVersion = 2,
                    status = PoloProtocol.Status.STATUS_OK,
                    secret = PoloProtocol.Secret(secret = hashResult)
                )
                output.write(secretMsg.encode())
                output.flush()

                // ==========================================
                // PHASE 9: SECRET ACK
                // ==========================================
                val secretAckMsg = PoloProtocol.readFramedMessage(input)
                Log.d(TAG, "[IN] SecretAck: status=${secretAckMsg.status}, hasSecretAck=${secretAckMsg.secretAck != null}")

                if (secretAckMsg.status == PoloProtocol.Status.STATUS_OK || secretAckMsg.secretAck != null) {
                    Log.d(TAG, "PAIRING_SUCCESS — TV successfully paired and authenticated.")
                    disconnect()
                    PairingResult.Success("TV successfully paired and verified!")
                } else {
                    Log.w(TAG, "TV rejected pairing secret with status ${secretAckMsg.status}")
                    disconnect()
                    PairingResult.Failed("TV rejected pairing secret (status ${secretAckMsg.status}). Please try again.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error confirming pairing PIN: ${e.message}", e)
                disconnect()
                PairingResult.Failed("Failed to confirm pairing code: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    private fun BigInteger.toUnsignedByteArray(): ByteArray {
        val array = this.toByteArray()
        return if (array.isNotEmpty() && array[0] == 0.toByte()) {
            array.copyOfRange(1, array.size)
        } else {
            array
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.filter { it in "0123456789ABCDEFabcdef" }
        val result = ByteArray(clean.length / 2)
        for (i in 0 until clean.length step 2) {
            result[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
        }
        return result
    }

    fun disconnect() {
        try {
            pairingSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing pairing socket: ${e.message}")
        } finally {
            pairingSocket = null
            serverCert = null
            clientCert = null
        }
    }
}
