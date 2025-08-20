package com.livecopilot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.os.Build
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.widget.Toast

class PreferencesActivity : AppCompatActivity() {

    private lateinit var languageSpinner: Spinner
    private lateinit var currencySpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Preferencias"
        // Color de encabezado igual al botón de Configuración
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.settings_primary))
        toolbar.setTitleTextColor(Color.WHITE)
        toolbar.navigationIcon?.setTint(Color.WHITE)

        languageSpinner = findViewById(R.id.spinner_language)
        currencySpinner = findViewById(R.id.spinner_currency)

        // Setup adapters
        val langAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.pref_languages_display,
            android.R.layout.simple_spinner_item
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        languageSpinner.adapter = langAdapter

        val currAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.pref_currencies_display,
            android.R.layout.simple_spinner_item
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        currencySpinner.adapter = currAdapter

        // Load existing values
        loadPreferences()

        findViewById<Button>(R.id.btn_save_prefs).setOnClickListener {
            savePreferences()
            // Aplicar idioma inmediatamente usando AppCompatDelegate
            LiveCopilotApp.applySavedLocale(this)
            // Reinicio suave del servicio de overlay para recargar recursos localizados
            restartOverlayService()
            Toast.makeText(this, "Preferencias guardadas", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("livecopilot_prefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("pref_language", "system")
        val savedCurr = prefs.getString("pref_currency", "USD")

        val langValues = resources.getStringArray(R.array.pref_languages_values)
        val currValues = resources.getStringArray(R.array.pref_currencies_values)

        val langIndex = langValues.indexOf(savedLang)
        val currIndex = currValues.indexOf(savedCurr)

        if (langIndex >= 0) languageSpinner.setSelection(langIndex) else languageSpinner.setSelection(0)
        if (currIndex >= 0) currencySpinner.setSelection(currIndex)
    }

    private fun savePreferences() {
        val langValues = resources.getStringArray(R.array.pref_languages_values)
        val currValues = resources.getStringArray(R.array.pref_currencies_values)

        val selectedLang = langValues.getOrNull(languageSpinner.selectedItemPosition) ?: "system"
        val selectedCurr = currValues.getOrNull(currencySpinner.selectedItemPosition) ?: "USD"

        val prefs = getSharedPreferences("livecopilot_prefs", Context.MODE_PRIVATE).edit()
        prefs.putString("pref_language", selectedLang)
        prefs.putString("pref_currency", selectedCurr)
        prefs.apply()
    }

    private fun restartOverlayService() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        // Parar si está corriendo
        stopService(serviceIntent)
        // Arrancar de nuevo para que se regenere con el nuevo locale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
