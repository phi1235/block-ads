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

    // Chứng chỉ Root CA chuẩn X.509 RSA-2048 hợp lệ 100% (RFC 5280, CA:TRUE)
    private val DEFAULT_CA_PEM = """
-----BEGIN CERTIFICATE-----
MIIDUDCCAjigAwIBAgIIDzTlMUp/dEUwDQYJKoZIhvcNAQEMBQAwRjELMAkGA1UE
BhMCVk4xGzAZBgNVBAoTElJlZWxHdWFyZCBTZWN1cml0eTEaMBgGA1UEAxMRUmVl
bEd1YXJkIFJvb3QgQ0EwHhcNMjYwODIzMTY0NTIwWhcNMzYwODIwMTY0NTIwWjBG
MQswCQYDVQQGEwJWTjEbMBkGA1UEChMSUmVlbEd1YXJkIFNlY3VyaXR5MRowGAYD
VQQDExFSZWVsR3VhcmQgUm9vdCBDQTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC
AQoCggEBAKoDNO3S21a0vKcXK53aP96fNe99dop7k+Fm8U4Lv4t5TdV1sz+5O7P+
B2W11GStsgM9gsP9VoG+E7SaEAZbh4dIsTzyarICmkwYNUeXzCeDw2B9bTX5TDyk
GHHyIXmoG+sZrYVFfzOKRAAli/8w7p3EHchpvsOOFrH1zAPk8/8LYUl9wssSA48Q
KUpvxOwAnkWvl3FkIaXxsFeqvlgfzl0FG0eB3KlduMGnXsfpyZcRq1B10EQ0D+wL
q7smhtirrynidA4NRGgQMdnjU4RgHorHHJ05kLbC+C8m771hueD1HjIhESMb5Q0s
AYGlHasGaTWzFpeI5flw6qGlB/s7bkMCAwEAAaNCMEAwHQYDVR0OBBYEFG6oKtgb
kHjkqYGOvrg4invmAnYRMA4GA1UdDwEB/wQEAwIBBjAPBgNVHRMBAf8EBTADAQH/
MA0GCSqGSIb3DQEBDAUAA4IBAQAk7nK2GgNpEC6r8R7KpQ1weyMfurfCMabZLWAL
8LqMPVjPPGqbwCaBbypi0oRjZbRWh7Haa79Lup1aGgecP9+bFHqhTQ7pJyCsUwP8
eDu58OtDSz9JwDOP4g6m5IE2MdFZQmyf6zVBx056lnrfGB4RoIZmZs00YSnUFhU1
NqqCq4+rsUed5kwsC4LdhReSf4ZT+aGzbACJcJHsnCIihvepDZBRAsxdGCZ1aEto
VBJOSA4f009G8jzp2Dkw3EM0Lsbv7YkHYSLBJsnMpRlAq33F3ErOdoMSYQGououv
7EzMuTuHc/8b+8wxzFsfMFu6iB/e9QpQJDxVNRYmQ35ZkOE5
-----END CERTIFICATE-----
    """.trimIndent()

    fun getOrGenerateCaFile(context: Context): File {
        val file = File(context.filesDir, CA_FILE_NAME)
        // Luôn ghi đè để đảm bảo chứng chỉ luôn chuẩn xác
        try {
            FileOutputStream(file).use { out ->
                out.write(DEFAULT_CA_PEM.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi tạo tệp CA", e)
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