package uk.televo.player

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/** Local storage: server address, api key, and this device's stable code. */
object Prefs {
    private const val FILE = "televo"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun baseUrl(c: Context): String? = sp(c).getString("base_url", null)
    fun apiKey(c: Context): String? = sp(c).getString("api_key", null)

    fun setServer(c: Context, url: String, key: String) {
        sp(c).edit().putString("base_url", normalize(url)).putString("api_key", key.trim()).apply()
    }

    fun normalize(url: String): String {
        var u = url.trim().trimEnd('/')
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return u
    }

    /** A stable, human-readable device code like TV:1A:2B:3C:4D:5E */
    fun deviceCode(c: Context): String {
        val s = sp(c)
        s.getString("device_code", null)?.let { return it }
        val code = generate(c)
        s.edit().putString("device_code", code).apply()
        return code
    }

    private fun generate(c: Context): String {
        val android = try {
            Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) { null }
        val seed = (android?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString())
        val md = MessageDigest.getInstance("MD5").digest(seed.toByteArray())
        val parts = (0 until 5).map { String.format("%02X", md[it].toInt() and 0xFF) }
        return "TV:" + parts.joinToString(":")
    }
}
