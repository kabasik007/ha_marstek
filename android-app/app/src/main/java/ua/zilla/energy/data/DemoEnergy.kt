package ua.zilla.energy.data

import kotlin.math.cos
import kotlin.math.sin
import ua.zilla.energy.model.DataSource
import ua.zilla.energy.model.EnergyMode
import ua.zilla.energy.model.EnergySnapshot
import ua.zilla.energy.model.PowerPoint

/** Clearly labelled synthetic values, never passed off as device telemetry. */
object DemoEnergy {
    fun snapshot(atMs: Long = System.currentTimeMillis()): EnergySnapshot {
        val t = atMs / 60_000.0
        return EnergySnapshot(
            timestampMs = atMs,
            source = DataSource.DEMO,
            socPercent = (75 + 3 * sin(t / 11)).toInt().coerceIn(0, 100),
            batteryPowerW = -900.0 + 230 * sin(t / 4),
            gridPowerW = 1250.0 + 140 * cos(t / 8),
            offgridPowerW = 0.0,
            solarPowerW = (2400.0 + 700 * sin(t / 9)).coerceAtLeast(0.0),
            batteryCapacityWh = 7600.0,
            mode = EnergyMode.AUTO
        )
    }

    fun history(now: Long = System.currentTimeMillis()): List<PowerPoint> =
        (48 downTo 0).map { i ->
            val timestamp = now - i * 60_000L
            val value = snapshot(timestamp)
            PowerPoint(timestamp, value.batteryPowerW, value.gridPowerW, value.solarPowerW)
        }
}
