package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import uk.televo.player.databinding.ActivityLiveBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full Live TV: categories -> channels -> inline player + Now & Next EPG. */
class LiveActivity : AppCompatActivity() {

    private lateinit var b: ActivityLiveBinding
    private var player: ExoPlayer? = null
    private var playlist: Api.Playlist? = null
    private var catalogue: Xtream.Catalogue? = null
    private var currentCat: Xtream.Category? = null

    private val clock = Handler(Looper.getMainLooper())
    private val clockTask = object : Runnable {
        override fun run() { tickClock(); clock.postDelayed(this, 30000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLiveBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.rvCategories.layoutManager = LinearLayoutManager(this)
        b.rvChannels.layoutManager = LinearLayoutManager(this)
        b.rvEpg.layoutManager = LinearLayoutManager(this)

        b.railHome.setOnClickListener { finish() }
        val soon = View.OnClickListener { Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show() }
        b.railFav.setOnClickListener(soon)
        b.railLock.setOnClickListener(soon)
        b.railSearch.setOnClickListener(soon)

        setupPlayer()
        tickClock()
        load()
    }

    private fun setupPlayer() {
        val p = ExoPlayer.Builder(this).build()
        b.playerView.player = p
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                b.playerBuffering.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }
            override fun onPlayerError(error: PlaybackException) {
                b.playerBuffering.visibility = View.GONE
                b.nowSub.text = "Can't play this channel"
            }
        })
        player = p
    }

    private fun load() {
        Net.run {
            try {
                val playlists = Api.playlists(this)
                val xt = playlists.firstOrNull { it.kind == "xtream" }
                if (xt == null) {
                    Net.ui { Toast.makeText(this, "No playlist assigned yet.", Toast.LENGTH_LONG).show(); finish() }
                    return@run
                }
                playlist = xt
                val cat = Xtream.loadCatalogue(xt)
                Net.ui {
                    b.livePlaylist.text = xt.label
                    b.liveActivation.text = "Active"
                    bindCatalogue(cat)
                }
            } catch (e: Exception) {
                Net.ui { Toast.makeText(this, "Couldn't load channels.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun bindCatalogue(cat: Xtream.Catalogue) {
        catalogue = cat
        if (cat.categories.isEmpty()) {
            Toast.makeText(this, "No channels found.", Toast.LENGTH_LONG).show()
            return
        }
        b.rvCategories.adapter = CategoryAdapter(cat.categories) { idx -> selectCategory(cat.categories[idx]) }
        selectCategory(cat.categories[0], autoplay = true)
    }

    private fun selectCategory(category: Xtream.Category, autoplay: Boolean = false) {
        currentCat = category
        b.liveCat.text = category.name
        b.chanCatLabel.text = category.name
        val channels = catalogue?.byCategory?.get(category.id) ?: emptyList()
        b.rvChannels.adapter = ChannelAdapter(channels) { ch -> playChannel(ch) }
        if (autoplay && channels.isNotEmpty()) playChannel(channels[0])
    }

    private fun playChannel(ch: Xtream.Channel) {
        val p = playlist ?: return
        val ex = player ?: return
        b.nowLogo.text = initials(ch.name)
        b.nowTitle.text = ch.name
        b.nowSub.text = currentCat?.name ?: ""
        b.nowNum.text = "N° ${ch.num}"
        ex.setMediaItem(MediaItem.fromUri(Xtream.playUrl(p, ch.streamId)))
        ex.playWhenReady = true
        ex.prepare()
        loadEpg(p, ch.streamId)
    }

    private fun loadEpg(p: Api.Playlist, streamId: String) {
        b.rvEpg.adapter = null
        b.epgEmpty.visibility = View.GONE
        Net.run {
            val list = Xtream.shortEpg(p, streamId)
            Net.ui {
                if (list.isEmpty()) b.epgEmpty.visibility = View.VISIBLE
                else { b.epgEmpty.visibility = View.GONE; b.rvEpg.adapter = EpgAdapter(list) }
            }
        }
    }

    private fun initials(name: String): String {
        val letters = name.filter { it.isLetterOrDigit() }
        return if (letters.length >= 2) letters.substring(0, 2).uppercase() else "TV"
    }

    private fun tickClock() {
        b.clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    override fun onResume() { super.onResume(); clock.postDelayed(clockTask, 30000); player?.playWhenReady = true }
    override fun onStop() { super.onStop(); clock.removeCallbacks(clockTask); player?.pause() }
    override fun onDestroy() { super.onDestroy(); player?.release(); player = null }
}
