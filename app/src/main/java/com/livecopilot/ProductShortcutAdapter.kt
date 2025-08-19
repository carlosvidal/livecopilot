package com.livecopilot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.Product
import com.livecopilot.utils.ImageUtils

class ProductShortcutAdapter(
    private val products: List<Product>,
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductShortcutAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.product_icon)
        val badge: TextView = view.findViewById(R.id.badge_number)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_shortcut, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val product = products[position]
        // Badge con número de orden (1-based)
        holder.badge.text = (position + 1).toString()

        // Cargar icono: si tiene imageUri, usarla; si no, placeholder ic_box
        if (product.imageUri.isNotEmpty()) {
            try {
                val uri = ImageUtils.getImageUri(holder.itemView.context, product.imageUri)
                if (uri != null) {
                    holder.icon.setImageURI(uri)
                    holder.icon.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    holder.icon.setImageResource(R.drawable.ic_box)
                    holder.icon.scaleType = ImageView.ScaleType.CENTER
                }
            } catch (e: Exception) {
                holder.icon.setImageResource(R.drawable.ic_box)
                holder.icon.scaleType = ImageView.ScaleType.CENTER
            }
        } else {
            holder.icon.setImageResource(R.drawable.ic_box)
            holder.icon.scaleType = ImageView.ScaleType.CENTER
        }

        holder.itemView.setOnClickListener { onProductClick(product) }
    }

    override fun getItemCount(): Int = products.size
}
