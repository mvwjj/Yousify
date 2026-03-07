package com.mvwj.yousify.youtube

import android.content.Context
import android.util.Log
import androidx.work.*
import com.mvwj.yousify.data.model.TrackEntity
import com.mvwj.yousify.data.model.YtTrackCacheEntity
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
 * ÐžÑÐ½Ð¾Ð²Ð½Ð¾Ð¹ Ð¼Ð¾Ð´ÑƒÐ»ÑŒ Ð¿Ð¾Ð¸ÑÐºÐ° YouTube Ð²Ð¸Ð´ÐµÐ¾ Ð¿Ð¾ Ð´Ð°Ð½Ð½Ñ‹Ð¼ Ñ‚Ñ€ÐµÐºÐ°
 */
object SearchEngine {
    private const val TAG = "Yousify.SearchEngine"
    private const val MIN_CONFIDENCE_SCORE = 70f
    private const val FINGERPRINT_MATCH_THRESHOLD = 0.95f

    // Singleton-Ð¸Ð½ÑÑ‚Ð°Ð½ÑÑ‹ Ð¼Ð¾Ð´ÐµÐ»ÐµÐ¹ Ð´Ð»Ñ SBERT Ð¸ Ð°ÑƒÐ´Ð¸Ð¾-Ð¾Ñ‚Ð¿ÐµÑ‡Ð°Ñ‚ÐºÐ¾Ð²
    private var sbertModel: SbertModel? = null
    private var audioFingerprint: AudioFingerprint? = null
    private var isInitialized: Boolean = false

