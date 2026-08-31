package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import uk.televo.player.databinding.ActivityLoginBinding

/**
 * Pick a server, type username + password, log in. No activation, no expiry.
 * The server list is loaded live from the admin panel (panel.televo.uk) so
 * servers can be added/edited/removed without updating the app; a cached copy
 * (or a built-in fallback) is shown instantly and while offline.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private var servers: List<Servers.Server> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Show the best list we have right away (cache, or built-in fallback).
        setServers(Prefs.servers(this))

        b.btnLogin.setOnClickListener { doLogin() }
        b.inUser.requestFocus()

        // Then refresh from the panel in the background.
        refreshServers()
    }

    private fun setServers(list: List<Servers.Server>) {
        servers = list
        val names = if (list.isEmpty()) listOf("No servers") else list.map { it.name }
        val keep = b.spHost.selectedItemPosition
        b.spHost.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        if (keep in names.indices) b.spHost.setSelection(keep)
    }

    private fun refreshServers() {
        Net.run {
            val fresh = runCatching { Api.fetchHosts() }.getOrNull()
            if (!fresh.isNullOrEmpty()) {
                Prefs.cacheServers(this, fresh)
                Net.ui { setServers(fresh) }
            }
        }
    }

    private fun doLogin() {
        val idx = b.spHost.selectedItemPosition
        val server = servers.getOrNull(idx)
        if (server == null) { warn("No server available yet — check your internet."); return }

        val user = b.inUser.text.toString().trim()
        val pass = b.inPass.text.toString().trim()
        if (user.isEmpty() || pass.isEmpty()) { warn("Enter your username and password."); return }

        info("Logging in…")
        b.btnLogin.isEnabled = false

        Net.run {
            val (ok, msg) = Api.xtreamLogin(server.baseUrl, user, pass)
            Net.ui {
                b.btnLogin.isEnabled = true
                if (ok) {
                    Prefs.saveLogin(this, server.name, server.baseUrl, user, pass)
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    warn(msg)
                }
            }
        }
    }

    private fun warn(text: String) {
        b.loginMsg.setTextColor(ContextCompat.getColor(this, R.color.tv_amber))
        b.loginMsg.text = text
    }

    private fun info(text: String) {
        b.loginMsg.setTextColor(ContextCompat.getColor(this, R.color.tv_muted))
        b.loginMsg.text = text
    }
}
