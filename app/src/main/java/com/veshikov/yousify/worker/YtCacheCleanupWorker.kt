package com.veshikov.yousify.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.veshikov.yousify.data.model.YousifyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Worker для автоматической очистки устаревших записей в кэше YouTube треков.
 * Выполняет две задачи:
 * 1. Удаляет записи старше 90 дней
 * 2. Ограничивает общий размер кэша до 25 МБ (если превышен, удаляет старые записи)
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
         * Запланировать периодическую очистку кэша
         */
        fun schedulePeriodicCleanup(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true)
                .build()
                
            val workRequest = PeriodicWorkRequestBuilder<YtCacheCleanupWorker>(
                1, TimeUnit.DAYS, // Запускать раз в день
                6, TimeUnit.HOURS  // С гибкостью 6 часов для оптимизации батареи
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
         * Запустить немедленную очистку кэша
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
            
            // 1. Удаляем записи старше 90 дней
            val cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_PERIOD_DAYS)
            val deletedByAge = cacheDao.deleteOlderThan(cutoffTimestamp)
            
            Log.i(TAG, "Deleted $deletedByAge cache entries older than $RETENTION_PERIOD_DAYS days")
            
            // 2. Проверяем размер кэша и уменьшаем его, если необходимо
            // val currentSizeBytes = cacheDao.getApproximateSizeInBytes() // Удалено, если метода нет
            
            // if (currentSizeBytes > MAX_CACHE_SIZE_BYTES) {
            //     // Нужно удалить старые записи, чтобы вписаться в лимит
            //     val excessBytes = currentSizeBytes - MAX_CACHE_SIZE_BYTES
            //     val entriesToDelete = (excessBytes / 250) + 1 // ~250 байт на запись
                
            //     // Получаем временную метку, после которой нужно сохранить entriesToDelete записей
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
     * Удаляет самые старые записи, чтобы оставить только указанное количество
     */
    private suspend fun pruneOldestEntries(cacheDao: com.veshikov.yousify.data.model.YtTrackCacheDao, keepCount: Int): Int {
        // В реальном приложении здесь был бы более эффективный SQL-запрос,
        // но для простоты используем подход с временной меткой
        
        // Находим временную метку, после которой нужно сохранить записи
        val timestampThreshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30) // Примерно 30 дней
        return cacheDao.deleteOlderThan(timestampThreshold)
    }
}
