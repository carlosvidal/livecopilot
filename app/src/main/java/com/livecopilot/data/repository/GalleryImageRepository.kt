package com.livecopilot.data.repository

import android.content.Context
import com.livecopilot.data.local.LocalDatabaseProvider
import com.livecopilot.data.local.entity.GalleryImageEntity
import kotlinx.coroutines.flow.Flow

class GalleryImageRepository(private val context: Context) {
    private val dao by lazy { LocalDatabaseProvider.getDatabase(context).galleryImageDao() }

    fun observeAll(): Flow<List<GalleryImageEntity>> = dao.observeAll()

    suspend fun upsert(entity: GalleryImageEntity) = dao.upsert(entity)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun clearAll() = dao.clearAll()

    suspend fun count(): Int = dao.count()
}
