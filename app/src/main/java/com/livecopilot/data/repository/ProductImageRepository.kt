package com.livecopilot.data.repository

import android.content.Context
import com.livecopilot.data.local.LocalDatabaseProvider
import com.livecopilot.data.local.entity.ProductImageEntity
import kotlinx.coroutines.flow.Flow

class ProductImageRepository(private val context: Context) {
    private val dao by lazy { LocalDatabaseProvider.getDatabase(context).productImageDao() }

    fun observeByProductId(productId: String): Flow<List<ProductImageEntity>> =
        dao.observeByProductId(productId)

    suspend fun upsertAll(images: List<ProductImageEntity>) = dao.upsertAll(images)

    suspend fun deleteByProductId(productId: String) = dao.deleteByProductId(productId)

    suspend fun clearAll() = dao.clearAll()
}
