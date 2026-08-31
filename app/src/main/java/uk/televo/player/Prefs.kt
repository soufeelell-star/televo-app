package uk.televo.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local storage for the customer's login: which server they picked and their
 * Xtream username / password. No activation, no expiry — once saved, they stay
 * logged in until they log out.
 */
object Prefs {
    private const val FILE = "televo"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun host(c: Context): String? = sp(c).getString("host", null)
    fun username(c: Context): String? = sp(c).getString("username", null)
    fun password(c: Context): String? = sp(c).getString("password", null)
    fun serverName(c: Context): String? = sp(c).getString("server_name", null)

    fun isLoggedIn(c: Context): Boolean =
        !host(c).isNullOrBlank() && !username(c).isNullOrBlank() && !password(c).isNullOrBlank()

    fun saveLogin(c: Context, serverName: String, host: String, user: String, pass: String) {
        sp(c).edit()
            .putString("server_name", serverName)
            .putString("host", normalize(host))
            .putString("username", user.trim())
            .putString("password", pass.trim())
            .apply()
    }

    // ---- subscription expiry (unix seconds; 0 = unlimited/lifetime) ----
    fun setExpiry(c: Context, unix: Long?) { sp(c).edit().putLong("expires_at", unix ?: 0L).apply() }
    fun expiry(c: Context): Long = sp(c).getLong("expires_at", 0L)

    // ---- interface language (ISO code, default English) ----
    fun language(c: Context): String = sp(c).getString("lang", "en") ?: "en"
    fun setLanguage(c: Context, code: String) { sp(c).edit().putString("lang", code).apply() }

    // ---- time-shift offset in minutes (0 = live) ----
    fun timeshift(c: Context): Int = sp(c).getInt("timeshift_min", 0)
    fun setTimeshift(c: Context, minutes: Int) { sp(c).edit().putInt("timeshift_min", minutes).apply() }

    fun logout(c: Context) {
        sp(c).edit()
            .remove("host").remove("username").remove("password").remove("server_name")
            .remove("expires_at")
            .apply()
    }

    /** The saved login as a playlist the content screens (Live/Movies/Series/Radio) already understand. */
    fun playlist(c: Context): Api.Playlist? {
        if (!isLoggedIn(c)) return null
        return Api.Playlist(
            kind = "xtream",
            label = serverName(c) ?: "Televo",
            host = host(c),
            username = username(c),
            password = password(c),
            url = null
        )
    }

    fun normalize(url: String): String = url.trim().trimEnd('/')

    // ---- server list cache (loaded from panel.televo.uk) ----

    fun cacheServers(c: Context, list: List<Servers.Server>) {
        val arr = JSONArray()
        for (s in list) arr.put(JSONObject().put("name", s.name).put("base_url", s.baseUrl))
        sp(c).edit().putString("servers_cache", arr.toString()).apply()
    }

    private fun cachedServers(c: Context): List<Servers.Server> {
        val raw = sp(c).getString("servers_cache", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull {
                val o = arr.optJSONObject(it) ?: return@mapNotNull null
                val n = o.optString("name"); val b = o.optString("base_url")
                if (n.isNotBlank() && b.isNotBlank()) Servers.Server(n, b) else null
            }
        } catch (e: Exception) { emptyList() }
    }

    /** Best server list we can show right now: cached (from panel) if any, else the built-in fallback. */
    fun servers(c: Context): List<Servers.Server> =
        cachedServers(c).ifEmpty { Servers.FALLBACK }
}
