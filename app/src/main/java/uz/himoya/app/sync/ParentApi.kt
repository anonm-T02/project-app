package uz.himoya.app.sync

import org.json.JSONObject
import uz.himoya.app.policy.Policy
import java.io.IOException

/** Ota-ona ekranida ko'rsatiladigan bola qurilmasi. */
data class ChildDevice(
    val id: String,
    val name: String,
    val lastSeenAt: Long,
    val protectionOn: Boolean,
    val bedtimeActive: Boolean,
    val alwaysOn: Boolean?,
    val checked: Long,
    val blocked: Long,
    val policy: Policy,
    val events: List<BlockEvent>,
)

class DeviceList(val serverTime: Long, val devices: List<ChildDevice>)

/** Ota-ona telefoni tomoni. Barcha chaqiruvlar bloklaydi: fon oqimida ishlating. */
object ParentApi {

    /** Oila hali yaratilmagan bo'lsa yaratadi va kalitni saqlaydi. */
    @Throws(IOException::class)
    private fun token(): String {
        SyncStore.current.parentToken?.let { return it }
        val created = Api.call("POST", "/api/family", null, JSONObject())
        val token = created.getString("parentToken")
        SyncStore.update { it.copy(parentToken = token) }
        return token
    }

    @Throws(IOException::class)
    fun devices(): DeviceList {
        val response = try {
            Api.call("GET", "/api/devices", token())
        } catch (e: ApiException) {
            if (e.status != 401) throw e
            // Server ma'lumotlari tozalangan: oilani qaytadan yaratamiz.
            SyncStore.update { it.copy(parentToken = null) }
            Api.call("GET", "/api/devices", token())
        }
        val array = response.optJSONArray("devices")
        val list = ArrayList<ChildDevice>()
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                list.add(parseDevice(item))
            }
        }
        return DeviceList(response.optLong("serverTime", System.currentTimeMillis()), list)
    }

    @Throws(IOException::class)
    fun pairCode(childName: String): String =
        Api.call("POST", "/api/pair-code", token(), JSONObject().put("childName", childName))
            .getString("code")

    @Throws(IOException::class)
    fun setPolicy(deviceId: String, policy: Policy) {
        Api.call(
            "PUT",
            "/api/devices/$deviceId/policy",
            token(),
            JSONObject().put("policy", PolicyJson.toJson(policy)),
        )
    }

    @Throws(IOException::class)
    fun remove(deviceId: String) {
        Api.call("DELETE", "/api/devices/$deviceId", token())
    }

    private fun parseDevice(json: JSONObject): ChildDevice {
        val status = json.optJSONObject("status") ?: JSONObject()
        val policyJson = json.optJSONObject("policy")
        val events = ArrayList<BlockEvent>()
        val eventsJson = json.optJSONArray("events")
        if (eventsJson != null) {
            for (i in 0 until eventsJson.length()) {
                val e = eventsJson.optJSONObject(i) ?: continue
                events.add(BlockEvent(e.optString("domain"), e.optString("reason"), e.optLong("at")))
            }
        }
        return ChildDevice(
            id = json.optString("id"),
            name = json.optString("name"),
            lastSeenAt = json.optLong("lastSeenAt"),
            protectionOn = status.optBoolean("protectionOn", false),
            bedtimeActive = status.optBoolean("bedtimeActive", false),
            alwaysOn = if (status.has("alwaysOn") && !status.isNull("alwaysOn")) status.optBoolean("alwaysOn") else null,
            checked = status.optLong("checked"),
            blocked = status.optLong("blocked"),
            policy = if (policyJson != null) PolicyJson.fromJson(policyJson) else Policy(),
            events = events,
        )
    }
}
