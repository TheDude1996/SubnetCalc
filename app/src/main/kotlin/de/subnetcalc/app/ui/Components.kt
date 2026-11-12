package de.subnetcalc.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.subnetcalc.app.ui.theme.Neon
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import kotlin.math.roundToInt

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()

internal fun fmt(n: Long): String = numberFormat.format(n)

/**
 * Formatiert sehr große Zahlen lesbar: kleine Werte ausgeschrieben,
 * große als Zweierpotenz mit Näherung, z. B. "2^64 (≈ 1,84 × 10^19)".
 */
internal fun fmtBig(n: BigInteger): String {
    if (n.bitLength() <= 50) return fmt(n.toLong())
    val decimal = BigDecimal(n)
    val exponent = decimal.precision() - 1
    val mantissa = decimal.movePointLeft(exponent).setScale(2, RoundingMode.HALF_UP)
    val approx = "≈ ${mantissa.toPlainString().replace('.', ',')} × 10^$exponent"
    return if (n.bitCount() == 1) "2^${n.bitLength() - 1} ($approx)" else approx
}

internal val CardShape = RoundedCornerShape(22.dp)

/** Grün für private, Pink für öffentliche, Blau für alle übrigen Bereiche. */
internal fun typeColor(label: String): Color = when {
    label.contains("rivat") -> Neon.green
    label == "Öffentlich" || label == "Global Unicast" -> Neon.pink
    else -> Neon.blue
}

internal fun bitColor(base: Color, isSet: Boolean, dimAlpha: Float = 0.22f): Color =
    if (isSet) base else base.copy(alpha = dimAlpha)

/** Dunkle Karte mit feinem Rahmen – Grundbaustein des Designs. */
@Composable
internal fun NeonCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Neon.surfaceRaised.copy(alpha = 0.72f))
            .border(1.dp, Neon.outlineFaint, CardShape)
            .padding(18.dp),
    ) {
        content()
    }
}

/**
 * Hervorgehobene Karte mit pulsierendem Leuchtrahmen. Ohne [tint] läuft der
 * Rahmen im Akzentverlauf, mit [tint] einfarbig (z. B. Grün/Pink als Urteil).
 */
@Composable
internal fun HeroContainer(
    tint: Color? = null,
    content: @Composable () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "heroGlow")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )
    val border: Brush =
        if (tint == null) Neon.borderGradient(glowAlpha) else SolidColor(tint.copy(alpha = glowAlpha))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = CardShape,
                ambientColor = tint ?: Neon.pink,
                spotColor = tint ?: Neon.green,
            )
            .clip(CardShape)
            .background(Brush.verticalGradient(listOf(Neon.surfaceHigh, Neon.surfaceRaised)))
            .border(1.2.dp, border, CardShape)
            .padding(18.dp),
    ) {
        content()
    }
}

@Composable
internal fun GradientHeadline(text: String, fontSize: TextUnit) {
    Text(
        text = text,
        style = TextStyle(
            brush = Neon.accentGradient,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        ),
    )
}

@Composable
internal fun Badge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Zeichnet [totalBits] Bits als Leuchtbalken, 32 pro Zeile, mit einer Lücke
 * nach jeweils [groupSize] Bits. [colorAt] liefert die Farbe je Bitposition
 * (0 = höchstwertiges Bit).
 */
@Composable
internal fun BitGrid(
    totalBits: Int,
    groupSize: Int,
    cellHeight: Dp,
    colorAt: (Int) -> Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (rowStart in 0 until totalBits step 32) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (p in rowStart until rowStart + 32) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 1.dp)
                            .height(cellHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colorAt(p)),
                    )
                    if ((p + 1) % groupSize == 0 && (p + 1) % 32 != 0) {
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun Legend(vararg entries: Pair<String, Color>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for ((text, color) in entries) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun ResultRows(content: @Composable () -> Unit) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            content()
        }
    }
}

@Composable
internal fun ResultRow(label: String, value: String, accent: Color = Neon.textPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            color = Neon.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = accent,
        )
    }
}

/** Wie [ResultRow], aber mit dem Wert unter der Beschriftung – für lange Werte wie IPv6-Adressen. */
@Composable
internal fun StackedRow(label: String, value: String, accent: Color = Neon.textPrimary) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.2.sp,
            color = Neon.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = accent,
        )
    }
}

@Composable
internal fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Neon.textSecondary,
        modifier = Modifier.padding(8.dp),
    )
}

