package uk.televo.player

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import uk.televo.player.databinding.ActivityHomeBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The hub: Live TV / Movies / Series / Radio + a fully working action bar. */
class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding

    private val langCodes = listOf("en", "fr", "es", "pt", "ar")
    private val langNames = listOf("English", "Français", "Español", "Português", "العربية")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tileLive.setOnClickListener { startActivity(Intent(this, LiveActivity::class.java)) }
        b.tileMovies.setOnClickListener { startActivity(Intent(this, MoviesActivity::class.java)) }
        b.tileSeries.setOnClickListener { startActivity(Intent(this, SeriesActivity::class.java)) }
        b.tileRadio.setOnClickListener { startActivity(Intent(this, RadioActivity::class.java)) }

        b.actRefresh.setOnClickListener { refresh() }
        b.actLang.setOnClickListener { languageDialog() }
        b.actTimeshift.setOnClickListener { startActivity(Intent(this, LiveActivity::class.java)) }
        b.actSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.actInfo.setOnClickListener { infoDialog() }
        b.actPower.setOnClickListener { powerDialog() }

        showAccount()
        b.tileLive.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        showAccount()
    }

    // ---------- status ----------

    private fun showAccount() {
        b.homePlaylist.text = Prefs.serverName(this) ?: "Televo"
        b.homeState.text = "●  " + getString(R.string.active)
        b.homeExpires.text = expiryText()
        b.chipLive.text = "● " + getString(R.string.cached)
        b.chipMovies.text = "● " + getString(R.string.cached)
        b.chipSeries.text = "● " + getString(R.string.cached)
        b.chipRadio.text = "● " + getString(R.string.ready)
        b.langLabel.text = getString(R.string.language) + " (" + Prefs.language(this).uppercase() + ")"
        b.tsLabel.text = getString(R.string.catch_up)
    }

    private fun expiryText(): String {
        val e = Prefs.expiry(this)
        if (e <= 0L) return getString(R.string.unlimited)
        val d = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(e * 1000L))
        return getString(R.string.expires) + " " + d
    }


    // ---------- refresh (re-check the subscription) ----------

    private fun refresh() {
        val host = Prefs.host(this); val user = Prefs.username(this); val pass = Prefs.password(this)
        if (host == null || user == null || pass == null) { showAccount(); return }
        Toast.makeText(this, "…", Toast.LENGTH_SHORT).show()
        Net.run {
            val res = Api.login(host, user, pass)
            Net.ui {
                if (res.ok) Prefs.setExpiry(this, res.expiresAt)
                showAccount()
                Toast.makeText(this, getString(R.string.refreshed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- language ----------

    private fun languageDialog() {
        val current = langCodes.indexOf(Prefs.language(this)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this, R.style.Theme_Televo_Dialog)
            .setTitle(R.string.choose_language)
            .setSingleChoiceItems(langNames.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                val code = langCodes[which]
                Prefs.setLanguage(this, code)
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }


    // ---------- info / about ----------

    private fun infoDialog() {
        val msg = "Televo v" + BuildInfo.VERSION +
            "\n" + getString(R.string.server) + ": " + (Prefs.serverName(this) ?: "-") +
            "\n" + getString(R.string.account) + ": " + (Prefs.username(this) ?: "-") +
            "\n" + expiryText()
        MaterialAlertDialogBuilder(this, R.style.Theme_Televo_Dialog)
            .setTitle(R.string.about)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------- power ----------

    private fun powerDialog() {
        MaterialAlertDialogBuilder(this, R.style.Theme_Televo_Dialog)
            .setTitle(R.string.app_name)
            .setMessage(R.string.power_msg)
            .setPositiveButton(R.string.log_out) { _, _ -> logout() }
            .setNegativeButton(R.string.exit) { _, _ -> finishAffinity() }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun logout() {
        Prefs.logout(this)
        startActivity(Intent(this, LoginActivity::class.java))
        finishAffinity()
    }
}
