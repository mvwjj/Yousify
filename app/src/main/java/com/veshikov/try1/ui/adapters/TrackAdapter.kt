package com.veshikov.try1.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.veshikov.try1.R
import com.veshikov.try1.data.model.Track
import com.veshikov.try1.data.model.TrackItem
import com.veshikov.try1.databinding.ItemTrackBinding

class TrackAdapter : ListAdapter<TrackItem, TrackAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemTrackBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemTrackBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(trackItem: TrackItem) {
            val track = trackItem.track
            b.trackName.text = track?.name ?: "Неизвестный трек"

            val artistsText = track?.artists?.joinToString(", ") { artist ->
                artist.name ?: "Неизвестный исполнитель"
            } ?: "Неизвестный исполнитель"
            b.artistName.text = artistsText

            var albumImageUrl: String? = null
            val images = track?.album?.images
            if (!images.isNullOrEmpty()) {
                albumImageUrl = images.firstOrNull()?.url
            }
            if (albumImageUrl != null) {
                b.trackImage.load(albumImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_track_placeholder)
                    error(R.drawable.ic_track_placeholder)
                    transformations(CircleCropTransformation())
                }
            } else {
                b.trackImage.setImageResource(R.drawable.ic_track_placeholder)
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