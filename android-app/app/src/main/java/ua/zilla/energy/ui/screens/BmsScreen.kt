package ua.zilla.energy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ua.zilla.energy.R
import ua.zilla.energy.data.LocalAddress
import ua.zilla.energy.model.BmsProbeState
import ua.zilla.energy.model.EnergyUiState
import ua.zilla.energy.ui.components.Metric
import ua.zilla.energy.ui.components.MintCard
import ua.zilla.energy.ui.components.SectionHeading
import ua.zilla.energy.ui.theme.Mint
import java.util.Locale

/**
 * BMS test requires user action: no timer, no auto-cell polling,
 * no modifications to the device or its operating mode.
 */
@Composable
fun BmsScreen(
    state: EnergyUiState,
    onBasicTest: (String) -> Unit,
    onCellsOnce: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var host by rememberSaveable(state.settings.host) {
        mutableStateOf(state.settings.host)
    }
    val bms = state.bms
    val connectedHost = bms.host == host.trim()
    val basicsReady = connectedHost && bms.voltageV != null && bms.currentA != null
    val basicsArePlausible = basicsReady && bms.voltageV!! in 40.0..65.0
    val acceptedHost = LocalAddress.valid(host.trim())

    Column(
        modifier.fillMaxSize().background(Mint.background)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeading(stringResource(R.string.bms_title))
        MintCard {
            Text(stringResource(R.string.bms_ethernet_hint),
                color = Mint.muted, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim() },
                singleLine = true,
                label = { Text(stringResource(R.string.bms_host)) },
                isError = host.isNotEmpty() && !acceptedHost,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Text(stringResource(R.string.bms_port),
                color = Mint.muted, style = MaterialTheme.typography.labelMedium)
            Button(
                onClick = { onBasicTest(host.trim()) },
                enabled = acceptedHost && !bms.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.bms_test))
            }
            if (bms.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (bms.errorType != null && connectedHost) {
                Text(stringResource(R.string.bms_failed),
                    color = Mint.danger, style = MaterialTheme.typography.bodySmall)
                Text(bms.errorType, color = Mint.muted,
                    style = MaterialTheme.typography.labelSmall)
            }
        }

        if (basicsReady) {
            MintCard {
                SectionHeading(stringResource(R.string.bms_battery))
                Row {
                    Metric(stringResource(R.string.bms_voltage),
                        String.format(Locale.US, "%.2f V", bms.voltageV),
                        Modifier.weight(1f))
                    Metric(stringResource(R.string.bms_current),
                        String.format(Locale.US, "%.1f A", bms.currentA),
                        Modifier.weight(1f))
                }
                if (!basicsArePlausible) Text(
                    stringResource(R.string.bms_voltage_unexpected),
                    color = Mint.danger, style = MaterialTheme.typography.bodySmall
                )
            }
            MintCard {
                SectionHeading(stringResource(R.string.bms_cells))
                Text(stringResource(R.string.bms_cells_warning),
                    style = MaterialTheme.typography.bodySmall, color = Mint.warning)
                OutlinedButton(
                    onClick = { onCellsOnce(host.trim()) },
                    enabled = acceptedHost && basicsArePlausible && !bms.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.bms_read_cells))
                }
                if (connectedHost && bms.cellsV.size == 16) {
                    val min = bms.cellsV.minOrNull() ?: 0.0
                    val max = bms.cellsV.maxOrNull() ?: 0.0
                    val spread = (max - min) * 1000.0
                    Row {
                        Metric(stringResource(R.string.bms_min),
                            String.format(Locale.US, "%.3f V", min),
                            Modifier.weight(1f))
                        Metric(stringResource(R.string.bms_max),
                            String.format(Locale.US, "%.3f V", max),
                            Modifier.weight(1f))
                    }
                    Metric(stringResource(R.string.bms_delta),
                        String.format(Locale.US, "%.1f mV", spread))
                    bms.cellsV.chunked(2).forEachIndexed { index, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEachIndexed { j, value ->
                                val n = index * 2 + j + 1
                                val unusual = value == min || value == max
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.small,
                                    color = if (unusual) Mint.subtle else Mint.background
                                ) {
                                    Text(
                                        "#$n  " + String.format(Locale.US, "%.3f V", value),
                                        modifier = Modifier.padding(10.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.bms_readonly_note),
            style = MaterialTheme.typography.labelSmall, color = Mint.muted)
    }
}
