package com.geeplayer.upnp.compatibility

import android.util.Log

/**
 * 推送端检测器 — 从请求特征识别推送端
 */
object PushDeviceDetector {

    enum class PushSource {
        BUBBLE_UPNP,
        QQ_MUSIC,
        NETEASE_MUSIC,
        VLC,
        WINDOWS_MEDIA_PLAYER,
        FOOBAR2000,
        PLEX,
        KODI,
        MCONNECT,
        UNKNOWN
    }

    data class DetectionResult(
        val source: PushSource,
        val userAgent: String = "",
        val displayName: String = ""
    )

    /**
     * 从 HTTP 请求头检测推送端
     */
    fun detect(userAgent: String?, callback: String?): DetectionResult {
        val ua = userAgent?.lowercase() ?: ""
        val cb = callback?.lowercase() ?: ""

        return when {
            ua.contains("bubbleupnp") || cb.contains("bubbleupnp") ->
                DetectionResult(PushSource.BUBBLE_UPNP, displayName = "BubbleUPnP")
            ua.contains("qqmusic") || cb.contains("qqmusic") ->
                DetectionResult(PushSource.QQ_MUSIC, displayName = "QQ音乐")
            ua.contains("netease") || ua.contains("cloudmusic") ->
                DetectionResult(PushSource.NETEASE_MUSIC, displayName = "网易云音乐")
            ua.contains("vlc") ->
                DetectionResult(PushSource.VLC, displayName = "VLC")
            ua.contains("windows") && ua.contains("media") ->
                DetectionResult(PushSource.WINDOWS_MEDIA_PLAYER, displayName = "Windows Media Player")
            ua.contains("foobar") ->
                DetectionResult(PushSource.FOOBAR2000, displayName = "Foobar2000")
            ua.contains("plex") ->
                DetectionResult(PushSource.PLEX, displayName = "Plex")
            cb.contains("android") && cb.contains("mconnect") ->
                DetectionResult(PushSource.MCONNECT, displayName = "mConnect")
            else ->
                DetectionResult(PushSource.UNKNOWN, displayName = "未知设备")
        }
    }
}
