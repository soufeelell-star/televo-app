package uk.televo.player

import android.content.Context

/**
 * Maps a channel name to a logo from the tv-logo/tv-logos collection.
 * The index (assets/tv_logos.tsv) is loaded once on a background thread and kept
 * in memory, so lookups are instant and never touch the UI thread.
 */
object LogoIndex {
    private const val CDN = "https://cdn.jsdelivr.net/gh/tv-logo/tv-logos@main/"

    @Volatile private var map: HashMap<String, String>? = null
    @Volatile private var loading = false

    private val QUALITY = hashSetOf(
        "hd", "fhd", "uhd", "sd", "4k", "fullhd", "hevc", "h265", "h264",
        "raw", "vip", "backup", "alt", "hq", "sdhd", "uhd4k", "4kuhd"
    )
    private val COUNTRY = hashSetOf(
        "uk", "us", "usa", "ca", "ie", "au", "nz", "fr", "de", "es", "it", "pt", "nl", "be",
        "pl", "br", "mx", "ar", "cl", "co", "pe", "tr", "gr", "ru", "in", "pk", "ph", "za",
        "ng", "se", "no", "dk", "fi", "ch", "at", "cz", "sk", "hu", "ro", "bg", "rs", "hr",
        "si", "ua", "il", "sa", "ae", "eg", "ma", "qa", "kw", "dz", "tn"
    )
    private val NUM = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5", "six" to "6",
        "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10", "eleven" to "11",
        "twelve" to "12", "thirteen" to "13", "fourteen" to "14", "fifteen" to "15",
        "sixteen" to "16", "seventeen" to "17", "eighteen" to "18", "nineteen" to "19", "twenty" to "20"
    )

    /** Load the index once (call from a background thread). */
    fun ensureLoaded(ctx: Context) {
        if (map != null) return
        synchronized(this) {
            if (map != null || loading) return
            loading = true
        }
        try {
            val m = HashMap<String, String>(9000)
            ctx.applicationContext.assets.open("tv_logos.tsv").bufferedReader().useLines { seq ->
                for (line in seq) {
                    val i = line.indexOf('\t')
                    if (i > 0) m[line.substring(0, i)] = line.substring(i + 1)
                }
            }
            map = m
        } catch (e: Exception) {
            // leave map null; callers fall back to the provider logo
        } finally {
            loading = false
        }
    }

    private fun norm(raw: String): String {
        val sb = StringBuilder()
        var tok = StringBuilder()
        fun flush() {
            if (tok.isEmpty()) return
            val t0 = tok.toString()
            tok = StringBuilder()
            if (QUALITY.contains(t0) || COUNTRY.contains(t0)) return
            val t = NUM[t0] ?: t0
            for (c in t) if (c in 'a'..'z' || c in '0'..'9') sb.append(c)
        }
        for (ch in raw.lowercase()) {
            if (ch in 'a'..'z' || ch in '0'..'9') tok.append(ch) else flush()
        }
        flush()
        return sb.toString()
    }

    /** CDN URL for the channel's logo, or null if not found in the index. */
    fun urlFor(name: String): String? {
        val m = map ?: return null
        val k = norm(name)
        if (k.isNotEmpty()) m[k]?.let { return CDN + it }
        // fallback: after a group separator, e.g. "AM | TV5 Monde"
        for (sep in charArrayOf('|', '·', '•', ':')) {
            val idx = name.lastIndexOf(sep)
            if (idx >= 0) {
                val k2 = norm(name.substring(idx + 1))
                if (k2.isNotEmpty()) m[k2]?.let { return CDN + it }
            }
        }
        return null
    }
}
