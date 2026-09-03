package com.example.agyremote.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

data class DiscoveredHost(
    val ip: String,
    val port: Int = 4400,
    val responseTimeMs: Long
)

class LanScanner {

    /**
     * Lấy địa chỉ IP IPv4 nội bộ hiện tại của thiết bị Android trong mạng WiFi/Ethernet
     */
    fun getDeviceLocalIp(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Kiểm tra một host cụ thể có mở cổng và phản hồi nhanh không
     */
    suspend fun checkHost(ip: String, port: Int = 4400, timeoutMs: Int = 400): DiscoveredHost? {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                    val elapsed = System.currentTimeMillis() - startTime
                    DiscoveredHost(ip, port, elapsed)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Quét nhanh toàn bộ subnet mạng WiFi (ví dụ: 192.168.1.1 - 254) tìm kiếm máy chủ AGY
     */
    suspend fun scanSubnet(
        baseIpPrefix: String? = null,
        port: Int = 4400,
        onHostFound: ((DiscoveredHost) -> Unit)? = null
    ): List<DiscoveredHost> = withContext(Dispatchers.IO) {
        val myIp = baseIpPrefix ?: getDeviceLocalIp() ?: return@withContext emptyList()
        val lastDotIndex = myIp.lastIndexOf('.')
        if (lastDotIndex == -1) return@withContext emptyList()
        val subnet = myIp.substring(0, lastDotIndex + 1)

        val discovered = mutableListOf<DiscoveredHost>()
        val semaphore = Semaphore(30) // Giới hạn tối đa 30 sockets đồng thời

        coroutineScope {
            val tasks = (1..254).map { i ->
                async {
                    semaphore.withPermit {
                        val testIp = "$subnet$i"
                        val host = checkHost(testIp, port, timeoutMs = 350)
                        if (host != null) {
                            synchronized(discovered) {
                                discovered.add(host)
                            }
                            onHostFound?.invoke(host)
                        }
                    }
                }
            }
            tasks.awaitAll()
        }

        discovered.sortedBy { it.responseTimeMs }
    }
}
