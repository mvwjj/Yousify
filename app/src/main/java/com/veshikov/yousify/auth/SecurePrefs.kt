package com.veshikov.yousify.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.veshikov.yousify.utils.Logger

object SecurePrefs {
    private const val FILE_NAME = "yousify_secure_auth_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"

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
        val MOCK_EXPIRES_AT = System.currentTimeMillis() + (expiresInSeconds * 1000) // Для примера, реальное время истечения
        editor.putLong(KEY_EXPIRES_AT, MOCK_EXPIRES_AT)
        editor.apply()
        Logger.i("SecurePrefs: Tokens saved. Access token ends with ${accessToken.takeLast(5)}, Refresh token ends with ${refreshToken.takeLast(5)}")
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

    fun clear(context: Context) {
        getEncryptedPrefs(context).edit().clear().apply()
        Logger.i("SecurePrefs: Cleared all tokens.")
    }
}