package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import uk.televo.player.databinding.ActivityActivationBinding

/** Shows the device code, polls for activation, and offers manual (username/password) login. */
class ActivationActivity : AppCompatActivity() {

    private lateinit var b: ActivityActivationBinding
    private var hosts: List<Api.HostItem> = emptyList()
    private val poller = Handler(Looper.getMainLooper())
    private val pollTask = object : Runnable {
        override fun run() {
            checkStatus(silent = true)
            poller.postDelayed(this, 12000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityActivationBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvCode.text = Prefs.deviceCode(this)
        loadHosts()

        b.btnCheck.setOnClickListener { checkStatus(silent = false) }
        b.btnActivate.setOnClickListener { doActivate() }
    }

    override fun onResume() {
        super.onResume()
        poller.postDelayed(pollTask, 5000)
    }

    override fun onPause() {
        super.onPause()
        poller.removeCallbacks(pollTask)
    }

    private fun loadHosts() {
        Net.run {
            try {
                val list = Api.hosts(this)
                Net.ui {
                    hosts = list
                    val names = list.map { it.name }
                    val ad = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                        if (names.isEmpty()) listOf("No hosts") else names)
                    b.spHost.adapter = ad
                }
            } catch (e: Exception) { /* ignore; user can retry */ }
        }
    }

    private fun checkStatus(silent: Boolean) {
        if (!silent) b.tvStatus.text = "Checking…"
        Net.run {
            try {
                val st = Api.status(this)
                Net.ui {
                    if (st.active) goHome()
                    else if (!silent) b.tvStatus.text = "Not active yet — waiting for your provider."
                }
            } catch (e: Exception) {
                if (!silent) Net.ui { b.tvStatus.text = "Can't reach server." }
            }
        }
    }

    private fun doActivate() {
        val idx = b.spHost.selectedItemPosition
        if (hosts.isEmpty() || idx < 0 || idx >= hosts.size) {
            b.tvLoginMsg.text = "No host available."
            return
        }
        val host = hosts[idx]
        val user = b.inUser.text.toString().trim()
        val pass = b.inPass.text.toString().trim()
        if (user.isEmpty() || pass.isEmpty()) {
            b.tvLoginMsg.text = "Enter your username and password."
            return
        }
        b.tvLoginMsg.text = "Activating…"
        b.btnActivate.isEnabled = false
        Net.run {
            try {
                val (ok, msg) = Api.selfActivate(this, host.id, user, pass)
                Net.ui {
                    b.btnActivate.isEnabled = true
                    if (ok) goHome() else b.tvLoginMsg.text = msg
                }
            } catch (e: Exception) {
                Net.ui {
                    b.btnActivate.isEnabled = true
                    b.tvLoginMsg.text = "Couldn't activate. Check your login."
                }
            }
        }
    }

    private fun goHome() {
        poller.removeCallbacks(pollTask)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
