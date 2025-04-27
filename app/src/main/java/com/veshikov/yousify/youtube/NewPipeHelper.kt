package com.veshikov.yousify.youtube

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream

object NewPipeHelper {

    /**
     * Получить URL аудио-потока наилучшего качества для заданного YouTube-видео
     * (можно передать полный URL или просто videoId).
     * Возвращает URL аудио-потока с максимальным averageBitrate, или null если не найдено.
     */
    fun getBestAudioUrl(videoId: String): String? {
        try {
            val url = if (videoId.length == 11 && videoId.all { it.isLetterOrDigit() || it == '-' || it == '_' })
                "https://www.youtube.com/watch?v=$videoId"
            else videoId
            val service = ServiceList.YouTube
            val streamInfo = StreamInfo.getInfo(service, url)
            val audioStreams: List<AudioStream> = streamInfo.getAudioStreams()
            android.util.Log.i("NewPipeHelper", "getBestAudioUrl: videoId=$videoId, streams=${audioStreams.size}")
            audioStreams.forEach { s ->
                android.util.Log.i("NewPipeHelper", "audioStream: bitrate=${s.averageBitrate}, url=${s.url}")
            }
            val best: AudioStream? = audioStreams.maxByOrNull { it.averageBitrate }
            android.util.Log.i("NewPipeHelper", "bestAudioUrl=${best?.url}")
            return best?.url
        } catch (e: Exception) {
            android.util.Log.e("NewPipeHelper", "getBestAudioUrl error: ${e.message}", e)
            return null
        }
    }
    // searchYouTube больше не используется и удалён
}
