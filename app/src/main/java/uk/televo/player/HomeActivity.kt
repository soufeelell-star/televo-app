package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import uk.televo.player.databinding.ActivityHomeBinding

/** The hub: Live TV / Movies / Series / Radio. */
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tileLive.setOnClickListener {
            startActivity(Intent(this, LiveActivity::class.java))
        }
        val soon = android.view.View.OnClickListener {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
        }
        b.tileMovies.setOnClickListener(soon)
        b.tileSeries.setOnClickListener(soon)
        b.tileRadio.setOnClickListener(soon)

        b.tileLive.requestFocus()
    }
}
