package ua.zilla.energy.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.zilla.energy.EnergyViewModel
import ua.zilla.energy.ui.screens.*

private enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    HEALTH("Diagnostics", Icons.Default.Timeline),
    CAMERAS("Cameras", Icons.Default.Videocam),
    CONTROL("Control", Icons.Default.Tune),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun EnergyApp(vm: EnergyViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.HOME) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                // Camera UI is intentionally parked while Marstek reliability is prioritized.
                Tab.entries.filter { it != Tab.CAMERAS }.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title, maxLines = 1) },
                        alwaysShowLabel = false
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            Tab.HOME -> DashboardScreen(state, onSettings = { tab = Tab.SETTINGS }, Modifier.padding(padding))
            Tab.HEALTH -> DiagnosticsScreen(state, onClear = vm::clearEvents, Modifier.padding(padding))
            Tab.CAMERAS -> CamerasScreen(state, onAdd = vm::addCamera, onRemove = vm::removeCamera, Modifier.padding(padding))
            Tab.CONTROL -> ControlScreen(state, Modifier.padding(padding))
            Tab.SETTINGS -> SettingsScreen(state, vm::saveEndpoint, vm::setDemo, vm::setPollSeconds,
                vm::setAdvancedDiagnostics, Modifier.padding(padding))
        }
    }
}
