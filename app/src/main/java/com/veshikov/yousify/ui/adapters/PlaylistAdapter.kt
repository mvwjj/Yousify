package com.veshikov.yousify.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.bumptech.glide.Glide
import com.veshikov.yousify.R
import com.veshikov.yousify.data.model.Playlist

class PlaylistAdapter(
    private val onClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val playlistName: TextView = itemView.findViewById(R.id.playlistName)
        private val playlistDescription: TextView = itemView.findViewById(R.id.playlistDescription)
        private val playlistImage: ImageView = itemView.findViewById(R.id.playlistImage)
        
        init {
            itemView.setOnClickListener {
                absoluteAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let {
                    onClick(getItem(it))
                }
            }
        }
        fun bind(p: Playlist) {
            try {
                // Безопасная установка имени плейлиста
                playlistName.text = p.name ?: "Неизвестный плейлист"
                
                // Безопасная установка описания плейлиста
                val description = p.description ?: "Треков: ${p.tracks?.total ?: 0}"
                playlistDescription.text = description
                
                // Добавляем информацию о владельце, если она доступна
                if (p.owner?.displayName != null && p.owner?.displayName?.isNotEmpty() == true) {
                    val ownerText = "Автор: ${p.owner?.displayName}"
                    if (playlistDescription.text.isNullOrEmpty()) {
                        playlistDescription.text = ownerText
                    }
                }
                
                var imageUrl: String? = null
                if (p.images != null && p.images.isNotEmpty()) {
                    imageUrl = p.images.firstOrNull()?.url
                }
                android.util.Log.i("Yousify", "[PlaylistAdapter] imageUrl=$imageUrl for playlist=${p.name}")
                
                if (!imageUrl.isNullOrEmpty()) {
                    // Coil автоматически поддерживает JPEG/PNG/WebP, SVG требует coil-svg
                    playlistImage.load(imageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_playlist_placeholder)
                        error(R.drawable.ic_playlist_placeholder)
                        listener(
                            onError = { _, _ ->
                                android.util.Log.w("Yousify", "Coil failed for $imageUrl, fallback to Glide")
                                Glide.with(itemView.context)
                                    .load(imageUrl)
                                    .placeholder(R.drawable.ic_playlist_placeholder)
                                    .error(R.drawable.ic_playlist_placeholder)
                                    .into(playlistImage)
                                true
                            }
                        )
                    }
                } else {
                    android.util.Log.w("Yousify", "[PlaylistAdapter] imageUrl is null or empty for playlist=${p.name}")
                    playlistImage.setImageResource(R.drawable.ic_playlist_placeholder)
                }
            } catch (e: Exception) {
                android.util.Log.e("Yousify", "Ошибка загрузки плейлиста: ${e.message}", e)
                playlistName.text = "Плейлист"
                playlistDescription.text = ""
                playlistImage.setImageResource(R.drawable.ic_playlist_placeholder)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(a: Playlist, b: Playlist): Boolean = a.id == b.id
        override fun areContentsTheSame(a: Playlist, b: Playlist): Boolean = a == b
    }
}