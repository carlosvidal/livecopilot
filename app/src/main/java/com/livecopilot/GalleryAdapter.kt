package com.livecopilot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.GalleryImage
import com.livecopilot.utils.ImageUtils
import coil.load

class GalleryAdapter(
    private val images: MutableList<GalleryImage>,
    private val onItemClick: (GalleryImage) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    class GalleryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.gallery_item_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery, parent, false)
        return GalleryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        val image = images[position]

        // Cargar imagen de la galería con Coil (async + cache)
        try {
            val uri = ImageUtils.getImageUri(holder.itemView.context, image.imagePath)
            holder.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            if (uri != null) {
                holder.imageView.load(uri) {
                    crossfade(true)
                    placeholder(R.drawable.ic_image)
                    error(R.drawable.ic_image)
                }
            } else {
                holder.imageView.setImageResource(R.drawable.ic_image)
                holder.imageView.scaleType = ImageView.ScaleType.CENTER
            }
        } catch (e: Exception) {
            holder.imageView.setImageResource(R.drawable.ic_image)
            holder.imageView.scaleType = ImageView.ScaleType.CENTER
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