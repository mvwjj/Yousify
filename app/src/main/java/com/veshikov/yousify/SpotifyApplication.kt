package com.veshikov.yousify

import android.app.Application
import com.veshikov.yousify.utils.Logger
import com.veshikov.yousify.youtube.NewPipeOkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
// import java.io.File // ИСПРАВЛЕНО: Не используется

class SpotifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.i("SpotifyApplication initialized")

        // ИСПРАВЛЕНО: Удалена установка свойства org.sqlite.tmpdir и связанные операции с File
        // val sqliteTempDir = File("C:/Users/aleksandr/AndroidStudioProjects/Youify/build/tmp/sqlite")
        // if (!sqliteTempDir.exists()) {
        //     sqliteTempDir.mkdirs()
        // }
        // System.setProperty("org.sqlite.tmpdir", sqliteTempDir.absolutePath)
        // Logger.i("SQLite temp dir: ${sqliteTempDir.absolutePath}, exists: ${sqliteTempDir.exists()}, writable: ${sqliteTempDir.canWrite()}")

        // Schedule YouTube search cache eviction
        com.veshikov.yousify.youtube.CacheEvictWorker.schedule(this)
        // Initialize NewPipe Extractor (fix NullPointerException for downloader)
        NewPipe.init(NewPipeOkHttpDownloader())
    }
}