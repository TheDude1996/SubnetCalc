package de.subnetcalc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import de.subnetcalc.core.Ipv6Calculator as V6
import de.subnetcalc.core.SubnetCalculator as SC

class Ipv6CalculatorTest {

    private fun ip(s: String): BigInteger = V6.parse(s)!!
    private val two = BigInteger.valueOf(2)

    // ---- Parsen und Formatieren ----

    @Test
    fun `parst gueltige Adressen`() {
        assertEquals(BigInteger.ZERO, V6.parse("::"))
        assertEquals(BigInteger.ONE, V6.parse("::1"))
        assertEquals(two.pow(127), V6.parse("8000::"))
        assertEquals(ip("2001:db8:0:0:0:0:0:1"), V6.parse("2001:db8::1"))
        assertEquals(ip("2001:db8::1"), V6.parse(" 2001:DB8::1 "))
        assertEquals(ip("fe80::1"), V6.parse("fe80::1%eth0"))
        assertEquals(ip("::ffff:c0a8:101"), V6.parse("::ffff:192.168.1.1"))
        assertEquals(ip("1:2:3:4:5:6:c0a8:101"), V6.parse("1:2:3:4:5:6:192.168.1.1"))
        assertEquals(ip("1:2:3:4:5:6:7:0"), V6.parse("1:2:3:4:5:6:7::"))
    }

    @Test
    fun `lehnt ungueltige Adressen ab`() {
        assertNull(V6.parse(""))
        assertNull(V6.parse("192.168.1.1"))
        assertNull(V6.parse("1::2::3"))
        assertNull(V6.parse(":::"))
        assertNull(V6.parse("12345::"))
        assertNull(V6.parse("g::1"))
        assertNull(V6.parse("1:2:3:4:5:6:7"))
        assertNull(V6.parse("1:2:3:4:5:6:7:8:9"))
        assertNull(V6.parse("1:2:3:4:5:6:7::8"))
        assertNull(V6.parse("1:2:3:4:5:6:7:8::"))
        assertNull(V6.parse(":1:2:3:4:5:6:7"))
        assertNull(V6.parse("1.2.3.4::1"))
        assertNull(V6.parse("::1.2.3.4:5"))
        assertNull(V6.parse("::ffff:1.2.3.256"))
    }

    @Test
    fun `formatiert nach RFC 5952`() {
        assertEquals("::", V6.format(ip("0:0:0:0:0:0:0:0")))
        assertEquals("::1", V6.format(ip("0:0:0:0:0:0:0:1")))
        assertEquals("1::", V6.format(ip("1:0:0:0:0:0:0:0")))
        assertEquals(
            "2001:db8::ff00:42:8329",
            V6.format(ip("2001:0db8:0000:0000:0000:ff00:0042:8329")),
        )
        // Bei gleich langen Nullfolgen wird die erste verkürzt
        assertEquals("2001:db8::1:0:0:1", V6.format(ip("2001:db8:0:0:1:0:0:1")))
        // Eine einzelne Nullgruppe wird nicht verkürzt
        assertEquals("2001:db8:0:1:1:1:1:1", V6.format(ip("2001:db8:0:1:1:1:1:1")))
        assertEquals("::ffff:c0a8:101", V6.format(ip("::ffff:192.168.1.1")))
    }

    @Test
    fun `formatiert ausgeschrieben`() {
        assertEquals(
            "2001:0db8:0000:0000:0000:0000:0000:0001",
            V6.formatExpanded(ip("2001:db8::1")),
        )
    }

    @Test
    fun `parst Praefixlaengen`() {
        assertEquals(64, V6.parsePrefix("64"))
        assertEquals(64, V6.parsePrefix("/64"))
        assertEquals(0, V6.parsePrefix("0"))
        assertEquals(128, V6.parsePrefix("128"))
        assertNull(V6.parsePrefix("129"))
        assertNull(V6.parsePrefix("-1"))
        assertNull(V6.parsePrefix("ffff::"))
        assertNull(V6.parsePrefix(""))
    }

    // ---- Subnetz-Berechnung ----

    @Test
    fun `berechnet slash 64`() {
        val info = V6.calculate(ip("2001:db8:abcd:12::1"), 64)
        assertEquals("2001:db8:abcd:12::", V6.format(info.network))
        assertEquals("2001:db8:abcd:12:ffff:ffff:ffff:ffff", V6.format(info.last))
        assertEquals(two.pow(64), info.totalAddresses)
    }

    @Test
    fun `berechnet Praefix ausserhalb der Gruppengrenzen`() {
        val info = V6.calculate(ip("2001:db8:abcd:1234::1"), 60)
        assertEquals("2001:db8:abcd:1230::", V6.format(info.network))
        assertEquals("2001:db8:abcd:123f:ffff:ffff:ffff:ffff", V6.format(info.last))
    }

    @Test
    fun `behandelt slash 128 und slash 0`() {
        val host = V6.calculate(ip("2001:db8::5"), 128)
        assertEquals(host.network, host.last)
        assertEquals(BigInteger.ONE, host.totalAddresses)

        val all = V6.calculate(ip("2001:db8::5"), 0)
        assertEquals("::", V6.format(all.network))
        assertEquals("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", V6.format(all.last))
        assertEquals(two.pow(128), all.totalAddresses)
    }

