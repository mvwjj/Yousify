package com.mvwj.yousify.auth
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
        const val REDIRECT_URI = "http://127.0.0.1:8888/callback"
        private const val SPOTIFY_AUTH_URL = "https://accounts.spotify.com/authorize"
        const val CODE_VERIFIER_KEY = "pkce_code_verifier"
        const val PREFS_NAME = "spotify_auth_prefs"
    }
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()
    private var server: LocalAuthServer? = null
    fun startAuth(activity: Activity): Boolean {
        val clientId = SecurePrefs.spotifyClientId(context)
        if (clientId == null) {
            Log.w("YousifyAuth", "Spotify client ID is missing. Cannot start auth.")
            return false
        }

        // 1. Ð¡Ñ‚Ð°Ñ€ÑƒÐµÐ¼ Ð»Ð¾ÐºÐ°Ð»ÑŒÐ½Ñ‹Ð¹ ÑÐµÑ€Ð²ÐµÑ€
        server = LocalAuthServer(onCodeReceived = { code ->
            // 4. ÐžÑÑ‚Ð°Ð½Ð°Ð²Ð»Ð¸Ð²Ð°ÐµÐ¼ ÑÐµÑ€Ð²ÐµÑ€ Ð¸ Ð¿ÐµÑ€ÐµÐ´Ð°Ñ‘Ð¼ ÐºÐ¾Ð´ Ð½Ð°Ð²ÐµÑ€Ñ…
            server?.stop()
            onAuthSuccess(code)
        }).also { it.start() }
        // 2. Ð“ÐµÐ½ÐµÑ€Ð¸Ñ€ÑƒÐµÐ¼ PKCE code_verifier Ð¸ ÑÐ¾Ñ…Ñ€Ð°Ð½ÑÐµÐ¼
        val codeVerifier = generateCodeVerifier()
        prefs.edit().putString(CODE_VERIFIER_KEY, codeVerifier).apply()
        // 3. Ð¡Ñ‚Ñ€Ð¾Ð¸Ð¼ URL Ð°Ð²Ñ‚Ð¾Ñ€Ð¸Ð·Ð°Ñ†Ð¸Ð¸
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val uri = Uri.parse(SPOTIFY_AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("scope", "user-read-private user-read-email playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private user-library-read user-library-modify")
            .build()
        CustomTabsIntent.Builder()
            .build()
            .launchUrl(activity, uri)
        return true
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
