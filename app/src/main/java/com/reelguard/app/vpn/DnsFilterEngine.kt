package com.reelguard.app.vpn

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

object AdBlockStats {
    private const val PREFS_NAME = "reelguard_stats"
    private const val KEY_BLOCKED_COUNT = "blocked_count"

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedCount = prefs?.getInt(KEY_BLOCKED_COUNT, 0) ?: 0
            _blockedCount.value = savedCount
        }
    }

    @Synchronized
    fun increment() {
        val newCount = _blockedCount.value + 1
        _blockedCount.value = newCount
        prefs?.edit()?.putInt(KEY_BLOCKED_COUNT, newCount)?.apply()
    }

    @Synchronized
    fun reset() {
        _blockedCount.value = 0
        prefs?.edit()?.putInt(KEY_BLOCKED_COUNT, 0)?.apply()
    }
}

object DnsFilterEngine {
    private const val TAG = "DnsFilter"
    
    // 1. Danh sách tên miền phân phối quảng cáo, Telemetry & Ad Beacons chuyên biệt của Meta & Ad SDKs
    private val AD_DOMAINS = setOf(
        "an.facebook.com",
        "audiencenetwork.facebook.com",
        "ads.facebook.com",
        "admarket.facebook.com",
        "pixel.facebook.com",
        "tr.facebook.com",
        "analytics.facebook.com",
        "adservices.meta.com",
        "meta.adservices.com",
        "ad.atdmt.com",
        "ads.instagram.com",
        "ads.fb.com",
        "facebookads.com",
        "graph-video.facebook.com",
        "graph-video-ads.facebook.com",
        "ads-api.facebook.com",
        "ads-api.meta.com",
        "ad_delivery.facebook.com",
        "ads_telemetry.facebook.com",
        "rupload.facebook.com",
        "logging.facebook.com",
        "gateway.facebook.com",
        "sonar.facebook.com",
        "mon.facebook.com",
        "telemetry.facebook.com",
        "advertiser.facebook.com",
        "connect.facebook.net",
        "events.facebook.com",
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "taboola.com",
        "outbrain.com",
        "appsflyer.com",
        "adjust.com",
        "app-measurement.com",
        "inmobi.com",
        "vungle.com",
        "unityads.unity3d.com",
        "ironsrc.com",
        "branch.io",
        "kochava.com",
        "singular.net"
    )

    private val AD_PATTERNS = listOf(
        "ad_delivery",
        "graph-video-ads",
        "page_ads",
        "audiencenetwork",
        "adservices",
        "video-ads",
        "ads-api",
        "ads_telemetry"
    )

    // 2. Whitelist BẮT BUỘC để bảo tồn 100% phân trang Reels (GraphQL) và luồng video CDN
    private val WHITELIST_DOMAINS = listOf(
        "graph.facebook.com",
        "b-graph.facebook.com",
        "z-m-graph.facebook.com",
        "z-p3-graph.facebook.com",
        "api.facebook.com",
        "b-api.facebook.com",
        "fbcdn.net",
        "fbsbx.com",
        "cdninstagram.com",
        "lookaside.facebook.com",
        "static.xx.fbcdn.net"
    )

    fun isAdDomain(domain: String): Boolean {
        val lower = domain.lowercase().trimEnd('.')

        // 1. Kiểm tra chính xác domain quảng cáo chèn ngang & tracking
        if (AD_DOMAINS.any { lower == it || lower.endsWith(".$it") }) {
            return true
        }

        // 2. Nếu nằm trong Whitelist phân trang & CDN video -> Tuyệt đối KHÔNG chặn
        if (WHITELIST_DOMAINS.any { lower == it || lower.endsWith(".$it") }) {
            return false
        }

        // 3. Kiểm tra các mẫu từ khóa quảng cáo
        return AD_PATTERNS.any { lower.contains(it) }
    }

    /**
     * Trích xuất tên miền (Domain Name) từ gói tin DNS Query (RFC 1035)
     */
    fun extractDomainFromDnsPayload(payload: ByteArray, offset: Int, length: Int): String? {
        if (length < 12) return null
        try {
            var pos = offset + 12 // Bỏ qua 12-byte DNS header
            val sb = StringBuilder()

            while (pos < offset + length) {
                val labelLen = payload[pos].toInt() and 0xFF
                if (labelLen == 0) break
                pos++
                if (pos + labelLen > offset + length) return null

                if (sb.isNotEmpty()) sb.append('.')
                for (i in 0 until labelLen) {
                    sb.append(payload[pos + i].toInt().toChar())
                }
                pos += labelLen
            }
            return if (sb.isNotEmpty()) sb.toString() else null
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Trích xuất Question Type (Type 1 = A, Type 28 = AAAA) từ DNS Query
     */
    fun extractQuestionType(payload: ByteArray, offset: Int, length: Int): Int? {
        if (length < 16) return null
        try {
            var pos = offset + 12
            while (pos < offset + length) {
                val labelLen = payload[pos].toInt() and 0xFF
                pos++
                if (labelLen == 0) break
                if ((labelLen and 0xC0) == 0xC0) {
                    pos++
                    break
                }
                if (labelLen > 63 || pos + labelLen > offset + length) return null
                pos += labelLen
            }
            if (pos + 4 <= offset + length) {
                val qType = ((payload[pos].toInt() and 0xFF) shl 8) or (payload[pos + 1].toInt() and 0xFF)
                return qType
            }
            return null
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Tạo gói tin phản hồi DNS NXDOMAIN chuẩn RFC 1035 (RCODE = 3: Name Error)
     * Giúp client drop ngay lập tức với 0ms timeout, không làm treo HTTP/2 connection pool
     */
    fun createBlockedDnsResponse(queryPacket: ByteArray, queryLen: Int): ByteArray {
        if (queryLen < 12) return queryPacket

        var pos = 12
        while (pos < queryLen) {
            val labelLen = queryPacket[pos].toInt() and 0xFF
            pos++
            if (labelLen == 0) break
            if ((labelLen and 0xC0) == 0xC0) {
                pos++
                break
            }
            if (labelLen > 63 || pos + labelLen > queryLen) break
            pos += labelLen
        }

        val questionEnd = if (pos + 4 <= queryLen) pos + 4 else queryLen
        val response = queryPacket.copyOfRange(0, questionEnd)

        // Set Flags: QR=1 (Response), AA=0, TC=0, RD=Query RD, RA=1, Z=0, RCODE=3 (NXDOMAIN)
        val origRd = queryPacket[2].toInt() and 0x01
        response[2] = (0x80 or origRd).toByte()
        response[3] = 0x83.toByte() // RA (0x80) | NXDOMAIN (0x03)

        // QDCOUNT = 1 (giữ nguyên từ query)
        // Reset ANCOUNT, NSCOUNT, ARCOUNT = 0
        if (response.size >= 12) {
            response[6] = 0
            response[7] = 0
            response[8] = 0
            response[9] = 0
            response[10] = 0
            response[11] = 0
        }

        return response
    }
}