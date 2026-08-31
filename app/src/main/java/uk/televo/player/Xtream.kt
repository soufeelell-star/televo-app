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
    data class Epg(
        val time: String, val title: String, val desc: String, val now: Boolean,
        val hasArchive: Boolean = false, val startUnix: Long = 0, val stopUnix: Long = 0, val past: Boolean = false
    ) {
        /** A programme that already aired and is available to replay from the provider's archive. */
        val catchup: Boolean get() = past && hasArchive && startUnix > 0 && stopUnix > startUnix
    }

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

    /**
     * Catch-up EPG: the fuller day listing (past + now + next) with the provider's
     * archive flags, so past programmes that were recorded can be replayed.
     */
    fun catchupEpg(p: Api.Playlist, streamId: String): List<Epg> {
        val host = p.host ?: return emptyList()
        val user = p.username ?: return emptyList()
        val pass = p.password ?: return emptyList()
        val url = "$host/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=get_simple_data_table&stream_id=$streamId"
        val body = runCatching { httpGet(url) }.getOrNull() ?: return emptyList()
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()
        val now = System.currentTimeMillis() / 1000
        val out = ArrayList<Epg>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val start = o.optString("start_timestamp", "0").toLongOrNull() ?: 0
            val stop = o.optString("stop_timestamp", "0").toLongOrNull() ?: 0
            val hasArchive = o.optString("has_archive", "0") == "1" || o.optInt("has_archive", 0) == 1
            out.add(
                Epg(
                    time = if (start > 0) fmt(start) else "",
                    title = decode(o.optString("title", "")),
                    desc = decode(o.optString("description", "")),
                    now = start in 1..now && now < stop,
                    hasArchive = hasArchive,
                    startUnix = start,
                    stopUnix = stop,
                    past = stop in 1 until now
                )
            )
        }
        return out
    }

    /** Playback URL for a catch-up (archived) programme. */
    fun catchupUrl(p: Api.Playlist, streamId: String, startUnix: Long, stopUnix: Long): String {
        val durationMin = (((stopUnix - startUnix) / 60).coerceAtLeast(1)).toInt()
        val start = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(Date(startUnix * 1000L))
        return "${p.host}/timeshift/${p.username}/${p.password}/$durationMin/$start/$streamId.ts"
    }

    /** Live stream URL (HLS preferred). */
    fun playUrl(p: Api.Playlist, streamId: String): String =
        "${p.host}/live/${p.username}/${p.password}/$streamId.m3u8"

    /** Raw MPEG-TS live URL — fallback for channels/qualities the HLS wrapper won't serve (e.g. some 4K). */
    fun playUrlTs(p: Api.Playlist, streamId: String): String =
        "${p.host}/live/${p.username}/${p.password}/$streamId.ts"

    /**
     * Catch-up / time-shift URL: play from [minutes] ago. Works where the
     * provider supports Xtream time-shift; otherwise the stream simply won't
     * load and the user can switch back to Live.
     */
    fun timeshiftUrl(p: Api.Playlist, streamId: String, minutes: Int): String {
        val start = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
            .format(Date(System.currentTimeMillis() - minutes * 60_000L))
        return "${p.host}/timeshift/${p.username}/${p.password}/$minutes/$start/$streamId.ts"
    }

    // ---------------- Movies (VOD) ----------------
    data class Vod(val name: String, val streamId: String, val icon: String?, val categoryId: String, val ext: String, val rating: String)
    class VodCatalogue(val categories: List<Category>, val byCategory: Map<String, List<Vod>>)

    fun loadVod(p: Api.Playlist): VodCatalogue {
        val host = p.host ?: return VodCatalogue(emptyList(), emptyMap())
        val user = p.username ?: return VodCatalogue(emptyList(), emptyMap())
        val pass = p.password ?: return VodCatalogue(emptyList(), emptyMap())
        val base = "$host/player_api.php?username=${enc(user)}&password=${enc(pass)}"
        val catNames = HashMap<String, String>(); val order = ArrayList<String>()
        runCatching {
            val ca = JSONArray(httpGet("$base&action=get_vod_categories"))
            for (i in 0 until ca.length()) { val o = ca.optJSONObject(i) ?: continue; val id = o.optString("category_id"); if (id.isNotEmpty()) { catNames[id] = o.optString("category_name", "Category"); order.add(id) } }
        }
        val grouped = LinkedHashMap<String, ArrayList<Vod>>()
        runCatching {
            val sa = JSONArray(httpGet("$base&action=get_vod_streams"))
            for (i in 0 until sa.length()) {
                val o = sa.optJSONObject(i) ?: continue
                val cid = o.optString("category_id", "0")
                grouped.getOrPut(cid) { ArrayList() }.add(
                    Vod(o.optString("name", "Movie"), o.optString("stream_id", ""), o.optString("stream_icon", null), cid, o.optString("container_extension", "mp4"), o.optString("rating", ""))
                )
            }
        }
        val cats = ArrayList<Category>(); val seen = HashSet<String>()
        for (id in order) { val l = grouped[id] ?: continue; cats.add(Category(id, catNames[id] ?: "Category", l.size)); seen.add(id) }
        for ((id, l) in grouped) if (!seen.contains(id)) cats.add(Category(id, catNames[id] ?: "Other", l.size))
        return VodCatalogue(cats, grouped)
    }

    fun vodPlayUrl(p: Api.Playlist, streamId: String, ext: String): String =
        "${p.host}/movie/${p.username}/${p.password}/$streamId.${ext.ifBlank { "mp4" }}"

    data class VodInfo(val plot: String, val genre: String, val cast: String, val director: String, val release: String, val duration: String, val rating: String, val cover: String?)

    fun vodInfo(p: Api.Playlist, streamId: String): VodInfo {
        val url = "${p.host}/player_api.php?username=${enc(p.username ?: "")}&password=${enc(p.password ?: "")}&action=get_vod_info&vod_id=$streamId"
        val info = runCatching { JSONObject(httpGet(url)).optJSONObject("info") }.getOrNull()
        return VodInfo(
            info?.optString("plot", "") ?: "", info?.optString("genre", "") ?: "", info?.optString("cast", "") ?: "",
            info?.optString("director", "") ?: "", info?.optString("releasedate", "") ?: "", info?.optString("duration", "") ?: "",
            info?.optString("rating", "") ?: "", info?.optString("movie_image", null)
        )
    }

    // ---------------- Series ----------------
    data class Serie(val seriesId: String, val name: String, val cover: String?, val categoryId: String, val plot: String)
    class SeriesCatalogue(val categories: List<Category>, val byCategory: Map<String, List<Serie>>)

    fun loadSeries(p: Api.Playlist): SeriesCatalogue {
        val host = p.host ?: return SeriesCatalogue(emptyList(), emptyMap())
        val user = p.username ?: return SeriesCatalogue(emptyList(), emptyMap())
        val pass = p.password ?: return SeriesCatalogue(emptyList(), emptyMap())
        val base = "$host/player_api.php?username=${enc(user)}&password=${enc(pass)}"
        val catNames = HashMap<String, String>(); val order = ArrayList<String>()
        runCatching {
            val ca = JSONArray(httpGet("$base&action=get_series_categories"))
            for (i in 0 until ca.length()) { val o = ca.optJSONObject(i) ?: continue; val id = o.optString("category_id"); if (id.isNotEmpty()) { catNames[id] = o.optString("category_name", "Category"); order.add(id) } }
        }
        val grouped = LinkedHashMap<String, ArrayList<Serie>>()
        runCatching {
            val sa = JSONArray(httpGet("$base&action=get_series"))
            for (i in 0 until sa.length()) {
                val o = sa.optJSONObject(i) ?: continue
                val cid = o.optString("category_id", "0")
                grouped.getOrPut(cid) { ArrayList() }.add(
                    Serie(o.optString("series_id", ""), o.optString("name", "Series"), o.optString("cover", null), cid, o.optString("plot", ""))
                )
            }
        }
        val cats = ArrayList<Category>(); val seen = HashSet<String>()
        for (id in order) { val l = grouped[id] ?: continue; cats.add(Category(id, catNames[id] ?: "Category", l.size)); seen.add(id) }
        for ((id, l) in grouped) if (!seen.contains(id)) cats.add(Category(id, catNames[id] ?: "Other", l.size))
        return SeriesCatalogue(cats, grouped)
    }

    data class Episode(val id: String, val title: String, val season: Int, val num: String, val ext: String)
    class SeriesDetail(val cover: String?, val plot: String, val seasons: List<Int>, val episodesBySeason: Map<Int, List<Episode>>)

    fun seriesInfo(p: Api.Playlist, seriesId: String): SeriesDetail {
        val url = "${p.host}/player_api.php?username=${enc(p.username ?: "")}&password=${enc(p.password ?: "")}&action=get_series_info&series_id=$seriesId"
        val root = runCatching { JSONObject(httpGet(url)) }.getOrNull()
        val info = root?.optJSONObject("info")
        val episodesObj = root?.optJSONObject("episodes")
        val bySeason = LinkedHashMap<Int, List<Episode>>()
        if (episodesObj != null) {
            val keys = episodesObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val sNum = k.toIntOrNull() ?: continue
                val arr = episodesObj.optJSONArray(k) ?: continue
                val eps = ArrayList<Episode>()
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    eps.add(Episode(e.optString("id", ""), e.optString("title", "Episode ${e.optString("episode_num", "")}"), sNum, e.optString("episode_num", ""), e.optString("container_extension", "mp4")))
                }
                bySeason[sNum] = eps
            }
        }
        return SeriesDetail(info?.optString("cover", null), info?.optString("plot", "") ?: "", bySeason.keys.sorted(), bySeason)
    }

    fun seriesPlayUrl(p: Api.Playlist, episodeId: String, ext: String): String =
        "${p.host}/series/${p.username}/${p.password}/$episodeId.${ext.ifBlank { "mp4" }}"

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
