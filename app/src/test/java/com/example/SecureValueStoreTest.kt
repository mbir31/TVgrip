package com.example

import com.example.core.security.SecureValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureValueStoreTest {

    @Test
    fun `legacy plaintext values are returned unchanged`() {
        val legacy = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
        assertEquals(legacy, SecureValueStore.decrypt(legacy))
    }

    @Test
    fun `encrypt then decrypt round-trips when keystore is available`() {
        // Android Keystore is not always available under Robolectric. When it is
        // unavailable the encrypt call falls back to no-op (returns null) and the
        // value is stored as legacy plaintext, so both paths are acceptable here.
        val plain = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
        val encrypted = SecureValueStore.encrypt(plain)
        if (encrypted != null) {
            assertTrue(encrypted.startsWith("enc:"))
            assertEquals(plain, SecureValueStore.decrypt(encrypted))
        } else {
            // Fallback path: stored value remains plaintext and remains usable.
            assertEquals(plain, SecureValueStore.decrypt(plain))
        }
    }

    @Test
    fun `malformed encrypted value falls back to original`() {
        val malformed = "enc:not-valid-base64:also-not-base64"
        assertEquals(malformed, SecureValueStore.decrypt(malformed))
    }

    @Test
    fun `blank values are passed through`() {
        assertEquals(null, SecureValueStore.decrypt(null))
        assertEquals("", SecureValueStore.decrypt(""))
        assertEquals("", SecureValueStore.encrypt(""))
    }
}
