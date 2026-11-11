package uz.himoya.app.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import uz.himoya.app.MainActivity
import uz.himoya.app.R
import uz.himoya.app.sync.ChildDevice
import uz.himoya.app.sync.ParentApi
import uz.himoya.app.sync.Role
import uz.himoya.app.sync.SyncStore
import uz.himoya.app.sync.UnlockRequest
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Ota-ona telefonida fonda ishlaydi: serverdan farzand qurilmalari holatini so'rab turadi va
 * muhim o'zgarishda bildirishnoma chiqaradi (ruxsat so'rovi, himoya o'chdi, qurilma ko'rinmayapti).
 *
 * Bu vaqtinchalik yechim: lokal serverda push-xabar yo'q. Haqiqiy backend'da bu xizmat o'rniga
 * FCM ishlatiladi, shunda doimiy bildirishnoma va fon so'rovlari kerak bo'lmaydi.
 */
class ParentWatchService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var alerts: ParentAlerts

    private val tick = object : Runnable {
        override fun run() {
            poll()
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SyncStore.init(this)
        alerts = ParentAlerts(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (SyncStore.current.role != Role.PARENT || !showForegroundNotification()) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun poll() {
        if (SyncStore.current.role != Role.PARENT) {
            stopSelf()
            return
        }
        runCatching {
            executor.execute {
                try {
                    val result = ParentApi.devices()
                    alerts.process(result.devices, result.serverTime)
                } catch (e: IOException) {
                    // Server javob bermadi: keyingi safar qayta uriniladi.
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Holatni tekshirib bo'lmadi", e)
                }
            }
        }
    }

    private fun showForegroundNotification(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Kuzatuv xizmatini boshlab bo'lmadi", e)
            false
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.watch_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            )
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.watch_notif_title))
            .setContentText(getString(R.string.watch_notif_text))
            .setOngoing(true)
            .setContentIntent(ParentAlerts.openAppIntent(this))
            .build()
    }

    companion object {
        private const val TAG = "ParentWatchService"
        private const val CHANNEL_ID = "family_watch"
        private const val NOTIFICATION_ID = 10
        private const val POLL_MS = 15_000L

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ParentWatchService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ParentWatchService::class.java))
        }
    }
}

/** Telefon qayta yoqilganda ota-ona telefonida kuzatuvni qayta ishga tushiradi. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        SyncStore.init(context)
        if (SyncStore.current.role == Role.PARENT) ParentWatchService.start(context)
    }
}

/**
 * Qaysi o'zgarish haqida xabar berish kerakligini aniqlaydi. Bir voqea uchun bir marta xabar
 * chiqishi uchun oldingi holat qurilmada saqlanadi.
 */
class ParentAlerts(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("parent_alerts", Context.MODE_PRIVATE)

    @Synchronized
    fun process(devices: List<ChildDevice>, serverTime: Long) {
        val notified = HashSet(prefs.getStringSet(KEY_REQUESTS, emptySet()).orEmpty())
        val editor = prefs.edit()
        val pendingIds = HashSet<String>()

        for (device in devices) {
            // 1) Yangi ruxsat so'rovlari
            for (request in device.requests) {
                if (request.status != UnlockRequest.PENDING) continue
                pendingIds.add(request.id)
                if (notified.add(request.id)) {
                    show(
                        id = request.id.hashCode(),
                        title = context.getString(R.string.alert_request_title, device.name),
                        text = request.domain,
                    )
                }
            }

            // 2) Himoya holati o'zgarishi
            val state = when {
                !device.protectionOn -> STATE_OFF
                serverTime - device.lastSeenAt > SILENT_AFTER_MS -> STATE_SILENT
                else -> STATE_ON
            }
            val key = KEY_STATE_PREFIX + device.id
            val previous = prefs.getString(key, null)
            if (previous != state) {
                editor.putString(key, state)
                val statusId = ("status:" + device.id).hashCode()
                when {
                    // Birinchi marta ko'rilgan qurilma uchun xabar chiqarilmaydi.
                    previous == null -> Unit
                    state == STATE_OFF -> show(
                        statusId,
                        context.getString(R.string.alert_off_title, device.name),
                        context.getString(R.string.alert_off_text),
                    )
                    state == STATE_SILENT -> show(
                        statusId,
                        context.getString(R.string.alert_silent_title, device.name),
                        context.getString(R.string.alert_silent_text),
                    )
                    else -> cancel(statusId)
                }
            }
        }

        // Javob berilgan so'rovlar ro'yxatdan chiqariladi, to'plam o'sib ketmaydi.
        notified.retainAll(pendingIds)
        editor.putStringSet(KEY_REQUESTS, notified).apply()
    }

    private fun show(id: Int, title: String, text: String) {
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
                    context.getString(R.string.alert_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(ContextCompat.getColor(context, R.color.accent))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build()
            manager.notify(id, notification)
        } catch (e: Exception) {
            Log.w("ParentAlerts", "Xabarni ko'rsatib bo'lmadi", e)
        }
    }

    private fun cancel(id: Int) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(id) }
    }

    companion object {
        private const val CHANNEL_ID = "family_alerts"
        private const val KEY_REQUESTS = "notified_requests"
        private const val KEY_STATE_PREFIX = "state_"
        private const val STATE_ON = "ON"
        private const val STATE_OFF = "OFF"
        private const val STATE_SILENT = "SILENT"

        /** Shu vaqtdan ko'p xabar kelmasa "ko'rinmayapti" deb ogohlantiriladi. */
        private const val SILENT_AFTER_MS = 3 * 60_000L

        fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
