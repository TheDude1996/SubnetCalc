package de.subnetcalc.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.subnetcalc.core.SubnetCalculator
import de.subnetcalc.core.SubnetInfo
import java.text.NumberFormat
import kotlin.math.roundToInt

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance()

private fun fmt(n: Long): String = numberFormat.format(n)

/** Maximal direkt gerenderte Subnetz-Zeilen; darüber wird ein Hinweis angezeigt. */
private const val MAX_ROWS = 65_536

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubnetScreen() {
    var ipText by rememberSaveable { mutableStateOf("192.168.1.0") }
    var maskText by rememberSaveable { mutableStateOf("/24") }
    var splitPrefix by rememberSaveable { mutableIntStateOf(26) }

    val ip = SubnetCalculator.parseIpv4(ipText)
    val prefix = SubnetCalculator.parsePrefixOrMask(maskText)
    val info = if (ip != null && prefix != null) SubnetCalculator.calculate(ip, prefix) else null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Lan,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Subnetz-Rechner", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                item(key = "result") { ResultCard(info) }

                if (info.prefix < 32) {
                    val effSplit = splitPrefix.coerceIn(info.prefix, 32)
                    val count = SubnetCalculator.subnetCount(info.prefix, effSplit)
                    val perSubnet = SubnetCalculator.calculate(info.network, effSplit).usableHosts

                    item(key = "split") {
                        SplitCard(
                            parentPrefix = info.prefix,
                            splitPrefix = effSplit,
                            onSplitChange = { splitPrefix = it },
                            count = count,
                            hostsPerSubnet = perSubnet,
                        )
                    }

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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            } else {
                item(key = "hint") {
                    Text(
                        text = "Gib eine gültige IPv4-Adresse und Netzmaske ein, " +
                            "z. B. 192.168.1.0 und /24 oder 255.255.255.0.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InputCard(
    ipText: String,
    onIpChange: (String) -> Unit,
    ipValid: Boolean,
    maskText: String,
    onMaskChange: (String) -> Unit,
    maskValid: Boolean,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        label = { Text("/$p") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(info: SubnetInfo) {
    val sc = SubnetCalculator
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "${sc.ipToString(info.network)}/${info.prefix}",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "${sc.classify(info.ip)} · Klasse ${sc.ipClass(info.ip)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Spacer(Modifier.height(12.dp))

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultRow("Netzmaske", "${sc.ipToString(info.mask)}  (/${info.prefix})")
                    ResultRow("Wildcard-Maske", sc.ipToString(info.wildcard))
                    ResultRow("Netzadresse", sc.ipToString(info.network))
                    ResultRow("Broadcast", sc.ipToString(info.broadcast))
                    ResultRow("Erster Host", sc.ipToString(info.firstHost))
                    ResultRow("Letzter Host", sc.ipToString(info.lastHost))
                    ResultRow("Nutzbare Hosts", fmt(info.usableHosts))
                    ResultRow("Adressen gesamt", fmt(info.totalAddresses))
                    ResultRow("Maske binär", sc.ipToBinaryString(info.mask), small = true)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, small: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
        Text(
            text = value,
            style = if (small) MaterialTheme.typography.bodySmall
            else MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SplitCard(
    parentPrefix: Int,
    splitPrefix: Int,
    onSplitChange: (Int) -> Unit,
    count: Long,
    hostsPerSubnet: Long,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "In Subnetze aufteilen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Neue Präfixlänge: /$splitPrefix — ${fmt(count)} " +
                    (if (count == 1L) "Subnetz" else "Subnetze") +
                    " mit je ${fmt(hostsPerSubnet)} nutzbaren Hosts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = splitPrefix.toFloat(),
                onValueChange = { onSplitChange(it.roundToInt()) },
                valueRange = parentPrefix.toFloat()..32f,
                steps = (32 - parentPrefix - 1).coerceAtLeast(0),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "/$parentPrefix",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "/32",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubnetRow(index: Int, sub: SubnetInfo) {
    val sc = SubnetCalculator
    Surface(
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "${sc.ipToString(sub.network)}/${sub.prefix}",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Hosts: ${sc.ipToString(sub.firstHost)} – ${sc.ipToString(sub.lastHost)}" +
                        "  ·  BC: ${sc.ipToString(sub.broadcast)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
