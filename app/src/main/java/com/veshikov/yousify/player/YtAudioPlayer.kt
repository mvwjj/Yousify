package com.veshikov.yousify.player

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import android.util.Log
import com.veshikov.yousify.R

/**
 * Сервис воспроизведения YouTube аудио с поддержкой:
 * - Foreground Service для непрерывного воспроизведения
 * - Только аудио-стримы (экономия трафика и батареи)
 * - Автоматический пропуск спонсорских вставок (SponsorBlock)
 * - Защита от рекламы (используя прямые ссылки на контент)
 * - Специальные настройки для устройств Android 8-10
 */
@UnstableApi
class YtAudioPlayer : LifecycleService() {
    companion object {
        private const val TAG = "YtAudioPlayer"
        private const val NOTIFICATION_ID = 1337
        private const val CHANNEL_ID = "YtAudioPlayer_channel"
        private const val ACTION_PLAY = "com.veshikov.yousify.ACTION_PLAY"
        private const val ACTION_PAUSE = "com.veshikov.yousify.ACTION_PAUSE"
        private const val ACTION_STOP = "com.veshikov.yousify.ACTION_STOP"
        private const val ACTION_FORWARD = "com.veshikov.yousify.ACTION_FORWARD"
        private const val ACTION_BACKWARD = "com.veshikov.yousify.ACTION_BACKWARD"
        
        // SponsorBlock API
        private const val SPONSOR_BLOCK_API = "https://sponsor.ajay.app/api/skipSegments"
        private const val SPONSOR_BLOCK_CATEGORIES = "[\"sponsor\",\"intro\",\"outro\",\"selfpromo\"]"
        
        // Небольшой запас времени после пропуска сегмента, чтобы не попасть сразу же обратно
        private const val SEGMENT_SKIP_PADDING_MS = 500L
    }

    // ExoPlayer и связанные компоненты
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager
    private var currentVideoId: String? = null
    private var isPlaying = false
    
    // Хранилище сегментов SponsorBlock для текущего видео
    private var sponsorSegments: List<Pair<Long, Long>> = emptyList()
    private var lastSkippedSegmentIndex = -1
    
