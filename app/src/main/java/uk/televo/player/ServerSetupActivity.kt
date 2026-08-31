package uk.televo.player

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import uk.televo.player.databinding.ActivityServerSetupBinding

/** One-time: enter the Televo panel URL + API key. */
class ServerSetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityServerSetupBinding.inflate(layoutInflater)
        setContentView(b.root)

        Prefs.baseUrl(this)?.let { b.inUrl.setText(it) }
        Prefs.apiKey(this)?.let { b.inKey.setText(it) }
        intent.getStringExtra("error")?.let { b.setupMsg.text = it }

        b.btnConnect.setOnClickListener {
            val url = b.inUrl.text.toString().trim()
            val key = b.inKey.text.toString().trim()
            if (url.isEmpty() || key.isEmpty()) {
                b.setupMsg.text = "Please enter both the server URL and the API key."
                return@setOnClickListener
            }
            Prefs.setServer(this, url, key)
            b.setupMsg.setTextColor(ContextCompat.getColor(this, R.color.tv_muted))
            b.setupMsg.text = "Connecting…"
            b.btnConnect.isEnabled = false
            Net.run {
                try {
                    Api.hosts(this) // connectivity + key test
                    Net.ui {
                        startActivity(Intent(this, BootActivity::class.java))
                        finish()
                    }
                } catch (e: Exception) {
                    Net.ui {
                        b.btnConnect.isEnabled = true
                        b.setupMsg.setTextColor(ContextCompat.getColor(this, R.color.tv_amber))
                        b.setupMsg.text = "Couldn't connect. Check the address and key, then try again."
                    }
                }
            }
        }
    }
}
