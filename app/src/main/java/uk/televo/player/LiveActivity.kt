package uk.televo.player

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import uk.televo.player.databinding.ActivityLiveBinding

/** Loads the device's first playlist and lists its live channels. */
class LiveActivity : AppCompatActivity() {

    private lateinit var b: ActivityLiveBinding
    private var playlist: Api.Playlist? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLiveBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.rvChannels.layoutManager = LinearLayoutManager(this)
        load()
    }

    private fun load() {
        b.tvLiveStatus.text = "Loading channels…"
        Net.run {
            try {
                val playlists = Api.playlists(this)
                val xt = playlists.firstOrNull { it.kind == "xtream" }
                if (xt == null) {
                    Net.ui { b.tvLiveStatus.text = "No playlist yet — ask your provider to add one." }
                    return@run
                }
                playlist = xt
                val channels = Xtream.liveStreams(xt)
                Net.ui {
                    if (channels.isEmpty()) {
                        b.tvLiveStatus.text = "No channels found on this playlist."
                    } else {
                        b.tvLiveStatus.text = "${channels.size} channels"
                        b.rvChannels.adapter = ChannelAdapter(channels) { openPlayer(it) }
                        b.rvChannels.requestFocus()
                    }
                }
            } catch (e: Api.ApiException) {
                Net.ui {
                    if (e.message == "not_active") {
                        startActivity(Intent(this, ActivationActivity::class.java)); finish()
                    } else {
                        b.tvLiveStatus.text = "Couldn't load channels."
                    }
                }
            } catch (e: Exception) {
                Net.ui { b.tvLiveStatus.text = "Couldn't load channels." }
            }
        }
    }

    private fun openPlayer(c: Xtream.Channel) {
        val p = playlist ?: return
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("url", Xtream.playUrl(p, c.streamId))
                .putExtra("title", c.name)
        )
    }
}
