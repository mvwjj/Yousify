package com.veshikov.yousify.youtube

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class CacheEvictWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = SearchCacheDatabase.getInstance(applicationContext)
        val expiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)
        db.searchCacheDao().deleteOlderThan(expiry)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = androidx.work.PeriodicWorkRequestBuilder<CacheEvictWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "youtube_cache_evict",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
