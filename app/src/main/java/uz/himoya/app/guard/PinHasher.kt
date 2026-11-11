package uz.himoya.app.guard

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN bilan bog'liq sof mantiq: xeshlash, format va qulflash muddati.
 * Android'ga bog'liq emas, unit test bilan tekshiriladi.
 */
object PinHasher {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6
    const val ATTEMPTS_PER_ROUND = 5

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val BASE_LOCK_MS = 30_000L
    private const val MAX_LOCK_MS = 15 * 60_000L

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    /** PIN'ning o'zi hech qayerda saqlanmaydi, faqat shu xesh saqlanadi. */
    fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun matches(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(pin, salt), expected)

    /**
     * Har 5-xato urinishdan keyin qulflash: 30 soniya, 1 daqiqa, 2 daqiqa ... eng ko'pi 15 daqiqa.
     * Boshqa urinishlarda 0 qaytadi.
     */
    fun lockDurationMs(failures: Int): Long {
        if (failures <= 0 || failures % ATTEMPTS_PER_ROUND != 0) return 0L
        val round = (failures / ATTEMPTS_PER_ROUND - 1).coerceAtMost(5)
        return minOf(BASE_LOCK_MS shl round, MAX_LOCK_MS)
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
