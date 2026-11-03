package ua.zilla.energy.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.zilla.energy.model.*
import ua.zilla.energy.ui.components.*
import ua.zilla.energy.ui.theme.Mint

@Composable
fun DiagnosticsScreen(state: EnergyUiState, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxSize().background(Mint.background)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("Diagnostics", "Local connectivity and incident history")
        if (state.settings.demoMode) {
            Text("Demo mode: no real LAN probes or incidents are recorded.", color = Mint.muted)
        }
        MintCard {
            DotLabel(connectionName(state.connection),
                if (state.connection == ConnectionStatus.ONLINE) Mint.primary else Mint.warning)
            HorizontalDivider(color = Mint.subtle)
            Row {
                Metric("UDP response", state.lastRoundTripMs?.let { it.toString() + " ms" } ?: "—",
                    Modifier.weight(1f))
                Metric("Missed probes", state.consecutiveMisses.toString(), Modifier.weight(1f))
            }
            Row {
                Metric("Polling every", state.settings.pollSeconds.toString() + " s", Modifier.weight(1f))
                Metric("Device IP", state.settings.host.ifBlank { "Not set" }, Modifier.weight(1f))
            }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = Color(0xFFFFF4E8)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Warning, null, tint = Mint.warning)
                Spacer(Modifier.width(10.dp))
                Text("Connectivity interruptions are not proof of a reboot. Compare timestamps with your router's Wi-Fi association logs and device LEDs.",
                    color = Mint.deep, style = MaterialTheme.typography.bodySmall)
            }
        }
        MintCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Event timeline", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(
                    enabled = state.events.isNotEmpty(),
                    onClick = {
                        val report = state.events.joinToString("\n") {
                            clockTime(it.timestampMs) + " | " + it.kind.name +
                                " | " + it.title + " | " + it.detail
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share diagnostic events"))
                    }
                ) { Icon(Icons.Default.Share, contentDescription = "Export events") }
                TextButton(onClick = onClear, enabled = state.events.isNotEmpty()) { Text("Clear") }
            }
            if (state.events.isEmpty()) {
                Text("No recorded events. Switch to Live mode in Settings to start monitoring.",
                    color = Mint.muted)
            } else {
                state.events.take(50).forEachIndexed { index, event ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            when (event.kind) {
                                EventKind.ONLINE, EventKind.RECOVERED -> Icons.Default.CheckCircle
                                EventKind.OFFLINE, EventKind.ERROR -> Icons.Default.ErrorOutline
                                else -> Icons.Default.Info
                            }, null,
                            tint = when (event.kind) {
                                EventKind.ONLINE, EventKind.RECOVERED -> Mint.primary
                                EventKind.OFFLINE, EventKind.ERROR -> Mint.danger
                                else -> Mint.warning
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(event.title, fontWeight = FontWeight.Medium)
                            Text(event.detail, color = Mint.muted, style = MaterialTheme.typography.bodySmall)
                            Text(clockTime(event.timestampMs),
                                color = Mint.muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (index < state.events.take(50).lastIndex) HorizontalDivider(color = Mint.subtle)
                }
            }
        }
    }
}
