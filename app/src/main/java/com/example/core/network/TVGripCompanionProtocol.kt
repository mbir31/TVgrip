package com.example.core.network

import android.util.Log
import com.example.core.model.CapabilitySet
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class TVGripCompanionProtocol : TvProtocol {

    private val TAG = "TVGripCompanionProtocol"
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isSocketConnected = false
    private var connectedDevice: TvDevice? = null

    override suspend fun connect(device: TvDevice, pairingCode: String?): ConnectionResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Connecting to TVGrip Companion Service on ${device.host}:${device.port}")
                disconnect()

                val s = Socket()
                s.connect(InetSocketAddress(device.host, device.port), 3000)
                s.tcpNoDelay = true

                socket = s
                outputStream = s.getOutputStream()
                inputStream = s.getInputStream()
                isSocketConnected = true
                connectedDevice = device

                Log.d(TAG, "Connected to TVGrip Companion successfully!")
                ConnectionResult.Success(CapabilitySet.FULLY_FEATURED)
            } catch (e: Exception) {
                Log.e(TAG, "Failed connecting to companion: ${e.message}")
                isSocketConnected = false
                ConnectionResult.Failed("Could not connect to TVGrip TV Companion service at ${device.host}:${device.port}. Reason: ${e.localizedMessage ?: "Connection refused"}")
            }
        }
    }

    override suspend fun sendCommand(command: TvCommand): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isSocketConnected || outputStream == null) return@withContext false
            try {
                // TVGrip Companion binary packet structure: [OpCode (1 byte)][Payload Length (2 bytes)][Payload]
                val stream = outputStream ?: return@withContext false
                when (command) {
                    is TvCommand.GamepadUpdate -> {
                        val s = command.state
                        val slot = command.playerSlot
                        val buffer = ByteArray(17)
                        buffer[0] = 0x10 // OP_GAMEPAD
                        buffer[1] = 0
                        buffer[2] = 14 // 14 bytes payload
                        buffer[3] = slot.slotIndex.toByte() // Player Index (0=P1, 1=P2, 2=P3, 3=P4)
                        buffer[4] = ((s.leftStickX * 127).toInt()).toByte()
                        buffer[5] = ((s.leftStickY * 127).toInt()).toByte()
                        buffer[6] = ((s.rightStickX * 127).toInt()).toByte()
                        buffer[7] = ((s.rightStickY * 127).toInt()).toByte()
                        buffer[8] = ((s.l2Value * 255).toInt()).toByte()
                        buffer[9] = ((s.r2Value * 255).toInt()).toByte()

                        var btn1 = 0
                        if (s.isAPressed) btn1 = btn1 or (1 shl 0)
                        if (s.isBPressed) btn1 = btn1 or (1 shl 1)
                        if (s.isXPressed) btn1 = btn1 or (1 shl 2)
                        if (s.isYPressed) btn1 = btn1 or (1 shl 3)
                        if (s.isL1Pressed) btn1 = btn1 or (1 shl 4)
                        if (s.isR1Pressed) btn1 = btn1 or (1 shl 5)
                        if (s.isL3Pressed) btn1 = btn1 or (1 shl 6)
                        if (s.isR3Pressed) btn1 = btn1 or (1 shl 7)
                        buffer[10] = btn1.toByte()

                        var btn2 = 0
                        if (s.isDpadUp) btn2 = btn2 or (1 shl 0)
                        if (s.isDpadDown) btn2 = btn2 or (1 shl 1)
                        if (s.isDpadLeft) btn2 = btn2 or (1 shl 2)
                        if (s.isDpadRight) btn2 = btn2 or (1 shl 3)
                        if (s.isStartPressed) btn2 = btn2 or (1 shl 4)
                        if (s.isSelectPressed) btn2 = btn2 or (1 shl 5)
                        buffer[11] = btn2.toByte()

                        buffer[12] = ((s.tiltSteer * 127).toInt()).toByte()
                        buffer[13] = ((s.throttle * 255).toInt()).toByte()
                        buffer[14] = ((s.brake * 255).toInt()).toByte()
                        buffer[15] = if (s.handbrake) 1 else 0
                        buffer[16] = s.gear.toByte()

                        stream.write(buffer)
                        stream.flush()
                    }
                    is TvCommand.PointerMove -> {
                        val buffer = ByteArray(7)
                        buffer[0] = 0x20 // OP_AIR_MOUSE
                        buffer[1] = 0
                        buffer[2] = 4
                        val dx = (command.deltaX * 10).toInt().coerceIn(-128, 127)
                        val dy = (command.deltaY * 10).toInt().coerceIn(-128, 127)
                        buffer[3] = dx.toByte()
                        buffer[4] = dy.toByte()
                        buffer[5] = 0
                        buffer[6] = 0
                        stream.write(buffer)
                        stream.flush()
                    }
                    is TvCommand.KeyPress -> {
                        val buffer = ByteArray(7)
                        buffer[0] = 0x01 // OP_KEY
                        buffer[1] = 0
                        buffer[2] = 4
                        buffer[3] = (command.key.code shr 8).toByte()
                        buffer[4] = command.key.code.toByte()
                        buffer[5] = 1 // down
                        buffer[6] = 0
                        stream.write(buffer)
                        kotlinx.coroutines.delay(20)
                        buffer[5] = 0 // up
                        stream.write(buffer)
                        stream.flush()
                    }
                    is TvCommand.TextString -> {
                        val textBytes = command.text.toByteArray(Charsets.UTF_8)
                        val buffer = ByteArray(3 + textBytes.size)
                        buffer[0] = 0x02 // OP_TEXT
                        buffer[1] = (textBytes.size shr 8).toByte()
                        buffer[2] = textBytes.size.toByte()
                        System.arraycopy(textBytes, 0, buffer, 3, textBytes.size)
                        stream.write(buffer)
                        stream.flush()
                    }
                    is TvCommand.SendText -> {
                        val textBytes = command.text.toByteArray(Charsets.UTF_8)
                        val buffer = ByteArray(3 + textBytes.size)
                        buffer[0] = 0x02 // OP_TEXT
                        buffer[1] = (textBytes.size shr 8).toByte()
                        buffer[2] = textBytes.size.toByte()
                        System.arraycopy(textBytes, 0, buffer, 3, textBytes.size)
                        stream.write(buffer)
                        stream.flush()
                    }
                    is TvCommand.Ping -> {
                        val buffer = byteArrayOf(0x00, 0, 0)
                        stream.write(buffer)
                        stream.flush()
                    }
                    else -> {}
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error writing companion command: ${e.message}")
                false
            }
        }
    }

    override suspend fun measureLatency(): Long {
        return withContext(Dispatchers.IO) {
            if (!isSocketConnected) return@withContext -1L
            val start = System.currentTimeMillis()
            try {
                sendCommand(TvCommand.Ping)
                (System.currentTimeMillis() - start).coerceAtLeast(3L)
            } catch (e: Exception) {
                -1L
            }
        }
    }

    override suspend fun fetchCapabilities(): CapabilitySet {
        return CapabilitySet.FULLY_FEATURED
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
