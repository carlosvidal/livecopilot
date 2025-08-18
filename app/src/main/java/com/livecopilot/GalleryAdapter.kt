package com.livecopilot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.GalleryImage
import com.livecopilot.utils.ImageUtils

class GalleryAdapter(
    private val images: MutableList<GalleryImage>,
    private val onItemClick: (GalleryImage) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    class GalleryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.gallery_item_image)
        val nameText: TextView = view.findViewById(R.id.gallery_item_name)
        val priceText: TextView = view.findViewById(R.id.gallery_item_price)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery, parent, false)
        return GalleryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        val image = images[position]
        
        holder.nameText.text = image.name
        holder.priceText.text = if (image.description.isNotEmpty()) {
            image.description
        } else {
            "Sin descripción"
        }
        
        // Cargar imagen de la galería
        try {
            val uri = ImageUtils.getImageUri(holder.itemView.context, image.imagePath)
            if (uri != null) {
                holder.imageView.setImageURI(uri)
                holder.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                
                // Para efecto masonry, generar altura aleatoria basada en el hash de la imagen
                val randomHeight = 150 + (image.id.hashCode() % 150).let { if (it < 0) -it else it }
                holder.imageView.layoutParams.height = (randomHeight * holder.itemView.context.resources.displayMetrics.density).toInt()
                holder.imageView.requestLayout()
            } else {
                holder.imageView.setImageResource(R.drawable.ic_image)
                holder.imageView.scaleType = ImageView.ScaleType.CENTER
                holder.imageView.layoutParams.height = (200 * holder.itemView.context.resources.displayMetrics.density).toInt()
                holder.imageView.requestLayout()
            }
        } catch (e: Exception) {
            holder.imageView.setImageResource(R.drawable.ic_image)
            holder.imageView.scaleType = ImageView.ScaleType.CENTER
            holder.imageView.layoutParams.height = (200 * holder.itemView.context.resources.displayMetrics.density).toInt()
            holder.imageView.requestLayout()
        }
        
        // Click listener
        holder.itemView.setOnClickListener {
            onItemClick(image)
        }
    }

    override fun getItemCount() = images.size
    
    fun updateImages(newImages: List<GalleryImage>) {
        images.clear()
        images.addAll(newImages)
        notifyDataSetChanged()
    }
}