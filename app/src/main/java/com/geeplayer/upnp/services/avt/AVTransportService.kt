package com.geeplayer.upnp.services.avt

import android.util.Log
import com.geeplayer.player.DlnaPlayer
import com.geeplayer.upnp.core.UpnpConstants
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLDecoder

/**
 * AVTransport:1 服务 — 处理音频传输控制
 *
 * 核心接口:
 * - SetAVTransportURI    : 设置推送的音频地址
 * - Play                 : 开始播放
 * - Pause                : 暂停
 * - Stop                 : 停止
 * - Seek                 : 跳转进度
 * - Next                 : 下一曲
 * - Previous             : 上一曲
 * - GetPositionInfo      : 获取播放进度
 * - GetTransportInfo     : 获取传输状态
 * - GetTransportSettings : 获取传输设置
 * - GetDeviceCapabilities: 获取设备能力
 */
class AVTransportService(
    private val stateManager: AVTStateManager,
    private val player: DlnaPlayer
) {
    /** 连接的推送设备列表 */
    data class ConnectedDevice(
        val name: String = "",
        val uri: String = "",
        val connectedAt: Long = System.currentTimeMillis()
    )

    private val _connectedDevices = mutableListOf<ConnectedDevice>()
    val connectedDevices: List<ConnectedDevice> get() = _connectedDevices.toList()

    /** 最后一次收到的原始元数据（调试用） */
    @Volatile
    var lastRawMetadata: String = ""
        private set
    private companion object {
        private const val TAG = "AVTransportService"

        // AVTransport:1 所有支持的 Action
        val SUPPORTED_ACTIONS = setOf(
            "SetAVTransportURI", "SetNextAVTransportURI",
            "Play", "Pause", "Stop", "Seek",
            "Next", "Previous",
            "GetPositionInfo", "GetTransportInfo",
            "GetTransportSettings", "GetDeviceCapabilities",
            "GetMediaInfo",
            "GetCurrentTransportActions"
        )
    }

    /**
     * 处理 SOAP Action，返回响应 XML
     */
    fun handleAction(soapAction: String, body: String): String? {
        val actionName = soapAction.substringAfterLast("#").substringBefore("(")
        Log.d(TAG, "Handling action: $actionName")

        return try {
            when (actionName) {
                "SetAVTransportURI" -> handleSetAVTransportURI(body)
                "SetNextAVTransportURI" -> handleSetNextAVTransportURI(body)
                "Play" -> handlePlay(body)
                "Pause" -> handlePause()
                "Stop" -> handleStop()
                "Seek" -> handleSeek(body)
                "Next" -> handleNext()
                "Previous" -> handlePrevious()
                "GetMediaInfo" -> handleGetMediaInfo(body)
                "GetPositionInfo" -> handleGetPositionInfo(body)
                "GetTransportInfo" -> handleGetTransportInfo()
                "GetTransportSettings" -> handleGetTransportSettings()
                "GetDeviceCapabilities" -> handleGetDeviceCapabilities()
                "GetCurrentTransportActions" -> handleGetCurrentTransportActions()
                else -> {
                    Log.w(TAG, "Unsupported action: $actionName")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling action: $actionName", e)
            null
        }
    }

    /**
     * SetAVTransportURI — 推送端设置播放地址（核心接口）
     */
    private fun handleSetAVTransportURI(body: String): String {
        val instanceId = extractTag(body, "InstanceID") ?: "0"
        val uri = extractTag(body, "CurrentURI") ?: ""
        val metadata = extractTag(body, "CurrentURIMetaData") ?: ""

        Log.i(TAG, "SetAVTransportURI: uri=$uri")
        Log.i(TAG, "SetAVTransportURI metadata raw: ${metadata.take(300)}")
        lastRawMetadata = metadata
        stateManager.setCurrentUri(uri, metadata)
        // 追踪已连接设备
        val deviceName = extractDidlValue(metadata, "title")?.let {
            if (it.isNotBlank()) it else uri.substringAfterLast("/").substringBefore("?")
        } ?: uri.substringAfterLast("/").substringBefore("?")
        // 保留最多 20 条记录，相同 URI 不重复添加
        _connectedDevices.removeAll { it.uri == uri }
        _connectedDevices.add(ConnectedDevice(name = deviceName, uri = uri))
        if (_connectedDevices.size > 20) _connectedDevices.removeAt(0)


        // 解析 DIDL-Lite 元数据
        if (metadata.isNotBlank()) {
            val xmlTitle = extractDidlValue(metadata, "title")
            var xmlArtist = extractDidlValue(metadata, "creator")
            // 某些推送端（如网易云）把真实歌手放在 upnp:artist 而非 dc:creator
            if (xmlArtist.isNullOrBlank() || xmlArtist.equals("Anonymous", ignoreCase = true)) {
                val upnpArtist = extractDidlValue(metadata, "artist")
                if (!upnpArtist.isNullOrBlank()) {
                    xmlArtist = upnpArtist
                }
            }
            val xmlAlbum = extractDidlValue(metadata, "album") ?: extractDidlValue(metadata, "album")
            val xmlAlbumArt = extractDidlValue(metadata, "albumArtURI")
            Log.i(TAG, "DIDL-Lite parsed: title=$xmlTitle, artist=$xmlArtist, album=$xmlAlbum, albumArt=$xmlAlbumArt")

            if (xmlTitle != null && xmlTitle.isNotBlank()) {
                Log.i(TAG, "Using DIDL-Lite metadata: title=$xmlTitle, artist=$xmlArtist")
                stateManager.currentTitle = xmlTitle
                stateManager.currentArtist = xmlArtist ?: ""
                player.updateMetadata(xmlTitle, xmlArtist ?: "")
                // 推送封面地址
                if (xmlAlbumArt != null && xmlAlbumArt.isNotBlank()) {
                    player.updateCoverUrl(xmlAlbumArt)
                }
            } else {
                // 回退: 从 URI 文件名提取
                val name = try { URLDecoder.decode(uri.substringAfterLast("/").substringBefore("?").substringBeforeLast("."), "UTF-8") } catch (e: Exception) { uri.substringAfterLast("/").substringBefore("?").substringBeforeLast(".") }
                Log.i(TAG, "DIDL-Lite title empty, fallback to URI: $name")
                if (name.isNotBlank()) {
                    val parts = name.split(" - ", "-")
                    val title = if (parts.size >= 2) parts.drop(1).joinToString(" - ").trim() else name
                    val artist = if (parts.size >= 2) parts[0].trim() else ""
                    stateManager.currentTitle = title
                    stateManager.currentArtist = artist
                    player.updateMetadata(title, artist)
                }
            }
        } else {
            Log.w(TAG, "No metadata in SetAVTransportURI, extracting from URI")
            val name = try { URLDecoder.decode(uri.substringAfterLast("/").substringBefore("?").substringBeforeLast("."), "UTF-8") } catch (e: Exception) { uri.substringAfterLast("/").substringBefore("?").substringBeforeLast(".") }
            if (name.isNotBlank()) {
                val parts = name.split(" - ", "-")
                val title = if (parts.size >= 2) parts.drop(1).joinToString(" - ").trim() else name
                val artist = if (parts.size >= 2) parts[0].trim() else ""
                stateManager.currentTitle = title
                stateManager.currentArtist = artist
                player.updateMetadata(title, artist)
            }
        }
        // 通知播放器加载
        player.load(uri)

        return buildSoapResponse("SetAVTransportURIResponse") {
            // no response params
            append("")
        }
    }

    /**
     * Play — 开始/继续播放
     */
    private fun handlePlay(body: String): String {
        val speed = extractTag(body, "Speed") ?: "1"
        Log.i(TAG, "Play: speed=$speed, currentState=${stateManager.state}")

        stateManager.setTransportState(AVTStateManager.TransportState.PLAYING)
        player.play()
        Log.i(TAG, "Player play() called")

        return buildSoapResponse("PlayResponse") {
            // no response params
            append("")
        }
    }

    /**
     * Pause — 暂停
     */
    private fun handlePause(): String {
        Log.i(TAG, "Pause")
        stateManager.setTransportState(AVTStateManager.TransportState.PAUSED_PLAYBACK)
        player.pause()

        return buildSoapResponse("PauseResponse") {
            // no response params
            append("")
        }
    }

    /**
     * Stop — 停止
     */
    private fun handleStop(): String {
        Log.i(TAG, "Stop")
        stateManager.setTransportState(AVTStateManager.TransportState.STOPPED)
        player.stop()

        return buildSoapResponse("StopResponse") {
            // no response params
            append("")
        }
    }

    /**
     * Seek — 跳转到指定位置
     */
    private fun handleSeek(body: String): String {
        val unit = extractTag(body, "Unit") ?: "REL_TIME"
        val target = extractTag(body, "Target") ?: "0:00:00"

        Log.i(TAG, "Seek: unit=$unit, target=$target")

        // 解析时间 target (格式: H:MM:SS)
        val millis = parseDurationToMillis(target)
        player.seekTo(millis)

        return buildSoapResponse("SeekResponse") {
            // no response params
            append("")
        }
    }

    /**
     * Next — 下一曲
     */
    private fun handleNext(): String {
        player.next()
        Log.i(TAG, "Next")
        return buildSoapResponse("NextResponse") {
            // no response params
            append("")
        }
    }

    /**
     * Previous — 上一曲
     */
    private fun handlePrevious(): String {
        player.previous()
        Log.i(TAG, "Previous")
        return buildSoapResponse("PreviousResponse") {
            // no response params
            append("")
        }
    }

    /**
     * GetPositionInfo — 获取播放进度（频繁调用）
     */
    private fun handleGetPositionInfo(body: String): String {
        return buildSoapResponse("GetPositionInfoResponse") {
            append(stateManager.buildPositionInfoXml())
        }
    }

    /**
     * GetTransportInfo — 获取传输状态
     */
    private fun handleGetTransportInfo(): String {
        return buildSoapResponse("GetTransportInfoResponse") {
            append(stateManager.buildTransportInfoXml())
        }
    }

    /**
     * GetTransportSettings — 获取传输设置
     */
    private fun handleGetTransportSettings(): String {
        return buildSoapResponse("GetTransportSettingsResponse") {
            append(stateManager.buildTransportSettingsXml())
        }
    }

    /**
     * GetDeviceCapabilities — 设备能力
     */
    private fun handleGetDeviceCapabilities(): String {
        return buildSoapResponse("GetDeviceCapabilitiesResponse") {
            append("""
<PlayMedia>NETWORK</PlayMedia>
<RecMedia>NOT_IMPLEMENTED</RecMedia>
<RecQualityModes>EP_0</RecQualityModes>
""")
        }
    }

    /**
     * GetCurrentTransportActions — 当前可用的操作
     */
    private fun handleGetCurrentTransportActions(): String {
        val actions = buildString {
            when (stateManager.state) {
                AVTStateManager.TransportState.STOPPED,
                AVTStateManager.TransportState.NO_MEDIA_PRESENT -> {
                    append("SetAVTransportURI,SetNextAVTransportURI,GetDeviceCapabilities,GetTransportSettings,Stop")
                }
                AVTStateManager.TransportState.PLAYING -> {
                    append("SetAVTransportURI,SetNextAVTransportURI,Pause,Stop,Seek,Next,Previous,GetPositionInfo,GetTransportInfo,GetTransportSettings,GetDeviceCapabilities")
                }
                AVTStateManager.TransportState.PAUSED_PLAYBACK -> {
                    append("SetAVTransportURI,SetNextAVTransportURI,Play,Stop,Seek,Next,Previous,GetPositionInfo,GetTransportInfo,GetTransportSettings,GetDeviceCapabilities")
                }
                else -> {
                    append("SetAVTransportURI,SetNextAVTransportURI,Stop")
                }
            }
        }

        return buildSoapResponse("GetCurrentTransportActionsResponse") {
            append("<Action>$actions</Action>")
        }
    }

    private fun handleSetNextAVTransportURI(body: String): String {
        return buildSoapResponse("SetNextAVTransportURIResponse") {
            // no response params
            append("")
        }
    }

    // ========== 工具方法 ==========

    /**
     * GetMediaInfo - è·åå½ååªä½ä¿¡æ¯ï¼æäºå®åå®¢æ·ç«¯å¼å®¹æ§éè¦ï¼
     */
    private fun handleGetMediaInfo(body: String): String {
        val instanceId = extractTag(body, "InstanceID") ?: "0"
        val duration = stateManager.trackDuration
        val durationStr = if (duration <= 0) "0:00:00" else {
            val totalSec = duration
            "%d:%02d:%02d".format(totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60)
        }
        val uri = stateManager.currentUri
        val metadata = stateManager.currentUriMetadata

        Log.d(TAG, "GetMediaInfo: instanceId=$instanceId, uri=$uri, duration=$durationStr")
        return buildSoapResponse("GetMediaInfoResponse") {
            append("<NrTracks>1</NrTracks>\n")
            append("<MediaDuration>$durationStr</MediaDuration>\n")
            append("<CurrentURI>$uri</CurrentURI>\n")
            append("<CurrentURIMetaData>${metadata.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</CurrentURIMetaData>\n")
            append("<NextURI></NextURI>\n")
            append("<NextURIMetaData></NextURIMetaData>\n")
            append("<PlayMedium>NETWORK</PlayMedium>\n")
            append("<RecordMedium>NOT_IMPLEMENTED</RecordMedium>\n")
            append("<WriteStatus>NOT_IMPLEMENTED</WriteStatus>\n")
        }
    }

    private fun buildSoapResponse(actionName: String, block: StringBuilder.() -> Unit): String {
        val content = StringBuilder().apply(block).toString()
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:${actionName} xmlns:u="${UpnpConstants.URN_AVT}">
            $content
        </u:${actionName}>
    </s:Body>
</s:Envelope>"""
    }

    private fun StringBuilder.appendTag(name: String, value: String): StringBuilder {
        append("<$name>$value</$name>\n")
        return this
    }

    /**
     * 从 SOAP XML 中提取标签内容
     */
    private fun extractTag(xml: String, tagName: String): String? {
        if (xml.isBlank()) return null
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val localName = parser.name.substringAfterLast(":")
                    if (localName.equals(tagName, ignoreCase = true)) {
                        return parser.nextText()
                    }
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract tag $tagName", e)
            // fallback 正则
            try {
                val flagSet = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                val regex = Regex("<(?:[^:>]+:)?%s[^>]*>(.*?)</(?:[^:>]+:)?%s>".format(tagName, tagName), flagSet)
                regex.find(xml)?.groupValues?.getOrNull(1)
            } catch (e2: Exception) { null }
        }
    }
    /**
     * 将 H:MM:SS 格式转为毫秒
     */
    private fun parseDurationToMillis(duration: String): Long {
        try {
            val parts = duration.split(":")
            return when (parts.size) {
                3 -> parts[0].toLong() * 3600000 + parts[1].toLong() * 60000 + (parts[2].toDouble() * 1000).toLong()
                2 -> parts[0].toLong() * 60000 + (parts[1].toDouble() * 1000).toLong()
                1 -> (parts[0].toDouble() * 1000).toLong()
                else -> 0L
            }
        } catch (e: Exception) {
            return 0L
        }
    }

    /**
     * 从 DIDL-Lite XML 中提取指定标签的值
     */
        /**
     * Extract a tag value from DIDL-Lite XML using direct regex approach.
     * Supports namespace prefixes like dc:title, upnp:albumArtURI.
     */
    private fun extractDidlValue(xml: String, tagName: String): String? {
        if (xml.isBlank()) return null
        try {
            // One regex for both namespaced and non-namespaced tags
            val pattern = "<(?:[^:>]+:)?" + tagName + "[^>]*>(.*?)</(?:[^:>]+:)?" + tagName + ">"
            val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val raw = regex.find(xml)?.groupValues?.getOrNull(1)?.trim() ?: return null
            return decodeXmlEntities(raw)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Decode XML/HTML numeric entities (&#xHHHH; &#DDDD;) and named entities (&amp; &lt; &gt; &quot; &apos;)
     */
    private fun decodeXmlEntities(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            when {
                text.regionMatches(i, "&amp;", 0, 5) -> { sb.append('&'); i += 5 }
                text.regionMatches(i, "&lt;", 0, 4) -> { sb.append('<'); i += 4 }
                text.regionMatches(i, "&gt;", 0, 4) -> { sb.append('>'); i += 4 }
                text.regionMatches(i, "&quot;", 0, 6) -> { sb.append('"'); i += 6 }
                text.regionMatches(i, "&apos;", 0, 6) -> { sb.append("'"); i += 6 }
                i + 3 < text.length && text[i] == '&' && text[i+1] == '#' && text[i+2] == 'x' -> {
                    val end = text.indexOf(';', i + 3)
                    if (end > i && end <= i + 10) {
                        try { sb.append(text.substring(i + 3, end).toInt(16).toChar()); i = end + 1 }
                        catch (_: Exception) { sb.append(text[i]); i++ }
                    } else { sb.append(text[i]); i++ }
                }
                i + 2 < text.length && text[i] == '&' && text[i+1] == '#' -> {
                    val end = text.indexOf(';', i + 2)
                    if (end > i && end <= i + 10) {
                        try { sb.append(text.substring(i + 2, end).toInt(10).toChar()); i = end + 1 }
                        catch (_: Exception) { sb.append(text[i]); i++ }
                    } else { sb.append(text[i]); i++ }
                }
                else -> { sb.append(text[i]); i++ }
            }
        }
        return sb.toString()
    }

}
