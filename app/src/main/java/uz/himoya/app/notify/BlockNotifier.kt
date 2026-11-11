package uz.himoya.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import uz.himoya.app.BlockActivity
import uz.himoya.app.R
import uz.himoya.app.vpn.BlockReason

/**
 * Sayt bloklanganda bildirishnoma ko'rsatadi. Bosilsa blok ekrani ochiladi.
 *
 * Brauzerning o'zida sahifa ko'rsatib bo'lmaydi (HTTPS ochilmaydi), shuning uchun
 * bolaga nima bo'lganini shu bildirishnoma tushuntiradi.
 */
class BlockNotifier(context: Context) {
    private val context = context.applicationContext
    private val lastShown = HashMap<String, Long>()

    @Synchronized
    fun onBlocked(rawDomain: String, reason: BlockReason) {
        val domain = rawDomain.removePrefix("www.")
        val now = SystemClock.elapsedRealtime()
        val last = lastShown[domain]
        // Bitta sayt ketma-ket bir necha so'rov yuboradi: har biriga alohida xabar chiqarmaymiz.
        if (last != null && now - last < COOLDOWN_MS) return
        if (lastShown.size > 200) lastShown.clear()
        lastShown[domain] = now

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.block_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
            val open = PendingIntent.getActivity(
                context,
                0,
                BlockActivity.intent(context, domain, reason),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(context.getString(R.string.block_notif_title))
                .setContentText(context.getString(R.string.block_notif_text, domain, reasonText(reason)))
                .setColor(ContextCompat.getColor(context, R.color.accent))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .setContentIntent(open)
                .build()
            // Bitta raqam: yangi xabar eskisini almashtiradi, ro'yxat to'lib ketmaydi.
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Blok bildirishnomasini ko'rsatib bo'lmadi", e)
        }
    }

    private fun reasonText(reason: BlockReason): String = context.getString(
        when (reason) {
            BlockReason.GAMBLING -> R.string.block_reason_gambling_short
            BlockReason.FAMILY_LIST -> R.string.block_reason_family_short
            else -> R.string.block_reason_adult_short
        }
    )

    private companion object {
        const val TAG = "BlockNotifier"
        const val CHANNEL_ID = "blocked_pages"
        const val NOTIFICATION_ID = 2
        const val COOLDOWN_MS = 30_000L
    }
}
