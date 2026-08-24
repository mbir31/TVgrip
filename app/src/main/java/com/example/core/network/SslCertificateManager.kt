package com.example.core.network

import android.content.Context
import android.util.Base64
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
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
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory

/**
 * Manages persistent client X.509 RSA certificates for mutual TLS authentication
 * required by Google TV and Android TV Remote v2 services.
 */
object SslCertificateManager {

    private const val TAG = "SslCertificateManager"
    private const val PREFS_NAME = "tvgrip_ssl_credentials"
    private const val PREF_KEY_PRIVATE = "ssl_client_private_key"
    private const val PREF_KEY_CERT = "ssl_client_certificate"
    private const val ALIAS = "atvremote"
    private const val KEY_PASS = "tvgrip_secret"

    private var cachedKeyManagerFactory: KeyManagerFactory? = null
    private var cachedClientCertificate: X509Certificate? = null
    private var cachedPrivateKey: PrivateKey? = null

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Synchronized
    fun getOrCreateKeyManagerFactory(context: Context): KeyManagerFactory {
        cachedKeyManagerFactory?.let { return it }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var privateKey: PrivateKey? = null
        var cert: X509Certificate? = null

        val storedPrivB64 = prefs.getString(PREF_KEY_PRIVATE, null)
        val storedCertB64 = prefs.getString(PREF_KEY_CERT, null)

        if (storedPrivB64 != null && storedCertB64 != null) {
            try {
                val privBytes = Base64.decode(storedPrivB64, Base64.NO_WRAP)
                val certBytes = Base64.decode(storedCertB64, Base64.NO_WRAP)

                val keyFactory = KeyFactory.getInstance("RSA")
                privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))

                val certFactory = CertificateFactory.getInstance("X.509")
                cert = certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
                cert.verify(cert.publicKey)
                if (!cert.subjectDN.name.contains("atvremote")) {
                    Log.d(TAG, "Legacy certificate subject detected (${cert.subjectDN}). Upgrading to CN=atvremote.")
                    privateKey = null
                    cert = null
                } else {
                    Log.d(TAG, "Loaded persistent client TLS certificate: Subject=${cert.subjectDN}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error restoring saved certificate: ${e.message}. Regenerating.")
                privateKey = null
                cert = null
            }
        }

        if (privateKey == null || cert == null) {
            Log.d(TAG, "Generating new 2048-bit RSA Key Pair and X.509 certificate via BouncyCastle...")
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048, SecureRandom())
            val keyPair = keyGen.generateKeyPair()
            privateKey = keyPair.private
            cert = createBouncyCastleCertificate(keyPair)

            try {
                val privB64 = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
                val certB64 = Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
                prefs.edit()
                    .putString(PREF_KEY_PRIVATE, privB64)
                    .putString(PREF_KEY_CERT, certB64)
                    .apply()
                Log.d(TAG, "Saved persistent client certificate to storage.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist client certificate: ${e.message}")
            }
        }

        cachedPrivateKey = privateKey
        cachedClientCertificate = cert

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry(ALIAS, privateKey, KEY_PASS.toCharArray(), arrayOf(cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, KEY_PASS.toCharArray())
        cachedKeyManagerFactory = kmf
        return kmf
    }

    fun getClientCertificate(context: Context): X509Certificate? {
        if (cachedClientCertificate == null) {
            getOrCreateKeyManagerFactory(context)
        }
        return cachedClientCertificate
    }

    fun getPrivateKey(context: Context): PrivateKey? {
        if (cachedPrivateKey == null) {
            getOrCreateKeyManagerFactory(context)
        }
        return cachedPrivateKey
    }

    private fun createBouncyCastleCertificate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val startDate = Date(now - 24 * 3600 * 1000L) // Yesterday
        val endDate = Date(now + 20L * 365 * 24 * 3600 * 1000L) // 20 years validity
        val serialNumber = BigInteger(64, SecureRandom())

        val subjectName = X500Name("CN=atvremote")
        val certBuilder = JcaX509v3CertificateBuilder(
            subjectName, // Issuer
            serialNumber,
            startDate,
            endDate,
            subjectName, // Subject
            keyPair.public
        )

        // Add extensions for TLS client authentication
        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
        )
        certBuilder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(arrayOf(KeyPurposeId.id_kp_clientAuth, KeyPurposeId.id_kp_serverAuth))
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .build(keyPair.private)

        val certHolder = certBuilder.build(signer)
        val cert = JcaX509CertificateConverter()
            .getCertificate(certHolder)

        cert.verify(keyPair.public)
        return cert
    }
}
