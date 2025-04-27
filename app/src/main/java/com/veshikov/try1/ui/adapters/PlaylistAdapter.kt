package com.veshikov.try1.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.veshikov.try1.R
import com.veshikov.try1.data.model.Playlist
import com.veshikov.try1.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private val onClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemPlaylistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemPlaylistBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.root.setOnClickListener {
                absoluteAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let {
                    onClick(getItem(it))
                }
            }
        }
        fun bind(p: Playlist) {
            try {
                // Безопасная установка имени плейлиста
                b.playlistName.text = p.name ?: "Неизвестный плейлист"
                
                // Безопасная установка описания плейлиста
                val description = p.description ?: "Треков: ${p.tracks?.total ?: 0}"
                b.playlistDescription.text = description
                
                // Безопасная обработка изображений
                val hasValidImages = p.images != null && 
                                    p.images!!.isNotEmpty() && 
                                    p.images!!.any { it.url != null && it.url!!.isNotEmpty() }
                
                if (hasValidImages) {
                    // Находим первое изображение с непустым URL
                    val imageUrl = p.images!!.firstOrNull { it.url != null && it.url!!.isNotEmpty() }?.url
                    
                    if (!imageUrl.isNullOrEmpty()) {
                        b.playlistImage.load(imageUrl) {
                            placeholder(R.drawable.ic_playlist_placeholder)
                            error(R.drawable.ic_playlist_placeholder)
                            transformations(CircleCropTransformation())
                            crossfade(true)
                            crossfade(300)
                        }
                    } else {
                        b.playlistImage.setImageResource(R.drawable.ic_playlist_placeholder)
                    }
                } else {
                    b.playlistImage.setImageResource(R.drawable.ic_playlist_placeholder)
                }
                
                // Добавляем информацию о владельце, если она доступна
                if (p.owner?.displayName != null && p.owner?.displayName?.isNotEmpty() == true) {
                    val ownerText = "Автор: ${p.owner?.displayName}"
                    if (b.playlistDescription.text.isNullOrEmpty()) {
                        b.playlistDescription.text = ownerText
                    }
                }
            } catch (e: Exception) {
                // В случае любой ошибки устанавливаем значения по умолчанию
                b.playlistName.text = "Плейлист"
                b.playlistDescription.text = ""
                b.playlistImage.setImageResource(R.drawable.ic_playlist_placeholder)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<Playlist>() {
        override fun areItemsTheSame(a: Playlist, b: Playlist): Boolean = a.id == b.id
        override fun areContentsTheSame(a: Playlist, b: Playlist): Boolean = a == b
    }
}