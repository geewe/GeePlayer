package com.geeplayer.upnp.compatibility

import android.util.Log

/**
 * 协议修正器 — 自动修正不规范的 SOAP 请求
 */
object ProtocolFixer {
    private const val TAG = "ProtocolFixer"

    /**
     * 修正 SetAVTransportURI 的 URI
     * （QQ音乐等推送端可能发送格式不规范的 URI）
     */
    fun fixUri(uri: String, source: PushDeviceDetector.PushSource): String {
        return when (source) {
            PushDeviceDetector.PushSource.QQ_MUSIC -> {
                // QQ音乐有时会包含额外的查询参数
                uri.trim()
            }
            PushDeviceDetector.PushSource.NETEASE_MUSIC -> {
                // 网易云可能需要处理认证 token
                uri.trim()
            }
            else -> uri.trim()
        }
    }

    /**
     * 修正 SOAP 请求正文中的 XML 命名空间问题
     */
    fun fixSoapBody(body: String): String {
        // 某些推送端会在 XML 中使用错误的命名空间，尝试修复
        return body
            .replace("s:", "")
            .replace("u:", "")
    }

    /**
     * 是否需要延迟响应（缓冲时间）
     */
    fun getResponseDelayMillis(source: PushDeviceDetector.PushSource): Long {
        return when (source) {
            PushDeviceDetector.PushSource.QQ_MUSIC -> 200L
            PushDeviceDetector.PushSource.NETEASE_MUSIC -> 150L
            else -> 0L
        }
    }
}
