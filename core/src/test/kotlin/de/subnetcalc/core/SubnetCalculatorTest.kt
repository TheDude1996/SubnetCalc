package de.subnetcalc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import de.subnetcalc.core.SubnetCalculator as SC

class SubnetCalculatorTest {

    private fun ip(s: String): Long = SC.parseIpv4(s)!!

    // ---- Parsen ----

    @Test
    fun `parst gueltige IP-Adressen`() {
        assertEquals(0L, SC.parseIpv4("0.0.0.0"))
        assertEquals(0xFFFFFFFFL, SC.parseIpv4("255.255.255.255"))
        assertEquals(0xC0A80101L, SC.parseIpv4("192.168.1.1"))
        assertEquals(0x0A000001L, SC.parseIpv4(" 10.0.0.1 "))
    }

    @Test
    fun `lehnt ungueltige IP-Adressen ab`() {
        assertNull(SC.parseIpv4(""))
        assertNull(SC.parseIpv4("192.168.1"))
        assertNull(SC.parseIpv4("192.168.1.1.1"))
        assertNull(SC.parseIpv4("256.1.1.1"))
        assertNull(SC.parseIpv4("192.168.1.-1"))
        assertNull(SC.parseIpv4("192.168..1"))
        assertNull(SC.parseIpv4("a.b.c.d"))
        assertNull(SC.parseIpv4("192.168.1.1a"))
        assertNull(SC.parseIpv4("1922.168.1.1"))
    }

    @Test
    fun `parst Masken in allen Formaten`() {
        assertEquals(24, SC.parsePrefixOrMask("24"))
        assertEquals(24, SC.parsePrefixOrMask("/24"))
        assertEquals(24, SC.parsePrefixOrMask("255.255.255.0"))
        assertEquals(0, SC.parsePrefixOrMask("0"))
        assertEquals(0, SC.parsePrefixOrMask("0.0.0.0"))
        assertEquals(32, SC.parsePrefixOrMask("32"))
        assertEquals(32, SC.parsePrefixOrMask("255.255.255.255"))
        assertEquals(26, SC.parsePrefixOrMask("255.255.255.192"))
        assertEquals(12, SC.parsePrefixOrMask("255.240.0.0"))
    }

    @Test
    fun `lehnt ungueltige Masken ab`() {
        assertNull(SC.parsePrefixOrMask(""))
        assertNull(SC.parsePrefixOrMask("33"))
        assertNull(SC.parsePrefixOrMask("-1"))
        assertNull(SC.parsePrefixOrMask("255.0.255.0"))   // nicht zusammenhängend
        assertNull(SC.parsePrefixOrMask("0.255.255.255")) // nicht zusammenhängend
        assertNull(SC.parsePrefixOrMask("255.255.255.193"))
        assertNull(SC.parsePrefixOrMask("abc"))
    }

    // ---- Masken/Formatierung ----

    @Test
    fun `wandelt Praefix in Maske um`() {
        assertEquals(ip("255.255.255.0"), SC.prefixToMask(24))
        assertEquals(ip("255.255.255.192"), SC.prefixToMask(26))
        assertEquals(0L, SC.prefixToMask(0))
        assertEquals(ip("255.255.255.255"), SC.prefixToMask(32))
        assertEquals(ip("128.0.0.0"), SC.prefixToMask(1))
    }

    @Test
    fun `formatiert Adressen`() {
        assertEquals("192.168.1.1", SC.ipToString(0xC0A80101L))
        assertEquals("0.0.0.0", SC.ipToString(0))
        assertEquals("255.255.255.255", SC.ipToString(0xFFFFFFFFL))
        assertEquals(
            "11000000.10101000.00000001.00000000",
            SC.ipToBinaryString(ip("192.168.1.0"))
        )
    }

    // ---- Subnetz-Berechnung ----

    @Test
    fun `berechnet Standardbeispiel 192_168_1_130 slash 26`() {
        val info = SC.calculate(ip("192.168.1.130"), 26)
        assertEquals(ip("192.168.1.128"), info.network)
        assertEquals(ip("192.168.1.191"), info.broadcast)
        assertEquals(ip("192.168.1.129"), info.firstHost)
        assertEquals(ip("192.168.1.190"), info.lastHost)
        assertEquals(62L, info.usableHosts)
        assertEquals(64L, info.totalAddresses)
        assertEquals(ip("255.255.255.192"), info.mask)
        assertEquals(ip("0.0.0.63"), info.wildcard)
    }

