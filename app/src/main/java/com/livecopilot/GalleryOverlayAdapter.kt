package com.livecopilot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.GalleryImage
import com.livecopilot.utils.ImageUtils

class GalleryOverlayAdapter(
    private val images: MutableList<GalleryImage>,
    private val onImageClick: (GalleryImage) -> Unit
) : RecyclerView.Adapter<GalleryOverlayAdapter.GalleryOverlayViewHolder>() {

    class GalleryOverlayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.gallery_modal_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryOverlayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_modal, parent, false)
        return GalleryOverlayViewHolder(view)
    }

    override fun onBindViewHolder(holder: GalleryOverlayViewHolder, position: Int) {
        val image = images[position]
        
        // Cargar imagen
        try {
            val uri = ImageUtils.getImageUri(holder.itemView.context, image.imagePath)
            if (uri != null) {
                holder.imageView.setImageURI(uri)
                // La vista usa adjustViewBounds y fitCenter desde XML para conservar aspect ratio
                holder.imageView.scaleType = ImageView.ScaleType.FIT_CENTER
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
            onImageClick(image)
        }
    }

    override fun getItemCount() = images.size
    
    fun updateImages(newImages: List<GalleryImage>) {
        images.clear()
        images.addAll(newImages)
        notifyDataSetChanged()
    }
}