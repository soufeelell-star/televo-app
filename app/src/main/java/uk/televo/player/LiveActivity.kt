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
    private var currentStreamId: String? = null

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
        b.railExit.setOnClickListener { finish() }
        val soon = View.OnClickListener { Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show() }
        b.railFull.setOnClickListener(soon)
        b.railFav.setOnClickListener(soon)
        b.railLock.setOnClickListener(soon)
        b.railSearch.setOnClickListener(soon)
        b.railSettings.setOnClickListener(soon)
        b.railLang.setOnClickListener(soon)
        b.railInfo.setOnClickListener(soon)

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
                    b.liveActivation.text = expiryLabel()
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
        val cats = sortCats(cat.categories)
        b.rvCategories.adapter = CategoryAdapter(cats) { idx -> selectCategory(cats[idx]) }

        // resume the last played channel if we still have it, else first channel
        val last = Prefs.lastStreamId(this)
        var startCat = cats[0]
        var startChan: Xtream.Channel? = null
        if (last != null) {
            for (c in cats) {
                val ch = cat.byCategory[c.id]?.firstOrNull { it.streamId == last }
                if (ch != null) { startCat = c; startChan = ch; break }
            }
        }
        selectCategory(startCat, autoplay = startChan == null)
        startChan?.let { playChannel(it) }
    }

    private fun selectCategory(category: Xtream.Category, autoplay: Boolean = false) {
        currentCat = category
        b.liveCat.text = category.name
        b.chanCatLabel.text = category.name
        val channels = sortChans(catalogue?.byCategory?.get(category.id) ?: emptyList())
        b.rvChannels.adapter = ChannelAdapter(channels) { ch -> playChannel(ch) }
        if (autoplay && channels.isNotEmpty()) playChannel(channels[0])
    }

    private fun sortCats(list: List<Xtream.Category>): List<Xtream.Category> = when (Prefs.sortCategories(this)) {
        1 -> list.sortedBy { it.name.lowercase() }
        2 -> list.sortedByDescending { it.name.lowercase() }
        else -> list
    }

    private fun sortChans(list: List<Xtream.Channel>): List<Xtream.Channel> = when (Prefs.sortContent(this)) {
        1 -> list.sortedBy { it.name.lowercase() }
        2 -> list.sortedByDescending { it.name.lowercase() }
        3 -> list.sortedBy { it.num.toIntOrNull() ?: Int.MAX_VALUE }
        else -> list
    }

    private fun playChannel(ch: Xtream.Channel) {
        val p = playlist ?: return
        val ex = player ?: return
        Prefs.saveLastChannel(this, ch.streamId)
        b.nowLogo.text = initials(ch.name)
        b.nowTitle.text = ch.name
        b.nowSub.text = currentCat?.name ?: ""
        b.nowNum.text = "N° ${ch.num}"
        currentStreamId = ch.streamId
        ex.setMediaItem(MediaItem.fromUri(Xtream.playUrl(p, ch.streamId)))
        ex.playWhenReady = true
        ex.prepare()
        loadEpg(p, ch.streamId)
    }

    private fun loadEpg(p: Api.Playlist, streamId: String) {
        b.rvEpg.adapter = null
        b.epgEmpty.visibility = View.GONE
        Net.run {
            // full listing incl. the provider's catch-up archive
            val list = Xtream.catchupEpg(p, streamId).ifEmpty { Xtream.shortEpg(p, streamId) }
            Net.ui {
                if (list.isEmpty()) b.epgEmpty.visibility = View.VISIBLE
                else {
                    b.epgEmpty.visibility = View.GONE
                    b.rvEpg.adapter = EpgAdapter(list) { e -> playCatchup(p, streamId, e) }
                }
            }
        }
    }

    /** Replay a past programme from the provider's catch-up archive. */
    private fun playCatchup(p: Api.Playlist, streamId: String, e: Xtream.Epg) {
        val ex = player ?: return
        b.nowTitle.text = e.title
        b.nowSub.text = (currentCat?.name ?: "") + "  •  " + getString(R.string.catch_up)
        ex.setMediaItem(MediaItem.fromUri(Xtream.catchupUrl(p, streamId, e.startUnix, e.stopUnix)))
        ex.playWhenReady = true
        ex.prepare()
        Toast.makeText(this, "▶ " + e.title, Toast.LENGTH_SHORT).show()
    }

    private fun initials(name: String): String {
        val letters = name.filter { it.isLetterOrDigit() }
        return if (letters.length >= 2) letters.substring(0, 2).uppercase() else "TV"
    }

    private fun expiryLabel(): String {
        val e = Prefs.expiry(this)
        if (e <= 0L) return getString(R.string.unlimited)
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(e * 1000L))
    }

    private fun tickClock() {
        b.clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    override fun onResume() { super.onResume(); clock.postDelayed(clockTask, 30000); player?.playWhenReady = true }
    override fun onStop() { super.onStop(); clock.removeCallbacks(clockTask); player?.pause() }
    override fun onDestroy() { super.onDestroy(); player?.release(); player = null }
}
