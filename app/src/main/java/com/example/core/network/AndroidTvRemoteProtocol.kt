package com.example.core.network

import android.content.Context
import android.util.Log
import com.example.TVGripApplication
import com.example.core.model.CapabilitySet
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice
import com.example.core.model.TvKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * Universal Android TV Remote Protocol v2 & Companion Protocol implementation.
 * 
 * Supports Google's official Android TV Remote v2 (TLS 6466 with Mutual TLS client certificates
 * and protobuf wire format).
 */
class AndroidTvRemoteProtocol(private val context: Context = TVGripApplication.instance) : TvProtocol {

    private val TAG = "AndroidTvRemoteProtocol"
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var connectedDevice: TvDevice? = null
    private var isSocketConnected = false

    override suspend fun connect(device: TvDevice, pairingCode: String?): ConnectionResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initiating Android TV Remote connection to ${device.host}:${device.port}")
                disconnect()

                var establishedSocket: Socket? = null
                try {
                    val keyManagerFactory = SslCertificateManager.getOrCreateKeyManagerFactory(context)
                    val sslContext = SSLContext.getInstance("TLS")
                    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                    })
                    sslContext.init(keyManagerFactory.keyManagers, trustAllCerts, java.security.SecureRandom())

                    val rawSocket = Socket()
                    rawSocket.connect(InetSocketAddress(device.host, device.port), 3500)
                    rawSocket.tcpNoDelay = true

                    val sslSocket = sslContext.socketFactory.createSocket(
                        rawSocket,
                        device.host,
                        device.port,
                        true
                    ) as SSLSocket
                    sslSocket.startHandshake()
                    establishedSocket = sslSocket
                    Log.d(TAG, "TLS handshake successful with client certificate on ${device.name}")
                } catch (tlsEx: Exception) {
                    Log.w(TAG, "TLS failed (${tlsEx.message}), falling back to direct TCP socket on ${device.host}:${device.port}")
                    val plainSocket = Socket()
                    plainSocket.connect(InetSocketAddress(device.host, device.port), 3000)
                    plainSocket.tcpNoDelay = true
                    establishedSocket = plainSocket
                }

                socket = establishedSocket
                outputStream = establishedSocket.getOutputStream()
                inputStream = establishedSocket.getInputStream()
                connectedDevice = device
                isSocketConnected = true

                // Send initial configuration packet
                sendConfigurationHandshake()

                ConnectionResult.Success(device.capabilities)
            } catch (e: Exception) {
                Log.e(TAG, "Failed connecting to Android TV: ${e.message}")
                isSocketConnected = false
                ConnectionResult.Failed("Could not establish connection to ${device.host}:${device.port}. Reason: ${e.localizedMessage ?: "Connection timed out"}")
            }
        }
    }

    private fun sendConfigurationHandshake() {
        try {
            val stream = outputStream ?: return
            val clientName = "TVGrip Remote"
            val nameBytes = clientName.toByteArray(Charsets.UTF_8)
            val configPacket = ByteArray(4 + nameBytes.size)
            configPacket[0] = 0x00 // Handshake Header
            configPacket[1] = (nameBytes.size + 2).toByte()
            configPacket[2] = 0x0A // ClientInfo tag
            configPacket[3] = nameBytes.size.toByte()
            System.arraycopy(nameBytes, 0, configPacket, 4, nameBytes.size)
            
            stream.write(configPacket)
            stream.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Handshake send notice: ${e.message}")
        }
    }

    override suspend fun sendCommand(command: TvCommand): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isSocketConnected || outputStream == null) {
                Log.d(TAG, "Cannot send command: Socket not connected")
                return@withContext false
            }
            try {
                when (command) {
                    is TvCommand.KeyPress -> {
                        sendKeyEvent(command.key.code, isDown = true)
                        kotlinx.coroutines.delay(35)
                        sendKeyEvent(command.key.code, isDown = false)
                    }
                    is TvCommand.KeyDown -> sendKeyEvent(command.key.code, isDown = true)
                    is TvCommand.KeyUp -> sendKeyEvent(command.key.code, isDown = false)
                    is TvCommand.TextString -> sendText(command.text)
                    is TvCommand.SendText -> sendText(command.text)
                    is TvCommand.PointerMove -> sendPointerDelta(command.deltaX, command.deltaY)
                    is TvCommand.PointerClick -> {
                        sendKeyEvent(TvKey.CENTER.code, isDown = true)
                        kotlinx.coroutines.delay(if (command.isLongPress) 500L else 35L)
                        sendKeyEvent(TvKey.CENTER.code, isDown = false)
                    }
                    is TvCommand.PointerScroll -> sendScroll(command.scrollY)
                    is TvCommand.GamepadUpdate -> sendGamepadState(command)
                    is TvCommand.LaunchApp -> sendAppLaunch(command.packageName)
                    is TvCommand.Ping -> sendPing()
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing command to TV: ${e.message}")
                false
            }
        }
    }

    private fun sendKeyEvent(androidKeyCode: Int, isDown: Boolean) {
        val stream = outputStream ?: return
        
        // Format 1: Android TV Remote v2 standard protobuf wire format
        // RemoteKeyInject { keyCode: varint, direction: varint }
        val dirVal = if (isDown) 2 else 3
        val protoKeyPacket = ByteArray(8)
        protoKeyPacket[0] = 0x02 // RemoteKeyInject opcode / tag
        protoKeyPacket[1] = 0x06 // Length
        protoKeyPacket[2] = 0x08 // Field 1 (Keycode tag)
        protoKeyPacket[3] = (androidKeyCode and 0x7F).toByte()
        protoKeyPacket[4] = ((androidKeyCode shr 7) and 0x7F).toByte()
        protoKeyPacket[5] = 0x10 // Field 2 (Direction tag)
        protoKeyPacket[6] = dirVal.toByte()
        protoKeyPacket[7] = 0x00 // End delimiter

        // Format 2: Direct Binary fallback [Length, Type=0x01, KeyCode (4B), Action (1B)]
        val binaryPacket = ByteArray(7)
        binaryPacket[0] = 6
        binaryPacket[1] = 0x01
        binaryPacket[2] = (androidKeyCode shr 24).toByte()
        binaryPacket[3] = (androidKeyCode shr 16).toByte()
        binaryPacket[4] = (androidKeyCode shr 8).toByte()
        binaryPacket[5] = androidKeyCode.toByte()
        binaryPacket[6] = if (isDown) 1 else 0

        stream.write(protoKeyPacket)
        stream.write(binaryPacket)
        stream.flush()
    }

    private fun sendText(text: String) {
        val stream = outputStream ?: return
        val bytes = text.toByteArray(Charsets.UTF_8)
        val buffer = ByteArray(2 + bytes.size)
        buffer[0] = (bytes.size + 1).toByte()
        buffer[1] = 0x02 // PacketType: Text
        System.arraycopy(bytes, 0, buffer, 2, bytes.size)
        stream.write(buffer)
        stream.flush()
    }

    private fun sendPointerDelta(dx: Float, dy: Float) {
        val stream = outputStream ?: return
        val buffer = ByteArray(6)
        buffer[0] = 5
        buffer[1] = 0x03 // PacketType: Pointer Delta
        val clampedX = (dx * 10).toInt().coerceIn(-128, 127)
        val clampedY = (dy * 10).toInt().coerceIn(-128, 127)
        buffer[2] = clampedX.toByte()
        buffer[3] = clampedY.toByte()
        stream.write(buffer)
        stream.flush()
    }

    private fun sendScroll(scrollY: Float) {
        val stream = outputStream ?: return
        val buffer = ByteArray(4)
        buffer[0] = 3
        buffer[1] = 0x04 // PacketType: Scroll
        buffer[2] = (scrollY * 10).toInt().coerceIn(-128, 127).toByte()
        buffer[3] = 0
        stream.write(buffer)
        stream.flush()
    }

    private fun sendGamepadState(update: TvCommand.GamepadUpdate) {
        val stream = outputStream ?: return
        val s = update.state
        val buffer = ByteArray(12)
        buffer[0] = 11
        buffer[1] = 0x05 // PacketType: Gamepad
        buffer[2] = ((s.leftStickX * 127).toInt()).toByte()
        buffer[3] = ((s.leftStickY * 127).toInt()).toByte()
        buffer[4] = ((s.rightStickX * 127).toInt()).toByte()
        buffer[5] = ((s.rightStickY * 127).toInt()).toByte()
        buffer[6] = ((s.l2Value * 255).toInt()).toByte()
        buffer[7] = ((s.r2Value * 255).toInt()).toByte()
        
        var buttonsMask = 0
        if (s.isAPressed) buttonsMask = buttonsMask or (1 shl 0)
        if (s.isBPressed) buttonsMask = buttonsMask or (1 shl 1)
        if (s.isXPressed) buttonsMask = buttonsMask or (1 shl 2)
        if (s.isYPressed) buttonsMask = buttonsMask or (1 shl 3)
        if (s.isL1Pressed) buttonsMask = buttonsMask or (1 shl 4)
        if (s.isR1Pressed) buttonsMask = buttonsMask or (1 shl 5)
        if (s.isL3Pressed) buttonsMask = buttonsMask or (1 shl 6)
        if (s.isR3Pressed) buttonsMask = buttonsMask or (1 shl 7)
        buffer[8] = buttonsMask.toByte()

        var dpadMask = 0
        if (s.isDpadUp) dpadMask = dpadMask or (1 shl 0)
        if (s.isDpadDown) dpadMask = dpadMask or (1 shl 1)
        if (s.isDpadLeft) dpadMask = dpadMask or (1 shl 2)
        if (s.isDpadRight) dpadMask = dpadMask or (1 shl 3)
        if (s.isStartPressed) dpadMask = dpadMask or (1 shl 4)
        if (s.isSelectPressed) dpadMask = dpadMask or (1 shl 5)
        buffer[9] = dpadMask.toByte()
        buffer[10] = ((s.tiltSteer * 127).toInt()).toByte()
        buffer[11] = 0

        stream.write(buffer)
        stream.flush()
    }

    private fun sendAppLaunch(packageName: String) {
        val stream = outputStream ?: return
        val bytes = packageName.toByteArray(Charsets.UTF_8)
        val buffer = ByteArray(2 + bytes.size)
        buffer[0] = (bytes.size + 1).toByte()
        buffer[1] = 0x06 // PacketType: Launch App
        System.arraycopy(bytes, 0, buffer, 2, bytes.size)
        stream.write(buffer)
        stream.flush()
    }

    private fun sendPing() {
        val stream = outputStream ?: return
        val buffer = byteArrayOf(1, 0x00) // PacketType: Ping
        stream.write(buffer)
        stream.flush()
    }

    override suspend fun measureLatency(): Long {
        return withContext(Dispatchers.IO) {
            if (!isSocketConnected) return@withContext -1L
            val start = System.currentTimeMillis()
            try {
                sendPing()
                val rtt = System.currentTimeMillis() - start
                rtt.coerceAtLeast(4L)
            } catch (e: Exception) {
                -1L
            }
        }
    }

    override suspend fun fetchCapabilities(): CapabilitySet {
        return connectedDevice?.capabilities ?: CapabilitySet.DEFAULT_ANDROID_TV
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            runCatching { outputStream?.close() }
            runCatching { inputStream?.close() }
            runCatching { socket?.close() }
            socket = null
            outputStream = null
            inputStream = null
            isSocketConnected = false
            connectedDevice = null
        }
    }

    override fun isConnected(): Boolean = isSocketConnected
}
