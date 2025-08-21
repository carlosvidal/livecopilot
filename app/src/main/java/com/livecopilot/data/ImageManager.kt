package com.livecopilot.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.livecopilot.utils.ImageUtils
import java.util.UUID

data class GalleryImage(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val imagePath: String,
    val description: String = "",
    val dateAdded: Long = System.currentTimeMillis()
)

class ImageManager(private val context: Context) {
    
    private val prefs: SharedPreferences by lazy { 
        context.getSharedPreferences("livecopilot_gallery", Context.MODE_PRIVATE) 
    }
    private val gson by lazy { Gson() }
    private val planManager by lazy { PlanManager(context) }
    
    companion object {
        private const val KEY_IMAGES = "gallery_images"
    }
    
    fun getAllImages(): List<GalleryImage> {
        val json = prefs.getString(KEY_IMAGES, "[]") ?: "[]"
        val type = object : TypeToken<List<GalleryImage>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    fun addImage(image: GalleryImage): AddImageResult {
        return try {
            val currentImages = getAllImages().toMutableList()
            if (!planManager.isPro() && currentImages.size >= PlanManager.MAX_FREE_ITEMS) {
                return AddImageResult.LIMIT_REACHED
            }
            currentImages.add(image)
            saveImages(currentImages)
            AddImageResult.SUCCESS
        } catch (e: Exception) {
            AddImageResult.ERROR
        }
    }
    
    fun deleteImage(imageId: String): Boolean {
        return try {
            val currentImages = getAllImages().toMutableList()
            val toDelete = currentImages.find { it.id == imageId }
            val updated = currentImages.filter { it.id != imageId }
            toDelete?.imagePath?.let { path -> ImageUtils.deleteImage(path) }
            saveImages(updated)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun getImage(imageId: String): GalleryImage? {
        return getAllImages().find { it.id == imageId }
    }
    
    fun getImageCount(): Int {
        return getAllImages().size
    }
    
    fun canAddMoreImages(): Boolean {
        return planManager.isPro() || getImageCount() < PlanManager.MAX_FREE_ITEMS
    }
    
    fun remainingSlots(): Int {
        return if (planManager.isPro()) Int.MAX_VALUE else (PlanManager.MAX_FREE_ITEMS - getImageCount())
    }
    
    private fun saveImages(images: List<GalleryImage>) {
        val json = gson.toJson(images)
        prefs.edit().putString(KEY_IMAGES, json).apply()
    }

    enum class AddImageResult { SUCCESS, LIMIT_REACHED, ERROR }
}