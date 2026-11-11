package uz.himoya.app.guard

import android.content.Context

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val attemptsLeft: Int) : PinResult
    data class Locked(val untilMillis: Long) : PinResult
}

/**
 * Ota-ona PIN kodi shu qurilmada, internet kerak bo'lmagan holda saqlanadi.
 * Faqat tuz (salt) va xesh yoziladi, PIN'ning o'zi emas.
 */
class PinStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("guard", Context.MODE_PRIVATE)

    fun isSet(): Boolean = prefs.contains(KEY_HASH)

    fun lockedUntil(): Long = prefs.getLong(KEY_LOCKED_UNTIL, 0L)

    @Synchronized
    fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin))
        val salt = PinHasher.newSalt()
        prefs.edit()
            .putString(KEY_SALT, PinHasher.toHex(salt))
            .putString(KEY_HASH, PinHasher.toHex(PinHasher.hash(pin, salt)))
            .remove(KEY_FAILURES)
            .remove(KEY_LOCKED_UNTIL)
            .commit()
    }

    /** Sekin ishlaydi (xeshlash), asosiy oqimdan chaqirilmasin. */
    @Synchronized
    fun verify(pin: String): PinResult {
        val now = System.currentTimeMillis()
        val lockedUntil = lockedUntil()
        if (now < lockedUntil) return PinResult.Locked(lockedUntil)

        val salt = prefs.getString(KEY_SALT, null)
        val hash = prefs.getString(KEY_HASH, null)
        if (salt != null && hash != null &&
            PinHasher.matches(pin, PinHasher.fromHex(salt), PinHasher.fromHex(hash))
        ) {
            prefs.edit().remove(KEY_FAILURES).remove(KEY_LOCKED_UNTIL).commit()
            return PinResult.Ok
        }

        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        val lockMs = PinHasher.lockDurationMs(failures)
        val until = if (lockMs > 0) now + lockMs else 0L
        prefs.edit().putInt(KEY_FAILURES, failures).putLong(KEY_LOCKED_UNTIL, until).commit()
        return if (lockMs > 0) {
            PinResult.Locked(until)
        } else {
            PinResult.Wrong(PinHasher.ATTEMPTS_PER_ROUND - failures % PinHasher.ATTEMPTS_PER_ROUND)
        }
    }

    private companion object {
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_FAILURES = "pin_failures"
        const val KEY_LOCKED_UNTIL = "pin_locked_until"
    }
}
