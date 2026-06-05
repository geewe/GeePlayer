package com.geeplayer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.geeplayer.R
import com.geeplayer.player.DlnaPlayer
import com.geeplayer.upnp.core.UpnpConstants
import com.geeplayer.upnp.core.UpnpStack
import com.geeplayer.upnp.http.UpnpHttpServer
import com.geeplayer.upnp.services.avt.AVTStateManager
import com.geeplayer.upnp.services.avt.AVTransportService
import com.geeplayer.upnp.services.cmgr.ConnectionManagerService
import com.geeplayer.upnp.services.rc.RenderingControlService
import com.geeplayer.data.preferences.AppPreferences
import com.geeplayer.upnp.ssdp.SsdpServer
import com.geeplayer.protocol_ext.airplay.AirPlayReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * DLNA 接收器前台服务 — 负责 UPnP 协议栈生命周期和后端播放
 *
 * 通过 companion object 暴露 currentPlayer 供 ViewModel 绑定
 */
class ReceiverForegroundService : Service() {
    companion object {
        private const val TAG = "ForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "dlna_playback"
        const val ACTION_START = "com.geeplayer.START"
        const val ACTION_STOP = "com.geeplayer.STOP"

        /** 当前播放器实例，供外部绑定 */
        @JvmStatic var currentPlayer: DlnaPlayer? = null
            private set

        /** 当前服务实例，供外部通信 */
        @JvmStatic var currentService: ReceiverForegroundService? = null
            private set
    }

    @JvmField internal var upnpStack: UpnpStack? = null
    private lateinit var ssdpServer: SsdpServer
    private lateinit var httpServer: UpnpHttpServer
    private lateinit var stateManager: AVTStateManager
    @JvmField internal var avTransportService: AVTransportService? = null
    private lateinit var renderingControlService: RenderingControlService
    private lateinit var connectionManagerService: ConnectionManagerService
    private lateinit var player: DlnaPlayer

    private var wifiManager: WifiManager? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var airPlayReceiver: AirPlayReceiver? = null
    private var networkStateObserver: NetworkStateObserver? = null
    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startReceiver()
            ACTION_STOP -> stopReceiver()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopReceiver()
        super.onDestroy()
    }

    private fun startReceiver() {
        if (isInitialized) return
        isInitialized = true
        Log.i(TAG, "Starting DLNA Receiver...")

        // 初始化播放器并暴露实例
        player = DlnaPlayer(applicationContext)
        player.initialize()
        currentPlayer = player

        stateManager = AVTStateManager()
        val stack = UpnpStack(httpPort = UpnpConstants.HTTP_PORT).apply { deviceName = getDeviceName() }
        upnpStack = stack

        // 注入生命周期回调
        currentService = this@ReceiverForegroundService
        stack.onStart = {
            Log.d(TAG, "UpnpStack started")
        }
        stack.onStop = {
            Log.d(TAG, "UpnpStack stopped")
        }
        stack.start()

        // 设备名称变更时重新发送 SSDP 公告
        stack.onNameChanged = { newName ->
            Log.d(TAG, "Name changed to $newName, re-announcing via SSDP")
            ssdpServer.stop()
            ssdpServer.start()
        }

        val avtService = AVTransportService(stateManager, player)
        avTransportService = avtService
        renderingControlService = RenderingControlService(stateManager, player)
        connectionManagerService = ConnectionManagerService()

        httpServer = UpnpHttpServer(stack, UpnpConstants.HTTP_PORT).apply {
            this.avTransportService = avtService
            this.renderingControlService = this@ReceiverForegroundService.renderingControlService
            this.connectionManagerService = this@ReceiverForegroundService.connectionManagerService
        }

        ssdpServer = SsdpServer(applicationContext, stack)
        acquireLocks()

        try {
            httpServer.start()
            Log.i(TAG, "HTTP Server started on port ${UpnpConstants.HTTP_PORT}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HTTP server", e)
        }

        ssdpServer.start()

        // 启动 AirPlay（mDNS + RTSP）
        try {
            airPlayReceiver = AirPlayReceiver(applicationContext).apply {
                addListener(object : AirPlayReceiver.Listener {
                    override fun onTrackChanged(title: String, artist: String) {
                        Log.d(TAG, "AirPlay track: $title - $artist")
                    }
                })
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "AirPlay start failed", e)
        }

        startForegroundService()
        startNetworkMonitoring()
        Log.i(TAG, "DLNA Receiver started successfully")
    }

    private fun stopReceiver() {
        if (!isInitialized) return
        isInitialized = false
        Log.i(TAG, "Stopping DLNA Receiver...")
        ssdpServer.stop()
        httpServer.stop()
        airPlayReceiver?.stop()
        airPlayReceiver = null
        player.release()
        currentPlayer = null
        currentService = null
        releaseLocks()
        stopNetworkMonitoring()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "DLNA Receiver stopped")
    }

    private fun acquireLocks() {
        try {
            wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("dlna-multicast-lock")
            multicastLock?.setReferenceCounted(false)
            multicastLock?.acquire()
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "dlna-wifi-lock")
            wifiLock?.acquire()
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dlna-wake-lock")
            wakeLock?.acquire()
            Log.d(TAG, "All locks acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire locks", e)
        }
    }

    private fun releaseLocks() {
        try {
            if (multicastLock?.isHeld == true) multicastLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) { Log.w(TAG, "Error releasing locks", e) }
    }

    private fun startForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "DLNA 播放控制", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_playing))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun getDeviceName(): String {
        // 从 AppPreferences DataStore 读取，与服务初始化同步
        return try {
            kotlinx.coroutines.runBlocking {
                AppPreferences(applicationContext).deviceName.first()
            }
        } catch (e: Exception) {
            "DLNA Receiver"
        }
    }

    private fun startNetworkMonitoring() {
        networkStateObserver = NetworkStateObserver(applicationContext) { connected ->
            if (connected && isInitialized) {
                Log.i(TAG, "Network reconnected, restarting SSDP")
                ssdpServer.stop()
                ssdpServer.start()
            }
        }
        networkStateObserver?.start()
    }

    private fun stopNetworkMonitoring() {
        networkStateObserver?.stop()
    }
}
