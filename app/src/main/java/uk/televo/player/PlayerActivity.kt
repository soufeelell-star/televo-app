package uk.televo.player

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import uk.televo.player.databinding.ActivityPlayerBinding

/** Full-screen playback with Media3 / ExoPlayer. */
class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        val url = intent.getStringExtra("url")
        b.playerTitle.text = intent.getStringExtra("title") ?: ""
        if (url.isNullOrBlank()) { finish(); return }

        val p = ExoPlayer.Builder(this).build()
        b.playerView.player = p
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                b.playerLoading.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }
            override fun onPlayerError(error: PlaybackException) {
                b.playerLoading.visibility = View.GONE
                b.playerTitle.text = "Can't play this channel"
            }
        })
        p.setMediaItem(MediaItem.fromUri(url))
        p.playWhenReady = true
        p.prepare()
        player = p
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