    @Test
    fun `berechnet grosses Netz 10_1_2_3 slash 8`() {
        val info = SC.calculate(ip("10.1.2.3"), 8)
        assertEquals(ip("10.0.0.0"), info.network)
        assertEquals(ip("10.255.255.255"), info.broadcast)
        assertEquals(ip("10.0.0.1"), info.firstHost)
        assertEquals(ip("10.255.255.254"), info.lastHost)
        assertEquals(16_777_214L, info.usableHosts)
        assertEquals(16_777_216L, info.totalAddresses)
    }

    @Test
    fun `behandelt slash 31 nach RFC 3021`() {
        val info = SC.calculate(ip("192.168.0.1"), 31)
        assertEquals(ip("192.168.0.0"), info.network)
        assertEquals(ip("192.168.0.1"), info.broadcast)
        assertEquals(ip("192.168.0.0"), info.firstHost)
        assertEquals(ip("192.168.0.1"), info.lastHost)
        assertEquals(2L, info.usableHosts)
    }

    @Test
    fun `behandelt slash 32 als Hostroute`() {
        val info = SC.calculate(ip("8.8.8.8"), 32)
        assertEquals(ip("8.8.8.8"), info.network)
        assertEquals(ip("8.8.8.8"), info.broadcast)
        assertEquals(1L, info.usableHosts)
        assertEquals(1L, info.totalAddresses)
    }

    @Test
    fun `behandelt slash 0 als gesamten Adressraum`() {
        val info = SC.calculate(ip("1.2.3.4"), 0)
        assertEquals(0L, info.network)
        assertEquals(ip("255.255.255.255"), info.broadcast)
        assertEquals(4_294_967_296L, info.totalAddresses)
        assertEquals(4_294_967_294L, info.usableHosts)
    }

    // ---- Aufteilung in Subnetze ----

    @Test
    fun `zaehlt Subnetze korrekt`() {
        assertEquals(1L, SC.subnetCount(24, 24))
        assertEquals(4L, SC.subnetCount(24, 26))
        assertEquals(256L, SC.subnetCount(16, 24))
        assertEquals(0L, SC.subnetCount(24, 23))
        assertEquals(4_294_967_296L, SC.subnetCount(0, 32))
    }

    @Test
    fun `liefert alle Subnetze einer slash24-Aufteilung in slash26`() {
        val parent = ip("192.168.1.0")
        val expected = listOf("192.168.1.0", "192.168.1.64", "192.168.1.128", "192.168.1.192")
        for (i in 0 until 4) {
            val sub = SC.subnetAt(parent, 26, i.toLong())
            assertEquals(expected[i], SC.ipToString(sub.network))
            assertEquals(62L, sub.usableHosts)
        }
        // letztes Subnetz endet am Broadcast des Elternnetzes
        assertEquals(ip("192.168.1.255"), SC.subnetAt(parent, 26, 3).broadcast)
    }

    @Test
    fun `liefert Subnetze auch bei grossen Indizes`() {
        // 10.0.0.0/8 in /24 -> 65536 Subnetze; das letzte ist 10.255.255.0/24
        val parent = ip("10.0.0.0")
        val last = SC.subnetAt(parent, 24, 65535L)
        assertEquals(ip("10.255.255.0"), last.network)
        assertEquals(ip("10.255.255.255"), last.broadcast)
    }

    // ---- Klassifizierung ----

    @Test
    fun `bestimmt Adressklassen`() {
        assertEquals('A', SC.ipClass(ip("10.0.0.1")))
        assertEquals('B', SC.ipClass(ip("172.16.0.1")))
        assertEquals('C', SC.ipClass(ip("192.168.1.1")))
        assertEquals('D', SC.ipClass(ip("224.0.0.1")))
        assertEquals('E', SC.ipClass(ip("250.0.0.1")))
    }

    @Test
    fun `klassifiziert Adresstypen`() {
        assertEquals("Privat (RFC 1918)", SC.classify(ip("10.1.2.3")))
        assertEquals("Privat (RFC 1918)", SC.classify(ip("172.16.5.5")))
        assertEquals("Privat (RFC 1918)", SC.classify(ip("172.31.255.255")))
        assertEquals("Öffentlich", SC.classify(ip("172.32.0.1")))
        assertEquals("Privat (RFC 1918)", SC.classify(ip("192.168.99.1")))
        assertEquals("Loopback", SC.classify(ip("127.0.0.1")))
        assertEquals("Link-Local (APIPA)", SC.classify(ip("169.254.1.1")))
        assertEquals("Carrier-Grade NAT (RFC 6598)", SC.classify(ip("100.64.0.1")))
        assertEquals("Multicast", SC.classify(ip("239.255.255.250")))
        assertEquals("Reserviert", SC.classify(ip("240.0.0.1")))
        assertEquals("Öffentlich", SC.classify(ip("8.8.8.8")))
    }
}
