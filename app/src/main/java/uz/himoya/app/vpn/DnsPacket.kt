package uz.himoya.app.vpn

/**
 * Tunneldan o'qilgan bitta DNS so'rovi (IPv4 + UDP).
 * Faqat sarlavhadagi manzillar va DNS yuklamasi saqlanadi.
 */
class DnsQuery(
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray,
)

/**
 * IPv4/UDP paketlarni ajratish va javob paketini yig'ish.
 * Android'ga bog'liq emas, shuning uchun oddiy unit test bilan tekshiriladi.
 */
object DnsPacket {
    private const val IPV4_HEADER = 20
    private const val UDP_HEADER = 8
    private const val PROTOCOL_UDP = 17
    private const val DNS_PORT = 53
    private const val DNS_HEADER = 12

    /** Paket IPv4 + UDP + 53-port bo'lsa so'rovni qaytaradi, aks holda null. */
    fun parseQuery(buf: ByteArray, len: Int): DnsQuery? {
        if (len < IPV4_HEADER + UDP_HEADER + DNS_HEADER || len > buf.size) return null
        val first = buf[0].toInt() and 0xFF
        if (first shr 4 != 4) return null
        val ihl = (first and 0x0F) * 4
        if (ihl < IPV4_HEADER || len < ihl + UDP_HEADER + DNS_HEADER) return null
        if ((buf[9].toInt() and 0xFF) != PROTOCOL_UDP) return null
        // Bo'laklangan (fragment) paketlarni qayta ishlamaymiz.
        if ((u16(buf, 6) and 0x3FFF) != 0) return null

        val srcPort = u16(buf, ihl)
        val dstPort = u16(buf, ihl + 2)
        if (dstPort != DNS_PORT) return null
        val payloadLen = u16(buf, ihl + 4) - UDP_HEADER
        if (payloadLen < DNS_HEADER || ihl + UDP_HEADER + payloadLen > len) return null

        val start = ihl + UDP_HEADER
        return DnsQuery(
            srcIp = buf.copyOfRange(12, 16),
            dstIp = buf.copyOfRange(16, 20),
            srcPort = srcPort,
            dstPort = dstPort,
            payload = buf.copyOfRange(start, start + payloadLen),
        )
    }

    /** So'rovga javob paketi: manzil va portlar o'rin almashadi. */
    fun buildResponse(query: DnsQuery, payload: ByteArray, payloadLen: Int): ByteArray {
        val total = IPV4_HEADER + UDP_HEADER + payloadLen
        val out = ByteArray(total)
        out[0] = 0x45
        putU16(out, 2, total)
        out[6] = 0x40 // Don't Fragment
        out[8] = 64 // TTL
        out[9] = PROTOCOL_UDP.toByte()
        System.arraycopy(query.dstIp, 0, out, 12, 4)
        System.arraycopy(query.srcIp, 0, out, 16, 4)
        putU16(out, 10, ipChecksum(out, IPV4_HEADER))

        putU16(out, IPV4_HEADER, query.dstPort)
        putU16(out, IPV4_HEADER + 2, query.srcPort)
        putU16(out, IPV4_HEADER + 4, UDP_HEADER + payloadLen)
        // UDP checksum 0 = "hisoblanmagan", IPv4 da ruxsat etilgan.
        System.arraycopy(payload, 0, out, IPV4_HEADER + UDP_HEADER, payloadLen)
        return out
    }

    /** DNS yuklamasidagi birinchi savol nomi (masalan "example.com"), o'qib bo'lmasa null. */
    fun questionName(payload: ByteArray): String? {
        if (payload.size < DNS_HEADER || u16(payload, 4) < 1) return null
        val sb = StringBuilder()
        var pos = DNS_HEADER
        while (pos < payload.size) {
            val labelLen = payload[pos].toInt() and 0xFF
            if (labelLen == 0) return sb.toString().lowercase()
            if (labelLen > 63 || pos + 1 + labelLen > payload.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..labelLen) sb.append((payload[pos + i].toInt() and 0xFF).toChar())
            pos += 1 + labelLen
        }
        return null
    }

    fun ipChecksum(buf: ByteArray, headerLen: Int): Int {
        var sum = 0
        var i = 0
        while (i < headerLen) {
            sum += u16(buf, i)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun u16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    private fun putU16(buf: ByteArray, off: Int, value: Int) {
        buf[off] = (value shr 8).toByte()
        buf[off + 1] = value.toByte()
    }
}
