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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.reelguard.app.ui.screens.GuideScreen
import com.reelguard.app.ui.screens.HomeScreen
import com.reelguard.app.ui.screens.ReelsPlayerScreen
import com.reelguard.app.ui.screens.SettingsScreen
import com.reelguard.app.ui.theme.BrandDarkBackground
import com.reelguard.app.ui.theme.ReelGuardTheme
import com.reelguard.app.vpn.ConnectionStatus
import com.reelguard.app.vpn.ReelGuardVpnService
import com.reelguard.app.vpn.VpnState

enum class CurrentScreen {
    HOME,
    GUIDE,
    SETTINGS,
    REELS_PLAYER
}

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

        setContent {
            ReelGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BrandDarkBackground
                ) {
                    var currentScreen by remember { mutableStateOf(CurrentScreen.HOME) }

                    when (currentScreen) {
                        CurrentScreen.HOME -> {
                            HomeScreen(
                                onToggleVpn = { toggleVpn() },
                                onNavigateGuide = { currentScreen = CurrentScreen.GUIDE },
                                onNavigateReelsPlayer = { currentScreen = CurrentScreen.REELS_PLAYER }
                            )
                        }
                        CurrentScreen.GUIDE -> {
                            GuideScreen(
                                onBack = { currentScreen = CurrentScreen.HOME }
                            )
                        }
                        CurrentScreen.SETTINGS -> {
                            SettingsScreen(
                                onBack = { currentScreen = CurrentScreen.HOME }
                            )
                        }
                        CurrentScreen.REELS_PLAYER -> {
                            ReelsPlayerScreen(
                                onBack = { currentScreen = CurrentScreen.HOME }
                            )
                        }
                    }
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