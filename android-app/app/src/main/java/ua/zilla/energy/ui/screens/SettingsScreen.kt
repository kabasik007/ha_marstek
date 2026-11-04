package ua.zilla.energy.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import ua.zilla.energy.R
import ua.zilla.energy.data.LocalAddress
import ua.zilla.energy.model.EnergyUiState
import ua.zilla.energy.model.DiscoveredMarstek
import ua.zilla.energy.ui.components.MintCard
import ua.zilla.energy.ui.components.SectionHeading
import ua.zilla.energy.ui.theme.Mint

@Composable
fun SettingsScreen(
    state: EnergyUiState,
    onSave: (String, Int) -> Unit,
    onDemo: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onAdvanced: (Boolean) -> Unit,
    onDiscover: () -> Unit,
    onSelectDevice: (DiscoveredMarstek) -> Unit,
    modifier: Modifier = Modifier
) {
    var ip by remember(state.settings.host) { mutableStateOf(state.settings.host) }
    var port by remember(state.settings.port) { mutableStateOf(state.settings.port.toString()) }
    var selectedLanguage by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }
    val valid = LocalAddress.valid(ip.trim()) && (port.toIntOrNull() ?: 0) in 1..65535

    LaunchedEffect(state.settings.host) {
        if (state.settings.host.isBlank() && !state.discoveryAttempted) onDiscover()
    }

    Column(
        modifier.fillMaxSize().background(Mint.background)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeading(stringResource(R.string.settings_title))
        MintCard {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "" to R.string.lang_system,
                    "uk" to R.string.lang_uk,
                    "en" to R.string.lang_en
                ).forEach { (tag, label) ->
                    FilterChip(
                        selected = selectedLanguage == tag ||
                            (tag.isNotEmpty() && selectedLanguage.startsWith(tag + "-")),
                        onClick = {
                            selectedLanguage = tag
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(tag)
                            )
                        },
                        label = { Text(stringResource(label), maxLines = 1) }
                    )
                }
            }
        }
        MintCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.demo_mode),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                Switch(checked = state.settings.demoMode, onCheckedChange = onDemo)
            }
        }
        MintCard {
            Text(stringResource(R.string.local_api), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.enable_api_hint), color = Mint.muted,
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = onDiscover, enabled = !state.isDiscovering,
                modifier = Modifier.fillMaxWidth()) {
                if (state.isDiscovering) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (state.isDiscovering)
                    R.string.searching_wifi else R.string.find_marstek))
            }
            if (state.discoveredDevices.isNotEmpty()) {
                Text(stringResource(R.string.found_devices),
                    style = MaterialTheme.typography.labelMedium, color = Mint.muted)
                state.discoveredDevices.forEach { device ->
                    OutlinedButton(
                        onClick = { onSelectDevice(device) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(device.model, style = MaterialTheme.typography.titleSmall)
                            Text(
                                device.host + ":" + device.port +
                                    (if (device.firmware.isNotBlank()) " · v" + device.firmware else ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            if (state.discoveryError != null) {
                val message = when {
                    state.discoveryError.contains("Wi-Fi", ignoreCase = true) -> R.string.wifi_missing
                    state.discoveryError.contains("No Marstek", ignoreCase = true) -> R.string.no_devices
                    else -> R.string.scan_failed
                }
                Text(stringResource(message), color = Mint.warning,
                    style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = ip, onValueChange = { ip = it.trim() },
                label = { Text(stringResource(R.string.ip_address)) },
                placeholder = { Text("192.168.0.61") },
                singleLine = true,
                isError = ip.isNotBlank() && !LocalAddress.valid(ip),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.udp_port)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(onClick = { onSave(ip.trim(), port.toInt()) }, enabled = valid,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_device)) }
        }
        MintCard {
            Text(stringResource(R.string.poll_interval), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60, 120).forEach { seconds ->
                    FilterChip(
                        selected = state.settings.pollSeconds == seconds,
                        onClick = { onInterval(seconds) },
                        label = { Text(stringResource(R.string.seconds_value, seconds)) }
                    )
                }
            }
        }
        MintCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.advanced_diagnostics),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.advanced_hint),
                        color = Mint.muted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.settings.advancedDiagnostics, onCheckedChange = onAdvanced)
            }
        }
        Text(stringResource(R.string.security_notice), color = Mint.muted,
            style = MaterialTheme.typography.bodySmall)
        if (state.error != null) {
            Text(stringResource(R.string.error_generic), color = Mint.warning)
        }
    }
}
