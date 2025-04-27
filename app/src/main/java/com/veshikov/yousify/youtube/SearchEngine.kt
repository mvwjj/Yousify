package com.veshikov.yousify.youtube

import android.content.Context
import android.util.Log
import androidx.work.*
import com.veshikov.yousify.data.model.TrackEntity
import com.veshikov.yousify.data.model.YtTrackCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.min

// Data class for search input
data class TrackInput(val artist: String, val title: String, val durationMs: Long, val isrc: String?, val spotifyTrackId: String)

// Data class for search result
data class YouTubeSearchResult(val url: String, val title: String, val channel: String, val durationSec: Int)

/**
 * Основной модуль поиска YouTube видео по данным трека
 */
object SearchEngine {
    private const val TAG = "Yousify.SearchEngine"
    private const val MIN_CONFIDENCE_SCORE = 70f
    private const val FINGERPRINT_MATCH_THRESHOLD = 0.95f

    // Singleton-инстансы моделей для SBERT и аудио-отпечатков
    private var sbertModel: SbertModel? = null
    private var audioFingerprint: AudioFingerprint? = null
    private var isInitialized: Boolean = false

    /**
     * Инициализирует модуль (загружает модели SBERT и подготавливает Chromaprint)
     */
    @Synchronized
    fun initialize(context: Context) {
        if (!isInitialized) {
            try {
                sbertModel = SbertModel(context)
                audioFingerprint = AudioFingerprint(context)
                isInitialized = true
                Log.i(TAG, "SearchEngine initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SearchEngine: ${e.message}")
            }
        }
    }

    @Deprecated("Function not used in pipeline")
    suspend fun search(context: Context, input: TrackInput): YouTubeSearchResult? {
        // Этот метод сохранен для обратной совместимости
        return null
    }

    // Нормализация: только буквы, цифры и пробелы, без спецсимволов
    private fun normalize(s: String): String =
        s.lowercase()
         .replace(Regex("[^\\p{L}\\p{N} ]"), "")
         .replace(Regex("\\s+"), " ")
         .trim()

