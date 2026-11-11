package uz.himoya.app.vpn

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
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

/**
 * Tunnelga kelgan DNS so'rovlarini oilaviy DNS serveriga yuboradi va javobni qaytaradi.
 *
 * Tunnelga faqat virtual DNS manzili yo'naltirilgan, shuning uchun bu yerdan
 * faqat DNS o'tadi. Sahifalar mazmuni, parollar va boshqa trafik bu kodga kelmaydi.
 */
class DnsForwarder(
    private val tun: ParcelFileDescriptor,
    private val protect: (DatagramSocket) -> Boolean,
    /** Oilaviy DNS saytni bloklaganda chaqiriladi (domen nomi bilan). */
    private val onSiteBlocked: (String) -> Unit,
) {
    private val running = AtomicBoolean(false)
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
                val query = DnsPacket.parseQuery(buf, n) ?: continue
                workers.execute { resolve(query) }
            }
        } finally {
            runCatching { Os.close(pipe[0]) }
        }
    }

    private fun resolve(query: DnsQuery) {
        try {
            val name = DnsPacket.questionName(query.payload)
            val reply: ByteArray? = when (val decision = LocalRules.decide(name)) {
                DnsDecision.Block -> {
                    VpnState.countBlocked()
                    DnsMessage.response(query.payload, DnsMessage.RCODE_NXDOMAIN)
                }

                is DnsDecision.Remap -> remap(query.payload, decision.target)

                DnsDecision.Forward -> exchange(query.payload)?.also { answer ->
                    if (DnsMessage.isBlockedAnswer(answer)) {
                        VpnState.countBlocked()
                        if (name != null) onSiteBlocked(name)
                    }
                }
            }
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
        val upstream = exchange(DnsMessage.buildQuery(DnsMessage.id(payload), target, qtype))
            ?: return DnsMessage.response(payload, DnsMessage.RCODE_SERVFAIL)
        return DnsMessage.response(
            payload,
            DnsMessage.RCODE_NOERROR,
            DnsMessage.addresses(upstream, qtype),
        )
    }

    /** So'rovni oilaviy DNS'ga yuboradi. Hech bir server javob bermasa null. */
    private fun exchange(payload: ByteArray): ByteArray? {
        val answer = ByteArray(MAX_DNS)
        for (server in UPSTREAMS) {
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
        const val MAX_DNS = 4096
        const val MAX_PACKET = MAX_DNS + 28

        /** Cloudflare for Families: kattalar kontenti va zararli saytlarni bloklaydi. */
        private val UPSTREAMS: List<InetAddress> = listOf(
            InetAddress.getByAddress(byteArrayOf(1, 1, 1, 3)),
            InetAddress.getByAddress(byteArrayOf(1, 0, 0, 3)),
        )
    }
}
