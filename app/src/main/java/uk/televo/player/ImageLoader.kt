package uk.televo.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL

/** Minimal async image loader (channel logos, posters) with an in-memory cache. */
object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(url: String?, into: ImageView) {
        into.tag = url
        if (url.isNullOrBlank()) { into.setImageDrawable(null); return }
        cache.get(url)?.let { into.setImageBitmap(it); return }
        into.setImageDrawable(null)
        Net.run {
            val bmp = getOrFetch(url)
            if (bmp != null) Net.ui { if (into.tag == url) into.setImageBitmap(bmp) }
        }
    }

    /**
     * Load a channel logo: match the name against the tv-logos index first, then
     * fall back to the provider's own logo. All resolution + fetching is off the
     * UI thread, so it never slows the app.
     */
    fun loadChannel(ctx: Context, name: String, providerIcon: String?, into: ImageView) {
        into.tag = name
        into.setImageDrawable(null)
        Net.run {
            LogoIndex.ensureLoaded(ctx)
            val primary = LogoIndex.urlFor(name)
            var bmp: Bitmap? = primary?.let { getOrFetch(it) }
            if (bmp == null && !providerIcon.isNullOrBlank() && providerIcon != primary) {
                bmp = getOrFetch(providerIcon)
            }
            val b = bmp
            if (b != null) Net.ui { if (into.tag == name) into.setImageBitmap(b) }
        }
    }

    private fun getOrFetch(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        val b = runCatching { fetch(url, 0) }.getOrNull() ?: return null
        cache.put(url, b)
        return b
    }

    /** Fetch an image, following redirects manually (incl. http<->https, which HttpURLConnection won't). */
    private fun fetch(spec: String, hops: Int): Bitmap? {
        if (hops > 4) return null
        val conn = (URL(spec).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9000
            readTimeout = 12000
            instanceFollowRedirects = false
            // Some logo hosts block non-browser agents (why they show in other players but not here).
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Televo")
            setRequestProperty("Accept", "image/avif,image/webp,image/png,image/*,*/*")
        }
        try {
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: return null
                val next = URL(URL(spec), loc).toString()   // resolve relative/absolute
                return fetch(next, hops + 1)
            }
            if (code !in 200..299) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null
            // downsample very large logos to keep memory sane
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            val maxDim = 256
            while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } finally {
            conn.disconnect()
        }
    }
}
