package com.livecopilot

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var shortcutsContainer: LinearLayout
    private val shortcutEditTexts = mutableListOf<TextInputEditText>()

    private val defaultShortcuts = listOf(
        "¡Gracias por tu compra! Aquí está tu link de pago: https://pago.link/123",
        "Si tienes dudas, escríbenos por WhatsApp: https://wa.me/123456789",
        "Envío gratis por compras hoy 🚚",
        "¡Oferta especial solo en vivo!",
        "¿Quieres otro producto? Comenta abajo",
        "Finaliza tu compra aquí: https://pago.link/xyz"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        shortcutsContainer = findViewById(R.id.shortcuts_editor_container)
        setupShortcutEditors()
        loadShortcuts()

        findViewById<FloatingActionButton>(R.id.fab_save_settings).setOnClickListener {
            saveShortcuts()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupShortcutEditors() {
        val inflater = LayoutInflater.from(this)
        for (i in 0 until 6) {
            val editorView = inflater.inflate(R.layout.item_shortcut_editor, shortcutsContainer, false)
            val editText = editorView.findViewById<TextInputEditText>(R.id.shortcut_edit_text)
            editText.hint = "Atajo ${i + 1}"
            shortcutEditTexts.add(editText)
            shortcutsContainer.addView(editorView)
        }
    }

    private fun loadShortcuts() {
        val prefs = getSharedPreferences("livecopilot_prefs", Context.MODE_PRIVATE)
        shortcutEditTexts.forEachIndexed { index, editText ->
            val savedShortcut = prefs.getString("shortcut_$index", defaultShortcuts.getOrElse(index) { "" })
            editText.setText(savedShortcut)
        }
    }

    private fun saveShortcuts() {
        val prefs = getSharedPreferences("livecopilot_prefs", Context.MODE_PRIVATE).edit()
        shortcutEditTexts.forEachIndexed { index, editText ->
            prefs.putString("shortcut_$index", editText.text.toString())
        }
        prefs.apply()

        Toast.makeText(this, "Atajos guardados correctamente", Toast.LENGTH_SHORT).show()
        finish() // Cierra la actividad después de guardar
    }
}
