package uz.himoya.app.sync

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uz.himoya.app.policy.Policy
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Server xato javob qaytarganda (masalan 401 yoki 404). */
class ApiException(val status: Int, message: String) : IOException(message)

/** Lokal server bilan oddiy JSON almashinuvi. Barcha chaqiruvlar bloklaydi: fon oqimida ishlating. */
object Api {

    @Throws(IOException::class)
    fun call(method: String, path: String, token: String? = null, body: JSONObject? = null): JSONObject {
        val base = SyncStore.current.serverUrl.trim().trimEnd('/')
        val connection = try {
            URL(base + path).openConnection() as HttpURLConnection
        } catch (e: ClassCastException) {
            throw IOException("Server manzili http:// bilan boshlanishi kerak")
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                JSONObject()
            }
            if (status !in 200..299) {
                throw ApiException(status, json.optString("error", "HTTP $status"))
            }
            return json
        } catch (e: SecurityException) {
            throw IOException(e.message ?: "Tarmoqqa ruxsat yo'q")
        } finally {
            connection.disconnect()
        }
    }
}

/** Qoidalarni server formatiga o'tkazish va qaytarish. */
object PolicyJson {

    fun toJson(policy: Policy): JSONObject = JSONObject()
        .put("bedtimeEnabled", policy.bedtimeEnabled)
        .put("bedtimeStart", policy.bedtimeStart)
        .put("bedtimeEnd", policy.bedtimeEnd)
        .put("blockGambling", policy.blockGambling)
        .put("blacklist", JSONArray(policy.blacklist.sorted()))
        .put("whitelist", JSONArray(policy.whitelist.sorted()))

    fun fromJson(json: JSONObject): Policy {
        val defaults = Policy()
        return Policy(
            bedtimeEnabled = json.optBoolean("bedtimeEnabled", defaults.bedtimeEnabled),
            bedtimeStart = json.optInt("bedtimeStart", defaults.bedtimeStart).coerceIn(0, 1439),
            bedtimeEnd = json.optInt("bedtimeEnd", defaults.bedtimeEnd).coerceIn(0, 1439),
            blockGambling = json.optBoolean("blockGambling", defaults.blockGambling),
            blacklist = strings(json.optJSONArray("blacklist")),
            whitelist = strings(json.optJSONArray("whitelist")),
        )
    }

    private fun strings(array: JSONArray?): Set<String> {
        if (array == null) return emptySet()
        val out = HashSet<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "")
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }
}
