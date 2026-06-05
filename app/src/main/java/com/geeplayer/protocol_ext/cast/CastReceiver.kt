package com.geeplayer.protocol_ext.cast

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google Cast 接收器 — 轻量级 Cast v2 协议实现
 *
 * Cast v2 协议基于 TLS/TCP，提供:
 * - 设备发现 (mDNS, 需系统支持)
 * - 应用启动 (DIAL 协议)
 * - 媒体控制 (播放/暂停/停止/音量)
 * - 媒体状态上报
 *
 * 注意: 完整 Google Cast 兼容需要 Play Services 及 Cast SDK，
 * 此处实现 Cast 协议的基本消息框架，支持基础媒体控制。
 */
class CastReceiver {
    private companion object {
        private const val TAG = "CastReceiver"
        private const val CAST_PORT = 8009
        private const val NAMESPACE_MEDIA = "urn:x-cast:com.google.cast.media"
        private const val NAMESPACE_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
        private const val NAMESPACE_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
        private const val NAMESPACE_RECEIVER = "urn:x-cast:com.google.cast.receiver"

        // 支持的媒体格式
        val SUPPORTED_MEDIA_TYPES = listOf(
            "audio/mpeg", "audio/aac", "audio/wav",
            "audio/flac", "audio/ogg", "audio/x-m4a"
        )
    }

    data class CastSession(
        val sessionId: String = UUID.randomUUID().toString(),
        val transportId: String = UUID.randomUUID().toString(),
        var appId: String = "",
        var displayName: String = "DLNA Receiver",
        var isPlaying: Boolean = false,
        var isPaused: Boolean = false,
        var volume: Float = 1.0f,
        var isMuted: Boolean = false,
        var currentMediaUrl: String = "",
        var currentTitle: String = "",
        var position: Long = 0L,
        var duration: Long = 0L
    )

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var currentSession = CastSession()
    private val gson = Gson()

    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null
    var onVolumeChanged: ((Float, Boolean) -> Unit)? = null
    var onMediaLoaded: ((String, String) -> Unit)? = null
    var onSessionConnected: (() -> Unit)? = null
    var onSessionDisconnected: (() -> Unit)? = null

