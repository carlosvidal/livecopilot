package com.livecopilot.data

data class Product(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val link: String = "",
    val imageUri: String = ""
)