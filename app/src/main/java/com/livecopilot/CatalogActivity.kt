package com.livecopilot

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.livecopilot.data.Product
import com.livecopilot.data.ProductManager

class CatalogActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProductCardAdapter
    private lateinit var fabAddProduct: FloatingActionButton
    private lateinit var emptyView: TextView
    private lateinit var productManager: ProductManager
    private val products = mutableListOf<Product>()
    private var selectionMode: Boolean = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)
        
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Catálogo"
        // Color de encabezado igual al botón de Catálogo
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.catalog_primary))
        toolbar.setTitleTextColor(Color.WHITE)
        toolbar.navigationIcon?.setTint(Color.WHITE)
        toolbar.overflowIcon?.setTint(Color.WHITE)
        
        productManager = ProductManager(this)
        setupViews()
        loadProducts()
    }
    
    private fun setupViews() {
        recyclerView = findViewById(R.id.recycler_products)
        fabAddProduct = findViewById(R.id.fab_add_product)
        emptyView = findViewById(R.id.empty_view)
        
        adapter = ProductCardAdapter(products) { product ->
            // Click en producto
            openProductDetail(product)
        }
        
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(GridSpacingItemDecoration(2, 12.dp(), includeEdge = true))
        
        fabAddProduct.setOnClickListener {
            openAddProduct()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(
            if (selectionMode) R.menu.menu_favorites_selection else R.menu.menu_favorites,
            menu
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_eliminar -> { enterSelectionMode(); true }
            R.id.action_confirm_delete -> { confirmBatchDelete(); true }
            android.R.id.home -> {
                if (selectionMode) { exitSelectionMode(); true } else super.onOptionsItemSelected(item)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun enterSelectionMode() {
        if (!selectionMode) {
            selectionMode = true
            adapter.setSelectionMode(true)
            fabAddProduct.hide()
            invalidateOptionsMenu()
        }
    }

    private fun exitSelectionMode() {
        if (selectionMode) {
            selectionMode = false
            adapter.setSelectionMode(false)
            fabAddProduct.show()
            invalidateOptionsMenu()
        }
    }

    private fun confirmBatchDelete() {
        val ids = adapter.getSelectedIds()
        if (ids.isEmpty()) { exitSelectionMode(); return }
        android.app.AlertDialog.Builder(this)
            .setTitle("Eliminar")
            .setMessage("¿Eliminar ${'$'}{ids.size} producto(s)?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { dialog, _ ->
                ids.forEach { id -> productManager.deleteProduct(id) }
                loadProducts()
                exitSelectionMode()
                dialog.dismiss()
            }
            .show()
    }

    // ItemDecoration para espaciado uniforme
    private inner class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val spacing: Int,
        private val includeEdge: Boolean
    ) : ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view) // item position
            val column = position % spanCount // item column

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount
                outRect.right = (column + 1) * spacing / spanCount
                if (position < spanCount) { // top edge
                    outRect.top = spacing
                }
                outRect.bottom = spacing // item bottom
            } else {
                outRect.left = column * spacing / spanCount
                outRect.right = spacing - (column + 1) * spacing / spanCount
                if (position >= spanCount) {
                    outRect.top = spacing // item top
                }
            }
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    
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
        return if (selectionMode) {
            exitSelectionMode(); true
        } else { finish(); true }
    }
    
    companion object {
        private const val MAX_FREE_PRODUCTS = 24
    }
}