package uk.televo.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Direct Xtream Codes access — the app talks straight to the chosen server's
 * player_api.php. No middleware, no activation, no expiry.
 */
object Api {

    /** A logged-in playlist. The content screens read host/username/password from here. */
    data class Playlist(
        val kind: String, val label: String,
        val host: String?, val username: String?, val password: String?, val url: String?
    )

    class ApiException(message: String) : Exception(message)

    /** Result of validating credentials against a server. */
    data class LoginResult(
        val ok: Boolean,
        val message: String,
        val status: String = "",
        /** Unix seconds when the subscription expires, or null = unlimited/lifetime. */
        val expiresAt: Long? = null
    )

    /**
     * Validate credentials against the server and read the account's expiry.
     * Call from a background thread.
     */
    fun login(host: String, user: String, pass: String): LoginResult {
        val base = Prefs.normalize(host)
        val url = "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}"
        val body = try {
            httpGet(url)
        } catch (e: Exception) {
            return LoginResult(false, "Can't reach the server. Check your internet and try again.")
        }
        val root = try { JSONObject(body) } catch (e: Exception) {
            return LoginResult(false, "Server returned an unexpected response.")
        }
        val info = root.optJSONObject("user_info")
            ?: return LoginResult(false, "Wrong username or password.")

        if (info.optInt("auth", 0) != 1) return LoginResult(false, "Wrong username or password.")

        val status = info.optString("status", "").lowercase()
        when (status) {
            "banned"   -> return LoginResult(false, "This account is banned. Contact your provider.", status)
            "disabled" -> return LoginResult(false, "This account is disabled. Contact your provider.", status)
            "expired"  -> return LoginResult(false, "This subscription has expired. Contact your provider.", status)
        }

        val exp = info.optString("exp_date", "").let { if (it.isBlank() || it == "null") null else it.toLongOrNull() }
        return LoginResult(true, "OK", status, exp)
    }

    /** Backwards-compatible helper the content screens use: the single saved login. */
    fun playlists(c: Context): List<Playlist> = listOfNotNull(Prefs.playlist(c))

    /**
     * Fetch the server list from the admin panel (panel.televo.uk). Returns the
     * hosts the admin has marked active. Throws on network/parse failure so the
     * caller can fall back to the cached/last-known list.
     */
    fun fetchHosts(): List<Servers.Server> {
        val body = httpGet("${Servers.PANEL}/api/app_hosts.php")
        val root = JSONObject(body)
        val arr: JSONArray = root.optJSONArray("hosts") ?: JSONArray()
        val out = ArrayList<Servers.Server>()
        for (i in 0 until arr.length()) {
            val h = arr.optJSONObject(i) ?: continue
            val name = h.optString("name", "").trim()
            val base = h.optString("base_url", "").trim()
            if (name.isNotEmpty() && base.isNotEmpty()) out.add(Servers.Server(name, base))
        }
        return out
    }

    private fun httpGet(spec: String): String {
        val conn = (URL(spec).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Televo/1.0")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (text.isBlank()) throw ApiException("Empty response (HTTP $code)")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
