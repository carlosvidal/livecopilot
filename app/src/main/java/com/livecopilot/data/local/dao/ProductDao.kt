package com.livecopilot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.livecopilot.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchByName(query: String): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<ProductEntity>): List<Long>

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM products")
    suspend fun clearAll(): Int
}
