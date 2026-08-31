package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import uk.televo.player.databinding.ActivityMoviesBinding

/** Movies (VOD): categories + poster grid -> detail. */
class MoviesActivity : AppCompatActivity() {

    private lateinit var b: ActivityMoviesBinding
    private var playlist: Api.Playlist? = null
    private var vod: Xtream.VodCatalogue? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMoviesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.screenTitle.text = getString(R.string.movies)
        b.rvCategories.layoutManager = LinearLayoutManager(this)
        b.rvGrid.layoutManager = GridLayoutManager(this, 5)
        load()
    }

    private fun load() {
        b.gridStatus.text = "Loading movies…"
        Net.run {
            try {
                val xt = Api.playlists(this).firstOrNull { it.kind == "xtream" }
                if (xt == null) { Net.ui { b.gridStatus.text = "No playlist assigned yet." }; return@run }
                playlist = xt
                val cat = Xtream.loadVod(xt)
                Net.ui {
                    vod = cat
                    if (cat.categories.isEmpty()) { b.gridStatus.text = "No movies found."; return@ui }
                    b.rvCategories.adapter = CategoryAdapter(cat.categories) { idx -> select(cat.categories[idx]) }
                    select(cat.categories[0])
                }
            } catch (e: Api.ApiException) {
                Net.ui { if (e.message == "not_active") { startActivity(Intent(this, LoginActivity::class.java)); finish() } else b.gridStatus.text = "Couldn't load movies." }
            } catch (e: Exception) {
                Net.ui { b.gridStatus.text = "Couldn't load movies." }
            }
        }
    }

    private fun select(category: Xtream.Category) {
        val list = vod?.byCategory?.get(category.id) ?: emptyList()
        b.gridStatus.text = "${category.name} · ${list.size}"
        val posters = list.map { PosterItem(it.name, it.icon, it.rating) }
        b.rvGrid.adapter = PosterAdapter(posters) { idx ->
            val v = list[idx]
            startActivity(
                Intent(this, MovieDetailActivity::class.java)
                    .putExtra("stream_id", v.streamId).putExtra("name", v.name)
                    .putExtra("ext", v.ext).putExtra("icon", v.icon)
            )
        }
    }
}
