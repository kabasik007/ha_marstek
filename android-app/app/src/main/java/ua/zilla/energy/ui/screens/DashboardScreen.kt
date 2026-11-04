package ua.zilla.energy.ui.screens

import android.content.Intent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.zilla.energy.model.ConnectionStatus
import ua.zilla.energy.model.EnergyUiState
import ua.zilla.energy.model.HistoryWindow
import ua.zilla.energy.ui.components.*
import ua.zilla.energy.ui.theme.Mint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun DashboardScreen(
    state: EnergyUiState,
    onSettings: () -> Unit,
    onWindow: (HistoryWindow) -> Unit,
    modifier: Modifier = Modifier
) {
    val snap = state.snapshot
    val context = LocalContext.current
    Column(
        modifier.fillMaxSize().background(Mint.background)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Zilla Energy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Home energy monitor", color = Mint.muted)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
        }

        if (state.settings.demoMode) {
            Surface(color = Mint.subtle, shape = MaterialTheme.shapes.medium) {
                Text("DEMO DATA — simulated values, not a connected Marstek",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = Mint.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
        MintCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, null, tint = Mint.primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Marstek energy storage", fontWeight = FontWeight.SemiBold)
                    Text(if (state.settings.host.isBlank()) "No device configured"
                         else state.settings.host + " • UDP " + state.settings.port,
                        style = MaterialTheme.typography.bodySmall, color = Mint.muted)
                }
                DotLabel(connectionName(state.connection),
                    if (state.connection == ConnectionStatus.ONLINE || state.connection == ConnectionStatus.DEMO)
                        Mint.primary else Mint.warning)
            }
        }

        MintCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BatteryChargingFull, null, tint = Mint.primary, modifier = Modifier.size(46.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Battery", color = Mint.muted)
                    Text(snap.socPercent?.let { it.toString() + "%" } ?: "—",
                        style = MaterialTheme.typography.displayMedium, color = Mint.deep,
                        fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Battery power", color = Mint.muted,
                        style = MaterialTheme.typography.labelSmall)
                    Text(watts(snap.batteryPowerW), color = Mint.primary,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            LinearProgressIndicator(
                progress = { (snap.socPercent ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth().height(9.dp),
                color = Mint.leaf,
                trackColor = Mint.subtle
            )
            Text("Power direction/sign depends on firmware; no automatic charge/discharge claim.",
                color = Mint.muted, style = MaterialTheme.typography.labelSmall)
            if (snap.batteryCapacityWh != null)
                Text("Reported total battery capacity: " +
                    String.format(java.util.Locale.US, "%.2f kWh", snap.batteryCapacityWh / 1000.0),
                    color = Mint.muted, style = MaterialTheme.typography.bodySmall)
        }

        if (!state.settings.demoMode && state.lastSuccessMs != null) {
            val stale = state.connection != ConnectionStatus.ONLINE
            Text(
                (if (stale) "LAST KNOWN READING • " else "Last read • ") +
                    clockTime(state.lastSuccessMs),
                color = if (stale) Mint.warning else Mint.muted,
                style = MaterialTheme.typography.labelMedium
            )
        }
        MintCard {
            SectionHeading("Energy flow", "Latest device readings")
            Row {
                Metric("Battery", watts(snap.batteryPowerW), Modifier.weight(1f), Mint.primary)
                Metric("Grid", watts(snap.gridPowerW), Modifier.weight(1f), Mint.blue)
            }
            HorizontalDivider(color = Mint.subtle)
            Row {
                Metric("Solar", watts(snap.solarPowerW), Modifier.weight(1f), Mint.leaf)
                Metric("Off-grid", watts(snap.offgridPowerW), Modifier.weight(1f))
            }
        }

        MintCard {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HistoryWindow.entries.forEach { window ->
                    FilterChip(
                        selected = state.historyWindow == window,
                        onClick = { onWindow(window) },
                        label = { Text(window.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            PowerChart(state.history, state.historyWindow)
            OutlinedButton(
                enabled = !state.settings.demoMode && state.history.isNotEmpty(),
                onClick = {
                    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    formatter.timeZone = TimeZone.getTimeZone("UTC")
                    val csv = buildString {
                        appendLine("timestamp_utc,battery_W,grid_W,solar_W")
                        state.history.forEach { point ->
                            fun value(v: Double?): String = v?.let {
                                String.format(Locale.US, "%.3f", it)
                            } ?: ""
                            appendLine(
                                formatter.format(Date(point.timestampMs)) + "," +
                                    value(point.batteryW) + "," + value(point.gridW) + "," +
                                    value(point.solarW)
                            )
                        }
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_TEXT, csv)
                        putExtra(Intent.EXTRA_SUBJECT, "Marstek chart " + state.historyWindow.label)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share chart CSV"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Share chart CSV")
            }
            Text(
                if (state.settings.demoMode) "Simulated demo history"
                else "Saved locally for up to 30 days. Logging requires the app to be running.",
                color = Mint.muted, style = MaterialTheme.typography.labelSmall
            )
            if (state.historyError != null)
                Text(state.historyError, color = Mint.danger,
                    style = MaterialTheme.typography.bodySmall)
        }

        if (state.connection == ConnectionStatus.OFFLINE ||
            state.connection == ConnectionStatus.DEGRADED) {
            MintCard {
                Text("Connection problem", color = Mint.danger, fontWeight = FontWeight.Bold)
                Text("Missed UDP probes: " + state.consecutiveMisses +
                    ". This does not confirm a device reboot.", color = Mint.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
