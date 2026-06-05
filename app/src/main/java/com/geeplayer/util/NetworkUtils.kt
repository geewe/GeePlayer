package com.geeplayer.util

import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.net.NetworkInterface

object NetworkUtils {

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isUp && !networkInterface.isLoopback &&
                    networkInterface.displayName.contains("wlan", ignoreCase = true)) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is InetAddress && !addr.isLoopbackAddress &&
                            addr.hostAddress?.contains(".") == true) {
                            return addr.hostAddress ?: "0.0.0.0"
                        }
                    }
                }
            }
            // fallback: 取第一个非回环 IPv4
            val interfaces2 = NetworkInterface.getNetworkInterfaces()
            while (interfaces2.hasMoreElements()) {
                val networkInterface = interfaces2.nextElement()
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is InetAddress && !addr.isLoopbackAddress &&
                            addr.hostAddress?.contains(".") == true) {
                            return addr.hostAddress ?: "0.0.0.0"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return "127.0.0.1"
        }
        return "127.0.0.1"
    }

    fun getWifiSsid(context: Context): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val connectionInfo = wifiManager?.connectionInfo
        val ssid = connectionInfo?.ssid ?: "未知"
        return ssid.trim('"')
    }
}
