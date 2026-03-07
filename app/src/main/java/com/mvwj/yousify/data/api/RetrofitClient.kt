package com.mvwj.yousify.data.api

import android.content.Context
import com.google.gson.GsonBuilder
import com.mvwj.yousify.auth.AuthEvents // Ð˜ÐœÐŸÐžÐ Ð¢
import com.mvwj.yousify.auth.SecurePrefs
import com.mvwj.yousify.auth.SpotifyAuthManager
import com.mvwj.yousify.utils.Logger
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val SPOTIFY_BASE_URL = "https://api.spotify.com/"
    @Volatile private var spotifyApiServiceInstance: SpotifyApiService? = null
    @Volatile private var okHttpClientInstance: OkHttpClient? = null

    private class TokenAuthenticator(private val context: Context) : Authenticator {
        override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
            Logger.i("TokenAuthenticator: Received ${response.code}. Attempting to refresh token.")

            if (response.request.url.toString().contains("api/token")) {
                Logger.w("TokenAuthenticator: Token refresh request itself failed with ${response.code}. Giving up.")
                return null
            }

            val currentRefreshToken = SecurePrefs.refreshToken(context)
            if (currentRefreshToken == null) {
                Logger.w("TokenAuthenticator: No refresh token available. Cannot refresh.")
                // Ð•ÑÐ»Ð¸ Ð½ÐµÑ‚ refresh token, Ð¸Ð½Ð¸Ñ†Ð¸Ð¸Ñ€ÑƒÐµÐ¼ Ð¿Ñ€Ð¸Ð½ÑƒÐ´Ð¸Ñ‚ÐµÐ»ÑŒÐ½Ñ‹Ð¹ Ð²Ñ‹Ñ…Ð¾Ð´, Ñ‚.Ðº. Ð²Ð¾ÑÑÑ‚Ð°Ð½Ð¾Ð²Ð¸Ñ‚ÑŒ ÑÐµÑÑÐ¸ÑŽ Ð½ÐµÐ²Ð¾Ð·Ð¼Ð¾Ð¶Ð½Ð¾
                AuthEvents.triggerForceLogoutNonSuspend() // Ð˜ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÐ¼ non-suspend Ð²ÐµÑ€ÑÐ¸ÑŽ
                return null
            }

            synchronized(this) {
                val newAccessTokenAfterSyncCheck = SecurePrefs.accessToken(context)
                if (response.request.header("Authorization") != "Bearer $newAccessTokenAfterSyncCheck") {
                    Logger.i("TokenAuthenticator: Token was already refreshed. Retrying with new token.")
                    return response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessTokenAfterSyncCheck")
                        .build()
                }

                Logger.d("TokenAuthenticator: Proceeding to refresh token synchronously.")
                val refreshedSuccessfully = refreshTokenSync(currentRefreshToken)

                return if (refreshedSuccessfully) {
                    val newAccessToken = SecurePrefs.accessToken(context)
                    Logger.i("TokenAuthenticator: Token refreshed successfully. Retrying original request.")
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newAccessToken")
                        .build()
                } else {
                    Logger.e("TokenAuthenticator: Failed to refresh token. Original request cannot be retried.")
                    // triggerForceLogoutNonSuspend() Ð·Ð´ÐµÑÑŒ ÑƒÐ¶Ðµ Ð±Ñ‹Ð» Ð²Ñ‹Ð·Ð²Ð°Ð½ Ð²Ð½ÑƒÑ‚Ñ€Ð¸ refreshTokenSync Ð¿Ñ€Ð¸ invalid_grant
                    null
                }
            }
        }

        private fun refreshTokenSync(refreshToken: String): Boolean {
            Logger.d("TokenAuthenticator: refreshTokenSync called.")
            return try {
                val clientId = SecurePrefs.spotifyClientId(context)
                if (clientId == null) {
                    Logger.e("TokenAuthenticator: Missing Spotify client ID. Cannot refresh token.")
                    return false
                }

                val url = URL("https://accounts.spotify.com/api/token")
                val postData = "grant_type=refresh_token" +
                        "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8") +
                        "&client_id=" + URLEncoder.encode(clientId, "UTF-8")

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

                if (responseCode == 200) {
                    val jsonResponse = JSONObject(responseBody)
                    val newAccessToken = jsonResponse.getString("access_token")
                    val newRefreshToken = jsonResponse.optString("refresh_token", refreshToken)
                    val expiresIn = jsonResponse.getLong("expires_in")
                    SecurePrefs.save(newAccessToken, newRefreshToken, expiresIn, context)
                    Logger.i("TokenAuthenticator: Token refreshed and saved.")
                    true
                } else {
                    Logger.e("TokenAuthenticator: Failed to refresh. Code: $responseCode, Body: $responseBody")
                    if (responseCode == 400 && responseBody.contains("invalid_grant", ignoreCase = true)) {
                        SecurePrefs.clear(context)
                        Logger.w("TokenAuthenticator: Invalid grant. Cleared tokens. User needs to re-login.")
                        AuthEvents.triggerForceLogoutNonSuspend() // Ð˜Ð—ÐœÐ•ÐÐ•ÐÐž: Ð˜ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÐ¼ non-suspend
                    }
                    false
                }
            } catch (e: IOException) {
                Logger.e("TokenAuthenticator: IOException during token refresh", e)
                false
            } catch (e: Exception) {
                Logger.e("TokenAuthenticator: Generic exception during token refresh", e)
                false
            }
        }
    }

    private class AuthHeaderInterceptor(private val context: Context) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val originalRequest = chain.request()
            if (originalRequest.url.toString().contains("accounts.spotify.com/api/token")) {
                return chain.proceed(originalRequest)
            }

            val accessToken = SecurePrefs.accessToken(context)
            if (accessToken != null) {
                val authenticatedRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                Logger.d("AuthHeaderInterceptor: Added auth header for ${originalRequest.url}")
                return chain.proceed(authenticatedRequest)
            }
            Logger.d("AuthHeaderInterceptor: No access token, proceeding without auth header for ${originalRequest.url}")
            return chain.proceed(originalRequest)
        }
    }

    private fun getOkHttpClient(context: Context): OkHttpClient {
        return okHttpClientInstance ?: synchronized(this) {
            okHttpClientInstance ?: run {
                val logging = HttpLoggingInterceptor { message -> Logger.d("OkHttp: $message") }
                logging.setLevel(HttpLoggingInterceptor.Level.NONE)

                OkHttpClient.Builder()
                    .addInterceptor(AuthHeaderInterceptor(context.applicationContext))
                    .authenticator(TokenAuthenticator(context.applicationContext))
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build().also { okHttpClientInstance = it }
            }
        }
    }

    fun getSpotifyService(context: Context): SpotifyApiService {
        return spotifyApiServiceInstance ?: synchronized(this) {
            spotifyApiServiceInstance ?: run {
                val gson = GsonBuilder().create()

                Retrofit.Builder()
                    .baseUrl(SPOTIFY_BASE_URL)
                    .client(getOkHttpClient(context))
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
                    .create(SpotifyApiService::class.java)
                    .also { spotifyApiServiceInstance = it }
            }
        }
    }

    fun clearInstance() {
        spotifyApiServiceInstance = null
        okHttpClientInstance = null
        Logger.i("RetrofitClient instances cleared.")
    }
}
