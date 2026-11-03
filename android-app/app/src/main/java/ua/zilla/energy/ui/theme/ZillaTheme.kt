package ua.zilla.energy.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Mint {
    val background = Color(0xFFF2FAF5)
    val surface = Color.White
    val deep = Color(0xFF153C34)
    val primary = Color(0xFF208D5B)
    val leaf = Color(0xFF4DBB76)
    val subtle = Color(0xFFE3F4E9)
    val muted = Color(0xFF687E75)
    val blue = Color(0xFF73A9DC)
    val warning = Color(0xFFDA9441)
    val danger = Color(0xFFC5574C)
}

private val scheme = lightColorScheme(
    primary = Mint.primary,
    onPrimary = Color.White,
    primaryContainer = Mint.subtle,
    onPrimaryContainer = Mint.deep,
    secondary = Mint.leaf,
    background = Mint.background,
    onBackground = Mint.deep,
    surface = Mint.surface,
    onSurface = Mint.deep,
    surfaceVariant = Mint.subtle,
    outline = Color(0xFFB7CEC0),
    error = Mint.danger
)

@Composable
fun ZillaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(26.dp)
        ),
        content = content
    )
}
