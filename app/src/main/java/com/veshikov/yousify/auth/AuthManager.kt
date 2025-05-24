package com.veshikov.yousify.auth

import android.content.Context
import com.veshikov.yousify.data.api.RetrofitClient
import com.veshikov.yousify.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AuthManager {
    // CLIENT_ID и REDIRECT_URI берутся из SpotifyAuthManager.CLIENT_ID и SpotifyAuthManager.REDIRECT_URI

    suspend fun exchangeCodeForTokens(code: String, codeVerifier: String, context: Context): Boolean = withContext(Dispatchers.IO) {
        Logger.i("AuthManager: Attempting to exchange code for tokens. Code: $code, Verifier: $codeVerifier")
        try {
            val url = URL("https://accounts.spotify.com/api/token")
            val postData = "grant_type=authorization_code" +
                    "&code=" + URLEncoder.encode(code, "UTF-8") +
                    "&redirect_uri=" + URLEncoder.encode(SpotifyAuthManager.REDIRECT_URI, "UTF-8") +
                    "&client_id=" + URLEncoder.encode(SpotifyAuthManager.CLIENT_ID, "UTF-8") +
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
            Logger.i("AuthManager: Token exchange response. Code: $responseCode, Body: $responseBody")

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
        // TODO: Уведомить ViewModel или UI о необходимости обновления состояния (пользователь вышел)
        // Это можно сделать через SharedFlow в ViewModel или аналогичный механизм.
    }
}