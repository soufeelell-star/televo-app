package uk.televo.player

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** First screen: go straight to Home if already logged in, otherwise to Login. */
class BootActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        // Optionally jump straight into the last played channel.
        if (Prefs.playLastOnStartup(this) && Prefs.lastStreamId(this) != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            startActivity(Intent(this, LiveActivity::class.java))
        } else {
            startActivity(Intent(this, HomeActivity::class.java))
        }
        finish()
    }
}
