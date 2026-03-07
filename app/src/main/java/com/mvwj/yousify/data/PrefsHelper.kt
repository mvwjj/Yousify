package com.mvwj.yousify.data

import android.content.Context
import android.content.SharedPreferences

object PrefsHelper {
    private const val PREFS_NAME = "yousify_prefs"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // REMOVE ALL TOKEN-RELATED METHODS
    // This file is now obsolete and can be deleted if not used elsewhere.
}
