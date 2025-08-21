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
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import com.livecopilot.data.Plan
import com.livecopilot.data.PlanManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.livecopilot.data.repository.PreferencesRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class PreferencesActivity : AppCompatActivity() {

    private lateinit var languageSpinner: Spinner
    private lateinit var currencySpinner: Spinner
    private lateinit var planManager: PlanManager
    private lateinit var prefsRepo: PreferencesRepository

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

        planManager = PlanManager(this)
        prefsRepo = PreferencesRepository(this)

        languageSpinner = findViewById(R.id.spinner_language)
        currencySpinner = findViewById(R.id.spinner_currency)
        val planSwitch = findViewById<SwitchCompat>(R.id.switch_plan_pref)
        val planLabel = findViewById<TextView>(R.id.text_plan_label_pref)

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

        // Load existing values (fallback to SharedPreferences), then start observing Room
        loadPreferences()
        observeRoomPreferences()

        // Init plan UI
        fun refreshLabel() {
            val isPro = planManager.isPro()
            planLabel.text = if (isPro) "Plan actual: Pro (ilimitado)" else "Plan actual: Free (24 ítems)"
            planSwitch.isChecked = isPro
        }
        refreshLabel()
        planSwitch.setOnCheckedChangeListener { _, isChecked ->
            planManager.setPlan(if (isChecked) Plan.PRO else Plan.FREE)
            refreshLabel()
        }

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

    private fun observeRoomPreferences() {
        val langValues = resources.getStringArray(R.array.pref_languages_values)
        val currValues = resources.getStringArray(R.array.pref_currencies_values)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Language
                launch {
                    prefsRepo.observe("pref_language").collect { entity ->
                        val v = entity?.value ?: return@collect
                        val idx = langValues.indexOf(v)
                        if (idx >= 0 && languageSpinner.selectedItemPosition != idx) {
                            languageSpinner.setSelection(idx)
                        }
                    }
                }
                // Currency
                launch {
                    prefsRepo.observe("pref_currency").collect { entity ->
                        val v = entity?.value ?: return@collect
                        val idx = currValues.indexOf(v)
                        if (idx >= 0 && currencySpinner.selectedItemPosition != idx) {
                            currencySpinner.setSelection(idx)
                        }
                    }
                }
            }
        }
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

        // Persist into Room (idempotent) without blocking UI
        lifecycleScope.launch {
            runCatching {
                val repo = PreferencesRepository(this@PreferencesActivity)
                repo.set("pref_language", selectedLang)
                repo.set("pref_currency", selectedCurr)
            }
        }
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