    // Binder для управления сервисом
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): YtAudioPlayer = this@YtAudioPlayer
    }

    // Слушатель состояния плеера
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "Buffering...")
                    updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
                }
                Player.STATE_READY -> {
                    Log.d(TAG, "Ready to play")
                    if (exoPlayer.playWhenReady) {
                        isPlaying = true
                        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                        updateNotification()
                    } else {
                        isPlaying = false
                        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                        updateNotification()
                    }
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Playback ended")
                    isPlaying = false
                    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                    stopSelf()
                }
                Player.STATE_IDLE -> {
                    Log.d(TAG, "Idle state")
                    isPlaying = false
                    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@YtAudioPlayer.isPlaying = isPlaying
            updateNotification()
        }
        
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, 
            newPosition: Player.PositionInfo, 
            reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            // Если разрыв не вызван нашим собственным ручным перемещением, проверяем спонсорские сегменты
            if (reason != Player.DISCONTINUITY_REASON_SEEK) {
                checkForSponsorSegments()
            }
        }
        
        override fun onEvents(player: Player, events: Player.Events) {
            super.onEvents(player, events)
            // Периодически проверяем спонсорские сегменты
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) || 
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                checkForSponsorSegments()
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Инициализация менеджера уведомлений и создание канала
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        
        // Инициализация MediaSession
        mediaSession = MediaSessionCompat(this, TAG)
        mediaSession.setCallback(MediaSessionCallback())
        mediaSession.isActive = true
        
        // Инициализация ExoPlayer с особыми настройками для API 26-29
        initializePlayer()
        
        // Отображаем пустое уведомление, чтобы сервис стал foreground
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    /**
     * Инициализация ExoPlayer с учетом специфичных проблем на Android 8-10
     */
    private fun initializePlayer() {
        val renderersFactory = DefaultRenderersFactory(this).apply {
            // Для устройств с API 26-29 (Android 8.0-10), используем обходной путь для известных багов MediaCodec
            if (Build.VERSION.SDK_INT in 26..29) {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                // Дополнительные настройки для обхода багов:
                // - Отключение туннелирования для избежания freeze/crash
                setEnableAudioTrackPlaybackParams(false)
                setEnableDecoderFallback(true)
            }
        }
        
        // Настройка селектора треков - ТОЛЬКО АУДИО
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setPreferredAudioLanguage("en"))
        }
        
        // Настройка фабрики источников данных
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
            this,
            DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0")
        )
        
        // Настройка фабрики медиа-источников
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        // Создание экземпляра ExoPlayer
        exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        
        // Важно: отключаем видео и поверхность для рендеринга
        exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setMaxVideoSizeSd() // Любые ограничения, чтобы не загружать видео
            .setPreferredVideoMimeType(null) // Null, чтобы не указывать предпочтения
            .build()
            
        // Добавляем слушатель
        exoPlayer.addListener(playerListener)
    }
    
    /**
     * Запуск воспроизведения YouTube видео по ID
     * @param videoId ID видео на YouTube
     * @param title Название трека (для уведомления)
     * @param artist Исполнитель (для уведомления)
     * @param thumbnailUrl URL обложки (опционально)
     */
    fun playYoutubeAudio(videoId: String, title: String, artist: String, thumbnailUrl: String? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                currentVideoId = videoId
                
                // Загружаем информацию о стримах
                // val streamInfo = extractStreamInfo(videoId)
                // if (streamInfo == null) {
                //     Log.e(TAG, "Failed to extract stream info for $videoId")
                //     return@launch
                // }
                
                // Обновляем метаданные для MediaSession
                updateMediaSessionMetadata(title, artist, thumbnailUrl)
                
                // Параллельно загружаем информацию о спонсорских сегментах
                // fetchSponsorSegments(videoId)
                
                // Выбираем аудио-стрим с наивысшим битрейтом
                // val bestAudioStream = selectBestAudioStream(streamInfo)
                // if (bestAudioStream == null) {
                //     Log.e(TAG, "No suitable audio stream found for $videoId")
                //     return@launch
                // }
                
                // Log.d(TAG, "Selected audio stream: ${bestAudioStream.url}, bitrate: ${bestAudioStream.averageBitrate}")
                
                // Создаем медиа-источник
                // val mediaItem = MediaItem.Builder()
                //     .setUri(Uri.parse(bestAudioStream.url))
                //     .setMediaId(videoId)
                //     .setMediaMetadata(
                //         MediaMetadata.Builder()
                //             .setTitle(title)
                //             .setArtist(artist)
                //             .setIsPlayable(true)
                //             .build()
                //     )
                //     .build()
                
                // Переключаемся на главный поток для работы с ExoPlayer
                withContext(Dispatchers.Main) {
                    // exoPlayer.setMediaItem(mediaItem)
                    // exoPlayer.prepare()
                    // exoPlayer.playWhenReady = true
                    
                    // Обновляем уведомление
                    updateNotification()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up YouTube playback", e)
            }
        }
    }
    
    /**
     * Извлекает информацию о потоках для видео
     */
    // private suspend fun extractStreamInfo(videoId: String): StreamInfo? = withContext(Dispatchers.IO) {
    //     try {
    //         val linkHandler = YoutubeStreamLinkHandler.getInstance()
    //         linkHandler.acceptUrl("https://www.youtube.com/watch?v=$videoId")
    //         val streamExtractor = YoutubeStreamExtractor(ServiceList.YouTube, linkHandler)
    //         streamExtractor.fetchPage()
    //         return@withContext streamExtractor.getStreamInfo()
    //     } catch (e: Exception) {
    //         Log.e(TAG, "Error extracting StreamInfo", e)
    //         return@withContext null
    //     }
    // }
    
    /**
     * Выбирает аудио-поток с наивысшим битрейтом
     * Предпочитает M4A или AAC за их лучшее качество на единицу битрейта
     */
    // private fun selectBestAudioStream(streamInfo: StreamInfo): AudioStream? {
    //     val audioStreams = streamInfo.audioStreams
    //     if (audioStreams.isEmpty()) return null
        
    //     // Сортируем потоки по нисходящему битрейту, предпочитая M4A/AAC
    //     return audioStreams.sortedWith(
    //         compareByDescending<AudioStream> { 
    //             // Приоритет M4A/AAC
    //             when {
    //                 it.getFormat().name.contains("M4A", ignoreCase = true) -> 1000000 + it.averageBitrate
    //                 it.getFormat().name.contains("AAC", ignoreCase = true) -> 900000 + it.averageBitrate
    //                 else -> it.averageBitrate
    //             }
    //         }
    //     ).firstOrNull()
    // }
    
    /**
     * Загружает информацию о спонсорских сегментах через SponsorBlock API
     */
    // private suspend fun fetchSponsorSegments(videoId: String) = withContext(Dispatchers.IO) {
    //     try {
    //         val url = URL("$SPONSOR_BLOCK_API?videoID=$videoId&categories=$SPONSOR_BLOCK_CATEGORIES")
    //         val connection = url.openConnection() as HttpsURLConnection
    //         connection.requestMethod = "GET"
    //         connection.connectTimeout = 5000
    //         connection.readTimeout = 5000
            
    //         val responseCode = connection.responseCode
    //         if (responseCode == HttpsURLConnection.HTTP_OK) {
    //             val response = connection.inputStream.bufferedReader().use { it.readText() }
    //             parseSegments(response)
    //         } else {
    //             Log.e(TAG, "SponsorBlock API error: $responseCode")
    //             sponsorSegments = emptyList()
    //         }
    //     } catch (e: Exception) {
    //         Log.e(TAG, "Error fetching sponsor segments", e)
    //         sponsorSegments = emptyList()
    //     }
    // }
    
    /**
     * Парсит ответ от SponsorBlock API
     */
    // private fun parseSegments(jsonResponse: String) {
    //     try {
    //         val segments = mutableListOf<Pair<Long, Long>>()
    //         val jsonArray = JSONArray(jsonResponse)
            
    //         for (i in 0 until jsonArray.length()) {
    //             val segmentObj = jsonArray.getJSONObject(i)
    //             val segmentInfo = segmentObj.getJSONArray("segment")
                
    //             val startMs = (segmentInfo.getDouble(0) * 1000).toLong()
    //             val endMs = (segmentInfo.getDouble(1) * 1000).toLong()
                
    //             segments.add(Pair(startMs, endMs))
    //         }
            
    //         sponsorSegments = segments.sortedBy { it.first }
    //         Log.d(TAG, "Loaded ${sponsorSegments.size} sponsor segments")
    //     } catch (e: Exception) {
    //         Log.e(TAG, "Error parsing sponsor segments", e)
    //         sponsorSegments = emptyList()
    //     }
    // }
    
    /**
     * Проверяет, находится ли текущая позиция в спонсорском сегменте
     * и пропускает его, если да
     */
    private fun checkForSponsorSegments() {
        if (!isPlaying || sponsorSegments.isEmpty()) return
        
        val currentPosition = exoPlayer.currentPosition
        
        for ((index, segment) in sponsorSegments.withIndex()) {
            val (startMs, endMs) = segment
            
            // Если мы внутри спонсорского сегмента и это не тот же сегмент, что мы только что пропустили
            if (currentPosition in startMs..endMs && index != lastSkippedSegmentIndex) {
                // Прыгаем в конец сегмента + небольшой запас
                val skipToMs = endMs + SEGMENT_SKIP_PADDING_MS
                
                // Если новая позиция всё ещё внутри диапазона воспроизведения
                if (skipToMs <= exoPlayer.duration) {
                    Log.d(TAG, "Skipping sponsor segment from ${startMs}ms to ${endMs}ms")
                    exoPlayer.seekTo(skipToMs)
                    lastSkippedSegmentIndex = index
                    
                    // Небольшая задержка перед сбросом индекса пропущенного сегмента
                    Handler(Looper.getMainLooper()).postDelayed({
                        lastSkippedSegmentIndex = -1
                    }, 1000)
                }
                break
            }
        }
    }
    
    /**
     * Создает канал уведомлений для Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Audio Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for YouTube Audio Player"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Создает/обновляет уведомление для foreground сервиса
     */
    private fun createNotification(): Notification {
        // Действия медиа-контроллера
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_track_placeholder, "Pause",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, YtAudioPlayer::class.java).setAction(ACTION_PAUSE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_track_placeholder, "Play",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, YtAudioPlayer::class.java).setAction(ACTION_PLAY),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        
        val stopAction = NotificationCompat.Action(
            R.drawable.ic_track_placeholder, "Stop",
            PendingIntent.getService(
                this, 0,
                Intent(this, YtAudioPlayer::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        
        // Intent для открытия приложения при нажатии на уведомление
        // val contentIntent = PendingIntent.getActivity(
        //     this, 0,
        //     Intent(this, MainActivity::class.java),
        //     PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // )
        
        // Получаем текущие метаданные
        val metadata = mediaSession.controller.metadata
        val title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "Unknown Title"
        val artist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "Unknown Artist"
        
        // Создаем и возвращаем уведомление
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_track_placeholder)
            .setLargeIcon(
                metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                    ?: BitmapFactory.decodeResource(resources, R.drawable.ic_track_placeholder)
            )
            // .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setDeleteIntent(
                PendingIntent.getService(
                    this, 0,
                    Intent(this, YtAudioPlayer::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_track_placeholder, "Previous 10s",
                    PendingIntent.getService(
                        this, 0,
                        Intent(this, YtAudioPlayer::class.java).setAction(ACTION_BACKWARD),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            )
            .addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_track_placeholder, "Next 10s",
                    PendingIntent.getService(
                        this, 0,
                        Intent(this, YtAudioPlayer::class.java).setAction(ACTION_FORWARD),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            )
            .addAction(stopAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(1, 2) // Показывать первые 2 действия в свернутом виде
                    .setShowCancelButton(true)
            )
            .build()
    }
    
    /**
     * Обновляет текущее уведомление
     */
    private fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Обновляет метаданные медиа-сессии
     */
    private fun updateMediaSessionMetadata(title: String, artist: String, thumbnailUrl: String?) {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1)
        
        // Асинхронно загружаем обложку, если доступна
        if (thumbnailUrl != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = loadBitmapFromUrl(thumbnailUrl)
                    if (bitmap != null) {
                        metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                        mediaSession.setMetadata(metadataBuilder.build())
                        updateNotification()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading album art", e)
                }
            }
        }
        
        mediaSession.setMetadata(metadataBuilder.build())
    }
    
    /**
     * Загружает обложку по URL
     */
    private suspend fun loadBitmapFromUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            return@withContext BitmapFactory.decodeStream(input)
        } catch (e: IOException) {
            Log.e(TAG, "Error loading album art from URL", e)
            return@withContext null
        }
    }
    
    /**
     * Обновляет состояние воспроизведения
     */
    private fun updatePlaybackState(state: Int) {
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                state,
                exoPlayer.currentPosition,
                exoPlayer.playbackParameters.speed
            )
        
        mediaSession.setPlaybackState(stateBuilder.build())
    }
    
    /**
     * Обработчик действий через MediaSession
     */
    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            exoPlayer.play()
            isPlaying = true
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            updateNotification()
        }

        override fun onPause() {
            exoPlayer.pause()
            isPlaying = false
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            updateNotification()
        }

        override fun onStop() {
            exoPlayer.stop()
            isPlaying = false
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            stopSelf()
        }

        override fun onSeekTo(pos: Long) {
            exoPlayer.seekTo(pos)
        }
    }
    
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        if (intent?.action != null) {
            when (intent.action) {
                ACTION_PLAY -> {
                    exoPlayer.play()
                }
                ACTION_PAUSE -> {
                    exoPlayer.pause()
                }
                ACTION_STOP -> {
                    exoPlayer.stop()
                    stopSelf()
                }
                ACTION_FORWARD -> {
                    val newPosition = exoPlayer.currentPosition + TimeUnit.SECONDS.toMillis(10)
                    exoPlayer.seekTo(newPosition.coerceAtMost(exoPlayer.duration))
                }
                ACTION_BACKWARD -> {
                    val newPosition = exoPlayer.currentPosition - TimeUnit.SECONDS.toMillis(10)
                    exoPlayer.seekTo(newPosition.coerceAtLeast(0))
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        mediaSession.isActive = false
        mediaSession.release()
        exoPlayer.release()
        super.onDestroy()
    }
}
