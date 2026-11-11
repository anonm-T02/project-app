package uz.himoya.app.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsPacketTest {

    /** "example.com" uchun A so'rovi. */
    private fun dnsPayload(): ByteArray {
        val header = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
        val name = byteArrayOf(7) + "example".toByteArray() + byteArrayOf(3) + "com".toByteArray() + byteArrayOf(0)
        return header + name + byteArrayOf(0, 1, 0, 1)
    }

    private fun ipUdpPacket(payload: ByteArray, dstPort: Int = 53, protocol: Int = 17): ByteArray {
        val total = 20 + 8 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total shr 8).toByte(); p[3] = total.toByte()
        p[8] = 64
        p[9] = protocol.toByte()
        byteArrayOf(10, 111, 222.toByte(), 1).copyInto(p, 12)
        byteArrayOf(10, 111, 222.toByte(), 2).copyInto(p, 16)
        p[20] = 0xC0.toByte(); p[21] = 0x01 // src port 49153
        p[22] = (dstPort shr 8).toByte(); p[23] = dstPort.toByte()
        val udpLen = 8 + payload.size
        p[24] = (udpLen shr 8).toByte(); p[25] = udpLen.toByte()
        payload.copyInto(p, 28)
        return p
    }

    @Test
    fun parsesDnsQuery() {
        val payload = dnsPayload()
        val packet = ipUdpPacket(payload)
        val q = DnsPacket.parseQuery(packet, packet.size)
        assertNotNull(q)
        assertEquals(49153, q!!.srcPort)
        assertEquals(53, q.dstPort)
        assertArrayEquals(payload, q.payload)
        assertEquals("example.com", DnsPacket.questionName(q.payload))
    }

    @Test
    fun ignoresNonDnsTraffic() {
        val payload = dnsPayload()
        assertNull(DnsPacket.parseQuery(ipUdpPacket(payload, dstPort = 443), 20 + 8 + payload.size))
        assertNull(DnsPacket.parseQuery(ipUdpPacket(payload, protocol = 6), 20 + 8 + payload.size))
        assertNull(DnsPacket.parseQuery(ByteArray(10), 10))
        val ipv6 = ipUdpPacket(payload).also { it[0] = 0x60 }
        assertNull(DnsPacket.parseQuery(ipv6, ipv6.size))
    }

    @Test
    fun rejectsTruncatedPacket() {
        val packet = ipUdpPacket(dnsPayload())
        assertNull(DnsPacket.parseQuery(packet, packet.size - 5))
    }

    @Test
    fun buildsResponseWithSwappedAddresses() {
        val packet = ipUdpPacket(dnsPayload())
        val q = DnsPacket.parseQuery(packet, packet.size)!!
        val answer = ByteArray(40) { it.toByte() }
        val out = DnsPacket.buildResponse(q, answer, answer.size)

        assertEquals(20 + 8 + 40, out.size)
        assertArrayEquals(q.dstIp, out.copyOfRange(12, 16))
        assertArrayEquals(q.srcIp, out.copyOfRange(16, 20))
        assertEquals(53, ((out[20].toInt() and 0xFF) shl 8) or (out[21].toInt() and 0xFF))
        assertEquals(49153, ((out[22].toInt() and 0xFF) shl 8) or (out[23].toInt() and 0xFF))
        assertArrayEquals(answer, out.copyOfRange(28, out.size))
        // To'g'ri checksum bilan sarlavha yig'indisi 0 bo'ladi.
        assertEquals(0, DnsPacket.ipChecksum(out, 20))
    }
}
