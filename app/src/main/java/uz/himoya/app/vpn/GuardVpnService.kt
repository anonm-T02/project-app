package uz.himoya.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import uz.himoya.app.MainActivity
import uz.himoya.app.R
import uz.himoya.app.notify.BlockNotifier

/**
 * Lokal, faqat DNS uchun VPN.
 *
 * Tunnelga faqat bitta virtual DNS manzili yo'naltiriladi. Qolgan barcha trafik
 * (sahifalar, video, xabarlar) odatdagi tarmoqdan o'tadi va bu ilovaga ko'rinmaydi.
 */
class GuardVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var forwarder: DnsForwarder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopProtection()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH) {
            // Ekran qayta ochilganda tizim sozlamalarini yangilash uchun.
            if (tun == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            publishSystemSettings()
            return START_STICKY
        }
        startProtection()
        return START_STICKY
    }

    /** Boshqa VPN yoqilganda yoki foydalanuvchi tizim sozlamasidan uzganda chaqiriladi. */
    override fun onRevoke() {
        stopProtection()
        stopSelf()
    }

    override fun onDestroy() {
        stopProtection()
        super.onDestroy()
    }

    private fun startProtection() {
        if (!showForegroundNotification()) {
            stopProtection()
            stopSelf()
            return
        }
        if (tun != null) return

        VpnState.setStatus(ProtectionStatus.STARTING)
        val pfd = try {
            buildTunnel()
        } catch (e: Exception) {
            Log.e(TAG, "Tunnelni ochib bo'lmadi", e)
            null
        }
        if (pfd == null) {
            // Ruxsat berilmagan yoki tizim rad etgan.
            stopProtection()
            stopSelf()
            return
        }
        tun = pfd
        val notifier = BlockNotifier(this)
        forwarder = DnsForwarder(
            tun = pfd,
            protect = { socket -> protect(socket) },
            onSiteBlocked = { domain -> notifier.onBlocked(domain) },
        ).also { it.start() }
        VpnState.setStatus(ProtectionStatus.ON)
        publishSystemSettings()
    }

    private fun publishSystemSettings() {
        val settings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            SystemVpnSettings(alwaysOn = isAlwaysOn, lockdown = isLockdownEnabled)
        } else {
            SystemVpnSettings()
        }
        VpnState.setSystemSettings(settings)
    }

    private fun stopProtection() {
        forwarder?.stop()
        forwarder = null
        tun?.let { pfd -> runCatching { pfd.close() } }
        tun = null
        VpnState.setStatus(ProtectionStatus.OFF)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun buildTunnel(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(DnsForwarder.MAX_PACKET)
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(VIRTUAL_DNS)
            // DNS-only: tunnelga faqat virtual DNS manzili kiradi.
            .addRoute(VIRTUAL_DNS, 32)
            // Qolgan IPv4/IPv6 trafik bloklanmasin, odatdagi tarmoqdan o'tsin.
            .allowFamily(OsConstants.AF_INET)
            .allowFamily(OsConstants.AF_INET6)
            .setConfigureIntent(openAppIntent())
        // Mashhur ochiq DNS manzillari ham tunnelga olinadi: ularga to'g'ridan-to'g'ri
        // yuborilgan DNS so'rovlari filtrdan o'tadi, shifrlangan DNS (DoT/DoH) esa o'tmaydi.
        for (address in PUBLIC_DNS_ADDRESSES) {
            builder.addRoute(address, 32)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        return builder.establish()
    }

    private fun showForegroundNotification(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Foreground xizmatni boshlab bo'lmadi", e)
            false
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setColor(ContextCompat.getColor(this, R.color.safe))
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val TAG = "GuardVpnService"
        private const val ACTION_STOP = "uz.himoya.app.action.STOP"
        private const val ACTION_REFRESH = "uz.himoya.app.action.REFRESH"
        private const val CHANNEL_ID = "protection_status"
        private const val NOTIFICATION_ID = 1

        private const val TUN_ADDRESS = "10.111.222.1"
        private const val VIRTUAL_DNS = "10.111.222.2"

        /** 1.1.1.3 va 1.0.0.3 bu ro'yxatda yo'q: ular bizning yuqori serverimiz. */
        private val PUBLIC_DNS_ADDRESSES = listOf(
            "8.8.8.8", "8.8.4.4", // Google
            "1.1.1.1", "1.0.0.1", "1.1.1.2", "1.0.0.2", // Cloudflare
            "9.9.9.9", "149.112.112.112", "9.9.9.10", "149.112.112.10", "9.9.9.11", "149.112.112.11", // Quad9
            "208.67.222.222", "208.67.220.220", "208.67.222.123", "208.67.220.123", // OpenDNS
            "94.140.14.14", "94.140.15.15", "94.140.14.140", "94.140.14.141", // AdGuard
            "94.140.14.15", "94.140.15.16",
            "77.88.8.8", "77.88.8.1", "77.88.8.88", "77.88.8.2", "77.88.8.7", "77.88.8.3", // Yandex
            "76.76.2.0", "76.76.10.0", // ControlD
            "45.90.28.0", "45.90.30.0", // NextDNS
            "185.228.168.9", "185.228.169.9", "185.228.168.168", "185.228.169.168", // CleanBrowsing
            "4.2.2.1", "4.2.2.2", "64.6.64.6", "64.6.65.6", // Level3, Verisign
            "8.26.56.26", "8.20.247.20", // Comodo
            "114.114.114.114", "223.5.5.5", "223.6.6.6", "119.29.29.29", // Xitoy xizmatlari
        )

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, GuardVpnService::class.java),
            )
        }

        /** Faqat ilova ekrani ochiq paytda chaqiriladi. */
        fun refresh(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, GuardVpnService::class.java).setAction(ACTION_REFRESH)
                )
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GuardVpnService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
