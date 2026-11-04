package ua.zilla.energy.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.zilla.energy.model.DiscoveredMarstek

/**
 * Official read-only Marstek.GetDevice LAN broadcast discovery.
 *
 * Search is bounded (4.5 seconds, two broadcast rounds). Only responses from
 * private IPv4 addresses and the configured UDP port are considered.
 * No full subnet scan, no credentials, no setting changes.
 */
class MarstekDiscovery(private val context: Context) {
    suspend fun search(port: Int = 30000): List<DiscoveredMarstek> =
        withContext(Dispatchers.IO) {
            require(port in 1..65535)
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val wifi = cm.allNetworks.firstOrNull { network ->
                cm.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } ?: throw PhoneWifiUnavailableException()

            val broadcasts = cm.getLinkProperties(wifi)?.linkAddresses
                ?.mapNotNull { link ->
                    val address = link.address as? Inet4Address ?: return@mapNotNull null
                    if (!LocalAddress.valid(address.hostAddress ?: "")) return@mapNotNull null
                    broadcastAddress(address, link.prefixLength)
                }
                ?.distinctBy { it.hostAddress }
                .orEmpty()

            if (broadcasts.isEmpty()) {
                throw IllegalStateException("Wi-Fi has no discoverable private IPv4 subnet")
            }

            val request = JSONObject()
                .put("id", 0)
                .put("method", "Marstek.GetDevice")
                .put("params", JSONObject().put("ble_mac", "0"))
                .toString().toByteArray(Charsets.UTF_8)

            val found = linkedMapOf<String, DiscoveredMarstek>()
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", port))
                wifi.bindSocket(socket)
                socket.broadcast = true
                socket.soTimeout = 300

                val start = SystemClock.elapsedRealtime()
                val deadline = start + 4_500L
                var nextBroadcast = start
                val data = ByteArray(8192)

                while (SystemClock.elapsedRealtime() < deadline) {
                    val now = SystemClock.elapsedRealtime()
                    if (now >= nextBroadcast) {
                        for (address in broadcasts) {
                            socket.send(DatagramPacket(request, request.size, address, port))
                        }
                        // Two rounds, at t=0 and t=2s.
                        nextBroadcast = deadline + 1L
                        if (now - start < 1_000L) nextBroadcast = start + 2_000L
                    }
                    val received = DatagramPacket(data, data.size)
                    try {
                        socket.receive(received)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val source = received.address.hostAddress ?: continue
                    if (received.port != port || !LocalAddress.valid(source)) continue

                    val candidate = try {
                        val raw = String(
                            received.data, received.offset, received.length, Charsets.UTF_8
                        )
                        val result = MarstekProtocol.parseReply(raw, 0).result
                        val device = result.optString("device").trim()
                        if (!device.startsWith("Venus", ignoreCase = true) &&
                            !device.startsWith("Marstek", ignoreCase = true)) null
                        else DiscoveredMarstek(
                            host = source,
                            port = port,
                            model = device.take(48),
                            firmware = result.optString("ver").take(24)
                        )
                    } catch (_: RuntimeException) {
                        null
                    }

                    if (candidate != null) found[source] = candidate
                }
            }
            found.values.toList()
        }

    companion object {
        /** Compute directed broadcast address for a private IPv4 Wi-Fi subnet. */
        fun broadcastAddress(ip: Inet4Address, prefixLength: Int): Inet4Address? {
            if (prefixLength !in 1..30) return null
            val bytes = ip.address
            var numeric = 0
            bytes.forEach { numeric = (numeric shl 8) or (it.toInt() and 255) }
            val mask = -1 shl (32 - prefixLength)
            val broadcast = (numeric and mask) or mask.inv()
            val target = byteArrayOf(
                (broadcast ushr 24).toByte(),
                (broadcast ushr 16).toByte(),
                (broadcast ushr 8).toByte(),
                broadcast.toByte()
            )
            return InetAddress.getByAddress(target) as Inet4Address
        }
    }
}
