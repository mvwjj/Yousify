package com.veshikov.yousify.auth

import android.content.Context
import com.veshikov.yousify.data.SpotifyApiWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuthManager {
    const val CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID" // TODO: set your client id
    const val REDIRECT = "yousify://callback"

    suspend fun getApi(ctx: Context): SpotifyApiWrapper = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val exp = SecurePrefs.expiresAt(ctx)
        val acc = SecurePrefs.access(ctx)
        val ref = SecurePrefs.refresh(ctx)

        if (acc != null && ref != null && now < exp) {
            SpotifyApiWrapper.getInstance().initializeApiWithToken(acc)
            return@withContext SpotifyApiWrapper.getInstance()
        }
        throw IllegalStateException("No valid token – launch login UI")
    }

    suspend fun exchangeCode(code: String, verifier: String, ctx: Context) = withContext(Dispatchers.IO) {
        // TODO: Реализовать обмен code/verifier на access/refresh token через ручной http-запрос
        // Для упрощения: просто сбрасываем api
        SpotifyApiWrapper.getInstance().release()
    }

    fun logout(ctx: Context) {
        SpotifyApiWrapper.getInstance().release()
        SecurePrefs.clear(ctx)
    }
}
