package com.reelguard.app.tile

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.reelguard.app.vpn.ConnectionStatus
import com.reelguard.app.vpn.ReelGuardVpnService
import com.reelguard.app.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class QuickToggleTile : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            VpnState.status.collectLatest { status ->
                updateTileState(status)
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val currentStatus = VpnState.status.value

        if (currentStatus == ConnectionStatus.CONNECTED) {
            // Đang bật -> Tắt
            val stopIntent = Intent(this, ReelGuardVpnService::class.java).apply {
                action = ReelGuardVpnService.ACTION_STOP
            }
            startService(stopIntent)
        } else if (currentStatus == ConnectionStatus.DISCONNECTED) {
            // Đang tắt -> Kiểm tra quyền VPN và Bật
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent == null) {
                val startIntent = Intent(this, ReelGuardVpnService::class.java).apply {
                    action = ReelGuardVpnService.ACTION_START
                }
                startService(startIntent)
            } else {
                // Cần mở app để xin quyền VPN lần đầu
                prepareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivityAndCollapse(prepareIntent)
            }
        }
    }

    private fun updateTileState(status: ConnectionStatus) {
        val tile = qsTile ?: return
        when (status) {
            ConnectionStatus.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "ReelGuard: Bật"
            }
            ConnectionStatus.CONNECTING -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = "Đang kết nối..."
            }
            ConnectionStatus.DISCONNECTED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "ReelGuard: Tắt"
            }
        }
        tile.updateTile()
    }
}
