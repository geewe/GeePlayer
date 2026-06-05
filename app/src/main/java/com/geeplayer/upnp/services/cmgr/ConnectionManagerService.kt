package com.geeplayer.upnp.services.cmgr

import android.util.Log
import com.geeplayer.upnp.core.UpnpConstants

/**
 * ConnectionManager:1 服务 — 连接与协议管理
 */
class ConnectionManagerService {
    private companion object {
        private const val TAG = "ConnectionManagerService"
    }

    fun handleAction(soapAction: String, body: String): String? {
        val actionName = soapAction.substringAfterLast("#").substringBefore("(")
        Log.d(TAG, "Handling action: $actionName")

        return try {
            when (actionName) {
                "GetProtocolInfo" -> handleGetProtocolInfo()
                "PrepareForConnection" -> handlePrepareForConnection()
                "GetCurrentConnectionIDs" -> handleGetCurrentConnectionIDs()
                "GetCurrentConnectionInfo" -> handleGetCurrentConnectionInfo(body)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling action: $actionName", e)
            null
        }
    }

    /**
     * GetProtocolInfo — 声明支持的传输协议和内容格式（兼容性关键）
     */
    private fun handleGetProtocolInfo(): String {
        // Source: 我们作为 MediaRenderer，可以接收的协议
        // Sink: 我们可以播放的格式
        return buildSoapResponse("GetProtocolInfoResponse", UpnpConstants.URN_CMGR) {
            append("""
<Source>http-get:*:audio/mpeg:*,
http-get:*:audio/x-ms-wma:*,
http-get:*:audio/x-wav:*,
http-get:*:audio/wav:*,
http-get:*:audio/ogg:*,
http-get:*:audio/flac:*,
http-get:*:audio/x-flac:*,
http-get:*:audio/aac:*,
http-get:*:audio/x-aac:*,
http-get:*:audio/mp4:*,
http-get:*:audio/x-m4a:*,
http-get:*:audio/x-ms-wma:*,
http-get:*:audio/alac:*,
http-get:*:audio/x-alac:*,
http-get:*:application/octet-stream:*</Source>
<Sink>http-get:*:audio/mpeg:*,
http-get:*:audio/x-ms-wma:*,
http-get:*:audio/x-wav:*,
http-get:*:audio/wav:*,
http-get:*:audio/ogg:*,
http-get:*:audio/flac:*,
http-get:*:audio/x-flac:*,
http-get:*:audio/aac:*,
http-get:*:audio/x-aac:*,
http-get:*:audio/mp4:*,
http-get:*:audio/x-m4a:*,
http-get:*:audio/x-ms-wma:*,
http-get:*:audio/alac:*,
http-get:*:audio/x-alac:*,
http-get:*:application/octet-stream:*</Sink>
""")
        }
    }

    private fun handlePrepareForConnection(): String {
        return buildSoapResponse("PrepareForConnectionResponse", UpnpConstants.URN_CMGR) {
            append("<ConnectionID>0</ConnectionID>\n<AVTransportID>0</AVTransportID>\n<RenderingControlID>0</RenderingControlID>")
        }
    }

    private fun handleGetCurrentConnectionIDs(): String {
        return buildSoapResponse("GetCurrentConnectionIDsResponse", UpnpConstants.URN_CMGR) {
            append("<ConnectionIDs>0</ConnectionIDs>")
        }
    }

    private fun handleGetCurrentConnectionInfo(body: String): String {
        return buildSoapResponse("GetCurrentConnectionInfoResponse", UpnpConstants.URN_CMGR) {
            append("""
<RsvConnectionID>0</RsvConnectionID>
<PeerConnectionManager></PeerConnectionManager>
<PeerConnectionID>-1</PeerConnectionID>
<Direction>Input</Direction>
<Status>OK</Status>
""")
        }
    }

    private fun buildSoapResponse(actionName: String, serviceUrn: String, block: StringBuilder.() -> Unit): String {
        val content = StringBuilder().apply(block).toString()
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:${actionName} xmlns:u="$serviceUrn">
            $content
        </u:${actionName}>
    </s:Body>
</s:Envelope>"""
    }

    private fun StringBuilder.appendTag(name: String, value: String): StringBuilder {
        append("<$name>$value</$name>\n")
        return this
    }
}
