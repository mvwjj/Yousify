package com.mvwj.yousify.youtube

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

object NewPipeHelper {
    private const val RETRYABLE_RELOAD_MESSAGE = "The page needs to be reloaded"
    private const val MAX_RELOAD_RETRIES = 3
    private const val RETRY_DELAY_MS = 400L

    fun getBestAudioUrl(videoId: String): String? {
        return try {
            val url = if (videoId.length == 11 && videoId.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                "https://www.youtube.com/watch?v=$videoId"
            } else {
                videoId
            }
            val streamInfo = fetchStreamInfoWithRetry(ServiceList.YouTube, url)
            val audioStreams = streamInfo.audioStreams
            android.util.Log.i("NewPipeHelper", "getBestAudioUrl: videoId=$videoId, streams=${audioStreams.size}")
            audioStreams.forEach { stream ->
                android.util.Log.i(
                    "NewPipeHelper",
                    "audioStream: bitrate=${stream.averageBitrate}, delivery=${stream.deliveryMethod}, url=${stream.url}"
                )
            }

            val best = audioStreams
                .asSequence()
                .filter { it.isUrl && !it.url.isNullOrBlank() }
                .sortedWith(
                    compareByDescending<AudioStream> { deliveryPriority(it.deliveryMethod) }
                        .thenByDescending { it.averageBitrate }
                )
                .firstOrNull()

            android.util.Log.i("NewPipeHelper", "bestAudioUrl=${best?.url}")
            best?.url
        } catch (e: Exception) {
            android.util.Log.e("NewPipeHelper", "getBestAudioUrl error: ${e.message}", e)
            null
        }
    }

    private fun fetchStreamInfoWithRetry(service: StreamingService, url: String): StreamInfo {
        var lastException: Exception? = null
        repeat(MAX_RELOAD_RETRIES) { attempt ->
            try {
                if (attempt > 0) {
                    android.util.Log.w(
                        "NewPipeHelper",
                        "Retrying StreamInfo fetch after reload error. attempt=${attempt + 1} url=$url"
                    )
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                }
                return StreamInfo.getInfo(service, url)
            } catch (e: ContentNotAvailableException) {
                lastException = e
                val shouldRetry = e.message?.contains(RETRYABLE_RELOAD_MESSAGE, ignoreCase = true) == true
                if (!shouldRetry || attempt == MAX_RELOAD_RETRIES - 1) {
                    throw e
                }
            }
        }
        throw lastException ?: IllegalStateException("Failed to fetch stream info for $url")
    }

    private fun deliveryPriority(deliveryMethod: DeliveryMethod): Int = when (deliveryMethod) {
        DeliveryMethod.PROGRESSIVE_HTTP -> 3
        DeliveryMethod.DASH -> 2
        DeliveryMethod.HLS -> 1
        else -> 0
    }
}
