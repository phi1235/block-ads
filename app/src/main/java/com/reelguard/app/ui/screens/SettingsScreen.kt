package com.reelguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelguard.app.ui.theme.BrandCardBackground
import com.reelguard.app.ui.theme.BrandDarkBackground
import com.reelguard.app.ui.theme.BrandRed
import com.reelguard.app.ui.theme.BrandTextPrimary
import com.reelguard.app.ui.theme.BrandTextSecondary
import com.reelguard.app.vpn.VpnState

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val splitTarget by VpnState.splitTunnelTarget.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandDarkBackground)
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = BrandTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Cài Đặt",
                style = MaterialTheme.typography.titleLarge,
                color = BrandTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Split-tunneling info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BrandCardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Gói ứng dụng được bảo vệ (Split-Tunneling)",
                    color = BrandTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = splitTarget,
                    color = BrandTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Chỉ ứng dụng này mới được đưa qua VPN để lọc Reels Ads. Các ứng dụng khác giữ nguyên đường truyền trực tiếp.",
                    color = BrandTextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reset statistics button
        Button(
            onClick = { VpnState.resetStats() },
            colors = ButtonDefaults.buttonColors(containerColor = BrandCardBackground),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = BrandRed
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đặt Lại Số Liệu Thống Kê",
                    color = BrandRed,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