    @Test
    fun `liefert Bits von links`() {
        val a = ip("8000::1")
        assertTrue(V6.bit(a, 0))
        assertFalse(V6.bit(a, 1))
        assertTrue(V6.bit(a, 127))
    }

    // ---- Aufteilung ----

    @Test
    fun `teilt slash 48 in slash 64`() {
        assertEquals(BigInteger.valueOf(65536), V6.subnetCount(48, 64))
        assertEquals(BigInteger.ONE, V6.subnetCount(64, 64))
        assertEquals(BigInteger.ZERO, V6.subnetCount(64, 48))

        val parent = ip("2001:db8::")
        assertEquals("2001:db8::", V6.format(V6.subnetAt(parent, 64, 0).network))
        assertEquals("2001:db8:0:1::", V6.format(V6.subnetAt(parent, 64, 1).network))
        assertEquals("2001:db8:0:ffff::", V6.format(V6.subnetAt(parent, 64, 65535).network))
        assertEquals(
            "2001:db8:0:ffff:ffff:ffff:ffff:ffff",
            V6.format(V6.subnetAt(parent, 64, 65535).last),
        )
    }

    @Test
    fun `findet Praefix fuer benoetigte Adressen`() {
        assertEquals(128, V6.prefixForAddresses(BigInteger.ONE))
        assertEquals(127, V6.prefixForAddresses(two))
        assertEquals(126, V6.prefixForAddresses(BigInteger.valueOf(3)))
        assertEquals(64, V6.prefixForAddresses(two.pow(64)))
        assertEquals(63, V6.prefixForAddresses(two.pow(64).add(BigInteger.ONE)))
        assertEquals(0, V6.prefixForAddresses(two.pow(128)))
        assertNull(V6.prefixForAddresses(two.pow(128).add(BigInteger.ONE)))
        assertNull(V6.prefixForAddresses(BigInteger.ZERO))
    }

    // ---- Klassifizierung ----

    @Test
    fun `klassifiziert Netze`() {
        assertEquals("Unique Local (privat)", V6.classifyNetwork(ip("fd12:3456:789a::"), 48))
        assertEquals("Unique Local (privat)", V6.classifyNetwork(ip("fc00::"), 7))
        assertEquals("Link-Local", V6.classifyNetwork(ip("fe80::"), 64))
        assertEquals("Dokumentation (RFC 3849)", V6.classifyNetwork(ip("2001:db8:1::"), 48))
        assertEquals("Global Unicast", V6.classifyNetwork(ip("2a00:1450:4001::"), 48))
        assertEquals("Multicast", V6.classifyNetwork(ip("ff02::"), 16))
        assertEquals("Loopback", V6.classifyNetwork(ip("::1"), 128))
        assertEquals("Unspezifiziert", V6.classifyNetwork(ip("::"), 128))
        assertEquals("IPv4-gemappt", V6.classifyNetwork(ip("::ffff:10.0.0.0"), 104))
        assertEquals("6to4", V6.classifyNetwork(ip("2002:c000:204::"), 48))
        assertEquals("Gemischt (mehrere Adressbereiche)", V6.classifyNetwork(ip("::"), 0))
        assertEquals("Gemischt (mehrere Adressbereiche)", V6.classifyNetwork(ip("fc00::"), 6))
        assertEquals("Reserviert (nicht zugewiesen)", V6.classifyNetwork(ip("4000::"), 3))
    }

    // ---- Vergleich zweier Adressen ----

    @Test
    fun `prueft gleiches Netz bei IPv6`() {
        val a = ip("2001:db8::1")
        val b = ip("2001:db8:0:1::1")
        assertEquals(63, V6.commonPrefixLength(a, b))
        assertTrue(V6.sameNetwork(a, b, 48))
        assertTrue(V6.sameNetwork(a, b, 63))
        assertFalse(V6.sameNetwork(a, b, 64))
        assertEquals(128, V6.commonPrefixLength(a, a))
        assertEquals(0, V6.commonPrefixLength(ip("::"), ip("8000::")))
        assertTrue(V6.sameNetwork(ip("::"), ip("ffff::"), 0))
    }

    @Test
    fun `prueft gleiches Netz bei IPv4`() {
        val a = SC.parseIpv4("192.168.1.10")!!
        val b = SC.parseIpv4("192.168.1.200")!!
        assertEquals(24, SC.commonPrefixLength(a, b))
        assertTrue(SC.sameNetwork(a, b, 24))
        assertFalse(SC.sameNetwork(a, b, 25))
        assertTrue(SC.sameNetwork(a, b, 0))
        assertEquals(32, SC.commonPrefixLength(a, a))
        assertEquals(0, SC.commonPrefixLength(SC.parseIpv4("10.0.0.1")!!, SC.parseIpv4("192.168.0.1")!!))
        assertEquals(
            15,
            SC.commonPrefixLength(SC.parseIpv4("172.16.0.1")!!, SC.parseIpv4("172.17.0.1")!!),
        )
        assertFalse(SC.sameNetwork(SC.parseIpv4("172.16.0.1")!!, SC.parseIpv4("172.17.0.1")!!, 16))
        assertTrue(SC.sameNetwork(SC.parseIpv4("172.16.0.1")!!, SC.parseIpv4("172.17.0.1")!!, 15))
    }
}
