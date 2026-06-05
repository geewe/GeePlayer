package com.geeplayer.upnp.core

/**
 * UPnP 协议常量定义
 */
object UpnpConstants {
    // SSDP 组播地址
    const val SSDP_ADDRESS = "239.255.255.250"
    const val SSDP_PORT = 1900
    const val HTTP_PORT = 49820  // 默认 HTTP 服务端口

    // URN 标识
    const val URN_DEVICE = "urn:schemas-upnp-org:device:MediaRenderer:1"
    const val URN_AVT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val URN_RC = "urn:schemas-upnp-org:service:RenderingControl:1"
    const val URN_CMGR = "urn:schemas-upnp-org:service:ConnectionManager:1"

    const val URN_AVT_EVENT = "urn:schemas-upnp-org:service:AVTransport:1#"
    const val URN_RC_EVENT = "urn:schemas-upnp-org:service:RenderingControl:1#"

    // SOAP 命名空间
    const val SOAP_NS = "urn:schemas-upnp-org:service-1-0"
    const val SOAP_ENV = "http://schemas.xmlsoap.org/soap/envelope/"

    // 设备描述
    const val DEVICE_ICON_WIDTH = 120
    const val DEVICE_ICON_HEIGHT = 120
    const val DEVICE_ICON_DEPTH = 24
    const val DEVICE_ICON_MIME = "image/png"

    // SSDP 心跳间隔
    const val SSDP_ALIVE_INTERVAL_MS = 30_000L
    const val SSDP_BOOTUP_INTERVAL_MS = 100L

    // SSDP 报文常量
    object Ssdp {
        const val MX = 3
        const val MAN = "\"ssdp:discover\""
        const val SERVER = "Android/14 UPnP/1.0 GeePlayer/1.0"

        // ST (Search Target) 值
        const val ST_ALL = "ssdp:all"
        const val ST_ROOTDEVICE = "upnp:rootdevice"
        const val ST_MEDIARENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"
        const val ST_AVT = "urn:schemas-upnp-org:service:AVTransport:1"
        const val ST_RC = "urn:schemas-upnp-org:service:RenderingControl:1"
        const val ST_CMGR = "urn:schemas-upnp-org:service:ConnectionManager:1"
    }
}
