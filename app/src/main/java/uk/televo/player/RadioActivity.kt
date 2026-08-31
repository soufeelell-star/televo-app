package uk.televo.player

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import uk.televo.player.databinding.ActivityRadioBinding

/** Radio: live streams whose category name mentions "radio". */
class RadioActivity : AppCompatActivity() {

    private lateinit var b: ActivityRadioBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRadioBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.rvRadio.layoutManager = LinearLayoutManager(this)
        load()
    }

    private fun load() {
        b.radioStatus.text = "Loading radio…"
        Net.run {
            try {
                val xt = Api.playlists(this).firstOrNull { it.kind == "xtream" }
                if (xt == null) { Net.ui { b.radioStatus.text = "No playlist assigned yet." }; return@run }
                val cat = Xtream.loadCatalogue(xt)
                val radioCats = cat.categories.filter { it.name.lowercase().contains("radio") }
                val channels = ArrayList<Xtream.Channel>()
                for (c in radioCats) channels.addAll(cat.byCategory[c.id] ?: emptyList())
                Net.ui {
                    if (channels.isEmpty()) { b.radioStatus.text = "No radio stations in this playlist." ; return@ui }
                    b.radioStatus.text = "${channels.size} stations"
                    b.rvRadio.adapter = ChannelAdapter(channels) { ch ->
                        startActivity(
                            Intent(this, PlayerActivity::class.java)
                                .putExtra("url", Xtream.playUrl(xt, ch.streamId))
                                .putExtra("title", ch.name)
                        )
                    }
                }
            } catch (e: Exception) {
                Net.ui { b.radioStatus.text = "Couldn't load radio." }
            }
        }
    }
}
