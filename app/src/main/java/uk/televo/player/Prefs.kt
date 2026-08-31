package uk.televo.player

import android.content.Context

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

    fun logout(c: Context) {
        sp(c).edit()
            .remove("host").remove("username").remove("password").remove("server_name")
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
}
