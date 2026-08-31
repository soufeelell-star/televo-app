package uk.televo.player

import android.content.Context
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

    /**
     * Validate credentials against the server. Returns Pair(success, message).
     * Success = the panel authenticated the user (auth == 1) and the line isn't
     * banned/disabled. Call from a background thread.
     */
    fun xtreamLogin(host: String, user: String, pass: String): Pair<Boolean, String> {
        val base = Prefs.normalize(host)
        val url = "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}"
        val body = try {
            httpGet(url)
        } catch (e: Exception) {
            return false to "Can't reach the server. Check your internet and try again."
        }
        val root = try { JSONObject(body) } catch (e: Exception) {
            return false to "Server returned an unexpected response."
        }
        val info = root.optJSONObject("user_info")
            ?: return false to "Wrong username or password."

        val auth = info.optInt("auth", 0)
        if (auth != 1) return false to "Wrong username or password."

        when (info.optString("status", "").lowercase()) {
            "banned"   -> return false to "This account is banned. Contact your provider."
            "disabled" -> return false to "This account is disabled. Contact your provider."
            "expired"  -> return false to "This subscription has expired. Contact your provider."
        }
        return true to "OK"
    }

    /** Backwards-compatible helper the content screens use: the single saved login. */
    fun playlists(c: Context): List<Playlist> = listOfNotNull(Prefs.playlist(c))

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
