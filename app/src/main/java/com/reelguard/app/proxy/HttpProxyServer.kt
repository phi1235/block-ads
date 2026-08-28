package com.reelguard.app.proxy

import android.content.Context
import android.util.Log
import com.reelguard.app.vpn.AdBlockStats
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class HttpProxyServer(private val context: Context, private val port: Int = 8888) {

    companion object {
        private const val TAG = "HttpProxyServer"
        private val GRAPH_HOSTS = setOf(
            "graph.facebook.com",
            "b-graph.facebook.com",
            "z-m-graph.facebook.com",
            "z-p3-graph.facebook.com",
            "api.facebook.com"
        )
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var executorService: ExecutorService? = null

    fun start() {
        if (isRunning) return
        CertificateManager.init(context)
        executorService = Executors.newCachedThreadPool()
        isRunning = true

        Thread {
            try {
                serverSocket = ServerSocket(port, 100, java.net.InetAddress.getByName("127.0.0.1"))
                Log.i(TAG, "ReelGuard Surgical HTTPS Proxy Server đã khởi động trên cổng $port")

                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    executorService?.execute { handleClientConnection(clientSocket) }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Lỗi ServerSocket", e)
                }
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        executorService?.shutdownNow()
        executorService = null
        Log.i(TAG, "ReelGuard Surgical HTTPS Proxy Server đã dừng.")
    }

    private fun handleClientConnection(clientSocket: Socket) {
        try {
            clientSocket.soTimeout = 15000
            val clientIn = BufferedInputStream(clientSocket.getInputStream())
            val clientOut = BufferedOutputStream(clientSocket.getOutputStream())

            // Đọc dòng HTTP Request đầu tiên
            val line = readLine(clientIn) ?: run {
                clientSocket.close()
                return
            }

            val parts = line.split(" ")
            if (parts.size < 2) {
                clientSocket.close()
                return
            }

            val method = parts[0]
            val uri = parts[1]

            if (method.equals("CONNECT", ignoreCase = true)) {
                handleHttpsConnect(uri, clientIn, clientOut, clientSocket)
            } else {
                // Xử lý HTTP Proxy thông thường nếu có
                clientSocket.close()
            }
        } catch (_: Exception) {
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun handleHttpsConnect(
        target: String,
        clientIn: BufferedInputStream,
        clientOut: BufferedOutputStream,
        clientSocket: Socket
    ) {
        val hostPort = target.split(":")
        val host = hostPort[0]
        val targetPort = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 443 else 443

        // Đọc hết các header còn lại của CONNECT request
        while (true) {
            val h = readLine(clientIn) ?: break
            if (h.isEmpty()) break
        }

        val shouldIntercept = GRAPH_HOSTS.any { host.equals(it, ignoreCase = true) || host.endsWith(".$it", ignoreCase = true) }

        if (!shouldIntercept) {
            // Passthrough nguyên bản (cho CDN video *.fbcdn.net và các app khác)
            val remoteSocket = Socket()
            remoteSocket.connect(InetSocketAddress(host, targetPort), 10000)
            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            clientOut.flush()

            val remoteIn = remoteSocket.getInputStream()
            val remoteOut = remoteSocket.getOutputStream()

            pipeStreams(clientIn, remoteOut, remoteIn, clientOut, clientSocket, remoteSocket)
        } else {
            // MITM giải mã & bóc tách GraphQL
            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            clientOut.flush()

            try {
                val sslCtx = CertificateManager.getSslContextForHost(host)
                val sslFactory = sslCtx.socketFactory
                val clientSsl = sslFactory.createSocket(clientSocket, host, targetPort, true) as SSLSocket
                clientSsl.useClientMode = false
                clientSsl.startHandshake()

                val defaultSslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val remoteSsl = defaultSslFactory.createSocket(host, targetPort) as SSLSocket
                remoteSsl.startHandshake()

                processGraphQLInterception(clientSsl, remoteSsl)
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi bắt tay TLS MITM cho $host (SSL Pinning kích hoạt): ${e.message}")
            }
        }
    }

    private fun processGraphQLInterception(clientSsl: SSLSocket, remoteSsl: SSLSocket) {
        val clientIn = BufferedInputStream(clientSsl.inputStream)
        val clientOut = BufferedOutputStream(clientSsl.outputStream)
        val remoteIn = BufferedInputStream(remoteSsl.inputStream)
        val remoteOut = BufferedOutputStream(remoteSsl.outputStream)

        try {
            while (isRunning && !clientSsl.isClosed && !remoteSsl.isClosed) {
                // 1. Đọc HTTP Request từ Client và chuyển tiếp lên Remote
                val reqLine = readLine(clientIn) ?: break
                if (reqLine.isEmpty()) break
                remoteOut.write("$reqLine\r\n".toByteArray(StandardCharsets.UTF_8))

                var reqContentLen = 0
                while (true) {
                    val header = readLine(clientIn) ?: break
                    if (header.isEmpty()) {
                        remoteOut.write("\r\n".toByteArray(StandardCharsets.UTF_8))
                        break
                    }
                    if (header.startsWith("Content-Length:", ignoreCase = true)) {
                        reqContentLen = header.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    remoteOut.write("$header\r\n".toByteArray(StandardCharsets.UTF_8))
                }
                remoteOut.flush()

                if (reqContentLen > 0) {
                    val buf = ByteArray(8192)
                    var readTotal = 0
                    while (readTotal < reqContentLen) {
                        val toRead = Math.min(buf.size, reqContentLen - readTotal)
                        val r = clientIn.read(buf, 0, toRead)
                        if (r == -1) break
                        remoteOut.write(buf, 0, r)
                        readTotal += r
                    }
                    remoteOut.flush()
                }

                // 2. Đọc HTTP Response từ Remote
                val respLine = readLine(remoteIn) ?: break
                val headers = mutableListOf<String>()
                var respContentLen = -1
                var isGzip = false
                var isChunked = false

                while (true) {
                    val header = readLine(remoteIn) ?: break
                    if (header.isEmpty()) break
                    headers.add(header)
                    if (header.startsWith("Content-Length:", ignoreCase = true)) {
                        respContentLen = header.substringAfter(":").trim().toIntOrNull() ?: -1
                    }
                    if (header.startsWith("Content-Encoding:", ignoreCase = true) && header.contains("gzip", ignoreCase = true)) {
                        isGzip = true
                    }
                    if (header.startsWith("Transfer-Encoding:", ignoreCase = true) && header.contains("chunked", ignoreCase = true)) {
                        isChunked = true
                    }
                }

                // Đọc response body
                val bodyBytes = if (respContentLen >= 0) {
                    readExactBytes(remoteIn, respContentLen)
                } else if (isChunked) {
                    readChunkedBody(remoteIn)
                } else {
                    ByteArray(0)
                }

                // Bóc tách JSON quảng cáo
                val (cleanedBytes, modified) = GraphQLAdStripper.stripAdsFromPayload(bodyBytes, isGzip)
                if (modified) {
                    Log.i(TAG, "✂️ [REELGUARD PHẪU THUẬT GRAPHQL]: Đã xóa sạch in_stream_ad & sponsored stories!")
                    AdBlockStats.increment()
                }

                // Gửi response sạch về client
                clientOut.write("$respLine\r\n".toByteArray(StandardCharsets.UTF_8))
                for (h in headers) {
                    if (h.startsWith("Content-Length:", ignoreCase = true)) {
                        clientOut.write("Content-Length: ${cleanedBytes.size}\r\n".toByteArray(StandardCharsets.UTF_8))
                    } else if (h.startsWith("Transfer-Encoding:", ignoreCase = true) && !isChunked) {
                        // Bỏ qua
                    } else {
                        clientOut.write("$h\r\n".toByteArray(StandardCharsets.UTF_8))
                    }
                }
                clientOut.write("\r\n".toByteArray(StandardCharsets.UTF_8))
                clientOut.write(cleanedBytes)
                clientOut.flush()
            }
        } catch (_: Exception) {
        } finally {
            try { clientSsl.close() } catch (_: Exception) {}
            try { remoteSsl.close() } catch (_: Exception) {}
        }
    }

    private fun readExactBytes(input: InputStream, length: Int): ByteArray {
        val res = ByteArray(length)
        var total = 0
        while (total < length) {
            val r = input.read(res, total, length - total)
            if (r == -1) break
            total += r
        }
        return res
    }

    private fun readChunkedBody(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readLine(input) ?: break
            val size = sizeLine.trim().split(";")[0].toIntOrNull(16) ?: break
            if (size == 0) {
                readLine(input) // CRLF cuối
                break
            }
            val chunk = readExactBytes(input, size)
            out.write(chunk)
            readLine(input) // CRLF sau chunk
        }
        return out.toByteArray()
    }

    private fun pipeStreams(
        in1: InputStream, out1: OutputStream,
        in2: InputStream, out2: OutputStream,
        s1: Socket, s2: Socket
    ) {
        val executor = Executors.newFixedThreadPool(2)
        executor.execute {
            try {
                val buf = ByteArray(16384)
                while (isRunning) {
                    val r = in1.read(buf)
                    if (r <= 0) break
                    out1.write(buf, 0, r)
                    out1.flush()
                }
            } catch (_: Exception) {}
            finally {
                try { s1.close() } catch (_: Exception) {}
                try { s2.close() } catch (_: Exception) {}
            }
        }

        executor.execute {
            try {
                val buf = ByteArray(16384)
                while (isRunning) {
                    val r = in2.read(buf)
                    if (r <= 0) break
                    out2.write(buf, 0, r)
                    out2.flush()
                }
            } catch (_: Exception) {}
            finally {
                try { s1.close() } catch (_: Exception) {}
                try { s2.close() } catch (_: Exception) {}
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var prev = 0
        while (true) {
            val c = input.read()
            if (c == -1) {
                return if (sb.isNotEmpty()) sb.toString() else null
            }
            if (c == '\n'.code && prev == '\r'.code) {
                if (sb.isNotEmpty()) sb.setLength(sb.length - 1)
                return sb.toString()
            }
            sb.append(c.toChar())
            prev = c
        }
    }
}
