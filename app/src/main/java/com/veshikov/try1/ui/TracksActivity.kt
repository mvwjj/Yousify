package com.veshikov.try1.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.veshikov.try1.databinding.ActivityTracksBinding
import com.veshikov.try1.ui.adapters.TrackAdapter
import com.veshikov.try1.data.SpotifyApiWrapper
import com.veshikov.try1.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TracksActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTracksBinding
    private val apiWrapper = SpotifyApiWrapper.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTracksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val playlistId = intent.getStringExtra(EXTRA_ID)
        val playlistName = intent.getStringExtra(EXTRA_NAME)
        
        // Устанавливаем заголовок с названием плейлиста, если оно доступно
        playlistName?.let {
            title = it
        } ?: run {
            title = "Треки плейлиста"
        }
        
        val adapter = TrackAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Показываем индикатор загрузки
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.noTracksText.visibility = View.GONE
        
        playlistId?.let { loadTracks(it, adapter) }
    }

    private fun loadTracks(playlistId: String, adapter: TrackAdapter) {
        lifecycleScope.launch {
            try {
                Logger.i("TracksActivity: Начинаем загрузку треков для плейлиста $playlistId")
                val tracks = apiWrapper.getPlaylistTracks(playlistId)
                if (tracks == null) {
                    Logger.e("TracksActivity: Ошибка при получении треков (null)")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TracksActivity, "Ошибка при получении треков", Toast.LENGTH_LONG).show()
                        binding.progressBar.visibility = View.GONE
                    }
                    return@launch
                }
                Logger.i("TracksActivity: Получено треков: ${tracks.size}")
                // tracks уже List<TrackItem>
                val validTracks = tracks.filter { trackItem ->
                    val track = trackItem.track
                    track != null && !track.id.isNullOrEmpty() && !track.name.isNullOrEmpty()
                }
                if (validTracks.size < tracks.size) {
                    Logger.w("TracksActivity: Отфильтровано "+(tracks.size - validTracks.size)+" треков с null или пустыми полями")
                    tracks.forEachIndexed { index, trackItem ->
                        val track = trackItem.track
                        if (track == null) {
                            Logger.w("TracksActivity: Трек #$index имеет null track")
                        } else if (track.id.isNullOrEmpty()) {
                            Logger.w("TracksActivity: Трек #$index имеет пустой id: $track")
                        } else if (track.name.isNullOrEmpty()) {
                            Logger.w("TracksActivity: Трек #$index имеет пустое name: $track")
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    if (validTracks.isEmpty()) {
                        Toast.makeText(this@TracksActivity, "В этом плейлисте нет треков", Toast.LENGTH_LONG).show()
                        Logger.i("TracksActivity: Треки не найдены")
                        binding.noTracksText.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                    } else {
                        Logger.i("TracksActivity: Отображаем ${validTracks.size} треков")
                        binding.recyclerView.visibility = View.VISIBLE
                        binding.noTracksText.visibility = View.GONE
                        adapter.submitList(validTracks ?: listOf())
                        binding.progressBar.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Logger.e("TracksActivity: Ошибка при загрузке треков", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TracksActivity, "Ошибка при загрузке треков", Toast.LENGTH_LONG).show()
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }
    
    companion object {
        private const val EXTRA_ID = "playlist_id"
        private const val EXTRA_NAME = "playlist_name"
        
        fun newIntent(ctx: Context, id: String, name: String? = null): Intent {
            return Intent(ctx, TracksActivity::class.java)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_NAME, name)
        }
    }
}