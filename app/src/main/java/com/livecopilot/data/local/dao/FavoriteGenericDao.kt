package com.livecopilot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.livecopilot.data.local.entity.FavoriteGenericEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteGenericDao {
    @Query("SELECT * FROM favorites_generic ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FavoriteGenericEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteGenericEntity)

    @Query("UPDATE favorites_generic SET type = :type, name = :name, content = :content WHERE id = :id")
    suspend fun update(id: String, type: String, name: String, content: String): Int

    @Query("DELETE FROM favorites_generic WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM favorites_generic")
    suspend fun clearAll(): Int
}
