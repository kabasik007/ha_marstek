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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.zilla.energy.R
import ua.zilla.energy.model.ConnectionStatus
import ua.zilla.energy.model.EnergyUiState
import ua.zilla.energy.model.HistoryWindow
import ua.zilla.energy.model.GridFlow
import ua.zilla.energy.model.GridDirection
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
    val gridFlow = GridFlow.fromRaw(snap.gridPowerW)
    val context = LocalContext.current
    val exportChooser = stringResource(R.string.chart_export_chooser)

    Column(
        modifier.fillMaxSize().background(Mint.background)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Zilla Energy", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings))
            }
        }
        if (state.settings.demoMode) {
            Surface(color = Mint.subtle, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.demo_badge),
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    color = Mint.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
        MintCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, null, tint = Mint.primary,
                    modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.marstek_device), fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.settings.host.isBlank()) stringResource(R.string.not_configured)
                        else state.settings.host + " · UDP " + state.settings.port,
                        style = MaterialTheme.typography.bodySmall, color = Mint.muted
                    )
                }
                DotLabel(connectionName(state.connection),
                    if (state.connection == ConnectionStatus.ONLINE ||
                        state.connection == ConnectionStatus.DEMO) Mint.primary else Mint.warning)
            }
        }

        MintCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BatteryChargingFull, null, tint = Mint.primary,
                    modifier = Modifier.size(42.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.battery), color = Mint.muted)
                    Text(snap.socPercent?.let { "$it%" } ?: "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = Mint.deep, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(stringResource(R.string.battery_power), color = Mint.muted,
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
            snap.batteryCapacityWh?.let { capacity ->
                val formatted = String.format(Locale.US, "%.2f", capacity / 1000.0)
                Text(stringResource(R.string.total_capacity, formatted),
                    style = MaterialTheme.typography.bodySmall, color = Mint.muted)
            }
        }

        if (!state.settings.demoMode && state.lastSuccessMs != null) {
            Text(
                stringResource(
                    if (state.connection == ConnectionStatus.ONLINE)
                        R.string.last_read else R.string.last_known,
                    clockTime(state.lastSuccessMs)
                ),
                color = if (state.connection == ConnectionStatus.ONLINE) Mint.muted else Mint.warning,
                style = MaterialTheme.typography.labelMedium
            )
        }

        MintCard {
            SectionHeading(stringResource(R.string.energy_flow))
            Row {
                Metric(stringResource(R.string.battery), watts(snap.batteryPowerW),
                    Modifier.weight(1f), Mint.primary)
                Metric(
                    label = stringResource(when (gridFlow.direction) {
                        GridDirection.IMPORT -> R.string.grid_import
                        GridDirection.EXPORT -> R.string.grid_export
                        GridDirection.IDLE -> R.string.grid_idle
                        GridDirection.UNKNOWN -> R.string.grid
                    }),
                    value = watts(gridFlow.magnitudeW),
                    modifier = Modifier.weight(1f),
                    color = Mint.blue
                )
            }
            HorizontalDivider(color = Mint.subtle)
            Row {
                Metric(stringResource(R.string.solar), watts(snap.solarPowerW),
                    Modifier.weight(1f), Mint.leaf)
                Metric(stringResource(R.string.offgrid), watts(snap.offgridPowerW),
                    Modifier.weight(1f))
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
                            fun value(v: Double?): String =
                                v?.let { String.format(Locale.US, "%.3f", it) } ?: ""
                            appendLine(formatter.format(Date(point.timestampMs)) + "," +
                                value(point.batteryW) + "," + value(point.gridW) + "," +
                                value(point.solarW))
                        }
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_TEXT, csv)
                    }
                    context.startActivity(Intent.createChooser(intent, exportChooser))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chart_export))
            }
            if (state.historyError != null)
                Text(stringResource(R.string.error_generic),
                    color = Mint.danger, style = MaterialTheme.typography.labelSmall)
        }

        if (state.connection == ConnectionStatus.OFFLINE ||
            state.connection == ConnectionStatus.DEGRADED) {
            MintCard {
                Text(stringResource(R.string.connection_problem), color = Mint.danger,
                    fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.missed_probes, state.consecutiveMisses),
                    color = Mint.muted)
            }
        }
    }
}
