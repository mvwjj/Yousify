package com.veshikov.try1.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.veshikov.try1.databinding.ActivityMainBinding
import com.veshikov.try1.auth.SpotifyAuthManager
import com.veshikov.try1.data.SpotifyApiWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var authManager: SpotifyAuthManager
    private val apiWrapper = SpotifyApiWrapper.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = SpotifyAuthManager(this) { code ->
            lifecycleScope.launch {
                val prefs = getSharedPreferences(SpotifyAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
                val codeVerifier = prefs.getString(SpotifyAuthManager.CODE_VERIFIER_KEY, null)
                if (codeVerifier != null) {
                    // Прямой обмен code+code_verifier на access_token через Spotify API
                    val token = exchangeCodeForToken(code, codeVerifier)
                    if (token != null) {
                        val ok = apiWrapper.initializeApiWithToken(token)
                        if (ok) {
                            Toast.makeText(this@MainActivity, "Вы успешно авторизированы", Toast.LENGTH_SHORT).show()
                            startActivity(PlaylistActivity.newIntent(this@MainActivity))
                        } else {
                            Toast.makeText(this@MainActivity, "Ошибка инициализации Spotify API", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Ошибка получения accessToken", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Ошибка: отсутствует code_verifier", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnLogin.setOnClickListener {
            authManager.startAuth(this)
        }
    }

    // Прямой обмен кода на access_token через Spotify API
    private suspend fun exchangeCodeForToken(code: String, codeVerifier: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://accounts.spotify.com/api/token")
            val postData = "grant_type=authorization_code" +
                    "&code=" + java.net.URLEncoder.encode(code, "UTF-8") +
                    "&redirect_uri=" + java.net.URLEncoder.encode(SpotifyAuthManager.REDIRECT_URI, "UTF-8") +
                    "&client_id=" + java.net.URLEncoder.encode(SpotifyAuthManager.CLIENT_ID, "UTF-8") +
                    "&code_verifier=" + java.net.URLEncoder.encode(codeVerifier, "UTF-8")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(postData.toByteArray()) }
            val responseCode = conn.responseCode
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            Log.i("SpotifyAuth", "token respCode=$responseCode body=$response")
            if (responseCode == 200) {
                val token = org.json.JSONObject(response).optString("access_token", null)
                Log.i("SpotifyAuth", "parsed access_token=$token")
                token
            } else {
                Log.e("SpotifyAuth", "Spotify error: $responseCode $response")
                null
            }
        } catch (e: Exception) {
            Log.e("SpotifyAuth", "Exception: ${e.message}", e)
            null
        }
    }
}