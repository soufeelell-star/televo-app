package uk.televo.player

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import uk.televo.player.databinding.ActivityBootBinding

/** First screen: registers the device with the server and routes accordingly. */
class BootActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityBootBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Prefs.baseUrl(this) == null) {
            startActivity(Intent(this, ServerSetupActivity::class.java))
            finish()
            return
        }

        b.bootStatus.text = "Connecting to Televo…"
        Net.run {
            try {
                Api.register(this)
                val st = Api.status(this)
                Net.ui { open(if (st.active) HomeActivity::class.java else ActivationActivity::class.java) }
            } catch (e: Exception) {
                Net.ui {
                    startActivity(
                        Intent(this, ServerSetupActivity::class.java)
                            .putExtra("error", "Couldn't reach the server. Check the address.")
                    )
                    finish()
                }
            }
        }
    }

    private fun open(cls: Class<*>) {
        startActivity(Intent(this, cls))
        finish()
    }
}
