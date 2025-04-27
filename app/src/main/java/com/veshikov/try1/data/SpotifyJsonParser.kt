package com.veshikov.try1.data

import com.veshikov.try1.data.model.*
import com.veshikov.try1.utils.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * Ручной парсер JSON-ответов от Spotify API
 * Используется как запасной вариант, когда стандартный парсер не справляется
 */
object SpotifyJsonParser {

    /**
     * Парсит JSON-ответ с плейлистами
     */
    fun parsePlaylistsJson(jsonString: String): List<Playlist> {
        try {
            val result = mutableListOf<Playlist>()
            val jsonObject = JSONObject(jsonString)
            
            // Проверяем, что есть поле items
            if (!jsonObject.has("items")) {
                Logger.e("JSON не содержит поле 'items'")
                return emptyList()
            }
            
            val itemsArray = jsonObject.getJSONArray("items")
            for (i in 0 until itemsArray.length()) {
                try {
                    val playlistJson = itemsArray.getJSONObject(i)
                    val playlist = parsePlaylistJson(playlistJson)
                    if (playlist != null) {
                        result.add(playlist)
                    }
                } catch (e: Exception) {
                    Logger.e("Ошибка при парсинге плейлиста #$i", e)
                    // Пропускаем этот плейлист и продолжаем
                }
            }
            
            Logger.i("Успешно распарсено ${result.size} плейлистов из ${itemsArray.length()}")
            return result
        } catch (e: Exception) {
            Logger.e("Ошибка при парсинге JSON с плейлистами", e)
            return emptyList()
        }
    }
    
