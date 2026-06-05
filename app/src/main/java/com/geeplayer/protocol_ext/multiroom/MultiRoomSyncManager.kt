package com.geeplayer.protocol_ext.multiroom

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 多设备同步播放管理器 (Multi-room / Sonos-like)
 *
 * 实现:
 * 1. 设备发现: UDP 组播 (地址 239.195.195.195:49821)
 * 2. 时钟同步: 往返时间 (RTT) 测量，计算播放偏移
 * 3. 播放同步: Leader 广播播放状态，Followers 根据偏移同步
 * 4. 延迟补偿: 根据 RTT 动态调整播放偏移
 */
class MultiRoomSyncManager {
    private companion object {
        private const val TAG = "MultiRoomSyncManager"
        private const val MULTICAST_ADDR = "239.195.195.195"
        private const val SYNC_PORT = 49821
        private const val DISCOVERY_INTERVAL_MS = 5000L
        private const val SYNC_INTERVAL_MS = 1000L
        private const val DEVICE_TIMEOUT_MS = 15000L
        private const val CLOCK_SAMPLE_COUNT = 5
    }

    /**
     * 远程同步设备信息
     */
    data class SyncDevice(
        val id: String,
        val name: String,
        val ip: String,
        val port: Int = SYNC_PORT,
        var isLeader: Boolean = false,
        var latencyMs: Long = 0L,
        var clockOffsetMs: Long = 0L,
        var lastSeenMs: Long = System.currentTimeMillis(),
        var isConnected: Boolean = true
    )

    /**
     * 播放同步状态
     */
    data class SyncState(
        val timestampMs: Long = 0L,
        val positionMs: Long = 0L,
        val isPlaying: Boolean = false,
        val sequenceNum: Int = 0
    )

    /**
     * 设备事件监听
     */
    interface MultiRoomListener {
        fun onDeviceDiscovered(device: SyncDevice)
        fun onDeviceLost(device: SyncDevice)
        fun onSyncStateChanged(leaderState: SyncState)
        fun onClockSyncCompleted(offsets: Map<String, Long>) // deviceId -> offsetMs
    }

    private val isRunning = AtomicBoolean(false)
    private val devices = CopyOnWriteArrayList<SyncDevice>()
    private val latencies = ConcurrentHashMap<String, MutableList<Long>>()

    private var localDeviceId: String = UUID.randomUUID().toString().take(8)
    private var localDeviceName: String = "DLNA Receiver"
    private var multicastSocket: MulticastSocket? = null
    private var multicastLock: Any? = null // 简化: 实际应使用 WifiManager.MulticastLock

    private var deviceId: String? = null
    private var deviceName: String? = null
    private var groupAddress: InetAddress? = null

    var isLeader: Boolean = false
        private set
    var currentSyncState = SyncState()
        private set

    private val listeners = CopyOnWriteArrayList<MultiRoomListener>()

    // 播放回调
    var onGetPlaybackPosition: (() -> Long)? = null
    var onSyncPosition: ((Long) -> Unit)? = null
    var onPlayCommand: (() -> Unit)? = null
    var onPauseCommand: (() -> Unit)? = null

    fun addListener(listener: MultiRoomListener) { listeners.add(listener) }
    fun removeListener(listener: MultiRoomListener) { listeners.remove(listener) }

