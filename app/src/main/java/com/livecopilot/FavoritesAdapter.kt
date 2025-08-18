package com.livecopilot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.livecopilot.data.Favorite
import com.livecopilot.data.FavoriteType

class FavoritesAdapter(
    private val items: MutableList<Favorite>,
    private val onClick: (Favorite) -> Unit,
    private val onLongClick: (Favorite) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.FavViewHolder>() {

    class FavViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.fav_icon)
        val name: TextView = view.findViewById(R.id.fav_name)
        val type: TextView = view.findViewById(R.id.fav_type)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite, parent, false)
        return FavViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavViewHolder, position: Int) {
        val fav = items[position]
        holder.name.text = fav.name
        holder.type.text = when (fav.type) {
            FavoriteType.LINK -> "Enlace"
            FavoriteType.PDF -> "PDF"
            FavoriteType.IMAGE -> "Imagen"
            FavoriteType.TEXT -> "Texto"
        }
        // Icono por tipo (usando drawables existentes)
        val iconRes = when (fav.type) {
            FavoriteType.LINK -> R.drawable.ic_shortcut
            FavoriteType.PDF -> R.drawable.ic_box
            FavoriteType.IMAGE -> R.drawable.ic_image
            FavoriteType.TEXT -> R.drawable.ic_save
        }
        holder.icon.setImageResource(iconRes)

        holder.itemView.setOnClickListener { onClick(fav) }
        holder.itemView.setOnLongClickListener {
            onLongClick(fav)
            true
        }
    }

    override fun getItemCount() = items.size

    fun setData(newItems: List<Favorite>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
