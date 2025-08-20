package com.livecopilot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.GalleryImage
import com.livecopilot.utils.ImageUtils
import coil.load
import android.graphics.BitmapFactory

class GalleryAdapter(
    private val images: MutableList<GalleryImage>,
    private val onItemClick: (GalleryImage) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    private var columnWidth: Int = 0
    private var selectionMode: Boolean = false
    private val selectedIds = linkedSetOf<String>()

    class GalleryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.gallery_item_image)
        val check: CheckBox = view.findViewById(R.id.gallery_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery, parent, false)
        return GalleryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        val image = images[position]

        // Calcular altura objetivo según aspect ratio si tenemos columnWidth
        if (columnWidth > 0) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(image.imagePath, opts)
                val srcW = opts.outWidth
                val srcH = opts.outHeight
                if (srcW > 0 && srcH > 0) {
                    val targetH = (srcH.toFloat() / srcW.toFloat() * columnWidth).toInt().coerceAtLeast(1)
                    val lp = holder.imageView.layoutParams
                    lp.height = targetH
                    holder.imageView.layoutParams = lp
                }
            } catch (_: Exception) { /* ignore and let Coil size it */ }
        }

        // Cargar imagen de la galería con Coil (async + cache)
        try {
            val uri = ImageUtils.getImageUri(holder.itemView.context, image.imagePath)
            holder.imageView.scaleType = ImageView.ScaleType.FIT_XY
            if (uri != null) {
                holder.imageView.load(uri) {
                    crossfade(true)
                    placeholder(R.drawable.ic_image)
                    error(R.drawable.ic_image)
                }
            } else {
                holder.imageView.setImageResource(R.drawable.ic_image)
                holder.imageView.scaleType = ImageView.ScaleType.FIT_XY
            }
        } catch (e: Exception) {
            holder.imageView.setImageResource(R.drawable.ic_image)
            holder.imageView.scaleType = ImageView.ScaleType.FIT_XY
        }
        
        // Selection UI
        holder.check.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.check.setOnCheckedChangeListener(null)
        holder.check.isChecked = selectedIds.contains(image.id)
        holder.check.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedIds.add(image.id) else selectedIds.remove(image.id)
        }

        // Click listener
        holder.itemView.setOnClickListener {
            if (selectionMode) {
                val nowChecked = !selectedIds.contains(image.id)
                if (nowChecked) selectedIds.add(image.id) else selectedIds.remove(image.id)
                notifyItemChanged(position)
            } else {
                onItemClick(image)
            }
        }
    }

    override fun getItemCount() = images.size
    
    fun updateImages(newImages: List<GalleryImage>) {
        images.clear()
        images.addAll(newImages)
        notifyDataSetChanged()
    }

    fun setColumnWidth(width: Int) {
        if (width != columnWidth && width > 0) {
            columnWidth = width
            notifyDataSetChanged()
        }
    }

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