package com.livecopilot.data.repository

import android.content.Context
import com.livecopilot.data.local.LocalDatabaseProvider
import com.livecopilot.data.local.entity.FavoriteGenericEntity
import kotlinx.coroutines.flow.Flow

class FavoriteGenericRepository(private val context: Context) {
    private val dao by lazy { LocalDatabaseProvider.getDatabase(context).favoriteGenericDao() }

    fun observeAll(): Flow<List<FavoriteGenericEntity>> = dao.observeAll()

    suspend fun upsert(entity: FavoriteGenericEntity) = dao.upsert(entity)

    suspend fun update(id: String, type: String, name: String, content: String) =
        dao.update(id, type, name, content)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun clearAll() = dao.clearAll()
}
