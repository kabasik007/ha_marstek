package ua.zilla.energy

import android.app.Application
import java.net.SocketTimeoutException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.zilla.energy.data.ConnectionTracker
import ua.zilla.energy.data.DemoEnergy
import ua.zilla.energy.data.LocalAddress
import ua.zilla.energy.data.MarstekUdpClient
import ua.zilla.energy.data.MarstekDeviceChanged
import ua.zilla.energy.data.PhoneWifiUnavailableException
import ua.zilla.energy.data.PhoneNetworkInspector
import ua.zilla.energy.data.PreferencesStore
import ua.zilla.energy.data.history.EnergyHistoryRepository
import ua.zilla.energy.data.history.ProbeResult
import ua.zilla.energy.model.AppSettings
import ua.zilla.energy.model.ConnectionStatus
import ua.zilla.energy.model.DataSource
import ua.zilla.energy.model.EnergyEvent
import ua.zilla.energy.model.EnergySnapshot
import ua.zilla.energy.model.DiscoveredMarstek
import ua.zilla.energy.model.EnergyUiState
import ua.zilla.energy.model.EventKind
import ua.zilla.energy.model.HistoryWindow
import ua.zilla.energy.model.PowerPoint
import ua.zilla.energy.model.EnergyMode
import ua.zilla.energy.model.ProbeStats

/**
 * Read-only Marstek session, lifetime bound to the open Android application.
 * Local database errors never count as a Marstek UDP connection failure.
 */
class EnergyViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PreferencesStore(application)
    private val api = MarstekUdpClient(application)
    private val archive = EnergyHistoryRepository(application)
    private val mutable = MutableStateFlow(EnergyUiState())
    val ui: StateFlow<EnergyUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            store.events.collect { events ->
                mutable.value = mutable.value.copy(events = events)
            }
        }
        viewModelScope.launch {
            store.settings.collect { settings ->
                mutable.value = mutable.value.copy(settings = settings)
            }
        }
        // Event writes, camera entries and chart-window changes do not restart
        // the device polling job.
        viewModelScope.launch {
            store.settings.map { it.copy(cameras = emptyList()) }
                .distinctUntilChanged()
                .collectLatest { runFor(it) }
        }
    }

    private suspend fun runFor(settings: AppSettings) {
        if (settings.demoMode) {
            mutable.value = mutable.value.copy(
                connection = ConnectionStatus.DEMO,
                snapshot = DemoEnergy.snapshot(),
                rawApiStatus = null,
                history = demoChart(mutable.value.historyWindow),
                probeStats = ProbeStats(),
                historyError = null,
                error = null,
                consecutiveMisses = 0,
                wifi = null,
                advancedLastCheckedMs = null,
                lastSuccessMs = null,
                lastRoundTripMs = null,
                phoneWifiAvailable = null,
                phoneInternetValidated = null
            )
            while (kotlin.coroutines.coroutineContext.isActive) {
                delay(5_000)
                mutable.value = mutable.value.copy(
                    snapshot = DemoEnergy.snapshot(),
                rawApiStatus = null,
                    history = demoChart(mutable.value.historyWindow)
                )
            }
            return
        }

        if (!LocalAddress.valid(settings.host)) {
            mutable.value = mutable.value.copy(
                connection = ConnectionStatus.DISABLED,
                snapshot = EnergySnapshot(source = DataSource.LOCAL_API),
                rawApiStatus = null,
                history = emptyList(),
                probeStats = ProbeStats(),
                wifi = null, lastSuccessMs = null, lastRoundTripMs = null,
                phoneWifiAvailable = null, phoneInternetValidated = null,
                error = "Enter a private LAN IPv4 address in Settings"
            )
            return
        }

        var tracker = ConnectionTracker(offlineAfter = 3)
        var lastAdvancedAtMs = 0L
        mutable.value = mutable.value.copy(
            connection = ConnectionStatus.CONNECTING,
            snapshot = EnergySnapshot(source = DataSource.LOCAL_API),
            history = emptyList(),
            consecutiveMisses = 0,
            wifi = null,
            lastSuccessMs = null,
            advancedLastCheckedMs = null,
            lastRoundTripMs = null,
            phoneWifiAvailable = null, phoneInternetValidated = null,
            error = null
        )
        refreshArchive(settings)

        while (kotlin.coroutines.coroutineContext.isActive) {
            val lan = PhoneNetworkInspector.inspect(getApplication())
            val previousValidation = mutable.value.phoneInternetValidated
            mutable.value = mutable.value.copy(
                phoneWifiAvailable = lan.hasWifiLan,
                phoneInternetValidated = lan.internetValidated
            )
            if (lan.hasWifiLan && previousValidation != null &&
                lan.internetValidated != null &&
                previousValidation != lan.internetValidated) {
                addEvent(EventKind.INFO, "Android internet validation changed",
                    "Android changed its Wi-Fi validation state; local Marstek API may still work")
            }
            val startMs = System.currentTimeMillis()
            try {
                val reading = api.readStatus(settings.host, settings.port)
                val snap = reading.snapshot
                val nowMs = System.currentTimeMillis()
                val wasPhoneOffline =
                    mutable.value.connection == ConnectionStatus.PHONE_WIFI_UNAVAILABLE
                val transition = tracker.observe(true, nowMs)
                val elapsed = (nowMs - startMs).coerceAtLeast(0L)

                mutable.value = mutable.value.copy(
                    snapshot = snap,
                    rawApiStatus = reading.rawStatus,
                    connection = tracker.status,
                    consecutiveMisses = 0,
                    error = null,
                    lastRoundTripMs = elapsed,
                    lastSuccessMs = snap.timestampMs
                )

                if (wasPhoneOffline) {
                    addEvent(EventKind.RECOVERED, "Phone LAN connection restored",
                        "Marstek API can be queried again")
                } else if (transition?.status == ConnectionStatus.ONLINE) {
                    if (transition.seconds != null) {
                        addEvent(EventKind.RECOVERED, "Marstek Local API recovered",
                            "Estimated interruption ${transition.seconds}s; not proof of device reboot")
                    } else {
                        addEvent(EventKind.ONLINE, "Marstek Local API online",
                            "Device returned ES.GetStatus")
                    }
                }
                safelyArchive(settings, snap, ProbeResult.OK, elapsed)

                // Opt-in, read-only diagnostics. A failure is not a failure of
                // the main ES.GetStatus query.
                if (settings.advancedDiagnostics &&
                    (lastAdvancedAtMs == 0L || nowMs - lastAdvancedAtMs >= 300_000L)) {
                    lastAdvancedAtMs = nowMs
                    val advanced = api.readAdvanced(settings.host, settings.port)
                    val current = mutable.value
                    mutable.value = current.copy(
                        wifi = advanced.wifi ?: current.wifi,
                        advancedLastCheckedMs = nowMs,
                        snapshot = if (advanced.mode != EnergyMode.UNKNOWN)
                            current.snapshot.copy(mode = advanced.mode)
                        else current.snapshot
                    )
                    if (advanced.wifiError != null || advanced.modeError != null) {
                        addEvent(EventKind.INFO, "Advanced API readings incomplete",
                            "Wi-Fi: ${advanced.wifiError ?: "ok"}; mode: ${advanced.modeError ?: "ok"}")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (identity: MarstekDeviceChanged) {
                safelyArchive(settings, null, ProbeResult.API_ERROR, null)
                addEvent(EventKind.ERROR, "Marstek source changed",
                    "Another device replied at this IP. Check DHCP reservations.")
                mutable.value = mutable.value.copy(
                    connection = ConnectionStatus.DISABLED,
                    error = "Device source changed — verify IP before continuing"
                )
                return
            } catch (phone: PhoneWifiUnavailableException) {
                val firstLoss = mutable.value.connection != ConnectionStatus.PHONE_WIFI_UNAVAILABLE
                // Do not label the inverter offline when Android lost Wi-Fi.
                tracker = ConnectionTracker(offlineAfter = 3)
                mutable.value = mutable.value.copy(
                    connection = ConnectionStatus.PHONE_WIFI_UNAVAILABLE,
                    consecutiveMisses = 0,
                    error = "Android is not connected to a Wi-Fi LAN",
                    lastRoundTripMs = null
                )
                if (firstLoss) {
                    addEvent(EventKind.INFO, "Phone Wi-Fi unavailable",
                        "Device state unknown until Android returns to the LAN")
                }
                safelyArchive(settings, null, ProbeResult.PHONE_WIFI_UNAVAILABLE, null)
            } catch (error: Exception) {
                val transition = tracker.observe(false, System.currentTimeMillis())
                if (transition?.status == ConnectionStatus.OFFLINE) {
                    addEvent(EventKind.OFFLINE, "Marstek Local API not responding",
                        "Three missed queries. Compare with router Wi-Fi logs.")
                } else if (transition?.status == ConnectionStatus.DEGRADED) {
                    addEvent(EventKind.TRANSIENT, "Marstek API reply missed",
                        "An unanswered UDP request does not prove a reboot")
                }
                mutable.value = mutable.value.copy(
                    connection = tracker.status,
                    consecutiveMisses = tracker.misses,
                    error = error.javaClass.simpleName,
                    lastRoundTripMs = null
                )
                val type = if (error is SocketTimeoutException)
                    ProbeResult.TIMEOUT else ProbeResult.API_ERROR
                safelyArchive(settings, null, type, null)
            }
            delay((settings.pollSeconds * 1000L -
                (System.currentTimeMillis() - startMs)).coerceAtLeast(1_000L))
        }
    }

    private suspend fun safelyArchive(
        settings: AppSettings,
        sample: EnergySnapshot?,
        probe: ProbeResult,
        elapsed: Long?
    ) {
        try {
            if (sample != null) archive.saveSample(settings.host, settings.port, sample)
            archive.saveProbe(settings.host, settings.port, probe, elapsed)
            refreshArchive(settings)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutable.value = mutable.value.copy(
                historyError = "History storage: ${error.javaClass.simpleName}"
            )
        }
    }

    private suspend fun refreshArchive(settings: AppSettings) {
        try {
            val requestedWindow = mutable.value.historyWindow
            val now = System.currentTimeMillis()
            val points = archive.chart(settings.host, settings.port, requestedWindow, now)
            val summary = archive.summary(settings.host, settings.port, now)
            if (mutable.value.settings.host == settings.host &&
                mutable.value.settings.port == settings.port &&
                mutable.value.historyWindow == requestedWindow &&
                !mutable.value.settings.demoMode) {
                mutable.value = mutable.value.copy(
                    history = points,
                    probeStats = ProbeStats(
                        attempts = summary.attempts, successes = summary.successes,
                        timeouts = summary.timedOut, apiErrors = summary.apiErrors,
                        phoneWifiMissing = summary.phoneWifiMissing,
                        firstSampleMs = summary.firstProbeMs,
                        lastSampleMs = summary.lastProbeMs
                    ),
                    historyError = null
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutable.value = mutable.value.copy(
                historyError = "History query: ${error.javaClass.simpleName}"
            )
        }
    }

    private fun demoChart(window: HistoryWindow): List<PowerPoint> {
        val now = System.currentTimeMillis()
        return (48 downTo 0).map { i ->
            val ts = now - i * window.durationMs / 48L
            val d = DemoEnergy.snapshot(ts)
            PowerPoint(ts, d.batteryPowerW, d.gridPowerW, d.solarPowerW)
        }
    }

    fun selectHistoryWindow(window: HistoryWindow) {
        mutable.value = mutable.value.copy(historyWindow = window)
        if (mutable.value.settings.demoMode) {
            mutable.value = mutable.value.copy(history = demoChart(window))
        } else {
            viewModelScope.launch { refreshArchive(mutable.value.settings) }
        }
    }

    private suspend fun addEvent(kind: EventKind, title: String, detail: String) {
        store.addEvent(EnergyEvent(kind = kind, title = title, detail = detail))
    }

    fun saveEndpoint(ip: String, port: Int) = viewModelScope.launch {
        try { store.saveEndpoint(ip.trim(), port) }
        catch (_: IllegalArgumentException) {
            mutable.value = mutable.value.copy(error = "Invalid private LAN IP/port")
        }
    }

    /**
     * One short, user-triggered (or first-setup) LAN broadcast scan.
     * No device settings are changed by discovery.
     */
    fun discoverMarstek() {
        if (mutable.value.isDiscovering) return
        viewModelScope.launch {
            val port = mutable.value.settings.port
            mutable.value = mutable.value.copy(
                isDiscovering = true,
                discoveryAttempted = true,
                discoveredDevices = emptyList(),
                discoveryError = null
            )
            try {
                val devices = api.discover(port)
                mutable.value = mutable.value.copy(
                    discoveredDevices = devices,
                    discoveryError = if (devices.isEmpty()) {
                        "No Marstek found. Enable Local API and verify the same Wi-Fi network."
                    } else null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: PhoneWifiUnavailableException) {
                mutable.value = mutable.value.copy(
                    discoveryError = "Connect this phone to the Marstek's Wi-Fi LAN first."
                )
            } catch (error: Exception) {
                mutable.value = mutable.value.copy(
                    discoveryError = "Scan failed: " + error.javaClass.simpleName +
                        ". You can still enter the IP manually."
                )
            } finally {
                mutable.value = mutable.value.copy(isDiscovering = false)
            }
        }
    }

    /** Selection is explicit: do not silently change the connected inverter. */
    fun selectDiscoveredMarstek(device: DiscoveredMarstek) = viewModelScope.launch {
        if (!LocalAddress.valid(device.host) || device.port !in 1..65535) {
            mutable.value = mutable.value.copy(discoveryError = "Invalid discovered IP or UDP port")
            return@launch
        }
        store.saveEndpoint(device.host, device.port)
        store.setDemo(false)
        mutable.value = mutable.value.copy(discoveryError = null)
    }

    fun setDemo(enabled: Boolean) = viewModelScope.launch { store.setDemo(enabled) }

    fun setAdvancedDiagnostics(enabled: Boolean) = viewModelScope.launch {
        store.setAdvancedDiagnostics(enabled)
    }

    fun setPollSeconds(seconds: Int) = viewModelScope.launch {
        store.setPollSeconds(seconds)
    }

    // Camera prototype remains in source but is parked in navigation.
    fun addCamera(name: String, url: String) = viewModelScope.launch {
        runCatching { store.addCamera(name, url) }.onFailure {
            mutable.value = mutable.value.copy(error = "Invalid private RTSP URL")
        }
    }

    fun removeCamera(id: String) = viewModelScope.launch { store.removeCamera(id) }

    fun clearEvents() = viewModelScope.launch { store.clearEvents() }
}
