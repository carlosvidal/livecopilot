package com.livecopilot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.Product
import com.livecopilot.util.CurrencyUtils
import com.livecopilot.utils.ImageUtils

class ProductCardAdapter(
    private val products: List<Product>,
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductCardAdapter.ViewHolder>() {

    private var selectionMode: Boolean = false
    private val selectedIds = linkedSetOf<String>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.product_image)
        val name: TextView = view.findViewById(R.id.product_name)
        val price: TextView = view.findViewById(R.id.product_price)
        val check: CheckBox = view.findViewById(R.id.product_check)
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

        // Selection UI
        holder.check.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.check.setOnCheckedChangeListener(null)
        holder.check.isChecked = selectedIds.contains(product.id)
        holder.check.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedIds.add(product.id) else selectedIds.remove(product.id)
        }

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                val nowChecked = !selectedIds.contains(product.id)
                if (nowChecked) selectedIds.add(product.id) else selectedIds.remove(product.id)
                notifyItemChanged(position)
            } else {
                onProductClick(product)
            }
        }
    }

    override fun getItemCount(): Int = products.size

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode != enabled) {
            selectionMode = enabled
            if (!enabled) selectedIds.clear()
            notifyDataSetChanged()
        }
    }

    fun getSelectedIds(): List<String> = selectedIds.toList()

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
    }
}
