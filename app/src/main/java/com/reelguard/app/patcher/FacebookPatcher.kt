package com.reelguard.app.patcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.reelguard.app.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object FacebookPatcher {
    private const val TAG = "FbPatcher"
    val TARGET_PACKAGES = listOf("com.facebook.katana", "com.facebook.orca", "com.facebook.lite")

    fun findInstalledFacebookPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in TARGET_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return null
    }

    /**
     * Bắt đầu toàn bộ quy trình Auto-Patch tự động cho toàn bộ Split APKs của Facebook
     */
    suspend fun startAutoPatch(context: Context) = withContext(Dispatchers.IO) {
        try {
            // 1. Kiểm tra quyền Cài đặt ứng dụng không rõ nguồn gốc
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(manageIntent)
                    PatcherState.updateProgress(PatchProgress.Error("Vui lòng gạt BẬT 'Cho phép cài đặt ứng dụng không rõ nguồn gốc' cho ReelGuard rồi bấm thử lại."))
                    return@withContext
                }
            }

            val pkg = findInstalledFacebookPackage(context) ?: "com.facebook.katana"
            PatcherState.updateProgress(PatchProgress.Extracting("Đang quét các file Split APK của $pkg..."))

            val pm = context.packageManager
            val appInfo = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (e: Exception) {
                PatcherState.updateProgress(PatchProgress.Error("Không tìm thấy ứng dụng Facebook ($pkg) trên máy."))
                return@withContext
            }

            val allSourceApkPaths = mutableListOf<String>()
            allSourceApkPaths.add(appInfo.sourceDir)
            appInfo.splitSourceDirs?.let { allSourceApkPaths.addAll(it) }

            val totalFiles = allSourceApkPaths.size
            Log.i(TAG, "Tìm thấy $totalFiles file APK (Split Bundle) của Facebook.")

            val outputDir = File(context.cacheDir, "patched_splits").apply {
                deleteRecursively()
                mkdirs()
            }

            val finalSignedApks = mutableListOf<File>()

            for ((index, apkPath) in allSourceApkPaths.withIndex()) {
                val sourceFile = File(apkPath)
                val isBase = index == 0
                val fileName = sourceFile.name

                PatcherState.updateProgress(
                    PatchProgress.Patching(
                        (index.toFloat() / totalFiles),
                        "Đang xử lý ${if (isBase) "base.apk" else fileName} (${index + 1}/$totalFiles)..."
                    )
                )

                val unsignedTemp = File(outputDir, "temp_unsigned_$fileName")
                val signedOutput = File(outputDir, "signed_$fileName")

                modifyApk(sourceFile, unsignedTemp)

                PatcherState.updateProgress(
                    PatchProgress.Signing("Đang ký số v2/v3 cho $fileName (${index + 1}/$totalFiles)...")
                )
                ApkSignerV2Helper.signApk(context, unsignedTemp, signedOutput)
                unsignedTemp.delete()

                finalSignedApks.add(signedOutput)
            }

            PatcherState.updateProgress(PatchProgress.ReadyToInstall(finalSignedApks.first()))
            Log.i(TAG, "Đã patch và ký thành công toàn bộ ${finalSignedApks.size} file Split APK!")

            installSplitApks(context, pkg, finalSignedApks)

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi trong quá trình Auto-Patch", e)
            PatcherState.updateProgress(PatchProgress.Error(e.localizedMessage ?: "Lỗi không xác định"))
        }
    }

    /**
     * Sao chép bảo toàn nguyên vẹn định dạng STORED (uncompressed) và DEFLATED của từng Entry
     * Loại bỏ chữ ký cũ trong META-INF để chuẩn bị ký mới chuẩn Google
     */
    private fun modifyApk(sourceApk: File, outputApk: File) {
        val zipFile = ZipFile(sourceApk)
        ZipOutputStream(FileOutputStream(outputApk).buffered()).use { zos ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (name.startsWith("META-INF/")) continue

                val newEntry = ZipEntry(name)
                if (entry.method == ZipEntry.STORED) {
                    newEntry.method = ZipEntry.STORED
                    newEntry.size = entry.size
                    newEntry.compressedSize = entry.size
                    newEntry.crc = entry.crc
                } else {
                    newEntry.method = ZipEntry.DEFLATED
                }

                zos.putNextEntry(newEntry)
                zipFile.getInputStream(entry).use { inStream ->
                    inStream.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
        zipFile.close()
    }

    fun installSplitApks(context: Context, packageName: String, apkFiles: List<File>) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            for (file in apkFiles) {
                val entryName = file.nameWithoutExtension
                session.openWrite(entryName, 0, file.length()).use { outStream ->
                    FileInputStream(file).use { inStream ->
                        inStream.copyTo(outStream)
                        session.fsync(outStream)
                    }
                }
            }

            val callbackIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.reelguard.app.INSTALL_COMPLETE"
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                sessionId,
                callbackIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            session.commit(pendingIntent.intentSender)
            session.close()

            Log.i(TAG, "Đã gửi PackageInstaller Session thành công với ${apkFiles.size} split files!")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gọi PackageInstaller Session", e)
            PatcherState.updateProgress(PatchProgress.Error(e.localizedMessage ?: "Lỗi cài đặt Split APK"))
        }
    }
}