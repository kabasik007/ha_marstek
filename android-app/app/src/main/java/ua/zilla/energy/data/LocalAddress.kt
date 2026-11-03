package ua.zilla.energy.data

/** Accept numeric RFC1918 IPv4 addresses only, never public internet hosts. */
object LocalAddress {
    fun valid(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val n = parts.map {
            if (it.isEmpty() || it.length > 3 || it.any { ch -> !ch.isDigit() }) return false
            val number = it.toIntOrNull() ?: return false
            if (number !in 0..255 || (it.length > 1 && it.startsWith('0'))) return false
            number
        }
        return n[0] == 10 ||
            n[0] == 192 && n[1] == 168 ||
            n[0] == 172 && n[1] in 16..31
    }

    fun validRtsp(raw: String): Boolean {
        val uri = try { java.net.URI(raw.trim()) } catch (_: Exception) { return false }
        if (!uri.scheme.equals("rtsp", true) || uri.userInfo != null) return false
        return uri.host?.let(::valid) == true && uri.port in -1..65535
    }
}
