package com.livecopilot.data

data class Favorite(
    val id: String,
    val type: FavoriteType,
    val name: String,
    val content: String
)
