package de.subnetcalc.core

/**
 * Alle Angaben zu einem IPv4-Subnetz. Adressen werden als vorzeichenlose
 * 32-Bit-Werte in einem Long gehalten (0..4294967295).
 */
data class SubnetInfo(
    val ip: Long,
    val prefix: Int,
    val network: Long,
    val broadcast: Long,
    val firstHost: Long,
    val lastHost: Long,
    val usableHosts: Long,
    val totalAddresses: Long,
    val mask: Long,
    val wildcard: Long,
)

object SubnetCalculator {

    /** Parst eine IPv4-Adresse in Punktnotation. Liefert null bei ungültiger Eingabe. */
    fun parseIpv4(text: String): Long? {
        val parts = text.trim().split(".")
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) return null
            val octet = part.toInt()
            if (octet > 255) return null
            value = (value shl 8) or octet.toLong()
        }
        return value
    }

    /**
     * Parst eine Netzmaske als Präfixlänge ("24", "/24") oder in
     * Punktnotation ("255.255.255.0"). Liefert null bei ungültiger Eingabe,
     * auch bei nicht zusammenhängenden Masken wie 255.0.255.0.
     */
    fun parsePrefixOrMask(text: String): Int? {
        val t = text.trim().removePrefix("/").trim()
        if (t.isEmpty()) return null
        val asInt = t.toIntOrNull()
        if (asInt != null) return if (asInt in 0..32) asInt else null
        val mask = parseIpv4(t) ?: return null
        return maskToPrefix(mask)
    }

    /** Wandelt eine Maske in die Präfixlänge um; null, wenn die Maske nicht zusammenhängend ist. */
    fun maskToPrefix(mask: Long): Int? {
        var seenZero = false
        var prefix = 0
        for (i in 31 downTo 0) {
            if ((mask shr i) and 1L == 1L) {
                if (seenZero) return null
                prefix++
            } else {
                seenZero = true
            }
        }
        return prefix
    }

    /** Liefert die Netzmaske zur Präfixlänge, z. B. 24 -> 255.255.255.0. */
    fun prefixToMask(prefix: Int): Long {
        require(prefix in 0..32) { "Präfix muss zwischen 0 und 32 liegen" }
        return if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    }

    /** Formatiert eine Adresse in Punktnotation. */
    fun ipToString(ip: Long): String =
        "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"

    /** Formatiert eine Adresse binär in Oktettgruppen, z. B. 11000000.10101000.00000001.00000000 */
    fun ipToBinaryString(ip: Long): String =
        (3 downTo 0).joinToString(".") { octet ->
            ((ip shr (octet * 8)) and 0xFF).toString(2).padStart(8, '0')
        }

    fun networkAddress(ip: Long, prefix: Int): Long = ip and prefixToMask(prefix)

    fun broadcastAddress(ip: Long, prefix: Int): Long =
        networkAddress(ip, prefix) or (prefixToMask(prefix).inv() and 0xFFFFFFFFL)

    /** Berechnet alle Kennwerte des Subnetzes, in dem die Adresse liegt. */
    fun calculate(ip: Long, prefix: Int): SubnetInfo {
        val mask = prefixToMask(prefix)
        val network = ip and mask
        val wildcard = mask.inv() and 0xFFFFFFFFL
        val broadcast = network or wildcard
        val total = 1L shl (32 - prefix)
        val (first, last, usable) = when {
            prefix >= 32 -> Triple(network, network, 1L)
            prefix == 31 -> Triple(network, broadcast, 2L) // RFC 3021: Punkt-zu-Punkt
            else -> Triple(network + 1, broadcast - 1, total - 2)
        }
        return SubnetInfo(
            ip = ip,
            prefix = prefix,
            network = network,
            broadcast = broadcast,
            firstHost = first,
            lastHost = last,
            usableHosts = usable,
            totalAddresses = total,
            mask = mask,
            wildcard = wildcard,
        )
    }

    /** Anzahl der Subnetze, wenn ein /parentPrefix-Netz in /newPrefix-Netze geteilt wird. */
    fun subnetCount(parentPrefix: Int, newPrefix: Int): Long {
        if (newPrefix < parentPrefix) return 0
        return 1L shl (newPrefix - parentPrefix)
    }

    /** Liefert das index-te Subnetz (0-basiert) bei Aufteilung des Netzes in /newPrefix-Netze. */
    fun subnetAt(parentNetwork: Long, newPrefix: Int, index: Long): SubnetInfo {
        val net = (parentNetwork + (index shl (32 - newPrefix))) and 0xFFFFFFFFL
        return calculate(net, newPrefix)
    }

    /** Historische Adressklasse (A–E). */
    fun ipClass(ip: Long): Char {
        val firstOctet = (ip shr 24) and 0xFF
        return when {
            firstOctet < 128 -> 'A'
            firstOctet < 192 -> 'B'
            firstOctet < 224 -> 'C'
            firstOctet < 240 -> 'D'
            else -> 'E'
        }
    }

    /** Adresstyp als deutsche Beschreibung (Privat, Öffentlich, Loopback, ...). */
    fun classify(ip: Long): String {
        fun inBlock(net: String, prefix: Int): Boolean {
            val netVal = parseIpv4(net)!!
            return (ip and prefixToMask(prefix)) == netVal
        }
        return when {
            inBlock("10.0.0.0", 8) -> "Privat (RFC 1918)"
            inBlock("172.16.0.0", 12) -> "Privat (RFC 1918)"
            inBlock("192.168.0.0", 16) -> "Privat (RFC 1918)"
            inBlock("127.0.0.0", 8) -> "Loopback"
            inBlock("169.254.0.0", 16) -> "Link-Local (APIPA)"
            inBlock("100.64.0.0", 10) -> "Carrier-Grade NAT (RFC 6598)"
            inBlock("224.0.0.0", 4) -> "Multicast"
            inBlock("240.0.0.0", 4) -> "Reserviert"
            else -> "Öffentlich"
        }
    }
}
