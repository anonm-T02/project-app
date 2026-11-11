package uz.himoya.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.himoya.app.guard.AppLock
import uz.himoya.app.guard.PinStore
import uz.himoya.app.guard.PrivateDnsCheck
import uz.himoya.app.policy.PolicyRepo
import uz.himoya.app.policy.TimeText
import uz.himoya.app.ui.home.HomeScreen
import uz.himoya.app.ui.pin.PinMode
import uz.himoya.app.ui.pin.PinScreen
import uz.himoya.app.ui.settings.SettingsScreen
import uz.himoya.app.ui.theme.AppTheme
import uz.himoya.app.vpn.GuardVpnService
import uz.himoya.app.vpn.ProtectionStatus
import uz.himoya.app.vpn.VpnState

class MainActivity : ComponentActivity() {

    private lateinit var pinStore: PinStore
    private var privateDnsProvider by mutableStateOf<String?>(null)

    /** Tizimning "VPN ulanishiga ruxsat" oynasi. */
    private val vpnConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) GuardVpnService.start(this)
        }

    /** Bildirishnoma ruxsati rad etilsa ham himoya yoqiladi. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            askVpnConsent()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pinStore = PinStore(this)
        PolicyRepo.init(this)
        setContent {
            AppTheme {
                var pinSet by remember { mutableStateOf(pinStore.isSet()) }
                var showSettings by remember { mutableStateOf(false) }
                val policy by PolicyRepo.policy.collectAsStateWithLifecycle()

                when {
                    // Birinchi ochilish: ota-ona PIN o'rnatadi.
                    !pinSet -> PinScreen(
                        mode = PinMode.CREATE,
                        pinStore = pinStore,
                        onSuccess = {
                            pinSet = true
                            AppLock.unlocked = true
                        },
                    )

                    // Ilova har ochilganda PIN so'raladi.
                    !AppLock.unlocked -> PinScreen(
                        mode = PinMode.VERIFY,
                        pinStore = pinStore,
                        onSuccess = { AppLock.unlocked = true },
                    )

                    showSettings -> {
                        BackHandler { showSettings = false }
                        SettingsScreen(
                            policy = policy,
                            onChange = { transform ->
                                PolicyRepo.update(transform)
                                // Uyqu vaqti o'zgargan bo'lsa xizmat darhol moslashsin.
                                if (VpnState.status.value == ProtectionStatus.ON) {
                                    GuardVpnService.refresh(this@MainActivity)
                                }
                            },
                            onBack = { showSettings = false },
                        )
                    }

                    else -> {
                        val bedtimeActive by VpnState.bedtimeActive.collectAsStateWithLifecycle()
                        val status by VpnState.status.collectAsStateWithLifecycle()
                        val checked by VpnState.checkedRequests.collectAsStateWithLifecycle()
                        val blocked by VpnState.blockedRequests.collectAsStateWithLifecycle()
                        val system by VpnState.systemSettings.collectAsStateWithLifecycle()
                        HomeScreen(
                            status = status,
                            checkedRequests = checked,
                            blockedRequests = blocked,
                            systemSettings = system,
                            privateDnsProvider = privateDnsProvider,
                            bedtimeActive = bedtimeActive,
                            bedtimeEnd = TimeText.format(policy.bedtimeEnd),
                            onTurnOn = { turnOn() },
                            onTurnOff = { GuardVpnService.stop(this@MainActivity) },
                            onOpenSettings = { showSettings = true },
                            onOpenVpnSettings = { openSettings(Settings.ACTION_VPN_SETTINGS) },
                            onOpenNetworkSettings = { openSettings(Settings.ACTION_WIRELESS_SETTINGS) },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        privateDnsProvider = PrivateDnsCheck.customProvider(this)
        // Ota-ona sozlamalardan qaytganda "Doim yoqiq VPN" holati yangilansin.
        if (VpnState.status.value == ProtectionStatus.ON) GuardVpnService.refresh(this)
    }

    override fun onStop() {
        super.onStop()
        // Ilova fonga o'tdi: keyingi ochilishda yana PIN kerak.
        if (!isChangingConfigurations) AppLock.unlocked = false
    }

    private fun turnOn() {
        val needsNotificationPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            askVpnConsent()
        }
    }

    private fun askVpnConsent() {
        val consent = VpnService.prepare(this)
        if (consent != null) vpnConsent.launch(consent) else GuardVpnService.start(this)
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
