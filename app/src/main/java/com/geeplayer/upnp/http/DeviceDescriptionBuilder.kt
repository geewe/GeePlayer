package com.geeplayer.upnp.http

import com.geeplayer.upnp.core.UpnpConstants
import com.geeplayer.upnp.core.UpnpStack

/**
 * 构建 UPnP 设备描述 XML
 */
object DeviceDescriptionBuilder {

    fun build(stack: UpnpStack): String {
        val ip = stack.deviceDescUrl
            .replace("http://", "")
            .substringBefore(":")
        val port = stack.port
        val udn = stack.udn
        val name = stack.name

        return """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
    <specVersion>
        <major>1</major>
        <minor>0</minor>
    </specVersion>
    <device>
        <deviceType>${UpnpConstants.URN_DEVICE}</deviceType>
        <friendlyName>$name</friendlyName>
        <manufacturer>GeePlayer</manufacturer>
        <manufacturerURL>https://github.com/geewe/GeePlayer</manufacturerURL>
        <modelDescription>GeePlayer - DLNA Media Renderer</modelDescription>
        <modelName>GeePlayer</modelName>
        <modelNumber>1.0.0</modelNumber>
        <modelURL>https://github.com/geewe/GeePlayer</modelURL>
        <serialNumber>00000001</serialNumber>
        <UDN>$udn</UDN>
        <iconList>
            <icon>
                <mimetype>${UpnpConstants.DEVICE_ICON_MIME}</mimetype>
                <width>${UpnpConstants.DEVICE_ICON_WIDTH}</width>
                <height>${UpnpConstants.DEVICE_ICON_HEIGHT}</height>
                <depth>${UpnpConstants.DEVICE_ICON_DEPTH}</depth>
                <url>/icon.png</url>
            </icon>
        </iconList>
        <serviceList>
            <service>
                <serviceType>${UpnpConstants.URN_AVT}</serviceType>
                <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                <SCPDURL>/avt/scpd.xml</SCPDURL>
                <controlURL>/upnp/control/avt</controlURL>
                <eventSubURL>/upnp/event/avt</eventSubURL>
            </service>
            <service>
                <serviceType>${UpnpConstants.URN_RC}</serviceType>
                <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
                <SCPDURL>/rc/scpd.xml</SCPDURL>
                <controlURL>/upnp/control/rc</controlURL>
                <eventSubURL>/upnp/event/rc</eventSubURL>
            </service>
            <service>
                <serviceType>${UpnpConstants.URN_CMGR}</serviceType>
                <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
                <SCPDURL>/cmgr/scpd.xml</SCPDURL>
                <controlURL>/upnp/control/cmgr</controlURL>
                <eventSubURL>/upnp/event/cmgr</eventSubURL>
            </service>
        </serviceList>
        <presentationURL>http://$ip:$port/</presentationURL>
    </device>
</root>"""
    }
}
