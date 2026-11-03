package ua.zilla.energy.model

enum class DataSource { DEMO, LOCAL_API }
enum class ConnectionStatus { DEMO, CONNECTING, ONLINE, DEGRADED, OFFLINE, DISABLED }
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
    val mode: EnergyMode = EnergyMode.UNKNOWN
)

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
    val cameras: List<CameraDevice> = emptyList()
)

data class EnergyUiState(
    val settings: AppSettings = AppSettings(),
    val snapshot: EnergySnapshot = EnergySnapshot(),
    val connection: ConnectionStatus = ConnectionStatus.DEMO,
    val events: List<EnergyEvent> = emptyList(),
    val history: List<PowerPoint> = emptyList(),
    val lastRoundTripMs: Long? = null,
    val consecutiveMisses: Int = 0,
    val error: String? = null
)
