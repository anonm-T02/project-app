package uz.himoya.app.policy

/**
 * Qimor va tikish saytlari. Bu saytlar manzilini tez-tez almashtiradi ("zerkalo"),
 * shuning uchun aniq ro'yxatdan tashqari brend nomi bo'yicha ham tekshiriladi.
 * Ro'yxat to'liq emas va vaqt o'tishi bilan yangilanishi kerak.
 */
object GamblingList {

    fun matches(name: String): Boolean =
        DomainText.inList(name, DOMAINS) || KEYWORDS.any { name.contains(it) }

    /** Domen nomining istalgan joyida uchrasa bloklanadigan so'zlar. */
    private val KEYWORDS = listOf(
        "1xbet", "1xslot", "1xbit", "1xstavka", "mostbet", "melbet", "linebet", "betwinner",
        "parimatch", "pari-match", "pin-up", "pinup", "1win", "vavada", "fonbet", "olimpbet",
        "leonbets", "winline", "betcity", "marathonbet", "megapari", "betandreas", "ggbet",
        "22bet", "4rabet", "dafabet", "roobet", "bet365", "betway", "betano", "bet9ja",
        "sportsbet", "sportingbet", "betfair", "betsson", "pokerstars", "pokerdom", "poker",
        "casino", "kazino", "cazino", "azino", "jackpot", "bukmeker", "bookmaker",
    )

    private val DOMAINS: Set<String> = setOf(
        // Tikish idoralari
        "bwin.com", "unibet.com", "williamhill.com", "ladbrokes.com", "coral.co.uk",
        "paddypower.com", "skybet.com", "betvictor.com", "888sport.com", "888.com",
        "10bet.com", "pinnacle.com", "sbobet.com", "188bet.com", "12bet.com", "w88.com",
        "fun88.com", "m88.com", "draftkings.com", "fanduel.com", "betmgm.com", "caesars.com",
        "bovada.lv", "betonline.ag", "mybookie.ag", "betus.com.pa", "stake.com", "stake.us",
        "bc.game", "rollbit.com", "duelbits.com", "csgoempire.com", "csgoroll.com",
        "gamdom.com", "500.casino", "thunderpick.io", "cloudbet.com", "sportsbet.io",
        "bitstarz.com", "fortunejack.com", "betfury.io", "trustdice.win", "primedice.com",
        "olimp.bet", "olimp.kz", "leon.bet", "leon.ru", "ligastavok.ru", "baltbet.ru",
        "tennisi.bet", "zenit.win", "zenitbet.com", "bettery.ru", "pari.ru", "betboom.ru",
        "liga.bet", "sportbet.ru", "astrabet.ru", "betring.ru", "favbet.com", "favorit.com.ua",
        "vbet.com", "tonybet.com", "betsafe.com", "nordicbet.com", "comeon.com",
        "leovegas.com", "mrgreen.com", "casumo.com", "videoslots.com", "rizk.com",
        "partypoker.com", "ggpoker.com", "wsop.com", "tigergaming.com",
        // Kazino va slotlar
        "joycasino.com", "casino-x.com", "riobet.com", "rox.casino", "sol.casino",
        "fresh.casino", "jet.casino", "izzi.casino", "legzo.casino", "starda.casino",
        "drip.casino", "monro.casino", "lex.casino", "gama.casino", "daddy.casino",
        "kent.casino", "cat.casino", "r7.casino", "retro.casino", "1go.casino",
        "volna.casino", "flagman.casino", "martin.casino", "irwin.casino", "gizbo.casino",
        "playfortuna.com", "booi.com", "slottica.com", "spinbetter.com", "sultanbet.com",
        "bons.com", "lopebet.com", "mostplay.com", "batery.in", "rajbet.com", "parimatch.in",
        "aviator-game.com", "spribe.co", "pragmaticplay.com", "evolution.com",
        "slotozal.com", "slotv.com", "frank.casino", "mr.bet", "bitcasino.io", "wolf.bet",
        "lucky-jet.com", "luckyjet.com", "aviatrix.bet", "crash.bet", "csgofast.com",
        "hellcase.com", "keydrop.com", "skinclub.gg", "farmskins.com", "datdrop.com",
    )
}
