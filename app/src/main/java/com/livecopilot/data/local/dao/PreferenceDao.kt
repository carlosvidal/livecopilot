package com.livecopilot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.livecopilot.data.local.entity.PreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preferences WHERE `key` = :key LIMIT 1")
    fun observe(key: String): Flow<PreferenceEntity?>

    @Query("SELECT * FROM preferences")
    fun observeAll(): Flow<List<PreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(preference: PreferenceEntity): Long

    @Query("DELETE FROM preferences WHERE `key` = :key")
    suspend fun delete(key: String): Int

    @Query("DELETE FROM preferences")
    suspend fun clearAll(): Int
}
