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

    // 最近 20 条 SOAP 请求记录
    private val _requestHistory = java.util.Collections.synchronizedList(mutableListOf<RequestRecord>())
    val requestHistory: List<RequestRecord> get() = _requestHistory.toList()
    data class RequestRecord(
        val time: String = "",
        val method: String = "",
        val uri: String = "",
        val soapAction: String = "",
        val bodyPreview: String = "",
        val bodyLength: Int = 0,
        val success: Boolean = false
    )

    override fun serve(session: IHTTPSession): Response {
        return try {
            debug.lastMethod = session.method.name
            debug.lastUri = session.uri
            debug.lastError = ""
            Log.i(TAG, "HTTP: ${session.method.name} ${session.uri} from UA=${session.headers["user-agent"] ?: "(none)"}")

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
        <action>
            <name>SetAVTransportURI</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                <argument><name>CurrentURI</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>
                <argument><name>CurrentURIMetaData</name><direction>in</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>SetNextAVTransportURI</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                <argument><name>NextURI</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>
                <argument><name>NextURIMetaData</name><direction>in</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>Play</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                <argument><name>Speed</name><direction>in</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>Pause</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>Stop</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>Seek</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                <argument><name>Unit</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SeekMode</relatedStateVariable></argument>
                <argument><name>Target</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_SeekTarget</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>Next</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>Previous</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>GetPositionInfo</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                <argument><name>Track</name><direction>out</direction><relatedStateVariable>CurrentTrack</relatedStateVariable></argument>
                <argument><name>TrackDuration</name><direction>out</direction><relatedStateVariable>CurrentTrackDuration</relatedStateVariable></argument>
                <argument><name>TrackMetaData</name><direction>out</direction><relatedStateVariable>TrackMetaData</relatedStateVariable></argument>
                <argument><name>TrackURI</name><direction>out</direction><relatedStateVariable>TrackURI</relatedStateVariable></argument>
                <argument><name>RelTime</name><direction>out</direction><relatedStateVariable>RelativeTimePosition</relatedStateVariable></argument>
                <argument><name>AbsTime</name><direction>out</direction><relatedStateVariable>AbsoluteTimePosition</relatedStateVariable></argument>
                <argument><name>RelCount</name><direction>out</direction><relatedStateVariable>RelativeCounter</relatedStateVariable></argument>
                <argument><name>AbsCount</name><direction>out</direction><relatedStateVariable>AbsoluteCounter</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>GetTransportInfo</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                <argument><name>CurrentTransportState</name><direction>out</direction><relatedStateVariable>TransportState</relatedStateVariable></argument>
                <argument><name>CurrentTransportStatus</name><direction>out</direction><relatedStateVariable>TransportStatus</relatedStateVariable></argument>
                <argument><name>CurrentSpeed</name><direction>out</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>GetTransportSettings</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                <argument><name>PlayMode</name><direction>out</direction><relatedStateVariable>CurrentPlayMode</relatedStateVariable></argument>
                <argument><name>RecQualityMode</name><direction>out</direction><relatedStateVariable>CurrentRecordQualityMode</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>GetDeviceCapabilities</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                <argument><name>PlayMedia</name><direction>out</direction><relatedStateVariable>PossiblePlayMedia</relatedStateVariable></argument>
                <argument><name>RecMedia</name><direction>out</direction><relatedStateVariable>PossibleRecordMedia</relatedStateVariable></argument>
                <argument><name>RecQualityModes</name><direction>out</direction><relatedStateVariable>PossibleRecordQualityModes</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>GetMediaInfo</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                <argument><name>NrTracks</name><direction>out</direction><relatedStateVariable>NumberOfTracks</relatedStateVariable></argument>
                <argument><name>MediaDuration</name><direction>out</direction><relatedStateVariable>CurrentMediaDuration</relatedStateVariable></argument>
                <argument><name>CurrentURI</name><direction>out</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>
                <argument><name>CurrentURIMetaData</name><direction>out</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>
                <argument><name>NextURI</name><direction>out</direction><relatedStateVariable>NextAVTransportURI</relatedStateVariable></argument>
                <argument><name>NextURIMetaData</name><direction>out</direction><relatedStateVariable>NextAVTransportURIMetaData</relatedStateVariable></argument>
                <argument><name>PlayMedium</name><direction>out</direction><relatedStateVariable>CurrentPlayMedium</relatedStateVariable></argument>
                <argument><name>RecordMedium</name><direction>out</direction><relatedStateVariable>CurrentRecordMedium</relatedStateVariable></argument>
                <argument><name>WriteStatus</name><direction>out</direction><relatedStateVariable>CurrentWriteStatus</relatedStateVariable></argument>
            </argumentList>
        </action>
        <action>
            <name>GetCurrentTransportActions</name>
            <argumentList>
                <argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>
                <argument><name>Actions</name><direction>out</direction><relatedStateVariable>CurrentTransportActions</relatedStateVariable></argument>
            </argumentList>
        </action>
    </actionList>
    <serviceStateTable>
        <stateVariable sendEvents="yes"><name>TransportState</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="yes"><name>TransportStatus</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="yes"><name>CurrentTransportActions</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="yes"><name>AbsoluteTimePosition</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="yes"><name>RelativeTimePosition</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>AVTransportURI</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>AVTransportURIMetaData</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>NextAVTransportURI</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>NextAVTransportURIMetaData</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>TrackURI</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>TrackMetaData</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>CurrentTrack</name><dataType>i4</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>CurrentTrackDuration</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>CurrentMediaDuration</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>NumberOfTracks</name><dataType>i4</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>A_ARG_TYPE_InstanceID</name><dataType>i4</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>A_ARG_TYPE_SeekMode</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>A_ARG_TYPE_SeekTarget</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>TransportPlaySpeed</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>CurrentPlayMode</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>CurrentRecordQualityMode</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>PossiblePlayMedia</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>PossibleRecordMedia</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>PossibleRecordQualityModes</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>CurrentPlayMedium</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>CurrentRecordMedium</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>CurrentWriteStatus</name><dataType>string</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>RelativeCounter</name><dataType>i4</dataType></stateVariable>
        <stateVariable sendEvents="no"><name>AbsoluteCounter</name><dataType>i4</dataType></stateVariable>
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
        val ct = session.headers.entries.firstOrNull {
            it.key.equals("content-type", ignoreCase = true)
        }?.value ?: "(none)"

        debug.lastSoapAction = soapAction
        debug.lastBody = body.take(300)
        Log.i(TAG, "SOAP: '$soapAction' bodyLen=${body.length} ct=$ct")

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

        val success = responseXml != null
        _requestHistory.add(RequestRecord(
            time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
            method = session.method.name,
            uri = session.uri,
            soapAction = soapAction,
            bodyPreview = body.take(120),
            bodyLength = body.length,
            success = success
        ))
        if (_requestHistory.size > 20) _requestHistory.removeAt(0)

        return if (success) {
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
            appendLine("<h2>Request History (last ${_requestHistory.size})</h2>")
            appendLine("<table border='1' cellpadding='4' cellspacing='0' style='border-collapse:collapse;width:100%;font-size:13px'>")
            appendLine("<tr style='background:#333'><th>Time</th><th>Action</th><th>Len</th><th>OK</th><th>Body Preview</th></tr>")
            _requestHistory.reversed().forEach { r ->
                val cls = if (r.success) "ok" else "fail"
                appendLine("<tr><td>${r.time}</td><td>${r.soapAction}</td><td>${r.bodyLength}</td><td class='$cls'>${r.success}</td><td style='font-size:11px'>${r.bodyPreview.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")}</td></tr>")
            }
            appendLine("</table>")
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
                    val shortUri = d.uri.take(60) + if (d.uri.length > 60) "..." else ""
                appendLine("$i: ${d.name} ($shortUri)")
                }
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", text)
    }

    private fun handleWebIndex(): Response {
        val html = """<!DOCTYPE html>
<html><head><meta charset=\"utf-8\"><title>GeePlayer</title>
<style>body{font-family:sans-serif;background:#121224;color:#fff;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0}
.card{background:#1E1E3A;border-radius:20px;padding:32px;text-align:center;width:360px}
h1{color:#BB86FC}.info{color:rgba(255,255,255,0.7)}</style></head>
<body><div class="card"><h1>GeePlayer</h1>
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
