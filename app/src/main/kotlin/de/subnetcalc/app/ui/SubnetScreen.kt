package de.subnetcalc.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.subnetcalc.app.ui.theme.Neon
import de.subnetcalc.core.Ipv6Calculator
import de.subnetcalc.core.Subnet6Info
import de.subnetcalc.core.SubnetCalculator
import de.subnetcalc.core.SubnetInfo
import java.math.BigInteger

/** Maximal direkt gerenderte Subnetz-Zeilen; darüber wird ein Hinweis angezeigt. */
private const val MAX_ROWS = 65_536

private val V4_CHIPS = listOf(8, 16, 24, 25, 26, 27, 28, 30)
private val V6_CHIPS = listOf(32, 48, 56, 60, 64, 96, 127, 128)

/** Eingaben der Aufteilen-Karte, gemeinsam für IPv4 und IPv6. */
private class SplitInputs(
    val splitPrefix: Int,
    val byHosts: Boolean,
    val hostsText: String,
    val onSplitChange: (Int) -> Unit,
    val onModeChange: (Boolean) -> Unit,
    val onHostsChange: (String) -> Unit,
)

@Composable
fun SubnetScreen() {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    var ipText by rememberSaveable { mutableStateOf("192.168.1.0") }
    var maskText by rememberSaveable { mutableStateOf("/24") }
    var splitPrefix by rememberSaveable { mutableIntStateOf(26) }
    var byHosts by rememberSaveable { mutableStateOf(false) }
    var hostsText by rememberSaveable { mutableStateOf("") }

    var cmpA by rememberSaveable { mutableStateOf("192.168.1.10") }
    var cmpB by rememberSaveable { mutableStateOf("192.168.1.200") }
    var cmpMask by rememberSaveable { mutableStateOf("/24") }

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
                item(key = "tabs") { TabSwitch(tab = tab, onTabChange = { tab = it }) }

                if (tab == 0) {
                    val split = SplitInputs(
                        splitPrefix = splitPrefix,
                        byHosts = byHosts,
                        hostsText = hostsText,
                        onSplitChange = { splitPrefix = it },
                        onModeChange = { byHosts = it },
                        onHostsChange = { hostsText = it },
                    )
                    calculatorSections(
                        ipText = ipText,
                        onIpChange = { ipText = it },
                        maskText = maskText,
                        onMaskChange = { maskText = it },
                        split = split,
                    )
                } else {
                    compareSections(
                        aText = cmpA,
                        onAChange = { cmpA = it },
                        bText = cmpB,
                        onBChange = { cmpB = it },
                        maskText = cmpMask,
                        onMaskChange = { cmpMask = it },
                    )
                }
            }
        }
    }
}

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
            text = "// IPv4 · IPv6 · CIDR · VLSM",
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            color = Neon.textFaint,
            letterSpacing = 2.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabSwitch(tab: Int, onTabChange: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val colors = SegmentedButtonDefaults.colors(
            activeContainerColor = Neon.pinkFaint,
            activeContentColor = Neon.pink,
            activeBorderColor = Neon.pink,
            inactiveContainerColor = Color.Transparent,
            inactiveContentColor = Neon.textSecondary,
            inactiveBorderColor = Neon.outline,
        )
        listOf("Subnetz berechnen", "Gleiches Netz?").forEachIndexed { index, label ->
            SegmentedButton(
                selected = tab == index,
                onClick = { onTabChange(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                colors = colors,
            ) { Text(label) }
        }
    }
}

// ---------------------------------------------------------------------------
// Reiter 1: Subnetz berechnen
// ---------------------------------------------------------------------------

private fun LazyListScope.calculatorSections(
    ipText: String,
    onIpChange: (String) -> Unit,
    maskText: String,
    onMaskChange: (String) -> Unit,
    split: SplitInputs,
) {
    val isV6 = ipText.contains(':')
    val ip4 = if (isV6) null else SubnetCalculator.parseIpv4(ipText)
    val ip6 = if (isV6) Ipv6Calculator.parse(ipText) else null
    val prefix = if (isV6) Ipv6Calculator.parsePrefix(maskText)
    else SubnetCalculator.parsePrefixOrMask(maskText)

    item(key = "input") {
        NeonCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AddressField(
                    value = ipText,
                    onValueChange = onIpChange,
                    label = "IP-Adresse (IPv4 oder IPv6)",
                    placeholder = "192.168.1.0 oder 2001:db8::1",
                    valid = ip4 != null || ip6 != null,
                    isV6 = isV6,
                )
                MaskField(
                    value = maskText,
                    onValueChange = onMaskChange,
                    valid = prefix != null,
                    isV6 = isV6,
                )
                PrefixChips(if (isV6) V6_CHIPS else V4_CHIPS, onPick = { onMaskChange("/$it") })
            }
        }
    }

    when {
        ip4 != null && prefix != null -> ipv4Sections(SubnetCalculator.calculate(ip4, prefix), split)
        ip6 != null && prefix != null -> ipv6Sections(Ipv6Calculator.calculate(ip6, prefix), split)
        else -> item(key = "hint") {
            HintText(
                "Gib eine gültige Adresse und Netzmaske ein, z. B. 192.168.1.0 mit /24 " +
                    "oder 255.255.255.0, oder 2001:db8::1 mit /64."
            )
        }
    }
}

