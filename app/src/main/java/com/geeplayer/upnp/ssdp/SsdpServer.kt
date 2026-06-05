package com.geeplayer.upnp.ssdp

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.geeplayer.upnp.core.UpnpConstants
import com.geeplayer.upnp.core.UpnpStack
import kotlinx.coroutines.*
import java.net.*
import java.util.*
import java.text.SimpleDateFormat

class SsdpServer(
    private val context: Context,
    private val upnpStack: UpnpStack,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private companion object {
        private const val TAG = "SsdpServer"
        private const val BUFFER_SIZE = 8192
    }

    private var multicastSocket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var listenJob: Job? = null
    private var aliveJob: Job? = null
    private var isRunning = false

    private val locationTemplate: String get() = upnpStack.deviceDescUrl
    private val dateHeader: String get() = "DATE: " + SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).format(Date()) + "\r\n"
    private val serverName: String get() = UpnpConstants.Ssdp.SERVER
    private val usnRoot: String get() = "${upnpStack.udn}::upnp:rootdevice"
    private val usnDevice: String get() = "${upnpStack.udn}::${UpnpConstants.URN_DEVICE}"
    private val usnAVT: String get() = "${upnpStack.udn}::${UpnpConstants.URN_AVT}"
    private val usnRC: String get() = "${upnpStack.udn}::${UpnpConstants.URN_RC}"
    private val usnCmgr: String get() = "${upnpStack.udn}::${UpnpConstants.URN_CMGR}"

    fun start() {
        if (isRunning) return
        isRunning = true
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("dlna-ssdp-multicast-lock")
            multicastLock?.setReferenceCounted(false)
            multicastLock?.acquire()
            Log.d(TAG, "MulticastLock acquired")

            val group = InetAddress.getByName(UpnpConstants.SSDP_ADDRESS)
            multicastSocket = MulticastSocket(UpnpConstants.SSDP_PORT).apply {
                reuseAddress = true
                timeToLive = 4
                soTimeout = 0
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val ni = interfaces.nextElement()
                    if (ni.isUp && !ni.isLoopback && ni.supportsMulticast()) {
                        try { joinGroup(InetSocketAddress(group, 0), ni); Log.d(TAG, "Joined on ${ni.displayName}") }
                        catch (e: Exception) { Log.w(TAG, "Failed join ${ni.displayName}: ${e.message}") }
                    }
                }
            }

            listenJob = scope.launch { listenLoop() }
            aliveJob = scope.launch { aliveLoop() }
            scope.launch { delay(UpnpConstants.SSDP_BOOTUP_INTERVAL_MS); sendAlive() }
            Log.i(TAG, "SSDP Server started on ${UpnpConstants.SSDP_PORT}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SSDP server", e)
            isRunning = false
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try { sendByeBye() } catch (e: Exception) { Log.w(TAG, "byebye error", e) }
        listenJob?.cancel(); aliveJob?.cancel()
        try {
            multicastSocket?.let { socket ->
                val group = InetAddress.getByName(UpnpConstants.SSDP_ADDRESS)
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val ni = interfaces.nextElement()
                    try { socket.leaveGroup(InetSocketAddress(group, 0), ni) } catch (_: Exception) { }
                }
                socket.close()
            }
        } catch (e: Exception) { Log.w(TAG, "close error", e) }
        multicastLock?.let { if (it.isHeld) try { it.release() } catch (_: Exception) { } }
        Log.i(TAG, "SSDP Server stopped")
    }

    private suspend fun listenLoop() {
        val buffer = ByteArray(BUFFER_SIZE)
        val packet = DatagramPacket(buffer, buffer.size)
        while (isRunning && multicastSocket?.isClosed == false) {
            try {
                multicastSocket?.receive(packet)
                val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (message.startsWith("M-SEARCH")) {
                    parseSearchTarget(message)?.let { st ->
                        Log.d(TAG, "M-SEARCH for $st from ${packet.address}:${packet.port}")
                        val usns = when (st) {
                            UpnpConstants.Ssdp.ST_ALL -> listOf(usnRoot, usnDevice, usnAVT, usnRC, usnCmgr)
                            "upnp:rootdevice" -> listOf(usnRoot)
                            UpnpConstants.URN_DEVICE -> listOf(usnDevice)
                            UpnpConstants.URN_AVT -> listOf(usnAVT)
                            UpnpConstants.URN_RC -> listOf(usnRC)
                            UpnpConstants.URN_CMGR -> listOf(usnCmgr)
                            else -> {
                            // 兼容搜索: 如果 ST 包含我们的 URN 或设备类型，也响应
                            if (st == upnpStack.udn || st.contains("MediaRenderer") || st.contains(UpnpConstants.URN_AVT) || st.contains(UpnpConstants.URN_RC) || st.contains(UpnpConstants.URN_CMGR)) {
                                listOf(usnDevice)
                            } else null
                        }
                        }
                        // 在 SSDP 响应中，ST 不应含 UUID 前缀（只取 :: 后面的部分）
                        // 例如 USN="uuid:xxx::upnp:rootdevice" -> ST="upnp:rootdevice"
                        usns?.forEach { fullUsn ->
                            val searchTarget = fullUsn.substringAfter("::", fullUsn)
                            sendMSearchResponse(fullUsn, searchTarget, packet.address, packet.port)
                        }
                    }
                }
            } catch (e: SocketException) { if (isRunning) Log.w(TAG, "Socket error", e) }
            catch (e: Exception) { if (isRunning) Log.w(TAG, "Listen error", e) }
        }
    }

    private fun sendMSearchResponse(usn: String, st: String, targetAddr: InetAddress, targetPort: Int) {
        try {
            val msg = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("CACHE-CONTROL: max-age=1800\r\n")
                append("EXT:\r\n")
                append(dateHeader)
                append("LOCATION: $locationTemplate\r\n")
                append("SERVER: $serverName\r\n")
                append("ST: $st\r\n")
                append("USN: $usn\r\n")
                append("X-AV-Physical-Unit-Information: NA\r\n")
                append("BOOTID.UPNP.ORG: 1\r\n")
                append("CONFIGID.UPNP.ORG: 1\r\n")
                append("\r\n")
            }
            multicastSocket?.send(DatagramPacket(msg.toByteArray(Charsets.UTF_8), msg.toByteArray(Charsets.UTF_8).size, targetAddr, targetPort))
            Log.v(TAG, "MSearch response sent to $targetAddr:$targetPort")
        } catch (e: Exception) { Log.w(TAG, "Failed to send M-SEARCH response", e) }
    }

    private suspend fun aliveLoop() {
        while (isRunning) { delay(UpnpConstants.SSDP_ALIVE_INTERVAL_MS); if (isRunning) sendAlive() }
    }

    private fun sendAlive() {
        try {
            val group = InetAddress.getByName(UpnpConstants.SSDP_ADDRESS)
            listOf(
                Pair("upnp:rootdevice", usnRoot),
                Pair(UpnpConstants.URN_DEVICE, usnDevice),
                Pair(UpnpConstants.URN_AVT, usnAVT),
                Pair(UpnpConstants.URN_RC, usnRC),
                Pair(UpnpConstants.URN_CMGR, usnCmgr)
            ).forEach { (nt, usn) ->
                val msg = buildString {
                    append("NOTIFY * HTTP/1.1\r\n")
                    append("HOST: ${UpnpConstants.SSDP_ADDRESS}:${UpnpConstants.SSDP_PORT}\r\n")
                    append("CACHE-CONTROL: max-age=1800\r\n")
                    append("LOCATION: $locationTemplate\r\n")
                    append("NT: $nt\r\n")
                    append("NTS: ssdp:alive\r\n")
                    append(dateHeader)
                    append("SERVER: $serverName\r\n")
                    append("USN: $usn\r\n")
                    append("\r\n")
                }
                multicastSocket?.send(DatagramPacket(msg.toByteArray(Charsets.UTF_8), msg.toByteArray(Charsets.UTF_8).size, group, UpnpConstants.SSDP_PORT))
            }
            Log.v(TAG, "Sent SSDP alive")
        } catch (e: Exception) { Log.w(TAG, "Failed to send alive", e) }
    }

    private fun sendByeBye() {
        try {
            val group = InetAddress.getByName(UpnpConstants.SSDP_ADDRESS)
            listOf("upnp:rootdevice" to usnRoot, UpnpConstants.URN_DEVICE to usnDevice, UpnpConstants.URN_AVT to usnAVT, UpnpConstants.URN_RC to usnRC, UpnpConstants.URN_CMGR to usnCmgr).forEach { (nt, usn) ->
                val msg = "NOTIFY * HTTP/1.1\r\nHOST: ${UpnpConstants.SSDP_ADDRESS}:${UpnpConstants.SSDP_PORT}\r\nNT: $nt\r\nNTS: ssdp:byebye\r\nUSN: $usn\r\n\r\n"
                multicastSocket?.send(DatagramPacket(msg.toByteArray(Charsets.UTF_8), msg.toByteArray(Charsets.UTF_8).size, group, UpnpConstants.SSDP_PORT))
            }
            Log.d(TAG, "Sent SSDP byebye")
        } catch (e: Exception) { Log.w(TAG, "byebye error", e) }
    }

    private fun parseSearchTarget(message: String): String? {
        // 兼容各种 ST 格式: "ST:xxx", "ST: xxx", "ST:  xxx"
        return message.lines().firstOrNull { it.startsWith("ST:", true) }
            ?.substringAfter(":")?.trim()
            ?.removeSurrounding("\"")
    }
}
