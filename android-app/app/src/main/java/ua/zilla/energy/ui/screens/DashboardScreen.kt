package ua.zilla.energy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.zilla.energy.model.ConnectionStatus
import ua.zilla.energy.model.DataSource
import ua.zilla.energy.model.EnergyUiState
import ua.zilla.energy.ui.components.*
import ua.zilla.energy.ui.theme.Mint

@Composable
fun DashboardScreen(state: EnergyUiState, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val snap = state.snapshot
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
                Text(if (snap.batteryPowerW == null) "Unknown"
                     else if (snap.batteryPowerW > 0) "Charging*" else if (snap.batteryPowerW < 0) "Discharging*" else "Idle",
                    color = Mint.muted, style = MaterialTheme.typography.bodySmall)
            }
            LinearProgressIndicator(
                progress = { (snap.socPercent ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth().height(9.dp),
                color = Mint.leaf,
                trackColor = Mint.subtle
            )
            Text("* Battery power sign conventions depend on model and firmware; verify on hardware.",
                color = Mint.muted, style = MaterialTheme.typography.labelSmall)
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
            PowerChart(state.history)
            Text("Actual readings are collected only while this app is open.",
                color = Mint.muted, style = MaterialTheme.typography.labelSmall)
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
