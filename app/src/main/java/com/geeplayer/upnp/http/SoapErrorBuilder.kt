package com.geeplayer.upnp.http

/**
 * SOAP 错误响应构建器
 */
object SoapErrorBuilder {

    fun build(soapAction: String, errorCode: Int, errorDesc: String): String {
        val actionName = soapAction.substringAfterLast("#").substringBefore("(")
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <s:Fault>
            <faultcode>s:Client</faultcode>
            <faultstring>UPnPError</faultstring>
            <detail>
                <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                    <errorCode>$errorCode</errorCode>
                    <errorDescription>$errorDesc</errorDescription>
                </UPnPError>
            </detail>
        </s:Fault>
    </s:Body>
</s:Envelope>"""
    }
}
