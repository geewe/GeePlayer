package com.geeplayer.upnp.http

import android.util.Log
import com.geeplayer.upnp.core.UpnpConstants
import com.geeplayer.upnp.core.UpnpStack
import com.geeplayer.upnp.services.avt.AVTransportService
import com.geeplayer.upnp.services.rc.RenderingControlService
import com.geeplayer.upnp.services.cmgr.ConnectionManagerService
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream

class UpnpHttpServer(
    private val upnpStack: UpnpStack,
    port: Int = UpnpConstants.HTTP_PORT
) : NanoHTTPD(port) {
    private companion object { private const val TAG = "UpnpHttpServer" }

    var avTransportService: AVTransportService? = null
    var renderingControlService: RenderingControlService? = null
    var connectionManagerService: ConnectionManagerService? = null

    // 最近一次 SOAP 请求的调试信息
    data class DebugInfo(
        var lastMethod: String = "",
        var lastUri: String = "",
        var lastSoapAction: String = "",
        var lastBody: String = "",
        var lastError: String = ""
    )
    val debug = DebugInfo()

    override fun serve(session: IHTTPSession): Response {
        return try {
            debug.lastMethod = session.method.name
            debug.lastUri = session.uri
            debug.lastError = ""

            when (session.uri) {
                "/device.xml", "/description.xml" -> handleDeviceDescription()
                "/icon.png" -> handleDeviceIcon()
                "/avt/scpd.xml" -> handleAVTScpd()
                "/rc/scpd.xml" -> handleRCScpd()
                "/cmgr/scpd.xml" -> handleCMGRScpd()
                "/api/debug" -> handleDebug()
                else -> {
                    if (session.uri.startsWith("/upnp/control/")) handleSoapControl(session)
                    else if (session.uri.startsWith("/upnp/event/")) handleEvent(session)
                    else if (session.uri.startsWith("/api/player-state")) handlePlayerState()
                else if (session.uri == "/" || session.uri == "/index.html") handleWebIndex()
                    else newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${session.uri}", e)
            debug.lastError = "${e::class.simpleName}: ${e.message}"
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
        }
    }

    private fun handleDeviceDescription(): Response {
        val xml = DeviceDescriptionBuilder.build(upnpStack)
        return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=\"utf-8\"", xml)
    }

    private fun handleDeviceIcon(): Response {
        val iconBytes = byteArrayOf(
            -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82,
            0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, -60, -119,
            0, 0, 0, 10, 73, 68, 65, 84, 120, -38, 99, 96, 0, 0, 0, 2,
            0, 1, -26, 43, -43, 12, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126
        )
        return newFixedLengthResponse(Response.Status.OK, "image/png",
            ByteArrayInputStream(iconBytes), iconBytes.size.toLong())
    }

    private fun handleAVTScpd(): Response {
        val scpd = """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
    <specVersion><major>1</major><minor>0</minor></specVersion>
    <actionList>
        <action><name>SetAVTransportURI</name></action>
        <action><name>SetNextAVTransportURI</name></action>
        <action><name>Play</name></action>
        <action><name>Pause</name></action>
        <action><name>Stop</name></action>
        <action><name>Seek</name></action>
        <action><name>Next</name></action>
        <action><name>Previous</name></action>
        <action><name>GetPositionInfo</name></action>
        <action><name>GetTransportInfo</name></action>
        <action><name>GetTransportSettings</name></action>
        <action><name>GetDeviceCapabilities</name></action>
        <action><name>GetCurrentTransportActions</name></action>
    </actionList>
    <serviceStateTable>
        <stateVariable sendEvents="yes"><name>TransportState</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="yes"><name>CurrentTransportActions</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="yes"><name>AbsoluteTimePosition</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="yes"><name>RelativeTimePosition</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>AVTransportURI</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>TrackDuration</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>TrackMetaData</name><dataType>string</dataType></stateVariable>
    </serviceStateTable>
</scpd>"""
        return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=\"utf-8\"", scpd)
    }

    private fun handleRCScpd(): Response {
        val scpd = """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
    <specVersion><major>1</major><minor>0</minor></specVersion>
    <actionList>
        <action><name>ListPresets</name></action>
        <action><name>SelectPreset</name></action>
        <action><name>GetVolume</name></action>
        <action><name>SetVolume</name></action>
        <action><name>GetMute</name></action>
        <action><name>SetMute</name></action>
        <action><name>GetVolumeDB</name></action>
        <action><name>SetVolumeDB</name></action>
        <action><name>GetVolumeDBRange</name></action>
    </actionList>
    <serviceStateTable>
        <stateVariable sendEvents="yes"><name>Volume</name><dataType>i4</dataType></stateVariable>
        <stateVariable sendEvents="yes"><name>Mute</name><dataType>boolean</dataType></stateVariable>
    </serviceStateTable>
</scpd>"""
        return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=\"utf-8\"", scpd)
    }

    private fun handleCMGRScpd(): Response {
        val scpd = """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0">
    <specVersion><major>1</major><minor>0</minor></specVersion>
    <actionList>
        <action><name>GetProtocolInfo</name></action>
        <action><name>PrepareForConnection</name></action>
        <action><name>GetCurrentConnectionIDs</name></action>
        <action><name>GetCurrentConnectionInfo</name></action>
    </actionList>
    <serviceStateTable>
        <stateVariable sendEvents="yes"><name>ConnectionIDs</name><dataType>string</dataType></stateVariable>
    </serviceStateTable>
</scpd>"""
        return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=\"utf-8\"", scpd)
    }

    private fun handleSoapControl(session: IHTTPSession): Response {
        val body = parseBody(session)
        val soapAction = session.headers.entries.firstOrNull {
            it.key.equals("SOAPACTION", ignoreCase = true)
        }?.value?.trim('"', ' ') ?: ""
        val serviceType = soapAction.substringBeforeLast("#")

        debug.lastSoapAction = soapAction
        debug.lastBody = body.take(300)

        val responseXml = when (serviceType) {
            UpnpConstants.URN_AVT -> avTransportService?.handleAction(soapAction, body)
            UpnpConstants.URN_RC -> renderingControlService?.handleAction(soapAction, body)
            UpnpConstants.URN_CMGR -> connectionManagerService?.handleAction(soapAction, body)
            else -> {
                val msg = "Unknown service: '$serviceType' from SOAPACTION='$soapAction'"
                Log.w(TAG, msg)
                debug.lastError = msg
                null
            }
        }

        return if (responseXml != null) {
            newFixedLengthResponse(Response.Status.OK, "text/xml; charset=\"utf-8\"", responseXml)
        } else {
            val avtOk = avTransportService != null
            val msg = "SOAP action '$soapAction' failed. avt=${avtOk}, bodyLen=${body.length}"
            Log.w(TAG, msg)
            debug.lastError = msg
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, "text/xml; charset=\"utf-8\"",
                SoapErrorBuilder.build(soapAction, 501, "Action Failed")
            )
        }
    }

    private fun handleEvent(session: IHTTPSession): Response {
        val methodStr = session.method.name
        return when {
            methodStr == "GET" -> {
                newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            }
            methodStr == "SUBSCRIBE" -> {
                val cb = session.headers.entries.firstOrNull {
                    it.key.equals("CALLBACK", ignoreCase = true)
                }?.value?.trim('<', '>', ' ') ?: ""
                val timeout = session.headers.entries.firstOrNull {
                    it.key.equals("TIMEOUT", ignoreCase = true)
                }?.value?.removePrefix("Second-")?.toIntOrNull() ?: 1800
                val sid = "uuid:${java.util.UUID.randomUUID()}"
                Log.i(TAG, "SUBSCRIBE: cb=$cb timeout=$timeout sid=$sid")
                val resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
                resp.addHeader("SID", sid)
                resp.addHeader("TIMEOUT", "Second-$timeout")
                resp
            }
            methodStr == "UNSUBSCRIBE" -> {
                val sid = session.headers.entries.firstOrNull {
                    it.key.equals("SID", ignoreCase = true)
                }?.value ?: ""
                Log.i(TAG, "UNSUBSCRIBE: sid=$sid")
                newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            }
            else -> newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Method Not Allowed")
        }
    }

    private fun handleDebug(): Response {
        val info = buildString {
            appendLine("<!DOCTYPE html><html><head><meta charset='utf-8'><title>DLNA Debug</title>")
            appendLine("<style>body{font-family:monospace;background:#121224;color:#fff;padding:20px}")
            appendLine("pre{background:#1E1E3A;padding:16px;border-radius:8px}")
            appendLine(".ok{color:#4CAF50}.fail{color:#f44336}}</style></head><body>")
            appendLine("<h1>DLNA Debug</h1>")

            appendLine("<h2>Services</h2><ul>")
            appendLine("<li>AVTransport: <span class='${if (avTransportService != null) "ok" else "fail"}'>${avTransportService != null}</span></li>")
            appendLine("<li>RenderingControl: <span class='${if (renderingControlService != null) "ok" else "fail"}'>${renderingControlService != null}</span></li>")
            appendLine("<li>ConnectionManager: <span class='${if (connectionManagerService != null) "ok" else "fail"}'>${connectionManagerService != null}</span></li>")
            appendLine("</ul>")

            appendLine("<h2>Last Request</h2><pre>")
            appendLine("Method: ${debug.lastMethod}")
            appendLine("URI: ${debug.lastUri}")
            appendLine("SOAPACTION: ${debug.lastSoapAction}")
            appendLine("</pre>")
            appendLine("<h3>SOAP Body (escaped)</h3><pre>")
            appendLine(debug.lastBody.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
            appendLine("</pre>")
            appendLine("<h3>Errors</h3><pre>")
            appendLine(debug.lastError)
            appendLine("</pre>")
            appendLine("<h3>Player State</h3><pre id='playerState'>")
            appendLine("(page not refreshed since last request)")
            appendLine("</pre>")
            appendLine("<script>")
            appendLine("setInterval(function(){")
            appendLine("  var x = new XMLHttpRequest();")
            appendLine("  x.open('GET', '/api/player-state', true);")
            appendLine("  x.onload = function(){ document.getElementById('playerState').textContent = x.responseText; };")
            appendLine("  x.send();")
            appendLine("}, 2000);")
            appendLine("</script>")

            appendLine("<h2>Links</h2>")
            appendLine("<a href='/device.xml' style='color:#BB86FC'>device.xml</a><br>")
            appendLine("<a href='/avt/scpd.xml' style='color:#BB86FC'>avt/scpd.xml</a><br>")
            appendLine("<a href='/rc/scpd.xml' style='color:#BB86FC'>rc/scpd.xml</a><br>")
            appendLine("<a href='/cmgr/scpd.xml' style='color:#BB86FC'>cmgr/scpd.xml</a><br>")
            appendLine("</body></html>")
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", info)
    }

    private fun handlePlayerState(): Response {
        val avt = avTransportService
        val state = com.geeplayer.service.ReceiverForegroundService.currentPlayer?.playerState?.value
        val text = buildString {
            appendLine("AVTransport connected: ${avt != null}")
            if (state != null) {
                appendLine("Title: ${state.metadataTitle}")
                appendLine("Artist: ${state.metadataArtist}")
                appendLine("URI: ${state.currentUri}")
                appendLine("Playing: ${state.isPlaying}")
                appendLine("Loading: ${state.isLoading}")
                appendLine("Duration: ${state.duration}ms")
                appendLine("Position: ${state.currentPosition}ms")
                appendLine("Error: ${state.error}")
                appendLine("Volume: ${state.volume}")
                appendLine("Muted: ${state.isMuted}")
                appendLine("CoverUrl: ${state.coverUrl}")
            } else {
                appendLine("No player state available")
            }
            if (avt != null) {
                appendLine("--- Raw Metadata (last) ---")
                val raw = avt.lastRawMetadata
                if (raw.isNotBlank()) {
                    appendLine(raw.take(500))
                } else {
                    appendLine("(empty)")
                }
                appendLine("")
                appendLine("--- Connected Devices ---")
                avt.connectedDevices.forEachIndexed { i, d ->
                    appendLine("$i: ${d.name} (${d.uri})")
                }
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", text)
    }

    private fun handleWebIndex(): Response {
        val html = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>DLNA Receiver</title>
<style>body{font-family:sans-serif;background:#121224;color:#fff;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0}
.card{background:#1E1E3A;border-radius:20px;padding:32px;text-align:center;width:360px}
h1{color:#BB86FC}.info{color:rgba(255,255,255,0.7)}</style></head>
<body><div class="card"><h1>DLNA Receiver</h1>
<p class="info">Media Renderer 运行中</p>
<p class="info">${upnpStack.name}</p>
<p><a href="/api/debug" style="color:#BB86FC">调试信息</a></p>
<p><a href="/device.xml" style="color:#BB86FC">device.xml</a></p>
</div></body></html>"""
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    /**
     * 读取 SOAP POST 请求体（始终以原始字节 + UTF-8 解码）
     */
    private fun parseBody(session: IHTTPSession): String {
        // 直接从输入流读取原始字节，然后用 UTF-8 解码
        // 不依赖 NanoHTTPD 的 parseBody（它可能用错误编码）
        try {
            val cl = session.headers.entries.firstOrNull {
                it.key.equals("content-length", ignoreCase = true)
            }?.value?.toIntOrNull() ?: 0
            if (cl > 0 && cl < 1_000_000) {
                val buf = ByteArray(cl)
                var total = 0
                val inputStream = session.getInputStream()
                while (total < cl) {
                    val r = inputStream.read(buf, total, cl - total)
                    if (r < 0) break
                    total += r
                }
                if (total > 0) {
                    val body = String(buf, 0, total, Charsets.UTF_8)
                    Log.v(TAG, "SOAP body (${total} bytes): ${body.take(120)}...")
                    return body
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct body read error: ${e.message}")
        }

        // 回退: NanoHTTPD parseBody
        try {
            val files = LinkedHashMap<String, String>()
            session.parseBody(files)
            val body = files["postData"]?.takeIf { it.isNotBlank() }
                ?: files[""]?.takeIf { it.isNotBlank() }
                ?: files.values.firstOrNull { it.isNotBlank() }
            if (body != null) {
                Log.v(TAG, "SOAP body (fallback, ${body.length} chars): ${body.take(120)}...")
                return body
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseBody fallback error: ${e.message}")
        }

        debug.lastError += " | body empty"
        return ""
    }
}
