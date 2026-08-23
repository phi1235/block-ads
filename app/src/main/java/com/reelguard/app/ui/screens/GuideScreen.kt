package com.reelguard.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelguard.app.cert.CertificateHelper
import com.reelguard.app.patcher.FacebookPatcher
import com.reelguard.app.ui.theme.BrandBlue
import com.reelguard.app.ui.theme.BrandCardBackground
import com.reelguard.app.ui.theme.BrandDarkBackground
import com.reelguard.app.ui.theme.BrandGreen
import com.reelguard.app.ui.theme.BrandTextPrimary
import com.reelguard.app.ui.theme.BrandTextSecondary
import kotlinx.coroutines.launch

@Composable
fun GuideScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandDarkBackground)
            .padding(20.dp)
            .verticalScroll(scrollState)
    ) {
        // Header with Back Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = BrandTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Hướng Dẫn Tự Động 2 Bước",
                style = MaterialTheme.typography.titleLarge,
                color = BrandTextPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Step 1: Install Root CA
        StepCard(
            stepNumber = "1",
            title = "Cài Đặt Chứng Chỉ Root CA",
            description = "Để ReelGuard có thể đọc và bóc tách các gói quảng cáo trong Reels, điện thoại cần tin tưởng chứng chỉ CA nội bộ."
        ) {
            Button(
                onClick = { CertificateHelper.openInstallCaIntent(context) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = BrandDarkBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cài Đặt Chứng Chỉ CA (1-Click)",
                    color = BrandDarkBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step 2: Auto-Patch Facebook
        StepCard(
            stepNumber = "2",
            title = "1-Click Tự Động Patch Facebook",
            description = "ReelGuard tự động trích xuất Facebook trên máy, gỡ SSL Pinning, ký số lại và bật hộp thoại cài đặt cho bạn."
        ) {
            Button(
                onClick = {
                    scope.launch {
                        FacebookPatcher.startAutoPatch(context)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chạy Auto-Patch Facebook Ngay",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step 3: Turn on ReelGuard & Enjoy
        StepCard(
            stepNumber = "3",
            title = "Bật Bảo Vệ & Lướt Reels Vô Tận",
            description = "Quay lại trang chủ > Bấm Nút Tròn để bật VPN > Mở Facebook và thưởng thức video hoàn toàn sạch quảng cáo!"
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = BrandGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hỗ trợ bật/tắt nhanh qua Quick Settings Tile.",
                    color = BrandTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun StepCard(
    stepNumber: String,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BrandCardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stepNumber,
                    color = BrandDarkBackground,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(BrandGreen, shape = CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = BrandTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                color = BrandTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}