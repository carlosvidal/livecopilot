package com.livecopilot

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.livecopilot.data.migration.ProductMigrations

class LiveCopilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applySavedLocale(this)
        // Migrate existing SharedPreferences-based products into Room (one-time if DB is empty)
        ProductMigrations.migratePrefsToRoomIfNeeded(this)
    }

    companion object {
        fun applySavedLocale(context: Context) {
            val prefs = context.getSharedPreferences("livecopilot_prefs", Context.MODE_PRIVATE)
            val langTag = prefs.getString("pref_language", "system") ?: "system"
            if (langTag.equals("system", ignoreCase = true) || langTag.isBlank()) {
                // Follow device locale
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            } else {
                val locales = LocaleListCompat.forLanguageTags(langTag)
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }
    }
}
