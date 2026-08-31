package uk.televo.player

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import uk.televo.player.databinding.ActivitySeriesBinding

/** Series: categories + poster grid -> seasons/episodes. */
class SeriesActivity : AppCompatActivity() {

    private lateinit var b: ActivitySeriesBinding
    private var cat: Xtream.SeriesCatalogue? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySeriesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.rvCategories.layoutManager = LinearLayoutManager(this)
        b.rvGrid.layoutManager = GridLayoutManager(this, 5)
        load()
    }

    private fun load() {
        b.gridStatus.text = "Loading series…"
        Net.run {
            try {
                val xt = Api.playlists(this).firstOrNull { it.kind == "xtream" }
                if (xt == null) { Net.ui { b.gridStatus.text = "No playlist assigned yet." }; return@run }
                val c = Xtream.loadSeries(xt)
                Net.ui {
                    cat = c
                    if (c.categories.isEmpty()) { b.gridStatus.text = "No series found."; return@ui }
                    b.rvCategories.adapter = CategoryAdapter(c.categories) { idx -> select(c.categories[idx]) }
                    select(c.categories[0])
                }
            } catch (e: Api.ApiException) {
                Net.ui { if (e.message == "not_active") { startActivity(Intent(this, LoginActivity::class.java)); finish() } else b.gridStatus.text = "Couldn't load series." }
            } catch (e: Exception) {
                Net.ui { b.gridStatus.text = "Couldn't load series." }
            }
        }
    }

    private fun select(category: Xtream.Category) {
        val list = cat?.byCategory?.get(category.id) ?: emptyList()
        b.gridStatus.text = "${category.name} · ${list.size}"
        val posters = list.map { PosterItem(it.name, it.cover, "") }
        b.rvGrid.adapter = PosterAdapter(posters) { idx ->
            val s = list[idx]
            startActivity(
                Intent(this, SeriesDetailActivity::class.java)
                    .putExtra("series_id", s.seriesId).putExtra("name", s.name).putExtra("cover", s.cover)
            )
        }
    }
}
