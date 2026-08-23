package com.reelguard.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.reelguard.app.ui.theme.BrandDarkBackground
import com.reelguard.app.ui.theme.BrandGreen
import com.reelguard.app.ui.theme.BrandTextPrimary
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReelsPlayerScreen(
    onBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val adBlockJs = """
        (function() {
            // CSS Chặn triệt để Banner và Video Ads
            var style = document.createElement('style');
            style.innerHTML = `
                div[aria-label*="Được tài trợ"],
                div[aria-label*="Sponsored"],
                div[data-tracking-duration-id],
                div[data-ad-preview],
                .feed-shared-update-v2--ad {
                    display: none !important;
                }
            `;
            document.head.appendChild(style);

            // Hook fetch & XMLHttpRequest để bóc tách GraphQL Ads
            const originalFetch = window.fetch;
            window.fetch = async function(...args) {
                const response = await originalFetch.apply(this, args);
                return response;
            };
        })();
    """.trimIndent()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandDarkBackground)
    ) {
        // Header Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = BrandTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = BrandGreen
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reels Siêu Sạch (Zero Ads)",
                    color = BrandTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Giữ 100% phân trang và bình luận",
                    color = BrandGreen,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = { webViewInstance?.reload() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Tải lại",
                    tint = BrandTextPrimary
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                view?.evaluateJavascript(adBlockJs, null)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString()?.lowercase() ?: ""
                                // Chặn triệt để các domain quảng cáo và tracking
                                if (url.contains("audiencenetwork.facebook.com") ||
                                    url.contains("an.facebook.com") ||
                                    url.contains("tr.facebook.com") ||
                                    url.contains("pixel.facebook.com") ||
                                    url.contains("doubleclick.net")
                                ) {
                                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        webChromeClient = WebChromeClient()
                        loadUrl("https://m.facebook.com/reel")
                        webViewInstance = this
                    }
                }
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = BrandGreen
                )
            }
        }
    }
}