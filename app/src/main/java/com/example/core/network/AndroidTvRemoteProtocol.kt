package com.example.core.network

import android.content.Context
import android.util.Log
import com.example.TVGripApplication
import com.example.core.model.CapabilitySet
import com.example.core.model.GamepadState
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.abs

/**
 * Android TV Remote v2 protocol over mutual-TLS port 6466.
 *
 * Wire contract (verified against tronikos/androidtvremote2 and
 * louis49/androidtv-remote):
 *
 *   RemoteMessage:
 *     remote_configure            = 1
 *     remote_set_active           = 2
 *     remote_ping_request         = 8
 *     remote_ping_response        = 9
 *     remote_key_inject           = 10
 *     remote_ime_key_inject       = 20
 *     remote_ime_batch_edit       = 21
 *     remote_voice_begin/payload  = 30/31/32
 *     remote_start                = 40
 *     remote_set_volume_level     = 50
 *     remote_app_link_launch      = 90
 *
 *   Handshake after TLS:
 *     1. TV -> remote_configure (supported feature mask)
 *     2. phone -> remote_configure (phone feature mask + device info)
 *     3. TV -> remote_set_active
 *     4. phone -> remote_set_active
 *     5. TV -> remote_start (session is ready and authenticated)
 *
 * A connection is only reported as "connected" after remote_start has been
 * received, i.e. an authenticated, usable session truly exists.
 */
