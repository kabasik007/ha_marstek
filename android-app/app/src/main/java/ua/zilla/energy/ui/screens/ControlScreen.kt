package ua.zilla.energy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.zilla.energy.model.EnergyMode
import ua.zilla.energy.model.EnergyUiState
import ua.zilla.energy.ui.components.MintCard
import ua.zilla.energy.ui.components.SectionHeading
import ua.zilla.energy.ui.theme.Mint

@Composable
fun ControlScreen(state: EnergyUiState, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Mint.background)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeading("Control", "Read-only prototype: safety first")
        MintCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = Mint.warning)
                Spacer(Modifier.width(10.dp))
                Text("Device write commands are locked", fontWeight = FontWeight.SemiBold)
            }
            Text("The first build intentionally cannot change modes, discharge limits, firmware or factory settings. This is especially important while investigating unexpected restarts.",
                color = Mint.muted)
        }
        MintCard {
            Text("Operating mode", style = MaterialTheme.typography.titleMedium)
            Text("Reported: " + state.snapshot.mode.name.lowercase().replaceFirstChar { it.uppercase() },
                color = Mint.muted)
            EnergyMode.entries.filter { it != EnergyMode.UNKNOWN }.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { mode ->
                        OutlinedCard(
                            modifier = Modifier.weight(1f).height(88.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = if (mode == state.snapshot.mode) Mint.subtle
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(mode.name, fontWeight = FontWeight.SemiBold)
                                Text("Read only", style = MaterialTheme.typography.labelSmall,
                                    color = Mint.muted)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        MintCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, tint = Mint.primary)
                Spacer(Modifier.width(8.dp))
                Text("Next phase", style = MaterialTheme.typography.titleMedium)
            }
            Text("Once the device model, firmware and API behavior are verified, explicit confirmation, capability checks and audit logging can be added for supported write operations.",
                color = Mint.muted)
        }
    }
}
