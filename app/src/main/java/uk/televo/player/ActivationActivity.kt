package uk.televo.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import uk.televo.player.databinding.ActivityActivationBinding

/** Shows the device code, polls for activation, and offers manual login. */
class ActivationActivity : AppCompatActivity() {

    private lateinit var b: ActivityActivationBinding
    private val poller = Handler(Looper.getMainLooper())
    private val pollTask = object : Runnable {
        override fun run() { checkStatus(true); poller.postDelayed(this, 12000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityActivationBinding.inflate(layoutInflater)
        setContentView(b.root)

        val code = Prefs.deviceCode(this)
        b.tvCode.text = code

        b.btnCheck.setOnClickListener { checkStatus(false) }
        b.btnManual.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
        b.btnCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Televo device code", code))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        b.btnCheck.requestFocus()
    }

    override fun onResume() { super.onResume(); poller.postDelayed(pollTask, 4000) }
    override fun onPause() { super.onPause(); poller.removeCallbacks(pollTask) }

    private fun checkStatus(silent: Boolean) {
        if (!silent) b.tvStatus.text = "●  Checking…"
        Net.run {
            try {
                val st = Api.status(this)
                Net.ui {
                    if (st.active) goHome()
                    else b.tvStatus.text = if (silent) "●  Waiting for activation…" else "●  Not active yet — waiting for your provider."
                }
            } catch (e: Exception) {
                if (!silent) Net.ui { b.tvStatus.text = "●  Can't reach the server." }
            }
        }
    }

    private fun goHome() {
        poller.removeCallbacks(pollTask)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
