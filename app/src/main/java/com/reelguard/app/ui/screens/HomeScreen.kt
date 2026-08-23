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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    onToggleVpn: () -> Unit,
    onNavigateGuide: () -> Unit,
    onNavigateReelsPlayer: () -> Unit
) {
    val vpnStatus by VpnState.status.collectAsState()
    val blockedCount by AdBlockStats.blockedCount.collectAsState()
    val isConnected = vpnStatus == ConnectionStatus.CONNECTED
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandDarkBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = BrandGreen,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Chặn Ads Reels & Giữ Phân Trang",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandTextSecondary
                )
            }
            IconButton(onClick = onNavigateGuide) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Hướng dẫn",
                    tint = BrandTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Big Power Toggle Button
        VpnToggleButton(
            status = vpnStatus,
            onClick = onToggleVpn
        )

        Spacer(modifier = Modifier.height(20.dp))

        // METHOD 2: Zero-Ads Reels Player Action Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateReelsPlayer() },
            colors = CardDefaults.cardColors(containerColor = BrandCardBackground),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(BrandBlue, BrandGreen)),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mở Trình Xem Reels Siêu Sạch",
                        color = BrandTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Không bao giờ có quảng cáo • Giữ 100% comment",
                        color = BrandGreen,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Indicators (2 Cards: Blocked Ads & Pagination Status)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        Spacer(modifier = Modifier.height(16.dp))

        // Split-Tunneling Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BrandCardBackground),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Chế độ Smart Stream Shield",
                        color = BrandTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Chỉ lọc luồng quảng cáo FB. 100% app ngân hàng & game chạy trực tiếp an toàn, không tốn pin.",
                        color = BrandTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
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
            .size(190.dp)
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
                modifier = Modifier.size(54.dp),
                tint = if (isConnected) BrandDarkBackground else Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (status) {
                    ConnectionStatus.CONNECTED -> "ĐANG BẬT"
                    ConnectionStatus.CONNECTING -> "ĐANG KẾT NỐI..."
                    ConnectionStatus.DISCONNECTED -> "BẤM ĐỂ BẬT"
                },
                color = if (isConnected) BrandDarkBackground else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = BrandCardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
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
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = BrandTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}