    fun start() {
        if (isRunning.getAndSet(true)) return
        Log.i(TAG, "Starting Cast receiver...")

        try {
            serverSocket = ServerSocket(CAST_PORT)
            Thread({ listenLoop() }, "cast-server").apply { isDaemon = true; start() }
            Log.i(TAG, "Cast receiver started on port $CAST_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Cast receiver", e)
            isRunning.set(false)
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.i(TAG, "Stopping Cast receiver...")
        try { serverSocket?.close() } catch (_: Exception) {}
        Log.i(TAG, "Cast receiver stopped")
    }

    /**
     * 更新当前播放状态（由播放引擎回调）
     */
    fun updatePlaybackState(isPlaying: Boolean, position: Long, duration: Long) {
        currentSession = currentSession.copy(
            isPlaying = isPlaying,
            position = position,
            duration = duration
        )
    }

    fun updateVolume(volume: Float, muted: Boolean) {
        currentSession = currentSession.copy(volume = volume, isMuted = muted)
    }

    private fun listenLoop() {
        val server = serverSocket ?: return
        while (isRunning.get() && !server.isClosed) {
            try {
                val client = server.accept()
                Thread({ handleClient(client) }, "cast-client").apply { isDaemon = true; start() }
            } catch (e: Exception) {
                if (isRunning.get()) Log.w(TAG, "Accept error", e)
            }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 30000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val writer = client.getOutputStream()

            Log.d(TAG, "Cast client connected: ${client.inetAddress}")

            // Cast v2 消息循环 (JSON line-delimited protocol)
            while (isRunning.get() && !client.isClosed) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                try {
                    handleCastMessage(line, writer, client.inetAddress)
                } catch (e: Exception) {
                    Log.w(TAG, "Message handling error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client disconnected: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
            onSessionDisconnected?.invoke()
        }
    }

    private fun handleCastMessage(jsonMessage: String, writer: OutputStream, remoteAddr: InetAddress) {
        val json = JsonParser.parseString(jsonMessage).asJsonObject
        val type = json.get("type")?.asString ?: return
        val namespace = json.get("namespace")?.asString ?: ""
        val senderId = json.get("senderId")?.asString ?: ""

        Log.v(TAG, "Cast msg: type=$type ns=$namespace")

        when (namespace) {
            NAMESPACE_CONNECTION -> handleConnectionMessage(type, writer, senderId)
            NAMESPACE_HEARTBEAT -> handleHeartbeat(type, writer)
            NAMESPACE_RECEIVER -> handleReceiverMessage(type, json, writer, senderId)
            NAMESPACE_MEDIA -> handleMediaMessage(type, json, writer, senderId)
        }
    }

    private fun handleConnectionMessage(type: String, writer: OutputStream, senderId: String) {
        when (type) {
            "CONNECT" -> {
                sendCastMessage(writer, NAMESPACE_CONNECTION, mapOf(
                    "type" to "CONNECTED",
                    "senderId" to senderId
                ))
                onSessionConnected?.invoke()
                Log.i(TAG, "Cast client connected: $senderId")
            }
            "CLOSE" -> {
                Log.i(TAG, "Cast connection closed by client")
            }
        }
    }

    private fun handleHeartbeat(type: String, writer: OutputStream) {
        if (type == "PING") {
            sendCastMessage(writer, NAMESPACE_HEARTBEAT, mapOf("type" to "PONG"))
        }
    }

    private fun handleReceiverMessage(type: String, messageJson: com.google.gson.JsonObject, writer: OutputStream, senderId: String) {
        when (type) {
            "GET_STATUS" -> {
                sendCastMessage(writer, NAMESPACE_RECEIVER, mapOf(
                    "type" to "RECEIVER_STATUS",
                    "requestId" to 1,
                    "status" to mapOf(
                        "applications" to listOf(
                            mapOf(
                                "appId" to currentSession.appId,
                                "displayName" to currentSession.displayName,
                                "namespaces" to listOf(
                                    mapOf("name" to NAMESPACE_MEDIA),
                                    mapOf("name" to NAMESPACE_CONNECTION)
                                ),
                                "sessionId" to currentSession.sessionId,
                                "transportId" to currentSession.transportId,
                                "statusText" to "ready"
                            )
                        ),
                        "volume" to mapOf(
                            "level" to currentSession.volume.toDouble(),
                            "muted" to currentSession.isMuted
                        )
                    )
                ))
            }
            "LAUNCH" -> {
                // 接受应用启动
                currentSession = currentSession.copy(appId = "DLNA_RECEIVER")
                sendCastMessage(writer, NAMESPACE_RECEIVER, mapOf(
                    "type" to "LAUNCH_RESPONSE",
                    "requestId" to 1,
                    "status" to mapOf(
                        "applications" to listOf(
                            mapOf(
                                "appId" to "DLNA_RECEIVER",
                                "displayName" to currentSession.displayName,
                                "sessionId" to currentSession.sessionId,
                                "transportId" to currentSession.transportId
                            )
                        )
                    )
                ))
            }
            "SET_VOLUME" -> {
                val volumeObj = messageJson.get("volume")?.asJsonObject
                volumeObj?.let {
                    val level = it.get("level")?.asFloat ?: currentSession.volume
                    val muted = it.get("muted")?.asBoolean ?: currentSession.isMuted
                    currentSession = currentSession.copy(volume = level, isMuted = muted)
                    onVolumeChanged?.invoke(level, muted)
                }
            }
        }
    }

    private fun handleMediaMessage(type: String, json: com.google.gson.JsonObject, writer: OutputStream, senderId: String) {
        when (type) {
            "LOAD" -> {
                val media = json.getAsJsonObject("media")
                val contentId = media?.get("contentId")?.asString ?: ""
                val contentType = media?.get("contentType")?.asString ?: ""
                val metadata = media?.getAsJsonObject("metadata")
                val title = metadata?.get("title")?.asString ?: ""

                currentSession = currentSession.copy(
                    currentMediaUrl = contentId,
                    currentTitle = title
                )

                // 检查内容格式是否支持
                val supported = SUPPORTED_MEDIA_TYPES.any { contentType.contains(it, ignoreCase = true) }
                if (!supported) {
                    Log.w(TAG, "Unsupported media type: $contentType")
                }

                onMediaLoaded?.invoke(contentId, contentType)

                // 返回加载成功和媒体状态
                sendCastMessage(writer, NAMESPACE_MEDIA, mapOf(
                    "type" to "MEDIA_STATUS",
                    "requestId" to (json.get("requestId")?.asInt ?: 1),
                    "status" to listOf(
                        mapOf(
                            "mediaSessionId" to 1,
                            "playbackRate" to 1,
                            "playerState" to "BUFFERING",
                            "currentTime" to 0.0,
                            "supportedMediaCommands" to 15,
                            "volume" to mapOf("level" to currentSession.volume.toDouble(), "muted" to currentSession.isMuted)
                        )
                    )
                ))

                // 自动开始播放
                currentSession = currentSession.copy(isPlaying = true, isPaused = false)
                onPlay?.invoke()

                Log.i(TAG, "Cast LOAD: $title ($contentId)")
            }
            "PLAY" -> {
                currentSession = currentSession.copy(isPlaying = true, isPaused = false)
                sendMediaStatus(writer, "PLAYING")
                onPlay?.invoke()
                Log.i(TAG, "Cast PLAY")
            }
            "PAUSE" -> {
                currentSession = currentSession.copy(isPlaying = false, isPaused = true)
                sendMediaStatus(writer, "PAUSED")
                onPause?.invoke()
                Log.i(TAG, "Cast PAUSE")
            }
            "STOP" -> {
                currentSession = currentSession.copy(isPlaying = false, isPaused = false)
                sendMediaStatus(writer, "IDLE")
                onStop?.invoke()
                Log.i(TAG, "Cast STOP")
            }
            "SEEK" -> {
                val currentTime = json.get("currentTime")?.asLong ?: 0L
                currentSession = currentSession.copy(position = currentTime)
                sendMediaStatus(writer, if (currentSession.isPlaying) "PLAYING" else "PAUSED")
                onSeek?.invoke(currentTime)
                Log.i(TAG, "Cast SEEK: $currentTime")
            }
            "GET_STATUS" -> {
                val state = when {
                    currentSession.isPlaying -> "PLAYING"
                    currentSession.isPaused -> "PAUSED"
                    else -> "IDLE"
                }
                sendMediaStatus(writer, state)
            }
        }
    }

    private fun sendMediaStatus(writer: OutputStream, playerState: String) {
        sendCastMessage(writer, NAMESPACE_MEDIA, mapOf(
            "type" to "MEDIA_STATUS",
            "status" to listOf(
                mapOf(
                    "mediaSessionId" to 1,
                    "playbackRate" to 1,
                    "playerState" to playerState,
                    "currentTime" to (currentSession.position / 1000.0),
                    "supportedMediaCommands" to 15,
                    "volume" to mapOf("level" to currentSession.volume.toDouble(), "muted" to currentSession.isMuted),
                    "media" to mapOf(
                        "contentId" to currentSession.currentMediaUrl,
                        "streamType" to "BUFFERED",
                        "contentType" to "audio/mpeg"
                    )
                )
            )
        ))
    }

    private fun sendCastMessage(writer: OutputStream, namespace: String, data: Map<String, Any?>) {
        try {
            val message = mapOf(
                "type" to "CAST_MESSAGE",
                "namespace" to namespace,
                "data" to gson.toJson(data)
            )
            val jsonStr = gson.toJson(message) + "\n"
            writer.write(jsonStr.toByteArray(Charsets.UTF_8))
            writer.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send Cast message", e)
        }
    }

    fun isRunning(): Boolean = isRunning.get()
    fun getSession(): CastSession = currentSession
}
