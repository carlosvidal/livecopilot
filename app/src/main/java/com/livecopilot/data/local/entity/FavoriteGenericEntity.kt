package com.livecopilot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites_generic")
data class FavoriteGenericEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val content: String,
    val createdAt: Long
)
