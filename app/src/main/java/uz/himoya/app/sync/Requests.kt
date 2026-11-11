package uz.himoya.app.sync

import org.json.JSONObject
import java.io.IOException

/** Bolaning "bu saytni ochib bering" so'rovi. */
data class UnlockRequest(
    val id: String,
    val domain: String,
    val note: String,
    /** PENDING, APPROVED, DENIED yoki EXPIRED. */
    val status: String,
    /** Ruxsat shu vaqtgacha amal qiladi (UTC, millisekund). */
    val until: Long,
    val createdAt: Long,
) {
    companion object {
        const val PENDING = "PENDING"
        const val APPROVED = "APPROVED"
        const val DENIED = "DENIED"

        fun fromJson(json: JSONObject) = UnlockRequest(
            id = json.optString("id"),
            domain = json.optString("domain"),
            note = json.optString("note"),
            status = json.optString("status", PENDING),
            until = json.optLong("until"),
            createdAt = json.optLong("createdAt"),
        )
    }
}

/**
 * Ota-ona vaqtincha ruxsat bergan saytlar. Faqat xotirada turadi va har sinxronlashda
 * serverdan yangilanadi; muddati o'tgani hisobga olinmaydi.
 */
object TempAllow {
    @Volatile
    private var entries: Map<String, Long> = emptyMap()

    fun replace(next: Map<String, Long>) {
        entries = next
    }

    @Synchronized
    fun put(domain: String, untilUtcMs: Long) {
        entries = entries + (domain to untilUtcMs)
    }

    fun active(nowUtcMs: Long): Set<String> {
        val current = entries
        if (current.isEmpty()) return emptySet()
        return current.filterValues { it > nowUtcMs }.keys
    }
}

/** Bola telefoni tomoni. Chaqiruvlar bloklaydi: fon oqimida ishlating. */
object ChildRequests {

    @Throws(IOException::class)
    fun send(domain: String, note: String): UnlockRequest {
        val token = SyncStore.current.deviceToken ?: throw IOException("Qurilma bog'lanmagan")
        val body = JSONObject().put("domain", domain).put("note", note)
        return UnlockRequest.fromJson(Api.call("POST", "/api/device/requests", token, body))
    }

    @Throws(IOException::class)
    fun check(id: String): UnlockRequest {
        val token = SyncStore.current.deviceToken ?: throw IOException("Qurilma bog'lanmagan")
        return UnlockRequest.fromJson(Api.call("GET", "/api/device/requests/$id", token))
    }
}
