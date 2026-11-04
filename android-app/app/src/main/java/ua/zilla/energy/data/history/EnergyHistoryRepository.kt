package ua.zilla.energy.data.history

import android.content.Context
import ua.zilla.energy.model.DataSource
import ua.zilla.energy.model.EnergySnapshot
import ua.zilla.energy.model.PowerPoint
import ua.zilla.energy.model.HistoryWindow

/** Persistent, bounded and read-only historical telemetry repository. */
class EnergyHistoryRepository(context: Context) {
    private val dao = EnergyDatabase.get(context).dao()
    private var writesSincePrune = 0
    private val dayMs = 86_400_000L

    private fun endpoint(host: String, port: Int) = "$host:$port"

    suspend fun saveSample(host: String, port: Int, value: EnergySnapshot) {
        if (value.source != DataSource.LOCAL_API) return // never mix demo data
        dao.insertSample(
            TelemetryRow(
                endpoint = endpoint(host, port), timestampMs = value.timestampMs,
                soc = value.socPercent, batteryW = value.batteryPowerW,
                gridW = value.gridPowerW, offgridW = value.offgridPowerW,
                solarW = value.solarPowerW, capacityWh = value.batteryCapacityWh,
                gridImportWh = value.totalGridInputWh,
                gridExportWh = value.totalGridOutputWh,
                solarEnergyWh = value.totalPvEnergyRaw, loadWh = value.totalLoadWh
            )
        )
        pruneIfNeeded()
    }

    suspend fun saveProbe(host: String, port: Int, result: ProbeResult, latencyMs: Long?) {
        dao.insertProbe(
            ProbeRow(
                endpoint = endpoint(host, port),
                timestampMs = System.currentTimeMillis(),
                result = result.dbCode,
                latencyMs = latencyMs,
                reasonCode = null // never persist exception text or MAC/SSID
            )
        )
        pruneIfNeeded()
    }

    suspend fun chart(host: String, port: Int, window: HistoryWindow, nowMs: Long): List<PowerPoint> {
        val sinceMs = nowMs - window.durationMs
        return dao.buckets(endpoint(host, port), sinceMs, window.bucketMs)
            .map { PowerPoint(it.timestampMs, it.batteryW, it.gridW, it.solarW) }
    }

    suspend fun summary(host: String, port: Int, nowMs: Long): ProbeSummary =
        dao.probeSummary(endpoint(host, port), nowMs - dayMs)

    /** Retain 30 days of samples and 30 days of reachability probes. */
    private suspend fun pruneIfNeeded() {
        writesSincePrune++
        if (writesSincePrune < 120) return
        writesSincePrune = 0
        val cutoff = System.currentTimeMillis() - 30 * dayMs
        dao.pruneTelemetry(cutoff)
        dao.pruneProbes(cutoff)
    }
}

enum class ProbeResult(val dbCode: String) {
    OK("ok"), TIMEOUT("timeout"), PHONE_WIFI_UNAVAILABLE("phone_wifi")
}
