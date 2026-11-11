package uz.himoya.app.policy

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Qoidalarni shu qurilmada saqlaydi. Ekran va VPN xizmati bitta jarayonda ishlagani uchun
 * o'zgarish darhol kuchga kiradi.
 */
object PolicyRepo {
    private val _policy = MutableStateFlow(Policy())
    val policy: StateFlow<Policy> = _policy.asStateFlow()

    val current: Policy get() = _policy.value

    private var prefs: SharedPreferences? = null

    /** Qoidalar shu telefonda o'zgartirilgan va hali serverga yuborilmagan. */
    @Volatile
    var dirty: Boolean = false
        private set

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("policy", Context.MODE_PRIVATE)
        prefs = p
        val defaults = Policy()
        _policy.value = Policy(
            bedtimeEnabled = p.getBoolean(KEY_BEDTIME_ENABLED, defaults.bedtimeEnabled),
            bedtimeStart = p.getInt(KEY_BEDTIME_START, defaults.bedtimeStart),
            bedtimeEnd = p.getInt(KEY_BEDTIME_END, defaults.bedtimeEnd),
            blockGambling = p.getBoolean(KEY_GAMBLING, defaults.blockGambling),
            blacklist = p.getStringSet(KEY_BLACKLIST, emptySet()).orEmpty().toSet(),
            whitelist = p.getStringSet(KEY_WHITELIST, emptySet()).orEmpty().toSet(),
        )
        dirty = p.getBoolean(KEY_DIRTY, false)
    }

    /** Shu telefonda (PIN ortidagi sozlamalardan) qilingan o'zgarish. */
    @Synchronized
    fun update(transform: (Policy) -> Policy) {
        store(transform(_policy.value))
        setDirty(true)
    }

    /** Ota-ona telefonidan kelgan qoidalar. */
    @Synchronized
    fun applyRemote(policy: Policy) {
        store(policy)
    }

    /** Server [sent] qoidalarni qabul qildi; shu orada yana o'zgarmagan bo'lsa tozalanadi. */
    @Synchronized
    fun markClean(sent: Policy) {
        if (_policy.value == sent) setDirty(false)
    }

    @Synchronized
    fun markDirty() {
        setDirty(true)
    }

    private fun setDirty(value: Boolean) {
        dirty = value
        prefs?.edit()?.putBoolean(KEY_DIRTY, value)?.apply()
    }

    private fun store(next: Policy) {
        _policy.value = next
        prefs?.edit()
            ?.putBoolean(KEY_BEDTIME_ENABLED, next.bedtimeEnabled)
            ?.putInt(KEY_BEDTIME_START, next.bedtimeStart)
            ?.putInt(KEY_BEDTIME_END, next.bedtimeEnd)
            ?.putBoolean(KEY_GAMBLING, next.blockGambling)
            ?.putStringSet(KEY_BLACKLIST, HashSet(next.blacklist))
            ?.putStringSet(KEY_WHITELIST, HashSet(next.whitelist))
            ?.apply()
    }

    private const val KEY_BEDTIME_ENABLED = "bedtime_enabled"
    private const val KEY_BEDTIME_START = "bedtime_start"
    private const val KEY_BEDTIME_END = "bedtime_end"
    private const val KEY_GAMBLING = "block_gambling"
    private const val KEY_BLACKLIST = "blacklist"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_DIRTY = "dirty"
}
