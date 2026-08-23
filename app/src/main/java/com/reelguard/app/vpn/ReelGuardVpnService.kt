package com.reelguard.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.reelguard.app.MainActivity
import com.reelguard.app.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class ReelGuardVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var workerThread: Thread? = null
    private var executorService: ExecutorService? = null
    private var upstreamSocket: DatagramSocket? = null
    private val dnsCache = ConcurrentHashMap<String, ByteArray>()
    private val writeLock = Any()
    private var notificationManager: NotificationManager? = null

    companion object {
        const val ACTION_START = "com.reelguard.app.vpn.START"
        const val ACTION_STOP = "com.reelguard.app.vpn.STOP"
        private const val NOTIFICATION_ID = 9991
        private const val CHANNEL_ID = "reelguard_vpn_channel"
        private const val TAG = "ReelGuardVpn"

        private val PRIMARY_DNS = InetAddress.getByName("1.1.1.1")
        private val SECONDARY_DNS = InetAddress.getByName("8.8.8.8")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP -> stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        VpnState.updateStatus(ConnectionStatus.CONNECTING)

        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(AdBlockStats.blockedCount.value))

        try {
            val builder = Builder()
                .setSession("ReelGuard Anti-Ad DNS")
                .setMtu(1500)
                .addAddress("10.88.0.2", 32)
                .addDnsServer("10.88.0.2")
                .addRoute("10.88.0.0", 16)
                // Chặn bắt các DNS IP phổ biến để chống rò rỉ DNS (Anti-DNS Leak)
                .addRoute("8.8.8.8", 32)
                .addRoute("8.8.4.4", 32)
                .addRoute("1.1.1.1", 32)
                .addRoute("1.0.0.1", 32)
                .addRoute("9.9.9.9", 32)
                .addRoute("208.67.222.222", 32)
                .addRoute("208.67.220.220", 32)
                .addRoute("94.140.14.14", 32)
                .addRoute("94.140.15.15", 32)

            // Ngăn chặn rò rỉ IPv6 DNS
            try {
                builder.addAddress("fd00:88::2", 128)
                builder.addDnsServer("fd00:88::2")
                builder.addRoute("fd00:88::", 64)
                builder.addRoute("2001:4860:4860::8888", 128)
                builder.addRoute("2606:4700:4700::1111", 128)
            } catch (e: Exception) {
                Log.w(TAG, "Cấu hình IPv6 bỏ qua: ${e.message}")
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Không thể thiết lập VPN TUN interface")
                stopVpn()
                return
            }

            upstreamSocket = DatagramSocket().apply {
                protect(this) // Bỏ qua VPN để truy cập Internet trực tiếp
                soTimeout = 1200
            }

            executorService = Executors.newCachedThreadPool()
            isRunning = true
            VpnState.updateStatus(ConnectionStatus.CONNECTED)
            Log.i(TAG, "ReelGuard Anti-Ad DNS Shield đã kích hoạt thành công!")

            startDnsLoop(vpnInterface!!)

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khởi tạo VPN", e)
            stopVpn()
        }
    }

    private fun startDnsLoop(pfd: ParcelFileDescriptor) {
        workerThread = thread(start = true, name = "ReelGuard-DnsWorker") {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val packet = ByteArray(32767)

            try {
                while (isRunning) {
                    val length = inputStream.read(packet)
                    if (length <= 0) continue

                    // Parse IPv4 Header
                    val version = (packet[0].toInt() and 0xF0) shr 4
                    if (version != 4) continue

                    val ihl = (packet[0].toInt() and 0x0F) * 4
                    val protocol = packet[9].toInt() and 0xFF

                    // Chỉ chặn bắt gói UDP Port 53 (DNS)
                    if (protocol == 17 && length >= ihl + 8) {
                        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)

                        if (dstPort == 53) {
                            val dnsOffset = ihl + 8
                            val dnsLen = length - dnsOffset

                            if (dnsLen > 12) {
                                val capturedPacket = packet.copyOf(length)
                                val domain = DnsFilterEngine.extractDomainFromDnsPayload(capturedPacket, dnsOffset, dnsLen)

                                if (domain != null && DnsFilterEngine.isAdDomain(domain)) {
                                    // Chặn đứng quảng cáo chèn ngang & theo dõi -> Sinkhole 0.0.0.0
                                    Log.i(TAG, "🚫 [CHẶN ADS FB]: $domain -> Trả về Sinkhole 0.0.0.0")
                                    AdBlockStats.increment()
                                    updateNotificationRealtime()

                                    val dnsResp = DnsFilterEngine.createSinkholeDnsResponse(
                                        capturedPacket.copyOfRange(dnsOffset, length),
                                        dnsLen
                                    )
                                    val responseIpPacket = buildValidIpUdpResponse(capturedPacket, ihl, dnsResp)
                                    synchronized(writeLock) {
                                        outputStream.write(responseIpPacket)
                                        outputStream.flush()
                                    }
                                } else {
                                    // Xử lý DNS bất đồng bộ cho video organic & GraphQL để tối ưu 100% tốc độ
                                    executorService?.execute {
                                        resolveAndForwardDns(
                                            capturedPacket = capturedPacket,
                                            ihl = ihl,
                                            dnsOffset = dnsOffset,
                                            dnsLen = dnsLen,
                                            domain = domain,
                                            outputStream = outputStream
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Lỗi vòng lặp DNS", e)
                }
            } finally {
                try {
                    inputStream.close()
                    outputStream.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun updateNotificationRealtime() {
        if (!isRunning) return
        val count = AdBlockStats.blockedCount.value
        val notification = buildNotification(count)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun resolveAndForwardDns(
        capturedPacket: ByteArray,
        ihl: Int,
        dnsOffset: Int,
        dnsLen: Int,
        domain: String?,
        outputStream: FileOutputStream
    ) {
        val queryTxId0 = capturedPacket[dnsOffset]
        val queryTxId1 = capturedPacket[dnsOffset + 1]

        // 1. Kiểm tra DNS Cache cục bộ (0ms response)
        if (domain != null) {
            val cached = dnsCache[domain]
            if (cached != null) {
                val clonedResp = cached.copyOf()
                clonedResp[0] = queryTxId0
                clonedResp[1] = queryTxId1
                val responseIpPacket = buildValidIpUdpResponse(capturedPacket, ihl, clonedResp)
                synchronized(writeLock) {
                    try {
                        outputStream.write(responseIpPacket)
                        outputStream.flush()
                    } catch (_: Exception) {}
                }
                return
            }
        }

        // 2. Chuyển tiếp tới Upstream DNS Cloudflare / Google
        try {
            val forwardQuery = DatagramPacket(capturedPacket, dnsOffset, dnsLen, PRIMARY_DNS, 53)
            val socket = upstreamSocket ?: return
            socket.send(forwardQuery)

            val recvBuffer = ByteArray(2048)
            val upstreamResp = DatagramPacket(recvBuffer, recvBuffer.size)
            socket.receive(upstreamResp)

            val realDnsResp = recvBuffer.copyOf(upstreamResp.length)

            // Lưu cache (tối đa 2000 entries)
            if (domain != null && dnsCache.size < 2000) {
                dnsCache[domain] = realDnsResp
            }

            val responseIpPacket = buildValidIpUdpResponse(capturedPacket, ihl, realDnsResp)
            synchronized(writeLock) {
                outputStream.write(responseIpPacket)
                outputStream.flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "DNS Upstream Cloudflare timeout, thử fallback Google: $domain")
            try {
                val fallbackQuery = DatagramPacket(capturedPacket, dnsOffset, dnsLen, SECONDARY_DNS, 53)
                val socket = upstreamSocket ?: return
                socket.send(fallbackQuery)

                val recvBuffer = ByteArray(2048)
                val upstreamResp = DatagramPacket(recvBuffer, recvBuffer.size)
                socket.receive(upstreamResp)

                val realDnsResp = recvBuffer.copyOf(upstreamResp.length)
                val responseIpPacket = buildValidIpUdpResponse(capturedPacket, ihl, realDnsResp)
                synchronized(writeLock) {
                    outputStream.write(responseIpPacket)
                    outputStream.flush()
                }
            } catch (_: Exception) {
                Log.w(TAG, "DNS Upstream fallback thất bại cho domain: $domain")
            }
        }
    }

    /**
     * Xây dựng gói tin IPv4/UDP chuẩn và tính toán IPv4 Header Checksum chính xác theo RFC 791
     */
    private fun buildValidIpUdpResponse(origIpPacket: ByteArray, ihl: Int, dnsPayload: ByteArray): ByteArray {
        val totalLen = 20 + 8 + dnsPayload.size
        val resp = ByteBuffer.allocate(totalLen)

        // 1. IPv4 Header (20 bytes)
        resp.put(0x45.toByte()) // Version 4, IHL 5
        resp.put(0x00.toByte()) // DSCP / ECN
        resp.putShort(totalLen.toShort()) // Total Length
        resp.putShort(0x4321.toShort()) // Identification
        resp.putShort(0x0000.toShort()) // Flags & Fragment Offset
        resp.put(64.toByte()) // TTL
        resp.put(17.toByte()) // Protocol UDP (17)
        resp.putShort(0.toShort()) // Checksum Placeholder

        // Swap Src IP & Dst IP
        val srcIp = origIpPacket.copyOfRange(12, 16)
        val dstIp = origIpPacket.copyOfRange(16, 20)
        resp.put(dstIp)
        resp.put(srcIp)

        // 2. UDP Header (8 bytes)
        val srcPort = ((origIpPacket[ihl].toInt() and 0xFF) shl 8) or (origIpPacket[ihl + 1].toInt() and 0xFF)
        val dstPort = ((origIpPacket[ihl + 2].toInt() and 0xFF) shl 8) or (origIpPacket[ihl + 3].toInt() and 0xFF)
        resp.putShort(dstPort.toShort()) // Src Port = 53
        resp.putShort(srcPort.toShort()) // Dst Port = Client Port
        resp.putShort((8 + dnsPayload.size).toShort()) // UDP Length
        resp.putShort(0.toShort()) // UDP Checksum (0 = disabled in IPv4)

        // 3. DNS Payload
        resp.put(dnsPayload)

        val rawPacket = resp.array()

        // 4. Tính toán IPv4 Checksum chính xác
        val ipChecksum = calculateIpv4Checksum(rawPacket, 0, 20)
        rawPacket[10] = ((ipChecksum.toInt() shr 8) and 0xFF).toByte()
        rawPacket[11] = (ipChecksum.toInt() and 0xFF).toByte()

        return rawPacket
    }

    private fun calculateIpv4Checksum(data: ByteArray, offset: Int, length: Int): Short {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            if (i == offset + 10) { // Bỏ qua trường Checksum chính nó
                i += 2
                continue
            }
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toShort()
    }

    private fun stopVpn() {
        isRunning = false
        workerThread?.interrupt()
        workerThread = null

        executorService?.shutdownNow()
        executorService = null

        try {
            upstreamSocket?.close()
            upstreamSocket = null
        } catch (_: Exception) {}

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đóng VPN", e)
        }

        dnsCache.clear()
        VpnState.updateStatus(ConnectionStatus.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "ReelGuard Smart DNS Shield đã ngắt kết nối.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_channel_desc)
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(blockedCount: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ReelGuardVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("ReelGuard: Đã chặn $blockedCount quảng cáo")
            .setContentText("Đang bảo vệ luồng video Facebook")
            .setContentIntent(pendingIntent)
            .addAction(0, "Tắt Bảo Vệ", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}