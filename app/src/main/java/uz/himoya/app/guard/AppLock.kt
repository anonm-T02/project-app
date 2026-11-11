package uz.himoya.app.guard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Ilova qulfi. Faqat xotirada turadi: ilova fonga o'tganda yoki jarayon
 * to'xtaganda qulflanadi va keyingi ochilishda yana PIN so'raladi.
 */
object AppLock {
    var unlocked by mutableStateOf(false)
}
