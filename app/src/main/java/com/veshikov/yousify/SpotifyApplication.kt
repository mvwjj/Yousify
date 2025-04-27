package com.veshikov.yousify

import android.app.Application
import com.veshikov.yousify.utils.Logger
import com.veshikov.yousify.youtube.NewPipeOkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe

class SpotifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.i("SpotifyApplication initialized")
        // Schedule YouTube search cache eviction
        com.veshikov.yousify.youtube.CacheEvictWorker.schedule(this)
        // Initialize NewPipe Extractor (fix NullPointerException for downloader)
        NewPipe.init(NewPipeOkHttpDownloader())
    }
}