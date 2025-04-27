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
import coil.transform.CircleCropTransformation
import com.veshikov.yousify.R
import com.veshikov.yousify.data.model.Playlist

class PlaylistAdapter(
    private val onClick: (Playlist) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.VH>(Diff()) {

    companion object {
        const val LIKED_SONGS_ID = "__liked_songs__"
    }

    fun submitPlaylistsWithLiked(playlists: List<Playlist>, liked: Playlist?) {
        val newList = if (liked != null) listOf(liked) + playlists else playlists
        submitList(newList)
    }

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
                // Специальное оформление для лайкнутых треков
                if (p.id == LIKED_SONGS_ID) {
                    itemView.setBackgroundResource(R.drawable.liked_songs_border)
                } else {
                    itemView.setBackgroundResource(0)
                }
                
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
                
                if (imageUrl != null) {
                    playlistImage.load(imageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_playlist_placeholder)
                        error(R.drawable.ic_playlist_placeholder)
                        transformations(CircleCropTransformation())
                    }
                } else {
                    playlistImage.setImageResource(R.drawable.ic_playlist_placeholder)
                }
            } catch (e: Exception) {
                // В случае любой ошибки устанавливаем значения по умолчанию
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