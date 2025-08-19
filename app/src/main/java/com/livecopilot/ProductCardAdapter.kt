package com.livecopilot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.Product
import com.livecopilot.util.CurrencyUtils
import com.livecopilot.utils.ImageUtils

class ProductCardAdapter(
    private val products: List<Product>,
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductCardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.product_image)
        val name: TextView = view.findViewById(R.id.product_name)
        val price: TextView = view.findViewById(R.id.product_price)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val product = products[position]
        holder.name.text = product.name
        holder.price.text = CurrencyUtils.formatAmount(holder.itemView.context, product.price)

        if (product.imageUri.isNotEmpty()) {
            try {
                val uri = ImageUtils.getImageUri(holder.itemView.context, product.imageUri)
                if (uri != null) {
                    holder.image.setImageURI(uri)
                    holder.image.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    holder.image.setImageResource(R.drawable.ic_box)
                    holder.image.scaleType = ImageView.ScaleType.CENTER
                }
            } catch (e: Exception) {
                holder.image.setImageResource(R.drawable.ic_box)
                holder.image.scaleType = ImageView.ScaleType.CENTER
            }
        } else {
            holder.image.setImageResource(R.drawable.ic_box)
            holder.image.scaleType = ImageView.ScaleType.CENTER
        }

        holder.itemView.setOnClickListener { onProductClick(product) }
    }

    override fun getItemCount(): Int = products.size
}
