package uk.televo.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL

/** Minimal async image loader (posters/covers) with an in-memory cache. No external library. */
object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(url: String?, into: ImageView) {
        into.tag = url
        if (url.isNullOrBlank()) { into.setImageDrawable(null); return }
        cache.get(url)?.let { into.setImageBitmap(it); return }
        into.setImageDrawable(null)
        Net.run {
            var bmp: Bitmap? = null
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000; readTimeout = 12000; instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Televo/1.0")
                }
                bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
                conn.disconnect()
            } catch (e: Exception) { /* ignore broken images */ }
            val b = bmp
            if (b != null) {
                cache.put(url, b)
                Net.ui { if (into.tag == url) into.setImageBitmap(b) }
            }
        }
    }
}
