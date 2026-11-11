package uz.himoya.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SntpMessageTest {

    private fun response(ntpSeconds: Long, fraction: Long = 0, mode: Int = 4, stratum: Int = 2): ByteArray {
        val b = ByteArray(48)
        b[0] = (0x20 or mode).toByte()
        b[1] = stratum.toByte()
        for (i in 0..3) b[40 + i] = (ntpSeconds shr (24 - 8 * i)).toByte()
        for (i in 0..3) b[44 + i] = (fraction shr (24 - 8 * i)).toByte()
        return b
    }

    @Test
    fun requestIsClientVersion4() {
        val r = SntpMessage.buildRequest()
        assertEquals(48, r.size)
        assertEquals(0x23, r[0].toInt())
    }

    @Test
    fun parsesTransmitTime() {
        // 2026-11-11 12:00:00 UTC = 1794398400 (Unix)
        val unix = 1_794_398_400L
        val ms = SntpMessage.parseTransmitTimeMs(response(unix + 2_208_988_800L, 0x8000_0000L), 48)
        assertEquals(unix * 1000 + 500, ms)
    }

    @Test
    fun handlesEraRolloverAfter2036() {
        // 2040-01-01 00:00:00 UTC = 2208988800 (Unix); NTP soniyalari 2^32 dan oshib aylanadi.
        val unix = 2_208_988_800L
        val wrapped = (unix + 2_208_988_800L) - 0x1_0000_0000L
        assertEquals(unix * 1000, SntpMessage.parseTransmitTimeMs(response(wrapped), 48))
    }

    @Test
    fun rejectsInvalidResponses() {
        val good = 1_794_398_400L + 2_208_988_800L
        assertNull(SntpMessage.parseTransmitTimeMs(response(good, mode = 3), 48))
        assertNull(SntpMessage.parseTransmitTimeMs(response(good, stratum = 0), 48)) // "kiss of death"
        assertNull(SntpMessage.parseTransmitTimeMs(response(good, stratum = 16), 48))
        assertNull(SntpMessage.parseTransmitTimeMs(response(0), 48))
        assertNull(SntpMessage.parseTransmitTimeMs(response(good), 20))
        assertNull(SntpMessage.parseTransmitTimeMs(ByteArray(10), 10))
    }

    @Test
    fun convertsUtcToTashkentMinuteOfDay() {
        // 2026-11-11 12:00 UTC -> 17:00 Toshkent
        assertEquals(17 * 60, TimeMath.minuteOfDay(1_794_398_400_000L))
        // 20:30 UTC -> ertasi kuni 01:30 Toshkent
        assertEquals(90, TimeMath.minuteOfDay(1_794_398_400_000L + (8 * 60 + 30) * 60_000L))
        // 19:00 UTC -> 00:00
        assertEquals(0, TimeMath.minuteOfDay(1_794_398_400_000L + 7 * 3_600_000L))
    }
}
