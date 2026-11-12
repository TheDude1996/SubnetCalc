package de.subnetcalc.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * "Neon Ops"-Designsystem: tiefe Blautöne mit Neon-Grün- und
 * Neon-Pink-Akzenten. Die App ist bewusst immer dunkel.
 */
object Neon {
    // Hintergründe (tiefes Mitternachtsblau)
    val bgTop = Color(0xFF030714)
    val bgMid = Color(0xFF071126)
    val bgBottom = Color(0xFF0A1830)
    val surface = Color(0xFF0A1428)
    val surfaceRaised = Color(0xFF0E1B36)
    val surfaceHigh = Color(0xFF13244A)

    // Akzente
    val green = Color(0xFF00FFA3)
    val greenDim = Color(0xFF0FBF7F)
    val greenFaint = Color(0x3300FFA3)
    val pink = Color(0xFFFF2E97)
    val pinkDim = Color(0xFFC92277)
    val pinkFaint = Color(0x33FF2E97)
    val blue = Color(0xFF4D8DFF)

    // Text und Linien
    val textPrimary = Color(0xFFE8F1FF)
    val textSecondary = Color(0xFF8DA6CC)
    val textFaint = Color(0xFF5C729B)
    val outline = Color(0xFF22406E)
    val outlineFaint = Color(0xFF16294A)

    // Verläufe
    val accentGradient = Brush.horizontalGradient(listOf(green, blue, pink))
    val backgroundGradient = Brush.verticalGradient(listOf(bgTop, bgMid, bgBottom))

    fun borderGradient(alpha: Float = 1f) = Brush.linearGradient(
        listOf(green.copy(alpha = alpha), blue.copy(alpha = alpha * 0.7f), pink.copy(alpha = alpha))
    )
}

private val NeonColorScheme = darkColorScheme(
    primary = Neon.green,
    onPrimary = Color(0xFF00170D),
    primaryContainer = Color(0xFF063226),
    onPrimaryContainer = Color(0xFF9CFFD9),
    secondary = Neon.pink,
    onSecondary = Color(0xFF2A0016),
    secondaryContainer = Color(0xFF3B0A26),
    onSecondaryContainer = Color(0xFFFFB3D9),
    tertiary = Neon.blue,
    onTertiary = Color(0xFF001633),
    background = Neon.bgMid,
    onBackground = Neon.textPrimary,
    surface = Neon.surface,
    onSurface = Neon.textPrimary,
    surfaceVariant = Neon.surfaceRaised,
    onSurfaceVariant = Neon.textSecondary,
    outline = Neon.outline,
    outlineVariant = Neon.outlineFaint,
    error = Color(0xFFFF5C8A),
    onError = Color(0xFF330013),
)

@Composable
fun SubnetCalcTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonColorScheme,
        typography = Typography(),
        content = content,
    )
}
