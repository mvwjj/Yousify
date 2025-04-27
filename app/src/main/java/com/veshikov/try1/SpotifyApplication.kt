package com.veshikov.try1

import android.app.Application
import com.veshikov.try1.utils.Logger

class SpotifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.i("SpotifyApplication initialized")
    }
}