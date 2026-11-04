package ua.zilla.energy.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ua.zilla.energy.model.RawApiStatus
import ua.zilla.energy.ui.theme.Mint

/** Read-only debug card. Displays only sanitized and ephemeral API data. */
@Composable
fun RawApiCard(raw: RawApiStatus?, demoMode: Boolean) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    MintCard {
        SectionHeading("Raw API • ES.GetStatus", "Debug the actual fields returned by Marstek")
        if (demoMode || raw == null) {
            Text(
                if (demoMode) "Turn off Demo Mode to inspect real API responses."
                else "Waiting for the first successful ES.GetStatus reply.",
                color = Mint.muted, style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text("Captured: " + clockTime(raw.timestampMs) +
                " • " + raw.reportedFields + " returned fields",
                color = Mint.muted, style = MaterialTheme.typography.labelMedium)
            if (raw.absentFields.isNotEmpty()) {
                Text("Not returned: " + raw.absentFields.joinToString(", "),
                    color = Mint.warning, style = MaterialTheme.typography.bodySmall)
            }
            if (raw.nullFields.isNotEmpty()) {
                Text("Returned null: " + raw.nullFields.joinToString(", "),
                    color = Mint.warning, style = MaterialTheme.typography.bodySmall)
            }
            Text("A missing battery power field is not a zero-watt reading.",
                color = Mint.muted, style = MaterialTheme.typography.bodySmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                    Text(if (expanded) "Hide JSON" else "Show JSON")
                }
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(raw.payload))
                }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy redacted JSON") }
                IconButton(onClick = {
                    val message = buildString {
                        appendLine("Marstek • ES.GetStatus (redacted)")
                        appendLine("Captured: " + clockTime(raw.timestampMs))
                        appendLine("Missing fields: " + raw.absentFields.joinToString(", "))
                        appendLine("Null fields: " + raw.nullFields.joinToString(", "))
                        appendLine(raw.payload)
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    context.startActivity(Intent.createChooser(send, "Share redacted API report"))
                }) { Icon(Icons.Default.Share, contentDescription = "Share redacted JSON") }
            }
            if (expanded) {
                Surface(
                    color = Mint.background,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        raw.payload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("Unknown fields and identifying strings are masked. Only the latest result lives in memory; no JSON history is saved.",
                    color = Mint.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
