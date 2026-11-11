package uz.himoya.app.vpn

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import uz.himoya.app.policy.PolicyRepo
import uz.himoya.app.sync.TempAllow
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Hozirgi vaqt, kun boshidan daqiqalarda: internet vaqti, Toshkent bo'yicha. */
internal fun currentMinuteOfDay(): Int = TrustedClock.minuteOfDay()

/**
 * Tunnelga kelgan DNS so'rovlarini lokal qoidalardan o'tkazadi, so'ng oilaviy DNS serveriga
 * yuboradi va javobni qaytaradi.
 *
 * Oddiy rejimda tunnelga faqat DNS manzillari yo'naltirilgan: sahifalar mazmuni, parollar va
 * boshqa trafik bu kodga kelmaydi. Uyqu rejimida ([bedtimeMode]) tunnelga butun trafik keladi
 * va tashlab yuboriladi (o'qilmaydi), DNS so'rovlari esa "topilmadi" javobini oladi.
 */
class DnsForwarder(
    private val tun: ParcelFileDescriptor,
    private val bedtimeMode: Boolean,
    private val protect: (DatagramSocket) -> Boolean,
    /** Sayt bloklanganda chaqiriladi (domen nomi va sabab bilan). */
    private val onSiteBlocked: (String, BlockReason) -> Unit,
    /** Uyqu vaqti boshlangan yoki tugagan, tunnelni qayta qurish kerak. */
    private val onModeMismatch: () -> Unit,
) {
    private val running = AtomicBoolean(false)
    private val mismatchReported = AtomicBoolean(false)
    private val output = FileOutputStream(tun.fileDescriptor)
    private val workers = ThreadPoolExecutor(
        0, MAX_PARALLEL, 30L, TimeUnit.SECONDS,
        SynchronousQueue<Runnable>(),
        // Juda ko'p so'rov bir vaqtda kelsa ortiqchasi tashlanadi, tizim o'zi qayta so'raydi.
        ThreadPoolExecutor.DiscardPolicy(),
    )

    private var stopPipe: Array<FileDescriptor>? = null
    private var reader: Thread? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        stopPipe = Os.pipe()
        reader = Thread({ readLoop() }, "dns-tun-reader").apply { start() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        // Yozish uchini yopamiz: o'qish uchida POLLHUP paydo bo'ladi va sikl to'xtaydi.
        stopPipe?.let { pipe -> runCatching { Os.close(pipe[1]) } }
        workers.shutdownNow()
        runCatching { reader?.join(500) }
        reader = null
    }

    private fun readLoop() {
        val pipe = stopPipe ?: return
        val input = FileInputStream(tun.fileDescriptor)
        val buf = ByteArray(MAX_PACKET)
        val pollTun = StructPollfd().apply {
            fd = tun.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }
        val pollStop = StructPollfd().apply {
            fd = pipe[0]
            events = OsConstants.POLLIN.toShort()
        }
        val fds = arrayOf(pollTun, pollStop)
        val brokenMask = OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL
        var readErrors = 0
        var lastModeCheck = 0L

        try {
            while (running.get()) {
                try {
                    Os.poll(fds, -1)
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EINTR) continue
                    Log.w(TAG, "poll xatosi", e)
                    break
                }
                if (pollStop.revents.toInt() != 0) break
                val tunEvents = pollTun.revents.toInt()
                if (tunEvents and brokenMask != 0) break
                if (tunEvents and OsConstants.POLLIN == 0) continue

                val n = try {
                    input.read(buf)
                } catch (e: IOException) {
                    if (++readErrors > 20) break
                    continue
                }
                if (n <= 0) continue
                readErrors = 0

                // Uyqu vaqti boshlangan/tugaganini sekundiga ko'pi bilan bir marta tekshiramiz.
                val uptime = SystemClock.elapsedRealtime()
                if (uptime - lastModeCheck > MODE_CHECK_MS) {
                    lastModeCheck = uptime
                    checkMode()
                }

                val query = DnsPacket.parseQuery(buf, n) ?: continue
                workers.execute { resolve(query) }
            }
        } finally {
            runCatching { Os.close(pipe[0]) }
        }
    }

    private fun checkMode() {
        val shouldBeBedtime = PolicyRepo.current.isBedtime(currentMinuteOfDay())
        if (shouldBeBedtime != bedtimeMode && mismatchReported.compareAndSet(false, true)) {
            onModeMismatch()
        }
    }

    private fun resolve(query: DnsQuery) {
        try {
            val reply = answer(query.payload)
            if (reply != null) {
                write(DnsPacket.buildResponse(query, reply, reply.size))
                VpnState.countRequest()
            }
        } catch (e: IOException) {
            // Tunnel yopilgan bo'lishi mumkin.
        } catch (e: RuntimeException) {
            Log.w(TAG, "DNS so'rovini qayta ishlab bo'lmadi", e)
        }
    }

    private fun answer(payload: ByteArray): ByteArray? {
        if (bedtimeMode) {
            return DnsMessage.response(payload, DnsMessage.RCODE_NXDOMAIN)
        }
        val name = DnsPacket.questionName(payload)
        // Ota-ona vaqtincha ochib bergan saytlar ruxsat ro'yxatiga qo'shib hisoblanadi.
        var policy = PolicyRepo.current
        val tempAllowed = TempAllow.active(TrustedClock.nowUtcMs())
        if (tempAllowed.isNotEmpty()) {
            policy = policy.copy(whitelist = policy.whitelist + tempAllowed)
        }
        return when (val decision = LocalRules.decide(name, policy)) {
            is DnsDecision.Block -> {
                VpnState.countBlocked()
                if (decision.reason != BlockReason.ENCRYPTED_DNS && name != null) {
                    onSiteBlocked(name, decision.reason)
                }
                DnsMessage.response(payload, DnsMessage.RCODE_NXDOMAIN)
            }

            is DnsDecision.Remap -> remap(payload, decision.target)

            DnsDecision.ForwardUnfiltered -> exchange(payload, UNFILTERED_UPSTREAMS)

            DnsDecision.Forward -> exchange(payload, FAMILY_UPSTREAMS)?.also { upstream ->
                if (DnsMessage.isBlockedAnswer(upstream)) {
                    VpnState.countBlocked()
                    if (name != null) onSiteBlocked(name, BlockReason.ADULT_OR_HARMFUL)
                }
            }
        }
    }

    /**
     * Xavfsiz qidiruv: so'ralgan nom o'rniga [target] manzillarini qaytaradi.
     * Manzilni olib bo'lmasa xato qaytadi, so'rov filtrsiz o'tib ketmaydi.
     */
    private fun remap(payload: ByteArray, target: String): ByteArray? {
        val qtype = DnsMessage.questionType(payload)
        if (qtype != DnsMessage.TYPE_A && qtype != DnsMessage.TYPE_AAAA) {
            // Boshqa turdagi yozuvlar (masalan HTTPS) bo'sh javob oladi.
            return DnsMessage.response(payload, DnsMessage.RCODE_NOERROR)
        }
        val upstream = exchange(
            DnsMessage.buildQuery(DnsMessage.id(payload), target, qtype),
            FAMILY_UPSTREAMS,
        ) ?: return DnsMessage.response(payload, DnsMessage.RCODE_SERVFAIL)
        return DnsMessage.response(
            payload,
            DnsMessage.RCODE_NOERROR,
            DnsMessage.addresses(upstream, qtype),
        )
    }

    /** So'rovni berilgan serverlarga yuboradi. Hech biri javob bermasa null. */
    private fun exchange(payload: ByteArray, servers: List<InetAddress>): ByteArray? {
        val answer = ByteArray(MAX_DNS)
        for (server in servers) {
            try {
                DatagramSocket().use { socket ->
                    // Soket VPN'dan tashqarida, oddiy tarmoq orqali chiqadi.
                    protect(socket)
                    socket.soTimeout = TIMEOUT_MS
                    socket.connect(server, DNS_PORT)
                    socket.send(DatagramPacket(payload, payload.size))
                    val reply = DatagramPacket(answer, answer.size)
                    socket.receive(reply)
                    return answer.copyOf(reply.length)
                }
            } catch (e: IOException) {
                // Keyingi serverni sinab ko'ramiz.
            }
        }
        return null
    }

    private fun write(packet: ByteArray) {
        synchronized(output) {
            output.write(packet)
        }
    }

    companion object {
        private const val TAG = "DnsForwarder"
        private const val DNS_PORT = 53
        private const val TIMEOUT_MS = 3000
        private const val MAX_PARALLEL = 32
        private const val MODE_CHECK_MS = 1000L
        const val MAX_DNS = 4096
        const val MAX_PACKET = MAX_DNS + 28

        /** Cloudflare for Families: kattalar kontenti va zararli saytlarni bloklaydi. */
        private val FAMILY_UPSTREAMS: List<InetAddress> = listOf(
            InetAddress.getByAddress(byteArrayOf(1, 1, 1, 3)),
            InetAddress.getByAddress(byteArrayOf(1, 0, 0, 3)),
        )

        /** Faqat ota-ona ruxsat bergan saytlar uchun: filtrsiz Cloudflare DNS. */
        private val UNFILTERED_UPSTREAMS: List<InetAddress> = listOf(
            InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)),
            InetAddress.getByAddress(byteArrayOf(1, 0, 0, 1)),
        )
    }
}
