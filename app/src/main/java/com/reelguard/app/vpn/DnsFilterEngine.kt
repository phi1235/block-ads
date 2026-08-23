package com.reelguard.app.vpn

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

object AdBlockStats {
    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    fun increment() {
        _blockedCount.value += 1
    }

    fun reset() {
        _blockedCount.value = 0
    }
}

object DnsFilterEngine {
    private const val TAG = "DnsFilter"
    private val AD_DOMAIN_KEYWORDS = listOf(
        "an.facebook.com",
        "audiencenetwork.facebook.com",
        "ads.facebook.com",
        "pixel.facebook.com",
        "tr.facebook.com",
        "admarket.facebook.com",
        "analytics.facebook.com",
        "ad_delivery",
        "page_ads",
        "doubleclick.net",
        "googleads",
        "adservice"
    )

    fun isAdDomain(domain: String): Boolean {
        val lower = domain.lowercase().trimEnd('.')
        return AD_DOMAIN_KEYWORDS.any { lower.contains(it) }
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
     * Tạo gói tin phản hồi DNS trả về 0.0.0.0 (Sinkhole Ad)
     */
    fun createSinkholeDnsResponse(queryPacket: ByteArray, queryLen: Int): ByteArray {
        val res = ByteBuffer.allocate(queryLen + 16)
        // Copy Transaction ID
        res.put(queryPacket[0])
        res.put(queryPacket[1])
        // Flags: Standard query response, No error (0x8180)
        res.put(0x81.toByte())
        res.put(0x80.toByte())
        // QDCOUNT: 1
        res.put(0x00.toByte())
        res.put(0x01.toByte())
        // ANCOUNT: 1 (1 Answer)
        res.put(0x00.toByte())
        res.put(0x01.toByte())
        // NSCOUNT: 0, ARCOUNT: 0
        res.put(0x00.toByte())
        res.put(0x00.toByte())
        res.put(0x00.toByte())
        res.put(0x00.toByte())

        // Copy Questions section
        val questionBytes = queryPacket.copyOfRange(12, queryLen)
        res.put(questionBytes)

        // Answer Section: Name pointer to offset 12 (0xC00C)
        res.put(0xC0.toByte())
        res.put(0x0C.toByte())
        // Type: A (0x0001)
        res.put(0x00.toByte())
        res.put(0x01.toByte())
        // Class: IN (0x0001)
        res.put(0x00.toByte())
        res.put(0x01.toByte())
        // TTL: 300 seconds (0x0000012C)
        res.put(0x00.toByte())
        res.put(0x00.toByte())
        res.put(0x01.toByte())
        res.put(0x2C.toByte())
        // Data Length: 4 bytes
        res.put(0x00.toByte())
        res.put(0x04.toByte())
        // IP: 0.0.0.0
        res.put(0x00.toByte())
        res.put(0x00.toByte())
        res.put(0x00.toByte())
        res.put(0x00.toByte())

        return res.array().copyOf(res.position())
    }
}