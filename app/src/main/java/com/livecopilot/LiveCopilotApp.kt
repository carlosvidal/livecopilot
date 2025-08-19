package com.livecopilot

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class LiveCopilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applySavedLocale(this)
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
