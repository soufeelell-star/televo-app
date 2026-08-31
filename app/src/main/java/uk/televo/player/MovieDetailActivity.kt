package uk.televo.player

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import uk.televo.player.databinding.ActivityMovieDetailBinding

class MovieDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityMovieDetailBinding
    private var playlist: Api.Playlist? = null
    private lateinit var streamId: String
    private lateinit var name: String
    private lateinit var ext: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMovieDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        streamId = intent.getStringExtra("stream_id") ?: ""
        name = intent.getStringExtra("name") ?: ""
        ext = intent.getStringExtra("ext") ?: "mp4"
        val icon = intent.getStringExtra("icon")

        b.dTitle.text = name
        b.dPlot.text = "Loading…"
        ImageLoader.load(icon, b.cover)

        b.btnPlay.setOnClickListener { play() }
        b.btnPlay.requestFocus()
        loadInfo()
    }

    private fun loadInfo() {
        Net.run {
            val xt = runCatching { Api.playlists(this).firstOrNull { it.kind == "xtream" } }.getOrNull()
            val info = if (xt != null) runCatching { Xtream.vodInfo(xt, streamId) }.getOrNull() else null
            Net.ui {
                playlist = xt
                if (info != null) {
                    val meta = listOf(info.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }, info.genre.takeIf { it.isNotBlank() }, info.duration.takeIf { it.isNotBlank() })
                        .filterNotNull().joinToString("   ·   ")
                    b.dMeta.text = meta
                    b.dPlot.text = info.plot.ifBlank { "No description available." }
                    if (!info.cover.isNullOrBlank()) ImageLoader.load(info.cover, b.cover)
                } else {
                    b.dPlot.text = ""
                }
            }
        }
    }

    private fun play() {
        val p = playlist ?: return
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("url", Xtream.vodPlayUrl(p, streamId, ext))
                .putExtra("title", name)
        )
    }
}
