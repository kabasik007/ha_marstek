package ua.zilla.energy.model

enum class DataSource { DEMO, LOCAL_API }
enum class ConnectionStatus { DEMO, CONNECTING, ONLINE, DEGRADED, OFFLINE, PHONE_WIFI_UNAVAILABLE, DISABLED }
enum class EnergyMode { AUTO, AI, MANUAL, PASSIVE, UPS, UNKNOWN }

data class EnergySnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val source: DataSource = DataSource.DEMO,
    val socPercent: Int? = null,
    val batteryPowerW: Double? = null,
    val gridPowerW: Double? = null,
    val offgridPowerW: Double? = null,
    val solarPowerW: Double? = null,
    val batteryCapacityWh: Double? = null,
    val totalPvEnergyRaw: Double? = null,
    val totalGridInputWh: Double? = null,
    val totalGridOutputWh: Double? = null,
    val totalLoadWh: Double? = null,
    val mode: EnergyMode = EnergyMode.UNKNOWN
)

/** Read-only Wifi.GetStatus; no MAC, gateway or DNS are stored. */
/** Discovery result deliberately excludes BLE/Wi-Fi MAC identifiers. */
data class DiscoveredMarstek(
    val host: String,
    val port: Int,
    val model: String,
    val firmware: String
)

data class WifiDiagnostics(
    val recordedAtMs: Long = System.currentTimeMillis(),
    val ssid: String? = null,
    val rssiDbm: Int? = null,
    val deviceIp: String? = null
)

enum class HistoryWindow(val label: String, val durationMs: Long, val bucketMs: Long) {
    HOUR("1H", 3_600_000L, 60_000L),
    DAY("24H", 86_400_000L, 300_000L),
    WEEK("7D", 7L * 86_400_000, 1_800_000L),
    MONTH("30D", 30L * 86_400_000, 7_200_000L)
}

/** Sample-based API reachability, not device uptime or reboot count. */
data class ProbeStats(
    val attempts: Long = 0,
    val successes: Long = 0,
    val timeouts: Long = 0,
    val apiErrors: Long = 0,
    val phoneWifiMissing: Long = 0,
    val firstSampleMs: Long? = null,
    val lastSampleMs: Long? = null
) {
    val reachabilityPercent: Int?
        get() {
            val usable = attempts - phoneWifiMissing
            return if (usable > 0) ((successes * 100) / usable).toInt().coerceIn(0, 100)
            else null
        }
}

data class PowerPoint(
    val timestampMs: Long,
    val batteryW: Double?,
    val gridW: Double?,
    val solarW: Double?
)

enum class EventKind { ONLINE, OFFLINE, RECOVERED, TRANSIENT, ERROR, INFO }

data class EnergyEvent(
    val timestampMs: Long = System.currentTimeMillis(),
    val kind: EventKind,
    val title: String,
    val detail: String
)

data class CameraDevice(
    val id: String,
    val name: String,
    val rtspUrl: String
)

data class AppSettings(
    val demoMode: Boolean = true,
    val host: String = "",
    val port: Int = 30000,
    val pollSeconds: Int = 30,
    val advancedDiagnostics: Boolean = false,
    val cameras: List<CameraDevice> = emptyList()
)

data class EnergyUiState(
    val settings: AppSettings = AppSettings(),
    val snapshot: EnergySnapshot = EnergySnapshot(),
    val connection: ConnectionStatus = ConnectionStatus.DEMO,
    val events: List<EnergyEvent> = emptyList(),
    val history: List<PowerPoint> = emptyList(),
    val historyWindow: HistoryWindow = HistoryWindow.DAY,
    val probeStats: ProbeStats = ProbeStats(),
    val historyError: String? = null,
    val isDiscovering: Boolean = false,
    val discoveryAttempted: Boolean = false,
    val discoveredDevices: List<DiscoveredMarstek> = emptyList(),
    val discoveryError: String? = null,
    val lastRoundTripMs: Long? = null,
    val lastSuccessMs: Long? = null,
    val wifi: WifiDiagnostics? = null,
    val phoneWifiAvailable: Boolean? = null,
    val phoneInternetValidated: Boolean? = null,
    val advancedLastCheckedMs: Long? = null,
    val consecutiveMisses: Int = 0,
    val error: String? = null
)
