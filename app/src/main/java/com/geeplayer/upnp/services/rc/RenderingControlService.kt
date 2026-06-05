package com.geeplayer.upnp.services.rc

import android.util.Log
import com.geeplayer.upnp.core.UpnpConstants
import com.geeplayer.upnp.services.avt.AVTStateManager
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * RenderingControl:1 服务 — 音量/静音/画面控制
 */
class RenderingControlService(
    private val stateManager: AVTStateManager,
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private val player: com.geeplayer.player.DlnaPlayer? = null
) {
    private companion object {
        private const val TAG = "RenderingControlService"

        val SUPPORTED_ACTIONS = setOf(
            "ListPresets", "SelectPreset",
            "GetVolume", "SetVolume",
            "GetMute", "SetMute",
            "GetVolumeDB", "SetVolumeDB",
            "GetVolumeDBRange"
        )
    }

    fun handleAction(soapAction: String, body: String): String? {
        val actionName = soapAction.substringAfterLast("#").substringBefore("(")
        Log.d(TAG, "Handling action: $actionName")

        return try {
            when (actionName) {
                "ListPresets" -> handleListPresets()
                "SelectPreset" -> handleSelectPreset(body)
                "GetVolume" -> handleGetVolume(body)
                "SetVolume" -> handleSetVolume(body)
                "GetMute" -> handleGetMute(body)
                "SetMute" -> handleSetMute(body)
                "GetVolumeDB" -> handleGetVolumeDB(body)
                "SetVolumeDB" -> handleSetVolumeDB(body)
                "GetVolumeDBRange" -> handleGetVolumeDBRange()
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling action: $actionName", e)
            null
        }
    }

    private fun handleListPresets(): String {
        return buildSoapResponse("ListPresetsResponse", UpnpConstants.URN_RC) {
            append("<CurrentPresetNameList>FactoryDefaults</CurrentPresetNameList>")
        }
    }

    private fun handleSelectPreset(body: String): String {
        return buildSoapResponse("SelectPresetResponse", UpnpConstants.URN_RC) {
            // no response params
            append("")
        }
    }

    private fun handleGetVolume(body: String): String {
        val channel = extractTag(body, "Channel") ?: "Master"
        return buildSoapResponse("GetVolumeResponse", UpnpConstants.URN_RC) {
            append("<CurrentVolume>${stateManager.volume}</CurrentVolume>")
        }
    }

    private fun handleSetVolume(body: String): String {
        val channel = extractTag(body, "Channel") ?: "Master"
        val desiredVolume = extractTag(body, "DesiredVolume")?.toIntOrNull() ?: 50
        stateManager.volume = desiredVolume.coerceIn(0, 100)
        player?.setVolume(desiredVolume / 100f)
        Log.i(TAG, "SetVolume: $desiredVolume")
        return buildSoapResponse("SetVolumeResponse", UpnpConstants.URN_RC) {
            // no response params
            append("")
        }
    }

    private fun handleGetMute(body: String): String {
        val channel = extractTag(body, "Channel") ?: "Master"
        return buildSoapResponse("GetMuteResponse", UpnpConstants.URN_RC) {
            append("<CurrentMute>${if (stateManager.mute) "1" else "0"}</CurrentMute>")
        }
    }

    private fun handleSetMute(body: String): String {
        val channel = extractTag(body, "Channel") ?: "Master"
        val desiredMute = extractTag(body, "DesiredMute") == "1"
        stateManager.mute = desiredMute
        player?.setMute(desiredMute)
        Log.i(TAG, "SetMute: $desiredMute")
        return buildSoapResponse("SetMuteResponse", UpnpConstants.URN_RC) {
            // no response params
            append("")
        }
    }

    private fun handleGetVolumeDB(body: String): String {
        val channel = extractTag(body, "Channel") ?: "Master"
        val db = (stateManager.volume - 50).toFloat()  // 简单映射
        return buildSoapResponse("GetVolumeDBResponse", UpnpConstants.URN_RC) {
            append("<CurrentVolume>$db</CurrentVolume>")
        }
    }

    private fun handleSetVolumeDB(body: String): String {
        val desiredDB = extractTag(body, "DesiredVolume")?.toFloatOrNull() ?: 0f
        stateManager.volume = (desiredDB + 50).toInt().coerceIn(0, 100)
        return buildSoapResponse("SetVolumeDBResponse", UpnpConstants.URN_RC) {
            // no response params
            append("")
        }
    }

    private fun handleGetVolumeDBRange(): String {
        return buildSoapResponse("GetVolumeDBRangeResponse", UpnpConstants.URN_RC) {
            append("<MinValue>-5000</MinValue><MaxValue>0</MaxValue>")
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

    private fun extractTag(xml: String, tagName: String): String? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.equals(tagName, ignoreCase = true)) {
                    return parser.nextText()
                }
                eventType = parser.next()
            }
            null
        } catch (e: Exception) {
            val regex = Regex("""<$tagName[^>]*>(.*?)</$tagName>""", RegexOption.IGNORE_CASE)
            regex.find(xml)?.groupValues?.getOrNull(1)
        }
    }
}
