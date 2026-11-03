package ua.zilla.energy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.zilla.energy.data.ConnectionTracker
import ua.zilla.energy.data.DemoEnergy
import ua.zilla.energy.data.LocalAddress
import ua.zilla.energy.data.MarstekUdpClient
import ua.zilla.energy.data.PreferencesStore
import ua.zilla.energy.model.AppSettings
import ua.zilla.energy.model.ConnectionStatus
import ua.zilla.energy.model.DataSource
import ua.zilla.energy.model.EnergyEvent
import ua.zilla.energy.model.EnergySnapshot
import ua.zilla.energy.model.EnergyUiState
import ua.zilla.energy.model.EventKind
import ua.zilla.energy.model.PowerPoint

class EnergyViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PreferencesStore(application)
    private val api = MarstekUdpClient(application)
    private val mutable = MutableStateFlow(EnergyUiState())
    val ui: StateFlow<EnergyUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            store.events.collect { events -> mutable.value = mutable.value.copy(events = events) }
        }
        viewModelScope.launch {
            store.settings.collect { settings ->
                mutable.value = mutable.value.copy(settings = settings)
            }
        }
        // Camera edits and event writes must not restart device polling.
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
                history = DemoEnergy.history(),
                error = null, consecutiveMisses = 0
            )
            while (kotlin.coroutines.coroutineContext.isActive) {
                delay(5_000)
                mutable.value = mutable.value.copy(
                    snapshot = DemoEnergy.snapshot(), history = DemoEnergy.history()
                )
            }
            return
        }

        if (!LocalAddress.valid(settings.host)) {
            mutable.value = mutable.value.copy(
                connection = ConnectionStatus.DISABLED,
                history = emptyList(), error = "Enter a private LAN IPv4 address in Settings"
            )
            return
        }

        val tracker = ConnectionTracker(offlineAfter = 3)
        mutable.value = mutable.value.copy(
            connection = ConnectionStatus.CONNECTING,
            snapshot = EnergySnapshot(source = DataSource.LOCAL_API),
            history = emptyList(),
            consecutiveMisses = 0,
            error = null
        )
        while (kotlin.coroutines.coroutineContext.isActive) {
            val start = System.currentTimeMillis()
            try {
                val snap = api.readStatus(settings.host, settings.port)
                val transition = tracker.observe(true, System.currentTimeMillis())
                if (transition?.status == ConnectionStatus.ONLINE) {
                    val recovered = transition.seconds
                    if (recovered != null) addEvent(
                        EventKind.RECOVERED, "LAN connection recovered",
                        "Estimated interruption: " + recovered + " s; not proof of a reboot"
                    ) else addEvent(EventKind.ONLINE, "Local API online", "Energy readings available")
                }
                val oldHistory = mutable.value.history
                val sample = PowerPoint(
                    snap.timestampMs, snap.batteryPowerW, snap.gridPowerW, snap.solarPowerW
                )
                mutable.value = mutable.value.copy(
                    snapshot = snap,
                    connection = tracker.status,
                    consecutiveMisses = 0,
                    error = null,
                    lastRoundTripMs = System.currentTimeMillis() - start,
                    history = (oldHistory + sample).takeLast(144)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val transition = tracker.observe(false, System.currentTimeMillis())
                if (transition?.status == ConnectionStatus.OFFLINE) {
                    addEvent(EventKind.OFFLINE, "Local API unavailable",
                        "Three consecutive missed replies. Check Wi-Fi/router logs.")
                } else if (transition?.status == ConnectionStatus.DEGRADED) {
                    addEvent(EventKind.TRANSIENT, "UDP reply missed",
                        "May be Wi-Fi, network routing or device API.")
                }
                mutable.value = mutable.value.copy(
                    connection = tracker.status,
                    consecutiveMisses = tracker.misses,
                    error = error.javaClass.simpleName,
                    lastRoundTripMs = null
                )
            }
            delay((settings.pollSeconds * 1000L - (System.currentTimeMillis() - start))
                .coerceAtLeast(1_000L))
        }
    }

    private suspend fun addEvent(kind: EventKind, title: String, detail: String) {
        store.addEvent(EnergyEvent(kind = kind, title = title, detail = detail))
    }

    fun saveEndpoint(ip: String, port: Int) = viewModelScope.launch {
        runCatching { store.saveEndpoint(ip.trim(), port) }
            .onFailure { mutable.value = mutable.value.copy(error = "Invalid private LAN address or port") }
    }

    fun setDemo(enabled: Boolean) = viewModelScope.launch { store.setDemo(enabled) }

    fun setPollSeconds(seconds: Int) = viewModelScope.launch {
        store.setPollSeconds(seconds)
    }

    fun addCamera(name: String, url: String) = viewModelScope.launch {
        runCatching { store.addCamera(name, url) }
            .onFailure {
                mutable.value = mutable.value.copy(
                    error = "Use a private-LAN rtsp:// address without credentials in the URL"
                )
            }
    }

    fun removeCamera(id: String) = viewModelScope.launch { store.removeCamera(id) }

    fun clearEvents() = viewModelScope.launch { store.clearEvents() }
}
