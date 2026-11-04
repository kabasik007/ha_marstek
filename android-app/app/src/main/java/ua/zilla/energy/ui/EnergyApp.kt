package ua.zilla.energy.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ua.zilla.energy.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.zilla.energy.EnergyViewModel
import ua.zilla.energy.ui.screens.*

private enum class Tab(val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, Icons.Default.Home),
    HEALTH(R.string.nav_diagnostics, Icons.Default.Timeline),
    BMS(R.string.nav_bms, Icons.Default.BatteryChargingFull),
    CAMERAS(R.string.nav_settings, Icons.Default.Videocam),
    CONTROL(R.string.nav_settings, Icons.Default.Tune),
    SETTINGS(R.string.nav_settings, Icons.Default.Settings)
}

@Composable
fun EnergyApp(vm: EnergyViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.HOME) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                // Camera UI is intentionally parked while Marstek reliability is prioritized.
                Tab.entries.filter { it in listOf(Tab.HOME, Tab.BMS, Tab.HEALTH, Tab.SETTINGS) }.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = stringResource(item.titleRes)) },
                        label = { Text(stringResource(item.titleRes), maxLines = 1) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            Tab.HOME -> DashboardScreen(state, onSettings = { tab = Tab.SETTINGS },
                onWindow = vm::selectHistoryWindow, modifier = Modifier.padding(padding))
            Tab.HEALTH -> DiagnosticsScreen(state, onClear = vm::clearEvents, Modifier.padding(padding))
            Tab.BMS -> BmsScreen(state, onBasicTest = vm::probeBmsBasics,
                onCellsOnce = vm::probeCellsOnce, modifier = Modifier.padding(padding))
            Tab.CAMERAS -> CamerasScreen(state, onAdd = vm::addCamera, onRemove = vm::removeCamera, Modifier.padding(padding))
            Tab.CONTROL -> ControlScreen(state, Modifier.padding(padding))
            Tab.SETTINGS -> SettingsScreen(state, vm::saveEndpoint, vm::setDemo, vm::setPollSeconds,
                vm::setAdvancedDiagnostics, vm::discoverMarstek, vm::selectDiscoveredMarstek,
                Modifier.padding(padding))
        }
    }
}
