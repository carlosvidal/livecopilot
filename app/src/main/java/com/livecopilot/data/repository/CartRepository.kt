package com.livecopilot.data.repository

import android.content.Context
import com.livecopilot.data.Product
import com.livecopilot.data.local.LocalDatabaseProvider
import com.livecopilot.data.local.entity.CartItemEntity
import kotlinx.coroutines.flow.Flow

class CartRepository(context: Context) {
    private val dao = LocalDatabaseProvider.getDatabase(context).cartDao()

    fun observeAll(): Flow<List<CartItemEntity>> = dao.observeAll()

    suspend fun getAllOnce(): List<CartItemEntity> = dao.getAllOnce()

    suspend fun upsert(product: Product, quantity: Int) {
        val entity = CartItemEntity(
            productId = product.id,
            name = product.name,
            price = product.price,
            link = product.link,
            imageUri = product.imageUri,
            quantity = quantity
        )
        dao.insert(entity)
    }

    suspend fun updateQuantity(productId: String, quantity: Int) {
        if (quantity <= 0) {
            dao.delete(productId)
        } else {
            dao.updateQuantity(productId, quantity)
        }
    }

    suspend fun delete(productId: String) = dao.delete(productId)

    suspend fun clear() = dao.clear()
}
