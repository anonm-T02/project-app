package uz.himoya.app.vpn

import java.util.TimeZone

/**
 * Internet vaqt serverlari (SNTP) bilan almashinadigan xabar va vaqt hisob-kitobi.
 * Android'ga bog'liq emas, unit test bilan tekshiriladi.
 */
object SntpMessage {
    const val SIZE = 48

    /** 1900-yil (NTP boshi) va 1970-yil (Unix boshi) orasidagi soniyalar. */
    private const val NTP_TO_UNIX_SECONDS = 2_208_988_800L
    private const val TRANSMIT_OFFSET = 40

    fun buildRequest(): ByteArray = ByteArray(SIZE).also { it[0] = 0x23 } // versiya 4, mijoz

    /** Server javobidagi vaqt (UTC, millisekund). Javob yaroqsiz bo'lsa null. */
    fun parseTransmitTimeMs(msg: ByteArray, length: Int): Long? {
        if (length < SIZE || msg.size < SIZE) return null
        val mode = msg[0].toInt() and 0x07
        val stratum = msg[1].toInt() and 0xFF
        if (mode != 4 || stratum !in 1..15) return null

        var seconds = u32(msg, TRANSMIT_OFFSET)
        val fraction = u32(msg, TRANSMIT_OFFSET + 4)
        if (seconds == 0L) return null
        // 2036-yildan keyin NTP soniyalari noldan qayta boshlanadi.
        if (seconds < 0x8000_0000L) seconds += 0x1_0000_0000L
        return (seconds - NTP_TO_UNIX_SECONDS) * 1000 + (fraction * 1000 ushr 32)
    }

    private fun u32(buf: ByteArray, off: Int): Long =
        ((buf[off].toLong() and 0xFF) shl 24) or
            ((buf[off + 1].toLong() and 0xFF) shl 16) or
            ((buf[off + 2].toLong() and 0xFF) shl 8) or
            (buf[off + 3].toLong() and 0xFF)
}

object TimeMath {
    /** Qoidalar shu vaqt mintaqasi bo'yicha ishlaydi (UTC+5). */
    const val ZONE_ID = "Asia/Tashkent"

    private val zone: TimeZone = TimeZone.getTimeZone(ZONE_ID)

    /** UTC vaqtdan Toshkent vaqti bo'yicha kun daqiqasi (0..1439). */
    fun minuteOfDay(utcMs: Long): Int {
        val localMs = utcMs + zone.getOffset(utcMs)
        val minutes = Math.floorMod(localMs / 60_000L, 1440L)
        return minutes.toInt()
    }
}
