package ua.zilla.energy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.zilla.energy.model.ConnectionStatus
import ua.zilla.energy.ui.theme.Mint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun MintCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) { Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Mint.muted)
    }
}

@Composable
fun DotLabel(text: String, color: Color = Mint.primary) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, color: Color = Mint.deep) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Mint.muted, style = MaterialTheme.typography.labelMedium)
        Text(value, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

fun watts(w: Double?): String = if (w == null) "—" else {
    if (abs(w) >= 1000.0) String.format(Locale.US, "%.2f kW", w / 1000.0)
    else String.format(Locale.US, "%.0f W", w)
}

fun clockTime(timestamp: Long): String =
    SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestamp))

fun connectionName(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.DEMO -> "Demo"
    ConnectionStatus.CONNECTING -> "Connecting"
    ConnectionStatus.ONLINE -> "Local API online"
    ConnectionStatus.DEGRADED -> "Unstable"
    ConnectionStatus.OFFLINE -> "Offline"
    ConnectionStatus.DISABLED -> "Not configured"
}
