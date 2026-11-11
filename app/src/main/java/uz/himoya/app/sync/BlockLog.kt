package uz.himoya.app.sync

import android.os.SystemClock
import uz.himoya.app.vpn.BlockReason
import uz.himoya.app.vpn.TrustedClock

class BlockEvent(val domain: String, val reason: String, val atUtcMs: Long)

/**
 * Ota-onaga yuboriladigan blok yozuvlari. Faqat domen, sabab va vaqt saqlanadi;
 * sahifa manzili, qidiruv so'zlari yoki sahifa mazmuni saqlanmaydi.
 * Faqat xotirada turadi va server qabul qilgach o'chiriladi.
 */
object BlockLog {
    private const val MAX_PENDING = 50
    private const val COOLDOWN_MS = 30_000L

    private val pending = ArrayList<BlockEvent>()
    private val lastSeen = HashMap<String, Long>()

    @Synchronized
    fun add(rawDomain: String, reason: BlockReason) {
        val domain = rawDomain.removePrefix("www.")
        val now = SystemClock.elapsedRealtime()
        val last = lastSeen[domain]
        if (last != null && now - last < COOLDOWN_MS) return
        if (lastSeen.size > 200) lastSeen.clear()
        lastSeen[domain] = now
        if (pending.size >= MAX_PENDING) pending.removeAt(0)
        pending.add(BlockEvent(domain, reason.name, TrustedClock.nowUtcMs()))
    }

    @Synchronized
    fun snapshot(): List<BlockEvent> = ArrayList(pending)

    /** Server qabul qilgan yozuvlarni o'chiradi. */
    @Synchronized
    fun confirm(sent: List<BlockEvent>) {
        pending.removeAll(sent.toSet())
    }
}
