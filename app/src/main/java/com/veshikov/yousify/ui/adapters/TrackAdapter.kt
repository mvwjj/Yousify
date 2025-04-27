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
import com.veshikov.yousify.data.model.TrackItem

class TrackAdapter(
    private val onClick: ((TrackItem) -> Unit)? = null
) : ListAdapter<TrackItem, TrackAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val trackName: TextView = itemView.findViewById(R.id.trackName)
        private val artistName: TextView = itemView.findViewById(R.id.artistName)
        private val trackImage: ImageView = itemView.findViewById(R.id.trackImage)
        private var currentItem: TrackItem? = null
        init {
            itemView.setOnClickListener {
                currentItem?.let { onClick?.invoke(it) }
            }
        }
        fun bind(trackItem: TrackItem) {
            currentItem = trackItem
            val track = trackItem.track
            trackName.text = track?.name ?: "Неизвестный трек"

            val artistsText = track?.artists?.joinToString(", ") { artist ->
                artist.name ?: "Неизвестный исполнитель"
            } ?: "Неизвестный исполнитель"
            artistName.text = artistsText

            var albumImageUrl: String? = null
            val images = track?.album?.images
            if (!images.isNullOrEmpty()) {
                albumImageUrl = images.firstOrNull()?.url
            }
            android.util.Log.i("Yousify", "[TrackAdapter] albumImageUrl=$albumImageUrl for track=${track?.name}")
            if (albumImageUrl != null) {
                trackImage.load(albumImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_track_placeholder)
                    error(R.drawable.ic_track_placeholder)
                    transformations(CircleCropTransformation())
                }
            } else {
                trackImage.setImageResource(R.drawable.ic_track_placeholder)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<TrackItem>() {
        override fun areItemsTheSame(a: TrackItem, b: TrackItem): Boolean {
            return a.track?.id == b.track?.id
        }
        override fun areContentsTheSame(a: TrackItem, b: TrackItem): Boolean = a == b
    }
}