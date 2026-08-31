package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.net.Uri
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import uk.televo.player.databinding.ActivityLiveBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Professional Live TV: categories → channels → HD player + EPG, with fullscreen,
 *  favourites, search, catch-up and a parental lock. Max quality, resilient playback. */
class LiveActivity : AppCompatActivity() {

    private lateinit var b: ActivityLiveBinding
    private var libVlc: LibVLC? = null
    private var vlc: MediaPlayer? = null
    private var playlist: Api.Playlist? = null
    private var catalogue: Xtream.Catalogue? = null
    private var allChannels: List<Xtream.Channel> = emptyList()
    private var currentCat: Xtream.Category? = null
    private var currentChannel: Xtream.Channel? = null
    private var currentNow: String = ""

    private var fullscreen = false
    private var locked = false
    private var favView = false
    private var searchOpen = false
    private var retries = 0

    private val clock = Handler(Looper.getMainLooper())
    private val clockTask = object : Runnable {
        override fun run() { tickClock(); clock.postDelayed(this, 30000) }
    }

    // Auto-hide the on-screen info when idle, leaving just the stream.
    private val overlayHandler = Handler(Looper.getMainLooper())
    private var overlayVisible = false
    private var muted = false
    private val hideOverlay = Runnable {
        b.liveBadge.visibility = View.GONE
        b.nowBar.visibility = View.GONE
        b.fsControls.visibility = View.GONE
        overlayVisible = false
    }
    private fun showOverlay() {
        b.liveBadge.visibility = View.VISIBLE
        b.nowBar.visibility = View.VISIBLE
        b.fsControls.visibility = if (fullscreen) View.VISIBLE else View.GONE
        overlayVisible = true
        overlayHandler.removeCallbacks(hideOverlay)
        overlayHandler.postDelayed(hideOverlay, if (fullscreen) 6000 else 4000)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        showOverlay()
    }

    private fun fmtHm(unix: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(unix * 1000L))

