package ua.zilla.energy.data

import org.json.JSONObject
import ua.zilla.energy.model.DataSource
import ua.zilla.energy.model.EnergyMode
import ua.zilla.energy.model.EnergySnapshot
import ua.zilla.energy.model.WifiDiagnostics

/**
 * Marstek Device Local API: parsing separated from transport so
 * firmware variations can be tested without a physical inverter.
 */
object MarstekProtocol {
    data class Reply(val result: JSONObject, val source: String?)

    fun parseReply(raw: String, expectedId: Int): Reply {
        val reply = JSONObject(raw)
        require(reply.optInt("id", Int.MIN_VALUE) == expectedId) {
            "UDP reply request identifier does not match"
        }
        if (reply.has("error") && !reply.isNull("error")) {
            val error = reply.optJSONObject("error")
            val code = error?.optInt("code")
            throw IllegalStateException("Marstek API error" + (code?.let { " ($it)" } ?: ""))
        }
        val result = reply.optJSONObject("result")
            ?: throw IllegalArgumentException("Response has no result object")
        val source = if (reply.isNull("src")) null
                     else reply.optString("src").trim().takeIf { it.isNotEmpty() }
        return Reply(result, source)
    }

    fun parseEnergy(result: JSONObject, timestampMs: Long = System.currentTimeMillis()) =
        EnergySnapshot(
            timestampMs = timestampMs,
            source = DataSource.LOCAL_API,
            socPercent = result.number("bat_soc")?.toInt()?.takeIf { it in 0..100 },
            batteryPowerW = result.number("bat_power"),
            gridPowerW = result.number("ongrid_power"),
            offgridPowerW = result.number("offgrid_power"),
            solarPowerW = result.number("pv_power"),
            // 'bat_cap' is total system capacity, not remaining charge.
            batteryCapacityWh = result.number("bat_cap"),
            totalPvEnergyRaw = result.number("total_pv_energy"),
            totalGridInputWh = result.number("total_grid_input_energy"),
            totalGridOutputWh = result.number("total_grid_output_energy"),
            totalLoadWh = result.number("total_load_energy")
        )

    fun parseWifi(result: JSONObject, timestampMs: Long = System.currentTimeMillis()) =
        WifiDiagnostics(
            recordedAtMs = timestampMs,
            ssid = result.text("ssid"),
            rssiDbm = result.number("rssi")?.toInt()?.takeIf { it in -120..0 },
            deviceIp = result.text("sta_ip")?.takeIf(LocalAddress::valid)
        )

    fun parseMode(result: JSONObject): EnergyMode =
        when (result.text("mode")?.lowercase()) {
            "auto" -> EnergyMode.AUTO
            "ai" -> EnergyMode.AI
            "manual" -> EnergyMode.MANUAL
            "passive" -> EnergyMode.PASSIVE
            "ups" -> EnergyMode.UPS
            else -> EnergyMode.UNKNOWN
        }

    private fun JSONObject.number(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return opt(key)?.toString()?.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun JSONObject.text(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() }
    }
}

class MarstekDeviceChanged : IllegalStateException(
    "Different Marstek device responded at configured IP; check DHCP reservation"
)
