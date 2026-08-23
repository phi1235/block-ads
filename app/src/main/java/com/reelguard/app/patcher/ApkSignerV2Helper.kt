package com.reelguard.app.patcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * Trình ký số APK chuẩn Google (APK Signature Scheme v1 + v2 + v3 + 4-byte zipalign)
 * Tự động sinh và quản lý khóa bảo mật động qua AndroidKeyStore an toàn
 * Không lưu trữ private key cứng trong mã nguồn (Zero-Key Repository)
 */
object ApkSignerV2Helper {

    private const val KEYSTORE_ALIAS = "ReelGuardDynamicSigner"
    private var cachedKeyPair: Pair<PrivateKey, List<X509Certificate>>? = null

    @Synchronized
    fun getOrLoadSignerKeys(context: Context): Pair<PrivateKey, List<X509Certificate>> {
        cachedKeyPair?.let { return it }

        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        // Nếu chưa có khóa trong AndroidKeyStore thì sinh mới tự động
        if (!ks.containsAlias(KEYSTORE_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )
            kpg.initialize(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .setCertificateSubject(X500Principal("CN=ReelGuard Dynamic Signer, O=ReelGuard, C=VN"))
                    .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                    .setCertificateNotBefore(Date(System.currentTimeMillis() - 100000))
                    .setCertificateNotAfter(Date(System.currentTimeMillis() + 1000L * 3600 * 24 * 3650))
                    .build()
            )
            kpg.generateKeyPair()
        }

        val privateKey = ks.getKey(KEYSTORE_ALIAS, null) as PrivateKey
        val cert = ks.getCertificate(KEYSTORE_ALIAS) as X509Certificate

        val result = Pair(privateKey, listOf(cert))
        cachedKeyPair = result
        return result
    }

    /**
     * Ký số file APK bằng Google ApkSigner (v1 + v2 + v3)
     */
    fun signApk(context: Context, unsignedApk: File, signedApk: File) {
        val (privateKey, certs) = getOrLoadSignerKeys(context)

        val signerConfig = ApkSigner.SignerConfig.Builder(
            "ReelGuard",
            privateKey,
            certs
        ).build()

        val apkSigner = ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()

        apkSigner.sign()
    }
}