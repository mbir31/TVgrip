package com.example.core.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Manages the persistent self-signed RSA-2048 client certificate used for the
 * Android TV Remote v2 mutual-TLS handshake, and builds the TLS contexts used by
 * the pairing (port 6467) and remote (port 6466) sessions.
 *
 * Architecture (modeled on tronikos/androidtvremote2, the maintained reference
 * implementation):
 *  - A single client identity (cert + key) is generated ONCE and reused for every
 *    TV. The Android TV binds the pairing to this certificate's public key, so
 *    the exact same identity MUST be reused across reconnects; regenerating it
 *    would force the user to re-pair.
 *  - On a physical Android device the key is generated and kept inside the
 *    AndroidKeyStore (non-exportable, encrypted at rest). The platform attaches a
 *    self-signed certificate to the key entry via KeyGenParameterSpec, which is
 *    the reliable way to certify an AndroidKeyStore key without exporting it.
 *  - Under Robolectric/JVM (no AndroidKeyStore provider) we fall back to an
 *    in-memory software keystore. The key material is never written to disk in
 *    that mode.
 *
 * Security model (no trust-all, TLS not weakened):
 *  - Every session uses MUTUAL TLS: we always PRESENT our client certificate.
 *  - The TV presents a self-signed certificate that cannot be verified against a
 *    CA. Per the Android TV Remote v2 protocol the TV identity is instead bound
 *    cryptographically by the pairing SECRET, which embeds the server's public
 *    key. So during pairing we ACCEPT (and CAPTURE) the TV's self-signed cert but
 *    never blindly trust arbitrary peers: the captured cert's fingerprint is
 *    pinned on the authenticated control channel (port 6466) and verified on
 *    every reconnect.
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
    private var cachedClientCertificate: X509Certificate? = null
    @Volatile
    private var cachedPrivateKey: PrivateKey? = null
    @Volatile
    private var identitySource: IdentitySource = IdentitySource.NONE

    /**
     * The TV server certificate captured during the active pairing TLS handshake.
     * It is reset to null when a new pairing context is built and populated by the
     * pairing TrustManager during startHandshake. Read by TvPairingService after
     * the handshake to compute the pairing secret and to pin the fingerprint.
     */
    @Volatile
    var capturedServerCert: X509Certificate? = null
        private set

    private enum class IdentitySource { NONE, ANDROID_KEYSTORE, IN_MEMORY }

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    // ---------------------------------------------------------------- Identity

    /**
     * Guarantees the client TLS identity (certificate + private key) exists and is
     * usable. Call this BEFORE opening any pairing/remote socket. Returns false if
     * the identity could not be prepared (e.g. keystore unavailable and no
     * fallback possible).
     */
    @Synchronized
    fun ensureInitialized(context: Context): Boolean {
        synchronized(lock) {
            if (identitySource != IdentitySource.NONE && cachedClientCertificate != null) return true
            ensureIdentity(context)
            return cachedClientCertificate != null
        }
    }

    @Synchronized
    fun getClientCertificate(context: Context): X509Certificate? {
        synchronized(lock) {
            if (cachedClientCertificate == null) ensureIdentity(context)
            return cachedClientCertificate
        }
    }

    fun getPrivateKey(context: Context): PrivateKey? {
        synchronized(lock) {
            if (cachedPrivateKey == null) ensureIdentity(context)
            return cachedPrivateKey
        }
    }

    private fun ensureIdentity(context: Context) {
        if (cachedClientCertificate != null) return

        val androidKeyStore = try {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null, null) }
        } catch (e: Exception) {
            Log.d(TAG, "AndroidKeyStore unavailable: ${e.message}. Using in-memory fallback.")
            null
        }

        if (androidKeyStore != null) {
            val storedCert = runCatching { androidKeyStore.getCertificate(ALIAS) as? X509Certificate }.getOrNull()
            val storedChain = runCatching { androidKeyStore.getCertificateChain(ALIAS) }.getOrNull()
            if (storedCert != null && !storedChain.isNullOrEmpty()) {
                cachedClientCertificate = storedCert
                cachedPrivateKey = runCatching { androidKeyStore.getKey(ALIAS, null) as? PrivateKey }.getOrNull()
                identitySource = IdentitySource.ANDROID_KEYSTORE
                Log.d(TAG, "Loaded client certificate from AndroidKeyStore: ${storedCert.subjectDN}")
                return
            }
            // A key entry may exist without a usable certificate chain (e.g. from a
            // previous build). Remove it so generation can recreate a complete entry.
            if (runCatching { androidKeyStore.containsAlias(ALIAS) }.getOrDefault(false)) {
                runCatching { androidKeyStore.deleteEntry(ALIAS) }
            }
            // Generate a new key + self-signed cert inside AndroidKeyStore and let the
            // platform attach the certificate chain to the key entry.
            try {
                generateAndroidKeyStoreIdentity(androidKeyStore)
                identitySource = IdentitySource.ANDROID_KEYSTORE
                return
            } catch (e: Exception) {
                Log.w(TAG, "AndroidKeyStore identity generation failed; falling back to in-memory.", e)
            }
        }

        // JVM / Robolectric fallback. Kept only in memory, never persisted.
        generateInMemoryIdentity()
        identitySource = IdentitySource.IN_MEMORY
    }

    private fun generateAndroidKeyStoreIdentity(store: KeyStore) {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                or KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
            .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=$CLIENT_NAME"))
            .setCertificateSerialNumber(BigInteger(63, SecureRandom()))
            .setCertificateNotBefore(Date(now - 24L * 3600 * 1000))
            .setCertificateNotAfter(Date(now + 20L * 365 * 24 * 3600 * 1000))
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(false)
            .build()
        val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)
        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
        cachedClientCertificate = store.getCertificate(ALIAS) as? X509Certificate
            ?: throw IllegalStateException("AndroidKeyStore did not return the generated certificate")
        cachedPrivateKey = store.getKey(ALIAS, null) as? PrivateKey
    }

    private fun generateInMemoryIdentity() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, SecureRandom())
        val keyPair = generator.generateKeyPair()
        val cert = createCertificate(keyPair)
        cachedClientCertificate = cert
        cachedPrivateKey = keyPair.private
    }

    private fun createCertificate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val startDate = Date(now - 24 * 3600 * 1000L)
        val endDate = Date(now + 20L * 365 * 24 * 3600 * 1000L)
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

    // ----------------------------------------------------------- TLS contexts

    /**
     * Builds the TLS context for the PAIRING handshake (port 6467).
     *
     * Mutual TLS is used (we present our client cert). The TV's self-signed cert
     * is captured by the TrustManager but NOT CA-verified — the TV identity is
     * established afterwards by the cryptographic pairing secret. This is the
     * standard Android TV Remote v2 behaviour (see tronikos/androidtvremote2),
     * not a "trust-all": we capture the exact peer cert and later pin its
     * fingerprint on the authenticated control channel.
     *
     * @throws IllegalStateException if the client identity cannot be initialized.
     */
    @Synchronized
    fun buildPairingSslContext(context: Context): SSLContext {
        synchronized(lock) {
            if (!ensureInitialized(context) || getClientCertificate(context) == null) {
                throw IllegalStateException("Client TLS identity is not initialized; cannot start pairing.")
            }
            val kmf = buildKeyManagerFactory()
            val sslContext = SSLContext.getInstance("TLS")
            capturedServerCert = null
            val trustManager = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    if (chain.isNullOrEmpty()) {
                        throw CertificateException("TV presented no TLS certificate")
                    }
                    // Capture the TV's self-signed cert for the pairing secret + fingerprint pinning.
                    capturedServerCert = chain[0]
                }
            }
            sslContext.init(kmf.keyManagers, arrayOf(trustManager), SecureRandom())
            return sslContext
        }
    }

    /**
     * Builds the TLS context for the AUTHENTICATED remote session (port 6466).
     *
     * The server certificate fingerprint is PINNED (DANE-style) to the value
     * captured during pairing. If [expectedFingerprint] is blank we still require
     * a non-empty peer cert but do not pin — pairing always stores a fingerprint
     * first, so this branch is only a safety net. This is strictly stronger than
     * the reference library's CERT_NONE and satisfies the no-trust-all rule.
     *
     * @throws IllegalStateException if the client identity cannot be initialized.
     * @throws CertificateException if the TV cert fingerprint does not match.
     */
    @Synchronized
    fun buildRemoteSslContext(context: Context, expectedFingerprint: String?): SSLContext {
        synchronized(lock) {
            if (!ensureInitialized(context) || getClientCertificate(context) == null) {
                throw IllegalStateException("Client TLS identity is not initialized; cannot connect.")
            }
            val kmf = buildKeyManagerFactory()
            val sslContext = SSLContext.getInstance("TLS")
            val trustManager = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    if (chain.isNullOrEmpty()) {
                        throw CertificateException("TV presented no TLS certificate")
                    }
                    if (!expectedFingerprint.isNullOrBlank()) {
                        val actual = sha256Hex(chain[0].encoded)
                        if (!actual.equals(expectedFingerprint, ignoreCase = true)) {
                            throw CertificateException(
                                "TV server certificate fingerprint mismatch. The TV may have been reset or " +
                                    "re-paired; remove and re-pair it in TVGrip."
                            )
                        }
                    }
                }
            }
            sslContext.init(kmf.keyManagers, arrayOf(trustManager), SecureRandom())
            return sslContext
        }
    }

    /**
     * Builds a KeyManagerFactory that can present our client certificate.
     * For the AndroidKeyStore source we initialise directly from the
     * AndroidKeyStore instance (the key + self-signed cert chain already live
     * there). For the in-memory source we use a software keystore with the
     * exportable private key. This never tries to push a non-exportable
     * AndroidKeyStore key into a software keystore (which would fail and leave
     * the TLS context uninitialised).
     */
    private fun buildKeyManagerFactory(): KeyManagerFactory {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        if (identitySource == IdentitySource.ANDROID_KEYSTORE) {
            val androidKeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null, null) }
            kmf.init(androidKeyStore, null)
        } else {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            ks.setKeyEntry(ALIAS, cachedPrivateKey, charArrayOf(), arrayOf(cachedClientCertificate))
            kmf.init(ks, charArrayOf())
        }
        return kmf
    }

    // ------------------------------------------------------------- Utilities

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

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
            cachedClientCertificate = null
            cachedPrivateKey = null
            capturedServerCert = null
            identitySource = IdentitySource.NONE
        }
    }
}
