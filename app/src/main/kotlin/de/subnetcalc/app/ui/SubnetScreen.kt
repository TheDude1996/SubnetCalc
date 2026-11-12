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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.subnetcalc.app.ui.theme.Neon
import de.subnetcalc.core.SubnetCalculator
import de.subnetcalc.core.SubnetInfo
import java.text.NumberFormat
import kotlin.math.roundToInt

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()

private fun fmt(n: Long): String = numberFormat.format(n)

/** Maximal direkt gerenderte Subnetz-Zeilen; darüber wird ein Hinweis angezeigt. */
private const val MAX_ROWS = 65_536

private val CardShape = RoundedCornerShape(22.dp)

@Composable
fun SubnetScreen() {
    var ipText by rememberSaveable { mutableStateOf("192.168.1.0") }
    var maskText by rememberSaveable { mutableStateOf("/24") }
    var splitPrefix by rememberSaveable { mutableIntStateOf(26) }
    var byHosts by rememberSaveable { mutableStateOf(false) }
    var hostsText by rememberSaveable { mutableStateOf("") }

    val ip = SubnetCalculator.parseIpv4(ipText)
    val prefix = SubnetCalculator.parsePrefixOrMask(maskText)
    val info = if (ip != null && prefix != null) SubnetCalculator.calculate(ip, prefix) else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Neon.backgroundGradient),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "header") { Header() }

                item(key = "input") {
                    InputCard(
                        ipText = ipText,
                        onIpChange = { ipText = it },
                        ipValid = ip != null,
                        maskText = maskText,
                        onMaskChange = { maskText = it },
                        maskValid = prefix != null,
                    )
                }

                if (info != null) {
                    item(key = "result") { HeroCard(info) }

                    if (info.prefix < 32) {
                        val hostsRequired = hostsText.trim().toLongOrNull()
                        val hostsPrefix = hostsRequired?.let { SubnetCalculator.prefixForHosts(it) }
                        val hostsError: String? = when {
                            !byHosts || hostsText.isBlank() -> null
                            hostsRequired == null || hostsPrefix == null ->
                                "Ungültige Anzahl (1 bis ${fmt(4_294_967_294L)})"
                            hostsPrefix < info.prefix ->
                                "Passt nicht in /${info.prefix} – dort sind höchstens " +
                                    "${fmt(info.usableHosts)} Hosts möglich"
                            else -> null
                        }
                        val effSplit: Int? = if (byHosts) {
                            if (hostsText.isNotBlank() && hostsError == null) hostsPrefix else null
                        } else {
                            splitPrefix.coerceIn(info.prefix, 32)
                        }
                        val count = effSplit?.let { SubnetCalculator.subnetCount(info.prefix, it) }
                        val perSubnet = effSplit?.let { SubnetCalculator.usableHosts(it) }

                        item(key = "split") {
                            SplitCard(
                                parentPrefix = info.prefix,
                                byHosts = byHosts,
                                onModeChange = { byHosts = it },
                                splitPrefix = splitPrefix.coerceIn(info.prefix, 32),
                                onSplitChange = { splitPrefix = it },
                                hostsText = hostsText,
                                onHostsChange = { hostsText = it },
                                hostsError = hostsError,
                                resolvedPrefix = effSplit,
                                count = count,
                                hostsPerSubnet = perSubnet,
                            )
                        }

                        if (effSplit != null && count != null) {
                            val shown = minOf(count, MAX_ROWS.toLong()).toInt()
                            items(count = shown, key = { it }) { index ->
                                SubnetRow(
                                    index = index,
                                    sub = SubnetCalculator.subnetAt(info.network, effSplit, index.toLong()),
                                )
                            }
                            if (count > shown) {
                                item(key = "more") {
                                    Text(
                                        text = "… und ${fmt(count - shown)} weitere Subnetze " +
                                            "(Anzeige auf ${fmt(shown.toLong())} begrenzt)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Neon.textSecondary,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item(key = "hint") {
                        Text(
                            text = "Gib eine gültige IPv4-Adresse und Netzmaske ein, " +
                                "z. B. 192.168.1.0 und /24 oder 255.255.255.0.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Neon.textSecondary,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bausteine
// ---------------------------------------------------------------------------

@Composable
private fun Header() {
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
        Text(
            text = "SUBNETZ-RECHNER",
            style = TextStyle(
                brush = Neon.accentGradient,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "// IPv4 · CIDR · VLSM",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            color = Neon.textFaint,
            letterSpacing = 2.sp,
        )
    }
}

/** Dunkle Karte mit feinem Rahmen – Grundbaustein des Designs. */
@Composable
private fun NeonCard(
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

@Composable
private fun neonFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
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

@Composable
private fun InputCard(
    ipText: String,
    onIpChange: (String) -> Unit,
    ipValid: Boolean,
    maskText: String,
    onMaskChange: (String) -> Unit,
    maskValid: Boolean,
) {
    NeonCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = ipText,
                onValueChange = onIpChange,
                label = { Text("IP-Adresse") },
                placeholder = { Text("192.168.1.0") },
                isError = ipText.isNotBlank() && !ipValid,
                supportingText = {
                    if (ipText.isNotBlank() && !ipValid) Text("Ungültige IPv4-Adresse")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = neonFieldColors(),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = maskText,
                onValueChange = onMaskChange,
                label = { Text("Netzmaske") },
                placeholder = { Text("/24 oder 255.255.255.0") },
                isError = maskText.isNotBlank() && !maskValid,
                supportingText = {
                    if (maskText.isNotBlank() && !maskValid) {
                        Text("Ungültige Maske (CIDR 0–32 oder Punktnotation)")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = neonFieldColors(),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(8, 16, 24, 25, 26, 27, 28, 30).forEach { p ->
                    AssistChip(
                        onClick = { onMaskChange("/$p") },
                        label = {
                            Text("/$p", fontFamily = FontFamily.Monospace)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Neon.surfaceHigh.copy(alpha = 0.5f),
                            labelColor = Neon.green,
                        ),
                        border = BorderStroke(1.dp, Neon.outline),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(info: SubnetInfo) {
    val sc = SubnetCalculator

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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = CardShape,
                ambientColor = Neon.pink,
                spotColor = Neon.green,
            )
            .clip(CardShape)
            .background(Brush.verticalGradient(listOf(Neon.surfaceHigh, Neon.surfaceRaised)))
            .border(1.2.dp, Neon.borderGradient(glowAlpha), CardShape)
            .padding(18.dp),
    ) {
        Column {
            Text(
                text = "${sc.ipToString(info.network)}/${info.prefix}",
                style = TextStyle(
                    brush = Neon.accentGradient,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val typeLabel = sc.classifyNetwork(info.network, info.prefix)
                val typeColor = when {
                    typeLabel.startsWith("Privat") -> Neon.green
                    typeLabel == "Öffentlich" -> Neon.pink
                    else -> Neon.blue
                }
                Badge(text = typeLabel, color = typeColor)
                Badge(text = "Klasse ${sc.ipClass(info.network)}", color = Neon.blue)
            }

            Spacer(Modifier.height(16.dp))
            BitVisualizer(network = info.network, prefix = info.prefix)
            Spacer(Modifier.height(16.dp))

            HorizontalDivider(color = Neon.outlineFaint)
            Spacer(Modifier.height(14.dp))

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ResultRow("Netzmaske", "${sc.ipToString(info.mask)}  (/${info.prefix})")
                    ResultRow("Wildcard", sc.ipToString(info.wildcard))
                    ResultRow("Netzadresse", sc.ipToString(info.network))
                    ResultRow("Broadcast", sc.ipToString(info.broadcast))
                    ResultRow("Erster Host", sc.ipToString(info.firstHost))
                    ResultRow("Letzter Host", sc.ipToString(info.lastHost))
                    ResultRow("Nutzbare Hosts", fmt(info.usableHosts), accent = Neon.green)
                    ResultRow("Adressen gesamt", fmt(info.totalAddresses))
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
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
 * Visualisiert die 32 Bit der Netzadresse: Netzanteil in Grün,
 * Hostanteil in Pink; gesetzte Bits leuchten, ungesetzte sind abgedunkelt.
 */
@Composable
private fun BitVisualizer(network: Long, prefix: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (p in 0 until 32) {
                val bitIndex = 31 - p
                val isNetworkBit = p < prefix
                val isSet = (network shr bitIndex) and 1L == 1L
                val base = if (isNetworkBit) Neon.green else Neon.pink
                val color = if (isSet) base else base.copy(alpha = 0.22f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
                if (p % 8 == 7 && p != 31) Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "■ Netzanteil · $prefix Bit",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Neon.green,
            )
            Text(
                text = "Hostanteil · ${32 - prefix} Bit ■",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Neon.pink,
            )
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, accent: Color = Neon.textPrimary) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitCard(
    parentPrefix: Int,
    byHosts: Boolean,
    onModeChange: (Boolean) -> Unit,
    splitPrefix: Int,
    onSplitChange: (Int) -> Unit,
    hostsText: String,
    onHostsChange: (String) -> Unit,
    hostsError: String?,
    resolvedPrefix: Int?,
    count: Long?,
    hostsPerSubnet: Long?,
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
                    label = { Text("Benötigte Hosts pro Subnetz") },
                    placeholder = { Text("z. B. 50") },
                    isError = hostsError != null,
                    supportingText = { if (hostsError != null) Text(hostsError) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = neonFieldColors(),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Slider(
                    value = splitPrefix.toFloat(),
                    onValueChange = { onSplitChange(it.roundToInt()) },
                    valueRange = parentPrefix.toFloat()..32f,
                    steps = (32 - parentPrefix - 1).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = Neon.pink,
                        activeTrackColor = Neon.green,
                        inactiveTrackColor = Neon.outline,
                        activeTickColor = Neon.surface,
                        inactiveTickColor = Neon.textFaint,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "/$parentPrefix",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = Neon.textSecondary,
                    )
                    Text(
                        "/32",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = Neon.textSecondary,
                    )
                }
            }

            if (resolvedPrefix != null && count != null && hostsPerSubnet != null) {
                val suffix = when (resolvedPrefix) {
                    31 -> " — /31: Punkt-zu-Punkt (RFC 3021)"
                    32 -> " — /32: Hostroute"
                    else -> ""
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "→ /$resolvedPrefix (${SubnetCalculator.ipToString(SubnetCalculator.prefixToMask(resolvedPrefix))}): " +
                        "${fmt(count)} " + (if (count == 1L) "Subnetz" else "Subnetze") +
                        " mit je ${fmt(hostsPerSubnet)} nutzbaren Hosts$suffix",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Neon.green,
                )
            }
        }
    }
}

@Composable
private fun SubnetRow(index: Int, sub: SubnetInfo) {
    val sc = SubnetCalculator
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
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Neon.textFaint,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${sc.ipToString(sub.network)}/${sub.prefix}",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = Neon.green,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Hosts: ${sc.ipToString(sub.firstHost)} – ${sc.ipToString(sub.lastHost)}" +
                    "  ·  BC: ${sc.ipToString(sub.broadcast)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Neon.textSecondary,
            )
        }
    }
}
