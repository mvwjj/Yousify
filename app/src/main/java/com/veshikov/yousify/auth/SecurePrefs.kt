package com.veshikov.yousify.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private const val FILE = "auth_secure_prefs"
    private const val KEY_ACCESS  = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRY  = "expires_at"

    private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
        ctx,
        FILE,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(access: String, refresh: String, expiresInSec: Long, ctx: Context) {
        val ts = System.currentTimeMillis() + expiresInSec * 1000
        prefs(ctx).edit().apply {
            putString(KEY_ACCESS, access)
            putString(KEY_REFRESH, refresh)
            putLong(KEY_EXPIRY, ts)
        }.apply()
    }

    fun access(ctx: Context)  = prefs(ctx).getString(KEY_ACCESS, null)
    fun refresh(ctx: Context) = prefs(ctx).getString(KEY_REFRESH, null)
    fun expiresAt(ctx: Context)= prefs(ctx).getLong(KEY_EXPIRY, 0)
    fun clear(ctx: Context)    = prefs(ctx).edit().clear().apply()
}
