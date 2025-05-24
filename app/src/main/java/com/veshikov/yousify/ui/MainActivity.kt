package com.veshikov.yousify.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme // ИЗМЕНЕНО: Material 3
import androidx.compose.material3.Surface // ИЗМЕНЕНО: Material 3
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.veshikov.yousify.auth.SecurePrefs
import com.veshikov.yousify.auth.SpotifyAuthManager
import com.veshikov.yousify.data.SpotifyApiWrapper
import com.veshikov.yousify.ui.theme.YouSifyTheme // Ваша обновленная M3 тема
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject


@androidx.media3.common.util.UnstableApi
class MainActivity : ComponentActivity() {
    private lateinit var authManager: SpotifyAuthManager
    private lateinit var apiWrapper: SpotifyApiWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiWrapper = SpotifyApiWrapper.getInstance(applicationContext)

        lifecycleScope.launch {
            try {
                val accessToken = SecurePrefs.accessToken(this@MainActivity)
                val refreshToken = SecurePrefs.refreshToken(this@MainActivity)
                if (accessToken != null) {
                    Log.i("MainActivity", "Initializing API with saved token")
                    apiWrapper.initializeApiWithToken(accessToken, refreshToken)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing API with saved token", e)
            }
        }

        setContent {
            YouSifyTheme { // Ваша обновленная M3 тема
                Surface( // Surface из androidx.compose.material3.Surface
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background // ИЗМЕНЕНО: M3 colorScheme
                ) {
                    MainScreen()
                }
            }
        }

        authManager = SpotifyAuthManager(this) { code ->
            lifecycleScope.launch {
                val prefs = getSharedPreferences(SpotifyAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
                val codeVerifier = prefs.getString(SpotifyAuthManager.CODE_VERIFIER_KEY, null)
                if (codeVerifier != null) {
                    val tokenResponse = exchangeCodeForTokenAuth(code, codeVerifier)
                    if (tokenResponse != null) {
                        val ok = apiWrapper.initializeApiWithToken(tokenResponse.accessToken, tokenResponse.refreshToken)
                        if (ok) {
                            Log.i("MainActivity", "Вы успешно авторизированы")
                            // Можно триггернуть обновление UI или синхронизацию данных здесь
                            // Например, через ViewModel, если MainScreen его использует для состояния логина
                        } else {
                            Log.e("MainActivity", "Ошибка инициализации Spotify API")
                        }
                    } else {
                        Log.e("MainActivity", "Ошибка получения accessToken")
                    }
                } else {
                    Log.e("MainActivity", "Ошибка: отсутствует code_verifier")
                }
            }
        }
    }

    data class TokenResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long)

    private suspend fun exchangeCodeForTokenAuth(code: String, codeVerifier: String): TokenResponse? = withContext(Dispatchers.IO) {
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
            conn.outputStream.use { it.write(postData.toByteArray()) }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }
            Log.i("SpotifyAuth", "Token exchange respCode=$responseCode body=$responseBody")

            if (responseCode == 200) {
                val jsonResponse = JSONObject(responseBody)
                val accessToken = jsonResponse.optString("access_token", null)
                val refreshToken = jsonResponse.optString("refresh_token", null)
                val expiresIn = jsonResponse.optLong("expires_in", 3600)

                if (accessToken != null && refreshToken != null) {
                    Log.i("SpotifyAuth", "Parsed access_token=${accessToken.takeLast(5)}, refresh_token=${refreshToken.takeLast(5)}, expires_in=$expiresIn")
                    SecurePrefs.save(accessToken, refreshToken, expiresIn, this@MainActivity)
                    TokenResponse(accessToken, refreshToken, expiresIn)
                } else {
                    Log.e("SpotifyAuth", "Access or Refresh token is null in response.")
                    null
                }
            } else {
                Log.e("SpotifyAuth", "Spotify error during token exchange: $responseCode $responseBody")
                null
            }
        } catch (e: Exception) {
            Log.e("SpotifyAuth", "Exception during token exchange: ${e.message}", e)
            null
        }
    }
}