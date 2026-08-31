package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import uk.televo.player.databinding.ActivitySeriesDetailBinding

class SeriesDetailActivity : AppCompatActivity() {

    private lateinit var b: ActivitySeriesDetailBinding
    private var playlist: Api.Playlist? = null
    private var detail: Xtream.SeriesDetail? = null
    private lateinit var name: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySeriesDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        val seriesId = intent.getStringExtra("series_id") ?: ""
        name = intent.getStringExtra("name") ?: ""
        b.sTitle.text = name
        ImageLoader.load(intent.getStringExtra("cover"), b.cover)
        b.rvEpisodes.layoutManager = LinearLayoutManager(this)
        load(seriesId)
    }

    private fun load(seriesId: String) {
        Net.run {
            val xt = runCatching { Api.playlists(this).firstOrNull { it.kind == "xtream" } }.getOrNull()
            val d = if (xt != null) runCatching { Xtream.seriesInfo(xt, seriesId) }.getOrNull() else null
            Net.ui {
                playlist = xt
                detail = d
                if (d == null || d.seasons.isEmpty()) { b.sStatus.text = "No episodes available."; return@ui }
                if (!d.cover.isNullOrBlank()) ImageLoader.load(d.cover, b.cover)
                b.sPlot.text = d.plot
                val labels = d.seasons.map { "Season $it" }
                b.spSeason.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
                b.spSeason.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        showSeason(d.seasons[position])
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                showSeason(d.seasons[0])
            }
        }
    }

    private fun showSeason(season: Int) {
        val eps = detail?.episodesBySeason?.get(season) ?: emptyList()
        b.sStatus.text = "${eps.size} episodes"
        b.rvEpisodes.adapter = EpisodeAdapter(eps) { ep -> playEpisode(ep) }
    }

    private fun playEpisode(ep: Xtream.Episode) {
        val p = playlist ?: return
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("url", Xtream.seriesPlayUrl(p, ep.id, ep.ext))
                .putExtra("title", "$name · S${ep.season} E${ep.num}")
        )
    }
}
