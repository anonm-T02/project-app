package uz.himoya.app.vpn

import java.io.ByteArrayOutputStream

/**
 * DNS xabarining o'zi (IP/UDP sarlavhasisiz) bilan ishlash: savolni o'qish,
 * lokal javob yig'ish, yuqori serverdan kelgan javobdan manzillarni ajratish.
 * Android'ga bog'liq emas, unit test bilan tekshiriladi.
 */
object DnsMessage {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    const val RCODE_NOERROR = 0
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3

    private const val HEADER = 12
    private const val CLASS_IN = 1
    private const val LOCAL_TTL = 60

    /** Birinchi savol tugaydigan joy (nom + tur + sinf), xato bo'lsa -1. */
    fun questionEnd(msg: ByteArray): Int {
        var pos = HEADER
        while (true) {
            if (pos >= msg.size) return -1
            val len = msg[pos].toInt() and 0xFF
            if (len == 0) break
            if (len > 63) return -1
            pos += 1 + len
        }
        val end = pos + 1 + 4
        return if (end <= msg.size) end else -1
    }

    /** Savol turi (A = 1, AAAA = 28 ...), o'qib bo'lmasa -1. */
    fun questionType(msg: ByteArray): Int {
        val end = questionEnd(msg)
        return if (end < 0) -1 else u16(msg, end - 4)
    }

    fun id(msg: ByteArray): Int = u16(msg, 0)

    /**
     * So'rovga lokal javob. [addresses] savol turiga mos bo'lsa (A uchun 4 bayt,
     * AAAA uchun 16 bayt) javobga qo'shiladi, qolganlari tashlanadi.
     */
    fun response(query: ByteArray, rcode: Int, addresses: List<ByteArray> = emptyList()): ByteArray? {
        val qEnd = questionEnd(query)
        if (qEnd < 0) return null
        val qtype = u16(query, qEnd - 4)
        val usable = addresses.filter {
            (qtype == TYPE_A && it.size == 4) || (qtype == TYPE_AAAA && it.size == 16)
        }

        val out = ByteArrayOutputStream(qEnd + usable.size * 28)
        out.write(query, 0, qEnd)
        for (address in usable) {
            out.write(0xC0) // nom: savoldagi nomga ko'rsatkich
            out.write(0x0C)
            write16(out, qtype)
            write16(out, CLASS_IN)
            write16(out, 0)
            write16(out, LOCAL_TTL)
            write16(out, address.size)
            out.write(address, 0, address.size)
        }
        val bytes = out.toByteArray()
        val opcodeAndRd = query[2].toInt() and 0x79
        bytes[2] = (0x80 or opcodeAndRd).toByte() // javob belgisi
        bytes[3] = (0x80 or (rcode and 0x0F)).toByte() // rekursiya mavjud + natija kodi
        put16(bytes, 4, 1)
        put16(bytes, 6, usable.size)
        put16(bytes, 8, 0)
        put16(bytes, 10, 0)
        return bytes
    }

    /** Yuqori serverga yuboriladigan oddiy so'rov. */
    fun buildQuery(id: Int, name: String, qtype: Int): ByteArray {
        val out = ByteArrayOutputStream(HEADER + name.length + 6)
        write16(out, id)
        write16(out, 0x0100) // rekursiya so'raladi
        write16(out, 1)
        write16(out, 0)
        write16(out, 0)
        write16(out, 0)
        for (label in name.split('.')) {
            if (label.isEmpty()) continue
            out.write(label.length)
            for (ch in label) out.write(ch.code)
        }
        out.write(0)
        write16(out, qtype)
        write16(out, CLASS_IN)
        return out.toByteArray()
    }

    /** Javobdagi [qtype] turidagi manzillar. Buzilgan xabar uchun bo'sh ro'yxat. */
    fun addresses(msg: ByteArray, qtype: Int): List<ByteArray> {
        val result = ArrayList<ByteArray>()
        try {
            if (msg.size < HEADER) return result
            val questions = u16(msg, 4)
            val answers = u16(msg, 6)
            var pos = HEADER
            repeat(questions) {
                pos = skipName(msg, pos) + 4
            }
            repeat(answers) {
                pos = skipName(msg, pos)
                val type = u16(msg, pos)
                val rdLength = u16(msg, pos + 8)
                val rdStart = pos + 10
                if (rdStart + rdLength > msg.size) return result
                if (type == qtype) result.add(msg.copyOfRange(rdStart, rdStart + rdLength))
                pos = rdStart + rdLength
            }
        } catch (e: IndexOutOfBoundsException) {
            // Yarim kelgan xabar: topilganlarini qaytaramiz.
        }
        return result
    }

    /**
     * Oilaviy DNS bloklagan domen uchun 0.0.0.0 (yoki ::) qaytaradi.
     * Shu belgi bo'yicha "bloklandi" hisoblaymiz.
     */
    fun isBlockedAnswer(msg: ByteArray): Boolean {
        if (msg.size < HEADER || (msg[3].toInt() and 0x0F) != RCODE_NOERROR) return false
        val qtype = questionType(msg)
        if (qtype != TYPE_A && qtype != TYPE_AAAA) return false
        val found = addresses(msg, qtype)
        return found.isNotEmpty() && found.all { address -> address.all { it.toInt() == 0 } }
    }

    private fun skipName(msg: ByteArray, start: Int): Int {
        var pos = start
        while (true) {
            val len = msg[pos].toInt() and 0xFF
            if (len == 0) return pos + 1
            if (len and 0xC0 == 0xC0) return pos + 2 // siqilgan nom
            pos += 1 + len
        }
    }

    private fun u16(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 8) or (buf[off + 1].toInt() and 0xFF)

    private fun put16(buf: ByteArray, off: Int, value: Int) {
        buf[off] = (value shr 8).toByte()
        buf[off + 1] = value.toByte()
    }

    private fun write16(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
