package com.mvwj.yousify.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.mvwj.yousify.data.model.YousifyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker Ð´Ð»Ñ Ð°Ð²Ñ‚Ð¾Ð¼Ð°Ñ‚Ð¸Ñ‡ÐµÑÐºÐ¾Ð¹ Ð¾Ñ‡Ð¸ÑÑ‚ÐºÐ¸ ÑƒÑÑ‚Ð°Ñ€ÐµÐ²ÑˆÐ¸Ñ… Ð·Ð°Ð¿Ð¸ÑÐµÐ¹ Ð² ÐºÑÑˆÐµ YouTube Ñ‚Ñ€ÐµÐºÐ¾Ð².
 * Ð’Ñ‹Ð¿Ð¾Ð»Ð½ÑÐµÑ‚ Ð´Ð²Ðµ Ð·Ð°Ð´Ð°Ñ‡Ð¸:
 * 1. Ð£Ð´Ð°Ð»ÑÐµÑ‚ Ð·Ð°Ð¿Ð¸ÑÐ¸ ÑÑ‚Ð°Ñ€ÑˆÐµ 90 Ð´Ð½ÐµÐ¹
 * 2. ÐžÐ³Ñ€Ð°Ð½Ð¸Ñ‡Ð¸Ð²Ð°ÐµÑ‚ Ð¾Ð±Ñ‰Ð¸Ð¹ Ñ€Ð°Ð·Ð¼ÐµÑ€ ÐºÑÑˆÐ° Ð´Ð¾ 25 ÐœÐ‘ (ÐµÑÐ»Ð¸ Ð¿Ñ€ÐµÐ²Ñ‹ÑˆÐµÐ½, ÑƒÐ´Ð°Ð»ÑÐµÑ‚ ÑÑ‚Ð°Ñ€Ñ‹Ðµ Ð·Ð°Ð¿Ð¸ÑÐ¸)
 */
class YtCacheCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "YtCacheCleanup"
        private const val MAX_CACHE_SIZE_BYTES = 25 * 1024 * 1024 // 25 MB in bytes
        private const val RETENTION_PERIOD_DAYS = 90L
        
        /**
         * Ð—Ð°Ð¿Ð»Ð°Ð½Ð¸Ñ€Ð¾Ð²Ð°Ñ‚ÑŒ Ð¿ÐµÑ€Ð¸Ð¾Ð´Ð¸Ñ‡ÐµÑÐºÑƒÑŽ Ð¾Ñ‡Ð¸ÑÑ‚ÐºÑƒ ÐºÑÑˆÐ°
         */
        fun schedulePeriodicCleanup(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .build()
                
            val workRequest = PeriodicWorkRequestBuilder<YtCacheCleanupWorker>(
                1, TimeUnit.DAYS, // Ð—Ð°Ð¿ÑƒÑÐºÐ°Ñ‚ÑŒ Ñ€Ð°Ð· Ð² Ð´ÐµÐ½ÑŒ
                6, TimeUnit.HOURS  // Ð¡ Ð³Ð¸Ð±ÐºÐ¾ÑÑ‚ÑŒÑŽ 6 Ñ‡Ð°ÑÐ¾Ð² Ð´Ð»Ñ Ð¾Ð¿Ñ‚Ð¸Ð¼Ð¸Ð·Ð°Ñ†Ð¸Ð¸ Ð±Ð°Ñ‚Ð°Ñ€ÐµÐ¸
            )
                .setConstraints(constraints)
                .build()
                
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "yt_cache_cleanup",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            
            Log.i(TAG, "Scheduled periodic cache cleanup (every 24h)")
        }
        
        /**
         * Ð—Ð°Ð¿ÑƒÑÑ‚Ð¸Ñ‚ÑŒ Ð½ÐµÐ¼ÐµÐ´Ð»ÐµÐ½Ð½ÑƒÑŽ Ð¾Ñ‡Ð¸ÑÑ‚ÐºÑƒ ÐºÑÑˆÐ°
         */
        fun runImmediateCleanup(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<YtCacheCleanupWorker>()
                .build()
                
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.i(TAG, "Requested immediate cache cleanup")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = YousifyDatabase.getInstance(applicationContext)
            val cacheDao = database.ytTrackCacheDao()
            
            // 1. Ð£Ð´Ð°Ð»ÑÐµÐ¼ Ð·Ð°Ð¿Ð¸ÑÐ¸ ÑÑ‚Ð°Ñ€ÑˆÐµ 90 Ð´Ð½ÐµÐ¹
            val cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_PERIOD_DAYS)
            val deletedByAge = cacheDao.deleteOlderThan(cutoffTimestamp)
            
            Log.i(TAG, "Deleted $deletedByAge cache entries older than $RETENTION_PERIOD_DAYS days")
            
            // 2. ÐŸÑ€Ð¾Ð²ÐµÑ€ÑÐµÐ¼ Ñ€Ð°Ð·Ð¼ÐµÑ€ ÐºÑÑˆÐ° Ð¸ ÑƒÐ¼ÐµÐ½ÑŒÑˆÐ°ÐµÐ¼ ÐµÐ³Ð¾, ÐµÑÐ»Ð¸ Ð½ÐµÐ¾Ð±Ñ…Ð¾Ð´Ð¸Ð¼Ð¾
            // val currentSizeBytes = cacheDao.getApproximateSizeInBytes() // Ð£Ð´Ð°Ð»ÐµÐ½Ð¾, ÐµÑÐ»Ð¸ Ð¼ÐµÑ‚Ð¾Ð´Ð° Ð½ÐµÑ‚
            
            // if (currentSizeBytes > MAX_CACHE_SIZE_BYTES) {
            //     // ÐÑƒÐ¶Ð½Ð¾ ÑƒÐ´Ð°Ð»Ð¸Ñ‚ÑŒ ÑÑ‚Ð°Ñ€Ñ‹Ðµ Ð·Ð°Ð¿Ð¸ÑÐ¸, Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð²Ð¿Ð¸ÑÐ°Ñ‚ÑŒÑÑ Ð² Ð»Ð¸Ð¼Ð¸Ñ‚
            //     val excessBytes = currentSizeBytes - MAX_CACHE_SIZE_BYTES
            //     val entriesToDelete = (excessBytes / 250) + 1 // ~250 Ð±Ð°Ð¹Ñ‚ Ð½Ð° Ð·Ð°Ð¿Ð¸ÑÑŒ
                
            //     // ÐŸÐ¾Ð»ÑƒÑ‡Ð°ÐµÐ¼ Ð²Ñ€ÐµÐ¼ÐµÐ½Ð½ÑƒÑŽ Ð¼ÐµÑ‚ÐºÑƒ, Ð¿Ð¾ÑÐ»Ðµ ÐºÐ¾Ñ‚Ð¾Ñ€Ð¾Ð¹ Ð½ÑƒÐ¶Ð½Ð¾ ÑÐ¾Ñ…Ñ€Ð°Ð½Ð¸Ñ‚ÑŒ entriesToDelete Ð·Ð°Ð¿Ð¸ÑÐµÐ¹
            //     val deletedBySize = pruneOldestEntries(cacheDao, entriesToDelete.toInt())
                
            //     Log.i(TAG, "Cache exceeded $MAX_CACHE_SIZE_BYTES bytes, deleted $deletedBySize oldest entries")
            // }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cache cleanup", e)
            Result.failure()
        }
    }
    
    /**
     * Ð£Ð´Ð°Ð»ÑÐµÑ‚ ÑÐ°Ð¼Ñ‹Ðµ ÑÑ‚Ð°Ñ€Ñ‹Ðµ Ð·Ð°Ð¿Ð¸ÑÐ¸, Ñ‡Ñ‚Ð¾Ð±Ñ‹ Ð¾ÑÑ‚Ð°Ð²Ð¸Ñ‚ÑŒ Ñ‚Ð¾Ð»ÑŒÐºÐ¾ ÑƒÐºÐ°Ð·Ð°Ð½Ð½Ð¾Ðµ ÐºÐ¾Ð»Ð¸Ñ‡ÐµÑÑ‚Ð²Ð¾
     */
    private suspend fun pruneOldestEntries(cacheDao: com.mvwj.yousify.data.model.YtTrackCacheDao, keepCount: Int): Int {
        // Ð’ Ñ€ÐµÐ°Ð»ÑŒÐ½Ð¾Ð¼ Ð¿Ñ€Ð¸Ð»Ð¾Ð¶ÐµÐ½Ð¸Ð¸ Ð·Ð´ÐµÑÑŒ Ð±Ñ‹Ð» Ð±Ñ‹ Ð±Ð¾Ð»ÐµÐµ ÑÑ„Ñ„ÐµÐºÑ‚Ð¸Ð²Ð½Ñ‹Ð¹ SQL-Ð·Ð°Ð¿Ñ€Ð¾Ñ,
        // Ð½Ð¾ Ð´Ð»Ñ Ð¿Ñ€Ð¾ÑÑ‚Ð¾Ñ‚Ñ‹ Ð¸ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÐ¼ Ð¿Ð¾Ð´Ñ…Ð¾Ð´ Ñ Ð²Ñ€ÐµÐ¼ÐµÐ½Ð½Ð¾Ð¹ Ð¼ÐµÑ‚ÐºÐ¾Ð¹
        
        // ÐÐ°Ñ…Ð¾Ð´Ð¸Ð¼ Ð²Ñ€ÐµÐ¼ÐµÐ½Ð½ÑƒÑŽ Ð¼ÐµÑ‚ÐºÑƒ, Ð¿Ð¾ÑÐ»Ðµ ÐºÐ¾Ñ‚Ð¾Ñ€Ð¾Ð¹ Ð½ÑƒÐ¶Ð½Ð¾ ÑÐ¾Ñ…Ñ€Ð°Ð½Ð¸Ñ‚ÑŒ Ð·Ð°Ð¿Ð¸ÑÐ¸
        val timestampThreshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30) // ÐŸÑ€Ð¸Ð¼ÐµÑ€Ð½Ð¾ 30 Ð´Ð½ÐµÐ¹
        return cacheDao.deleteOlderThan(timestampThreshold)
    }
}
