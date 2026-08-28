package com.reelguard.app.proxy

import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

object CertificateManager {
    private const val TAG = "CertificateManager"
    private const val CA_ALIAS = "reelguard_root_ca"
    private const val CA_KEY_PASSWORD = "reelguard_pass"

    private var caKeyPair: KeyPair? = null
    private var caCertificate: X509Certificate? = null
    private val hostSslContextCache = ConcurrentHashMap<String, SSLContext>()

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun init(context: Context) {
        try {
            val caFile = File(context.filesDir, "reelguard_ca.p12")
            if (caFile.exists()) {
                val keyStore = KeyStore.getInstance("PKCS12")
                caFile.inputStream().use { keyStore.load(it, CA_KEY_PASSWORD.toCharArray()) }
                caCertificate = keyStore.getCertificate(CA_ALIAS) as? X509Certificate
                val privKey = keyStore.getKey(CA_ALIAS, CA_KEY_PASSWORD.toCharArray()) as? java.security.PrivateKey
                if (caCertificate != null && privKey != null) {
                    caKeyPair = KeyPair(caCertificate!!.publicKey, privKey)
                    Log.i(TAG, "Đã nạp thành công Root CA sẵn có")
                    return
                }
            }

            // Tạo mới Root CA
            generateAndSaveRootCA(context)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khởi tạo CertificateManager", e)
        }
    }

    private fun generateAndSaveRootCA(context: Context) {
        val keyGen = KeyPairGenerator.getInstance("RSA", "BC")
        keyGen.initialize(2048, SecureRandom())
        val keyPair = keyGen.generateKeyPair()

        val issuer = X500Name("CN=ReelGuard Root CA, O=ReelGuard Security, C=VN")
        val serial = BigInteger(64, SecureRandom())
        val notBefore = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
        val notAfter = Date(System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000L) // 10 năm

        val builder = JcaX509v3CertificateBuilder(issuer, serial, notBefore, notAfter, issuer, keyPair.public)
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true)) // Là CA

        val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider("BC").build(keyPair.private)
        val cert = JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))

        caKeyPair = keyPair
        caCertificate = cert

        // Lưu vào PKCS12 file nội bộ
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry(CA_ALIAS, keyPair.private, CA_KEY_PASSWORD.toCharArray(), arrayOf(cert))

        val caFile = File(context.filesDir, "reelguard_ca.p12")
        FileOutputStream(caFile).use { keyStore.store(it, CA_KEY_PASSWORD.toCharArray()) }

        // Xuất file .crt công khai ra thư mục cache để người dùng cài đặt
        exportRootCaCrt(context, cert)
        Log.i(TAG, "Đã sinh mới thành công ReelGuard Root CA")
    }

    private fun exportRootCaCrt(context: Context, cert: X509Certificate): File {
        val exportFile = File(context.getExternalFilesDir(null) ?: context.filesDir, "ReelGuard_Root_CA.crt")
        FileOutputStream(exportFile).use { fos ->
            fos.write("-----BEGIN CERTIFICATE-----\n".toByteArray())
            fos.write(android.util.Base64.encode(cert.encoded, android.util.Base64.DEFAULT))
            fos.write("-----END CERTIFICATE-----\n".toByteArray())
        }
        return exportFile
    }

    fun getExportedCaFile(context: Context): File {
        val file = File(context.getExternalFilesDir(null) ?: context.filesDir, "ReelGuard_Root_CA.crt")
        if (!file.exists() && caCertificate != null) {
            exportRootCaCrt(context, caCertificate!!)
        }
        return file
    }

    fun getCaCertificate(): X509Certificate? = caCertificate

    fun getSslContextForHost(host: String): SSLContext {
        return hostSslContextCache.computeIfAbsent(host) { createSslContextForHost(it) }
    }

    private fun createSslContextForHost(host: String): SSLContext {
        val caPair = caKeyPair ?: throw IllegalStateException("Root CA chưa được khởi tạo")
        val caCert = caCertificate ?: throw IllegalStateException("Root CA Cert chưa có")

        val keyGen = KeyPairGenerator.getInstance("RSA", "BC")
        keyGen.initialize(2048, SecureRandom())
        val hostPair = keyGen.generateKeyPair()

        val subject = X500Name("CN=$host, O=ReelGuard Dynamic Cert")
        val issuer = X500Name(caCert.subjectX500Principal.name)
        val serial = BigInteger(64, SecureRandom())
        val notBefore = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
        val notAfter = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000L)

        val builder = JcaX509v3CertificateBuilder(issuer, serial, notBefore, notAfter, subject, hostPair.public)
        builder.addExtension(Extension.basicConstraints, false, BasicConstraints(false))

        // Thêm SAN (Subject Alternative Name)
        val altNames = arrayOf(GeneralName(GeneralName.dNSName, host))
        builder.addExtension(Extension.subjectAlternativeName, false, GeneralNames(altNames))

        val signer = JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider("BC").build(caPair.private)
        val hostCert = JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry("host", hostPair.private, "pass".toCharArray(), arrayOf(hostCert, caCert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, "pass".toCharArray())

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())
        return sslContext
    }
}
