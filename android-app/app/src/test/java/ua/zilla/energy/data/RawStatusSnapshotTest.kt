package ua.zilla.energy.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RawStatusSnapshotTest {
    @Test fun missingAndNullBatteryPowerHaveDifferentDiagnostics() {
        val missing = RawStatusSnapshot.fromResult(JSONObject(
            """{"id":0,"bat_soc":76,"bat_cap":5120,"ongrid_power":0,"pv_power":0,"offgrid_power":0}"""
        ), 1234L)
        assertTrue(missing.absentFields.contains("bat_power"))
        assertFalse(missing.nullFields.contains("bat_power"))
        val parsed = JSONObject(missing.payload)
        assertFalse(parsed.has("bat_power"))
        assertEquals(76, parsed.optInt("bat_soc"))
        assertEquals(5120, parsed.optInt("bat_cap"))
        assertEquals(1234L, missing.timestampMs)

        val nullReading = RawStatusSnapshot.fromResult(JSONObject(
            """{"bat_soc":76,"bat_power":null}"""
        ))
        assertFalse(nullReading.absentFields.contains("bat_power"))
        assertTrue(nullReading.nullFields.contains("bat_power"))
        assertTrue(JSONObject(nullReading.payload).isNull("bat_power"))
    }

    @Test fun redactsUnknownFieldsAndIdentifiers() {
        val raw = JSONObject(
            """{"id":0,"bat_soc":76,"wifi_mac":"secret-device-mac","serial":"PRIVATE123","firmware":2026,"nested":{"token":"secret"}}"""
        )
        val observed = RawStatusSnapshot.fromResult(raw)
        val output = observed.payload
        assertFalse(output.contains("secret-device-mac"))
        assertFalse(output.contains("PRIVATE123"))
        assertFalse(output.contains("secret"))
        assertFalse(output.contains("2026"))
        val json = JSONObject(output)
        assertEquals(76, json.optInt("bat_soc"))
        assertEquals("<redacted: unknown field>", json.optString("wifi_mac"))
        assertEquals("<redacted: unknown field>", json.optString("nested"))
        assertEquals(6, observed.reportedFields)
    }

    @Test fun unexpectedTypeIsNeverCopiedVerbatim() {
        val raw = JSONObject("""{"bat_soc":"secret-as-soc","bat_cap":5120}""")
        val observed = RawStatusSnapshot.fromResult(raw)
        assertFalse(observed.payload.contains("secret-as-soc"))
        assertEquals("<redacted: unexpected type>",
            JSONObject(observed.payload).getString("bat_soc"))
    }
}
