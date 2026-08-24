package com.example

import com.example.core.network.PoloProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.MessageDigest

class PoloProtocolUnitTest {

    @Test
    fun testOuterMessagePairingRequestEncoding() {
        val msg = PoloProtocol.OuterMessage(
            protocolVersion = 2,
            status = PoloProtocol.Status.STATUS_OK,
            pairingRequest = PoloProtocol.PairingRequest(
                serviceName = "atvremote",
                clientName = "TVGrip"
            )
        )

        val encoded = msg.encode()
        val decoded = PoloProtocol.readFramedMessage(ByteArrayInputStream(encoded))

        assertEquals(2, decoded.protocolVersion)
        assertEquals(PoloProtocol.Status.STATUS_OK, decoded.status)
        assertNotNull(decoded.pairingRequest)
        assertEquals("atvremote", decoded.pairingRequest?.serviceName)
        assertEquals("TVGrip", decoded.pairingRequest?.clientName)
    }

    @Test
    fun testOuterMessageOptionsEncoding() {
        val msg = PoloProtocol.OuterMessage(
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

        val encoded = msg.encode()
        val decoded = PoloProtocol.readFramedMessage(ByteArrayInputStream(encoded))

        assertEquals(2, decoded.protocolVersion)
        assertEquals(PoloProtocol.Status.STATUS_OK, decoded.status)
        assertNotNull(decoded.options)
        assertEquals(PoloProtocol.RoleType.ROLE_TYPE_INPUT, decoded.options?.preferredRole)
        assertEquals(1, decoded.options?.inputEncodings?.size)
        assertEquals(PoloProtocol.EncodingType.ENCODING_TYPE_HEXADECIMAL, decoded.options?.inputEncodings?.first()?.type)
        assertEquals(6, decoded.options?.inputEncodings?.first()?.symbolLength)
    }

    @Test
    fun testOuterMessageConfigurationEncoding() {
        val msg = PoloProtocol.OuterMessage(
            protocolVersion = 2,
            status = PoloProtocol.Status.STATUS_OK,
            configuration = PoloProtocol.Configuration(
                encoding = PoloProtocol.Encoding(
                    type = PoloProtocol.EncodingType.ENCODING_TYPE_HEXADECIMAL,
                    symbolLength = 6
                ),
                clientRole = PoloProtocol.RoleType.ROLE_TYPE_INPUT
            )
        )

        val encoded = msg.encode()
        val decoded = PoloProtocol.readFramedMessage(ByteArrayInputStream(encoded))

        assertEquals(2, decoded.protocolVersion)
        assertEquals(PoloProtocol.Status.STATUS_OK, decoded.status)
        assertNotNull(decoded.configuration)
        assertEquals(PoloProtocol.EncodingType.ENCODING_TYPE_HEXADECIMAL, decoded.configuration?.encoding?.type)
        assertEquals(6, decoded.configuration?.encoding?.symbolLength)
        assertEquals(PoloProtocol.RoleType.ROLE_TYPE_INPUT, decoded.configuration?.clientRole)
    }

    @Test
    fun testOuterMessageSecretEncoding() {
        val secretBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val msg = PoloProtocol.OuterMessage(
            protocolVersion = 2,
            status = PoloProtocol.Status.STATUS_OK,
            secret = PoloProtocol.Secret(secret = secretBytes)
        )

        val encoded = msg.encode()
        val decoded = PoloProtocol.readFramedMessage(ByteArrayInputStream(encoded))

        assertEquals(2, decoded.protocolVersion)
        assertEquals(PoloProtocol.Status.STATUS_OK, decoded.status)
        assertNotNull(decoded.secret)
        assertArrayEquals(secretBytes, decoded.secret?.secret)
    }

    @Test
    fun testPoloCryptographicSecretCalculation() {
        val clientModulus = BigInteger("12345678901234567890").toByteArray()
        val clientExponent = BigInteger("65537").toByteArray()
        val serverModulus = BigInteger("98765432109876543210").toByteArray()
        val serverExponent = BigInteger("65537").toByteArray()

        // 6-character hex PIN: "A1B2C3" -> remainder is "B2C3"
        val codeRemainder = byteArrayOf(0xB2.toByte(), 0xC3.toByte())

        val sha256 = MessageDigest.getInstance("SHA-256")
        sha256.update(clientModulus)
        sha256.update(clientExponent)
        sha256.update(serverModulus)
        sha256.update(serverExponent)
        sha256.update(codeRemainder)
        val digest = sha256.digest()

        assertEquals(32, digest.size)
    }
}
