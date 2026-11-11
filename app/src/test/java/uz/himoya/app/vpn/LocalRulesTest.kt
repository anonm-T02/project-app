package uz.himoya.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalRulesTest {

    @Test
    fun blocksEncryptedDnsProviders() {
        for (host in listOf(
            "dns.google", "DNS.Google.", "cloudflare-dns.com", "mozilla.cloudflare-dns.com",
            "family.cloudflare-dns.com", "one.one.one.one", "abc123.dns.nextdns.io",
            "use-application-dns.net", "dns.adguard.com", "x.d.adguard-dns.com",
        )) assertEquals(host, DnsDecision.Block, LocalRules.decide(host))
    }

    @Test
    fun doesNotBlockLookalikes() {
        for (host in listOf(
            "google.dev", "notdns.google.evil.com", "mycloudflare-dns.com", "kun.uz",
            "example.com", "mail.google.com", "play.google.com", "google.company",
        )) assertEquals(host, DnsDecision.Forward, LocalRules.decide(host))
    }

    @Test
    fun remapsSearchEnginesToSafeSearch() {
        val google = DnsDecision.Remap("forcesafesearch.google.com")
        for (host in listOf(
            "google.com", "www.google.com", "www.google.uz", "google.ru",
            "www.google.co.uk", "www.google.com.tr",
        )) assertEquals(host, google, LocalRules.decide(host))

        assertEquals(DnsDecision.Remap("restrict.youtube.com"), LocalRules.decide("www.youtube.com"))
        assertEquals(DnsDecision.Remap("restrict.youtube.com"), LocalRules.decide("youtubei.googleapis.com"))
        assertEquals(DnsDecision.Remap("strict.bing.com"), LocalRules.decide("www.bing.com"))
        assertEquals(DnsDecision.Remap("safe.duckduckgo.com"), LocalRules.decide("duckduckgo.com"))
        assertEquals(DnsDecision.Remap("familysearch.yandex.ru"), LocalRules.decide("yandex.uz"))
        assertEquals(DnsDecision.Remap("familysearch.yandex.ru"), LocalRules.decide("www.ya.ru"))
    }

    @Test
    fun emptyNameIsForwarded() {
        assertEquals(DnsDecision.Forward, LocalRules.decide(null))
        assertEquals(DnsDecision.Forward, LocalRules.decide(""))
    }
}
