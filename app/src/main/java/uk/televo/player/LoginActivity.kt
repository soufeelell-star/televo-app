package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import uk.televo.player.databinding.ActivityLoginBinding

/**
 * The only gate into the app: pick a server, type username + password, log in.
 * No activation, no expiry — a successful login is saved and the customer stays
 * logged in until they log out.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Server 1 / Server 2 / Server 3
        b.spHost.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Servers.names
        )

        b.btnLogin.setOnClickListener { doLogin() }
        b.inUser.requestFocus()
    }

    private fun doLogin() {
        val server = Servers.at(b.spHost.selectedItemPosition)
        if (server == null) { warn("Please choose a server."); return }

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
