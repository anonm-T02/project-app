package uz.himoya.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Test
import uz.himoya.app.policy.Policy

class LocalRulesTest {

    @Test
    fun blocksEncryptedDnsProviders() {
        for (host in listOf(
            "dns.google", "DNS.Google.", "cloudflare-dns.com", "mozilla.cloudflare-dns.com",
            "family.cloudflare-dns.com", "one.one.one.one", "abc123.dns.nextdns.io",
            "use-application-dns.net", "dns.adguard.com", "x.d.adguard-dns.com",
        )) assertEquals(host, DnsDecision.Block(BlockReason.ENCRYPTED_DNS), LocalRules.decide(host))
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
    fun familyListsAndGambling() {
        val policy = Policy(
            blacklist = setOf("tiktok.com", "badsite.uz"),
            whitelist = setOf("school.uz", "mostbet-info.example.org"),
        )
        assertEquals(DnsDecision.Block(BlockReason.FAMILY_LIST), LocalRules.decide("www.tiktok.com", policy))
        assertEquals(DnsDecision.Block(BlockReason.FAMILY_LIST), LocalRules.decide("badsite.uz", policy))
        assertEquals(DnsDecision.ForwardUnfiltered, LocalRules.decide("lms.school.uz", policy))
        // Ruxsat ro'yxati qimor qoidasidan ustun.
        assertEquals(DnsDecision.ForwardUnfiltered, LocalRules.decide("mostbet-info.example.org", policy))
        assertEquals(DnsDecision.Forward, LocalRules.decide("nottiktok.com", policy))

        val gambling = DnsDecision.Block(BlockReason.GAMBLING)
        for (host in listOf(
            "1xbet.com", "uz.1xbet-mirror123.xyz", "mostbet.uz", "www.pin-up.bet", "stake.com",
            "play.vavada777.net", "royal.casino", "bwin.com",
        )) assertEquals(host, gambling, LocalRules.decide(host, policy))

        for (host in listOf("alphabet.com", "bethesda.net", "kun.uz", "better.com", "stakeholder.org")) {
            assertEquals(host, DnsDecision.Forward, LocalRules.decide(host, policy))
        }
        assertEquals(DnsDecision.Forward, LocalRules.decide("1xbet.com", Policy(blockGambling = false)))
    }

    @Test
    fun whitelistCannotUnblockEncryptedDnsOrSafeSearch() {
        val policy = Policy(whitelist = setOf("dns.google", "google.com"))
        assertEquals(DnsDecision.Block(BlockReason.ENCRYPTED_DNS), LocalRules.decide("dns.google", policy))
        assertEquals(DnsDecision.Remap("forcesafesearch.google.com"), LocalRules.decide("www.google.com", policy))
    }

    @Test
    fun emptyNameIsForwarded() {
        assertEquals(DnsDecision.Forward, LocalRules.decide(null))
        assertEquals(DnsDecision.Forward, LocalRules.decide(""))
    }
}
