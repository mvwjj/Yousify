package com.mvwj.yousify.auth

import android.content.Context
import com.mvwj.yousify.data.api.RetrofitClient
import com.mvwj.yousify.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AuthManager {
    suspend fun exchangeCodeForTokens(code: String, codeVerifier: String, context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val clientId = SecurePrefs.spotifyClientId(context.applicationContext)
            if (clientId == null) {
                Logger.e("AuthManager: Missing Spotify client ID.")
                return@withContext false
            }

            val url = URL("https://accounts.spotify.com/api/token")
            val postData = "grant_type=authorization_code" +
                    "&code=" + URLEncoder.encode(code, "UTF-8") +
                    "&redirect_uri=" + URLEncoder.encode(SpotifyAuthManager.REDIRECT_URI, "UTF-8") +
                    "&client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                    "&code_verifier=" + URLEncoder.encode(codeVerifier, "UTF-8")

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            conn.outputStream.use { it.write(postData.toByteArray()) }
            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error stream"
            }
            Logger.i("AuthManager: Token exchange response code=$responseCode")

            if (responseCode == 200) {
                val jsonResponse = JSONObject(responseBody)
                val accessToken = jsonResponse.optString("access_token", null)
                val refreshToken = jsonResponse.optString("refresh_token", null)
                val expiresIn = jsonResponse.optLong("expires_in", 3600)

                if (accessToken != null && refreshToken != null) {
                    SecurePrefs.save(accessToken, refreshToken, expiresIn, context.applicationContext)
                    Logger.i("AuthManager: Tokens exchanged and saved successfully.")
                    return@withContext true
                } else {
                    Logger.e("AuthManager: Access or Refresh token is null in exchangeCode response.")
                    return@withContext false
                }
            } else {
                Logger.e("AuthManager: Spotify error during code exchange: $responseCode $responseBody")
                return@withContext false
            }
        } catch (e: Exception) {
            Logger.e("AuthManager: Exception during code exchange", e)
            return@withContext false
        }
    }

    fun logout(context: Context) {
        SecurePrefs.clear(context.applicationContext)
        RetrofitClient.clearInstance()
        Logger.i("AuthManager: User logged out, tokens cleared, Retrofit client reset.")
        // TODO: Ð£Ð²ÐµÐ´Ð¾Ð¼Ð¸Ñ‚ÑŒ ViewModel Ð¸Ð»Ð¸ UI Ð¾ Ð½ÐµÐ¾Ð±Ñ…Ð¾Ð´Ð¸Ð¼Ð¾ÑÑ‚Ð¸ Ð¾Ð±Ð½Ð¾Ð²Ð»ÐµÐ½Ð¸Ñ ÑÐ¾ÑÑ‚Ð¾ÑÐ½Ð¸Ñ (Ð¿Ð¾Ð»ÑŒÐ·Ð¾Ð²Ð°Ñ‚ÐµÐ»ÑŒ Ð²Ñ‹ÑˆÐµÐ»)
        // Ð­Ñ‚Ð¾ Ð¼Ð¾Ð¶Ð½Ð¾ ÑÐ´ÐµÐ»Ð°Ñ‚ÑŒ Ñ‡ÐµÑ€ÐµÐ· SharedFlow Ð² ViewModel Ð¸Ð»Ð¸ Ð°Ð½Ð°Ð»Ð¾Ð³Ð¸Ñ‡Ð½Ñ‹Ð¹ Ð¼ÐµÑ…Ð°Ð½Ð¸Ð·Ð¼.
    }
}
