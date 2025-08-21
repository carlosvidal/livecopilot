package com.livecopilot.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.livecopilot.utils.ImageUtils
import java.util.UUID

class ProductManager(private val context: Context) {
    
    private val prefs: SharedPreferences by lazy { 
        context.getSharedPreferences("livecopilot_products", Context.MODE_PRIVATE) 
    }
    private val gson by lazy { Gson() }
    private val planManager by lazy { PlanManager(context) }
    
    companion object {
        private const val KEY_PRODUCTS = "products"
        private const val MAX_FREE_PRODUCTS = 24
    }
    
    fun isPremium(): Boolean = planManager.isPro()
    
    fun getAllProducts(): List<Product> {
        val json = prefs.getString(KEY_PRODUCTS, null) ?: return emptyList()
        val type = object : TypeToken<List<Product>>() {}.type
        return gson.fromJson(json, type)
    }
    
    fun addProduct(product: Product): AddProductResult {
        val products = getAllProducts().toMutableList()
        
        // Verificar límite para usuarios gratuitos
        if (!isPremium() && products.size >= MAX_FREE_PRODUCTS) {
            return AddProductResult.LIMIT_REACHED
        }
        
        // Generar ID único si no tiene
        val productWithId = if (product.id.isEmpty()) {
            product.copy(id = UUID.randomUUID().toString())
        } else {
            product
        }
        
        products.add(productWithId)
        saveProducts(products)
        return AddProductResult.SUCCESS
    }
    
    fun updateProduct(product: Product): Boolean {
        val products = getAllProducts().toMutableList()
        val index = products.indexOfFirst { it.id == product.id }
        
        if (index == -1) return false
        
        val old = products[index]
        products[index] = product
        saveProducts(products)
        
        // Si cambió la imagen, eliminar archivo anterior
        if (old.imageUri.isNotEmpty() && old.imageUri != product.imageUri) {
            ImageUtils.deleteImage(old.imageUri)
        }
        
        return true
    }
    
    fun deleteProduct(productId: String): Boolean {
        val products = getAllProducts().toMutableList()
        val toDelete = products.find { it.id == productId }
        val removed = products.removeAll { it.id == productId }
        if (removed) {
            saveProducts(products)
            // Eliminar archivo de imagen del producto eliminado
            toDelete?.imageUri?.takeIf { it.isNotEmpty() }?.let { ImageUtils.deleteImage(it) }
            // Limpieza de huérfanas en directorio interno
            cleanupOrphanImages()
        }
        return removed
    }
    
    fun getProduct(productId: String): Product? {
        return getAllProducts().find { it.id == productId }
    }
    
    fun getProductCount(): Int {
        return getAllProducts().size
    }
    
    fun canAddMoreProducts(): Boolean {
        return isPremium() || getProductCount() < MAX_FREE_PRODUCTS
    }
    
    fun getRemainingSlots(): Int {
        if (isPremium()) return Int.MAX_VALUE
        return MAX_FREE_PRODUCTS - getProductCount()
    }
    
    private fun saveProducts(products: List<Product>) {
        val json = gson.toJson(products)
        prefs.edit().putString(KEY_PRODUCTS, json).apply()
    }

    private fun cleanupOrphanImages() {
        val used = getAllProducts().mapNotNull { it.imageUri.takeIf { uri -> uri.isNotEmpty() } }
        ImageUtils.cleanupUnusedImages(context, used)
    }
    
    enum class AddProductResult {
        SUCCESS,
        LIMIT_REACHED
    }
}