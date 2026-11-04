package ua.zilla.energy.model

import kotlin.math.abs

/**
 * Venus E 3.0 / V150 ES.GetStatus.on grid power convention, confirmed against
 * the official app and community captures:
 *
 *   raw negative: grid -> battery (import/charging)
 *   raw positive: battery -> grid (export/discharging)
 *
 * Only the presentation magnitude is unsigned. Raw API values, Room samples,
 * charts, CSV exports and integrations must retain the signed value.
 */
enum class GridDirection { IMPORT, EXPORT, IDLE, UNKNOWN }

data class GridFlow(val direction: GridDirection, val magnitudeW: Double?) {
    companion object {
        fun fromRaw(raw: Double?): GridFlow = when {
            raw == null || !raw.isFinite() -> GridFlow(GridDirection.UNKNOWN, null)
            raw < 0.0 -> GridFlow(GridDirection.IMPORT, abs(raw))
            raw > 0.0 -> GridFlow(GridDirection.EXPORT, raw)
            else -> GridFlow(GridDirection.IDLE, 0.0)
        }
    }
}
