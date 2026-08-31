package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import uk.televo.player.databinding.ActivityHomeBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The hub: status pills + Live TV / Movies / Series / Radio + action bar. */
class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tileLive.setOnClickListener { startActivity(Intent(this, LiveActivity::class.java)) }
        val soon = View.OnClickListener { Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show() }
        b.tileMovies.setOnClickListener(soon)
        b.tileSeries.setOnClickListener(soon)
        b.tileRadio.setOnClickListener(soon)

        b.btnRefresh.setOnClickListener { loadStatus() }
        b.btnLang.setOnClickListener(soon)
        b.btnTimeshift.setOnClickListener(soon)
        b.btnInfo.setOnClickListener(soon)
        b.btnPower.setOnClickListener { finishAffinity() }

        b.tileLive.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        loadStatus()
    }

    private fun loadStatus() {
        Net.run {
            val status = runCatching { Api.status(this) }.getOrNull()
            val label = runCatching { Api.playlists(this).firstOrNull()?.label }.getOrNull()
            Net.ui {
                b.homePlaylist.text = label ?: "—"
                if (status != null && status.active) {
                    b.homeState.text = "●  Active"
                    b.homeExpires.text = if (status.expiresAt == null) "Lifetime" else "Expires " + fmt(status.expiresAt)
                } else {
                    b.homeState.text = "●  Inactive"
                    b.homeExpires.text = ""
                }
            }
        }
    }

    private fun fmt(dt: String): String =
        runCatching {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(Date(java.sql.Timestamp.valueOf(dt).time))
        }.getOrDefault(dt)
}
