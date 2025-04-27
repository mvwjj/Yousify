package com.veshikov.try1.data.api

import com.veshikov.try1.utils.Logger
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Клиент Retrofit для работы с API Spotify
 */
object RetrofitClient {
    
    /**
     * Создает и возвращает экземпляр SpotifyService
     */
    fun getSpotifyService(): SpotifyService {
        val retrofit = Retrofit.Builder()
            .baseUrl(SpotifyService.BASE_URL)
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        return retrofit.create(SpotifyService::class.java)
    }
    
    /**
     * Создает настроенный OkHttpClient с логированием и таймаутами
     */
    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Logger.d("OkHttp: $message")
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }
}