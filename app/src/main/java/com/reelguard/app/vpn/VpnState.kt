package com.reelguard.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

object VpnState {
    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _adsBlockedCount = MutableStateFlow(0)
    val adsBlockedCount: StateFlow<Int> = _adsBlockedCount.asStateFlow()

    private val _splitTunnelTarget = MutableStateFlow("com.facebook.katana")
    val splitTunnelTarget: StateFlow<String> = _splitTunnelTarget.asStateFlow()

    fun updateStatus(newStatus: ConnectionStatus) {
        _status.value = newStatus
    }

    fun incrementAdsBlocked(count: Int = 1) {
        _adsBlockedCount.value += count
    }

    fun resetStats() {
        _adsBlockedCount.value = 0
    }
}
