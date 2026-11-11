package uz.himoya.app.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsMessageTest {

    private val query = DnsMessage.buildQuery(0x1234, "www.google.com", DnsMessage.TYPE_A)

    /** Yuqori server javobiga o'xshash xabar: CNAME + berilgan manzillar. */
    private fun upstreamAnswer(q: ByteArray, type: Int, vararg addresses: ByteArray): ByteArray {
        val qEnd = DnsMessage.questionEnd(q)
        val out = java.io.ByteArrayOutputStream()
        out.write(q, 0, qEnd)
        // CNAME yozuvi: nom ko'rsatkichi, tur 5, rdata "a" + ildiz
        out.write(byteArrayOf(0xC0.toByte(), 0x0C, 0, 5, 0, 1, 0, 0, 0, 30, 0, 3, 1, 'a'.code.toByte(), 0))
        for (a in addresses) {
            out.write(byteArrayOf(0xC0.toByte(), 0x0C, 0, type.toByte(), 0, 1, 0, 0, 0, 30, 0, a.size.toByte()))
            out.write(a)
        }
        val bytes = out.toByteArray()
        bytes[2] = 0x81.toByte(); bytes[3] = 0x80.toByte()
        bytes[7] = (1 + addresses.size).toByte()
        return bytes
    }

    @Test
    fun buildsQueryThatParsesBack() {
        assertEquals("www.google.com", DnsPacket.questionName(query))
        assertEquals(DnsMessage.TYPE_A, DnsMessage.questionType(query))
        assertEquals(0x1234, DnsMessage.id(query))
        assertEquals(query.size, DnsMessage.questionEnd(query))
    }

    @Test
    fun nxDomainResponseKeepsIdAndQuestion() {
        val r = DnsMessage.response(query, DnsMessage.RCODE_NXDOMAIN)!!
        assertEquals(0x1234, DnsMessage.id(r))
        assertTrue(r[2].toInt() and 0x80 != 0) // javob
        assertTrue(r[2].toInt() and 0x01 != 0) // RD saqlangan
        assertEquals(3, r[3].toInt() and 0x0F)
        assertEquals(0, r[7].toInt()) // javoblar yo'q
        assertEquals("www.google.com", DnsPacket.questionName(r))
        assertEquals(query.size, r.size)
    }

    @Test
    fun addressResponseCarriesOnlyMatchingAddresses() {
        val v4 = byteArrayOf(216.toByte(), 239.toByte(), 38, 120)
        val v6 = ByteArray(16) { 1 }
        val r = DnsMessage.response(query, DnsMessage.RCODE_NOERROR, listOf(v4, v6))!!
        assertEquals(1, r[7].toInt())
        val parsed = DnsMessage.addresses(r, DnsMessage.TYPE_A)
        assertEquals(1, parsed.size)
        assertArrayEquals(v4, parsed[0])
    }

    @Test
    fun parsesAddressesAndSkipsCname() {
        val a1 = byteArrayOf(1, 2, 3, 4)
        val a2 = byteArrayOf(5, 6, 7, 8)
        val parsed = DnsMessage.addresses(upstreamAnswer(query, 1, a1, a2), DnsMessage.TYPE_A)
        assertEquals(2, parsed.size)
        assertArrayEquals(a1, parsed[0])
        assertArrayEquals(a2, parsed[1])
    }

    @Test
    fun detectsFamilyDnsBlock() {
        assertTrue(DnsMessage.isBlockedAnswer(upstreamAnswer(query, 1, byteArrayOf(0, 0, 0, 0))))
        assertFalse(DnsMessage.isBlockedAnswer(upstreamAnswer(query, 1, byteArrayOf(1, 2, 3, 4))))
        assertFalse(DnsMessage.isBlockedAnswer(upstreamAnswer(query, 1)))
        assertFalse(DnsMessage.isBlockedAnswer(DnsMessage.response(query, DnsMessage.RCODE_NXDOMAIN)!!))
        val q6 = DnsMessage.buildQuery(7, "example.com", DnsMessage.TYPE_AAAA)
        assertTrue(DnsMessage.isBlockedAnswer(upstreamAnswer(q6, 28, ByteArray(16))))
    }

    @Test
    fun brokenMessagesDoNotCrash() {
        assertNull(DnsMessage.response(ByteArray(5), DnsMessage.RCODE_NXDOMAIN))
        assertEquals(-1, DnsMessage.questionType(ByteArray(12)))
        val cut = upstreamAnswer(query, 1, byteArrayOf(1, 2, 3, 4)).copyOf(query.size + 20)
        assertNotNull(DnsMessage.addresses(cut, DnsMessage.TYPE_A))
        assertFalse(DnsMessage.isBlockedAnswer(ByteArray(3)))
    }
}
