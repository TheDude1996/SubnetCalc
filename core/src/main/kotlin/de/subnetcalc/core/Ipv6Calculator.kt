package de.subnetcalc.core

import java.math.BigInteger

/** Kennwerte eines IPv6-Subnetzes. Adressen sind 128-Bit-Werte. */
data class Subnet6Info(
    val ip: BigInteger,
    val prefix: Int,
    val network: BigInteger,
    val last: BigInteger,
    val totalAddresses: BigInteger,
)

object Ipv6Calculator {

    private val MAX: BigInteger = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
    private val GROUP_MASK: BigInteger = BigInteger.valueOf(0xFFFF)

    /**
     * Parst eine IPv6-Adresse, auch verkürzt ("::"), mit eingebetteter
     * IPv4-Adresse ("::ffff:192.168.1.1") oder Zonen-ID ("fe80::1%eth0").
     * Liefert null bei ungültiger Eingabe.
     */
    fun parse(text: String): BigInteger? {
        var t = text.trim()
        val zone = t.indexOf('%')
        if (zone >= 0) t = t.substring(0, zone)
        if (t.isEmpty() || !t.contains(':')) return null

        val dbl = t.indexOf("::")
        if (dbl != t.lastIndexOf("::")) return null

        val headParts: List<String>
        val tailParts: List<String>
        if (dbl >= 0) {
            headParts = t.substring(0, dbl).let { if (it.isEmpty()) emptyList() else it.split(":") }
            tailParts = t.substring(dbl + 2).let { if (it.isEmpty()) emptyList() else it.split(":") }
        } else {
            headParts = t.split(":")
            tailParts = emptyList()
        }

        // Eine eingebettete IPv4-Adresse darf nur ganz am Ende stehen.
        val head = toGroups(headParts, allowV4 = dbl < 0) ?: return null
        val tail = toGroups(tailParts, allowV4 = true) ?: return null

        val total = head.size + tail.size
        if (dbl >= 0) {
            if (total > 7) return null
        } else if (total != 8) {
            return null
        }

        val groups = head + List(8 - total) { 0 } + tail
        var value = BigInteger.ZERO
        for (g in groups) value = value.shiftLeft(16).or(BigInteger.valueOf(g.toLong()))
        return value
    }

