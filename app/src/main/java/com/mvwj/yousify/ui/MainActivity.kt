package com.mvwj.yousify.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mvwj.yousify.auth.SecurePrefs
import com.mvwj.yousify.auth.SpotifyAuthManager
import com.mvwj.yousify.data.SpotifyApiWrapper
import com.mvwj.yousify.ui.theme.YouSifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@androidx.media3.common.util.UnstableApi
class MainActivity : ComponentActivity() {
    private lateinit var authManager: SpotifyAuthManager
    private lateinit var apiWrapper: SpotifyApiWrapper

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i("MainActivity", "POST_NOTIFICATIONS result=$granted")
    }

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
            YouSifyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }

        requestNotificationPermissionOnFirstLaunch()

        authManager = SpotifyAuthManager(this) { code ->
            lifecycleScope.launch {
                val prefs = getSharedPreferences(SpotifyAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
                val codeVerifier = prefs.getString(SpotifyAuthManager.CODE_VERIFIER_KEY, null)
                if (codeVerifier != null) {
                    val tokenResponse = exchangeCodeForTokenAuth(code, codeVerifier)
                    if (tokenResponse != null) {
                        val ok = apiWrapper.initializeApiWithToken(tokenResponse.accessToken, tokenResponse.refreshToken)
                        if (ok) {
                            Log.i("MainActivity", "Spotify auth completed")
                        } else {
                            Log.e("MainActivity", "Spotify API initialization failed")
                        }
                    } else {
                        Log.e("MainActivity", "Access token exchange failed")
                    }
                } else {
                    Log.e("MainActivity", "Missing code_verifier")
                }
            }
        }
    }

    data class TokenResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long)

    private fun requestNotificationPermissionOnFirstLaunch() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val prefs = getSharedPreferences("app_permissions", Context.MODE_PRIVATE)
        val requestAlreadyShown = prefs.getBoolean("notification_request_shown", false)
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            prefs.edit().putBoolean("notification_request_shown", true).apply()
            return
        }

        if (!requestAlreadyShown) {
            prefs.edit().putBoolean("notification_request_shown", true).apply()
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private suspend fun exchangeCodeForTokenAuth(code: String, codeVerifier: String): TokenResponse? = withContext(Dispatchers.IO) {
        try {
            val clientId = SecurePrefs.spotifyClientId(this@MainActivity)
            if (clientId == null) {
                Log.e("SpotifyAuth", "Spotify client ID is missing")
                return@withContext null
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
            conn.outputStream.use { it.write(postData.toByteArray()) }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }
            Log.i("SpotifyAuth", "Token exchange respCode=$responseCode")

            if (responseCode == 200) {
                val jsonResponse = JSONObject(responseBody)
                val accessToken = jsonResponse.optString("access_token", null)
                val refreshToken = jsonResponse.optString("refresh_token", null)
                val expiresIn = jsonResponse.optLong("expires_in", 3600)

                if (accessToken != null && refreshToken != null) {
                    SecurePrefs.save(accessToken, refreshToken, expiresIn, this@MainActivity)
                    TokenResponse(accessToken, refreshToken, expiresIn)
                } else {
                    Log.e("SpotifyAuth", "Access or refresh token is null in response")
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
