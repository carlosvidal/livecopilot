package com.livecopilot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.CartItem
import com.livecopilot.utils.ImageUtils
import java.text.NumberFormat
import java.util.Locale

class CartAdapter(
    private val cartItems: MutableList<CartItem>,
    private val onQuantityChanged: (CartItem, Int) -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    class CartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.cart_item_image)
        val nameText: TextView = view.findViewById(R.id.cart_item_name)
        val priceText: TextView = view.findViewById(R.id.cart_item_price)
        val quantityText: TextView = view.findViewById(R.id.cart_item_quantity)
        val decreaseBtn: ImageView = view.findViewById(R.id.btn_decrease_quantity)
        val increaseBtn: ImageView = view.findViewById(R.id.btn_increase_quantity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cart, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val cartItem = cartItems[position]
        val product = cartItem.product
        
        holder.nameText.text = product.name
        
        // Formatear precio unitario
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        holder.priceText.text = format.format(product.price)
        
        // Mostrar cantidad
        holder.quantityText.text = cartItem.quantity.toString()
        
        // Cargar imagen del producto
        if (product.imageUri.isNotEmpty()) {
            try {
                val uri = ImageUtils.getImageUri(holder.itemView.context, product.imageUri)
                if (uri != null) {
                    holder.imageView.setImageURI(uri)
                    holder.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    holder.imageView.setImageResource(R.drawable.ic_box)
                    holder.imageView.scaleType = ImageView.ScaleType.CENTER
                }
            } catch (e: Exception) {
                holder.imageView.setImageResource(R.drawable.ic_box)
                holder.imageView.scaleType = ImageView.ScaleType.CENTER
            }
        } else {
            holder.imageView.setImageResource(R.drawable.ic_box)
            holder.imageView.scaleType = ImageView.ScaleType.CENTER
        }
        
        // Configurar botones de cantidad
        holder.increaseBtn.setOnClickListener {
            val newQuantity = cartItem.quantity + 1
            onQuantityChanged(cartItem, newQuantity)
        }
        
        holder.decreaseBtn.setOnClickListener {
            val newQuantity = cartItem.quantity - 1
            onQuantityChanged(cartItem, newQuantity)
        }
    }

    override fun getItemCount() = cartItems.size
    
    fun updateItems(newItems: List<CartItem>) {
        cartItems.clear()
        cartItems.addAll(newItems)
        notifyDataSetChanged()
    }
}