package com.mvwj.yousify.data

import com.mvwj.yousify.data.model.*
import com.mvwj.yousify.utils.Logger
import org.json.JSONObject
import java.net.HttpURLConnection // Ð˜Ð¡ÐŸÐ ÐÐ’Ð›Ð•ÐÐž: Ð”Ð¾Ð±Ð°Ð²Ð»ÐµÐ½ Ð¸Ð¼Ð¿Ð¾Ñ€Ñ‚
import java.net.URL
import java.io.InputStreamReader // Ð”Ð»Ñ BufferedReader

object SpotifyJsonParser {

    // parsePlaylistsJson Ð¸ parsePlaylistJson Ð¾ÑÑ‚Ð°ÑŽÑ‚ÑÑ Ð±ÐµÐ· Ð¸Ð·Ð¼ÐµÐ½ÐµÐ½Ð¸Ð¹

    fun parsePlaylistsJson(jsonString: String): List<Playlist> {
        try {
            val result = mutableListOf<Playlist>()
            val jsonObject = JSONObject(jsonString)

            if (!jsonObject.has("items")) {
                Logger.e("JSON does not contain 'items' field")
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
                    Logger.e("Error parsing playlist #$i", e)
                }
            }
            Logger.i("Successfully parsed ${result.size} playlists out of ${itemsArray.length()}")
            return result
        } catch (e: Exception) {
            Logger.e("Error parsing JSON with playlists", e)
            return emptyList()
        }
    }

    private fun parsePlaylistJson(json: JSONObject): Playlist? {
        try {
            val id = json.optString("id")
            if (id.isEmpty()) {
                Logger.e("Playlist without ID, skipping")
                return null
            }
            val name = json.optString("name", "")
            val description = json.optString("description", "")
            val uri = json.optString("uri", "")
            val href = json.optString("href", "")
            val images = mutableListOf<Image>()
            if (json.has("images") && !json.isNull("images") && json.opt("images") is org.json.JSONArray) {
                try {
                    val imagesArray = json.getJSONArray("images")
                    for (i in 0 until imagesArray.length()) {
                        val imageJson = imagesArray.getJSONObject(i)
                        val url = imageJson.optString("url")
                        if (url.isNotEmpty()) {
                            images.add(Image(url = url, height = imageJson.optInt("height"), width = imageJson.optInt("width")))
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("Error parsing playlist images array", e)
                }
            }
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
            var tracks: Tracks? = null
            if (json.has("tracks") && !json.isNull("tracks")) {
                val tracksJson = json.getJSONObject("tracks")
                tracks = Tracks(
                    href = tracksJson.optString("href", null),
                    total = if (tracksJson.has("total") && !tracksJson.isNull("total")) tracksJson.optInt("total") else null
                )
            }
            return Playlist(id, name, description, uri, href, images, owner, tracks)
        } catch (e: Exception) {
            Logger.e("Error parsing playlist", e)
            return null
        }
    }

    fun parsePlaylistTracksJson(jsonString: String): List<TrackItem> {
        try {
            val result = mutableListOf<TrackItem>()
            val jsonObject = JSONObject(jsonString)
            if (!jsonObject.has("items")) {
                Logger.e("JSON does not contain 'items' field when parsing tracks")
                return emptyList()
            }
            val itemsArray = jsonObject.getJSONArray("items")
            for (i in 0 until itemsArray.length()) {
                try {
                    val trackItemJson = itemsArray.getJSONObject(i)
                    if (!trackItemJson.has("track") || trackItemJson.isNull("track")) {
                        Logger.w("Track #$i does not contain 'track' field or it is null, skipping: ${trackItemJson.opt("track")}")
                        continue
                    }
                    val trackObject = trackItemJson.optJSONObject("track")
                    if (trackObject == null) {
                        Logger.w("'track' field for item #$i is not a JSONObject, skipping.")
                        continue
                    }
                    val trackJson = trackObject

                    val id = trackJson.optString("id", null)
                    val name = trackJson.optString("name", "")

                    if (id == null) {
                        Logger.w("Track #$i without id (possibly deleted or unavailable), skipping. Name: $name")
                        continue
                    }
                    if (name.isEmpty()) {
                        Logger.w("Track #$i (ID: $id) without name, skipping")
                        continue
                    }

                    val durationMs = trackJson.optLong("duration_ms", 0)
                    val uri = trackJson.optString("uri", "")
                    val href = trackJson.optString("href", "")

                    val artists = mutableListOf<Artist>()
                    if (trackJson.has("artists") && !trackJson.isNull("artists")) {
                        val artistsArray = trackJson.getJSONArray("artists")
                        for (j in 0 until artistsArray.length()) {
                            val artistJson = artistsArray.getJSONObject(j)
                            artists.add(Artist(artistJson.optString("id", ""), artistJson.optString("name", ""), artistJson.optString("type", ""), artistJson.optString("uri", ""), artistJson.optString("href", "")))
                        }
                    }

                    var album: Album? = null
                    if (trackJson.has("album") && !trackJson.isNull("album")) {
                        val albumJson = trackJson.getJSONObject("album")
                        val albumImages = mutableListOf<Image>()
                        if (albumJson.has("images") && !albumJson.isNull("images") && albumJson.opt("images") is org.json.JSONArray) {
                            val imagesArray = albumJson.getJSONArray("images")
                            for (j in 0 until imagesArray.length()) {
                                val imageJson = imagesArray.getJSONObject(j)
                                albumImages.add(Image(imageJson.optString("url", ""), imageJson.optInt("height", 0), imageJson.optInt("width", 0)))
                            }
                        }
                        album = Album(albumJson.optString("id", ""), albumJson.optString("name", ""), albumJson.optString("album_type", ""), emptyList(), emptyList(), ExternalUrls(albumJson.optJSONObject("external_urls")?.optString("spotify") ?: ""), albumJson.optString("href", ""), albumImages, albumJson.optString("release_date", ""), albumJson.optString("release_date_precision", ""), albumJson.optInt("total_tracks", 0), albumJson.optString("type", ""), albumJson.optString("uri", ""))
                    }

                    var externalIds: Map<String, String>? = null
                    if (trackJson.has("external_ids") && !trackJson.isNull("external_ids")) {
                        val extIdsObj = trackJson.getJSONObject("external_ids")
                        externalIds = mutableMapOf<String, String>().apply {
                            val keys = extIdsObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val value = extIdsObj.optString(key, null)
                                if (value != null) put(key, value)
                            }
                        }
                    }

                    val track = Track(id, name, album, artists, emptyList(), trackJson.optInt("disc_number", 0), durationMs, trackJson.optBoolean("explicit", false), externalIds, ExternalUrls(trackJson.optJSONObject("external_urls")?.optString("spotify") ?: ""), href, trackJson.optBoolean("is_local", false), trackJson.optInt("popularity", 0), trackJson.optString("preview_url", null), trackJson.optInt("track_number", 0), trackJson.optString("type", ""), uri)

                    val addedAt = trackItemJson.optString("added_at", "")
                    val addedBy: Owner? = if (trackItemJson.has("added_by") && !trackItemJson.isNull("added_by")) {
                        val ownerJson = trackItemJson.getJSONObject("added_by")
                        Owner(ownerJson.optString("id", ""), ownerJson.optString("display_name", ""), ownerJson.optString("href", ""), ownerJson.optString("type", ""), ownerJson.optString("uri", ""))
                    } else null

                    result.add(TrackItem(addedAt, addedBy, trackItemJson.optBoolean("is_local", false), track))
                } catch (e: Exception) {
                    Logger.e("Error parsing track item #$i", e)
                }
            }
            return result
        } catch (e: Exception) {
            Logger.e("Error parsing playlist tracks", e)
            return emptyList()
        }
    }

    fun fetchJsonAndCodeFromUrl(url: String, token: String): Pair<String?, Int> {
        var connection: HttpURLConnection? = null
        try {
            Logger.i("fetchJsonAndCodeFromUrl: requesting $url")
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            Logger.i("fetchJsonAndCodeFromUrl: responseCode=$responseCode for $url")

            val inputStream = if (responseCode in 200..299) {
                connection.inputStream // Ð˜Ð¡ÐŸÐ ÐÐ’Ð›Ð•ÐÐž: .inputStream() Ð½Ðµ Ð½ÑƒÐ¶Ð½Ð¾, ÑÑ‚Ð¾ ÑÐ²Ð¾Ð¹ÑÑ‚Ð²Ð¾
            } else {
                connection.errorStream // Ð˜Ð¡ÐŸÐ ÐÐ’Ð›Ð•ÐÐž: .errorStream() Ð½Ðµ Ð½ÑƒÐ¶Ð½Ð¾, ÑÑ‚Ð¾ ÑÐ²Ð¾Ð¹ÑÑ‚Ð²Ð¾
            }

            // Ð˜Ð¡ÐŸÐ ÐÐ’Ð›Ð•ÐÐž: Ð§Ñ‚ÐµÐ½Ð¸Ðµ Ð¿Ð¾Ñ‚Ð¾ÐºÐ° Ñ Ð¿Ð¾Ð¼Ð¾Ñ‰ÑŒÑŽ InputStreamReader Ð¸ BufferedReader
            val resultBody = inputStream?.let { stream ->
                InputStreamReader(stream).buffered().use { reader -> reader.readText() }
            }


            if (responseCode !in 200..299) {
                Logger.e("fetchJsonAndCodeFromUrl: ERROR responseCode=$responseCode for $url")
                if (responseCode == 401) {
                    Logger.e("fetchJsonAndCodeFromUrl: AUTH ERROR (token expired, invalid, or missing scopes)")
                } else if (responseCode == 429) {
                    Logger.e("fetchJsonAndCodeFromUrl: RATE LIMIT EXCEEDED")
                }
            } else {
                Logger.i("fetchJsonAndCodeFromUrl: success for $url")
            }
            // Ð˜Ð¡ÐŸÐ ÐÐ’Ð›Ð•ÐÐž: Ð›Ð¾Ð³Ð¸ÐºÐ° Ð²Ð¾Ð·Ð²Ñ€Ð°Ñ‚Ð°. Ð•ÑÐ»Ð¸ ÐºÐ¾Ð´ Ð½Ðµ 2xx, resultBody Ð¼Ð¾Ð¶ÐµÑ‚ Ð±Ñ‹Ñ‚ÑŒ Ñ‚ÐµÐ»Ð¾Ð¼ Ð¾ÑˆÐ¸Ð±ÐºÐ¸ Ð¸Ð»Ð¸ null.
            return Pair(resultBody, responseCode)

        } catch (e: Exception) {
            Logger.e("fetchJsonAndCodeFromUrl: exception for $url", e)
            return Pair(null, -1) // -1 ÐºÐ°Ðº Ð¸Ð½Ð´Ð¸ÐºÐ°Ñ‚Ð¾Ñ€ Ð¸ÑÐºÐ»ÑŽÑ‡ÐµÐ½Ð¸Ñ
        } finally {
            connection?.disconnect()
        }
    }
}