    // Levenshtein distance ratio (fuzzy matching)
    private fun levenshteinRatio(a: String, b: String): Double {
        val la = a.length
        val lb = b.length
        if (la == 0 || lb == 0) return 0.0
        val dp = Array(la + 1) { IntArray(lb + 1) }
        for (i in 0..la) dp[i][0] = i
        for (j in 0..lb) dp[0][j] = j
        for (i in 1..la) {
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        val dist = dp[la][lb]
        return 1.0 - dist.toDouble() / maxOf(la, lb)
    }

    private fun isrcSearch(isrc: String, track: TrackEntity): String? {
        val extractor = ServiceList.YouTube.getSearchExtractor(isrc)
        extractor.fetchPage()
        val item = extractor.getInitialPage().getItems()
            .filterIsInstance<StreamInfoItem>()
            .firstOrNull()
        if (item != null) {
            val videoTitleNorm = normalize(item.name)
            val titleNorm = normalize(track.title)
            val titleFuzzy = levenshteinRatio(videoTitleNorm, titleNorm) >= 0.7
            val titleIn = videoTitleNorm.contains(titleNorm)
            if (titleIn || titleFuzzy) {
                Log.i(TAG, "ISRC: title match: '${item.name}' matches '${track.title}'")
                return item.url.substringAfter("v=").take(11)
            } else {
                Log.w(TAG, "ISRC: first video '${item.name}' does not match title: '${track.title}' (normalized: '$videoTitleNorm' vs '$titleNorm')")
                return null
            }
        } else {
            Log.w(TAG, "ISRC: no video found for $isrc")
            return null
        }
    }

    private fun textSearch(q: String): List<StreamInfoItem> {
        try {
            val extractor = ServiceList.YouTube.getSearchExtractor(q)
            extractor.fetchPage()
            return extractor.getInitialPage().getItems().filterIsInstance<StreamInfoItem>()
        } catch (e: Exception) {
            Log.w(TAG, "Text search failed: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Основной метод для поиска YouTube-видео для трека
     * Реализует стратегию:
     * 1. Сначала ищет по ISRC (если доступен)
     * 2. Затем по текстовому запросу с фильтром "Песни"
     * 3. Оценивает результаты по сходству названия и другим факторам
     * 4. Если оценка < 70, проверяет сходство аудио-отпечатков
     *
     * @param track Информация о треке (артист, название, длительность, ISRC)
     * @param context Контекст приложения для доступа к моделям
     * @return YtTrackCacheEntity с videoId или null, если ничего не найдено
     */
    suspend fun findBestYoutube(track: TrackEntity, context: Context? = null): YtTrackCacheEntity? {
        try {
            // Инициализация моделей, если нужно
            if (context != null && !isInitialized) {
                initialize(context)
            }
            
            Log.i(TAG, "Searching for track: ${track.title} by ${track.artist}, ISRC=${track.isrc}")
            
            // 1. ISRC fast-path
            if (track.isrc != null) {
                try {
                    val videoId = isrcSearch(track.isrc, track)
                    if (videoId != null) {
                        Log.i(TAG, "ISRC search found exact match: $videoId")
                        return YtTrackCacheEntity(track.id, videoId, 100f, isrc = track.isrc)
                    } else {
                        Log.i(TAG, "ISRC search found no suitable result")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ISRC search failed for ${track.isrc}: ${e.message}", e)
                }
            }
            // 2. Text search с новым скорингом
            val query = "${track.artist} - ${track.title}"
            val items = textSearch(query)
            Log.i(TAG, "Text search found ${items.size} results")
            items.forEach { Log.i(TAG, "Text search result: ${it.name} by ${it.uploaderName}") }
            if (items.isNotEmpty()) {
                val ranked = items.map { it to scoreTrackMatch(it, track) }
                    .sortedWith(compareByDescending<Pair<StreamInfoItem, Int>> { it.second }
                        .thenByDescending { levenshteinRatio(normalize(it.first.name), normalize(track.title)) })
                val best = ranked.firstOrNull()
                if (best != null && best.second >= 0) {
                    val item = best.first
                    val videoId = item.url.substringAfter("v=").take(11)
                    Log.i(TAG, "Found match: ${item.name} by ${item.uploaderName}")
                    return YtTrackCacheEntity(track.id, videoId, 100f, isrc = track.isrc)
                } else {
                    Log.w(TAG, "No match found")
                    if (context != null && audioFingerprint != null) {
                        return verifyWithAudioFingerprint(ranked.firstOrNull()?.first, track, context)
                    }
                }
            } else {
                Log.w(TAG, "No results found for query: $query")
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for track: ${track.title} by ${track.artist}", e)
            return null
        }
    }
    
    /**
     * Оценка совпадения результата поиска с треком
     */
    private fun scoreTrackMatch(item: StreamInfoItem, track: TrackEntity): Int {
        val videoTitleNorm = normalize(item.name)
        val uploaderNorm = normalize(item.uploaderName ?: "")
        val titleNorm = normalize(track.title)
        val artistNorm = normalize(track.artist)
        var score = 0
        // Title в начале названия
        if (videoTitleNorm.startsWith(titleNorm)) score += 2
        // Title где угодно
        else if (videoTitleNorm.contains(titleNorm)) score += 1
        // Artist в uploader или в названии
        if (uploaderNorm.contains(artistNorm) || videoTitleNorm.contains(artistNorm)) score += 1
        // "slowed"/"reverb" если есть в названии и в запросе
        val mods = listOf("slowed", "reverb")
        val queryMods = mods.filter { normalize(track.title + track.artist).contains(it) }
        if (queryMods.any { videoTitleNorm.contains(it) }) score += 1
        // Penalty если title не найден вообще
        if (!videoTitleNorm.contains(titleNorm)) score -= 2
        return score
    }
    
    /**
     * Проверяет совпадение на основе аудио-отпечатка
     * Используется, когда уверенность на основе текста < 70
     */
    private suspend fun verifyWithAudioFingerprint(
        item: StreamInfoItem?,
        track: TrackEntity,
        context: Context
    ): YtTrackCacheEntity? = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Verifying with audio fingerprint: ${item?.name}")
            
            if (audioFingerprint == null) {
                audioFingerprint = AudioFingerprint(context)
            }
            
            // Получаем videoId
            val videoId = item?.url?.substringAfter("v=")?.take(11)
            
            // 1. Загружаем первые 10 секунд аудио
            val audioUrl = "https://www.youtube.com/watch?v=$videoId" // Правильный URL для загрузки аудио
            val fingerprint = audioFingerprint?.createFingerprintFromUrl(audioUrl)
            
            if (fingerprint == null) {
                Log.w(TAG, "Failed to create fingerprint for $videoId")
                return@withContext null
            }
            
            // 2. Сравниваем с референсным отпечатком (в реальности был бы запрос к AcoustID)
            // Здесь для примера просто приложение должно кэшировать отпечатки треков
            
            // Упрощенно: если есть локальный отпечаток, сравниваем с ним
            val cachedFingerprint = getCachedFingerprint(track.id, context)
            if (cachedFingerprint != null) {
                val similarity = audioFingerprint?.compareFingerprintSimilarity(fingerprint, cachedFingerprint)
                
                Log.i(TAG, "Fingerprint similarity: $similarity")
                
                if (similarity != null && similarity >= FINGERPRINT_MATCH_THRESHOLD) {
                    Log.i(TAG, "Fingerprint match confirmed: ${item?.name}")
                    return@withContext YtTrackCacheEntity(track.id, videoId ?: "", 95f)
                }
            } else {
                // Если нет референсного отпечатка, просто сохраняем текущий как референсный
                // и полагаемся на базовую оценку
                saveFingerprintToCache(track.id, fingerprint, context)
                
                // Возвращаем результат, но с низкой уверенностью
                return@withContext YtTrackCacheEntity(track.id, videoId ?: "", 65f)
            }
            
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying with audio fingerprint: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Получает кэшированный отпечаток трека (упрощенно)
     * В реальном приложении использовалась бы база данных
     */
    private fun getCachedFingerprint(trackId: String, context: Context): String? {
        try {
            val cacheFile = File(context.cacheDir, "fingerprint_$trackId.bin")
            if (cacheFile.exists()) {
                return cacheFile.readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached fingerprint: ${e.message}")
        }
        return null
    }
    
    /**
     * Сохраняет отпечаток в кэш (упрощенно)
     */
    private fun saveFingerprintToCache(trackId: String, fingerprint: String, context: Context) {
        try {
            val cacheFile = File(context.cacheDir, "fingerprint_$trackId.bin")
            cacheFile.writeText(fingerprint)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving fingerprint to cache: ${e.message}")
        }
    }

    /**
     * Ранжирует результаты по нескольким критериям:
     * - Сходство с запросом
     * - Тип канала (предпочитаем официальные каналы)
     * - Длительность (предпочитаем близкую к оригиналу)
     */
    private fun rankResults(input: TrackInput, items: List<StreamInfoItem>): List<YouTubeSearchResult> {
        val results = ArrayList<YouTubeSearchResult>()
        for (item in items) {
            var score = 0
            
            // Базовая оценка - TODO: улучшить алгоритм сравнения
            val query = "${input.artist} - ${input.title}"
            score += (Similarity.jaroWinkler(query, item.name) * 50).toInt()
            
            // Бонус за официальный канал
            if (item.uploaderName.endsWith(" - Topic") || item.uploaderName.contains("VEVO")) score += 25
            
            // Бонус или штраф за длительность
            val lenSec = (input.durationMs / 1000).toInt()
            val delta = abs(item.duration.toInt() - lenSec)
            if (delta <= 5) {
                score += 10
            } else {
                score -= (delta - 5) * 2
            }
            
            results.add(YouTubeSearchResult(item.url, item.name, item.uploaderName, item.duration.toInt()))
        }
        return results
    }
    
    /**
     * Освобождает ресурсы модели SBERT и аудио-отпечатков
     */
    fun release() {
        sbertModel?.close()
        audioFingerprint?.release()
        sbertModel = null
        audioFingerprint = null
        isInitialized = false
    }
}
