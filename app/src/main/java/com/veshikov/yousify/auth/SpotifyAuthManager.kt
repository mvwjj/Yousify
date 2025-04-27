package com.veshikov.yousify.auth
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import android.util.Log

import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64
class SpotifyAuthManager(
    private val context: Context,
    private val onAuthSuccess: (String) -> Unit
) {
    companion object {
        const val CLIENT_ID = "77faeb227036485e940073b17936236d"
        const val REDIRECT_URI = "http://127.0.0.1:8888/callback"
        private const val SPOTIFY_AUTH_URL = "https://accounts.spotify.com/authorize"
        const val CODE_VERIFIER_KEY = "pkce_code_verifier"
        const val PREFS_NAME = "spotify_auth_prefs"
    }
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()
    private var server: LocalAuthServer? = null
    fun startAuth(activity: Activity) {
        Log.i("YousifyAuth", "START_SERVER: before LocalAuthServer.start()")
        // 1. Старуем локальный сервер
        server = LocalAuthServer(onCodeReceived = { code ->
            Log.i("YousifyAuth", "SERVER_RECEIVED_CODE: $code")
            // 4. Останавливаем сервер и передаём код наверх
            server?.stop()
            // Получаем сохраненный code_verifier для PKCE
            val codeVerifier = prefs.getString(CODE_VERIFIER_KEY, null)
            Log.i("YousifyAuth", "SERVER_CODE_VERIFIER: $codeVerifier")
            onAuthSuccess(code)
        }).also { it.start() }
        // 2. Генерируем PKCE code_verifier и сохраняем
        val codeVerifier = generateCodeVerifier()
        prefs.edit().putString(CODE_VERIFIER_KEY, codeVerifier).apply()
        Log.i("YousifyAuth", "GENERATED_CODE_VERIFIER: $codeVerifier")
        // 3. Строим URL авторизации
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val uri = Uri.parse(SPOTIFY_AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("scope", "user-read-private user-read-email playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private user-library-read user-library-modify")
            .build()
        Log.i("YousifyAuth", "AUTH_URL: $uri")
        CustomTabsIntent.Builder()
            .build()
            .launchUrl(activity, uri)
    }
    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64).also { secureRandom.nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}