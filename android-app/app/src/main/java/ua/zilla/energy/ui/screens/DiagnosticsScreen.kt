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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.zilla.energy.R
import ua.zilla.energy.model.*
import ua.zilla.energy.ui.components.*
import ua.zilla.energy.ui.theme.Mint

private fun eventLabel(title: String, kind: EventKind): Int = when (title) {
    "Marstek Local API online" -> R.string.event_online
    "Marstek Local API recovered" -> R.string.event_recovered
    "Marstek Local API not responding" -> R.string.event_offline
    "Marstek API reply missed" -> R.string.event_transient
    "Phone Wi-Fi unavailable" -> R.string.event_phone_wifi
    "Phone LAN connection restored" -> R.string.event_lan_recovered
    "Marstek source changed" -> R.string.event_source_change
    "Advanced API readings incomplete" -> R.string.event_advanced
    "Android internet validation changed" -> R.string.event_internet
    else -> when (kind) {
        EventKind.ONLINE -> R.string.event_online
        EventKind.OFFLINE -> R.string.event_offline
        EventKind.RECOVERED -> R.string.event_recovered
        EventKind.TRANSIENT -> R.string.event_transient
        EventKind.ERROR -> R.string.event_error
        EventKind.INFO -> R.string.event_info
    }
}

@Composable
fun DiagnosticsScreen(state: EnergyUiState, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_report)
    Column(
        modifier.fillMaxSize().background(Mint.background)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeading(stringResource(R.string.diagnostics_title))
        MintCard {
            DotLabel(connectionName(state.connection),
                if (state.connection == ConnectionStatus.ONLINE) Mint.primary else Mint.warning)
            HorizontalDivider(color = Mint.subtle)
            Row {
                Metric(stringResource(R.string.udp_response),
                    state.lastRoundTripMs?.let { stringResource(R.string.ms_value, it.toInt()) } ?: "—",
                    Modifier.weight(1f))
                Metric(stringResource(R.string.missed_count),
                    state.consecutiveMisses.toString(), Modifier.weight(1f))
            }
        }
        MintCard {
            SectionHeading(stringResource(R.string.api_reliability))
            val stats = state.probeStats
            Row {
                Metric(stringResource(R.string.responded),
                    stats.reachabilityPercent?.let { it.toString() + "%" } ?: "—",
                    Modifier.weight(1f), Mint.primary)
                Metric(stringResource(R.string.samples),
                    stats.attempts.toString(), Modifier.weight(1f))
            }
            HorizontalDivider(color = Mint.subtle)
            Row {
                Metric(stringResource(R.string.timeouts),
                    stats.timeouts.toString(), Modifier.weight(1f), Mint.warning)
                Metric(stringResource(R.string.api_errors),
                    stats.apiErrors.toString(), Modifier.weight(1f), Mint.danger)
            }
            if (stats.phoneWifiMissing > 0) {
                Text(stringResource(R.string.phone_wifi_losses, stats.phoneWifiMissing.toInt()),
                    color = Mint.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        MintCard {
            SectionHeading(stringResource(R.string.network))
            Row {
                Metric(stringResource(R.string.phone_wifi),
                    when (state.phoneWifiAvailable) {
                        true -> stringResource(R.string.available)
                        false -> stringResource(R.string.unavailable)
                        null -> "—"
                    }, Modifier.weight(1f))
                Metric(stringResource(R.string.internet),
                    when (state.phoneInternetValidated) {
                        true -> stringResource(R.string.validated)
                        false -> stringResource(R.string.not_validated)
                        null -> "—"
                    }, Modifier.weight(1f))
            }
        }
        MintCard {
            SectionHeading(stringResource(R.string.wifi_status))
            if (!state.settings.advancedDiagnostics || state.wifi == null) {
                Text(stringResource(if (state.settings.advancedDiagnostics)
                    R.string.waiting_wifi else R.string.extra_disabled),
                    color = Mint.muted, style = MaterialTheme.typography.bodySmall)
            } else {
                val wifi = state.wifi
                Row {
                    Metric(stringResource(R.string.signal),
                        wifi.rssiDbm?.let { it.toString() + " dBm" } ?: "—",
                        Modifier.weight(1f))
                    Metric(stringResource(R.string.mode), state.snapshot.mode.name,
                        Modifier.weight(1f))
                }
                wifi.ssid?.let { ssid ->
                    Text(stringResource(R.string.ssid) + ": " + ssid,
                        color = Mint.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        RawApiCard(state.rawApiStatus, state.settings.demoMode)

        MintCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.event_timeline),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(
                    enabled = state.events.isNotEmpty(),
                    onClick = {
                        val report = buildString {
                            appendLine(context.getString(R.string.diagnostics_title))
                            state.events.forEach { item ->
                                appendLine(clockTime(item.timestampMs) + " | " +
                                    context.getString(eventLabel(item.title, item.kind)))
                            }
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    }
                ) {
                    Icon(Icons.Default.Share, stringResource(R.string.export_events))
                }
                TextButton(onClick = onClear, enabled = state.events.isNotEmpty()) {
                    Text(stringResource(R.string.clear_events))
                }
            }
            if (state.events.isEmpty()) {
                Text(stringResource(R.string.no_events), color = Mint.muted)
            } else {
                state.events.take(30).forEachIndexed { index, event ->
                    Row(verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            Text(stringResource(eventLabel(event.title, event.kind)),
                                fontWeight = FontWeight.Medium)
                            Text(clockTime(event.timestampMs),
                                color = Mint.muted,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (index < state.events.take(30).lastIndex) HorizontalDivider(color = Mint.subtle)
                }
            }
        }
        Text(stringResource(R.string.reboot_warning),
            color = Mint.muted, style = MaterialTheme.typography.labelSmall)
    }
}
