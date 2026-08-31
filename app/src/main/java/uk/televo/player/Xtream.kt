package uk.televo.player

import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Reads live channels from an Xtream Codes playlist and builds playable URLs. */
object Xtream {

    data class Channel(val name: String, val streamId: String, val num: String, val icon: String?)

    /** Fetch up to [limit] live streams for this playlist. Call on a background thread. */
    fun liveStreams(p: Api.Playlist, limit: Int = 600): List<Channel> {
        val host = p.host ?: return emptyList()
        val user = p.username ?: return emptyList()
        val pass = p.password ?: return emptyList()
        val url = "$host/player_api.php?username=${enc(user)}&password=${enc(pass)}&action=get_live_streams"
        val body = httpGet(url)
        val arr = try { JSONArray(body) } catch (e: Exception) { return emptyList() }
        val out = ArrayList<Channel>()
        val n = minOf(arr.length(), limit)
        for (i in 0 until n) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Channel(
                    name = o.optString("name", "Channel"),
                    streamId = o.optString("stream_id", ""),
                    num = o.optString("num", (i + 1).toString()),
                    icon = o.optString("stream_icon", null)
                )
            )
        }
        return out
    }

    /** Live stream URL. HLS (.m3u8) is preferred for smooth adaptive playback. */
    fun playUrl(p: Api.Playlist, streamId: String): String {
        return "${p.host}/live/${p.username}/${p.password}/$streamId.m3u8"
    }

    private fun httpGet(spec: String): String {
        val conn = (URL(spec).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 20000
            setRequestProperty("User-Agent", "Televo/1.0")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            return BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
