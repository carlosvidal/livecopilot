package com.livecopilot.data.repository

import android.content.Context
import com.livecopilot.data.local.LocalDatabaseProvider
import com.livecopilot.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val context: Context) {
    private val dao by lazy { LocalDatabaseProvider.getDatabase(context).productDao() }

    fun observeAll(): Flow<List<ProductEntity>> = dao.observeAll()

    fun observeById(id: String): Flow<ProductEntity?> = dao.observeById(id)

    fun searchByName(query: String): Flow<List<ProductEntity>> = dao.searchByName(query)

    suspend fun upsert(product: ProductEntity) = dao.upsert(product)

    suspend fun upsertAll(products: List<ProductEntity>) = dao.upsertAll(products)

    suspend fun deleteById(id: String) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAll()
}
