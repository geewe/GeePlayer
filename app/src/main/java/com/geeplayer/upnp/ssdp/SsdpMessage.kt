package com.geeplayer.upnp.ssdp

object SsdpMessage {
    data class SsdpRequest(val method: String, val headers: Map<String, String>) {
        val st: String? get() = headers["ST"]
        val nt: String? get() = headers["NT"]
        val nts: String? get() = headers["NTS"]
        val location: String? get() = headers["LOCATION"]
        val usn: String? get() = headers["USN"]
        val mx: Int? get() = headers["MX"]?.toIntOrNull()
    }

    fun parse(raw: String): SsdpRequest? {
        val lines = raw.lines()
        if (lines.isEmpty()) return null
        val method = when {
            lines.first().startsWith("M-SEARCH") -> "M-SEARCH"
            lines.first().startsWith("NOTIFY") -> "NOTIFY"
            else -> return null
        }
        val headers = mutableMapOf<String, String>()
        for (line in lines.drop(1)) {
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().uppercase()] = line.substring(idx + 1).trim()
        }
        return SsdpRequest(method, headers)
    }
}
