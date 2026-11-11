package uz.himoya.app.guard

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings

/**
 * Telefon sozlamalarida qo'lda yozilgan Private DNS provayderini aniqlaydi.
 * "Avtomatik" rejim filtrga xalaqit bermaydi; qo'lda yozilgan provayder esa
 * DNS so'rovlarini shifrlab, filtrni chetlab o'tishi mumkin.
 */
object PrivateDnsCheck {

    /** Provayder nomi (masalan "dns.example.com"), sozlanmagan bo'lsa null. */
    fun customProvider(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            for (network in cm.allNetworks) {
                val name = cm.getLinkProperties(network)?.privateDnsServerName
                if (!name.isNullOrBlank()) return name
            }
        } catch (e: Exception) {
            // Pastdagi zaxira usulga o'tamiz.
        }
        return try {
            val resolver = context.contentResolver
            if (Settings.Global.getString(resolver, "private_dns_mode") == "hostname") {
                Settings.Global.getString(resolver, "private_dns_specifier")?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
