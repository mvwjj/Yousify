package com.veshikov.yousify.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.veshikov.yousify.R
import com.veshikov.yousify.data.model.Playlist
import com.veshikov.yousify.data.SpotifyApiWrapper
import com.veshikov.yousify.ui.adapters.PlaylistAdapter
import com.veshikov.yousify.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var noPlaylistsText: TextView
    // ИСПРАВЛЕНО: apiWrapper инициализируется в onCreate, используя контекст
    private lateinit var apiWrapper: SpotifyApiWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        // ИСПРАВЛЕНО: Инициализация apiWrapper
        apiWrapper = SpotifyApiWrapper.getInstance(this)

        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        noPlaylistsText = findViewById(R.id.noPlaylistsText)

        val adapter = PlaylistAdapter { playlist ->
            startActivity(TracksActivity.newIntent(this, playlist.id, playlist.name))
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                Logger.i("PlaylistActivity: Starting playlist loading")

                var playlists: List<Playlist>? = apiWrapper.getUserPlaylists()

                if (playlists == null) {
                    Logger.i("PlaylistActivity: First attempt to get playlists failed, retrying once...")
                    kotlinx.coroutines.delay(1500) // Немного увеличил задержку
                    playlists = apiWrapper.getUserPlaylists()
                }
                // Удалена третья попытка для краткости, но можно добавить при необходимости

                if (playlists == null) {
                    Logger.e("PlaylistActivity: Error getting playlists (null after retries)")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PlaylistActivity, "Error getting playlists", Toast.LENGTH_LONG).show()
                        progressBar.visibility = View.GONE
                        noPlaylistsText.text = "Failed to load playlists. Please try again."
                        noPlaylistsText.visibility = View.VISIBLE
                    }
                    return@launch
                }

                Logger.i("PlaylistActivity: Received playlists: ${playlists.size}")

                val validPlaylists = playlists.filter { it.id != null && it.id.isNotBlank() } // Добавил isNotBlank
                if (validPlaylists.size < playlists.size) {
                    Logger.w("PlaylistActivity: Filtered out ${playlists.size - validPlaylists.size} playlists with null or blank id")
                }

                withContext(Dispatchers.Main) {
                    if (validPlaylists.isEmpty()) {
                        Toast.makeText(this@PlaylistActivity, "No playlists found or failed to load.", Toast.LENGTH_LONG).show()
                        Logger.i("PlaylistActivity: No valid playlists found to display")
                        noPlaylistsText.text = "No playlists available."
                        noPlaylistsText.visibility = View.VISIBLE
                    } else {
                        Logger.i("PlaylistActivity: Displaying ${validPlaylists.size} playlists")
                        recyclerView.visibility = View.VISIBLE
                        noPlaylistsText.visibility = View.GONE // Скрываем текст, если плейлисты есть
                    }
                    adapter.submitList(validPlaylists)
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Logger.e("PlaylistActivity: Error loading playlists", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlaylistActivity, "Error loading playlists: ${e.message}", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                    noPlaylistsText.text = "Error: ${e.message}"
                    noPlaylistsText.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object {
        fun newIntent(ctx: Context) = Intent(ctx, PlaylistActivity::class.java)
    }
}