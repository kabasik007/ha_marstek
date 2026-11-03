package ua.zilla.energy.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.zilla.energy.model.DataSource
import ua.zilla.energy.model.EnergySnapshot

/**
 * Read-only Marstek UDP local API client.
 *
 * Firmware implementations may reply only if source and destination ports match.
 * Socket is therefore bound to the configured *device* port. Do not run a
 * competing listener on the Android device. All operations are serialized.
 *
 * Only ES.GetStatus is used. No Bat.GetStatus, mode changes or resets:
 * some firmware variants have been reported unstable under polling.
 */
class MarstekUdpClient(private val context: Context) {
    private val mutex = Mutex()
    private var requestId: Int = 0

    suspend fun readStatus(host: String, port: Int): EnergySnapshot =
        mutex.withLock {
            require(LocalAddress.valid(host)) { "Enter a private LAN IPv4 address" }
            require(port in 1..65535)
            val result = request(host, port, "ES.GetStatus", JSONObject().put("id", 0))
            EnergySnapshot(
                timestampMs = System.currentTimeMillis(),
                source = DataSource.LOCAL_API,
                socPercent = result.numeric("bat_soc")?.toInt()?.coerceIn(0, 100),
                batteryPowerW = result.numeric("bat_power"),
                gridPowerW = result.numeric("ongrid_power"),
                offgridPowerW = result.numeric("offgrid_power"),
                solarPowerW = result.numeric("pv_power"),
                batteryCapacityWh = result.numeric("bat_cap")
            )
        }

    private suspend fun request(
        host: String,
        port: Int,
        method: String,
        params: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        requestId = if (requestId >= Int.MAX_VALUE - 1) 1 else requestId + 1
        val id = requestId
        val payload = JSONObject()
            .put("id", id).put("method", method).put("params", params)
            .toString().toByteArray(Charsets.UTF_8)

        // Wi-Fi must be used even when the router has no internet, and
        // Android's default network is mobile data.
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifi = cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) == true
        } ?: throw IllegalStateException("Wi-Fi network unavailable")

        val target = InetAddress.getByName(host)
        val receive = ByteArray(8192)
        DatagramSocket(null).use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("0.0.0.0", port))
            wifi.bindSocket(socket)
            socket.soTimeout = 2500

            socket.send(DatagramPacket(payload, payload.size, target, port))
            val startedAt = System.nanoTime()
            while (System.nanoTime() - startedAt < 3_500_000_000L) {
                val packet = DatagramPacket(receive, receive.size)
                socket.receive(packet)
                if (packet.address != target) continue // no spoofed source
                val response = try {
                    JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
                } catch (_: Exception) { continue }
                if (response.optInt("id", -1) != id) continue
                if (response.has("error")) throw IllegalStateException("Marstek API returned an error")
                val body = response.optJSONObject("result")
                    ?: throw IllegalStateException("Missing API result")
                return@withContext body
            }
            error("Marstek reply timeout")
        }
    }
}

private fun JSONObject.numeric(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return opt(key)?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() }
}
