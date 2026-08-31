package uk.televo.player

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads categories, channels and EPG from an Xtream Codes playlist. */
object Xtream {

    data class Category(val id: String, val name: String, var count: Int = 0)
    data class Channel(
        val name: String, val streamId: String, val num: String,
        val icon: String?, val categoryId: String, val epgId: String?
    )
    data class Epg(val time: String, val title: String, val desc: String, val now: Boolean)

    /** Full catalogue: categories (with counts) + channels grouped by category id. */
    class Catalogue(
        val categories: List<Category>,
        val byCategory: Map<String, List<Channel>>
    )

    fun loadCatalogue(p: Api.Playlist): Catalogue {
        val host = p.host ?: return Catalogue(emptyList(), emptyMap())
        val user = p.username ?: return Catalogue(emptyList(), emptyMap())
        val pass = p.password ?: return Catalogue(emptyList(), emptyMap())
        val base = "$host/player_api.php?username=${enc(user)}&password=${enc(pass)}"

        // category names
        val catNames = HashMap<String, String>()
        val order = ArrayList<String>()
        runCatching {
            val ca = JSONArray(httpGet("$base&action=get_live_categories"))
            for (i in 0 until ca.length()) {
                val o = ca.optJSONObject(i) ?: continue
                val id = o.optString("category_id")
                if (id.isNotEmpty()) { catNames[id] = o.optString("category_name", "Category"); order.add(id) }
            }
        }

        // all live streams, grouped
        val grouped = LinkedHashMap<String, ArrayList<Channel>>()
        runCatching {
            val sa = JSONArray(httpGet("$base&action=get_live_streams"))
            for (i in 0 until sa.length()) {
                val o = sa.optJSONObject(i) ?: continue
                val cid = o.optString("category_id", "0")
                val ch = Channel(
                    name = o.optString("name", "Channel"),
                    streamId = o.optString("stream_id", ""),
                    num = o.optString("num", (i + 1).toString()),
                    icon = o.optString("stream_icon", null),
                    categoryId = cid,
                    epgId = o.optString("epg_channel_id", null)
                )
                grouped.getOrPut(cid) { ArrayList() }.add(ch)
            }
        }

        // build ordered category list (only those that have channels), with counts
        val cats = ArrayList<Category>()
        val seen = HashSet<String>()
        for (id in order) {
            val list = grouped[id] ?: continue
            cats.add(Category(id, catNames[id] ?: "Category", list.size)); seen.add(id)
        }
        // any categories present in streams but not in the names list
        for ((id, list) in grouped) if (!seen.contains(id))
            cats.add(Category(id, catNames[id] ?: "Other", list.size))

        return Catalogue(cats, grouped)
    }

    /** Now & Next for a channel. */
    fun shortEpg(p: Api.Playlist, streamId: String, limit: Int = 6): List<Epg> {
        val host = p.host ?: return emptyList()
        val user = p.username ?: return emptyList()
        val pass = p.password ?: return emptyList()
        val url = "$host/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=get_short_epg&stream_id=$streamId&limit=$limit"
        val body = runCatching { httpGet(url) }.getOrNull() ?: return emptyList()
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()
        val out = ArrayList<Epg>()
        val now = System.currentTimeMillis() / 1000
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val start = o.optLong("start_timestamp", 0)
            val stop = o.optLong("stop_timestamp", 0)
            out.add(
                Epg(
                    time = if (start > 0) fmt(start) else "",
                    title = decode(o.optString("title", "")),
                    desc = decode(o.optString("description", "")),
                    now = start in 1..now && now < stop
                )
            )
        }
        return out
    }

    /** Live stream URL (HLS preferred). */
    fun playUrl(p: Api.Playlist, streamId: String): String =
        "${p.host}/live/${p.username}/${p.password}/$streamId.m3u8"

    private fun fmt(unix: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(unix * 1000))

    private fun decode(b64: String): String = try {
        if (b64.isBlank()) "" else String(Base64.decode(b64, Base64.DEFAULT)).trim()
    } catch (e: Exception) { b64 }

    private fun httpGet(spec: String): String {
        val conn = (URL(spec).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 12000; readTimeout = 25000
            setRequestProperty("User-Agent", "Televo/1.0")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            return BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } finally { conn.disconnect() }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
