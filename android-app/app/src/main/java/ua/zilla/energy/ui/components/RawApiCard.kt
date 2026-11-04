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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ua.zilla.energy.R
import ua.zilla.energy.model.RawApiStatus
import ua.zilla.energy.ui.theme.Mint

@Composable
fun RawApiCard(raw: RawApiStatus?, demoMode: Boolean) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shareTitle = stringResource(R.string.share_json_chooser)
    var expanded by remember { mutableStateOf(false) }
    MintCard {
        SectionHeading(stringResource(R.string.raw_title))
        if (demoMode || raw == null) {
            Text(stringResource(if (demoMode) R.string.raw_demo else R.string.raw_wait),
                color = Mint.muted, style = MaterialTheme.typography.bodySmall)
        } else {
            Text(stringResource(R.string.raw_capture, clockTime(raw.timestampMs),
                raw.reportedFields), color = Mint.muted, style = MaterialTheme.typography.labelMedium)
            if (raw.absentFields.isNotEmpty()) {
                Text(stringResource(R.string.raw_missing, raw.absentFields.joinToString(", ")),
                    color = Mint.warning, style = MaterialTheme.typography.bodySmall)
            }
            if (raw.nullFields.isNotEmpty()) {
                Text(stringResource(R.string.raw_null, raw.nullFields.joinToString(", ")),
                    color = Mint.warning, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (expanded) R.string.hide_json else R.string.show_json))
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(raw.payload)) }) {
                    Icon(Icons.Default.ContentCopy, stringResource(R.string.copy_json))
                }
                IconButton(onClick = {
                    val message = "Marstek ES.GetStatus\n" + raw.payload
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    context.startActivity(Intent.createChooser(intent, shareTitle))
                }) { Icon(Icons.Default.Share, stringResource(R.string.share_json)) }
            }
            if (expanded) {
                Surface(color = Mint.background, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(raw.payload,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()).padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
