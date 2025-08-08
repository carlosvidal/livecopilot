package com.livecopilot

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.Product
import com.livecopilot.utils.ImageUtils
import java.text.NumberFormat
import java.util.Locale

class ProductAdapter(
    private val products: List<Product>,
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.product_image)
        val nameText: TextView = view.findViewById(R.id.product_name)
        val descriptionText: TextView = view.findViewById(R.id.product_description)
        val priceText: TextView = view.findViewById(R.id.product_price)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        
        holder.nameText.text = product.name
        holder.descriptionText.text = product.description
        
        // Formatear precio
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        holder.priceText.text = format.format(product.price)
        
        // Cargar imagen del producto
        if (product.imageUri.isNotEmpty()) {
            try {
                val uri = ImageUtils.getImageUri(holder.itemView.context, product.imageUri)
                if (uri != null) {
                    holder.imageView.setImageURI(uri)
                    holder.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    throw Exception("No se pudo cargar la imagen")
                }
            } catch (e: Exception) {
                // Si hay error al cargar la imagen, usar placeholder
                holder.imageView.setImageResource(R.drawable.ic_box)
                holder.imageView.scaleType = ImageView.ScaleType.CENTER
            }
        } else {
            // Sin imagen, usar placeholder
            holder.imageView.setImageResource(R.drawable.ic_box)
            holder.imageView.scaleType = ImageView.ScaleType.CENTER
        }
        
        holder.itemView.setOnClickListener {
            onProductClick(product)
        }
    }

    override fun getItemCount() = products.size
}