package com.livecopilot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gallery_images")
data class GalleryImageEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imagePath: String,
    val description: String,
    val dateAdded: Long
)
