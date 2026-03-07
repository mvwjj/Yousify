package com.mvwj.yousify

import android.app.Application
import com.mvwj.yousify.utils.Logger
import com.mvwj.yousify.youtube.NewPipeOkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

class SpotifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.i("SpotifyApplication initialized")

        com.mvwj.yousify.youtube.CacheEvictWorker.schedule(this)

        // Use the iOS extraction client to avoid web-client integrity checks.
        YoutubeStreamExtractor.setFetchIosClient(true)
        NewPipe.init(NewPipeOkHttpDownloader())
    }
}
