package uz.himoya.app.policy

/**
 * Ota-ona belgilagan qoidalar. Vaqtlar kun boshidan boshlab daqiqalarda (22:00 = 1320).
 * Android'ga bog'liq emas, unit test bilan tekshiriladi.
 */
data class Policy(
    val bedtimeEnabled: Boolean = false,
    val bedtimeStart: Int = 22 * 60,
    val bedtimeEnd: Int = 7 * 60,
    val blockGambling: Boolean = true,
    /** Ota-ona qo'shimcha yopgan saytlar. */
    val blacklist: Set<String> = emptySet(),
    /** Ota-ona ruxsat bergan saytlar: filtr yopgan bo'lsa ham ochiladi. */
    val whitelist: Set<String> = emptySet(),
) {
    /** Uyqu vaqti yarim tundan o'tishi mumkin (22:00–07:00). */
    fun isBedtime(minuteOfDay: Int): Boolean {
        if (!bedtimeEnabled || bedtimeStart == bedtimeEnd) return false
        return if (bedtimeStart < bedtimeEnd) {
            minuteOfDay in bedtimeStart until bedtimeEnd
        } else {
            minuteOfDay >= bedtimeStart || minuteOfDay < bedtimeEnd
        }
    }
}

object TimeText {
    const val MINUTES_PER_DAY = 24 * 60

    fun format(minuteOfDay: Int): String {
        val m = wrap(minuteOfDay)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** Vaqtni [delta] daqiqaga suradi, kun chegarasidan aylanib o'tadi. */
    fun shift(minuteOfDay: Int, delta: Int): Int = wrap(minuteOfDay + delta)

    private fun wrap(value: Int): Int = ((value % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
}

object DomainText {

    /**
     * Foydalanuvchi yozgan matndan toza domen nomini ajratadi:
     * "https://www.Example.com/page?x=1" -> "example.com". Yaroqsiz bo'lsa null.
     */
    fun normalize(input: String): String? {
        var s = input.trim().lowercase()
        s = s.substringAfter("://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@').substringBefore(':')
        s = s.removePrefix("*.").removePrefix("www.").trim('.')
        if (s.length !in 4..253 || !s.contains('.')) return null
        val labels = s.split('.')
        val valid = labels.all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                !label.startsWith('-') && !label.endsWith('-') &&
                label.all { c -> c in 'a'..'z' || c in '0'..'9' || c == '-' }
        }
        if (!valid) return null
        // IP manzil emas, domen bo'lishi kerak.
        if (labels.last().all { it in '0'..'9' }) return null
        return s
    }

    /** [name] ro'yxatdagi domenning o'zi yoki quyi domeni bo'lsa true. */
    fun inList(name: String, list: Set<String>): Boolean {
        if (list.isEmpty()) return false
        var candidate = name
        while (true) {
            if (candidate in list) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }
}
