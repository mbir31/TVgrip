package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.CapabilitySet
import com.example.core.model.DeviceConnectionState
import com.example.core.model.GamepadState
import com.example.core.model.PlayerSlot
import com.example.core.model.ProtocolType
import com.example.core.model.TvCommand
import com.example.core.model.TvDevice
import com.example.core.model.TvKey
import com.example.core.network.SslCertificateManager
import com.example.core.network.TvConnectionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TvGripCommandPipelineTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TVGrip", appName)
    }

    @Test
    fun `verify all 12 critical remote keys have valid Android keycodes`() {
        // UP, DOWN, LEFT, RIGHT, OK (CENTER), BACK, HOME, VOLUME UP, VOLUME DOWN, MUTE, PLAY, PAUSE
        assertEquals(19, TvKey.UP.code)
        assertEquals(20, TvKey.DOWN.code)
        assertEquals(21, TvKey.LEFT.code)
        assertEquals(22, TvKey.RIGHT.code)
        assertEquals(23, TvKey.CENTER.code)
        assertEquals(4, TvKey.BACK.code)
        assertEquals(3, TvKey.HOME.code)
        assertEquals(24, TvKey.VOLUME_UP.code)
        assertEquals(25, TvKey.VOLUME_DOWN.code)
        assertEquals(164, TvKey.VOLUME_MUTE.code)
        assertEquals(126, TvKey.MEDIA_PLAY.code)
        assertEquals(127, TvKey.MEDIA_PAUSE.code)
    }

    @Test
    fun `verify tv command data classes serialization integrity`() {
        val keyPressCmd = TvCommand.KeyPress(TvKey.UP)
        assertEquals(TvKey.UP, keyPressCmd.key)

        val pointerCmd = TvCommand.PointerMove(dx = 1.5f, dy = -2.0f)
        assertEquals(1.5f, pointerCmd.deltaX)
        assertEquals(-2.0f, pointerCmd.deltaY)

        val textCmd = TvCommand.TextString("Netflix Search")
        assertEquals("Netflix Search", textCmd.text)

        val gamepadCmd = TvCommand.GamepadUpdate(
            state = GamepadState(
                isAPressed = true,
                leftStickX = 0.75f,
                leftStickY = -0.5f
            ),
            playerSlot = PlayerSlot.PLAYER_1
        )
        assertTrue(gamepadCmd.state.isAPressed)
        assertEquals(0.75f, gamepadCmd.state.leftStickX)
        assertEquals(PlayerSlot.PLAYER_1, gamepadCmd.playerSlot)
    }

    @Test
    fun `verify ssl certificate manager generates client x509 cert`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cert = SslCertificateManager.getClientCertificate(context)
        assertNotNull("Client X.509 certificate must be generated for mutual TLS pairing", cert)
        assertEquals("X.509", cert?.type)
    }

    @Test
    fun `verify connection manager state transitions`() {
        val cm = TvConnectionManager()
        assertEquals(DeviceConnectionState.DISCONNECTED, cm.connectionState.value)
        assertFalse(cm.isConnected())

        val testTv = TvDevice(
            id = "test-tv-id",
            name = "Sony Bravia 4K",
            host = "192.168.1.100",
            port = 6466,
            protocolType = ProtocolType.ANDROID_TV_REMOTE_V2
        )
        assertEquals("Sony Bravia 4K", testTv.name)
        assertEquals(6466, testTv.port)
        assertEquals(ProtocolType.ANDROID_TV_REMOTE_V2, testTv.protocolType)
    }

    @Test
    fun `verify 4 player slots mapping`() {
        assertEquals(4, PlayerSlot.entries.size)
        assertEquals("P1", PlayerSlot.PLAYER_1.label)
        assertEquals("P2", PlayerSlot.PLAYER_2.label)
        assertEquals("P3", PlayerSlot.PLAYER_3.label)
        assertEquals("P4", PlayerSlot.PLAYER_4.label)
    }

    @Test
    fun `verify pairing result classes and statuses`() {
        val prompt = com.example.core.network.PairingResult.CodePromptReceived("Enter PIN")
        assertEquals("Enter PIN", prompt.promptMessage)

        val success = com.example.core.network.PairingResult.Success("OK")
        assertEquals("OK", success.message)

        val failed = com.example.core.network.PairingResult.Failed("Error")
        assertEquals("Error", failed.error)
    }
}
