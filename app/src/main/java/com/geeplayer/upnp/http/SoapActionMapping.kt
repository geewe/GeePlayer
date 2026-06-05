package com.geeplayer.upnp.http

import com.geeplayer.upnp.core.UpnpConstants

/**
 * SOAP Action 到处理函数的映射
 */
object SoapActionMapping {

    data class ActionInfo(
        val serviceType: String,
        val actionName: String
    )

    fun parse(soapAction: String): ActionInfo? {
        val clean = soapAction.trim('"', ' ')
        val hashIndex = clean.lastIndexOf('#')
        if (hashIndex < 0) return null

        val serviceType = clean.substring(0, hashIndex)
        val actionName = clean.substring(hashIndex + 1).substringBefore("(")

        return ActionInfo(serviceType, actionName)
    }
}
