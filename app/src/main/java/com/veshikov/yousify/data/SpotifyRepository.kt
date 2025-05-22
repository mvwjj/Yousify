package com.veshikov.yousify.data // Убедитесь, что это правильный пакет

import android.content.Context // ИСПРАВЛЕНО: Добавлен импорт Context
import com.veshikov.yousify.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.veshikov.yousify.utils.Logger

// ИСПРАВЛЕНО: SpotifyRepository теперь принимает Context
class SpotifyRepository(private val context: Context) {
    // ИСПРАВЛЕНО: SpotifyApiWrapper.getInstance() теперь принимает Context
    private val apiWrapper = SpotifyApiWrapper.getInstance(context)

    fun getUserPlaylists(): Flow<List<Playlist>> = flow {
        try {
            // apiWrapper.getUserPlaylists() может вернуть null
            val list = apiWrapper.getUserPlaylists() ?: emptyList()
            emit(list)
        } catch (e: Exception) {
            Logger.e("Repo getUserPlaylists", e)
            // throw e // Можно не перебрасывать, а emit(emptyList()) или специальное состояние ошибки
            emit(emptyList()) // Пример: эмитим пустой список при ошибке
        }
    }

    fun getPlaylistTracks(id: String): Flow<List<TrackItem>> = flow {
        try {
            // apiWrapper.getPlaylistTracks(id) может вернуть null
            val list = apiWrapper.getPlaylistTracks(id) ?: emptyList()
            emit(list)
        } catch (e: Exception) {
            Logger.e("Repo getPlaylistTracks", e)
            // throw e
            emit(emptyList()) // Пример: эмитим пустой список при ошибке
        }
    }
}