package ua.zilla.energy.data

import org.json.JSONObject
import ua.zilla.energy.model.RawApiStatus

/**
 * Diagnostic view of the SAME successful ES.GetStatus reply already used by
 * the dashboard. No extra UDP polling and no write requests.
 *
 * The upstream UDP envelope is intentionally discarded (it can include
 * a device MAC in 'src'). Unknown fields are listed by name but their values
 * are masked. This object is never written to Room/DataStore or app logs.
 */
object RawStatusSnapshot {
    private val expected = listOf(
        "bat_soc", "bat_cap", "bat_power", "pv_power",
        "ongrid_power", "offgrid_power",
        "total_pv_energy", "total_grid_input_energy",
        "total_grid_output_energy", "total_load_energy"
    )

    private val safeValues = expected.toSet() + "id"

    fun fromResult(result: JSONObject, timestampMs: Long = System.currentTimeMillis()): RawApiStatus {
        val missing = expected.filterNot { result.has(it) }
        val nullFields = expected.filter { result.has(it) && result.isNull(it) }
        val redacted = JSONObject()
        val keys = result.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            // JSONObject.NULL is an actual JSON null, not the string "null".
            if (key in safeValues) {
                val value = result.opt(key)
                redacted.put(
                    key,
                    if (value == JSONObject.NULL || value == null) JSONObject.NULL
                    else if (value is Number || value is Boolean) value
                    else "<redacted: unexpected type>"
                )
            } else {
                redacted.put(key, "<redacted: unknown field>")
            }
        }

        // Only this small, sanitized in-memory snapshot is exposed to the UI.
        return RawApiStatus(
            timestampMs = timestampMs,
            payload = redacted.toString(2),
            absentFields = missing,
            nullFields = nullFields,
            reportedFields = result.length()
        )
    }
}