    /**
     * Парсит JSON-объект одного плейлиста
     */
    private fun parsePlaylistJson(json: JSONObject): Playlist? {
        try {
            // Обязательные поля
            val id = json.optString("id")
            if (id.isEmpty()) {
                Logger.e("Плейлист без ID, пропускаем")
                return null
            }
            
            val name = json.optString("name", "")
            val description = json.optString("description", "")
            val uri = json.optString("uri", "")
            val href = json.optString("href", "")
            
            // Парсим изображения (если есть)
            val images = mutableListOf<Image>()
            if (json.has("images")) {
                if (!json.isNull("images") && json.opt("images") is org.json.JSONArray) {
                    try {
                        val imagesArray = json.getJSONArray("images")
                        for (i in 0 until imagesArray.length()) {
                            try {
                                val imageJson = imagesArray.getJSONObject(i)
                                val url = imageJson.optString("url")
                                if (url.isNotEmpty()) {
                                    val height = imageJson.optInt("height")
                                    val width = imageJson.optInt("width")
                                    images.add(Image(url = url, height = height, width = width))
                                }
                            } catch (e: Exception) {
                                Logger.e("Ошибка при парсинге изображения плейлиста", e)
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e("Ошибка при парсинге массива изображений плейлиста", e)
                    }
                } else {
                    Logger.i("Поле images равно null или не является массивом для плейлиста $id")
                    // Если images равно null или не массив, оставляем пустой список
                }
            } else {
                Logger.i("Поле images отсутствует для плейлиста $id")
                // Если поле images отсутствует, оставляем пустой список
            }
            
            // Парсим информацию о владельце
            var owner: Owner? = null
            if (json.has("owner") && !json.isNull("owner")) {
                val ownerJson = json.getJSONObject("owner")
                owner = Owner(
                    id = ownerJson.optString("id", ""),
                    displayName = ownerJson.optString("display_name", ""),
                    href = ownerJson.optString("href", ""),
                    type = ownerJson.optString("type", ""),
                    uri = ownerJson.optString("uri", "")
                )
            }
            
            // Парсим информацию о треках (tracks)
            var tracks: Tracks? = null
            if (json.has("tracks") && !json.isNull("tracks")) {
                val tracksJson = json.getJSONObject("tracks")
                tracks = Tracks(
                    href = tracksJson.optString("href", null),
                    total = if (tracksJson.has("total") && !tracksJson.isNull("total")) tracksJson.optInt("total") else null
                )
            }
            
            // Создаем объект плейлиста с учетом типов
            return Playlist(
                id = id,
                name = name,
                description = description,
                uri = uri,
                href = href,
                images = images,
                owner = owner,
                tracks = tracks
            )
        } catch (e: Exception) {
            Logger.e("Ошибка при парсинге плейлиста", e)
            return null
        }
    }
    
    /**
     * Парсит JSON-ответ с треками плейлиста
     */
    fun parsePlaylistTracksJson(jsonString: String): List<TrackItem> {
        try {
            val result = mutableListOf<TrackItem>()
            val jsonObject = JSONObject(jsonString)
            if (!jsonObject.has("items")) {
                Logger.e("JSON не содержит поле 'items'")
                return emptyList()
            }
            val itemsArray = jsonObject.getJSONArray("items")
            for (i in 0 until itemsArray.length()) {
                try {
                    val trackItemJson = itemsArray.getJSONObject(i)
                    if (!trackItemJson.has("track") || trackItemJson.isNull("track")) {
                        Logger.e("Трек #$i не содержит поле 'track' или оно равно null, пропускаем")
                        continue
                    }
                    val trackJson = trackItemJson.getJSONObject("track")
                    val id = trackJson.optString("id", "")
                    val name = trackJson.optString("name", "")
                    if (id.isEmpty() || name.isEmpty()) {
                        Logger.e("Трек #$i без id или name, пропускаем")
                        continue
                    }
                    val durationMs = trackJson.optLong("duration_ms", 0)
                    val uri = trackJson.optString("uri", "")
                    val href = trackJson.optString("href", "")
                    
                    // Парсим информацию об исполнителях
                    val artists = mutableListOf<Artist>()
                    if (trackJson.has("artists") && !trackJson.isNull("artists")) {
                        val artistsArray = trackJson.getJSONArray("artists")
                        for (j in 0 until artistsArray.length()) {
                            val artistJson = artistsArray.getJSONObject(j)
                            artists.add(
                                Artist(
                                    id = artistJson.optString("id", ""),
                                    name = artistJson.optString("name", ""),
                                    type = artistJson.optString("type", ""),
                                    uri = artistJson.optString("uri", ""),
                                    href = artistJson.optString("href", "")
                                )
                            )
                        }
                    }
                    
                    // Парсим информацию об альбоме
                    var album: Album? = null
                    if (trackJson.has("album") && !trackJson.isNull("album")) {
                        val albumJson = trackJson.getJSONObject("album")
                        val albumImages = mutableListOf<Image>()
                        if (albumJson.has("images") && !albumJson.isNull("images") && albumJson.opt("images") is org.json.JSONArray) {
                            val imagesArray = albumJson.getJSONArray("images")
                            for (j in 0 until imagesArray.length()) {
                                val imageJson = imagesArray.getJSONObject(j)
                                albumImages.add(
                                    Image(
                                        url = imageJson.optString("url", ""),
                                        height = imageJson.optInt("height", 0),
                                        width = imageJson.optInt("width", 0)
                                    )
                                )
                            }
                        }
                        album = Album(
                            id = albumJson.optString("id", ""),
                            name = albumJson.optString("name", ""),
                            albumType = albumJson.optString("album_type", ""),
                            artists = emptyList(),
                            availableMarkets = emptyList(),
                            externalUrls = ExternalUrls(albumJson.optJSONObject("external_urls")?.optString("spotify") ?: ""),
                            href = albumJson.optString("href", ""),
                            images = albumImages,
                            releaseDate = albumJson.optString("release_date", ""),
                            releaseDatePrecision = albumJson.optString("release_date_precision", ""),
                            totalTracks = albumJson.optInt("total_tracks", 0),
                            type = albumJson.optString("type", ""),
                            uri = albumJson.optString("uri", "")
                        )
                    }
                    
                    // Парсим информацию о треке
                    val track = Track(
                        id = id,
                        name = name,
                        album = album,
                        artists = artists,
                        availableMarkets = emptyList(),
                        discNumber = trackJson.optInt("disc_number", 0),
                        durationMs = durationMs,
                        explicit = trackJson.optBoolean("explicit", false),
                        externalIds = null,
                        externalUrls = ExternalUrls(trackJson.optJSONObject("external_urls")?.optString("spotify") ?: ""),
                        href = href,
                        isLocal = trackJson.optBoolean("is_local", false),
                        popularity = trackJson.optInt("popularity", 0),
                        previewUrl = trackJson.optString("preview_url", null),
                        trackNumber = trackJson.optInt("track_number", 0),
                        type = trackJson.optString("type", ""),
                        uri = uri
                    )
                    
                    // Парсим информацию о добавлении трека
                    val addedAt = trackItemJson.optString("added_at", "")
                    val addedBy: Owner? = if (trackItemJson.has("added_by") && !trackItemJson.isNull("added_by")) {
                        val ownerJson = trackItemJson.getJSONObject("added_by")
                        Owner(
                            id = ownerJson.optString("id", ""),
                            displayName = ownerJson.optString("display_name", ""),
                            href = ownerJson.optString("href", ""),
                            type = ownerJson.optString("type", ""),
                            uri = ownerJson.optString("uri", "")
                        )
                    } else null
                    
                    // Создаем объект трека
                    result.add(
                        TrackItem(
                            addedAt = addedAt,
                            addedBy = addedBy,
                            isLocal = trackItemJson.optBoolean("is_local", false),
                            track = track
                        )
                    )
                } catch (e: Exception) {
                    Logger.e("Ошибка при парсинге трека #$i", e)
                }
            }
            return result
        } catch (e: Exception) {
            Logger.e("Ошибка при парсинге треков плейлиста", e)
            return emptyList()
        }
    }
    
    /**
     * Получает JSON-строку из URL
     */
    fun fetchJsonFromUrl(url: String, token: String): String? {
        try {
            Logger.i("fetchJsonFromUrl: url=$url, token=${token.take(10)}...")
            val connection = URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val responseCode = connection.responseCode
            Logger.i("fetchJsonFromUrl: responseCode=$responseCode")
            val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val result = inputStream.bufferedReader().use { it.readText() }
            Logger.i("fetchJsonFromUrl: result=$result")
            return if (responseCode in 200..299) result else null
        } catch (e: Exception) {
            Logger.e("fetchJsonFromUrl: exception", e)
            return null
        }
    }
}