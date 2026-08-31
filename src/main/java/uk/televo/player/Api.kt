package uk.televo.player

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Talks to the Televo server (the PHP API you deployed). Call from a background thread. */
object Api {

    data class Status(val ok: Boolean, val active: Boolean, val status: String, val expiresAt: String?)
    data class HostItem(val id: Int, val name: String, val baseUrl: String, val type: String)
    data class Playlist(
        val kind: String, val label: String,
        val host: String?, val username: String?, val password: String?, val url: String?
    )

    class ApiException(message: String) : Exception(message)

    private fun endpoint(c: Context): String =
        (Prefs.baseUrl(c) ?: throw ApiException("No server set")) + "/api/index.php"

    /** POST form-encoded fields, return parsed JSON. */
    private fun call(c: Context, action: String, params: Map<String, String> = emptyMap()): JSONObject {
        val key = Prefs.apiKey(c) ?: throw ApiException("No API key")
        val form = StringBuilder("action=").append(enc(action))
        form.append("&device_code=").append(enc(Prefs.deviceCode(c)))
        for ((k, v) in params) form.append("&").append(enc(k)).append("=").append(enc(v))

        val conn = (URL(endpoint(c)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Authorization", "ApiKey $key")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        try {
            conn.outputStream.use { os: OutputStream -> os.write(form.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (body.isBlank()) throw ApiException("Empty response (HTTP $code)")
            return JSONObject(body)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(e.message ?: "Network error")
        } finally {
            conn.disconnect()
        }
    }

    fun register(c: Context): Status = parseStatus(call(c, "register",
        mapOf("model" to android.os.Build.MODEL, "app_version" to BuildInfo.VERSION)))

    fun status(c: Context): Status = parseStatus(call(c, "status"))

    fun playlists(c: Context): List<Playlist> {
        val j = call(c, "playlists")
        if (!j.optBoolean("ok", false)) {
            if (j.optString("error") == "not active") throw ApiException("not_active")
            throw ApiException(j.optString("error", "Failed"))
        }
        val arr = j.optJSONArray("playlists") ?: return emptyList()
        val out = ArrayList<Playlist>()
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            out.add(
                Playlist(
                    kind = p.optString("kind"),
                    label = p.optString("label"),
                    host = p.optString("host", null),
                    username = p.optString("username", null),
                    password = p.optString("password", null),
                    url = p.optString("url", null)
                )
            )
        }
        return out
    }

    fun hosts(c: Context): List<HostItem> {
        val j = call(c, "hosts")
        val arr = j.optJSONArray("hosts") ?: return emptyList()
        val out = ArrayList<HostItem>()
        for (i in 0 until arr.length()) {
            val h = arr.getJSONObject(i)
            out.add(HostItem(h.optInt("id"), h.optString("name"), h.optString("base_url"), h.optString("type")))
        }
        return out
    }

    /** Returns Pair(success, message). */
    fun selfActivate(c: Context, hostId: Int, user: String, pass: String): Pair<Boolean, String> {
        val j = call(c, "self_activate", mapOf(
            "host_id" to hostId.toString(), "username" to user, "password" to pass
        ))
        return if (j.optBoolean("ok", false)) true to "Activated"
        else false to j.optString("reason", j.optString("error", "Activation failed"))
    }

    private fun parseStatus(j: JSONObject) = Status(
        ok = j.optBoolean("ok", false),
        active = j.optBoolean("active", false),
        status = j.optString("status", "unknown"),
        expiresAt = if (j.isNull("expires_at")) null else j.optString("expires_at", null)
    )

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
