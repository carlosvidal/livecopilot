package com.livecopilot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.livecopilot.data.local.entity.ProductImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductImageDao {
    @Query("SELECT * FROM product_images WHERE productId = :productId ORDER BY COALESCE(position, 0) ASC, id ASC")
    fun observeByProductId(productId: String): Flow<List<ProductImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(images: List<ProductImageEntity>): List<Long>

    @Query("DELETE FROM product_images WHERE productId = :productId")
    suspend fun deleteByProductId(productId: String): Int

    @Query("DELETE FROM product_images")
    suspend fun clearAll(): Int
}
