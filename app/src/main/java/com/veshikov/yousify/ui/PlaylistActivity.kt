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
    private val apiWrapper = SpotifyApiWrapper.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)
        
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        noPlaylistsText = findViewById(R.id.noPlaylistsText)

        val adapter = PlaylistAdapter { playlist ->
            startActivity(TracksActivity.newIntent(this, playlist.id, playlist.name))
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Показываем индикатор загрузки
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                Logger.i("PlaylistActivity: Начинаем загрузку плейлистов")
                
                // Получаем плейлисты с повторными попытками
                var playlists: List<Playlist>? = apiWrapper.getUserPlaylists()
                
                // Если первая попытка не удалась, пробуем еще раз
                if (playlists == null) {
                    Logger.i("PlaylistActivity: Первая попытка получения плейлистов не удалась, пробуем еще раз")
                    // Небольшая задержка перед повторной попыткой
                    kotlinx.coroutines.delay(1000)
                    playlists = apiWrapper.getUserPlaylists()
                }
                
                // Если и вторая попытка не удалась, пробуем еще раз с большей задержкой
                if (playlists == null) {
                    Logger.i("PlaylistActivity: Вторая попытка получения плейлистов не удалась, пробуем еще раз")
                    // Большая задержка перед третьей попыткой
                    kotlinx.coroutines.delay(2000)
                    playlists = apiWrapper.getUserPlaylists()
                }
                
                if (playlists == null) {
                    Logger.e("PlaylistActivity: Ошибка при получении плейлистов (null)")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PlaylistActivity, "Ошибка при получении плейлистов", Toast.LENGTH_LONG).show()
                        progressBar.visibility = View.GONE
                    }
                    return@launch
                }
                
                Logger.i("PlaylistActivity: Получено плейлистов: ${playlists.size}")
                
                // Фильтруем плейлисты с null id
                val validPlaylists = playlists.filter { it.id != null }
                if (validPlaylists.size < playlists.size) {
                    Logger.w("PlaylistActivity: Отфильтровано ${playlists.size - validPlaylists.size} плейлистов с null id")
                }
                
                // Проверяем, что у нас есть плейлисты
                if (validPlaylists.isEmpty() && playlists.isNotEmpty()) {
                    Logger.w("PlaylistActivity: Все полученные плейлисты имеют null id, пробуем еще раз")
                    // Еще одна попытка с большой задержкой
                    kotlinx.coroutines.delay(3000)
                    val retryPlaylists = apiWrapper.getUserPlaylists()
                    if (retryPlaylists != null && retryPlaylists.isNotEmpty()) {
                        val retryValidPlaylists = retryPlaylists.filter { it.id != null }
                        if (retryValidPlaylists.isNotEmpty()) {
                            Logger.i("PlaylistActivity: После повторной попытки получено ${retryValidPlaylists.size} валидных плейлистов")
                            
                            withContext(Dispatchers.Main) {
                                Logger.i("PlaylistActivity: Отображаем ${retryValidPlaylists.size} плейлистов")
                                recyclerView.visibility = View.VISIBLE
                                adapter.submitList(retryValidPlaylists)
                                progressBar.visibility = View.GONE
                            }
                            return@launch
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (validPlaylists.isEmpty()) {
                        // Мы знаем, что у пользователя есть плейлисты, поэтому сообщение изменено
                        Toast.makeText(this@PlaylistActivity, "Не удалось загрузить плейлисты. Пожалуйста, попробуйте позже.", Toast.LENGTH_LONG).show()
                        Logger.i("PlaylistActivity: Плейлисты не найдены")
                        noPlaylistsText.visibility = View.VISIBLE
                    } else {
                        Logger.i("PlaylistActivity: Отображаем ${validPlaylists.size} плейлистов")
                        recyclerView.visibility = View.VISIBLE
                    }
                    adapter.submitList(validPlaylists)
                    
                    // Скрываем индикатор загрузки
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Logger.e("PlaylistActivity: Ошибка загрузки плейлистов", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlaylistActivity, "Ошибка загрузки плейлистов: ${e.message}", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    companion object {
        fun newIntent(ctx: Context) = Intent(ctx, PlaylistActivity::class.java)
    }
}