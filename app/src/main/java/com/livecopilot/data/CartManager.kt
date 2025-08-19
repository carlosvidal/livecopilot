package com.livecopilot.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class CartManager(private val context: Context) {
    
    private val prefs: SharedPreferences by lazy { 
        context.getSharedPreferences("livecopilot_cart", Context.MODE_PRIVATE) 
    }
    private val gson by lazy { Gson() }
    
    companion object {
        private const val KEY_CART_ITEMS = "cart_items"
    }
    
    fun addToCart(product: Product, quantity: Int = 1): Boolean {
        val currentItems = getCartItems().toMutableList()
        
        // Buscar si el producto ya existe en el carrito
        val existingItemIndex = currentItems.indexOfFirst { it.product.id == product.id }
        
        if (existingItemIndex >= 0) {
            // Si existe, aumentar la cantidad
            currentItems[existingItemIndex].quantity += quantity
        } else {
            // Si no existe, añadir nuevo item
            currentItems.add(CartItem(product, quantity))
        }
        
        return saveCartItems(currentItems)
    }
    
    fun removeFromCart(productId: String): Boolean {
        val currentItems = getCartItems().toMutableList()
        currentItems.removeAll { it.product.id == productId }
        return saveCartItems(currentItems)
    }
    
    fun updateQuantity(productId: String, newQuantity: Int): Boolean {
        if (newQuantity <= 0) {
            return removeFromCart(productId)
        }
        
        val currentItems = getCartItems().toMutableList()
        val itemIndex = currentItems.indexOfFirst { it.product.id == productId }
        
        if (itemIndex >= 0) {
            currentItems[itemIndex].quantity = newQuantity
            return saveCartItems(currentItems)
        }
        
        return false
    }
    
    fun getCartItems(): List<CartItem> {
        val json = prefs.getString(KEY_CART_ITEMS, "[]")
        val type = object : TypeToken<List<CartItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getCartItemsCount(): Int {
        return getCartItems().sumOf { it.quantity }
    }
    
    fun getCartTotal(): Double {
        return getCartItems().sumOf { it.totalPrice }
    }
    
    fun clearCart(): Boolean {
        return prefs.edit().remove(KEY_CART_ITEMS).commit()
    }
    
    fun isInCart(productId: String): Boolean {
        return getCartItems().any { it.product.id == productId }
    }
    
    private fun saveCartItems(items: List<CartItem>): Boolean {
        val json = gson.toJson(items)
        return prefs.edit().putString(KEY_CART_ITEMS, json).commit()
    }
}