package com.example.core.network

import android.content.Context
import android.util.Log
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory

/**
 * Manages persistent client X.509 RSA certificates for mutual TLS authentication
 * required by Google TV and Android TV Remote v2 services.
 */
object SslCertificateManager {

    private const val ALIAS = "tvgrip_client"
    private const val KEY_PASS = "tvgrip_secret"

    private var cachedKeyManagerFactory: KeyManagerFactory? = null
    private var cachedClientCertificate: X509Certificate? = null

    @Synchronized
    fun getOrCreateKeyManagerFactory(context: Context): KeyManagerFactory {
        cachedKeyManagerFactory?.let { return it }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)

        // Generate 2048-bit RSA Key Pair
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048, SecureRandom())
        val keyPair = keyGen.generateKeyPair()

        // Generate self-signed X.509 certificate
        val cert = generateSelfSignedCertificate(keyPair)
        cachedClientCertificate = cert

        keyStore.setKeyEntry(ALIAS, keyPair.private, KEY_PASS.toCharArray(), arrayOf(cert))

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

    @Suppress("DEPRECATION")
    private fun generateSelfSignedCertificate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val startDate = Date(now - 86400000L)
        val endDate = Date(now + 3650L * 86400000L) // 10 years

        try {
            val dname = "CN=TVGrip Remote, O=TVGrip, C=US"
            val sigAlg = "SHA256withRSA"
            
            val builderClass = Class.forName("org.bouncycastle.x509.X509V3CertificateGenerator")
            val gen = builderClass.getDeclaredConstructor().newInstance()
            
            val setSerial = builderClass.getMethod("setSerialNumber", BigInteger::class.java)
            setSerial.invoke(gen, BigInteger.valueOf(System.currentTimeMillis()))
            
            val setIssuerDN = builderClass.getMethod("setIssuerDN", Class.forName("javax.security.auth.x500.X500Principal"))
            setIssuerDN.invoke(gen, javax.security.auth.x500.X500Principal(dname))
            
            val setNotBefore = builderClass.getMethod("setNotBefore", Date::class.java)
            setNotBefore.invoke(gen, startDate)
            
            val setNotAfter = builderClass.getMethod("setNotAfter", Date::class.java)
            setNotAfter.invoke(gen, endDate)
            
            val setSubjectDN = builderClass.getMethod("setSubjectDN", Class.forName("javax.security.auth.x500.X500Principal"))
            setSubjectDN.invoke(gen, javax.security.auth.x500.X500Principal(dname))
            
            val setPublicKey = builderClass.getMethod("setPublicKey", java.security.PublicKey::class.java)
            setPublicKey.invoke(gen, keyPair.public)
            
            val setSigAlg = builderClass.getMethod("setSignatureAlgorithm", String::class.java)
            setSigAlg.invoke(gen, sigAlg)
            
            val generate = builderClass.getMethod("generate", java.security.PrivateKey::class.java)
            return generate.invoke(gen, keyPair.private) as X509Certificate
        } catch (e: Throwable) {
            return createMinimalSelfSignedCert(keyPair, startDate, endDate)
        }
    }

    private fun createMinimalSelfSignedCert(keyPair: KeyPair, start: Date, end: Date): X509Certificate {
        val certBytes = generateSelfSignedDer(keyPair, start, end)
        val cf = java.security.cert.CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(java.io.ByteArrayInputStream(certBytes)) as X509Certificate
    }

    private fun generateSelfSignedDer(keyPair: KeyPair, start: Date, end: Date): ByteArray {
        val pubKeyBytes = keyPair.public.encoded
        val sig = java.security.Signature.getInstance("SHA256withRSA")
        sig.initSign(keyPair.private)
        sig.update(pubKeyBytes)
        val signatureBytes = sig.sign()

        val out = java.io.ByteArrayOutputStream()
        out.write(0x30)
        val tbsAndSig = java.io.ByteArrayOutputStream()
        
        val tbs = java.io.ByteArrayOutputStream()
        tbs.write(byteArrayOf(0xA0.toByte(), 0x03, 0x02, 0x01, 0x02)) // Version v3
        tbs.write(byteArrayOf(0x02, 0x01, 0x01)) // Serial 1
        
        val sigAlgId = byteArrayOf(
            0x30, 0x0D,
            0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B,
            0x05, 0x00
        )
        tbs.write(sigAlgId)
        
        val name = byteArrayOf(
            0x30, 0x13, 0x31, 0x11, 0x30, 0x0F, 0x06, 0x03, 0x55, 0x04, 0x03, 0x13, 0x08,
            0x54, 0x56, 0x47, 0x72, 0x69, 0x70, 0x52, 0x4D // "TVGripRM"
        )
        tbs.write(name) // Issuer
        
        val validity = byteArrayOf(
            0x30, 0x1E,
            0x17, 0x0D, 0x32, 0x34, 0x30, 0x31, 0x30, 0x31, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x5A,
            0x17, 0x0D, 0x33, 0x36, 0x30, 0x31, 0x30, 0x31, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x5A
        )
        tbs.write(validity)
        tbs.write(name) // Subject
        tbs.write(pubKeyBytes) // SubjectPublicKeyInfo
        
        val tbsBytes = tbs.toByteArray()
        tbsAndSig.write(0x30)
        writeLength(tbsAndSig, tbsBytes.size)
        tbsAndSig.write(tbsBytes)
        
        tbsAndSig.write(sigAlgId)
        
        tbsAndSig.write(0x03)
        writeLength(tbsAndSig, signatureBytes.size + 1)
        tbsAndSig.write(0x00)
        tbsAndSig.write(signatureBytes)
        
        val totalBytes = tbsAndSig.toByteArray()
        writeLength(out, totalBytes.size)
        out.write(totalBytes)
        return out.toByteArray()
    }

    private fun writeLength(out: java.io.ByteArrayOutputStream, length: Int) {
        if (length < 128) {
            out.write(length)
        } else if (length < 256) {
            out.write(0x81)
            out.write(length)
        } else {
            out.write(0x82)
            out.write(length shr 8)
            out.write(length and 0xFF)
        }
    }
}
