package ua.zilla.energy.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import ua.zilla.energy.data.LocalAddress
import ua.zilla.energy.model.CameraDevice
import ua.zilla.energy.model.EnergyUiState
import ua.zilla.energy.ui.components.MintCard
import ua.zilla.energy.ui.components.SectionHeading
import ua.zilla.energy.ui.theme.Mint

@Composable
fun CamerasScreen(
    state: EnergyUiState,
    onAdd: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var adding by remember { mutableStateOf(false) }
    var activeId by remember { mutableStateOf<String?>(null) }
    val camera = state.settings.cameras.firstOrNull { it.id == activeId }

    Column(
        modifier.fillMaxSize().background(Mint.background)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionHeading("Cameras", "Private LAN RTSP feeds")
            }
            FilledIconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, "Add camera") }
        }
        if (camera != null) {
            MintCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(camera.name, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { activeId = null }) { Text("Close") }
                }
                CameraPlayer(camera)
                Text("Live stream stays on this device. No cloud relay.",
                    color = Mint.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (state.settings.cameras.isEmpty()) {
            MintCard {
                Icon(Icons.Default.Videocam, null, tint = Mint.primary, modifier = Modifier.size(44.dp))
                Text("Your camera wall starts here", style = MaterialTheme.typography.titleMedium)
                Text("Add a camera with a local RTSP stream. The app does not discover or access cameras automatically.",
                    color = Mint.muted)
                OutlinedButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add camera")
                }
            }
        }
        state.settings.cameras.forEach { item ->
            MintCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Videocam, null, tint = Mint.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text("RTSP • Local network", color = Mint.muted)
                    }
                    IconButton(onClick = {
                        if (activeId == item.id) activeId = null
                        onRemove(item.id)
                    }) { Icon(Icons.Default.Delete, "Remove camera", tint = Mint.danger) }
                }
                Button(onClick = { activeId = item.id }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (activeId == item.id) "Playing" else "Open live view")
                }
            }
        }
        Text("For authenticated RTSP cameras, secure credential storage will be added later. Do not paste passwords or tokens into camera URLs.",
            color = Mint.muted, style = MaterialTheme.typography.bodySmall)
    }
    if (adding) {
        AddCameraDialog(
            onDismiss = { adding = false },
            onAdd = { name, url ->
                onAdd(name, url)
                adding = false
            }
        )
    }
}

@Composable
private fun AddCameraDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add local camera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") })
                OutlinedTextField(url, { url = it.trim() },
                    label = { Text("RTSP URL") },
                    placeholder = { Text("rtsp://192.168.0.20:554/stream") })
                Text("Only private IPv4 RTSP URLs with no username/password are supported.",
                    color = Mint.muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, url) },
                enabled = name.isNotBlank() && LocalAddress.validRtsp(url)
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A single active decoder instead of opening all feeds at once. */
@Composable
private fun CameraPlayer(camera: CameraDevice) {
    val context = LocalContext.current
    val player = remember(camera.id, camera.rtspUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(camera.rtspUrl)))
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(230.dp),
        factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
        update = { it.player = player }
    )
}
