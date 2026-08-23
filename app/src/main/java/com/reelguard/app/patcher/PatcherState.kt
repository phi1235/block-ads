package com.reelguard.app.patcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed class PatchProgress {
    object Idle : PatchProgress()
    data class Extracting(val message: String = "Đang trích xuất Facebook APK gốc...") : PatchProgress()
    data class Patching(val progress: Float = 0.5f, val message: String = "Đang gỡ SSL Pinning & cấu hình mạng...") : PatchProgress()
    data class Signing(val message: String = "Đang ký số lại APK bằng chứng chỉ nội bộ...") : PatchProgress()
    data class ReadyToInstall(val apkFile: File) : PatchProgress()
    data class Error(val error: String) : PatchProgress()
}

object PatcherState {
    private val _progress = MutableStateFlow<PatchProgress>(PatchProgress.Idle)
    val progress: StateFlow<PatchProgress> = _progress.asStateFlow()

    fun updateProgress(newProgress: PatchProgress) {
        _progress.value = newProgress
    }

    fun reset() {
        _progress.value = PatchProgress.Idle
    }
}
