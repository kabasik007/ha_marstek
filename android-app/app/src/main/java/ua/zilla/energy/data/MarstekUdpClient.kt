package ua.zilla.energy.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.zilla.energy.model.EnergyMode
import ua.zilla.energy.model.EnergySnapshot
import ua.zilla.energy.model.WifiDiagnostics

/**
 * Read-only local Marstek protocol adapter (Local API Rev 3.1.2).
 *
 * Calls are serialized and never alter hardware settings. The source string
 * returned by firmware is pinned per host:port until app restart or endpoint
 * change, to detect a DHCP reassignment. It is never persisted/exported.
 */
class MarstekUdpClient(private val context: Context) {
    private val mutex = Mutex()
    private var requestId = 0
    private var endpoint: String? = null
    private var deviceSource: String? = null
    private var lastRequestAtMs = 0L

    suspend fun readStatus(host: String, port: Int): EnergySnapshot = mutex.withLock {
        val result = request(host, port, "ES.GetStatus")
        MarstekProtocol.parseEnergy(result)
    }

    data class AdvancedReading(
        val wifi: WifiDiagnostics?,
        val mode: EnergyMode,
        val wifiError: String? = null,
        val modeError: String? = null
    )

    /**
     * Explicitly opted-in diagnostics, intended no more often than 5 min.
     * Read failures are isolated; main energy polling remains independent.
     */
    suspend fun readAdvanced(host: String, port: Int): AdvancedReading = mutex.withLock {
        var wifi: WifiDiagnostics? = null
        var mode = EnergyMode.UNKNOWN
        var wifiError: String? = null
        var modeError: String? = null
        try {
            wifi = MarstekProtocol.parseWifi(request(host, port, "Wifi.GetStatus"))
        } catch (error: Exception) {
            if (error is MarstekDeviceChanged) throw error
            if (error is kotlinx.coroutines.CancellationException) throw error
            wifiError = error.javaClass.simpleName
        }
        try {
            mode = MarstekProtocol.parseMode(request(host, port, "ES.GetMode"))
        } catch (error: Exception) {
            if (error is MarstekDeviceChanged) throw error
            if (error is kotlinx.coroutines.CancellationException) throw error
            modeError = error.javaClass.simpleName
        }
        AdvancedReading(wifi, mode, wifiError, modeError)
    }

    private suspend fun request(
        host: String, port: Int, method: String
    ): JSONObject = withContext(Dispatchers.IO) {
        require(LocalAddress.valid(host)) { "Only private LAN IPv4 addresses are accepted" }
        require(port in 1..65535)
        val key = "$host:$port"
        if (endpoint != key) {
            endpoint = key
            deviceSource = null
        }

        // Keep a gap between read requests to reduce firmware load.
        val gap = 750L - (android.os.SystemClock.elapsedRealtime() - lastRequestAtMs)
        if (gap > 0) delay(gap)

        requestId = if (requestId >= Int.MAX_VALUE - 1) 1 else requestId + 1
        val id = requestId
        val message = JSONObject()
            .put("id", id)
            .put("method", method)
            .put("params", JSONObject().put("id", 0))
            .toString().toByteArray(Charsets.UTF_8)

        // Explicit Wi-Fi routing: mobile data must not take over when ISP is down.
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val wifi = manager.allNetworks.firstOrNull { network ->
            manager.getNetworkCapabilities(network)?.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) == true
        } ?: throw IllegalStateException("Wi-Fi LAN not available")

        val target = InetAddress.getByName(host)
        try {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                // Marstek firmware/client implementations sometimes require
                // local UDP port to equal the target port.
                socket.bind(InetSocketAddress("0.0.0.0", port))
                wifi.bindSocket(socket)
                socket.soTimeout = 2_500
                socket.send(DatagramPacket(message, message.size, target, port))

                val receive = ByteArray(8192)
                // Ignore unrelated data, wrong request IDs and other hosts.
                val deadline = android.os.SystemClock.elapsedRealtime() + 3_000L
                while (android.os.SystemClock.elapsedRealtime() < deadline) {
                    val packet = DatagramPacket(receive, receive.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        throw SocketTimeoutException("Marstek UDP response timeout")
                    }
                    if (packet.address != target || packet.port != port) continue
                    val reply = try {
                        MarstekProtocol.parseReply(
                            String(packet.data, packet.offset, packet.length, Charsets.UTF_8), id
                        )
                    } catch (_: IllegalArgumentException) {
                        continue
                    }
                    val actual = reply.source
                    if (actual != null && deviceSource != null && actual != deviceSource) {
                        throw MarstekDeviceChanged()
                    }
                    if (actual != null && deviceSource == null) deviceSource = actual
                    return@withContext reply.result
                }
                throw SocketTimeoutException("Marstek UDP response timeout")
            }
        } finally {
            lastRequestAtMs = android.os.SystemClock.elapsedRealtime()
        }
    }
}
