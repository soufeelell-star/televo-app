package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import uk.televo.player.databinding.ActivityLoginBinding

/** Manual login: enter Xtream username/password to activate this device instantly. */
class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private var hosts: List<Api.HostItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        loadHosts()
        b.btnLogin.setOnClickListener { doLogin() }
    }

    private fun loadHosts() {
        Net.run {
            try {
                val list = Api.hosts(this)
                Net.ui {
                    hosts = list
                    val names = list.map { it.name }
                    b.spHost.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                        if (names.isEmpty()) listOf("No hosts") else names)
                }
            } catch (e: Exception) {
                Net.ui { b.loginMsg.text = "Couldn't reach the server." }
            }
        }
    }

    private fun doLogin() {
        val idx = b.spHost.selectedItemPosition
        if (hosts.isEmpty() || idx < 0 || idx >= hosts.size) { b.loginMsg.text = "No host available."; return }
        val host = hosts[idx]
        val user = b.inUser.text.toString().trim()
        val pass = b.inPass.text.toString().trim()
        if (user.isEmpty() || pass.isEmpty()) { b.loginMsg.text = "Enter your username and password."; return }
        b.loginMsg.text = "Logging in…"
        b.btnLogin.isEnabled = false
        Net.run {
            try {
                val (ok, msg) = Api.selfActivate(this, host.id, user, pass)
                Net.ui {
                    b.btnLogin.isEnabled = true
                    if (ok) { startActivity(Intent(this, HomeActivity::class.java)); finish() }
                    else b.loginMsg.text = msg
                }
            } catch (e: Exception) {
                Net.ui { b.btnLogin.isEnabled = true; b.loginMsg.text = "Login failed. Check your details." }
            }
        }
    }
}
