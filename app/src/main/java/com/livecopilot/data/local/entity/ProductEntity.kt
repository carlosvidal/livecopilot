package com.livecopilot.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "products",
    indices = [Index(value = ["name"])]
)
data class ProductEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
    val priceCents: Int,
    val currency: String,
    val isAvailable: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
