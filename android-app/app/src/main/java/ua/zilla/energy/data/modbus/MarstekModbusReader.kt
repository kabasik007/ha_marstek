package ua.zilla.energy.data.modbus

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.zilla.energy.data.LocalAddress

/** Manual-only FC03 Modbus TCP probe for Venus E 3.0 on wired LAN, port 502. */
class MarstekModbusReader(private val context: Context) {
    private val transactions = AtomicInteger(0)

    suspend fun batteryBasics(host: String): BatteryBasics = withContext(Dispatchers.IO) {
        val values = read(host, 30100, 2)
        BatteryBasics(
            voltageV = values[0] * 0.01,
            currentA = ModbusTcpCodec.signed(values[1]) * 0.1
        )
    }

    /** User-requested only: no timer, no background battery-cell probes. */
    suspend fun cellsOnce(host: String): CellReading = withContext(Dispatchers.IO) {
        val cells = read(host, 34018, 16).map { it * 0.001 }
        // Validate ranges for LiFePO4; do not publish values for another model/
        // wrong register offset as if they were actual cell measurements.
        if (cells.any { it !in 2.0..4.0 }) {
            throw IOException("Unexpected cell voltages; verify model and register map")
        }
        CellReading(cells)
    }

    private fun read(host: String, address: Int, count: Int): List<Int> {
        require(LocalAddress.valid(host)) { "Only private LAN IPv4 addresses are allowed" }
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifi = cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: throw IOException("Phone Wi-Fi LAN is unavailable")
        val id = transactions.updateAndGet { if (it >= 65535) 1 else it + 1 }
        val request = ModbusTcpCodec.buildRead(id, address, count)

        Socket().use { socket ->
            wifi.bindSocket(socket)
            socket.connect(InetSocketAddress(host, 502), 2500)
            socket.soTimeout = 2500
            socket.getOutputStream().write(request)
            socket.getOutputStream().flush()
            val input = DataInputStream(socket.getInputStream())
            val header = ByteArray(7)
            input.readFully(header)
            val length = ((header[4].toInt() and 255) shl 8) or
                (header[5].toInt() and 255)
            if (length !in 3..253) throw IOException("Invalid response size")
            val rest = ByteArray(length - 1)
            input.readFully(rest)
            return ModbusTcpCodec.decodeResponse(header + rest, id, count)
        }
    }
}

data class BatteryBasics(val voltageV: Double, val currentA: Double)

data class CellReading(val voltagesV: List<Double>) {
    val minimumV: Double get() = voltagesV.minOrNull() ?: 0.0
    val maximumV: Double get() = voltagesV.maxOrNull() ?: 0.0
    val differenceMv: Double get() = (maximumV - minimumV) * 1000.0
}
