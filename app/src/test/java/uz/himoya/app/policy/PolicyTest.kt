package uz.himoya.app.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {

    @Test
    fun bedtimeAcrossMidnight() {
        val p = Policy(bedtimeEnabled = true, bedtimeStart = 22 * 60, bedtimeEnd = 7 * 60)
        assertFalse(p.isBedtime(21 * 60 + 59))
        assertTrue(p.isBedtime(22 * 60))
        assertTrue(p.isBedtime(23 * 60 + 59))
        assertTrue(p.isBedtime(0))
        assertTrue(p.isBedtime(6 * 60 + 59))
        assertFalse(p.isBedtime(7 * 60))
        assertFalse(p.isBedtime(12 * 60))
    }

    @Test
    fun bedtimeWithinOneDay() {
        val p = Policy(bedtimeEnabled = true, bedtimeStart = 13 * 60, bedtimeEnd = 15 * 60)
        assertFalse(p.isBedtime(12 * 60 + 59))
        assertTrue(p.isBedtime(13 * 60))
        assertTrue(p.isBedtime(14 * 60 + 59))
        assertFalse(p.isBedtime(15 * 60))
    }

    @Test
    fun bedtimeDisabledOrEmpty() {
        assertFalse(Policy(bedtimeEnabled = false).isBedtime(23 * 60))
        assertFalse(Policy(bedtimeEnabled = true, bedtimeStart = 600, bedtimeEnd = 600).isBedtime(600))
    }

    @Test
    fun formatsAndShiftsTime() {
        assertEquals("22:00", TimeText.format(22 * 60))
        assertEquals("07:30", TimeText.format(7 * 60 + 30))
        assertEquals("00:00", TimeText.format(0))
        assertEquals(0, TimeText.shift(23 * 60 + 30, 30))
        assertEquals(23 * 60 + 30, TimeText.shift(0, -30))
    }

    @Test
    fun normalizesDomains() {
        assertEquals("example.com", DomainText.normalize("https://www.Example.com/page?x=1"))
        assertEquals("example.com", DomainText.normalize("  example.com.  "))
        assertEquals("sub.example.co.uk", DomainText.normalize("sub.example.co.uk:8080/a"))
        assertEquals("tiktok.com", DomainText.normalize("*.tiktok.com"))
        assertEquals("xn--80ak6aa92e.com", DomainText.normalize("xn--80ak6aa92e.com"))
        assertNull(DomainText.normalize(""))
        assertNull(DomainText.normalize("localhost"))
        assertNull(DomainText.normalize("bad domain.com"))
        assertNull(DomainText.normalize("192.168.1.1"))
        assertNull(DomainText.normalize("-bad.com"))
        assertNull(DomainText.normalize("сайт.рф"))
    }

    @Test
    fun listMatchesSubdomainsOnly() {
        val list = setOf("example.com")
        assertTrue(DomainText.inList("example.com", list))
        assertTrue(DomainText.inList("a.b.example.com", list))
        assertFalse(DomainText.inList("notexample.com", list))
        assertFalse(DomainText.inList("example.com.evil.org", list))
        assertFalse(DomainText.inList("example.com", emptySet()))
    }
}
