package com.geeplayer.upnp.core

import android.util.Log
import com.geeplayer.util.NetworkUtils
import java.util.UUID

/**
 * UPnP 核心引擎，协调 SSDP、HTTP Server、Service 生命周期
 */
class UpnpStack(
    private val deviceUuid: String = UUID.randomUUID().toString(),
    private val httpPort: Int = UpnpConstants.HTTP_PORT
) {
    /** 设备名称，支持运行时修改 */
    @Volatile
    var deviceName: String = "Geeplayer"
    private companion object {
        private const val TAG = "UpnpStack"
    }

    val uuid: String get() = deviceUuid
    val name: String get() = deviceName
    val port: Int get() = httpPort

    // UPnP 设备描述中的专用 UUID 格式
    val udn: String get() = "uuid:$deviceUuid"

    // 设备描述 URL — 动态获取本机 IP
    val deviceDescUrl: String get() = "http://${getLocalIpAddress()}:$httpPort/device.xml"
    val iconUrl: String get() = "http://${getLocalIpAddress()}:$httpPort/icon.png"

    // 当前本机 IP 地址
    @Volatile
    private var currentIp: String = "0.0.0.0"

    /**
     * 更新设备名称（运行时生效）
     */
    fun updateDeviceName(newName: String) {
        deviceName = newName
        // 重新发送 SSDP 公告广播新名称
        onNameChanged?.invoke(newName)
        Log.i(TAG, "Device name updated to: $newName")
    }

    var onNameChanged: ((String) -> Unit)? = null

    private var isRunning = false

    // 组件引用 (由服务层注入)
    var onStart: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null

    /**
     * 启动 UPnP 核心引擎
     * 初始化网络信息并通知组件
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        // 初始化网络信息
        refreshIpAddress()

        Log.i(TAG, "UPnP Stack starting: uuid=$deviceUuid, name=$deviceName, ip=$currentIp, port=$httpPort")
        Log.i(TAG, "Device URL: $deviceDescUrl")

        // 通知子组件启动
        onStart?.invoke()
    }

    /**
     * 停止 UPnP 核心引擎
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false

        // 通知子组件停止
        onStop?.invoke()

        Log.i(TAG, "UPnP Stack stopped")
    }

    /**
     * 刷新本机 IP 地址（在网络切换时调用）
     * @return 当前 IP 地址
     */
    fun refreshIpAddress(): String {
        currentIp = NetworkUtils.getLocalIpAddress()
        Log.d(TAG, "IP address refreshed: $currentIp")
        return currentIp
    }

    /**
     * 获取本机局域网 IP 地址
     * 委托给 NetworkUtils 获取真实的 WiFi 网卡 IP
     */
    private fun getLocalIpAddress(): String {
        // 如果尚未初始化，先获取 IP
        if (currentIp == "0.0.0.0") {
            refreshIpAddress()
        }
        return currentIp
    }

    /**
     * 获取设备描述中的基础 URL
     */
    fun getBaseUrl(): String = "http://$currentIp:$httpPort"

    override fun toString(): String {
        return "UpnpStack(uuid=$deviceUuid, name=$deviceName, ip=$currentIp, port=$httpPort, running=$isRunning)"
    }
}
