package com.geeplayer.upnp.core

/**
 * XML 命名空间管理，用于 SOAP 请求/响应路由
 */
object UpnpNamespace {
    // 服务类型 -> 控制路径映射
    val SERVICE_CONTROL_PATHS = mapOf(
        UpnpConstants.URN_AVT to "/upnp/control/avt",
        UpnpConstants.URN_RC to "/upnp/control/rc",
        UpnpConstants.URN_CMGR to "/upnp/control/cmgr"
    )

    val SERVICE_EVENT_PATHS = mapOf(
        UpnpConstants.URN_AVT to "/upnp/event/avt",
        UpnpConstants.URN_RC to "/upnp/event/rc"
    )

    fun getServiceTypeFromPath(path: String): String? {
        return when (path) {
            "/upnp/control/avt" -> UpnpConstants.URN_AVT
            "/upnp/control/rc" -> UpnpConstants.URN_RC
            "/upnp/control/cmgr" -> UpnpConstants.URN_CMGR
            "/upnp/event/avt" -> UpnpConstants.URN_AVT_EVENT
            "/upnp/event/rc" -> UpnpConstants.URN_RC_EVENT
            else -> null
        }
    }
}
