package com.livecopilot.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_images",
    indices = [Index(value = ["productId"])]
)
data class ProductImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val productId: String,
    val uri: String,
    val width: Int? = null,
    val height: Int? = null,
    val position: Int? = null
)