private fun LazyListScope.ipv4Sections(info: SubnetInfo, split: SplitInputs) {
    val sc = SubnetCalculator
    item(key = "result") { HeroCard4(info) }
    if (info.prefix >= 32) return

    val hostsRequired = split.hostsText.trim().toLongOrNull()
    val hostsPrefix = hostsRequired?.let { sc.prefixForHosts(it) }
    val hostsError: String? = when {
        !split.byHosts || split.hostsText.isBlank() -> null
        hostsRequired == null || hostsPrefix == null ->
            "Ungültige Anzahl (1 bis ${fmt(4_294_967_294L)})"
        hostsPrefix < info.prefix ->
            "Passt nicht in /${info.prefix} – dort sind höchstens ${fmt(info.usableHosts)} Hosts möglich"
        else -> null
    }
    val effSplit: Int? = if (split.byHosts) {
        if (split.hostsText.isNotBlank() && hostsError == null) hostsPrefix else null
    } else {
        split.splitPrefix.coerceIn(info.prefix, 32)
    }
    val count = effSplit?.let { sc.subnetCount(info.prefix, it) }

    val summary = if (effSplit != null && count != null) {
        val suffix = when (effSplit) {
            31 -> " — /31: Punkt-zu-Punkt (RFC 3021)"
            32 -> " — /32: Hostroute"
            else -> ""
        }
        "→ /$effSplit (${sc.ipToString(sc.prefixToMask(effSplit))}): ${fmt(count)} " +
            (if (count == 1L) "Subnetz" else "Subnetze") +
            " mit je ${fmt(sc.usableHosts(effSplit))} nutzbaren Hosts$suffix"
    } else null

    item(key = "split") {
        SplitCard(
            parentPrefix = info.prefix,
            maxPrefix = 32,
            byHosts = split.byHosts,
            onModeChange = split.onModeChange,
            splitPrefix = split.splitPrefix.coerceIn(info.prefix, 32),
            onSplitChange = split.onSplitChange,
            hostsLabel = "Benötigte Hosts pro Subnetz",
            hostsText = split.hostsText,
            onHostsChange = split.onHostsChange,
            hostsError = hostsError,
            summary = summary,
        )
    }

    if (effSplit != null && count != null) {
        val shown = minOf(count, MAX_ROWS.toLong()).toInt()
        items(count = shown, key = { it }) { index ->
            val sub = sc.subnetAt(info.network, effSplit, index.toLong())
            SubnetRow(
                index = index,
                title = "${sc.ipToString(sub.network)}/${sub.prefix}",
                detail = "Hosts: ${sc.ipToString(sub.firstHost)} – ${sc.ipToString(sub.lastHost)}" +
                    "  ·  BC: ${sc.ipToString(sub.broadcast)}",
            )
        }
        if (count > shown) {
            item(key = "more") { MoreRowsHint(fmt(count - shown), shown) }
        }
    }
}

