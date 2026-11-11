package uz.himoya.app.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Role { PARENT, CHILD }

/** Bu telefonning roli va server bilan bog'lanish ma'lumotlari. */
data class SyncState(
    val role: Role? = null,
    val serverUrl: String = SyncStore.DEFAULT_SERVER_URL,
    /** Ota-ona telefoni: oila kaliti. */
    val parentToken: String? = null,
    /** Bola telefoni: juftlashgandan keyin beriladigan qurilma kaliti. */
    val deviceToken: String? = null,
    val childName: String? = null,
    /** Bola telefoni: oxirgi qabul qilingan qoidalar versiyasi. */
    val policyVersion: Int = -1,
)

object SyncStore {
    /** Emulyatordan kompyuterdagi lokal serverga boradigan manzil. */
    const val DEFAULT_SERVER_URL = "http://10.0.2.2:8787"

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    val current: SyncState get() = _state.value

    private var prefs: SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("sync", Context.MODE_PRIVATE)
        prefs = p
        _state.value = SyncState(
            role = p.getString(KEY_ROLE, null)?.let { name -> Role.entries.firstOrNull { it.name == name } },
            serverUrl = p.getString(KEY_SERVER, null) ?: DEFAULT_SERVER_URL,
            parentToken = p.getString(KEY_PARENT_TOKEN, null),
            deviceToken = p.getString(KEY_DEVICE_TOKEN, null),
            childName = p.getString(KEY_CHILD_NAME, null),
            policyVersion = p.getInt(KEY_POLICY_VERSION, -1),
        )
    }

    @Synchronized
    fun update(transform: (SyncState) -> SyncState) {
        val next = transform(_state.value)
        _state.value = next
        prefs?.edit()
            ?.putString(KEY_ROLE, next.role?.name)
            ?.putString(KEY_SERVER, next.serverUrl)
            ?.putString(KEY_PARENT_TOKEN, next.parentToken)
            ?.putString(KEY_DEVICE_TOKEN, next.deviceToken)
            ?.putString(KEY_CHILD_NAME, next.childName)
            ?.putInt(KEY_POLICY_VERSION, next.policyVersion)
            ?.apply()
    }

    private const val KEY_ROLE = "role"
    private const val KEY_SERVER = "server_url"
    private const val KEY_PARENT_TOKEN = "parent_token"
    private const val KEY_DEVICE_TOKEN = "device_token"
    private const val KEY_CHILD_NAME = "child_name"
    private const val KEY_POLICY_VERSION = "policy_version"
}
