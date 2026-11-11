package uz.himoya.app.vpn

sealed interface DnsDecision {
    /** Oilaviy DNS'ga o'zgarishsiz yuboriladi. */
    data object Forward : DnsDecision

    /** Lokal bloklanadi ("bunday domen yo'q" javobi). */
    data object Block : DnsDecision

    /** Boshqa nomning manzili qaytariladi (xavfsiz qidiruv). */
    data class Remap(val target: String) : DnsDecision
}

/**
 * Oilaviy DNS'dan oldin ishlaydigan lokal qoidalar:
 * 1) filtrni chetlab o'tadigan shifrlangan DNS (DoH/DoT) xizmatlari bloklanadi;
 * 2) qidiruv tizimlari xavfsiz qidiruv manziliga yo'naltiriladi.
 */
object LocalRules {

    fun decide(rawName: String?): DnsDecision {
        if (rawName.isNullOrEmpty()) return DnsDecision.Forward
        val name = rawName.lowercase().trimEnd('.')
        if (isEncryptedDnsHost(name)) return DnsDecision.Block
        safeSearchTarget(name)?.let { return DnsDecision.Remap(it) }
        return DnsDecision.Forward
    }

    internal fun isEncryptedDnsHost(name: String): Boolean {
        var candidate = name
        while (true) {
            if (candidate in ENCRYPTED_DNS_HOSTS) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    internal fun safeSearchTarget(name: String): String? {
        SAFE_SEARCH[name]?.let { return it }
        return if (isGoogleSearchHost(name.removePrefix("www."))) GOOGLE_SAFE else null
    }

    /** google.com, google.uz, google.co.uk, google.com.tr kabi qidiruv manzillari. */
    private fun isGoogleSearchHost(bare: String): Boolean {
        if (!bare.startsWith("google.")) return false
        val parts = bare.removePrefix("google.").split('.')
        return when (parts.size) {
            1 -> parts[0] == "com" || isCountryCode(parts[0])
            2 -> (parts[0] == "co" || parts[0] == "com") && isCountryCode(parts[1])
            else -> false
        }
    }

    private fun isCountryCode(label: String): Boolean =
        label.length == 2 && label.all { it in 'a'..'z' }

    private const val GOOGLE_SAFE = "forcesafesearch.google.com"
    private const val YOUTUBE_SAFE = "restrict.youtube.com"
    private const val BING_SAFE = "strict.bing.com"
    private const val DUCK_SAFE = "safe.duckduckgo.com"
    private const val YANDEX_SAFE = "familysearch.yandex.ru"

    private val SAFE_SEARCH: Map<String, String> = buildMap {
        for (host in listOf(
            "www.youtube.com", "m.youtube.com", "youtubei.googleapis.com",
            "youtube.googleapis.com", "www.youtube-nocookie.com",
        )) put(host, YOUTUBE_SAFE)

        put("www.bing.com", BING_SAFE)

        put("duckduckgo.com", DUCK_SAFE)
        put("www.duckduckgo.com", DUCK_SAFE)

        for (domain in listOf(
            "yandex.ru", "yandex.com", "yandex.uz", "yandex.kz", "yandex.by", "yandex.com.tr", "ya.ru",
        )) {
            put(domain, YANDEX_SAFE)
            put("www.$domain", YANDEX_SAFE)
        }
    }

    /**
     * Shifrlangan DNS xizmatlari. Nomning o'zi va barcha quyi domenlari bloklanadi.
     * use-application-dns.net — Firefox shu nom ochilmasa o'zining DoH'ini yoqmaydi.
     */
    private val ENCRYPTED_DNS_HOSTS: Set<String> = setOf(
        "use-application-dns.net",
        // Google
        "dns.google", "dns.google.com", "dns64.dns.google",
        // Cloudflare
        "cloudflare-dns.com", "one.one.one.one", "cloudflare-gateway.com",
        // Quad9
        "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net", "dns12.quad9.net",
        // AdGuard
        "dns.adguard.com", "dns-family.adguard.com", "dns-unfiltered.adguard.com", "adguard-dns.com",
        "adguard-dns.io",
        // NextDNS, ControlD, OpenDNS, CleanBrowsing
        "dns.nextdns.io", "controld.com", "doh.opendns.com", "dns.opendns.com",
        "doh.familyshield.opendns.com", "doh.cleanbrowsing.org",
        // Yandex, Comss
        "dot.dns.yandex.net", "doh.dns.yandex.net", "dns.comss.one", "router.comss.one",
        // Boshqalar
        "dns.sb", "dns.mullvad.net", "doh.mullvad.net", "doh.libredns.gr", "dns.switch.ch",
        "doh.applied-privacy.net", "dns.alidns.com", "doh.pub", "dot.pub", "doh.360.cn",
        "dns.twnic.tw", "doh.xfinity.com", "doh.tiar.app", "dns.digitale-gesellschaft.ch",
        "doh.li", "dns.njal.la", "ordns.he.net", "dns0.eu", "dns.aa.net.uk", "doh.dnslify.com",
        "dns.rabbitdns.org", "blahdns.com", "dns.brahma.world", "doh.ffmuc.net", "dot.ffmuc.net",
        "dns.oszx.co", "pumplex.com", "dnsforge.de", "dns.decloudus.com", "dns.cira.ca",
        "canadianshield.cira.ca", "odvr.nic.cz", "doh.sandbox.opendns.com", "public.dns.iij.jp",
        "doh.centraleu.pi-dns.com", "pi-dns.com", "dns.bebasid.com", "doh.crypto.sx",
        "jp.tiar.app", "fi.doh.dns.snopyta.org", "dns.flatuslifir.is", "rethinkdns.com",
        "dns.digitalsize.net", "doh.seby.io", "dns.containerpi.com", "ibksturm.synology.me",
    )
}