class AndroidTvRemoteProtocol(
    private val context: Context = TVGripApplication.instance,
    private val onDisconnected: (() -> Unit)? = null
) : TvProtocol {

    companion object {
        private const val TAG = "AndroidTvRemoteProtocol"
        private const val REMOTE_PORT_DEFAULT = 6466
        private const val CONNECT_TIMEOUT_MS = 6000
        private const val HANDSHAKE_TIMEOUT_MS = 12000L
        private const val ACTIVE_FEATURES = 1 or 2 or 4 or 32 or 64 or 512 // PING|KEY|IME|POWER|VOLUME|APP_LINK

        private const val DIRECTION_START = 1
        private const val DIRECTION_END = 2
        private const val DIRECTION_SHORT = 3

        // Android key codes used by RemoteKeyInject.
        private const val KEY_DPAD_UP = 19
        private const val KEY_DPAD_DOWN = 20
        private const val KEY_DPAD_LEFT = 21
        private const val KEY_DPAD_RIGHT = 22
        private const val KEY_DPAD_CENTER = 23
        private const val KEY_BACK = 4
        private const val KEY_HOME = 3
        private const val KEY_MENU = 82
        private const val KEY_SEARCH = 84
        private const val KEY_POWER = 26
        private const val KEY_VOLUME_UP = 24
        private const val KEY_VOLUME_DOWN = 25
        private const val KEY_VOLUME_MUTE = 164
        private const val KEY_PAGE_UP = 92
        private const val KEY_PAGE_DOWN = 93

        private const val KEY_BUTTON_A = 96
        private const val KEY_BUTTON_B = 97
        private const val KEY_BUTTON_X = 99
        private const val KEY_BUTTON_Y = 100
        private const val KEY_BUTTON_L1 = 102
        private const val KEY_BUTTON_R1 = 103
        private const val KEY_BUTTON_L2 = 104
        private const val KEY_BUTTON_R2 = 105
        private const val KEY_BUTTON_THUMBL = 106
        private const val KEY_BUTTON_THUMBR = 107
        private const val KEY_BUTTON_START = 108
        private const val KEY_BUTTON_SELECT = 109

        private val POINTER_THRESHOLD = 22f

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = AtomicBoolean(false)
    private val writeLock = Any()

    @Volatile
    private var sslSocket: SSLSocket? = null
    @Volatile
    private var outputStream: OutputStream? = null
    @Volatile
    private var inputStream: InputStream? = null
    @Volatile
    private var connectedDevice: TvDevice? = null
    @Volatile
    private var readerJob: Job? = null

    @Volatile
    private var imeCounter = 0
    @Volatile
    private var imeFieldCounter = 0
    @Volatile
    private var pingRequestSentAtMs = 0L
    @Volatile
    private var lastPingRttMs = -1L
    @Volatile
    private var tvSupportedFeatures = ACTIVE_FEATURES

    private var pointerAccumX = 0f
    private var pointerAccumY = 0f

    private val heldKeys = mutableMapOf<Int, Boolean>()
    private var lastStickDirX = 0
    private var lastStickDirY = 0

    override suspend fun connect(device: TvDevice, pairingCode: String?): ConnectionResult {
        return withContext(Dispatchers.IO) {
            try {
                disconnect()

                val host = device.host
                val remotePort = if (device.port > 0) device.port else REMOTE_PORT_DEFAULT
                Log.d(TAG, "Connecting to Android TV Remote v2 session at $host:$remotePort")

                val expectedFingerprint = device.serverCertSha256
                if (expectedFingerprint.isNullOrBlank()) {
                    error(
                        "This TV does not have a stored server certificate fingerprint. " +
                            "Remove and re-pair the TV in TVGrip so the TV identity can be verified."
                    )
                }
                val keyManagerFactory = SslCertificateManager.getOrCreateKeyManagerFactory(context)
                val sslContext = buildSslContext(
                    keyManagerFactory,
                    expectedServerFingerprint = expectedFingerprint
                )

                val rawSocket = Socket()
                rawSocket.connect(InetSocketAddress(host, remotePort), CONNECT_TIMEOUT_MS)
                rawSocket.tcpNoDelay = true

                val sslSocket = sslContext.socketFactory.createSocket(
                    rawSocket,
                    host,
                    remotePort,
                    true
                ) as SSLSocket
                sslSocket.soTimeout = 30_000
                sslSocket.startHandshake()

                outputStream = sslSocket.getOutputStream()
                inputStream = sslSocket.getInputStream()
                connectedDevice = device
                ready.set(false)

                val readySignal = CompletableDeferred<Boolean>()
                readerJob = scope.launch { runReaderLoop(readySignal) }

                try {
                    val isReady = withTimeout(HANDSHAKE_TIMEOUT_MS) { readySignal.await() }
                    if (!isReady) {
                        error("The TV did not start the Android TV Remote session (remote_start was not received).")
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Timeout waiting for TV Remote session handshake")
                    error("Timed out waiting for the TV Remote session. The TV may not support this protocol or is not paired.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error while waiting for remote handshake: ${e.message}")
                    throw e
                }

                Log.d(TAG, "Android TV Remote v2 session authenticated and ready on ${device.name}")
                ConnectionResult.Success(device.capabilities)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect Android TV Remote v2 session: ${e.message}")
                runBlockingDisconnect()
                ConnectionResult.Failed(
                    "Could not establish an authenticated remote session with ${device.host}:${device.port}. " +
                        if (device.serverCertSha256.isNullOrBlank()) {
                            "Verify the TV is powered on, reachable, and paired."
                        } else {
                            "Verify you are still on the same Wi-Fi network. If the TV was reset or re-paired, remove and re-pair it in TVGrip."
                        }
                )
            }
        }
    }

    override suspend fun sendCommand(command: TvCommand): Boolean {
        return withContext(Dispatchers.IO) {
            if (!ready.get() || outputStream == null) {
                Log.d(TAG, "Cannot send command: authenticated remote session is not available")
                return@withContext false
            }
            try {
                synchronized(writeLock) {
                    when (command) {
                        is TvCommand.KeyPress -> sendKeyInject(command.key.code, DIRECTION_SHORT)
                        is TvCommand.KeyDown -> {
                            sendKeyInject(command.key.code, DIRECTION_START)
                            heldKeys[command.key.code] = true
                        }
                        is TvCommand.KeyUp -> {
                            sendKeyInject(command.key.code, DIRECTION_END)
                            heldKeys.remove(command.key.code)
                        }
                        is TvCommand.SendText -> sendImeBatchEdit(command.text)
                        is TvCommand.TextString -> sendImeBatchEdit(command.text)
                        is TvCommand.PointerMove -> sendPointerNavigation(command.deltaX, command.deltaY)
                        is TvCommand.PointerClick -> sendPointerClick(command.isRightClick, command.isLongPress)
                        is TvCommand.PointerScroll -> sendPointerScroll(command.scrollY)
                        is TvCommand.LaunchApp -> sendAppLink(command.packageName)
                        is TvCommand.GamepadUpdate -> sendGamepadFallback(command.state)
                        is TvCommand.Ping -> sendPingRequest()
                    }
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing command to televison: ${e.message}")
                markDisconnected()
                false
            }
        }
    }

    override suspend fun measureLatency(): Long {
        return withContext(Dispatchers.IO) {
            if (!ready.get()) -1L
            else lastPingRttMs
        }
    }

    override suspend fun fetchCapabilities(): CapabilitySet =
        connectedDevice?.capabilities ?: CapabilitySet.DEFAULT_ANDROID_TV

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) { closeSocket() }
    }

    override fun isConnected(): Boolean = ready.get()

    private fun runBlockingDisconnect() {
        closeSocket()
    }

    private fun closeSocket() {
        ready.set(false)
        readerJob?.cancel()
        readerJob = null
        runCatching { outputStream?.close() }
        runCatching { inputStream?.close() }
        runCatching { sslSocket?.close() }
        sslSocket = null
        outputStream = null
        inputStream = null
        connectedDevice = null
        releaseAllGamepadKeys()
        heldKeys.clear()
        lastStickDirX = 0
        lastStickDirY = 0
        tvSupportedFeatures = ACTIVE_FEATURES
    }

    private fun markDisconnected() {
        val wasReady = ready.getAndSet(false)
        releaseAllGamepadKeys()
        if (wasReady) {
            Log.w(TAG, "Remote session disconnected")
            onDisconnected?.invoke()
        }
    }

    private suspend fun runReaderLoop(readySignal: CompletableDeferred<Boolean>) {
        try {
            val input = inputStream ?: return
            while (kotlinx.coroutines.currentCoroutineContext().isActive && sslSocket?.isClosed == false) {
                val payload = readDelimitedMessage(input) ?: continue
                handleIncomingMessage(payload, readySignal)
            }
        } catch (e: EOFException) {
            Log.d(TAG, "Remote session stream closed (TV went away).")
        } catch (e: Exception) {
            Log.d(TAG, "Remote session reader stopped: ${e.message}")
        }
        markDisconnected()
    }

    private fun handleIncomingMessage(bytes: ByteArray, readySignal: CompletableDeferred<Boolean>) {
        val incoming = RemoteMessageDecoder.decode(bytes)
        synchronized(writeLock) {
            when {
                incoming.hasConfigure -> {
                    val supported = incoming.configureCode1
                    tvSupportedFeatures = supported and ACTIVE_FEATURES
                    Log.d(TAG, "TV supports feature mask=$supported; negotiated active features=${tvSupportedFeatures}")
                    sendConfigure()
                }
                incoming.hasSetActive -> {
                    sendSetActive()
                }
                incoming.hasPingRequest -> {
                    sendPingResponse(incoming.pingRequestVal1)
                }
                incoming.hasPingResponse -> {
                    val now = System.currentTimeMillis()
                    if (pingRequestSentAtMs > 0L) {
                        lastPingRttMs = (now - pingRequestSentAtMs).coerceAtLeast(0L)
                        pingRequestSentAtMs = 0L
                    }
                    Log.d(TAG, "Ping response: val1=${incoming.pingResponseVal1}, rtt=${lastPingRttMs}ms")
                }
                incoming.hasImeBatchEdit -> {
                    imeCounter = incoming.imeCounter
                    imeFieldCounter = incoming.imeFieldCounter
                }
                incoming.hasStart -> {
                    if (!readySignal.isCompleted) {
                        readySignal.complete(true)
                    }
                    ready.set(true)
                    Log.d(TAG, "TV Remote session started (authenticated): started=${incoming.startStarted}")
                }
                incoming.hasError -> {
                    Log.w(TAG, "TV reported remote error")
                }
                else -> {
                    Log.d(TAG, "Received unhandled remote message: ${bytes.size} bytes")
                }
            }
        }
    }

    // ---------- Outgoing messages ----------

    private fun sendConfigure() {
        val stream = outputStream ?: return
        // Matches tronikos/androidtvremote2 remote.py: the client reports its
        // own device_info with the fields the protocol uses to identify the
        // remote app (not the TV model).
        val deviceInfo = ByteArrayOutputStream().apply {
            writeVarintField(this, 3, 1L)            // unknown1
            writeStringField(this, 4, "1")           // unknown2
            writeStringField(this, 5, "atvremote")   // package_name
            writeStringField(this, 6, "1.0.0")       // app_version
        }.toByteArray()

        val active = tvSupportedFeatures
        val configure = ByteArrayOutputStream().apply {
            writeVarintField(this, 1, active.toLong())
            writeLengthDelimitedField(this, 2, deviceInfo)
        }.toByteArray()

        val outer = ByteArrayOutputStream().apply {
            writeLengthDelimitedField(this, 1, configure) // remote_configure
        }.toByteArray()
        writeDelimited(stream, outer)
    }

    private fun sendSetActive() {
        val stream = outputStream ?: return
        val setActive = ByteArrayOutputStream().apply {
            writeVarintField(this, 1, tvSupportedFeatures.toLong())
        }.toByteArray()
        val outer = ByteArrayOutputStream().apply {
            writeLengthDelimitedField(this, 2, setActive) // remote_set_active
        }.toByteArray()
        writeDelimited(stream, outer)
    }

    private fun sendPingResponse(val1: Int) {
        val stream = outputStream ?: return
        val ping = ByteArrayOutputStream().apply {
            writeVarintField(this, 1, val1.toLong()) // val1
        }.toByteArray()
        val outer = ByteArrayOutputStream().apply {
            writeLengthDelimitedField(this, 9, ping) // remote_ping_response
        }.toByteArray()
        writeDelimited(stream, outer)
    }

    private fun sendKeyInject(keyCode: Int, direction: Int) {
        val stream = outputStream ?: return
        val key = ByteArrayOutputStream().apply {
            writeVarintField(this, 1, keyCode.toLong()) // key_code
            writeVarintField(this, 2, direction.toLong()) // direction
        }.toByteArray()
        val outer = ByteArrayOutputStream().apply {
            writeLengthDelimitedField(this, 10, key) // remote_key_inject
        }.toByteArray()
        writeDelimited(stream, outer)
    }

    private fun sendImeBatchEdit(text: String) {
        val stream = outputStream ?: return
        if (text.isEmpty()) return
        // See tronikos/androidtvremote2.remote.send_text: start/end are length-1
        val cursor = text.length - 1
        val imeObject = ByteArrayOutputStream().apply {
            writeVarintField(this, 1, cursor.toLong())          // start
            writeVarintField(this, 2, cursor.toLong())          // end
            writeStringField(this, 3, text)                     // value
        }.toByteArray()
        val editInfo = ByteArrayOutputStream().apply {
            writeVarintField(this, 1, 1L)                        // insert
            writeLengthDelimitedField(this, 2, imeObject)        // text_field_status
        }.toByteArray()
        val batch = ByteArrayOutputStream().apply {
            writeVarintField(this, 1, imeCounter.toLong())       // ime_counter
            writeVarintField(this, 2, imeFieldCounter.toLong())  // field_counter
            writeLengthDelimitedField(this, 3, editInfo)         // edit_info
        }.toByteArray()
        val outer = ByteArrayOutputStream().apply {
            writeLengthDelimitedField(this, 21, batch) // remote_ime_batch_edit
        }.toByteArray()
        writeDelimited(stream, outer)
    }

    private fun sendAppLink(linkOrPackage: String) {
        val stream = outputStream ?: return
        if (linkOrPackage.isBlank()) return
        val link = if (linkOrPackage.startsWith("http://") || linkOrPackage.startsWith("https://") ||
            linkOrPackage.contains("://")) {
            linkOrPackage
        } else {
            "market://launch?id=$linkOrPackage"
        }
        val request = ByteArrayOutputStream().apply {
            writeStringField(this, 1, link) // app_link
        }.toByteArray()
        val outer = ByteArrayOutputStream().apply {
            writeLengthDelimitedField(this, 90, request) // remote_app_link_launch_request
        }.toByteArray()
        writeDelimited(stream, outer)
    }

    private fun sendPointerNavigation(dx: Float, dy: Float) {
        pointerAccumX += dx
        pointerAccumY += dy
        if (abs(pointerAccumX) > POINTER_THRESHOLD || abs(pointerAccumY) > POINTER_THRESHOLD) {
            val navKey = when {
                abs(pointerAccumX) > abs(pointerAccumY) && pointerAccumX > 0 -> KEY_DPAD_RIGHT
                abs(pointerAccumX) > abs(pointerAccumY) && pointerAccumX < 0 -> KEY_DPAD_LEFT
                abs(pointerAccumY) >= abs(pointerAccumX) && pointerAccumY > 0 -> KEY_DPAD_DOWN
                else -> KEY_DPAD_UP
            }
            // A short key press drives Android TV navigation. This is the real
            // supported alternative on TVs that do not expose an absolute
            // pointer stream over this protocol.
            sendKeyInject(navKey, DIRECTION_SHORT)
            pointerAccumX = 0f
            pointerAccumY = 0f
        }
    }

    private fun sendPointerClick(isRightClick: Boolean, isLongPress: Boolean) {
        if (isRightClick) {
            sendKeyInject(KEY_BACK, DIRECTION_SHORT)
            return
        }
        if (isLongPress) {
            sendKeyInject(KEY_DPAD_CENTER, DIRECTION_START)
            scope.launch {
                delay(450)
                synchronized(writeLock) {
                    sendKeyInject(KEY_DPAD_CENTER, DIRECTION_END)
                }
            }
        } else {
            sendKeyInject(KEY_DPAD_CENTER, DIRECTION_SHORT)
        }
    }

    private fun sendPointerScroll(scrollY: Float) {
        // Real supported alternative: page navigation keys.
        if (scrollY < 0f) sendKeyInject(KEY_PAGE_UP, DIRECTION_SHORT)
        else sendKeyInject(KEY_PAGE_DOWN, DIRECTION_SHORT)
    }

    /**
     * Android TV Remote v2 has no analog controller stream. This sends the real
     * keyboard/gamepad key injects that Android TV understands based on the
     * current button/trigger/dpad state. Analog positions are used only as
     * directional input, not fake analog packets.
     */
    private fun sendGamepadFallback(state: GamepadState) {
        setGamepadKey(KEY_DPAD_UP, state.isDpadUp)
        setGamepadKey(KEY_DPAD_DOWN, state.isDpadDown)
        setGamepadKey(KEY_DPAD_LEFT, state.isDpadLeft)
        setGamepadKey(KEY_DPAD_RIGHT, state.isDpadRight)
        setGamepadKey(KEY_BUTTON_A, state.isAPressed)
        setGamepadKey(KEY_BUTTON_B, state.isBPressed)
        setGamepadKey(KEY_BUTTON_X, state.isXPressed)
        setGamepadKey(KEY_BUTTON_Y, state.isYPressed)
        setGamepadKey(KEY_BUTTON_L1, state.isL1Pressed)
        setGamepadKey(KEY_BUTTON_R1, state.isR1Pressed)
        setGamepadKey(KEY_BUTTON_L2, state.l2Value > 0.25f)
        setGamepadKey(KEY_BUTTON_R2, state.r2Value > 0.25f)
        setGamepadKey(KEY_BUTTON_THUMBL, state.isL3Pressed)
        setGamepadKey(KEY_BUTTON_THUMBR, state.isR3Pressed)
        setGamepadKey(KEY_BUTTON_START, state.isStartPressed)
        setGamepadKey(KEY_BUTTON_SELECT, state.isSelectPressed)
        setGamepadKey(KEY_HOME, state.isHomePressed)
        setGamepadKey(KEY_BACK, state.isBackPressed)

        val stickDirX = when {
            state.leftStickX > 0.35f -> 1
            state.leftStickX < -0.35f -> -1
            else -> 0
        }
        if (stickDirX != lastStickDirX) {
            if (lastStickDirX != 0) setGamepadKey(if (lastStickDirX > 0) KEY_DPAD_RIGHT else KEY_DPAD_LEFT, false)
            if (stickDirX != 0) setGamepadKey(if (stickDirX > 0) KEY_DPAD_RIGHT else KEY_DPAD_LEFT, true)
            lastStickDirX = stickDirX
        }

        val stickDirY = when {
            state.leftStickY > 0.35f -> 1
            state.leftStickY < -0.35f -> -1
            else -> 0
        }
        if (stickDirY != lastStickDirY) {
            if (lastStickDirY != 0) setGamepadKey(if (lastStickDirY > 0) KEY_DPAD_DOWN else KEY_DPAD_UP, false)
            if (stickDirY != 0) setGamepadKey(if (stickDirY > 0) KEY_DPAD_DOWN else KEY_DPAD_UP, true)
            lastStickDirY = stickDirY
        }
    }

    /** Sends a key up for any key still held, then clears the held set. */
    private fun releaseAllGamepadKeys() {
        val keys = heldKeys.keys.toList()
        for (key in keys) {
            try {
                sendKeyInject(key, DIRECTION_END)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to release held key $key: ${e.message}")
            }
        }
        heldKeys.clear()
        lastStickDirX = 0
        lastStickDirY = 0
    }

    private fun setGamepadKey(keyCode: Int, pressed: Boolean) {
        val previous = heldKeys[keyCode] ?: false
        if (previous == pressed) return
        heldKeys[keyCode] = pressed
        sendKeyInject(keyCode, if (pressed) DIRECTION_START else DIRECTION_END)
    }

    private fun sendPingRequest() {
        val stream = outputStream ?: return
        pingRequestSentAtMs = System.currentTimeMillis()
        val ping = ByteArrayOutputStream().apply {
            writeVarintField(this, 1, System.currentTimeMillis().toInt().toLong()) // val1
            writeVarintField(this, 2, 0L) // val2
        }.toByteArray()
        val outer = ByteArrayOutputStream().apply {
            writeLengthDelimitedField(this, 8, ping) // remote_ping_request
        }.toByteArray()
        writeDelimited(stream, outer)
    }

    // ---------- TLS ----------

    private fun buildSslContext(
        keyManagerFactory: javax.net.ssl.KeyManagerFactory,
        expectedServerFingerprint: String?
    ): SSLContext {
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty()) {
                    throw CertificateException("TV presented no TLS certificate")
                }
                if (!expectedServerFingerprint.isNullOrBlank()) {
                    val actual = sha256Hex(chain[0].encoded)
                    if (!actual.equals(expectedServerFingerprint, ignoreCase = true)) {
                        throw CertificateException(
                            "TV server certificate fingerprint mismatch. The TV may have been reset or re-paired. Remove and pair it again in TVGrip."
                        )
                    }
                }
                // The Android TV Remote service presents a self-signed certificate
                // and the pairing secret is what authenticates the TV. On reconnect
                // we additionally pin the server's exact DER certificate.
            }
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(
            keyManagerFactory.keyManagers,
            arrayOf<TrustManager>(trustManager),
            SecureRandom()
        )
        return ctx
    }

    // ---------- Framing and wire helpers ----------

    private fun writeDelimited(stream: OutputStream, bytes: ByteArray) {
        writeVarint(stream, bytes.size.toLong())
        stream.write(bytes)
        stream.flush()
    }

    private fun readDelimitedMessage(input: InputStream): ByteArray? {
        val length = readVarint(input).toInt()
        if (length <= 0 || length > 1_000_000) {
            throw IllegalStateException("Invalid remote message length: $length")
        }
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read == -1) throw EOFException("EOF while reading $length byte remote message")
            offset += read
        }
        return buffer
    }

    private fun writeVarint(out: OutputStream, value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    private fun readVarint(input: InputStream): Long {
        var result = 0L
        var shift = 0
        while (shift <= 63) {
            val b = input.read()
            if (b == -1) throw EOFException("EOF reading varint")
            result = result or ((b.toLong() and 0x7F) shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        throw IllegalStateException("Malformed varint")
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

/**
 * Minimal decoder for the fields this app needs from an incoming RemoteMessage.
 */
internal class RemoteMessageDecoder private constructor(
    val hasConfigure: Boolean,
    val configureCode1: Int,
    val hasSetActive: Boolean,
    val hasPingRequest: Boolean,
    val pingRequestVal1: Int,
    val hasPingResponse: Boolean,
    val pingResponseVal1: Int,
    val hasImeBatchEdit: Boolean,
    val imeCounter: Int,
    val imeFieldCounter: Int,
    val hasStart: Boolean,
    val startStarted: Boolean,
    val hasError: Boolean
) {
    companion object {
        fun decode(bytes: ByteArray): RemoteMessageDecoder {
            var hasConfigure = false
            var configureCode1 = 0
            var hasSetActive = false
            var hasPingRequest = false
            var pingVal1 = 0
            var hasPingResponse = false
            var pingResponseVal1 = 0
            var hasImeBatchEdit = false
            var imeCounter = 0
            var imeFieldCounter = 0
            var hasStart = false
            var startStarted = false
            var hasError = false

            var offset = 0
            try {
                while (offset < bytes.size) {
                    val tag = readVarint(bytes, offset)
                    offset = tag.second
                    val field = (tag.first ushr 3).toInt()
                    val wire = (tag.first and 0x07).toInt()
                    when (wire) {
                        0 -> {
                            val v = readVarint(bytes, offset)
                            offset = v.second
                            // Field 40 (remote_start) is a nested message with
                            // wire type 2 (handled below); ignore stray varints.
                        }
                        2 -> {
                            val len = readVarint(bytes, offset)
                            offset = len.second
                            val lenInt = len.first.toInt()
                            val start = offset
                            offset += lenInt
                            when (field) {
                                1 -> {
                                    hasConfigure = true
                                    configureCode1 = parseConfigureCode1(bytes, start, lenInt)
                                }
                                2 -> hasSetActive = true
                                8 -> {
                                    hasPingRequest = true
                                    pingVal1 = parsePingVal1(bytes, start, lenInt)
                                }
                                9 -> {
                                    hasPingResponse = true
                                    pingResponseVal1 = parsePingVal1(bytes, start, lenInt)
                                }
                                21 -> {
                                    hasImeBatchEdit = true
                                    val counters = parseImeCounter(bytes, start, lenInt)
                                    imeCounter = counters.first
                                    imeFieldCounter = counters.second
                                }
                                40 -> {
                                    hasStart = true
                                    startStarted = parseStartStarted(bytes, start, lenInt)
                                }
                                3 -> hasError = true
                                else -> {}
                            }
                        }
                        else -> break
                    }
                }
            } catch (_: Exception) {
                // Best-effort parse: a malformed frame is ignored by the caller.
            }

            return RemoteMessageDecoder(
                hasConfigure,
                configureCode1,
                hasSetActive,
                hasPingRequest,
                pingVal1,
                hasPingResponse,
                pingResponseVal1,
                hasImeBatchEdit,
                imeCounter,
                imeFieldCounter,
                hasStart,
                startStarted,
                hasError
            )
        }

        private fun parseConfigureCode1(bytes: ByteArray, start: Int, length: Int): Int {
            var offset = start
            val end = start + length
            while (offset < end) {
                val tag = readVarint(bytes, offset)
                offset = tag.second
                val field = (tag.first ushr 3).toInt()
                val wire = (tag.first and 0x07).toInt()
                if (wire == 0) {
                    val v = readVarint(bytes, offset)
                    offset = v.second
                    if (field == 1) return v.first.toInt()
                } else if (wire == 2) {
                    val len = readVarint(bytes, offset)
                    offset = len.second + len.first.toInt()
                }
            }
            return 0
        }

        private fun parsePingVal1(bytes: ByteArray, start: Int, length: Int): Int {
            var offset = start
            val end = start + length
            while (offset < end) {
                val tag = readVarint(bytes, offset)
                offset = tag.second
                val field = (tag.first ushr 3).toInt()
                val wire = (tag.first and 0x07).toInt()
                if (wire == 0) {
                    val v = readVarint(bytes, offset)
                    offset = v.second
                    if (field == 1) return v.first.toInt()
                } else if (wire == 2) {
                    val len = readVarint(bytes, offset)
                    offset = len.second + len.first.toInt()
                }
            }
            return 0
        }

        private fun parseStartStarted(bytes: ByteArray, start: Int, length: Int): Boolean {
            var offset = start
            val end = start + length
            var started = false
            while (offset < end) {
                val tag = readVarint(bytes, offset)
                offset = tag.second
                val field = (tag.first ushr 3).toInt()
                val wire = (tag.first and 0x07).toInt()
                if (wire == 0) {
                    val v = readVarint(bytes, offset)
                    offset = v.second
                    if (field == 1) started = v.first != 0L
                } else if (wire == 2) {
                    val len = readVarint(bytes, offset)
                    offset = len.second + len.first.toInt()
                }
            }
            return started
        }

        private fun parseImeCounter(bytes: ByteArray, start: Int, length: Int): Pair<Int, Int> {
            var offset = start
            val end = start + length
            var counter = 0
            var fieldCounter = 0
            while (offset < end) {
                val tag = readVarint(bytes, offset)
                offset = tag.second
                val field = (tag.first ushr 3).toInt()
                val wire = (tag.first and 0x07).toInt()
                if (wire == 0) {
                    val v = readVarint(bytes, offset)
                    offset = v.second
                    when (field) {
                        1 -> counter = v.first.toInt()
                        2 -> fieldCounter = v.first.toInt()
                    }
                } else if (wire == 2) {
                    val len = readVarint(bytes, offset)
                    offset = len.second + len.first.toInt()
                }
            }
            return counter to fieldCounter
        }

        private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
            var result = 0L
            var shift = 0
            var offset = start
            while (shift <= 63 && offset < bytes.size) {
                val b = bytes[offset++].toInt()
                result = result or ((b.toLong() and 0x7F) shl shift)
                if ((b and 0x80) == 0) return result to offset
                shift += 7
            }
            return result to offset
        }
    }
}
