package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import uk.televo.player.databinding.ActivityHomeBinding

/** The hub: Live TV / Movies / Series / Radio + action bar. Always active — no expiry. */
class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tileLive.setOnClickListener { startActivity(Intent(this, LiveActivity::class.java)) }
        b.tileMovies.setOnClickListener { startActivity(Intent(this, MoviesActivity::class.java)) }
        b.tileSeries.setOnClickListener { startActivity(Intent(this, SeriesActivity::class.java)) }
        b.tileRadio.setOnClickListener { startActivity(Intent(this, RadioActivity::class.java)) }

        val soon = View.OnClickListener { Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show() }
        b.actRefresh.setOnClickListener { showAccount(); Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show() }
        b.actLang.setOnClickListener(soon)
        b.actTimeshift.setOnClickListener(soon)
        b.actSettings.setOnClickListener(soon)
        b.actInfo.setOnClickListener(soon)
        b.actPower.setOnClickListener { logout() }

        showAccount()
        b.tileLive.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        showAccount()
    }

    private fun showAccount() {
        b.homePlaylist.text = Prefs.serverName(this) ?: "Televo"
        b.homeState.text = "●  Active"
        b.homeExpires.text = "Lifetime"
    }

    private fun logout() {
        Prefs.logout(this)
        startActivity(Intent(this, LoginActivity::class.java))
        finishAffinity()
    }
}
