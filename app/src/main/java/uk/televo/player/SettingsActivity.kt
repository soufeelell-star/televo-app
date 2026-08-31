package uk.televo.player

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import uk.televo.player.databinding.ActivitySettingsBinding

/** Player settings: startup, sorting, search scope, and PIN. All persisted. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        // Play last played channel on startup
        b.swPlayLast.isChecked = Prefs.playLastOnStartup(this)
        b.swPlayLast.setOnCheckedChangeListener { _, v -> Prefs.setPlayLastOnStartup(this, v) }

        // Search within the selected category
        b.swSearchCat.isChecked = Prefs.searchInCategory(this)
        b.swSearchCat.setOnCheckedChangeListener { _, v -> Prefs.setSearchInCategory(this, v) }

        // Sort categories (Default / A→Z / Z→A)
        b.valSortCats.text = sortCatLabel(Prefs.sortCategories(this))
        b.rowSortCats.setOnClickListener {
            val next = (Prefs.sortCategories(this) + 1) % 3
            Prefs.setSortCategories(this, next)
            b.valSortCats.text = sortCatLabel(next)
        }

        // Sort content list (Default / A→Z / Z→A / By number)
        b.valSortContent.text = sortContentLabel(Prefs.sortContent(this))
        b.rowSortContent.setOnClickListener {
            val next = (Prefs.sortContent(this) + 1) % 4
            Prefs.setSortContent(this, next)
            b.valSortContent.text = sortContentLabel(next)
        }

        b.btnChangePin.setOnClickListener { changePin() }
        b.btnClose.setOnClickListener { finish() }

        b.swPlayLast.requestFocus()
    }

    private fun sortCatLabel(v: Int): String = when (v) {
        1 -> getString(R.string.opt_az)
        2 -> getString(R.string.opt_za)
        else -> getString(R.string.opt_default)
    }

    private fun sortContentLabel(v: Int): String = when (v) {
        1 -> getString(R.string.opt_az)
        2 -> getString(R.string.opt_za)
        3 -> getString(R.string.opt_by_number)
        else -> getString(R.string.opt_default)
    }

    private fun changePin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "0000"
        }
        MaterialAlertDialogBuilder(this, R.style.Theme_Televo_Dialog)
            .setTitle(R.string.new_pin)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length == 4 && pin.all { it.isDigit() }) {
                    Prefs.setPin(this, pin)
                    Toast.makeText(this, getString(R.string.pin_changed), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.pin_invalid), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