    private fun toggleMute() {
        muted = !muted
        vlc?.volume = if (muted) 0 else 100
        b.fsVolume.setImageResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume)
    }

    // Keep the parsed catalogue for the session so re-entering Live TV is instant.
    companion object {
        private var cacheKey: String? = null
        private var cached: Xtream.Catalogue? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLiveBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.rvCategories.layoutManager = LinearLayoutManager(this)
        b.rvChannels.layoutManager = LinearLayoutManager(this)
        b.rvEpg.layoutManager = LinearLayoutManager(this)

        b.railHome.setOnClickListener { if (!guardLocked()) finish() }
        b.railExit.setOnClickListener { if (!guardLocked()) finish() }
        b.railFull.setOnClickListener { toggleFullscreen() }
        b.railAspect.setOnClickListener { cycleAspect() }
        b.playerFrame.setOnClickListener { toggleFullscreen() }
        b.fsBack.setOnClickListener { setFullscreen(false) }
        b.fsAspect.setOnClickListener { cycleAspect() }
        b.fsAspectLabel.setOnClickListener { cycleAspect() }
        b.fsVolume.setOnClickListener { toggleMute() }
        b.fsAspectLabel.text = aspectName(Prefs.aspectMode(this))
        b.railFav.setOnClickListener { toggleFavourites() }
        b.railLock.setOnClickListener { toggleLock() }
        b.railSearch.setOnClickListener { toggleSearch() }
        b.railSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.railLang.setOnClickListener { languageDialog() }
        b.railInfo.setOnClickListener { infoDialog() }

        b.searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applySearch(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
        })

        setupPlayer()
        tickClock()
        load()
    }

    // ---------------- player ----------------

    private var usingTs = false
    private var altTried = false

    private fun setupPlayer() {
        val options = ArrayList<String>().apply {
            add("--network-caching=3000")   // bigger buffer → fewer stalls
            add("--live-caching=3000")
            add("--file-caching=3000")
            add("--clock-jitter=0")
            add("--clock-synchro=0")
            add("--avcodec-hw=any")         // hardware where possible, software fallback for 4K/HEVC
            add("--audio-time-stretch")
        }
        val lib = LibVLC(this, options)
        val mp = MediaPlayer(lib)
        mp.attachViews(b.playerView, null, false, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mp.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Buffering ->
                    b.playerBuffering.visibility = if (ev.buffering < 100f) View.VISIBLE else View.GONE
                MediaPlayer.Event.Playing -> {
                    b.playerBuffering.visibility = View.GONE
                    retries = 0
                    applyAspect()
                    // video track size isn't ready instantly — poll a few times
                    updateQuality()
                    clock.postDelayed({ updateQuality() }, 900)
                    clock.postDelayed({ updateQuality() }, 2200)
                }
                MediaPlayer.Event.Vout -> updateQuality()
                MediaPlayer.Event.EncounteredError -> onPlayError()
                MediaPlayer.Event.EndReached -> onPlayError()   // live shouldn't end — recover
            }
        }
        libVlc = lib
        vlc = mp
    }

    private fun setMedia(uri: String) {
        val lib = libVlc ?: return
        val m = Media(lib, Uri.parse(uri))
        m.setHWDecoderEnabled(true, false)     // HW decode, auto software fallback (this fixes 4K/HEVC)
        m.addOption(":network-caching=3000")
        m.addOption(":clock-jitter=0")
        m.addOption(":clock-synchro=0")
        vlc?.media = m
        m.release()
        vlc?.play()
    }

    /** Play the current channel as raw MPEG-TS (default, like other IPTV players) or HLS. */
    private fun playVariant(ts: Boolean) {
        val p = playlist ?: return
        val ch = currentChannel ?: return
        usingTs = ts
        setMedia(if (ts) Xtream.playUrlTs(p, ch.streamId) else Xtream.playUrl(p, ch.streamId))
    }

    private fun onPlayError() {
        val ch = currentChannel
        if (ch != null && !altTried) {          // try the other container once (ts <-> m3u8)
            altTried = true; retries = 0
            playVariant(!usingTs)
            return
        }
        if (retries < 8) {
            retries++
            clock.postDelayed({ playVariant(usingTs) }, 1500)
        } else {
            b.playerBuffering.visibility = View.GONE
            b.nowSub.text = "Can't play this channel"
        }
    }

    private fun updateQuality() {
        val t = vlc?.currentVideoTrack ?: return
        if (t.width <= 0 || t.height <= 0) return
        b.nowQuality.text = qualityLabel(t.width, t.height)
        b.qualityBadge.text = qualityTag(t.width, t.height)
        b.qualityBadge.visibility = View.VISIBLE   // persistent — always shows the current quality
    }

    private fun qualityTag(w: Int, h: Int): String = when {
        h >= 2000 || w >= 3000 -> "4K"
        h >= 1080 -> "1080p"
        h >= 720 -> "720p"
        else -> "SD"
    }

    private fun qualityLabel(w: Int, h: Int): String {
        if (w <= 0 || h <= 0) return ""
        return "${w}×${h} · ${qualityTag(w, h)}"
    }

    // ---------------- aspect ratio ----------------

    private fun applyAspect() {
        vlc?.videoScale = when (Prefs.aspectMode(this)) {
            0 -> MediaPlayer.ScaleType.SURFACE_FILL        // Fill screen (stretch, no zoom, full res) — default
            1 -> MediaPlayer.ScaleType.SURFACE_BEST_FIT    // Fit (true aspect, may show bars)
            2 -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN  // Zoom (crop to fill)
            3 -> MediaPlayer.ScaleType.SURFACE_16_9
            4 -> MediaPlayer.ScaleType.SURFACE_4_3
            else -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
        }
    }

    private fun cycleAspect() {
        val next = (Prefs.aspectMode(this) + 1) % 6
        Prefs.setAspectMode(this, next)
        applyAspect()
        b.fsAspectLabel.text = aspectName(next)
        showOverlay()
        Toast.makeText(this, aspectName(next), Toast.LENGTH_SHORT).show()
    }

    private fun aspectName(m: Int): String = when (m) {
        1 -> getString(R.string.aspect_fit)
        2 -> getString(R.string.aspect_zoom)
        3 -> "16:9"
        4 -> "4:3"
        5 -> getString(R.string.aspect_original)
        else -> getString(R.string.aspect_fill)
    }

    // ---------------- load ----------------

    private fun load() {
        val xt = Prefs.playlist(this)
        if (xt == null) { Toast.makeText(this, "Please log in again.", Toast.LENGTH_LONG).show(); finish(); return }
        playlist = xt
        b.livePlaylist.text = xt.label
        b.liveActivation.text = expiryLabel()

        val key = "${xt.host}|${xt.username}"
        val c = cached
        if (cacheKey == key && c != null) { bindCatalogue(c); return }  // instant

        Net.run {
            try {
                val cat = Xtream.loadCatalogue(xt)
                cached = cat; cacheKey = key
                Net.ui { bindCatalogue(cat) }
            } catch (e: Exception) {
                Net.ui { Toast.makeText(this, "Couldn't load channels.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun bindCatalogue(cat: Xtream.Catalogue) {
        catalogue = cat
        if (cat.categories.isEmpty()) { Toast.makeText(this, "No channels found.", Toast.LENGTH_LONG).show(); return }
        allChannels = cat.categories.flatMap { cat.byCategory[it.id] ?: emptyList() }
        val cats = sortCats(cat.categories)
        b.rvCategories.adapter = CategoryAdapter(cats) { idx -> favView = false; selectCategory(cats[idx]) }

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
        b.rvChannels.post { b.rvChannels.requestFocus() }   // land the remote on the channel list
    }

    private fun selectCategory(category: Xtream.Category, autoplay: Boolean = false) {
        currentCat = category
        b.liveCat.text = category.name
        b.chanCatLabel.text = category.name.uppercase()
        val channels = sortChans(catalogue?.byCategory?.get(category.id) ?: emptyList())
        showChannels(channels)
        if (autoplay && channels.isNotEmpty()) playChannel(channels[0])
    }

    private var currentList: List<Xtream.Channel> = emptyList()

    private fun showChannels(channels: List<Xtream.Channel>) {
        currentList = channels
        b.rvChannels.adapter = ChannelAdapter(channels, { ch -> onChannelClick(ch) }, { ch -> toggleFavourite(ch) })
    }

    /** OK on a channel: first press plays it; pressing OK again on the same channel goes fullscreen. */
    private fun onChannelClick(ch: Xtream.Channel) {
        if (ch.streamId == currentChannel?.streamId && !fullscreen) toggleFullscreen()
        else playChannel(ch)
    }

    /** Channel up/down (zap) within the current list. */
    private fun zap(delta: Int) {
        val list = currentList
        val ch = currentChannel ?: return
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.streamId == ch.streamId }
        if (i < 0) return
        val n = (i + delta + list.size) % list.size
        playChannel(list[n])
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

    // ---------------- playback ----------------

    private fun playChannel(ch: Xtream.Channel) {
        val p = playlist ?: return
        currentChannel = ch
        currentStreamId = ch.streamId
        retries = 0
        altTried = false
        Prefs.saveLastChannel(this, ch.streamId)
        b.nowTitle.text = ch.name
        b.nowSub.text = currentCat?.name ?: ""
        b.nowNum.text = "N° ${ch.num}"
        b.nowQuality.text = ""
        b.nowProgress.progress = 0
        b.nowTimeStart.text = ""
        b.nowTimeEnd.text = ""
        showOverlay()
        playVariant(ts = true)   // .ts first (best for 4K); falls back to HLS on error
        loadEpg(p, ch.streamId)
    }

    private var currentStreamId: String? = null

    private fun loadEpg(p: Api.Playlist, streamId: String) {
        b.rvEpg.adapter = null
        b.epgEmpty.visibility = View.GONE
        currentNow = ""
        Net.run {
            val list = Xtream.catchupEpg(p, streamId).ifEmpty { Xtream.shortEpg(p, streamId) }
            Net.ui {
                val nowItem = list.firstOrNull { it.now }
                currentNow = nowItem?.title ?: ""
                // info-bar programme + progress line
                if (nowItem != null && nowItem.startUnix > 0 && nowItem.stopUnix > nowItem.startUnix) {
                    b.nowSub.text = nowItem.title
                    b.nowTimeStart.text = fmtHm(nowItem.startUnix)
                    b.nowTimeEnd.text = fmtHm(nowItem.stopUnix)
                    val nowS = System.currentTimeMillis() / 1000
                    val prog = ((nowS - nowItem.startUnix) * 100 / (nowItem.stopUnix - nowItem.startUnix)).toInt()
                    b.nowProgress.progress = prog.coerceIn(0, 100)
                } else {
                    b.nowSub.text = currentCat?.name ?: ""
                    b.nowProgress.progress = 0
                }
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
        retries = 0
        usingTs = true; altTried = true   // catch-up URL is already .ts — don't switch to live
        b.nowTitle.text = e.title
        b.nowSub.text = (currentCat?.name ?: "") + "  •  " + getString(R.string.catch_up)
        showOverlay()
        setMedia(Xtream.catchupUrl(p, streamId, e.startUnix, e.stopUnix))
        Toast.makeText(this, "▶ " + e.title, Toast.LENGTH_SHORT).show()
    }

    // ---------------- fullscreen ----------------

    private fun toggleFullscreen() = setFullscreen(!fullscreen)

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        val vis = if (on) View.GONE else View.VISIBLE
        b.topBar.visibility = vis
        b.railCol.visibility = vis
        b.colCategories.visibility = vis
        b.colChannels.visibility = vis
        b.epgCard.visibility = vis
        applyAspect()
        val pad = if (on) 0 else (14 * resources.displayMetrics.density).toInt()
        b.stageCol.setPadding(pad, pad, pad, pad)
        b.railFull.setColorFilter(ContextCompat.getColor(this, if (on) R.color.tv_accent else R.color.tv_muted))
        if (on) {
            showOverlay()
            b.fsAspectLabel.post { b.fsAspectLabel.requestFocus() }
        } else {
            b.fsControls.visibility = View.GONE
            b.rvChannels.post { b.rvChannels.requestFocus() }
        }
    }

    // ---------------- favourites ----------------

    private fun toggleFavourites() {
        favView = !favView
        if (favView) {
            val favs = allChannels.filter { Prefs.isFavorite(this, it.streamId) }
            b.chanCatLabel.text = "★ " + getString(R.string.favorites)
            b.liveCat.text = getString(R.string.favorites)
            showChannels(sortChans(favs))
            if (favs.isEmpty()) Toast.makeText(this, getString(R.string.no_favorites), Toast.LENGTH_LONG).show()
        } else {
            currentCat?.let { selectCategory(it) }
        }
    }

    private fun toggleFavourite(ch: Xtream.Channel) {
        val now = Prefs.toggleFavorite(this, ch.streamId)
        Toast.makeText(this, (if (now) "★ " else "☆ ") + ch.name, Toast.LENGTH_SHORT).show()
    }

    // ---------------- search ----------------

    private fun toggleSearch() {
        searchOpen = !searchOpen
        b.searchInput.visibility = if (searchOpen) View.VISIBLE else View.GONE
        if (searchOpen) b.searchInput.requestFocus()
        else { b.searchInput.setText(""); currentCat?.let { selectCategory(it) } }
    }

    private fun applySearch(q: String) {
        if (!searchOpen) return
        val base = if (Prefs.searchInCategory(this)) catalogue?.byCategory?.get(currentCat?.id) ?: emptyList() else allChannels
        val res = if (q.isBlank()) base else base.filter { it.name.contains(q, ignoreCase = true) }
        showChannels(sortChans(res))
    }

    // ---------------- lock (parental) ----------------

    private fun toggleLock() {
        if (!locked) {
            locked = true
            setFullscreen(true)
            Toast.makeText(this, getString(R.string.locked), Toast.LENGTH_SHORT).show()
        } else {
            promptUnlock()
        }
    }

    /** @return true if an action was blocked because the screen is locked. */
    private fun guardLocked(): Boolean {
        if (locked) { promptUnlock(); return true }
        return false
    }

    private fun promptUnlock() {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; hint = "0000" }
        MaterialAlertDialogBuilder(this, R.style.Theme_Televo_Dialog)
            .setTitle(R.string.enter_pin)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (input.text.toString().trim() == Prefs.pin(this)) {
                    locked = false
                    setFullscreen(false)
                } else {
                    Toast.makeText(this, getString(R.string.wrong_pin), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------- language / info ----------------

    private fun languageDialog() {
        val codes = listOf("en", "fr", "es", "pt", "ar")
        val names = arrayOf("English", "Français", "Español", "Português", "العربية")
        val current = codes.indexOf(Prefs.language(this)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this, R.style.Theme_Televo_Dialog)
            .setTitle(R.string.choose_language)
            .setSingleChoiceItems(names, current) { d, which ->
                d.dismiss()
                Prefs.setLanguage(this, codes[which])
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags(codes[which])
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun infoDialog() {
        val ch = currentChannel
        val msg = (ch?.name ?: "-") +
            "\nN° " + (ch?.num ?: "-") +
            "\n" + (currentCat?.name ?: "-") +
            (if (currentNow.isNotBlank()) "\n\n" + getString(R.string.now_playing) + ": " + currentNow else "")
        MaterialAlertDialogBuilder(this, R.style.Theme_Televo_Dialog)
            .setTitle(R.string.info)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------------- misc ----------------

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_DOWN) {
            // Dedicated channel-zap keys work anywhere.
            when (e.keyCode) {
                KeyEvent.KEYCODE_CHANNEL_UP -> { showOverlay(); zap(-1); return true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { showOverlay(); zap(+1); return true }
            }
            // In fullscreen: up/down zap channels; left/right/OK reach the on-screen controls.
            if (fullscreen && !locked) {
                when (e.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { showOverlay(); zap(-1); return true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { showOverlay(); zap(+1); return true }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        // First press just reveals the controls & focuses them; then let the D-pad move between them.
                        if (!overlayVisible) { showOverlay(); b.fsAspectLabel.requestFocus(); return true }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onBackPressed() {
        when {
            locked -> promptUnlock()
            searchOpen -> toggleSearch()
            fullscreen -> setFullscreen(false)
            else -> super.onBackPressed()
        }
    }

    private fun expiryLabel(): String {
        val e = Prefs.expiry(this)
        if (e <= 0L) return getString(R.string.unlimited)
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(e * 1000L))
    }

    private fun tickClock() {
        b.clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    private fun initials(name: String): String {
        val letters = name.filter { it.isLetterOrDigit() }
        return if (letters.length >= 2) letters.substring(0, 2).uppercase() else "TV"
    }

    override fun onResume() { super.onResume(); clock.postDelayed(clockTask, 30000); vlc?.let { if (!it.isPlaying) it.play() } }
    override fun onStop() { super.onStop(); clock.removeCallbacks(clockTask); overlayHandler.removeCallbacks(hideOverlay); vlc?.pause() }
    override fun onDestroy() {
        super.onDestroy()
        vlc?.let { it.stop(); it.detachViews(); it.release() }
        libVlc?.release()
        vlc = null; libVlc = null
    }
}
