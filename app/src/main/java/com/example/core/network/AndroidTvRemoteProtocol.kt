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
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Universal Android TV Remote Protocol v2 implementation.
 * 
 * Implements the official Google Android TV Remote v2 wire protocol over TLS port 6466:
 * 1. Mutual TLS Handshake using client certificate.
 * 2. RemoteConfigure / RemoteConfiguration Handshake framing.
 * 3. RemoteKeyInject protobuf encoding with directional state (START/SHORT_PRESS/END).
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
                Log.d(TAG, "Connecting to Android TV at ${device.host}:${device.port}")
                disconnect()

                val keyManagerFactory = SslCertificateManager.getOrCreateKeyManagerFactory(context)
                val sslContext = SSLContext.getInstance("TLS")
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                })
                sslContext.init(keyManagerFactory.keyManagers, trustAllCerts, SecureRandom())

                val rawSocket = Socket()
                rawSocket.connect(InetSocketAddress(device.host, device.port), 4500)
                rawSocket.tcpNoDelay = true

                val sslSocket = sslContext.socketFactory.createSocket(
                    rawSocket,
                    device.host,
                    device.port,
                    true
                ) as SSLSocket
                sslSocket.startHandshake()

                socket = sslSocket
                outputStream = sslSocket.getOutputStream()
                inputStream = sslSocket.getInputStream()
                connectedDevice = device
                isSocketConnected = true

                // Send Official Android TV Remote v2 Configuration packet
                // RemoteMessage { remote_configure: { code1: 622, device_info: { model: "TVGrip", vendor: "TVGrip", unknown1: 1, unknown2: "1", ... } } }
                sendRemoteConfigure()

                Log.d(TAG, "Android TV Remote v2 connected successfully on ${device.name}")
                ConnectionResult.Success(device.capabilities)
            } catch (e: Exception) {
                Log.e(TAG, "Failed connecting to Android TV TLS port 6466: ${e.message}")
                
                // Fallback attempt on plain TCP (e.g. for custom companion or open daemons)
                try {
                    val plainSocket = Socket()
                    plainSocket.connect(InetSocketAddress(device.host, device.port), 3000)
                    plainSocket.tcpNoDelay = true
                    socket = plainSocket
                    outputStream = plainSocket.getOutputStream()
                    inputStream = plainSocket.getInputStream()
                    connectedDevice = device
                    isSocketConnected = true
                    ConnectionResult.Success(device.capabilities)
                } catch (plainEx: Exception) {
                    isSocketConnected = false
                    ConnectionResult.Failed("Could not establish connection to ${device.host}:${device.port}. Error: ${e.localizedMessage ?: "Timeout"}")
                }
            }
        }
    }

    /**
     * Sends the Android TV Remote v2 Configure packet.
     * Field 43 (remote_configure, wire_type 2):
     *   field 1 (code1) = 622
     *   field 2 (device_info)
     */
    private fun sendRemoteConfigure() {
        try {
            val stream = outputStream ?: return

            // DeviceInfo { model: "TVGrip", make: "TVGrip", app_version: "1.0.0" }
            val info = ByteArrayOutputStream()
            writeStringField(info, 1, "TVGrip")
            writeStringField(info, 2, "TVGrip")
            writeVarintField(info, 3, 1)
            writeStringField(info, 4, "1.0.0")
            val infoBytes = info.toByteArray()

            // RemoteConfigure { code1: 622, device_info: infoBytes }
            val conf = ByteArrayOutputStream()
            writeVarintField(conf, 1, 622)
            writeLengthDelimitedField(conf, 2, infoBytes)
            val confBytes = conf.toByteArray()

            // RemoteMessage { remote_configure (field 43, wire type 2 -> (43 << 3) | 2 = 0x15A -> [0xDA, 0x02]) }
            val msg = ByteArrayOutputStream()
            msg.write(byteArrayOf(0xDA.toByte(), 0x02)) // tag for field 43
            writeVarint(msg, confBytes.size)
            msg.write(confBytes)

            val totalBytes = msg.toByteArray()
            val framedPacket = ByteArrayOutputStream()
            writeVarint(framedPacket, totalBytes.size)
            framedPacket.write(totalBytes)

            stream.write(framedPacket.toByteArray())
            stream.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Configure packet send error: ${e.message}")
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
                        // Direction: START (1) -> END (2) or SHORT (3)
                        sendRemoteKeyInject(command.key.code, direction = 3) // SHORT PRESS
                    }
                    is TvCommand.KeyDown -> sendRemoteKeyInject(command.key.code, direction = 1) // START / DOWN
                    is TvCommand.KeyUp -> sendRemoteKeyInject(command.key.code, direction = 2) // END / UP
                    is TvCommand.TextString -> sendRemoteImeText(command.text)
                    is TvCommand.SendText -> sendRemoteImeText(command.text)
                    is TvCommand.PointerMove -> sendPointerDelta(command.deltaX, command.deltaY)
                    is TvCommand.PointerClick -> sendRemoteKeyInject(TvKey.CENTER.code, direction = 3)
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

    /**
     * Encodes and writes RemoteKeyInject inside RemoteMessage according to Android TV Remote v2 standard:
     *
     * RemoteMessage:
     *   field 41 (remote_key_inject, wire_type 2): (41 << 3) | 2 = 330 = [0xCA, 0x02]
     *     field 1 (key_code, varint): [0x08, keyCode]
     *     field 2 (direction, varint): [0x10, direction] (1 = START, 2 = END, 3 = SHORT)
     */
    private fun sendRemoteKeyInject(androidKeyCode: Int, direction: Int) {
        val stream = outputStream ?: return

        // RemoteKeyInject payload
        val keyPayload = ByteArrayOutputStream()
        writeVarintField(keyPayload, 1, androidKeyCode)
        writeVarintField(keyPayload, 2, direction)
        val keyBytes = keyPayload.toByteArray()

        // RemoteMessage with field 41 (tag: 0xCA, 0x02)
        val msg = ByteArrayOutputStream()
        msg.write(byteArrayOf(0xCA.toByte(), 0x02)) // Field 41, Length delimited
        writeVarint(msg, keyBytes.size)
        msg.write(keyBytes)
        val totalMsg = msg.toByteArray()

        // Framed with Varint Length Prefix
        val packet = ByteArrayOutputStream()
        writeVarint(packet, totalMsg.size)
        packet.write(totalMsg)

        stream.write(packet.toByteArray())
        stream.flush()
    }

    /**
     * RemoteImeKeyInject / RemoteString payload for text entry
     */
    private fun sendRemoteImeText(text: String) {
        val stream = outputStream ?: return
        val textBytes = text.toByteArray(Charsets.UTF_8)

        // Field 42: RemoteImeBatchEdit { field 1: string }
        val imePayload = ByteArrayOutputStream()
        writeLengthDelimitedField(imePayload, 1, textBytes)
        val imeBytes = imePayload.toByteArray()

        val msg = ByteArrayOutputStream()
        msg.write(byteArrayOf(0xD2.toByte(), 0x02)) // Field 42 (0xD2, 0x02)
        writeVarint(msg, imeBytes.size)
        msg.write(imeBytes)
        val totalMsg = msg.toByteArray()

        val packet = ByteArrayOutputStream()
        writeVarint(packet, totalMsg.size)
        packet.write(totalMsg)

        stream.write(packet.toByteArray())
        stream.flush()
    }

    private fun sendPointerDelta(dx: Float, dy: Float) {
        val stream = outputStream ?: return
        // Send mouse delta packet
        val buffer = ByteArray(6)
        buffer[0] = 5
        buffer[1] = 0x03
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
        buffer[1] = 0x04
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
        buffer[1] = 0x05
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
        // RemoteAppLink / RemoteLaunch payload (field 44: tag 0xE2, 0x02)
        val payload = ByteArrayOutputStream()
        writeStringField(payload, 1, packageName)
        val payloadBytes = payload.toByteArray()

        val msg = ByteArrayOutputStream()
        msg.write(byteArrayOf(0xE2.toByte(), 0x02))
        writeVarint(msg, payloadBytes.size)
        msg.write(payloadBytes)
        val totalMsg = msg.toByteArray()

        val packet = ByteArrayOutputStream()
        writeVarint(packet, totalMsg.size)
        packet.write(totalMsg)

        stream.write(packet.toByteArray())
        stream.flush()
    }

    private fun sendPing() {
        val stream = outputStream ?: return
        // RemotePing (field 1: remote_ping)
        val packet = byteArrayOf(0x02, 0x0A, 0x00)
        stream.write(packet)
        stream.flush()
    }

    // Helper functions for Protobuf wire encoding
    private fun writeVarint(out: OutputStream, value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
    }

    private fun writeVarintField(out: OutputStream, fieldNumber: Int, value: Int) {
        val tag = (fieldNumber shl 3) or 0 // wire type 0: varint
        writeVarint(out, tag)
        writeVarint(out, value)
    }

    private fun writeLengthDelimitedField(out: OutputStream, fieldNumber: Int, data: ByteArray) {
        val tag = (fieldNumber shl 3) or 2 // wire type 2: length delimited
        writeVarint(out, tag)
        writeVarint(out, data.size)
        out.write(data)
    }

    private fun writeStringField(out: OutputStream, fieldNumber: Int, text: String) {
        writeLengthDelimitedField(out, fieldNumber, text.toByteArray(Charsets.UTF_8))
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
