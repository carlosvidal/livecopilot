package com.livecopilot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.livecopilot.data.local.entity.GalleryImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryImageDao {
    @Query("SELECT * FROM gallery_images ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<GalleryImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GalleryImageEntity)

    @Query("DELETE FROM gallery_images WHERE id = :id")
    suspend fun delete(id: String): Int

    @Query("DELETE FROM gallery_images")
    suspend fun clearAll(): Int

    @Query("SELECT COUNT(*) FROM gallery_images")
    suspend fun count(): Int
}