    private fun toGroups(parts: List<String>, allowV4: Boolean): List<Int>? {
        val out = ArrayList<Int>(parts.size + 1)
        for ((i, part) in parts.withIndex()) {
            if (part.contains('.')) {
                if (!allowV4 || i != parts.lastIndex) return null
                val v4 = SubnetCalculator.parseIpv4(part) ?: return null
                out.add(((v4 shr 16) and 0xFFFF).toInt())
                out.add((v4 and 0xFFFF).toInt())
            } else {
                if (part.isEmpty() || part.length > 4) return null
                if (part.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
                out.add(part.toInt(16))
            }
        }
        return out
    }

    /** Präfixlänge "64" oder "/64" (0–128); null bei ungültiger Eingabe. */
    fun parsePrefix(text: String): Int? {
        val n = text.trim().removePrefix("/").trim().toIntOrNull() ?: return null
        return if (n in 0..128) n else null
    }

    private fun groups(ip: BigInteger): IntArray =
        IntArray(8) { i -> ip.shiftRight(16 * (7 - i)).and(GROUP_MASK).toInt() }

    /** Kurzschreibweise nach RFC 5952 (Kleinbuchstaben, längste Nullfolge als "::"). */
    fun format(ip: BigInteger): String {
        val g = groups(ip)
        var bestStart = -1
        var bestLen = 0
        var i = 0
        while (i < 8) {
            if (g[i] == 0) {
                var j = i
                while (j < 8 && g[j] == 0) j++
                if (j - i > bestLen) {
                    bestStart = i
                    bestLen = j - i
                }
                i = j
            } else {
                i++
            }
        }
        if (bestLen < 2) return g.joinToString(":") { it.toString(16) }
        val left = g.take(bestStart).joinToString(":") { it.toString(16) }
        val right = g.drop(bestStart + bestLen).joinToString(":") { it.toString(16) }
        return "$left::$right"
    }

    /** Ausgeschriebene Form mit allen 32 Hex-Ziffern. */
    fun formatExpanded(ip: BigInteger): String =
        groups(ip).joinToString(":") { it.toString(16).padStart(4, '0') }

    fun mask(prefix: Int): BigInteger {
        require(prefix in 0..128) { "Präfix muss zwischen 0 und 128 liegen" }
        return MAX.shiftLeft(128 - prefix).and(MAX)
    }

    /** Wert des Bits an Position [position] (0 = höchstwertiges Bit). */
    fun bit(ip: BigInteger, position: Int): Boolean = ip.testBit(127 - position)

    fun calculate(ip: BigInteger, prefix: Int): Subnet6Info {
        val m = mask(prefix)
        val network = ip.and(m)
        return Subnet6Info(
            ip = ip,
            prefix = prefix,
            network = network,
            last = network.or(MAX.xor(m)),
            totalAddresses = BigInteger.ONE.shiftLeft(128 - prefix),
        )
    }

    fun subnetCount(parentPrefix: Int, newPrefix: Int): BigInteger =
        if (newPrefix < parentPrefix) BigInteger.ZERO
        else BigInteger.ONE.shiftLeft(newPrefix - parentPrefix)

    fun subnetAt(parentNetwork: BigInteger, newPrefix: Int, index: Long): Subnet6Info =
        calculate(
            parentNetwork.add(BigInteger.valueOf(index).shiftLeft(128 - newPrefix)).and(MAX),
            newPrefix,
        )

    /**
     * Längster Präfix, dessen Subnetze mindestens [addresses] Adressen
     * enthalten. Bei IPv6 sind alle Adressen eines Subnetzes nutzbar.
     */
    fun prefixForAddresses(addresses: BigInteger): Int? {
        if (addresses < BigInteger.ONE) return null
        for (prefix in 128 downTo 0) {
            if (BigInteger.ONE.shiftLeft(128 - prefix) >= addresses) return prefix
        }
        return null
    }

    /** Anzahl der führenden Bits, in denen beide Adressen übereinstimmen (0–128). */
    fun commonPrefixLength(a: BigInteger, b: BigInteger): Int = 128 - a.xor(b).bitLength()

    fun sameNetwork(a: BigInteger, b: BigInteger, prefix: Int): Boolean {
        val m = mask(prefix)
        return a.and(m) == b.and(m)
    }

    private class Block(text: String, val prefix: Int, val label: String) {
        val net: BigInteger = parse(text)!!
        val last: BigInteger = net.or(MAX.xor(mask(prefix)))
    }

    // Speziellere Blöcke stehen vor den allgemeineren, die sie enthalten.
    private val blocks: List<Block> by lazy {
        listOf(
            Block("::", 128, "Unspezifiziert"),
            Block("::1", 128, "Loopback"),
            Block("::ffff:0:0", 96, "IPv4-gemappt"),
            Block("64:ff9b::", 96, "NAT64 (RFC 6052)"),
            Block("2001:db8::", 32, "Dokumentation (RFC 3849)"),
            Block("2002::", 16, "6to4"),
            Block("fc00::", 7, "Unique Local (privat)"),
            Block("fe80::", 10, "Link-Local"),
            Block("ff00::", 8, "Multicast"),
            Block("2000::", 3, "Global Unicast"),
        )
    }

    /**
     * Adresstyp eines ganzen Netzes: das Label eines Blocks gilt nur, wenn
     * das Netz vollständig darin liegt.
     */
    fun classifyNetwork(network: BigInteger, prefix: Int): String {
        val last = network.or(MAX.xor(mask(prefix)))
        blocks.firstOrNull { network >= it.net && last <= it.last }?.let { return it.label }
        val overlaps = blocks.any { it.net <= last && it.last >= network }
        return if (overlaps) "Gemischt (mehrere Adressbereiche)" else "Reserviert (nicht zugewiesen)"
    }
}
