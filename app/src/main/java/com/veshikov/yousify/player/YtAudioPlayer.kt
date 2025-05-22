package com.veshikov.yousify.player

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat as SupportMediaMetadata
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.veshikov.yousify.R // Ваш R файл
import com.veshikov.yousify.utils.Logger
import com.veshikov.yousify.youtube.NewPipeHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@UnstableApi
class YtAudioPlayer : LifecycleService() {
    companion object {
        private const val TAG = "YtAudioPlayer"
        private const val NOTIFICATION_ID = 1337
        private const val CHANNEL_ID = "YtAudioPlayer_channel"
        const val ACTION_PLAY = "com.veshikov.yousify.ACTION_PLAY"
        const val ACTION_PAUSE = "com.veshikov.yousify.ACTION_PAUSE"
        const val ACTION_STOP = "com.veshikov.yousify.ACTION_STOP"
        const val SERVICE_ACTION_SKIP_NEXT = "com.veshikov.yousify.SERVICE_ACTION_SKIP_NEXT"
        const val SERVICE_ACTION_SKIP_PREVIOUS = "com.veshikov.yousify.SERVICE_ACTION_SKIP_PREVIOUS"

        private const val SEGMENT_SKIP_PADDING_MS = 500L

        private val _playerEvents = MutableSharedFlow<PlayerEvent>(replay = 0)
        val playerEvents: SharedFlow<PlayerEvent> = _playerEvents.asSharedFlow()
    }

    sealed class PlayerEvent {
        data class Error(val message: String?, val videoId: String?) : PlayerEvent()
        data class StateChanged(val playbackState: Int, val videoId: String?, val isPlaying: Boolean) : PlayerEvent()
        data class SponsorSegmentsFetched(val videoId: String, val hasSegments: Boolean) : PlayerEvent()
        data class MediaCommand(val command: Long, val videoId: String?) : PlayerEvent()
    }

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat

    private var currentPlayingVideoId: String? = null
    private var currentTrackTitle: String? = "Yousify"
    private var currentTrackArtist: String? = "Audio Player"
    private var currentThumbnailUrl: String? = null