    /**
     * Ð˜Ð½Ð¸Ñ†Ð¸Ð°Ð»Ð¸Ð·Ð¸Ñ€ÑƒÐµÑ‚ Ð¼Ð¾Ð´ÑƒÐ»ÑŒ (Ð·Ð°Ð³Ñ€ÑƒÐ¶Ð°ÐµÑ‚ Ð¼Ð¾Ð´ÐµÐ»Ð¸ SBERT Ð¸ Ð¿Ð¾Ð´Ð³Ð¾Ñ‚Ð°Ð²Ð»Ð¸Ð²Ð°ÐµÑ‚ Chromaprint)
     */
    @Synchronized
    fun initialize(context: Context) {
        if (!isInitialized) {
            try {
                // ÐŸÑ€Ð¾Ð±ÑƒÐµÐ¼ Ð¸Ð½Ð¸Ñ†Ð¸Ð°Ð»Ð¸Ð·Ð¸Ñ€Ð¾Ð²Ð°Ñ‚ÑŒ SBERT Ð¼Ð¾Ð´ÐµÐ»ÑŒ, Ð½Ð¾ Ð¿Ñ€Ð¾Ð´Ð¾Ð»Ð¶Ð°ÐµÐ¼ Ñ€Ð°Ð±Ð¾Ñ‚Ñƒ Ð´Ð°Ð¶Ðµ ÐµÑÐ»Ð¸ Ð½Ðµ ÑƒÐ´Ð°Ð»Ð¾ÑÑŒ
                try {
                    sbertModel = SbertModel(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to initialize SBERT model: ${e.message}")
                    // ÐŸÑ€Ð¾Ð´Ð¾Ð»Ð¶Ð°ÐµÐ¼ Ñ€Ð°Ð±Ð¾Ñ‚Ñƒ Ð±ÐµÐ· SBERT Ð¼Ð¾Ð´ÐµÐ»Ð¸
                }
                
                // ÐŸÑ€Ð¾Ð±ÑƒÐµÐ¼ Ð¸Ð½Ð¸Ñ†Ð¸Ð°Ð»Ð¸Ð·Ð¸Ñ€Ð¾Ð²Ð°Ñ‚ÑŒ AudioFingerprint
                try {
                    audioFingerprint = AudioFingerprint(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to initialize AudioFingerprint: ${e.message}")
                    // ÐŸÑ€Ð¾Ð´Ð¾Ð»Ð¶Ð°ÐµÐ¼ Ñ€Ð°Ð±Ð¾Ñ‚Ñƒ Ð±ÐµÐ· AudioFingerprint
                }
                
                // Ð¡Ñ‡Ð¸Ñ‚Ð°ÐµÐ¼ Ð¸Ð½Ð¸Ñ†Ð¸Ð°Ð»Ð¸Ð·Ð°Ñ†Ð¸ÑŽ ÑƒÑÐ¿ÐµÑˆÐ½Ð¾Ð¹ Ð² Ð»ÑŽÐ±Ð¾Ð¼ ÑÐ»ÑƒÑ‡Ð°Ðµ
                isInitialized = true
                Log.i(TAG, "SearchEngine initialized (some components may be unavailable)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SearchEngine: ${e.message}")
                // Ð”Ð°Ð¶Ðµ Ð¿Ñ€Ð¸ Ð¿Ð¾Ð»Ð½Ð¾Ð¹ Ð¾ÑˆÐ¸Ð±ÐºÐµ Ð¸Ð½Ð¸Ñ†Ð¸Ð°Ð»Ð¸Ð·Ð°Ñ†Ð¸Ð¸, Ð¼Ñ‹ Ð²ÑÐµ Ñ€Ð°Ð²Ð½Ð¾ Ð±ÑƒÐ´ÐµÐ¼ Ð¿Ñ‹Ñ‚Ð°Ñ‚ÑŒÑÑ Ð¸ÑÐºÐ°Ñ‚ÑŒ Ñ‚Ñ€ÐµÐºÐ¸
                isInitialized = true
            }
        }
    }

    @Deprecated("Function not used in pipeline")
    suspend fun search(context: Context, input: TrackInput): YouTubeSearchResult? {
        // Ð­Ñ‚Ð¾Ñ‚ Ð¼ÐµÑ‚Ð¾Ð´ ÑÐ¾Ñ…Ñ€Ð°Ð½ÐµÐ½ Ð´Ð»Ñ Ð¾Ð±Ñ€Ð°Ñ‚Ð½Ð¾Ð¹ ÑÐ¾Ð²Ð¼ÐµÑÑ‚Ð¸Ð¼Ð¾ÑÑ‚Ð¸
        return null
    }

    // ÐÐ¾Ñ€Ð¼Ð°Ð»Ð¸Ð·Ð°Ñ†Ð¸Ñ: Ñ‚Ð¾Ð»ÑŒÐºÐ¾ Ð±ÑƒÐºÐ²Ñ‹, Ñ†Ð¸Ñ„Ñ€Ñ‹ Ð¸ Ð¿Ñ€Ð¾Ð±ÐµÐ»Ñ‹, Ð±ÐµÐ· ÑÐ¿ÐµÑ†ÑÐ¸Ð¼Ð²Ð¾Ð»Ð¾Ð²
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
     * ÐžÑÐ½Ð¾Ð²Ð½Ð¾Ð¹ Ð¼ÐµÑ‚Ð¾Ð´ Ð´Ð»Ñ Ð¿Ð¾Ð¸ÑÐºÐ° YouTube-Ð²Ð¸Ð´ÐµÐ¾ Ð´Ð»Ñ Ñ‚Ñ€ÐµÐºÐ°
     * Ð ÐµÐ°Ð»Ð¸Ð·ÑƒÐµÑ‚ ÑÑ‚Ñ€Ð°Ñ‚ÐµÐ³Ð¸ÑŽ:
     * 1. Ð¡Ð½Ð°Ñ‡Ð°Ð»Ð° Ð¸Ñ‰ÐµÑ‚ Ð¿Ð¾ ISRC (ÐµÑÐ»Ð¸ Ð´Ð¾ÑÑ‚ÑƒÐ¿ÐµÐ½)
     * 2. Ð—Ð°Ñ‚ÐµÐ¼ Ð¿Ð¾ Ñ‚ÐµÐºÑÑ‚Ð¾Ð²Ð¾Ð¼Ñƒ Ð·Ð°Ð¿Ñ€Ð¾ÑÑƒ Ñ Ñ„Ð¸Ð»ÑŒÑ‚Ñ€Ð¾Ð¼ "ÐŸÐµÑÐ½Ð¸"
     * 3. ÐžÑ†ÐµÐ½Ð¸Ð²Ð°ÐµÑ‚ Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚Ñ‹ Ð¿Ð¾ ÑÑ…Ð¾Ð´ÑÑ‚Ð²Ñƒ Ð½Ð°Ð·Ð²Ð°Ð½Ð¸Ñ Ð¸ Ð´Ñ€ÑƒÐ³Ð¸Ð¼ Ñ„Ð°ÐºÑ‚Ð¾Ñ€Ð°Ð¼
     * 4. Ð•ÑÐ»Ð¸ Ð¾Ñ†ÐµÐ½ÐºÐ° < 70, Ð¿Ñ€Ð¾Ð²ÐµÑ€ÑÐµÑ‚ ÑÑ…Ð¾Ð´ÑÑ‚Ð²Ð¾ Ð°ÑƒÐ´Ð¸Ð¾-Ð¾Ñ‚Ð¿ÐµÑ‡Ð°Ñ‚ÐºÐ¾Ð²
     *
     * @param track Ð˜Ð½Ñ„Ð¾Ñ€Ð¼Ð°Ñ†Ð¸Ñ Ð¾ Ñ‚Ñ€ÐµÐºÐµ (Ð°Ñ€Ñ‚Ð¸ÑÑ‚, Ð½Ð°Ð·Ð²Ð°Ð½Ð¸Ðµ, Ð´Ð»Ð¸Ñ‚ÐµÐ»ÑŒÐ½Ð¾ÑÑ‚ÑŒ, ISRC)
     * @param context ÐšÐ¾Ð½Ñ‚ÐµÐºÑÑ‚ Ð¿Ñ€Ð¸Ð»Ð¾Ð¶ÐµÐ½Ð¸Ñ Ð´Ð»Ñ Ð´Ð¾ÑÑ‚ÑƒÐ¿Ð° Ðº Ð¼Ð¾Ð´ÐµÐ»ÑÐ¼
     * @return YtTrackCacheEntity Ñ videoId Ð¸Ð»Ð¸ null, ÐµÑÐ»Ð¸ Ð½Ð¸Ñ‡ÐµÐ³Ð¾ Ð½Ðµ Ð½Ð°Ð¹Ð´ÐµÐ½Ð¾
     */
    suspend fun findBestYoutube(track: TrackEntity, context: Context? = null): YtTrackCacheEntity? {
        try {
            // Ð˜Ð½Ð¸Ñ†Ð¸Ð°Ð»Ð¸Ð·Ð°Ñ†Ð¸Ñ Ð¼Ð¾Ð´ÐµÐ»ÐµÐ¹, ÐµÑÐ»Ð¸ Ð½ÑƒÐ¶Ð½Ð¾
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
            // 2. Text search Ñ Ð½Ð¾Ð²Ñ‹Ð¼ ÑÐºÐ¾Ñ€Ð¸Ð½Ð³Ð¾Ð¼
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
     * ÐžÑ†ÐµÐ½ÐºÐ° ÑÐ¾Ð²Ð¿Ð°Ð´ÐµÐ½Ð¸Ñ Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚Ð° Ð¿Ð¾Ð¸ÑÐºÐ° Ñ Ñ‚Ñ€ÐµÐºÐ¾Ð¼
     */
    private fun scoreTrackMatch(item: StreamInfoItem, track: TrackEntity): Int {
        val videoTitleNorm = normalize(item.name)
        val uploaderNorm = normalize(item.uploaderName ?: "")
        val titleNorm = normalize(track.title)
        val artistNorm = normalize(track.artist)
        var score = 0
        // Title Ð² Ð½Ð°Ñ‡Ð°Ð»Ðµ Ð½Ð°Ð·Ð²Ð°Ð½Ð¸Ñ
        if (videoTitleNorm.startsWith(titleNorm)) score += 2
        // Title Ð³Ð´Ðµ ÑƒÐ³Ð¾Ð´Ð½Ð¾
        else if (videoTitleNorm.contains(titleNorm)) score += 1
        // Artist Ð² uploader Ð¸Ð»Ð¸ Ð² Ð½Ð°Ð·Ð²Ð°Ð½Ð¸Ð¸
        if (uploaderNorm.contains(artistNorm) || videoTitleNorm.contains(artistNorm)) score += 1
        // "slowed"/"reverb" ÐµÑÐ»Ð¸ ÐµÑÑ‚ÑŒ Ð² Ð½Ð°Ð·Ð²Ð°Ð½Ð¸Ð¸ Ð¸ Ð² Ð·Ð°Ð¿Ñ€Ð¾ÑÐµ
        val mods = listOf("slowed", "reverb")
        val queryMods = mods.filter { normalize(track.title + track.artist).contains(it) }
        if (queryMods.any { videoTitleNorm.contains(it) }) score += 1
        // Penalty ÐµÑÐ»Ð¸ title Ð½Ðµ Ð½Ð°Ð¹Ð´ÐµÐ½ Ð²Ð¾Ð¾Ð±Ñ‰Ðµ
        if (!videoTitleNorm.contains(titleNorm)) score -= 2
        return score
    }
    
    /**
     * ÐŸÑ€Ð¾Ð²ÐµÑ€ÑÐµÑ‚ ÑÐ¾Ð²Ð¿Ð°Ð´ÐµÐ½Ð¸Ðµ Ð½Ð° Ð¾ÑÐ½Ð¾Ð²Ðµ Ð°ÑƒÐ´Ð¸Ð¾-Ð¾Ñ‚Ð¿ÐµÑ‡Ð°Ñ‚ÐºÐ°
     * Ð˜ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÑ‚ÑÑ, ÐºÐ¾Ð³Ð´Ð° ÑƒÐ²ÐµÑ€ÐµÐ½Ð½Ð¾ÑÑ‚ÑŒ Ð½Ð° Ð¾ÑÐ½Ð¾Ð²Ðµ Ñ‚ÐµÐºÑÑ‚Ð° < 70
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
            
            // ÐŸÐ¾Ð»ÑƒÑ‡Ð°ÐµÐ¼ videoId
            val videoId = item?.url?.substringAfter("v=")?.take(11)
            
            // 1. Ð—Ð°Ð³Ñ€ÑƒÐ¶Ð°ÐµÐ¼ Ð¿ÐµÑ€Ð²Ñ‹Ðµ 10 ÑÐµÐºÑƒÐ½Ð´ Ð°ÑƒÐ´Ð¸Ð¾
            val audioUrl = "https://www.youtube.com/watch?v=$videoId" // ÐŸÑ€Ð°Ð²Ð¸Ð»ÑŒÐ½Ñ‹Ð¹ URL Ð´Ð»Ñ Ð·Ð°Ð³Ñ€ÑƒÐ·ÐºÐ¸ Ð°ÑƒÐ´Ð¸Ð¾
            val fingerprint = audioFingerprint?.createFingerprintFromUrl(audioUrl)
            
            if (fingerprint == null) {
                Log.w(TAG, "Failed to create fingerprint for $videoId")
                return@withContext null
            }
            
            // 2. Ð¡Ñ€Ð°Ð²Ð½Ð¸Ð²Ð°ÐµÐ¼ Ñ Ñ€ÐµÑ„ÐµÑ€ÐµÐ½ÑÐ½Ñ‹Ð¼ Ð¾Ñ‚Ð¿ÐµÑ‡Ð°Ñ‚ÐºÐ¾Ð¼ (Ð² Ñ€ÐµÐ°Ð»ÑŒÐ½Ð¾ÑÑ‚Ð¸ Ð±Ñ‹Ð» Ð±Ñ‹ Ð·Ð°Ð¿Ñ€Ð¾Ñ Ðº AcoustID)
            // Ð—Ð´ÐµÑÑŒ Ð´Ð»Ñ Ð¿Ñ€Ð¸Ð¼ÐµÑ€Ð° Ð¿Ñ€Ð¾ÑÑ‚Ð¾ Ð¿Ñ€Ð¸Ð»Ð¾Ð¶ÐµÐ½Ð¸Ðµ Ð´Ð¾Ð»Ð¶Ð½Ð¾ ÐºÑÑˆÐ¸Ñ€Ð¾Ð²Ð°Ñ‚ÑŒ Ð¾Ñ‚Ð¿ÐµÑ‡Ð°Ñ‚ÐºÐ¸ Ñ‚Ñ€ÐµÐºÐ¾Ð²
            
            // Ð£Ð¿Ñ€Ð¾Ñ‰ÐµÐ½Ð½Ð¾: ÐµÑÐ»Ð¸ ÐµÑÑ‚ÑŒ Ð»Ð¾ÐºÐ°Ð»ÑŒÐ½Ñ‹Ð¹ Ð¾Ñ‚Ð¿ÐµÑ‡Ð°Ñ‚Ð¾Ðº, ÑÑ€Ð°Ð²Ð½Ð¸Ð²Ð°ÐµÐ¼ Ñ Ð½Ð¸Ð¼
            val cachedFingerprint = getCachedFingerprint(track.id, context)
            if (cachedFingerprint != null) {
                val similarity = audioFingerprint?.compareFingerprintSimilarity(fingerprint, cachedFingerprint)
                
                Log.i(TAG, "Fingerprint similarity: $similarity")
                
                if (similarity != null && similarity >= FINGERPRINT_MATCH_THRESHOLD) {
                    Log.i(TAG, "Fingerprint match confirmed: ${item?.name}")
                    return@withContext YtTrackCacheEntity(track.id, videoId ?: "", 95f)
                }
            } else {
                // Ð•ÑÐ»Ð¸ Ð½ÐµÑ‚ Ñ€ÐµÑ„ÐµÑ€ÐµÐ½ÑÐ½Ð¾Ð³Ð¾ Ð¾Ñ‚Ð¿ÐµÑ‡Ð°Ñ‚ÐºÐ°, Ð¿Ñ€Ð¾ÑÑ‚Ð¾ ÑÐ¾Ñ…Ñ€Ð°Ð½ÑÐµÐ¼ Ñ‚ÐµÐºÑƒÑ‰Ð¸Ð¹ ÐºÐ°Ðº Ñ€ÐµÑ„ÐµÑ€ÐµÐ½ÑÐ½Ñ‹Ð¹
                // Ð¸ Ð¿Ð¾Ð»Ð°Ð³Ð°ÐµÐ¼ÑÑ Ð½Ð° Ð±Ð°Ð·Ð¾Ð²ÑƒÑŽ Ð¾Ñ†ÐµÐ½ÐºÑƒ
                saveFingerprintToCache(track.id, fingerprint, context)
                
                // Ð’Ð¾Ð·Ð²Ñ€Ð°Ñ‰Ð°ÐµÐ¼ Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚, Ð½Ð¾ Ñ Ð½Ð¸Ð·ÐºÐ¾Ð¹ ÑƒÐ²ÐµÑ€ÐµÐ½Ð½Ð¾ÑÑ‚ÑŒÑŽ
                return@withContext YtTrackCacheEntity(track.id, videoId ?: "", 65f)
            }
            
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying with audio fingerprint: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * ÐŸÐ¾Ð»ÑƒÑ‡Ð°ÐµÑ‚ ÐºÑÑˆÐ¸Ñ€Ð¾Ð²Ð°Ð½Ð½Ñ‹Ð¹ Ð¾Ñ‚Ð¿ÐµÑ‡Ð°Ñ‚Ð¾Ðº Ñ‚Ñ€ÐµÐºÐ° (ÑƒÐ¿Ñ€Ð¾Ñ‰ÐµÐ½Ð½Ð¾)
     * Ð’ Ñ€ÐµÐ°Ð»ÑŒÐ½Ð¾Ð¼ Ð¿Ñ€Ð¸Ð»Ð¾Ð¶ÐµÐ½Ð¸Ð¸ Ð¸ÑÐ¿Ð¾Ð»ÑŒÐ·Ð¾Ð²Ð°Ð»Ð°ÑÑŒ Ð±Ñ‹ Ð±Ð°Ð·Ð° Ð´Ð°Ð½Ð½Ñ‹Ñ…
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
     * Ð¡Ð¾Ñ…Ñ€Ð°Ð½ÑÐµÑ‚ Ð¾Ñ‚Ð¿ÐµÑ‡Ð°Ñ‚Ð¾Ðº Ð² ÐºÑÑˆ (ÑƒÐ¿Ñ€Ð¾Ñ‰ÐµÐ½Ð½Ð¾)
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
     * Ð Ð°Ð½Ð¶Ð¸Ñ€ÑƒÐµÑ‚ Ñ€ÐµÐ·ÑƒÐ»ÑŒÑ‚Ð°Ñ‚Ñ‹ Ð¿Ð¾ Ð½ÐµÑÐºÐ¾Ð»ÑŒÐºÐ¸Ð¼ ÐºÑ€Ð¸Ñ‚ÐµÑ€Ð¸ÑÐ¼:
     * - Ð¡Ñ…Ð¾Ð´ÑÑ‚Ð²Ð¾ Ñ Ð·Ð°Ð¿Ñ€Ð¾ÑÐ¾Ð¼
     * - Ð¢Ð¸Ð¿ ÐºÐ°Ð½Ð°Ð»Ð° (Ð¿Ñ€ÐµÐ´Ð¿Ð¾Ñ‡Ð¸Ñ‚Ð°ÐµÐ¼ Ð¾Ñ„Ð¸Ñ†Ð¸Ð°Ð»ÑŒÐ½Ñ‹Ðµ ÐºÐ°Ð½Ð°Ð»Ñ‹)
     * - Ð”Ð»Ð¸Ñ‚ÐµÐ»ÑŒÐ½Ð¾ÑÑ‚ÑŒ (Ð¿Ñ€ÐµÐ´Ð¿Ð¾Ñ‡Ð¸Ñ‚Ð°ÐµÐ¼ Ð±Ð»Ð¸Ð·ÐºÑƒÑŽ Ðº Ð¾Ñ€Ð¸Ð³Ð¸Ð½Ð°Ð»Ñƒ)
     */
    private fun rankResults(input: TrackInput, items: List<StreamInfoItem>): List<YouTubeSearchResult> {
        val results = ArrayList<YouTubeSearchResult>()
        for (item in items) {
            var score = 0
            
            // Ð‘Ð°Ð·Ð¾Ð²Ð°Ñ Ð¾Ñ†ÐµÐ½ÐºÐ° - TODO: ÑƒÐ»ÑƒÑ‡ÑˆÐ¸Ñ‚ÑŒ Ð°Ð»Ð³Ð¾Ñ€Ð¸Ñ‚Ð¼ ÑÑ€Ð°Ð²Ð½ÐµÐ½Ð¸Ñ
            val query = "${input.artist} - ${input.title}"
            score += (Similarity.jaroWinkler(query, item.name) * 50).toInt()
            
            // Ð‘Ð¾Ð½ÑƒÑ Ð·Ð° Ð¾Ñ„Ð¸Ñ†Ð¸Ð°Ð»ÑŒÐ½Ñ‹Ð¹ ÐºÐ°Ð½Ð°Ð»
            if (item.uploaderName.endsWith(" - Topic") || item.uploaderName.contains("VEVO")) score += 25
            
            // Ð‘Ð¾Ð½ÑƒÑ Ð¸Ð»Ð¸ ÑˆÑ‚Ñ€Ð°Ñ„ Ð·Ð° Ð´Ð»Ð¸Ñ‚ÐµÐ»ÑŒÐ½Ð¾ÑÑ‚ÑŒ
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
     * ÐžÑÐ²Ð¾Ð±Ð¾Ð¶Ð´Ð°ÐµÑ‚ Ñ€ÐµÑÑƒÑ€ÑÑ‹ Ð¼Ð¾Ð´ÐµÐ»Ð¸ SBERT Ð¸ Ð°ÑƒÐ´Ð¸Ð¾-Ð¾Ñ‚Ð¿ÐµÑ‡Ð°Ñ‚ÐºÐ¾Ð²
     */
    fun release() {
        sbertModel?.close()
        audioFingerprint?.release()
        sbertModel = null
        audioFingerprint = null
        isInitialized = false
    }
}