    /**
     * 启动多房间同步
     * @param enableAsLeader 是否作为主设备
     * @param deviceId 本机设备标识
     * @param deviceName 本机设备名称
     */
    fun start(enableAsLeader: Boolean, deviceId: String? = null, deviceName: String? = null) {
        if (isRunning.getAndSet(true)) return

        this.deviceId = deviceId ?: localDeviceId
        this.deviceName = deviceName ?: localDeviceName
        isLeader = enableAsLeader

        Log.i(TAG, "MultiRoom ${if (isLeader) "Leader" else "Follower"} starting as ${this.deviceName}...")

        try {
            groupAddress = InetAddress.getByName(MULTICAST_ADDR)

            // 启动 UDP 组播通信
            multicastSocket = MulticastSocket(SYNC_PORT).apply {
                reuseAddress = true
                timeToLive = 4
                soTimeout = 2000
                joinGroup(InetAddress.getByName(MULTICAST_ADDR))
            }

            // 启动发现线程（定期广播自身存在）
            Thread({ discoveryLoop() }, "mr-discovery").apply { isDaemon = true; start() }

            // 启动同步线程
            if (isLeader) {
                Thread({ leaderSyncLoop() }, "mr-leader-sync").apply { isDaemon = true; start() }
            } else {
                Thread({ followerListenLoop() }, "mr-follower-listen").apply { isDaemon = true; start() }
            }

            // 启动清理线程
            Thread({ cleanupLoop() }, "mr-cleanup").apply { isDaemon = true; start() }

            Log.i(TAG, "MultiRoom started, ID=${this.deviceId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MultiRoom", e)
            isRunning.set(false)
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        // 发送下线广播
        sendDiscoveryMessage("BYE")

        try { multicastSocket?.leaveGroup(groupAddress) } catch (_: Exception) {}
        try { multicastSocket?.close() } catch (_: Exception) {}
        devices.clear()
        latencies.clear()
        Log.i(TAG, "MultiRoom stopped")
    }

    /**
     * 通知同步管理器播放状态变化
     */
    fun notifyPlaybackChange(isPlaying: Boolean) {
        if (isLeader) {
            currentSyncState = currentSyncState.copy(
                isPlaying = isPlaying,
                timestampMs = System.currentTimeMillis(),
                sequenceNum = currentSyncState.sequenceNum + 1
            )
        }
    }

    /**
     * 通知同步管理器位置变化
     */
    fun notifyPositionChange(positionMs: Long) {
        if (isLeader) {
            currentSyncState = currentSyncState.copy(
                positionMs = positionMs,
                timestampMs = System.currentTimeMillis()
            )
        }
    }

    fun addDevice(device: SyncDevice) {
        val existing = devices.find { it.id == device.id }
        if (existing == null) {
            devices.add(device)
            listeners.forEach { it.onDeviceDiscovered(device) }
            Log.i(TAG, "Device discovered: ${device.name} (${device.ip})")
        } else {
            val idx = devices.indexOf(existing)
            devices[idx] = device.copy(lastSeenMs = System.currentTimeMillis(), isConnected = true)
        }
    }

    fun getDevices(): List<SyncDevice> = devices.toList()

    // ===== 发现循环 =====
    private fun discoveryLoop() {
        val gson = Gson()
        while (isRunning.get()) {
            try {
                // 广播发现消息
                sendDiscoveryMessage("HELLO")

                // 监听其他设备
                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    multicastSocket?.receive(packet)
                    val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    handleDiscoveryMessage(msg, packet.address)
                } catch (e: SocketTimeoutException) { /* continue */ }

                Thread.sleep(DISCOVERY_INTERVAL_MS)
            } catch (e: Exception) {
                if (isRunning.get()) Log.w(TAG, "Discovery error", e)
            }
        }
    }

    private fun sendDiscoveryMessage(type: String) {
        try {
            val msg = Gson().toJson(mapOf(
                "type" to "MULTIROOM_DISCOVERY",
                "subtype" to type,
                "deviceId" to deviceId,
                "deviceName" to deviceName,
                "isLeader" to isLeader,
                "timestamp" to System.currentTimeMillis()
            ))
            val data = msg.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(data, data.size, groupAddress, SYNC_PORT)
            multicastSocket?.send(packet)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send discovery", e)
        }
    }

