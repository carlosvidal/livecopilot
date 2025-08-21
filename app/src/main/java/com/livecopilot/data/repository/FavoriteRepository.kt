package com.livecopilot.data.repository

import android.content.Context
import com.livecopilot.data.local.LocalDatabaseProvider
import com.livecopilot.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val context: Context) {
    private val dao by lazy { LocalDatabaseProvider.getDatabase(context).favoriteDao() }

    fun observeFavoriteIds(): Flow<List<String>> = dao.observeFavoriteIds()

    fun isFavorite(productId: String): Flow<Boolean> = dao.isFavorite(productId)

    suspend fun add(productId: String) = dao.add(FavoriteEntity(productId = productId))

    suspend fun remove(productId: String) = dao.remove(productId)

    suspend fun clearAll() = dao.clearAll()
}