@Composable
internal fun MoreRowsHint(remaining: String, shown: Int) {
    Text(
        text = "… und $remaining weitere Subnetze (Anzeige auf ${fmt(shown.toLong())} begrenzt)",
        style = MaterialTheme.typography.bodyMedium,
        color = Neon.textSecondary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
internal fun neonFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Neon.green,
    unfocusedBorderColor = Neon.outline,
    cursorColor = Neon.pink,
    focusedLabelColor = Neon.green,
    unfocusedLabelColor = Neon.textSecondary,
    focusedTextColor = Neon.textPrimary,
    unfocusedTextColor = Neon.textPrimary,
    focusedPlaceholderColor = Neon.textFaint,
    unfocusedPlaceholderColor = Neon.textFaint,
)

private val fieldTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp)

/** Eingabefeld für eine IPv4- oder IPv6-Adresse. */
@Composable
internal fun AddressField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    valid: Boolean,
    isV6: Boolean,
) {
    val showError = value.isNotBlank() && !valid
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = showError,
        supportingText = {
            if (showError) Text(if (isV6) "Ungültige IPv6-Adresse" else "Ungültige IPv4-Adresse")
        },
        singleLine = true,
        // IPv6 braucht Buchstaben und Doppelpunkte, daher keine reine Zifferntastatur.
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            autoCorrectEnabled = false,
        ),
        colors = neonFieldColors(),
        textStyle = fieldTextStyle,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun MaskField(
    value: String,
    onValueChange: (String) -> Unit,
    valid: Boolean,
    isV6: Boolean,
) {
    val showError = value.isNotBlank() && !valid
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (isV6) "Präfixlänge" else "Netzmaske") },
        placeholder = { Text(if (isV6) "/64" else "/24 oder 255.255.255.0") },
        isError = showError,
        supportingText = {
            if (showError) {
                Text(
                    if (isV6) "Ungültige Präfixlänge (0–128)"
                    else "Ungültige Maske (CIDR 0–32 oder Punktnotation)"
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = neonFieldColors(),
        textStyle = fieldTextStyle,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun PrefixChips(prefixes: List<Int>, onPick: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        prefixes.forEach { p ->
            AssistChip(
                onClick = { onPick(p) },
                label = { Text("/$p", fontFamily = FontFamily.Monospace) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Neon.surfaceHigh.copy(alpha = 0.5f),
                    labelColor = Neon.green,
                ),
                border = BorderStroke(1.dp, Neon.outline),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SplitCard(
    parentPrefix: Int,
    maxPrefix: Int,
    byHosts: Boolean,
    onModeChange: (Boolean) -> Unit,
    splitPrefix: Int,
    onSplitChange: (Int) -> Unit,
    hostsLabel: String,
    hostsText: String,
    onHostsChange: (String) -> Unit,
    hostsError: String?,
    summary: String?,
) {
    NeonCard {
        Column {
            Text(
                text = "IN SUBNETZE AUFTEILEN",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Neon.textPrimary,
            )
            Spacer(Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val segColors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Neon.greenFaint,
                    activeContentColor = Neon.green,
                    activeBorderColor = Neon.green,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = Neon.textSecondary,
                    inactiveBorderColor = Neon.outline,
                )
                SegmentedButton(
                    selected = !byHosts,
                    onClick = { onModeChange(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = segColors,
                ) { Text("Nach Präfix") }
                SegmentedButton(
                    selected = byHosts,
                    onClick = { onModeChange(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = segColors,
                ) { Text("Nach Hosts") }
            }
            Spacer(Modifier.height(12.dp))

            if (byHosts) {
                OutlinedTextField(
                    value = hostsText,
                    onValueChange = onHostsChange,
                    label = { Text(hostsLabel) },
                    placeholder = { Text("z. B. 50") },
                    isError = hostsError != null,
                    supportingText = { if (hostsError != null) Text(hostsError) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = neonFieldColors(),
                    textStyle = fieldTextStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Slider(
                    value = splitPrefix.toFloat(),
                    onValueChange = { onSplitChange(it.roundToInt()) },
                    valueRange = parentPrefix.toFloat()..maxPrefix.toFloat(),
                    steps = (maxPrefix - parentPrefix - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = Neon.pink,
                        activeTrackColor = Neon.green,
                        inactiveTrackColor = Neon.outline,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    for (edge in listOf(parentPrefix, maxPrefix)) {
                        Text(
                            "/$edge",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = Neon.textSecondary,
                        )
                    }
                }
            }

            if (summary != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Neon.green,
                )
            }
        }
    }
}

@Composable
internal fun SubnetRow(index: Int, title: String, detail: String) {
    val rowShape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(rowShape)
            .background(Neon.surfaceRaised.copy(alpha = 0.55f))
            .border(1.dp, Neon.outlineFaint, rowShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(Neon.green, Neon.pink))),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(top = 10.dp, bottom = 10.dp, end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Neon.textFaint,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Neon.green,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Neon.textSecondary,
            )
        }
    }
}
