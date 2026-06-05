package com.geeplayer.protocol_ext.airplay

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.wifi.WifiManager
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * AirPlay 接收器 — 完整实现：mDNS 广播 + RTSP 控制 + ALAC 解码 + 音频播放
 */
class AirPlayReceiver(
    private val context: Context? = null
) {
    private var multicastLock: WifiManager.MulticastLock? = null
    private var jmDNS: JmDNS? = null
    // private var serviceInfo: ServiceInfo? = null
    private companion object {
        private const val TAG = "AirPlayReceiver"
        private const val RTSP_PORT = 5000
        private const val AUDIO_BASE_PORT = 6000
        private const val TIMEOUT_MS = 30000
        private const val SERVICE_TYPE_RAOP = "_raop._tcp"
        private const val SERVICE_TYPE_AIRPLAY = "_airplay._tcp"
        private const val SERVICE_NAME = "Geeplayer"

        /** ALAC 魔数 —— Apple Lossless 音频每帧大小 */
        private const val ALAC_FRAME_SIZE = 1024
        private const val ALAC_HEADER_SIZE = 16  // sync header
    }

    data class AirPlaySession(
        var remoteAddress: InetAddress? = null,
        var remotePort: Int = 0,
        val sessionId: String = UUID.randomUUID().toString(),
        var isPlaying: Boolean = false,
        var isPaused: Boolean = false,
        var volume: Float = 1.0f,
        var audioDataPort: Int = 0,
        var audioControlPort: Int = 0,
        var trackName: String = "",
        var trackArtist: String = ""
    )

    private val isRunning = AtomicBoolean(false)
    private var rtspServerSocket: ServerSocket? = null
    private var currentSession: AirPlaySession? = null
    private var audioDecoder: AirPlayAudioDecoder? = null
    private var rtspConnectionThread: Thread? = null
    private var audioServerSocket: ServerSocket? = null
    private var audioReceiveThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    /** 监听者 */
    interface Listener {
        fun onTrackChanged(title: String, artist: String) {}
        fun onPlaybackStateChanged(isPlaying: Boolean, isPaused: Boolean) {}
        fun onSessionStarted(session: AirPlaySession) {}
        fun onSessionEnded() {}
        fun onVolumeChanged(volume: Float) {}
    }
    private val listeners = CopyOnWriteArrayList<Listener>()
    fun addListener(l: Listener) { listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    // ======================== Public API ========================

    fun start() {
        if (isRunning.getAndSet(true)) return
        Log.i(TAG, "Starting AirPlay...")

        try {
            registerBonjour()
            startRtspServer()
            Log.i(TAG, "AirPlay started (mDNS + RTSP)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AirPlay", e)
            isRunning.set(false)
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.i(TAG, "Stopping AirPlay...")

        stopAudioPlayback()
        try { audioReceiveThread?.interrupt() } catch (_: Exception) {}
        try { rtspConnectionThread?.interrupt() } catch (_: Exception) {}
        try { audioServerSocket?.close() } catch (_: Exception) {}
        try { rtspServerSocket?.close() } catch (_: Exception) {}
        unregisterBonjour()
        audioDecoder?.release()
        audioDecoder = null
        currentSession = null
        Log.i(TAG, "AirPlay stopped")
    }

    fun isRunning(): Boolean = isRunning.get()
    fun getSession(): AirPlaySession? = currentSession

    // ======================== Bonjour/mDNS ========================

    private fun registerBonjour() {
        try {
            // 获取 WiFi 多播锁（Android 需要才能接收 mDNS）
            val wifiManager = context?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("airplay-mdns-lock")
            multicastLock?.setReferenceCounted(false)
            multicastLock?.acquire()

            // 使用实际 WiFi IP 创建 JmDNS
            val wifiIp = InetAddress.getByName(com.geeplayer.util.NetworkUtils.getLocalIpAddress())
            jmDNS = JmDNS.create(wifiIp, "Geeplayer")

            // AirPlay 1 (_raop._tcp) — 旧版 iOS / iTunes 发现
            val raopProps = mapOf(
                "txtvers" to "1",
                "ch" to "2",
                "cn" to "0,1",
                "sr" to "44100",
                "ss" to "16",
                "tp" to "UDP",
                "sm" to "false",
                "sv" to "false",
                "ek" to "1",
                "et" to "0,1",
                "md" to "0,1,2",
                "vn" to "3",
                "da" to "true",
                "vs" to "130.14",
                "am" to "Geeplayer"
            )
            val raopInfo = ServiceInfo.create(
                SERVICE_TYPE_RAOP,
                "$SERVICE_NAME@Geeplayer",
                RTSP_PORT,
                0,
                0,
                raopProps
            )
            jmDNS?.registerService(raopInfo)
            Log.i(TAG, "Bonjour registered: $SERVICE_NAME.$SERVICE_TYPE_RAOP on $wifiIp:$RTSP_PORT")

            // AirPlay 2 (_airplay._tcp) — 新版 iOS 发现需要
            val airplayProps = mapOf(
                "txtvers" to "1",
                "model" to "Geeplayer",
                "srcvers" to "130.14",
                "vv" to "2"
            )
            val airplayInfo = ServiceInfo.create(
                SERVICE_TYPE_AIRPLAY,
                "$SERVICE_NAME",
                RTSP_PORT,
                0,
                0,
                airplayProps
            )
            jmDNS?.registerService(airplayInfo)
            Log.i(TAG, "Bonjour registered: $SERVICE_NAME.$SERVICE_TYPE_AIRPLAY on $wifiIp:$RTSP_PORT")
        } catch (e: Exception) {
            Log.w(TAG, "Bonjour registration failed: ${e.message}", e)
        }
    }

    private fun unregisterBonjour() {
        try {
            jmDNS?.unregisterAllServices()
            jmDNS?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Bonjour unregister error", e)
        }
        try {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (_: Exception) {}
        jmDNS = null
        multicastLock = null
    }


    // ======================== RTSP Server ========================

    private fun startRtspServer() {
        rtspServerSocket = ServerSocket(RTSP_PORT).also { it.soTimeout = TIMEOUT_MS }
        rtspConnectionThread = Thread({ rtspListenLoop() }, "airplay-rtsp").apply { isDaemon = true; start() }
    }

    private fun rtspListenLoop() {
        val server = rtspServerSocket ?: return
        while (isRunning.get() && !server.isClosed) {
            try {
                val client = server.accept()
                Thread({ handleRtspClient(client) }, "airplay-rtsp-client").apply { isDaemon = true; start() }
            } catch (e: java.net.SocketTimeoutException) { /* normal */ }
            catch (e: Exception) { if (isRunning.get()) Log.w(TAG, "RTSP accept", e) }
        }
    }

    private fun handleRtspClient(client: Socket) {
        try {
            client.soTimeout = TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val writer = client.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()

            when (method) {
                "OPTIONS" -> sendRtspResp(writer, "200 OK", mapOf("Public" to "ANNOUNCE, SETUP, RECORD, PAUSE, FLUSH, TEARDOWN, OPTIONS"))
                "ANNOUNCE" -> handleAnnounce(reader, writer)
                "SETUP" -> handleSetup(reader, writer, client.inetAddress)
                "RECORD" -> handleRecord(writer)
                "PAUSE" -> handlePause(writer)
                "FLUSH" -> handleFlush(writer)
                "TEARDOWN" -> handleTeardown(writer)
                else -> sendRtspResp(writer, "501 Not Implemented")
            }
        } catch (e: Exception) {
            Log.w(TAG, "RTSP handler", e)
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    // ======================== RTSP Handlers ========================

    private fun handleAnnounce(reader: BufferedReader, writer: OutputStream) {
        val headers = readRtspHeaders(reader)
        currentSession = AirPlaySession()
        val session = currentSession!!
        // 解析 Apple 推送标题（从 Content-Type 的 application/x-dmap-tagged 数据中）
        val contentLen = headers["CONTENT-LENGTH"]?.toIntOrNull() ?: 0
        if (contentLen > 0 && contentLen < 10000) {
            val body = CharArray(contentLen).also { reader.read(it, 0, contentLen) }
            val dmaps = String(body)
            // Apple DMAP: mlit + minm (item name) / asal (artist)
            val titleMatch = Regex("""minm\s*([\s\S]*?)(?=mlit|asal|$)""").find(dmaps)
            if (titleMatch != null) {
                session.trackName = titleMatch.groupValues[1].trim().filter { it.isLetterOrDigit() || it.isWhitespace() }
            }
        }
        sendRtspResp(writer, "200 OK", mapOf("Session" to session.sessionId))
        listeners.forEach { it.onSessionStarted(session) }
    }

    private fun handleSetup(reader: BufferedReader, writer: OutputStream, remoteAddr: InetAddress) {
        val headers = readRtspHeaders(reader)
        val session = currentSession ?: run { sendRtspResp(writer, "454 Session Not Found"); return }
        session.remoteAddress = remoteAddr

        // startAudioServer on the server_port
        val serverPort = AUDIO_BASE_PORT + (session.sessionId.hashCode() and 0xFF)
        audioServerSocket = ServerSocket(serverPort).also { it.soTimeout = TIMEOUT_MS }
        session.audioDataPort = serverPort

        // Start audio receive thread
        audioReceiveThread?.interrupt()
        audioReceiveThread = Thread({ audioReceiveLoop(serverPort) }, "airplay-audio").apply { isDaemon = true; start() }

        sendRtspResp(writer, "200 OK", mapOf(
            "Session" to session.sessionId,
            "Transport" to "RTP/AVP/UDP;unicast;server_port=$serverPort;ssrc=${session.sessionId.hashCode().toUShort()}"
        ))
        Log.i(TAG, "AirPlay SETUP: audioPort=$serverPort")
    }

    private fun handleRecord(writer: OutputStream) {
        val session = currentSession ?: run { sendRtspResp(writer, "454 Session Not Found"); return }
        session.isPlaying = true; session.isPaused = false
        startAudioPlayback()
        sendRtspResp(writer, "200 OK", mapOf("Session" to session.sessionId))
        listeners.forEach { it.onPlaybackStateChanged(true, false) }
        Log.i(TAG, "AirPlay RECORD")
    }

    private fun handlePause(writer: OutputStream) {
        currentSession?.let { it.isPlaying = false; it.isPaused = true }
        stopAudioPlayback()
        sendRtspResp(writer, "200 OK")
        listeners.forEach { it.onPlaybackStateChanged(false, true) }
    }

    private fun handleFlush(writer: OutputStream) {
        sendRtspResp(writer, "200 OK")
    }

    private fun handleTeardown(writer: OutputStream) {
        stopAudioPlayback()
        sendRtspResp(writer, "200 OK")
        currentSession?.let { session ->
            session.isPlaying = false
            listeners.forEach { it.onSessionEnded() }
        }
        currentSession = null
        audioDecoder?.release()
        audioDecoder = null
    }

    // ======================== Audio Reception & Playback ========================

    /**
     * 接收 AirPlay 音频数据 TCP 流
     * 格式: [16字节 sync header][ALAC 帧数据]
     */
    private fun audioReceiveLoop(port: Int) {
        val server = audioServerSocket ?: return
        while (isRunning.get() && !server.isClosed()) {
            try {
                val client = server.accept()
                Log.i(TAG, "Audio client connected: ${client.inetAddress}")
                client.soTimeout = 0  // blocking read

                // 初始化解码器
                audioDecoder?.release()
                audioDecoder = AirPlayAudioDecoder().apply {
                    init(44100, 2)
                }

                val input = client.getInputStream()
                val buf = ByteArray(8192)
                var alacBuffer = ByteArrayOutputStream()

                while (isRunning.get() && !client.isClosed()) {
                    val r = input.read(buf)
                    if (r < 0) break

                    alacBuffer.write(buf, 0, r)
                    val data: ByteArray = alacBuffer.toByteArray()

                    // 处理完整的 ALAC 帧
                    var offset = 0
                    var decoded = false
                    while (offset + ALAC_HEADER_SIZE + ALAC_FRAME_SIZE <= data.size) {
                        val alacFrame = data.copyOfRange(offset + ALAC_HEADER_SIZE, offset + ALAC_HEADER_SIZE + ALAC_FRAME_SIZE)
                        val pcm = audioDecoder?.decodeFrame(alacFrame)
                        if (pcm != null && pcm.isNotEmpty()) {
                            writePcmToAudioTrack(pcm)
                            decoded = true
                        }
                        offset += ALAC_HEADER_SIZE + ALAC_FRAME_SIZE
                    }

                    // 保留未处理完的残余数据
                    if (offset > 0) {
                        alacBuffer = ByteArrayOutputStream()
                        if (offset < data.size) {
                            alacBuffer.write(data, offset, data.size - offset)
                        }
                    }
                }
            } catch (e: java.net.SocketTimeoutException) { /* normal */ }
            catch (e: Exception) {
                if (isRunning.get()) Log.w(TAG, "Audio receive error", e)
            }
        }
    }

    private fun startAudioPlayback() {
        stopAudioPlayback()
        try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build())
                .setBufferSizeInBytes(bufferSize.coerceAtLeast(4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also { it.play() }
            Log.i(TAG, "AudioTrack started")
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack init failed", e)
        }
    }

    private fun writePcmToAudioTrack(pcm: ByteArray) {
        try {
            audioTrack?.write(pcm, 0, pcm.size)
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack write error", e)
        }
    }

    private fun stopAudioPlayback() {
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (_: Exception) {}
        audioTrack = null
    }

    // ======================== Utility ========================

    private fun readRtspHeaders(reader: BufferedReader): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
                headers[line.substring(0, idx).trim().uppercase()] = line.substring(idx + 1).trim()
            }
        }
        return headers
    }

    private fun sendRtspResp(writer: OutputStream, status: String, headers: Map<String, String> = emptyMap()) {
        val sb = StringBuilder("RTSP/1.0 $status\r\n")
        sb.append("CSeq: 1\r\n")
        headers.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        sb.append("\r\n")
        writer.write(sb.toString().toByteArray(Charsets.UTF_8))
        writer.flush()
    }
}
