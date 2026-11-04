package ua.zilla.energy.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ua.zilla.energy.model.DataSource
import ua.zilla.energy.model.EnergyMode

/** Exact field names from Marstek Device Local API Rev 3.1.2. */
class MarstekProtocolTest {
    @Test fun parsesEnergyStatusWithNullableFields() {
        val data = JSONObject("""
            {"id":0,"bat_soc":98,"bat_cap":5120,"pv_power":0,
             "ongrid_power":100,"offgrid_power":0,"bat_power":null,
             "total_pv_energy":5,"total_grid_output_energy":2548,
             "total_grid_input_energy":3273,"total_load_energy":0}
        """.trimIndent())
        val out = MarstekProtocol.parseEnergy(data, 1000)
        assertEquals(DataSource.LOCAL_API, out.source)
        assertEquals(98, out.socPercent)
        assertEquals(5120.0, out.batteryCapacityWh!!, 0.01)
        assertNull(out.batteryPowerW)
        assertEquals(100.0, out.gridPowerW!!, 0.01)
        assertEquals(3273.0, out.totalGridInputWh!!, 0.01)
        assertEquals(1000L, out.timestampMs)
    }

    @Test fun parsesWifiAndSanitizesUnavailableValues() {
        val wifi = MarstekProtocol.parseWifi(JSONObject("""
          {"ssid":"Marstek_2.4G","rssi":-59,"sta_ip":"192.168.0.61","wifi_mac":"private"}
        """.trimIndent()))
        assertEquals("Marstek_2.4G", wifi.ssid)
        assertEquals(-59, wifi.rssiDbm)
        assertEquals("192.168.0.61", wifi.deviceIp)
        assertNull(MarstekProtocol.parseWifi(
            JSONObject("""{"rssi": 999, "sta_ip":"8.8.8.8"}""")).rssiDbm)
        assertNull(MarstekProtocol.parseWifi(
            JSONObject("""{"rssi": 999, "sta_ip":"8.8.8.8"}""")).deviceIp)
    }

    @Test fun readsKnownModesWithoutGuessing() {
        assertEquals(EnergyMode.AUTO, MarstekProtocol.parseMode(JSONObject("""{"mode":"Auto"}""")))
        assertEquals(EnergyMode.UPS, MarstekProtocol.parseMode(JSONObject("""{"mode":"UPS"}""")))
        assertEquals(EnergyMode.UNKNOWN, MarstekProtocol.parseMode(JSONObject("""{"mode":null}""")))
    }

    @Test fun matchesUdpReplyIdsAndSurfacesErrors() {
        val raw = """{"id":7,"src":"VenusE-sample","result":{"bat_soc":88}}"""
        val ok = MarstekProtocol.parseReply(raw, 7)
        assertEquals("VenusE-sample", ok.source)
        assertEquals(88, ok.result.optInt("bat_soc"))
        assertThrows(IllegalArgumentException::class.java) {
            MarstekProtocol.parseReply(raw, 8)
        }
        assertThrows(IllegalStateException::class.java) {
            MarstekProtocol.parseReply(
                """{"id":7,"error":{"code":-32601,"message":"Method not found"}}""", 7
            )
        }
    }

    @Test fun rejectsMalformedAndMissingResult() {
        assertThrows(IllegalArgumentException::class.java) {
            MarstekProtocol.parseReply("""{"id":1}""", 1)
        }
        assertThrows(org.json.JSONException::class.java) {
            MarstekProtocol.parseReply("{not json", 1)
        }
    }
}
