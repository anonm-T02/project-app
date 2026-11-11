package uz.himoya.app.guard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun validatesPinFormat() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("123456"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("1234567"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin(""))
    }

    @Test
    fun correctPinMatchesAndWrongPinDoesNot() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4821", salt)
        assertTrue(PinHasher.matches("4821", salt, hash))
        assertFalse(PinHasher.matches("4822", salt, hash))
        assertFalse(PinHasher.matches("48210", salt, hash))
    }

    @Test
    fun sameSaltGivesSameHashDifferentSaltDiffers() {
        val salt = ByteArray(16) { it.toByte() }
        assertArrayEquals(PinHasher.hash("1234", salt), PinHasher.hash("1234", salt))
        val other = ByteArray(16) { (it + 1).toByte() }
        assertFalse(PinHasher.hash("1234", salt).contentEquals(PinHasher.hash("1234", other)))
    }

    @Test
    fun hexRoundTrip() {
        val bytes = byteArrayOf(0, 1, 127, -128, -1, 16)
        assertEquals("00017f80ff10", PinHasher.toHex(bytes))
        assertArrayEquals(bytes, PinHasher.fromHex(PinHasher.toHex(bytes)))
    }

    @Test
    fun locksAfterEveryFifthFailure() {
        for (n in 1..4) assertEquals(0L, PinHasher.lockDurationMs(n))
        assertEquals(30_000L, PinHasher.lockDurationMs(5))
        assertEquals(0L, PinHasher.lockDurationMs(6))
        assertEquals(60_000L, PinHasher.lockDurationMs(10))
        assertEquals(120_000L, PinHasher.lockDurationMs(15))
        assertEquals(15 * 60_000L, PinHasher.lockDurationMs(30))
        assertEquals(15 * 60_000L, PinHasher.lockDurationMs(500))
    }
}
