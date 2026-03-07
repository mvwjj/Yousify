package com.mvwj.yousify.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mvwj.yousify.utils.Logger

object SecurePrefs {
    private const val FILE_NAME = "yousify_secure_auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_SPOTIFY_CLIENT_ID = "spotify_client_id"
    private const val KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(accessToken: String, refreshToken: String, expiresInSeconds: Long, context: Context) {
        val editor = getEncryptedPrefs(context).edit()
        editor.putString(KEY_ACCESS_TOKEN, accessToken)
        editor.putString(KEY_REFRESH_TOKEN, refreshToken)
        val MOCK_EXPIRES_AT = System.currentTimeMillis() + (expiresInSeconds * 1000) // Ð”Ð»Ñ Ð¿Ñ€Ð¸Ð¼ÐµÑ€Ð°, Ñ€ÐµÐ°Ð»ÑŒÐ½Ð¾Ðµ Ð²Ñ€ÐµÐ¼Ñ Ð¸ÑÑ‚ÐµÑ‡ÐµÐ½Ð¸Ñ
        editor.putLong(KEY_EXPIRES_AT, MOCK_EXPIRES_AT)
        editor.apply()
        Logger.i("SecurePrefs: Spotify tokens saved.")
    }

    fun saveSpotifyDeveloperCredentials(clientId: String, clientSecret: String, context: Context) {
        getEncryptedPrefs(context).edit()
            .putString(KEY_SPOTIFY_CLIENT_ID, clientId)
            .putString(KEY_SPOTIFY_CLIENT_SECRET, clientSecret)
            .apply()
        Logger.i("SecurePrefs: Spotify developer credentials saved locally.")
    }

    fun accessToken(context: Context): String? {
        val token = getEncryptedPrefs(context).getString(KEY_ACCESS_TOKEN, null)
        // Logger.d("SecurePrefs: Retrieved access token: ${token != null}")
        return token
    }

    fun refreshToken(context: Context): String? {
        val token = getEncryptedPrefs(context).getString(KEY_REFRESH_TOKEN, null)
        // Logger.d("SecurePrefs: Retrieved refresh token: ${token != null}")
        return token
    }

    fun expiresAt(context: Context): Long {
        return getEncryptedPrefs(context).getLong(KEY_EXPIRES_AT, 0L)
    }

    fun spotifyClientId(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_SPOTIFY_CLIENT_ID, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun spotifyClientSecret(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_SPOTIFY_CLIENT_SECRET, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun hasSpotifyDeveloperCredentials(context: Context): Boolean {
        return spotifyClientId(context) != null && spotifyClientSecret(context) != null
    }

    fun clear(context: Context) {
        getEncryptedPrefs(context).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
        Logger.i("SecurePrefs: Cleared Spotify tokens.")
    }
}
