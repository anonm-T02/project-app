package uz.himoya.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ProtectionStatus { OFF, STARTING, ON }

/**
 * Tizimning VPN sozlamalari. Android 10 dan pastda aniqlab bo'lmaydi, shunda null.
 * lockdown = "VPN'siz ulanishni bloklash".
 */
data class SystemVpnSettings(val alwaysOn: Boolean? = null, val lockdown: Boolean? = null)

/** Xizmat va ekran o'rtasidagi umumiy holat. Faqat xotirada saqlanadi. */
object VpnState {
    private val _status = MutableStateFlow(ProtectionStatus.OFF)
    val status: StateFlow<ProtectionStatus> = _status.asStateFlow()

    private val _checkedRequests = MutableStateFlow(0L)
    val checkedRequests: StateFlow<Long> = _checkedRequests.asStateFlow()

    private val _systemSettings = MutableStateFlow(SystemVpnSettings())
    val systemSettings: StateFlow<SystemVpnSettings> = _systemSettings.asStateFlow()

    fun setSystemSettings(value: SystemVpnSettings) {
        _systemSettings.value = value
    }

    fun setStatus(value: ProtectionStatus) {
        _status.value = value
    }

    fun countRequest() {
        _checkedRequests.update { it + 1 }
    }

    private val _blockedRequests = MutableStateFlow(0L)
    val blockedRequests: StateFlow<Long> = _blockedRequests.asStateFlow()

    fun countBlocked() {
        _blockedRequests.update { it + 1 }
    }
}