private fun LazyListScope.ipv6Sections(info: Subnet6Info, split: SplitInputs) {
    val v6 = Ipv6Calculator
    item(key = "result") { HeroCard6(info) }
    if (info.prefix >= 128) return

    val required = split.hostsText.trim().toBigIntegerOrNull()
    val hostsPrefix = required?.let { v6.prefixForAddresses(it) }
    val hostsError: String? = when {
        !split.byHosts || split.hostsText.isBlank() -> null
        required == null || hostsPrefix == null -> "Ungültige Anzahl"
        hostsPrefix < info.prefix ->
            "Passt nicht in /${info.prefix} – dort gibt es ${fmtBig(info.totalAddresses)} Adressen"
        else -> null
    }
    val effSplit: Int? = if (split.byHosts) {
        if (split.hostsText.isNotBlank() && hostsError == null) hostsPrefix else null
    } else {
        split.splitPrefix.coerceIn(info.prefix, 128)
    }
    val count = effSplit?.let { v6.subnetCount(info.prefix, it) }

    val summary = if (effSplit != null && count != null) {
        "→ /$effSplit: ${fmtBig(count)} " +
            (if (count == BigInteger.ONE) "Subnetz" else "Subnetze") +
            " mit je ${fmtBig(BigInteger.ONE.shiftLeft(128 - effSplit))} Adressen"
    } else null

    item(key = "split") {
        SplitCard(
            parentPrefix = info.prefix,
            maxPrefix = 128,
            byHosts = split.byHosts,
            onModeChange = split.onModeChange,
            splitPrefix = split.splitPrefix.coerceIn(info.prefix, 128),
            onSplitChange = split.onSplitChange,
            hostsLabel = "Benötigte Adressen pro Subnetz",
            hostsText = split.hostsText,
            onHostsChange = split.onHostsChange,
            hostsError = hostsError,
            summary = summary,
        )
    }

    if (effSplit != null && count != null) {
        val shown = count.min(BigInteger.valueOf(MAX_ROWS.toLong())).toInt()
        items(count = shown, key = { it }) { index ->
            val sub = v6.subnetAt(info.network, effSplit, index.toLong())
            SubnetRow(
                index = index,
                title = "${v6.format(sub.network)}/${sub.prefix}",
                detail = "bis ${v6.format(sub.last)}",
            )
        }
        if (count > BigInteger.valueOf(shown.toLong())) {
            item(key = "more") {
                MoreRowsHint(fmtBig(count.subtract(BigInteger.valueOf(shown.toLong()))), shown)
            }
        }
    }
}

