package uk.televo.player

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** First screen: go straight to Home if already logged in, otherwise to Login. */
class BootActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val next = if (Prefs.isLoggedIn(this)) HomeActivity::class.java else LoginActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }
}
