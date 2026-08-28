package com.reelguard.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.reelguard.app.ui.screens.HomeScreen
import com.reelguard.app.ui.theme.BrandDarkBackground
import com.reelguard.app.ui.theme.ReelGuardTheme
import com.reelguard.app.vpn.ConnectionStatus
import com.reelguard.app.vpn.ReelGuardVpnService
import com.reelguard.app.vpn.VpnState

import com.reelguard.app.vpn.AdBlockStats

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "Cần cấp quyền VPN để bắt đầu lọc quảng cáo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdBlockStats.init(applicationContext)

        setContent {
            ReelGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BrandDarkBackground
                ) {
                    HomeScreen(
                        onToggleVpn = { toggleVpn() }
                    )
                }
            }
        }
    }

    private fun toggleVpn() {
        val currentStatus = VpnState.status.value
        if (currentStatus == ConnectionStatus.CONNECTED) {
            stopVpnService()
        } else if (currentStatus == ConnectionStatus.DISCONNECTED) {
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                startVpnService()
            }
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, ReelGuardVpnService::class.java).apply {
            action = ReelGuardVpnService.ACTION_START
        }
        startService(intent)
    }

    private fun stopVpnService() {
        val intent = Intent(this, ReelGuardVpnService::class.java).apply {
            action = ReelGuardVpnService.ACTION_STOP
        }
        startService(intent)
    }
}