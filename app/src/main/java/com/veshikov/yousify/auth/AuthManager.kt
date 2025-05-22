package com.veshikov.yousify.auth

import android.content.Context
import com.veshikov.yousify.data.SpotifyApiWrapper
import com.veshikov.yousify.utils.Logger // Предполагая, что Logger используется
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AuthManager {
    const val CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID" // TODO: set your client id
    const val REDIRECT = "yousify://callback"

    suspend fun getApi(ctx: Context): SpotifyApiWrapper? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val exp = SecurePrefs.expiresAt(ctx)
        val acc = SecurePrefs.accessToken(ctx)
        val ref = SecurePrefs.refreshToken(ctx)

        val apiWrapperInstance = SpotifyApiWrapper.getInstance(ctx)

        // Если токен действителен, используем его
        if (acc != null && now < exp) {
            apiWrapperInstance.initializeApiWithToken(acc, ref)
            return@withContext apiWrapperInstance
        }

        // Если есть refresh_token, пробуем обновить токен
        if (ref != null) {
            try {
                val url = URL("https://accounts.spotify.com/api/token")
                val postData = "grant_type=refresh_token" +
                        "&refresh_token=" + URLEncoder.encode(ref, "UTF-8") +
                        "&client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(postData.toByteArray()) }
                val responseCode = conn.responseCode
                val responseBody = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error stream"
                }

                if (responseCode == 200) {
                    val jsonResponse = JSONObject(responseBody)
                    val newAccessToken = jsonResponse.optString("access_token", null)
                    // Spotify может вернуть новый refresh_token, а может и не вернуть. Если вернул - сохраняем.
                    val newRefreshToken = jsonResponse.optString("refresh_token", ref) // Используем старый, если новый не пришел
                    val expiresIn = jsonResponse.optLong("expires_in", 3600)

                    if (newAccessToken != null) {
                        // Сохраняем новые токены
                        SecurePrefs.save(newAccessToken, newRefreshToken, expiresIn, ctx)

                        // Инициализируем API с новым токеном
                        apiWrapperInstance.initializeApiWithToken(newAccessToken, newRefreshToken)
                        return@withContext apiWrapperInstance
                    } else {
                        Logger.e("AuthManager: New access token is null after refresh")
                    }
                } else {
                    Logger.e("AuthManager: Failed to refresh token. Code: $responseCode, Body: $responseBody")
                    if (responseCode == 400 || responseCode == 401) { // Bad request or Unauthorized (e.g. invalid refresh token)
                        SecurePrefs.clear(ctx) // Очищаем старые токены
                        apiWrapperInstance.release() // Сбрасываем состояние в apiWrapper
                        // Возможно, нужно уведомить UI о необходимости повторного входа
                    }
                }
            } catch (e: Exception) {
                Logger.e("AuthManager: Exception during token refresh", e)
                // Ошибка обновления токена, продолжаем к исключению или возвращаем null
                apiWrapperInstance.release()
                return@withContext null
            }
        }
        // Если нет валидного access token и не удалось обновить через refresh token
        Logger.w("AuthManager: No valid token and refresh failed or not possible.")
        apiWrapperInstance.release() // Убедимся, что wrapper сброшен
        return@withContext null // Возвращаем null, если не удалось получить/обновить токен
        // throw IllegalStateException("No valid token – launch login UI") // Или бросаем исключение, если это предпочтительнее
    }

    suspend fun exchangeCode(code: String, verifier: String, ctx: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://accounts.spotify.com/api/token")
            val postData = "grant_type=authorization_code" +
                    "&code=" + URLEncoder.encode(code, "UTF-8") +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT, "UTF-8") +
                    "&client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") +
                    "&code_verifier=" + URLEncoder.encode(verifier, "UTF-8")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(postData.toByteArray()) }
            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error stream"
            }

            if (responseCode == 200) {
                val jsonResponse = JSONObject(responseBody)
                val accessToken = jsonResponse.optString("access_token", null)
                val refreshToken = jsonResponse.optString("refresh_token", null)
                val expiresIn = jsonResponse.optLong("expires_in", 3600)

                if (accessToken != null && refreshToken != null) {
                    SecurePrefs.save(accessToken, refreshToken, expiresIn, ctx)
                    SpotifyApiWrapper.getInstance(ctx).initializeApiWithToken(accessToken, refreshToken)
                    return@withContext true
                } else {
                    Logger.e("AuthManager: Access or Refresh token is null in exchangeCode response.")
                }
            } else {
                Logger.e("AuthManager: Spotify error during code exchange: $responseCode $responseBody")
            }

            SpotifyApiWrapper.getInstance(ctx).release()
            return@withContext false
        } catch (e: Exception) {
            Logger.e("AuthManager: Exception during code exchange", e)
            SpotifyApiWrapper.getInstance(ctx).release()
            return@withContext false
        }
    }

    fun logout(ctx: Context) {
        SpotifyApiWrapper.getInstance(ctx).release()
        SecurePrefs.clear(ctx)
        Logger.i("AuthManager: User logged out, tokens cleared.")
    }
}