package com.livecopilot.data.repository

import android.content.Context
import com.livecopilot.data.local.LocalDatabaseProvider
import com.livecopilot.data.local.entity.PreferenceEntity
import kotlinx.coroutines.flow.Flow

class PreferencesRepository(private val context: Context) {
    private val dao by lazy { LocalDatabaseProvider.getDatabase(context).preferenceDao() }

    fun observe(key: String): Flow<PreferenceEntity?> = dao.observe(key)

    fun observeAll(): Flow<List<PreferenceEntity>> = dao.observeAll()

    suspend fun set(key: String, value: String) = dao.set(
        PreferenceEntity(key = key, value = value)
    )

    suspend fun delete(key: String) = dao.delete(key)

    suspend fun clearAll() = dao.clearAll()
}
