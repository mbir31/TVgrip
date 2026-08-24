package com.example.core.network

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    private const val ALIAS = "tvgrip_client"
    private const val KEY_PASS = "tvgrip_secret"

    private var cachedKeyManagerFactory: KeyManagerFactory? = null
    private var cachedClientCertificate: X509Certificate? = null
    private var cachedPrivateKey: PrivateKey? = null

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
            Log.d(TAG, "Generating new 2048-bit RSA Key Pair and X.509 certificate...")
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048, SecureRandom())
            val keyPair = keyGen.generateKeyPair()
            privateKey = keyPair.private
            cert = createValidSelfSignedCert(keyPair)

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

    private fun createValidSelfSignedCert(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val startDate = Date(now - 24 * 3600 * 1000L)
        val endDate = Date(now + 10L * 365 * 24 * 3600 * 1000L) // 10 years

        val derBytes = generateSelfSignedDer(keyPair, startDate, endDate)
        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(ByteArrayInputStream(derBytes)) as X509Certificate
        cert.verify(keyPair.public)
        return cert
    }

    private fun generateSelfSignedDer(keyPair: KeyPair, start: Date, end: Date): ByteArray {
        val pubKeyBytes = keyPair.public.encoded // DER SubjectPublicKeyInfo

        // AlgorithmIdentifier: SHA256withRSA (OID: 1.2.840.113549.1.1.11)
        val sigAlgId = byteArrayOf(
            0x30, 0x0D,
            0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B,
            0x05, 0x00
        )

        // Issuer & Subject Name: CN=atvremote
        val nameBytes = byteArrayOf(
            0x30, 0x14,
            0x31, 0x12,
            0x30, 0x10,
            0x06, 0x03, 0x55, 0x04, 0x03, // id-at-commonName
            0x0C, 0x09, // UTF8String, length 9
            0x61, 0x74, 0x76, 0x72, 0x65, 0x6D, 0x6F, 0x74, 0x65 // "atvremote"
        )

        // Validity: UTCTime
        val utcFormat = SimpleDateFormat("yyMMddHHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startStr = utcFormat.format(start).toByteArray(Charsets.US_ASCII)
        val endStr = utcFormat.format(end).toByteArray(Charsets.US_ASCII)

        val validityStream = ByteArrayOutputStream()
        validityStream.write(0x30)
        validityStream.write(startStr.size + 2 + endStr.size + 2)
        validityStream.write(0x17) // UTCTime
        validityStream.write(startStr.size)
        validityStream.write(startStr)
        validityStream.write(0x17) // UTCTime
        validityStream.write(endStr.size)
        validityStream.write(endStr)
        val validityBytes = validityStream.toByteArray()

        // TBSCertificate:
        // Version v3 (0xA0 0x03 0x02 0x01 0x02)
        // Serial Number (0x02 0x08 [8 random bytes])
        // Signature Algorithm (sigAlgId)
        // Issuer (nameBytes)
        // Validity (validityBytes)
        // Subject (nameBytes)
        // SubjectPublicKeyInfo (pubKeyBytes)
        val randomSerial = ByteArray(8)
        SecureRandom().nextBytes(randomSerial)
        randomSerial[0] = (randomSerial[0].toInt() and 0x7F).toByte() // Ensure positive integer

        val tbsContent = ByteArrayOutputStream()
        tbsContent.write(byteArrayOf(0xA0.toByte(), 0x03, 0x02, 0x01, 0x02)) // Version v3
        tbsContent.write(0x02) // INTEGER (serial)
        tbsContent.write(randomSerial.size)
        tbsContent.write(randomSerial)
        tbsContent.write(sigAlgId)
        tbsContent.write(nameBytes)
        tbsContent.write(validityBytes)
        tbsContent.write(nameBytes)
        tbsContent.write(pubKeyBytes)

        val tbsContentBytes = tbsContent.toByteArray()
        val tbs = ByteArrayOutputStream()
        tbs.write(0x30)
        writeLength(tbs, tbsContentBytes.size)
        tbs.write(tbsContentBytes)
        val tbsBytes = tbs.toByteArray()

        // Sign the TBSCertificate structure
        val sig = java.security.Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(tbsBytes)
        val signatureBytes = sig.sign()

        // Certificate = SEQUENCE { TBSCertificate, AlgorithmIdentifier, BIT STRING signature }
        val certBody = ByteArrayOutputStream()
        certBody.write(tbsBytes)
        certBody.write(sigAlgId)
        certBody.write(0x03) // BIT STRING
        writeLength(certBody, signatureBytes.size + 1)
        certBody.write(0x00) // 0 unused bits
        certBody.write(signatureBytes)

        val certBodyBytes = certBody.toByteArray()
        val certSeq = ByteArrayOutputStream()
        certSeq.write(0x30)
        writeLength(certSeq, certBodyBytes.size)
        certSeq.write(certBodyBytes)

        return certSeq.toByteArray()
    }

    private fun writeLength(out: ByteArrayOutputStream, length: Int) {
        if (length < 128) {
            out.write(length)
        } else if (length < 256) {
            out.write(0x81)
            out.write(length)
        } else {
            out.write(0x82)
            out.write((length ushr 8) and 0xFF)
            out.write(length and 0xFF)
        }
    }
}
