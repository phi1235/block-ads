package com.reelguard.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelguard.app.R
import com.reelguard.app.ui.theme.BrandBlue
import com.reelguard.app.ui.theme.BrandCardBackground
import com.reelguard.app.ui.theme.BrandDarkBackground
import com.reelguard.app.ui.theme.BrandGreen
import com.reelguard.app.ui.theme.BrandRed
import com.reelguard.app.ui.theme.BrandTextPrimary
import com.reelguard.app.ui.theme.BrandTextSecondary
import com.reelguard.app.vpn.AdBlockStats
import com.reelguard.app.vpn.ConnectionStatus
import com.reelguard.app.vpn.VpnState

@Composable
fun HomeScreen(
    onToggleVpn: () -> Unit
) {
    val vpnStatus by VpnState.status.collectAsState()
    val blockedCount by AdBlockStats.blockedCount.collectAsState()
    val isConnected = vpnStatus == ConnectionStatus.CONNECTED
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandDarkBackground)
            .padding(horizontal = 20.dp, vertical = 28.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = BrandGreen,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Lá Chắn Smart DNS - Chặn Ads In-Stream Facebook",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Big Power Toggle Button (Smart DNS Shield)
        VpnToggleButton(
            status = vpnStatus,
            onClick = onToggleVpn
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Live Real-time Status Indicators (2 Cards: Blocked Ads & Pagination Status)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusCard(
                title = "Quảng cáo đã chặn",
                value = "$blockedCount",
                icon = Icons.Default.Block,
                iconColor = BrandRed,
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "Phân trang Reels",
                value = if (isConnected) "Bảo tồn 100%" else "Sẵn sàng",
                icon = Icons.Default.CheckCircle,
                iconColor = BrandGreen,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card Lọc HTTPS Phẫu thuật (Surgical GraphQL Rewriter)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = BrandCardBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = BrandBlue,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Lọc HTTPS Phẫu Thuật GraphQL",
                        style = MaterialTheme.typography.titleSmall,
                        color = BrandTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tự động bóc tách in-stream ads & bảo tồn end_cursor phân trang video.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun VpnToggleButton(
    status: ConnectionStatus,
    onClick: () -> Unit
) {
    val isConnected = status == ConnectionStatus.CONNECTED
    val isConnecting = status == ConnectionStatus.CONNECTING

    val buttonScale by animateFloatAsState(
        targetValue = if (isConnected) 1.05f else 1.0f,
        animationSpec = tween(300),
        label = "ButtonScale"
    )

    val gradientBrush = when (status) {
        ConnectionStatus.CONNECTED -> Brush.radialGradient(listOf(BrandGreen, Color(0xFF00BFA5)))
        ConnectionStatus.CONNECTING -> Brush.radialGradient(listOf(BrandBlue, Color(0xFF1E88E5)))
        ConnectionStatus.DISCONNECTED -> Brush.radialGradient(listOf(Color(0xFF2C303E), Color(0xFF1F222E)))
    }

    Box(
        modifier = Modifier
            .size(200.dp)
            .scale(buttonScale)
            .background(brush = gradientBrush, shape = CircleShape)
            .clickable(enabled = !isConnecting) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = "Bật/Tắt VPN",
                modifier = Modifier.size(60.dp),
                tint = if (isConnected) BrandDarkBackground else Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when (status) {
                    ConnectionStatus.CONNECTED -> "ĐANG BẬT"
                    ConnectionStatus.CONNECTING -> "ĐANG KẾT NỐI..."
                    ConnectionStatus.DISCONNECTED -> "BẤM ĐỂ BẬT"
                },
                color = if (isConnected) BrandDarkBackground else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BrandCardBackground),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = BrandTextSecondary,
                    fontSize = 12.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                color = BrandTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}