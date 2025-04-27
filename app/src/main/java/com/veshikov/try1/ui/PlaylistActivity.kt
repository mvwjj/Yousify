package com.veshikov.try1.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.veshikov.try1.databinding.ActivityPlaylistBinding
import com.veshikov.try1.data.model.Playlist
import com.veshikov.try1.data.SpotifyApiWrapper
import com.veshikov.try1.ui.adapters.PlaylistAdapter
import com.veshikov.try1.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlaylistBinding
    private val apiWrapper = SpotifyApiWrapper.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = PlaylistAdapter { playlist ->
            startActivity(TracksActivity.newIntent(this, playlist.id, playlist.name))
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Показываем индикатор загрузки
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        
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
                        binding.progressBar.visibility = View.GONE
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
                                binding.recyclerView.visibility = View.VISIBLE
                                adapter.submitList(retryValidPlaylists)
                                binding.progressBar.visibility = View.GONE
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
                        binding.noPlaylistsText.visibility = View.VISIBLE
                    } else {
                        Logger.i("PlaylistActivity: Отображаем ${validPlaylists.size} плейлистов")
                        binding.recyclerView.visibility = View.VISIBLE
                    }
                    adapter.submitList(validPlaylists)
                    
                    // Скрываем индикатор загрузки
                    binding.progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Logger.e("PlaylistActivity: Ошибка загрузки плейлистов", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PlaylistActivity, "Ошибка загрузки плейлистов: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    companion object {
        fun newIntent(ctx: Context) = Intent(ctx, PlaylistActivity::class.java)
    }
}