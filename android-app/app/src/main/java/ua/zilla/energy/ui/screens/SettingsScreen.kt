package ua.zilla.energy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ua.zilla.energy.data.LocalAddress
import ua.zilla.energy.model.EnergyUiState
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
    modifier: Modifier = Modifier
) {
    var ip by remember(state.settings.host) { mutableStateOf(state.settings.host) }
    var port by remember(state.settings.port) { mutableStateOf(state.settings.port.toString()) }
    val valid = LocalAddress.valid(ip.trim()) &&
        (port.toIntOrNull() ?: 0) in 1..65535

    Column(
        modifier.fillMaxSize().background(Mint.background)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("Settings", "Local-first. No account or cloud required.")
        MintCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Demo mode", style = MaterialTheme.typography.titleMedium)
                    Text("Synthetic sample data, clearly labelled",
                        color = Mint.muted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.settings.demoMode, onCheckedChange = onDemo)
            }
        }
        MintCard {
            Text("Marstek Local API", style = MaterialTheme.typography.titleMedium)
            Text("Enable Open API via Bluetooth inside the official Marstek app first.",
                color = Mint.muted)
            OutlinedTextField(
                value = ip, onValueChange = { ip = it.trim() },
                label = { Text("Device IP (private LAN)") },
                placeholder = { Text("192.168.0.61") },
                singleLine = true,
                isError = ip.isNotBlank() && !LocalAddress.valid(ip),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text("UDP port") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(onClick = { onSave(ip.trim(), port.toInt()) }, enabled = valid,
                modifier = Modifier.fillMaxWidth()) { Text("Save device") }
            Text("Live polling starts after switching Demo mode off. The phone and device must share Wi-Fi; internet access is not required.",
                color = Mint.muted, style = MaterialTheme.typography.bodySmall)
        }
        MintCard {
            Text("Polling interval", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60, 120).forEach { seconds ->
                    FilterChip(
                        selected = state.settings.pollSeconds == seconds,
                        onClick = { onInterval(seconds) },
                        label = { Text(seconds.toString() + "s") }
                    )
                }
            }
            Text("30–60 seconds recommended when investigating instability. Only ES.GetStatus is requested automatically.",
                color = Mint.muted, style = MaterialTheme.typography.bodySmall)
        }
        MintCard {
            Text("Marstek Wi-Fi + operating mode diagnostics", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Enable extra read-only queries")
                    Text("Opt in to wifi.GetStatus and ES.GetMode at most once every 5 minutes. May not work on all firmware versions.",
                        color = Mint.muted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = state.settings.advancedDiagnostics, onCheckedChange = onAdvanced)
            }
        }
        MintCard {
            Text("Security", style = MaterialTheme.typography.titleMedium)
            Text("The Local API is unauthenticated UDP. Keep the device on a trusted Wi-Fi/VLAN. Do not forward UDP 30000 to the internet.",
                color = Mint.muted)
            Text("Real measurements and network probes are retained locally for up to 30 days. Data exports always require a Share action.",
                color = Mint.muted)
        }
        if (state.error != null) {
            Text("Last message: " + state.error, color = Mint.warning)
        }
    }
}
