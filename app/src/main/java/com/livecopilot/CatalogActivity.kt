package com.livecopilot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.livecopilot.data.Product
import com.livecopilot.data.ProductManager

class CatalogActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProductAdapter
    private lateinit var fabAddProduct: FloatingActionButton
    private lateinit var emptyView: TextView
    private lateinit var productManager: ProductManager
    private val products = mutableListOf<Product>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)
        
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Catálogo"
        
        productManager = ProductManager(this)
        setupViews()
        loadProducts()
    }
    
    private fun setupViews() {
        recyclerView = findViewById(R.id.recycler_products)
        fabAddProduct = findViewById(R.id.fab_add_product)
        emptyView = findViewById(R.id.empty_view)
        
        adapter = ProductAdapter(products) { product ->
            // Click en producto
            openProductDetail(product)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        fabAddProduct.setOnClickListener {
            openAddProduct()
        }
    }
    
    private fun loadProducts() {
        products.clear()
        products.addAll(productManager.getAllProducts())
        adapter.notifyDataSetChanged()
        updateEmptyView()
        updateFabText()
    }
    
    private fun updateEmptyView() {
        if (products.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            emptyView.text = "No tienes productos aún.\n¡Agrega tu primer producto!"
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }
    
    private fun updateFabText() {
        val remaining = productManager.getRemainingSlots()
        if (productManager.isPremium()) {
            fabAddProduct.isEnabled = true
        } else {
            fabAddProduct.isEnabled = productManager.canAddMoreProducts()
            if (remaining <= 0) {
                // Podrías cambiar el ícono o deshabilitarlo
                fabAddProduct.alpha = 0.5f
            } else {
                fabAddProduct.alpha = 1.0f
            }
        }
    }
    
    private fun openAddProduct() {
        if (!productManager.canAddMoreProducts()) {
            val remaining = productManager.getRemainingSlots()
            android.app.AlertDialog.Builder(this)
                .setTitle("Límite alcanzado")
                .setMessage("Has alcanzado el límite de $MAX_FREE_PRODUCTS productos en el plan gratuito.\n\n¿Te gustaría actualizar a Premium para productos ilimitados?")
                .setPositiveButton("Más tarde") { dialog, _ -> dialog.dismiss() }
                .setNegativeButton("Ver Premium") { dialog, _ -> 
                    dialog.dismiss()
                    // TODO: Abrir pantalla de premium
                }
                .show()
            return
        }
        
        val intent = Intent(this, AddProductActivity::class.java)
        startActivity(intent)
    }
    
    private fun openProductDetail(product: Product) {
        val intent = Intent(this, AddProductActivity::class.java)
        intent.putExtra("product_id", product.id)
        startActivity(intent)
    }
    
    override fun onResume() {
        super.onResume()
        // Recargar productos cuando regresamos de AddProductActivity
        loadProducts()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    companion object {
        private const val MAX_FREE_PRODUCTS = 24
    }
}