package com.livecopilot.data.migration

import android.content.Context
import com.livecopilot.data.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PreferencesMigrations {
    private const val PREFS_NAME = "livecopilot_prefs"

    fun migratePrefsToRoomIfPresent(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lang = sp.getString("pref_language", null)
                val curr = sp.getString("pref_currency", null)
                if (lang == null && curr == null) return@launch
                val repo = PreferencesRepository(context)
                lang?.let { runCatching { repo.set("pref_language", it) } }
                curr?.let { runCatching { repo.set("pref_currency", it) } }
            } catch (_: Throwable) { }
        }
    }
}
