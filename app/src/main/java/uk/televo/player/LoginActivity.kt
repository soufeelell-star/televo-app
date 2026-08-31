package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import uk.televo.player.databinding.ActivityLoginBinding

/**
 * Pick a server, type username + password, sign in. No activation, no expiry.
 * Servers are loaded live from the admin panel (panel.televo.uk) with an
 * instant cached/built-in fallback.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private var servers: List<Servers.Server> = emptyList()
    private var selected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        setServers(Prefs.servers(this))

        b.hostRow.setOnClickListener { pickHost() }
        b.btnLogin.setOnClickListener { doLogin() }
        b.inUser.requestFocus()

        refreshServers()
    }

    /** Always shown as "Server 1", "Server 2", … regardless of the admin's host name. */
    private fun label(index: Int): String = "Server ${index + 1}"

    private fun setServers(list: List<Servers.Server>) {
        servers = list
        if (selected >= list.size) selected = 0
        b.hostName.text = if (list.isEmpty()) "No servers" else label(selected)
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

    private fun pickHost() {
        if (servers.isEmpty()) return
        val menu = PopupMenu(this, b.hostRow)
        servers.forEachIndexed { i, _ -> menu.menu.add(0, i, i, label(i)) }
        menu.setOnMenuItemClickListener { item ->
            selected = item.itemId
            b.hostName.text = label(selected)
            true
        }
        menu.show()
    }

    private fun doLogin() {
        val server = servers.getOrNull(selected)
        if (server == null) { warn("No server available yet — check your internet."); return }

        val user = b.inUser.text.toString().trim()
        val pass = b.inPass.text.toString().trim()
        if (user.isEmpty() || pass.isEmpty()) { warn("Enter your username and password."); return }

        info("Signing in…")
        b.btnLogin.isEnabled = false

        Net.run {
            val res = Api.login(server.baseUrl, user, pass)
            Net.ui {
                b.btnLogin.isEnabled = true
                if (res.ok) {
                    Prefs.saveLogin(this, label(selected), server.baseUrl, user, pass)
                    Prefs.setExpiry(this, res.expiresAt)
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    warn(res.message)
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
