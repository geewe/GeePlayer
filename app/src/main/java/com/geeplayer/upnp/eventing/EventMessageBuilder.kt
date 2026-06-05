package com.geeplayer.upnp.eventing

/**
 * GENA 事件通知报文构建器
 */
object EventMessageBuilder {

    fun buildStateUpdate(serviceUrn: String, properties: Map<String, String>): String {
        val props = properties.entries.joinToString("\n") { (key, value) ->
            "            <$key>$value</$key>"
        }

        return """<?xml version="1.0" encoding="utf-8"?>
<e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">
    <e:property>
$props
    </e:property>
</e:propertyset>"""
    }
}
