package uz.himoya.app.vpn

import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Telefon soatiga bog'liq bo'lmagan vaqt.
 *
 * Vaqt internetdagi vaqt serveridan olinadi va telefon yoqilgandan beri o'tgan vaqt
 * hisoblagichiga bog'lanadi. Bu hisoblagichni foydalanuvchi o'zgartira olmaydi, shuning uchun
 * sozlamalarda soatni surish uyqu vaqtini aylanib o'tishga yordam bermaydi.
 *
 * Internet vaqti hali olinmagan bo'lsa (masalan, telefon endi yoqilgan va tarmoq yo'q)
 * vaqtincha telefon soati ishlatiladi.
 */
object TrustedClock {
    private const val TAG = "TrustedClock"
    private const val NTP_PORT = 123
    private const val TIMEOUT_MS = 3000
    private const val RESYNC_AFTER_MS = 6 * 60 * 60 * 1000L
    private const val RETRY_AFTER_MS = 30_000L

    private class Base(val utcMs: Long, val elapsedMs: Long)

    @Volatile
    private var base: Base? = null

    @Volatile
    private var lastAttemptElapsed = 0L

    /**
     * Vaqt serverlari IP manzil bilan yozilgan: uyqu vaqtida nomlarni aniqlash (DNS) yopiq.
     * Google Public NTP va Cloudflare Time.
     */
    private val SERVERS: List<InetAddress> = listOf(
        InetAddress.getByAddress(byteArrayOf(216.toByte(), 239.toByte(), 35, 0)),
        InetAddress.getByAddress(byteArrayOf(162.toByte(), 159.toByte(), 200.toByte(), 1)),
        InetAddress.getByAddress(byteArrayOf(216.toByte(), 239.toByte(), 35, 4)),
        InetAddress.getByAddress(byteArrayOf(162.toByte(), 159.toByte(), 200.toByte(), 123)),
    )

    val isSynced: Boolean get() = base != null

    fun nowUtcMs(): Long {
        val b = base ?: return System.currentTimeMillis()
        return b.utcMs + (SystemClock.elapsedRealtime() - b.elapsedMs)
    }

    /** Toshkent vaqti bo'yicha kun daqiqasi (0..1439). */
    fun minuteOfDay(): Int = TimeMath.minuteOfDay(nowUtcMs())

    /** Vaqt olinmagan yoki eskirgan bo'lsa va oxirgi urinishdan yetarlicha vaqt o'tgan bo'lsa true. */
    fun needsSync(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val b = base
        val stale = b == null || now - b.elapsedMs > RESYNC_AFTER_MS
        return stale && now - lastAttemptElapsed > RETRY_AFTER_MS
    }

    /**
     * Internet vaqtini oladi. Tarmoqqa chiqadi, asosiy oqimdan chaqirilmasin.
     * [protect] soketni VPN'dan tashqariga chiqaradi.
     */
    @Synchronized
    fun sync(protect: (DatagramSocket) -> Boolean): Boolean {
        lastAttemptElapsed = SystemClock.elapsedRealtime()
        val buffer = ByteArray(SntpMessage.SIZE)
        for (server in SERVERS) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = TIMEOUT_MS
                    socket.connect(server, NTP_PORT)
                    val request = SntpMessage.buildRequest()
                    val sentAt = SystemClock.elapsedRealtime()
                    socket.send(DatagramPacket(request, request.size))
                    val reply = DatagramPacket(buffer, buffer.size)
                    socket.receive(reply)
                    val receivedAt = SystemClock.elapsedRealtime()
                    val serverMs = SntpMessage.parseTransmitTimeMs(buffer, reply.length)
                    if (serverMs != null) {
                        // Javob yo'lda ketgan vaqtning yarmini qo'shamiz.
                        base = Base(serverMs + (receivedAt - sentAt) / 2, receivedAt)
                        return true
                    }
                }
            } catch (e: IOException) {
                // Keyingi serverni sinab ko'ramiz.
            } catch (e: RuntimeException) {
                Log.w(TAG, "Vaqt serveriga ulanib bo'lmadi", e)
            }
        }
        return false
    }
}