    private fun handleDiscoveryMessage(msg: String, remoteAddr: InetAddress) {
        try {
            val json = JsonParser.parseString(msg).asJsonObject
            if (json.get("type")?.asString != "MULTIROOM_DISCOVERY") return

            val subtype = json.get("subtype")?.asString ?: return
            val remoteId = json.get("deviceId")?.asString ?: return

            // 忽略自身
            if (remoteId == deviceId) return

            when (subtype) {
                "HELLO" -> {
                    val name = json.get("deviceName")?.asString ?: "Unknown"
                    val isRemoteLeader = json.get("isLeader")?.asBoolean ?: false
                    addDevice(SyncDevice(
                        id = remoteId,
                        name = name,
                        ip = remoteAddr.hostAddress ?: remoteAddr.toString(),
                        isLeader = isRemoteLeader,
                        lastSeenMs = System.currentTimeMillis()
                    ))
                }
                "BYE" -> {
                    devices.find { it.id == remoteId }?.let {
                        devices.remove(it)
                        listeners.forEach { l -> l.onDeviceLost(it) }
                        Log.i(TAG, "Device left: ${it.name}")
                    }
                }
                "SYNC" -> {
                    // 来自 Leader 的同步状态
                    val seq = json.get("sequenceNum")?.asInt ?: 0
                    val pos = json.get("positionMs")?.asLong ?: 0L
                    val playing = json.get("isPlaying")?.asBoolean ?: false
                    val remoteTimestamp = json.get("timestamp")?.asLong ?: 0L

                    // 计算时钟偏移
                    val receivedAt = System.currentTimeMillis()
                    val estimatedOffset = receivedAt - remoteTimestamp
                    trackLatency(remoteId, estimatedOffset)

                    // 更新同步状态（根据偏移补偿）
                    val avgLatency = getAverageLatency(remoteId)
                    val adjustedPosition = pos + (receivedAt - remoteTimestamp) - avgLatency

                    currentSyncState = SyncState(
                        timestampMs = receivedAt,
                        positionMs = adjustedPosition.coerceAtLeast(0),
                        isPlaying = playing,
                        sequenceNum = seq
                    )

                    // 同步播放引擎
                    if (playing) {
                        onSyncPosition?.invoke(adjustedPosition.coerceAtLeast(0))
                        onPlayCommand?.invoke()
                    } else {
                        onPauseCommand?.invoke()
                    }

                    listeners.forEach { it.onSyncStateChanged(currentSyncState) }
                }
                "CLOCK_REQ" -> {
                    // 时钟同步请求 — 立即回复
                    sendClockResponse(remoteAddr)
                }
                "CLOCK_RESP" -> {
                    val reqTime = json.get("reqTimestamp")?.asLong ?: 0L
                    val respTime = json.get("respTimestamp")?.asLong ?: 0L
                    val recvTime = System.currentTimeMillis()
                    val rtt = recvTime - reqTime
                    val offset = ((respTime - reqTime) + (recvTime - respTime)) / 2
                    trackLatency(remoteId, rtt / 2)
                    listeners.forEach { it.onClockSyncCompleted(mapOf(remoteId to offset)) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse discovery message", e)
        }
    }

    private fun sendClockResponse(remoteAddr: InetAddress) {
        try {
            val msg = Gson().toJson(mapOf(
                "type" to "MULTIROOM_DISCOVERY",
                "subtype" to "CLOCK_RESP",
                "deviceId" to deviceId,
                "reqTimestamp" to System.currentTimeMillis()
            ))
            val data = msg.toByteArray(Charsets.UTF_8)
            multicastSocket?.send(DatagramPacket(data, data.size, remoteAddr, SYNC_PORT))
        } catch (e: Exception) { Log.w(TAG, "Clock response error", e) }
    }

    // ===== Leader 同步循环 =====
    private fun leaderSyncLoop() {
        while (isRunning.get() && isLeader) {
            try {
                val position = onGetPlaybackPosition?.invoke() ?: currentSyncState.positionMs
                currentSyncState = currentSyncState.copy(
                    positionMs = position,
                    timestampMs = System.currentTimeMillis(),
                    sequenceNum = currentSyncState.sequenceNum + 1
                )

                // 向所有已知设备广播同步状态
                val msg = Gson().toJson(mapOf(
                    "type" to "MULTIROOM_DISCOVERY",
                    "subtype" to "SYNC",
                    "deviceId" to deviceId,
                    "isLeader" to true,
                    "positionMs" to currentSyncState.positionMs,
                    "isPlaying" to currentSyncState.isPlaying,
                    "timestamp" to System.currentTimeMillis(),
                    "sequenceNum" to currentSyncState.sequenceNum
                ))

                val data = msg.toByteArray(Charsets.UTF_8)
                multicastSocket?.send(DatagramPacket(data, data.size, groupAddress, SYNC_PORT))

                Thread.sleep(SYNC_INTERVAL_MS)
            } catch (e: Exception) {
                if (isRunning.get()) Log.w(TAG, "Leader sync error", e)
            }
        }
    }

    // ===== Follower 监听循环 =====
    private fun followerListenLoop() {
        val buffer = ByteArray(4096)
        val packet = DatagramPacket(buffer, buffer.size)

        while (isRunning.get() && !isLeader) {
            try {
                multicastSocket?.receive(packet)
                val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                handleDiscoveryMessage(msg, packet.address)
            } catch (e: SocketTimeoutException) { /* continue */ }
            catch (e: Exception) {
                if (isRunning.get()) Log.w(TAG, "Follower listen error", e)
            }
        }
    }

    // ===== 设备清理循环 =====
    private fun cleanupLoop() {
        while (isRunning.get()) {
            try {
                val now = System.currentTimeMillis()
                val toRemove = devices.filter { now - it.lastSeenMs > DEVICE_TIMEOUT_MS }
                toRemove.forEach { device ->
                    devices.remove(device)
                    latencies.remove(device.id)
                    listeners.forEach { it.onDeviceLost(device) }
                    Log.d(TAG, "Device timed out: ${device.name}")
                }
                Thread.sleep(DEVICE_TIMEOUT_MS / 2)
            } catch (e: Exception) {
                if (isRunning.get()) Log.w(TAG, "Cleanup error", e)
            }
        }
    }

    // ===== 延迟跟踪 =====
    private fun trackLatency(deviceId: String, sample: Long) {
        val samples = latencies.getOrPut(deviceId) { mutableListOf() }
        synchronized(samples) {
            samples.add(sample)
            if (samples.size > CLOCK_SAMPLE_COUNT) samples.removeAt(0)
        }
        // 更新设备延迟
        devices.find { it.id == deviceId }?.let {
            val idx = devices.indexOf(it)
            devices[idx] = it.copy(latencyMs = getAverageLatency(deviceId))
        }
    }

    private fun getAverageLatency(deviceId: String): Long {
        val samples = latencies[deviceId] ?: return 0L
        synchronized(samples) {
            if (samples.isEmpty()) return 0L
            return samples.sum() / samples.size
        }
    }

    fun isRunning(): Boolean = isRunning.get()
}