@Composable
private fun HeroCard4(info: SubnetInfo) {
    val sc = SubnetCalculator
    HeroContainer {
        Column {
            GradientHeadline("${sc.ipToString(info.network)}/${info.prefix}", fontSize = 27.sp)
            Spacer(Modifier.height(10.dp))
            val typeLabel = sc.classifyNetwork(info.network, info.prefix)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(text = "IPv4", color = Neon.blue)
                Badge(text = "Klasse ${sc.ipClass(info.network)}", color = Neon.blue)
            }
            Spacer(Modifier.height(8.dp))
            Badge(text = typeLabel, color = typeColor(typeLabel))

            Spacer(Modifier.height(16.dp))
            BitGrid(totalBits = 32, groupSize = 8, cellHeight = 16.dp) { p ->
                bitColor(
                    base = if (p < info.prefix) Neon.green else Neon.pink,
                    isSet = (info.network shr (31 - p)) and 1L == 1L,
                )
            }
            Spacer(Modifier.height(6.dp))
            Legend(
                "■ Netzanteil · ${info.prefix} Bit" to Neon.green,
                "■ Hostanteil · ${32 - info.prefix} Bit" to Neon.pink,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Neon.outlineFaint)
            Spacer(Modifier.height(14.dp))

            ResultRows {
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

@Composable
private fun HeroCard6(info: Subnet6Info) {
    val v6 = Ipv6Calculator
    HeroContainer {
        Column {
            GradientHeadline("${v6.format(info.network)}/${info.prefix}", fontSize = 19.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val typeLabel = v6.classifyNetwork(info.network, info.prefix)
                Badge(text = "IPv6", color = Neon.blue)
                Badge(text = typeLabel, color = typeColor(typeLabel))
            }

            Spacer(Modifier.height(16.dp))
            BitGrid(totalBits = 128, groupSize = 16, cellHeight = 10.dp) { p ->
                bitColor(
                    base = if (p < info.prefix) Neon.green else Neon.pink,
                    isSet = v6.bit(info.network, p),
                )
            }
            Spacer(Modifier.height(6.dp))
            Legend(
                "■ Netzanteil · ${info.prefix} Bit" to Neon.green,
                "■ Hostanteil · ${128 - info.prefix} Bit" to Neon.pink,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Neon.outlineFaint)
            Spacer(Modifier.height(14.dp))

            ResultRows {
                StackedRow("Erste Adresse", v6.format(info.network))
                StackedRow("Letzte Adresse", v6.format(info.last))
                StackedRow("Eingabe ausgeschrieben", v6.formatExpanded(info.ip))
                StackedRow("Adressen gesamt", fmtBig(info.totalAddresses), accent = Neon.green)
                if (info.prefix < 64) {
                    StackedRow(
                        "Enthaltene /64-Netze",
                        fmtBig(BigInteger.ONE.shiftLeft(64 - info.prefix)),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reiter 2: Gleiches Netz?
// ---------------------------------------------------------------------------

/** Ergebnis des Vergleichs, unabhängig von der IP-Version aufbereitet. */
private class Comparison(
    val same: Boolean,
    val prefix: Int,
    val totalBits: Int,
    val commonBits: Int,
    val identical: Boolean,
    val netA: String,
    val netB: String,
    val notes: List<String>,
    val bitA: (Int) -> Boolean,
    val bitB: (Int) -> Boolean,
)

private fun compare4(a: Long, b: Long, prefix: Int): Comparison {
    val sc = SubnetCalculator
    val infoA = sc.calculate(a, prefix)
    val infoB = sc.calculate(b, prefix)
    val notes = buildList {
        if (prefix < 31) {
            for ((name, addr, info) in listOf(Triple("A", a, infoA), Triple("B", b, infoB))) {
                if (addr == info.network) add("Adresse $name ist die Netzadresse und nicht als Host nutzbar.")
                if (addr == info.broadcast) add("Adresse $name ist die Broadcast-Adresse und nicht als Host nutzbar.")
            }
        }
    }
    return Comparison(
        same = sc.sameNetwork(a, b, prefix),
        prefix = prefix,
        totalBits = 32,
        commonBits = sc.commonPrefixLength(a, b),
        identical = a == b,
        netA = "${sc.ipToString(infoA.network)}/$prefix",
        netB = "${sc.ipToString(infoB.network)}/$prefix",
        notes = notes,
        bitA = { p -> (a shr (31 - p)) and 1L == 1L },
        bitB = { p -> (b shr (31 - p)) and 1L == 1L },
    )
}

private fun compare6(a: BigInteger, b: BigInteger, prefix: Int): Comparison {
    val v6 = Ipv6Calculator
    return Comparison(
        same = v6.sameNetwork(a, b, prefix),
        prefix = prefix,
        totalBits = 128,
        commonBits = v6.commonPrefixLength(a, b),
        identical = a == b,
        netA = "${v6.format(v6.calculate(a, prefix).network)}/$prefix",
        netB = "${v6.format(v6.calculate(b, prefix).network)}/$prefix",
        notes = emptyList(),
        bitA = { p -> v6.bit(a, p) },
        bitB = { p -> v6.bit(b, p) },
    )
}

private fun LazyListScope.compareSections(
    aText: String,
    onAChange: (String) -> Unit,
    bText: String,
    onBChange: (String) -> Unit,
    maskText: String,
    onMaskChange: (String) -> Unit,
) {
    val aV6 = aText.contains(':')
    val bV6 = bText.contains(':')
    val a4 = if (aV6) null else SubnetCalculator.parseIpv4(aText)
    val a6 = if (aV6) Ipv6Calculator.parse(aText) else null
    val b4 = if (bV6) null else SubnetCalculator.parseIpv4(bText)
    val b6 = if (bV6) Ipv6Calculator.parse(bText) else null
    val aValid = a4 != null || a6 != null
    val bValid = b4 != null || b6 != null
    val prefix = if (aV6) Ipv6Calculator.parsePrefix(maskText)
    else SubnetCalculator.parsePrefixOrMask(maskText)

    item(key = "cmpInput") {
        NeonCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AddressField(
                    value = aText,
                    onValueChange = onAChange,
                    label = "Adresse A",
                    placeholder = "192.168.1.10",
                    valid = aValid,
                    isV6 = aV6,
                )
                AddressField(
                    value = bText,
                    onValueChange = onBChange,
                    label = "Adresse B",
                    placeholder = "192.168.1.200",
                    valid = bValid,
                    isV6 = bV6,
                )
                MaskField(
                    value = maskText,
                    onValueChange = onMaskChange,
                    valid = prefix != null,
                    isV6 = aV6,
                )
                PrefixChips(if (aV6) V6_CHIPS else V4_CHIPS, onPick = { onMaskChange("/$it") })
            }
        }
    }

    val comparison: Comparison? = when {
        prefix == null -> null
        a4 != null && b4 != null -> compare4(a4, b4, prefix)
        a6 != null && b6 != null -> compare6(a6, b6, prefix)
        else -> null
    }

    when {
        comparison != null -> item(key = "verdict") { VerdictCard(comparison) }
        aValid && bValid && aV6 != bV6 -> item(key = "cmpHint") {
            HintText("Adresse A und B müssen dieselbe IP-Version haben (beide IPv4 oder beide IPv6).")
        }
        else -> item(key = "cmpHint") {
            HintText("Gib zwei gültige Adressen und eine Netzmaske ein, um zu prüfen, ob beide im selben Netz liegen.")
        }
    }
}

@Composable
private fun VerdictCard(c: Comparison) {
    val tint = if (c.same) Neon.green else Neon.pink
    HeroContainer(tint = tint) {
        Column {
            Text(
                text = if (c.same) "✓ IM SELBEN NETZ" else "✗ NICHT IM SELBEN NETZ",
                style = TextStyle(
                    color = tint,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (c.same) {
                    "Beide Adressen liegen bei /${c.prefix} im selben Netz und erreichen sich direkt."
                } else {
                    "Die Adressen liegen bei /${c.prefix} in verschiedenen Netzen; " +
                        "für die Kommunikation ist ein Router nötig."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Neon.textSecondary,
            )

            Spacer(Modifier.height(16.dp))
            val cell = if (c.totalBits == 32) 14.dp else 8.dp
            val group = if (c.totalBits == 32) 8 else 16
            for ((name, bitAt) in listOf("A" to c.bitA, "B" to c.bitB)) {
                Text(
                    text = "Adresse $name",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Neon.textFaint,
                )
                Spacer(Modifier.height(3.dp))
                BitGrid(totalBits = c.totalBits, groupSize = group, cellHeight = cell) { p ->
                    val base = when {
                        p >= c.prefix -> Neon.blue
                        c.bitA(p) == c.bitB(p) -> Neon.green
                        else -> Neon.pink
                    }
                    bitColor(base = base, isSet = bitAt(p), dimAlpha = if (base == Neon.pink) 0.45f else 0.22f)
                }
                Spacer(Modifier.height(8.dp))
            }
            Legend(
                "■ gleich" to Neon.green,
                "■ abweichend" to Neon.pink,
                "■ Hostanteil" to Neon.blue,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Neon.outlineFaint)
            Spacer(Modifier.height(14.dp))

            ResultRows {
                StackedRow("Netz von A", c.netA, accent = if (c.same) Neon.green else Neon.textPrimary)
                StackedRow("Netz von B", c.netB, accent = if (c.same) Neon.green else Neon.pink)
                StackedRow(
                    "Übereinstimmende führende Bits",
                    "${c.commonBits} von ${c.totalBits}",
                )
                when {
                    c.identical -> StackedRow("Hinweis", "Beide Adressen sind identisch.")
                    c.same -> StackedRow(
                        "Getrennte Netze ab",
                        "/${c.commonBits + 1} oder länger",
                    )
                    else -> StackedRow(
                        "Im selben Netz ab",
                        "/${c.commonBits} oder kürzer",
                    )
                }
            }

            if (c.notes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                for (note in c.notes) {
                    Text(
                        text = "⚠ $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.pink,
                    )
                }
            }
        }
    }
}
