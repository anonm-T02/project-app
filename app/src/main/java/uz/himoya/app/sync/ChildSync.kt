package uz.himoya.app.sync

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import uz.himoya.app.policy.PolicyRepo
import uz.himoya.app.vpn.VpnState
import java.io.IOException

/**
 * Bola telefoni tomoni: holatni serverga yuboradi va ota-ona belgilagan qoidalarni oladi.
 * Juftlanmagan bo'lsa hech narsa qilmaydi.
 */
object ChildSync {
    private const val TAG = "ChildSync"

    enum class Outcome { NOT_PAIRED, OK, POLICY_CHANGED, UNPAIRED, FAILED }

    /** Bloklaydi (tarmoq so'rovi): fon oqimidan chaqiring. */
    @Synchronized
    fun syncOnce(context: Context, protectionOn: Boolean): Outcome {
        val token = SyncStore.current.deviceToken ?: return Outcome.NOT_PAIRED

        val status = JSONObject()
            .put("protectionOn", protectionOn)
            .put("bedtimeActive", protectionOn && VpnState.bedtimeActive.value)
            .put("checked", VpnState.checkedRequests.value)
            .put("blocked", VpnState.blockedRequests.value)
            .put("appVersion", appVersion(context))
        VpnState.systemSettings.value.alwaysOn?.let { status.put("alwaysOn", it) }

        val events = BlockLog.snapshot()
        val eventsJson = JSONArray()
        for (event in events) {
            eventsJson.put(
                JSONObject().put("domain", event.domain).put("reason", event.reason).put("at", event.atUtcMs)
            )
        }

        // Qoidalar shu telefonda (PIN bilan) o'zgartirilgan bo'lsa, serverga ham yuboriladi.
        val sentPolicy = if (PolicyRepo.dirty) PolicyRepo.current else null
        val body = JSONObject().put("status", status).put("events", eventsJson)
        if (sentPolicy != null) body.put("policy", PolicyJson.toJson(sentPolicy))

        return try {
            val response = Api.call("POST", "/api/device/sync", token, body)
            BlockLog.confirm(events)
            val version = response.optInt("policyVersion", 0)
            val remote = response.optJSONObject("policy")
            var changed = false
            if (sentPolicy != null) {
                PolicyRepo.markClean(sentPolicy)
            } else if (remote != null && version != SyncStore.current.policyVersion && !PolicyRepo.dirty) {
                val policy = PolicyJson.fromJson(remote)
                changed = policy != PolicyRepo.current
                PolicyRepo.applyRemote(policy)
            } else if (remote == null) {
                // Serverda hali qoidalar yo'q: keyingi safar o'zimiznikini yuboramiz.
                PolicyRepo.markDirty()
            }
            if (version != SyncStore.current.policyVersion) {
                SyncStore.update { it.copy(policyVersion = version) }
            }
            if (changed) Outcome.POLICY_CHANGED else Outcome.OK
        } catch (e: ApiException) {
            if (e.status == 401) {
                // Ota-ona bu qurilmani ro'yxatdan o'chirgan.
                SyncStore.update { it.copy(deviceToken = null, childName = null, policyVersion = -1) }
                Outcome.UNPAIRED
            } else {
                Outcome.FAILED
            }
        } catch (e: IOException) {
            Outcome.FAILED
        } catch (e: RuntimeException) {
            Log.w(TAG, "Sinxronlashda xato", e)
            Outcome.FAILED
        }
    }

    private fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }
}