    private var sponsorSegments: List<Pair<Long, Long>> = emptyList()
    private var lastSkippedSegmentIndex = -1

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): YtAudioPlayer = this@YtAudioPlayer
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val videoId = currentPlayingVideoId
            val isPlaying = exoPlayer.isPlaying

            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    Logger.d("Player State: Buffering for $videoId")
                    updatePlaybackStateSupport(PlaybackStateCompat.STATE_BUFFERING)
                    emitEvent(PlayerEvent.StateChanged(PlaybackStateCompat.STATE_BUFFERING, videoId, isPlaying))
                }
                Player.STATE_READY -> {
                    Logger.d("Player State: Ready for $videoId. PlayWhenReady: ${exoPlayer.playWhenReady}")
                    val sessionState = if (exoPlayer.playWhenReady) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
                    updatePlaybackStateSupport(sessionState)
                    emitEvent(PlayerEvent.StateChanged(sessionState, videoId, isPlaying))
                    if (exoPlayer.playWhenReady) {
                        checkForSponsorSegments()
                    }
                    updateNotificationIfPermitted()
                }
                Player.STATE_ENDED -> {
                    Logger.d("Player State: Ended for $videoId")
                    updatePlaybackStateSupport(PlaybackStateCompat.STATE_STOPPED)
                    emitEvent(PlayerEvent.StateChanged(PlaybackStateCompat.STATE_STOPPED, videoId, false))
                    mediaSession.controller.transportControls.skipToNext()
                }
                Player.STATE_IDLE -> {
                    Logger.d("Player State: Idle for $videoId")
                    updatePlaybackStateSupport(PlaybackStateCompat.STATE_NONE)
                    emitEvent(PlayerEvent.StateChanged(PlaybackStateCompat.STATE_NONE, videoId, false))
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Logger.d("Player isPlaying changed to: $isPlaying for $currentPlayingVideoId")
            val videoId = currentPlayingVideoId
            val currentState = mediaSession.controller.playbackState?.state ?: PlaybackStateCompat.STATE_NONE

            if (isPlaying) {
                if (currentState != PlaybackStateCompat.STATE_PLAYING) {
                    updatePlaybackStateSupport(PlaybackStateCompat.STATE_PLAYING)
                }
                emitEvent(PlayerEvent.StateChanged(PlaybackStateCompat.STATE_PLAYING, videoId, true))
                checkForSponsorSegments()
            } else {
                if (exoPlayer.playbackState != Player.STATE_ENDED &&
                    exoPlayer.playbackState != Player.STATE_IDLE &&
                    currentState != PlaybackStateCompat.STATE_ERROR &&
                    currentState != PlaybackStateCompat.STATE_STOPPED) {
                    if (currentState != PlaybackStateCompat.STATE_PAUSED) {
                        updatePlaybackStateSupport(PlaybackStateCompat.STATE_PAUSED)
                    }
                    emitEvent(PlayerEvent.StateChanged(PlaybackStateCompat.STATE_PAUSED, videoId, false))
                }
            }
            updateNotificationIfPermitted()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)
            if (reason != Player.DISCONTINUITY_REASON_SEEK && exoPlayer.isPlaying) {
                checkForSponsorSegments()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Logger.e("ExoPlayer Error for $currentPlayingVideoId: ${error.errorCodeName} - ${error.message}", error)
            updatePlaybackStateSupport(PlaybackStateCompat.STATE_ERROR)
            emitEvent(PlayerEvent.Error(error.localizedMessage ?: "Unknown player error", currentPlayingVideoId))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i("$TAG Service Created")

        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, TAG)
        mediaSession.setCallback(MediaSessionCallback())

        initializePlayer()
    }

    private fun initializePlayer() {
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setEnableDecoderFallback(true)
        }
        val trackSelector = DefaultTrackSelector(this)
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
            this,
            DefaultHttpDataSource.Factory().setUserAgent("Yousify/1.0 (Android; ${Build.MODEL})")
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

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

        exoPlayer.addListener(playerListener)
    }

    fun playYoutubeAudio(youtubeVideoId: String, title: String, artist: String, thumbnailUrl: String? = null) {
        Logger.d("playYoutubeAudio called for YouTube Video ID: $youtubeVideoId, title: $title")

        currentPlayingVideoId = youtubeVideoId
        currentTrackTitle = title
        currentTrackArtist = artist
        currentThumbnailUrl = thumbnailUrl

        lifecycleScope.launch {
            try {
                if (exoPlayer.isPlaying || exoPlayer.isLoading) {
                    exoPlayer.stop()
                }
                exoPlayer.clearMediaItems()

                updateMediaSessionMetadata(title, artist, thumbnailUrl)

                sponsorSegments = emptyList()
                lastSkippedSegmentIndex = -1

                launch(Dispatchers.IO) {
                    fetchSponsorSegmentsForCurrentTrack()
                }

                val audioStreamUrl = withContext(Dispatchers.IO) {
                    NewPipeHelper.getBestAudioUrl(youtubeVideoId)
                }

                if (audioStreamUrl == null) {
                    Logger.e("Failed to get audio stream URL for videoId=$youtubeVideoId. Title: $title")
                    updatePlaybackStateSupport(PlaybackStateCompat.STATE_ERROR)
                    emitEvent(PlayerEvent.Error("Could not load audio stream.", youtubeVideoId))
                    stopSelfAndForeground()
                    return@launch
                }

                Logger.d("Obtained audio stream URL for $title: $audioStreamUrl")

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(audioStreamUrl))
                    .setMediaId(youtubeVideoId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(title)
                            .setArtist(artist)
                            .setArtworkUri(thumbnailUrl?.let { Uri.parse(it) })
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()

                mediaSession.isActive = true
                if (checkNotificationPermission()) {
                    startForeground(NOTIFICATION_ID, createNotification())
                } else {
                    Logger.w("Notification permission not granted, cannot start foreground service with notification immediately.")
                }

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } catch (e: Exception) {
                Logger.e("Error in playYoutubeAudio for $title (video ID: $youtubeVideoId)", e)
                updatePlaybackStateSupport(PlaybackStateCompat.STATE_ERROR)
                emitEvent(PlayerEvent.Error(e.localizedMessage ?: "Playback setup error", youtubeVideoId))
                stopSelfAndForeground()
            }
        }
    }

    private suspend fun fetchSponsorSegmentsForCurrentTrack() {
        currentPlayingVideoId?.let { videoId ->
            try {
                Logger.d("Fetching SponsorBlock segments for videoId: $videoId")
                val segmentsFromApi = SponsorBlockApi.getOrFetch(applicationContext, videoId)
                sponsorSegments = segmentsFromApi.map { (startSec, endSec) ->
                    (startSec * 1000).toLong() to (endSec * 1000).toLong()
                }.sortedBy { it.first }
                Logger.d("Loaded ${sponsorSegments.size} sponsor segments for $videoId.")
                emitEvent(PlayerEvent.SponsorSegmentsFetched(videoId, sponsorSegments.isNotEmpty()))

                if (exoPlayer.playbackState == Player.STATE_READY && exoPlayer.playWhenReady) {
                    withContext(Dispatchers.Main) { checkForSponsorSegments() }
                }
            } catch (e: Exception) {
                Logger.e("Error fetching SponsorBlock segments for $videoId", e)
                emitEvent(PlayerEvent.SponsorSegmentsFetched(videoId, false))
            }
        }
    }

    private fun checkForSponsorSegments() {
        if (!exoPlayer.isPlaying || sponsorSegments.isEmpty() || exoPlayer.currentMediaItem == null) return

        val currentPosition = exoPlayer.currentPosition

        for ((index, segment) in sponsorSegments.withIndex()) {
            val (startMs, endMs) = segment

            if (currentPosition >= startMs && currentPosition < endMs && index != lastSkippedSegmentIndex) {
                val skipToMs = endMs + SEGMENT_SKIP_PADDING_MS
                val duration = exoPlayer.duration
                if (duration != C.TIME_UNSET && skipToMs < duration) {
                    Logger.d("SponsorBlock: Skipping segment [${startMs}ms - ${endMs}ms] to ${skipToMs}ms for $currentPlayingVideoId")
                    exoPlayer.seekTo(skipToMs)
                    lastSkippedSegmentIndex = index
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (lastSkippedSegmentIndex == index) lastSkippedSegmentIndex = -1
                    }, 2000)
                } else if (duration == C.TIME_UNSET) {
                    Logger.d("SponsorBlock: Skipping segment (unknown duration) [${startMs}ms - ${endMs}ms] to ${skipToMs}ms for $currentPlayingVideoId")
                    exoPlayer.seekTo(skipToMs)
                    lastSkippedSegmentIndex = index
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (lastSkippedSegmentIndex == index) lastSkippedSegmentIndex = -1
                    }, 2000)
                }
                break
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Yousify Audio Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audio playback for Yousify"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val isPlayingNow = exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED

        val playPauseActionIcon = if (isPlayingNow) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseActionTitle = if (isPlayingNow) getString(R.string.pause) else getString(R.string.play)

        val playPauseIntentAction = if (isPlayingNow) ACTION_PAUSE else ACTION_PLAY
        val playPausePendingIntent = PendingIntent.getService(this, 1, Intent(this, YtAudioPlayer::class.java).setAction(playPauseIntentAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val playPauseAction = NotificationCompat.Action(playPauseActionIcon, playPauseActionTitle, playPausePendingIntent)

        val stopPendingIntent = PendingIntent.getService(this, 2, Intent(this, YtAudioPlayer::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val skipToNextPendingIntent = PendingIntent.getService(this, 3, Intent(this, YtAudioPlayer::class.java).setAction(SERVICE_ACTION_SKIP_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val skipToPreviousPendingIntent = PendingIntent.getService(this, 4, Intent(this, YtAudioPlayer::class.java).setAction(SERVICE_ACTION_SKIP_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val metadata = mediaSession.controller.metadata
        val title = metadata?.getString(SupportMediaMetadata.METADATA_KEY_TITLE) ?: currentTrackTitle
        val artist = metadata?.getString(SupportMediaMetadata.METADATA_KEY_ARTIST) ?: currentTrackArtist

        var albumArtBitmap: Bitmap? = metadata?.getBitmap(SupportMediaMetadata.METADATA_KEY_ALBUM_ART)
        if (albumArtBitmap == null) {
            try {
                albumArtBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_track_placeholder)
            } catch (e: Resources.NotFoundException) {
                Logger.e("$TAG: Placeholder drawable R.drawable.ic_track_placeholder not found.", e)
                try {
                    albumArtBitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher) // Иконка приложения как запасной вариант
                } catch (e2: Resources.NotFoundException) {
                    Logger.e("$TAG: App launcher icon R.mipmap.ic_launcher also not found.", e2)
                }
            }
        }

        val activityIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = if (activityIntent != null) {
            PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            mediaSession.controller.sessionActivity ?: PendingIntent.getService(this, 0, Intent(this, YtAudioPlayer::class.java), PendingIntent.FLAG_IMMUTABLE)
        }

        // ИСПРАВЛЕНО: Более надежное получение smallIcon
        val smallNotificationIcon = try {
            resources.getIdentifier("ic_notification_icon", "drawable", packageName).let {
                if (it != 0) it else R.drawable.ic_play // Ваш ic_play.xml в качестве запасного варианта, если ic_notification_icon нет
            }
        } catch (e: Exception) { // Широкий Exception, т.к. getIdentifier может не найти
            Logger.w("$TAG: ic_notification_icon not found, using ic_play as fallback for small icon.")
            R.drawable.ic_play // Ваш ic_play.xml
        }


        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(smallNotificationIcon)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_previous, getString(R.string.skip_previous), skipToPreviousPendingIntent))
            .addAction(playPauseAction)
            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_next, getString(R.string.skip_next), skipToNextPendingIntent))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(stopPendingIntent)
            )
            .setOngoing(isPlayingNow)

        albumArtBitmap?.let {
            builder.setLargeIcon(it)
        }

        return builder.build()
    }

    private fun updateNotificationIfPermitted() {
        if (currentPlayingVideoId != null && checkNotificationPermission()) {
            try {
                notificationManager.notify(NOTIFICATION_ID, createNotification())
            } catch (e: Exception) {
                Logger.e("$TAG: Error creating or updating notification", e)
                try {
                    // ИСПРАВЛЕНО: Запасное уведомление также должно использовать безопасную иконку
                    val fallbackSmallIcon = try {
                        resources.getIdentifier("ic_notification_icon", "drawable", packageName).let {
                            if (it != 0) it else R.drawable.ic_play
                        }
                    } catch (e: Exception) { R.drawable.ic_play }

                    val fallbackNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(fallbackSmallIcon)
                        .setContentTitle(currentTrackTitle ?: "Yousify")
                        .setContentText(currentTrackArtist ?: "Playing audio")
                        .setOngoing(exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED)
                        .build()
                    notificationManager.notify(NOTIFICATION_ID, fallbackNotification)
                } catch (fe: Exception) {
                    Logger.e("$TAG: Failed to create even a fallback notification", fe)
                }
            }
        } else if (currentPlayingVideoId != null) {
            Logger.w("$TAG: Cannot update notification: POST_NOTIFICATIONS permission not granted.")
        }
    }

    private fun updateMediaSessionMetadata(title: String, artist: String, thumbnailUrl: String?) {
        val durationMs = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: -1L
        val metadataBuilder = SupportMediaMetadata.Builder()
            .putString(SupportMediaMetadata.METADATA_KEY_TITLE, title)
            .putString(SupportMediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(SupportMediaMetadata.METADATA_KEY_ALBUM, artist)
            .putLong(SupportMediaMetadata.METADATA_KEY_DURATION, durationMs)

        if (thumbnailUrl != null) {
            lifecycleScope.launch {
                val bitmap = loadBitmapFromUrl(thumbnailUrl)
                val finalBitmap = bitmap ?: try {
                    BitmapFactory.decodeResource(resources, R.drawable.ic_track_placeholder)
                } catch (e: Resources.NotFoundException) {
                    Logger.e("$TAG: Placeholder for MediaSession metadata not found (after URL load fail).", e)
                    null
                }

                finalBitmap?.let { metadataBuilder.putBitmap(SupportMediaMetadata.METADATA_KEY_ALBUM_ART, it) }
                mediaSession.setMetadata(metadataBuilder.build())
                updateNotificationIfPermitted()
            }
        } else {
            try {
                val placeholderBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_track_placeholder)
                placeholderBitmap?.let { metadataBuilder.putBitmap(SupportMediaMetadata.METADATA_KEY_ALBUM_ART, it) }
            } catch (e: Resources.NotFoundException) {
                Logger.e("$TAG: Placeholder for MediaSession metadata not found (no URL).", e)
            }
            mediaSession.setMetadata(metadataBuilder.build())
            updateNotificationIfPermitted()
        }
    }


    private suspend fun loadBitmapFromUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doInput = true
            connection.connect()
            if (connection.responseCode == HttpsURLConnection.HTTP_OK) {
                BitmapFactory.decodeStream(connection.inputStream)
            } else {
                Logger.w("Failed to load bitmap from $url. Response code: ${connection.responseCode}")
                null
            }
        } catch (e: IOException) {
            Logger.e("IOException loading bitmap from URL: $url", e)
            null
        } catch (e: Exception) {
            Logger.e("Generic Exception loading bitmap from URL: $url", e)
            null
        }
    }

    private fun updatePlaybackStateSupport(state: Int) {
        val position = if (exoPlayer.currentMediaItem != null &&
            (exoPlayer.playbackState == Player.STATE_READY ||
                    exoPlayer.playbackState == Player.STATE_BUFFERING ||
                    exoPlayer.playbackState == Player.STATE_ENDED ||
                    state == PlaybackStateCompat.STATE_PAUSED))
            exoPlayer.currentPosition else C.TIME_UNSET
        val playbackSpeed = exoPlayer.playbackParameters.speed

        var availableActions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        availableActions = if (state == PlaybackStateCompat.STATE_PLAYING) {
            availableActions or PlaybackStateCompat.ACTION_PAUSE
        } else {
            availableActions or PlaybackStateCompat.ACTION_PLAY
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(availableActions)
            .setState(state, position, playbackSpeed, SystemClock.elapsedRealtime())

        mediaSession.setPlaybackState(stateBuilder.build())
    }

    fun play() {
        if (!exoPlayer.isPlaying) {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                if (exoPlayer.currentMediaItem != null) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.playWhenReady = true
                }
            } else {
                exoPlayer.playWhenReady = true
            }
        }
    }

    fun pause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.playWhenReady = false
        }
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Logger.d("$TAG: MediaSessionCallback: onPlay")
            this@YtAudioPlayer.play()
        }

        override fun onPause() {
            Logger.d("$TAG: MediaSessionCallback: onPause")
            this@YtAudioPlayer.pause()
        }

        override fun onStop() {
            Logger.d("$TAG: MediaSessionCallback: onStop")
            stopPlaybackAndService()
        }

        override fun onSeekTo(pos: Long) {
            Logger.d("$TAG: MediaSessionCallback: onSeekTo $pos")
            exoPlayer.seekTo(pos)
            if(exoPlayer.isPlaying) checkForSponsorSegments()
        }

        override fun onSkipToNext() {
            Logger.d("$TAG: MediaSessionCallback: onSkipToNext")
            emitEvent(PlayerEvent.MediaCommand(PlaybackStateCompat.ACTION_SKIP_TO_NEXT, currentPlayingVideoId))
        }

        override fun onSkipToPrevious() {
            Logger.d("$TAG: MediaSessionCallback: onSkipToPrevious")
            emitEvent(PlayerEvent.MediaCommand(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS, currentPlayingVideoId))
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        Logger.d("$TAG: Service Bound")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Logger.d("$TAG: Service Unbound")
        return super.onUnbind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        intent?.action?.let { action ->
            Logger.d("$TAG: onStartCommand received action: $action for $currentPlayingVideoId")
            when (action) {
                ACTION_PLAY -> {
                    if (currentPlayingVideoId != null || exoPlayer.currentMediaItem != null) {
                        play()
                    } else {
                        Logger.w("$TAG: ACTION_PLAY received but no track loaded to play.")
                    }
                }
                ACTION_PAUSE -> {
                    pause()
                }
                ACTION_STOP -> {
                    stopPlaybackAndService()
                }
                SERVICE_ACTION_SKIP_NEXT -> {
                    Logger.d("$TAG: Service handling SERVICE_ACTION_SKIP_NEXT via MediaSession")
                    mediaSession.controller.transportControls.skipToNext()
                }
                SERVICE_ACTION_SKIP_PREVIOUS -> {
                    Logger.d("$TAG: Service handling SERVICE_ACTION_SKIP_PREVIOUS via MediaSession")
                    mediaSession.controller.transportControls.skipToPrevious()
                }
            }
        }
        return START_STICKY
    }

    private fun stopPlaybackAndService() {
        Logger.d("Stopping playback and service for $currentPlayingVideoId")

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        mediaSession.isActive = false
        updatePlaybackStateSupport(PlaybackStateCompat.STATE_NONE)

        currentPlayingVideoId = null
        currentTrackTitle = "Yousify"
        currentTrackArtist = "Audio Player"
        currentThumbnailUrl = null
        sponsorSegments = emptyList()
        lastSkippedSegmentIndex = -1

        stopSelfAndForeground()
        emitEvent(PlayerEvent.StateChanged(PlaybackStateCompat.STATE_NONE, null, false))
    }

    private fun stopSelfAndForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        Logger.d("$TAG: Service stopped and removed from foreground.")
    }

    private fun emitEvent(event: PlayerEvent) {
        lifecycleScope.launch {
            _playerEvents.emit(event)
        }
    }

    private fun checkNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    override fun onDestroy() {
        Logger.d("$TAG Service Destroyed")
        if (this::exoPlayer.isInitialized) {
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        }
        if (this::mediaSession.isInitialized) {
            mediaSession.release()
        }
        super.onDestroy()
    }
}