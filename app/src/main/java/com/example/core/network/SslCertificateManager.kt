package com.example.core.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory

/**
 * Manages the persistent self-signed RSA-2048 client certificate used for the
 * Android TV Remote v2 mutual-TLS handshake.
 *
 * On physical Android devices the private key is generated and kept inside
 * Android Keystore (non-exportable, encrypted at rest). The certificate chain
 * is stored in the same Android Keystore alias and is reused across app
 * restarts: the Android TV binds the pairing to the certificate's RSA
 * modulus/exponent, so reconnecting requires the exact same identity.
 *
 * Robolectric does not provide the AndroidKeyStore provider, so tests fall back
 * to an in-memory JVM identity that is never written to disk.
 */
object SslCertificateManager {

    private const val TAG = "SslCertificateManager"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "tvgrip_atvremote"
    private const val CLIENT_NAME = "TVGrip"

    private const val PREFS_NAME = "tvgrip_ssl_credentials"
    private const val PREF_KEY_CERT = "ssl_client_certificate"
    private const val PREF_KEY_PRIV_LEGACY = "ssl_client_private_key"

    private val lock = Any()

    @Volatile
    private var cachedKeyManagerFactory: KeyManagerFactory? = null
    @Volatile
    private var cachedClientCertificate: X509Certificate? = null
    @Volatile
    private var cachedPrivateKey: PrivateKey? = null

    // Robolectric/JVM fallback identity (never persisted).
    @Volatile
    private var fallbackKeyPair: KeyPairHolder? = null

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Synchronized
    fun getOrCreateKeyManagerFactory(context: Context): KeyManagerFactory {
        synchronized(lock) {
            cachedKeyManagerFactory?.let { return it }
            ensureIdentity(context)

            val cert = cachedClientCertificate ?: error("Client certificate is not initialized")
            val privateKey = cachedPrivateKey

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())

            val androidKeyStore = try {
                KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null, null) }
            } catch (e: Exception) {
                null
            }

            if (androidKeyStore != null) {
                // The provider already contains the key+chain; use it directly.
                try {
                    if (privateKey != null && !androidKeyStore.containsAlias(ALIAS)) {
                        androidKeyStore.setKeyEntry(
                            ALIAS,
                            privateKey,
                            null,
                            arrayOf(cert)
                        )
                    }
                    kmf.init(androidKeyStore, null)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not init KeyManagerFactory from AndroidKeyStore. Falling back to in-memory.", e)
                    kmf.init(javaKeyStore(cert, privateKey), charArrayOf())
                }
            } else {
                kmf.init(javaKeyStore(cert, privateKey), charArrayOf())
            }

            cachedKeyManagerFactory = kmf
            return kmf
        }
    }

    private fun javaKeyStore(cert: X509Certificate, privateKey: PrivateKey?): KeyStore {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        if (privateKey != null) {
            ks.setKeyEntry(ALIAS, privateKey, CHAR_PASSWORD, arrayOf(cert))
        } else {
            ks.setCertificateEntry(ALIAS, cert)
        }
        return ks
    }

    private val CHAR_PASSWORD = CharArray(0)

    fun getClientCertificate(context: Context): X509Certificate? {
        synchronized(lock) {
            if (cachedClientCertificate == null) {
                ensureIdentity(context)
            }
            return cachedClientCertificate
        }
    }

    fun getPrivateKey(context: Context): PrivateKey? {
        synchronized(lock) {
            if (cachedPrivateKey == null) {
                ensureIdentity(context)
            }
            return cachedPrivateKey
        }
    }

    fun clearIdentity(context: Context) {
        synchronized(lock) {
            try {
                val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
                ks.load(null)
                if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
            } catch (_: Exception) {
                // AndroidKeyStore unavailable (e.g. Robolectric); in-memory fallback is cleared below.
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply()
            cachedKeyManagerFactory = null
            cachedClientCertificate = null
            cachedPrivateKey = null
            fallbackKeyPair = null
        }
    }

    private fun ensureIdentity(context: Context) {
        if (cachedClientCertificate != null) return

        // Remove any legacy plaintext private key that was stored by old builds.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(PREF_KEY_PRIV_LEGACY)) {
            prefs.edit().remove(PREF_KEY_PRIV_LEGACY).apply()
            Log.w(TAG, "Removed legacy plaintext private key from SharedPreferences.")
        }

        val androidKeyStore = try {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null, null) }
        } catch (e: Exception) {
            Log.d(TAG, "AndroidKeyStore unavailable: ${e.message}. Using in-memory fallback.")
            null
        }

        if (androidKeyStore != null) {
            val storedCert = androidKeyStore.getCertificate(ALIAS) as? X509Certificate
            if (storedCert != null) {
                cachedClientCertificate = storedCert
                cachedPrivateKey = androidKeyStore.getKey(ALIAS, null) as? PrivateKey
                Log.d(TAG, "Loaded client certificate from AndroidKeyStore: ${storedCert.subjectDN}")
                return
            }

            val identity = generateAndStoreAndroidKeyPair(androidKeyStore)
            cachedClientCertificate = identity.certificate
            cachedPrivateKey = identity.keyPair.private
            Log.d(TAG, "Generated AndroidKeyStore RSA identity: Subject=${identity.certificate.subjectDN}")
            return
        }

        // JVM / Robolectric fallback. Keep only in memory, never persist.
        val existing = fallbackKeyPair ?: generateFallbackKeyPair().also { fallbackKeyPair = it }
        cachedClientCertificate = existing.certificate
        cachedPrivateKey = existing.keyPair.private
    }

    private fun generateAndStoreAndroidKeyPair(store: KeyStore): KeyPairHolder {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or
                KeyProperties.PURPOSE_VERIFY or
                KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(false)
            .build()
        val keyPair = keyPairGenerator.generateKeyPair()
        val cert = createCertificate(keyPair)
        store.setKeyEntry(ALIAS, keyPair.private, null, arrayOf(cert))
        return KeyPairHolder(keyPair, cert)
    }

    private fun generateFallbackKeyPair(): KeyPairHolder {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val keyPair = generator.generateKeyPair()
        return KeyPairHolder(keyPair, createCertificate(keyPair))
    }

    private fun createCertificate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val startDate = Date(now - 24 * 3600 * 1000L)
        val endDate = Date(now + 20L * 365 * 24 * 3600 * 1000L)
        // Positive serial number (avoid the sign bit so the generated
        // self-signed client certificate is accepted by strict TLS stacks).
        val serialNumber = BigInteger(63, SecureRandom())

        val subject = X500Name("CN=$CLIENT_NAME")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            serialNumber,
            startDate,
            endDate,
            subject,
            keyPair.public
        )
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )
        builder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_clientAuth))
        )
        builder.addExtension(
            Extension.basicConstraints,
            true,
            BasicConstraints(false)
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        cert.verify(keyPair.public)
        return cert
    }

    private data class KeyPairHolder(val keyPair: KeyPair, val certificate: X509Certificate)

    /**
     * Writes a DER certificate to a base64 string (used by tests/debug tooling).
     */
    fun encodeCertificateBase64(cert: X509Certificate): String =
        Base64.encodeToString(cert.encoded, Base64.NO_WRAP)

    /**
     * Reads a base64 DER certificate back into an X509Certificate.
     */
    fun decodeCertificateBase64(encoded: String): X509Certificate? = try {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    } catch (e: Exception) {
        Log.w(TAG, "Failed to decode certificate", e)
        null
    }
}
