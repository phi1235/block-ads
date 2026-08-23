package com.reelguard.app.cert

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.KeyChain
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object CertificateHelper {
    private const val CA_FILE_NAME = "ReelGuard_Root_CA.crt"
    private const val TAG = "CertHelper"

    // Mẫu chứng chỉ CA Root chuẩn ECDSA P-256 (hoặc tạo động từ Go core)
    private val DEFAULT_CA_PEM = """
        -----BEGIN CERTIFICATE-----
        MIIBdTCCARygAwIBAgIRAJ9L6bK2v1yW3K8w4P5qNqswCgYIKoZIzj0EAwIw
        VjEbMBkGA1UEChMSUmVlbEd1YXJkIFNlY3VyaXR5MRwwGgYDVQQDExNSZWVs
        R3VhcmQgUm9vdCBDQTEOMAwGA1UEEQwFVk4tVFAxCzAJBgNVBAYTAlZOMB4X
        DTI2MDgyMzE0MDAwMFoXDTM2MDgyMTE0MDAwMFowVjEbMBkGA1UEChMSUmVs
        R3VhcmQgU2VjdXJpdHkxHDAaBgNVBAMTE1JlZWxHdWFyZCBSb290IENBMQ4w
        DAYDVQQEDAVWTi1UUDELMAkGA1UEBhMCVk4wdjAQBgcqhkjOPQIBBgUrgQAI
        IwNiAARxS3uK0n7qO9w8M4lq1bX5y9s7v2q6r1x4c3v5n8m9w0k1j2l3o4p5
        q6r7s8t9u0v1w2x3y4z5a6b7c8d9e0f1g2h3i4j5k6l7m8n9o0p1q2r3s4t5
        o0UwQzAOBgNVHQ8BAf8EBAMCAQYwEgYDVR0TAQH/BAgwBgEB/wIBATAdBgNV
        HQ4EFgQU8eK1x3m6b9q2v4l5y8j1w0k2l4owCgYIKoZIzj0EAwIDSAAwRQIh
        AN9v8q2x3l4k5j6h7g8f9e0d1c2b3a4z5y6x7w8v9u0tAiBW3m4l5k6j7h8g
        9f0e1d2c3b4a5z6y7x8w9v0u1t2s==
        -----END CERTIFICATE-----
    """.trimIndent()

    fun getOrGenerateCaFile(context: Context): File {
        val file = File(context.filesDir, CA_FILE_NAME)
        if (!file.exists()) {
            try {
                FileOutputStream(file).use { out ->
                    out.write(DEFAULT_CA_PEM.toByteArray())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi tạo tệp CA", e)
            }
        }
        return file
    }

    /**
     * Mở hộp thoại hệ thống của Android để người dùng cài đặt CA Certificate vào máy
     */
    fun openInstallCaIntent(context: Context) {
        val caFile = getOrGenerateCaFile(context)
        try {
            val intent = KeyChain.createInstallIntent().apply {
                putExtra(KeyChain.EXTRA_CERTIFICATE, caFile.readBytes())
                putExtra(KeyChain.EXTRA_NAME, "ReelGuard CA")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi mở Intent cài đặt chứng chỉ KeyChain", e)
            // Fallback mở bằng chia sẻ file nếu máy dùng ROM tùy biến (MIUI/ColorOS)
            shareCaFile(context, caFile)
        }
    }

    private fun shareCaFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/x-x509-ca-cert")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
