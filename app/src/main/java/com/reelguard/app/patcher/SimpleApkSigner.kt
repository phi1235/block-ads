package com.reelguard.app.patcher

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Trình ký số APK nhúng tự động (In-App APK Signer)
 * Tự sinh cặp khóa RSA-2048 và ký chuẩn MANIFEST.MF, CERT.SF, CERT.RSA
 */
object SimpleApkSigner {

    fun signApk(unsignedApk: File, signedApk: File) {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        val privateKey = keyPair.private
        val publicKey = keyPair.public

        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest.mainAttributes[Attributes.Name("Created-By")] = "ReelGuard Auto-Patcher Engine"

        val fileEntries = mutableMapOf<String, ByteArray>()
        val md = MessageDigest.getInstance("SHA-256")

        // 1. Đọc toàn bộ entries và tính digest SHA-256
        ZipInputStream(FileInputStream(unsignedApk)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!name.startsWith("META-INF/") && !entry.isDirectory) {
                    val bytes = zis.readBytes()
                    fileEntries[name] = bytes

                    val digest = md.digest(bytes)
                    val digestB64 = Base64.encodeToString(digest, Base64.NO_WRAP)
                    val attr = Attributes()
                    attr[Attributes.Name("SHA-256-Digest")] = digestB64
                    manifest.entries[name] = attr
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // 2. Tạo MANIFEST.MF bytes
        val manifestBaos = ByteArrayOutputStream()
        manifest.write(manifestBaos)
        val manifestBytes = manifestBaos.toByteArray()

        // 3. Tạo CERT.SF
        val sf = Manifest()
        sf.mainAttributes[Attributes.Name("Signature-Version")] = "1.0"
        sf.mainAttributes[Attributes.Name("Created-By")] = "ReelGuard"
        val manifestDigest = md.digest(manifestBytes)
        sf.mainAttributes[Attributes.Name("SHA-256-Digest-Manifest")] = Base64.encodeToString(manifestDigest, Base64.NO_WRAP)

        for ((name, _) in manifest.entries) {
            val entryManifest = "Name: $name\r\nSHA-256-Digest: ${manifest.entries[name]?.getValue("SHA-256-Digest")}\r\n\r\n"
            val entryDigest = md.digest(entryManifest.toByteArray())
            val sfAttr = Attributes()
            sfAttr[Attributes.Name("SHA-256-Digest")] = Base64.encodeToString(entryDigest, Base64.NO_WRAP)
            sf.entries[name] = sfAttr
        }

        val sfBaos = ByteArrayOutputStream()
        sf.write(sfBaos)
        val sfBytes = sfBaos.toByteArray()

        // 4. Tạo chữ ký số RSA cho CERT.SF
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(sfBytes)
        val signatureBytes = signer.sign()

        // 5. Ghi file APK đã ký hoàn chỉnh
        ZipOutputStream(FileOutputStream(signedApk)).use { zos ->
            // Ghi META-INF/MANIFEST.MF
            val manifestEntry = ZipEntry("META-INF/MANIFEST.MF")
            zos.putNextEntry(manifestEntry)
            zos.write(manifestBytes)
            zos.closeEntry()

            // Ghi META-INF/CERT.SF
            val sfEntry = ZipEntry("META-INF/CERT.SF")
            zos.putNextEntry(sfEntry)
            zos.write(sfBytes)
            zos.closeEntry()

            // Ghi META-INF/CERT.RSA
            val rsaEntry = ZipEntry("META-INF/CERT.RSA")
            zos.putNextEntry(rsaEntry)
            zos.write(signatureBytes)
            zos.closeEntry()

            // Ghi lại toàn bộ file của APK
            for ((name, data) in fileEntries) {
                val ze = ZipEntry(name)
                zos.putNextEntry(ze)
                zos.write(data)
                zos.closeEntry()
            }
        }
    }
}
