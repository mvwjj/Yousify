package com.veshikov.yousify.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.veshikov.yousify.data.model.TrackEntity
import com.veshikov.yousify.data.model.YousifyDatabase
import com.veshikov.yousify.ui.components.MiniPlayerData
import com.veshikov.yousify.ui.components.MiniPlayerEvents
import com.veshikov.yousify.ui.components.MiniPlayerState
import com.veshikov.yousify.ui.components.MiniPlayerUiState
import com.veshikov.yousify.youtube.SearchEngine
import com.veshikov.yousify.ui.YousifyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import android.support.v4.media.session.PlaybackStateCompat

@androidx.media3.common.util.UnstableApi
class MiniPlayerController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewModel: YousifyViewModel,
    private val onRequirePermissions: (Array<String>) -> Unit
) : MiniPlayerEvents {

    private val TAG = "MiniPlayerController"
    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + job)
    internal val uiState = MiniPlayerUiState() // Сделаем internal, чтобы ViewModel не имел прямого доступа

    private var audioPlayer: YtAudioPlayer? = null
    private var serviceConnection: ServiceConnection? = null
    private var isServiceBound = false

    private var trackForPlayerServiceWithYtId: TrackEntity? = null // Хранит TrackEntity, где id - это YouTube ID
    private var currentPlayingSpotifyId: String? = null // Spotify ID трека, который сейчас должен играть

    // Эти StateFlows наблюдаются из MainScreen
    val miniPlayerState get() = uiState.getStateFlow()
    val miniPlayerData get() = uiState.getDataFlow()
    val isExpanded get() = uiState.getExpandedFlow()

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        arrayOf(android.Manifest.permission.FOREGROUND_SERVICE)
    } else {
        emptyArray()
    }

    init {
        lifecycleOwner.lifecycleScope.launch {
            viewModel.playbackCommand.collectLatest { command ->
                Log.d(TAG, "Received PlaybackCommand from ViewModel: $command")
                when (command) {
                    is YousifyViewModel.PlaybackCommand.PlayTrack -> {
                        // ViewModel уже установил контекст плейлиста и текущий трек (_currentTrack)
                        // command.track - это трек, который нужно играть (с Spotify ID)
                        // command.playlistContext - контекст плейлиста
                        playTrackInternal(command.track, command.playlistContext, true)
                    }
                    is YousifyViewModel.PlaybackCommand.UpdateSponsorBlockStatus -> {
                        val currentData = uiState.getDataFlow().value
                        // currentPlayingSpotifyId - это Spotify ID текущего трека
                        if (currentData != null && currentData.trackId == currentPlayingSpotifyId && command.videoId == trackForPlayerServiceWithYtId?.id) {
                            uiState.update(newData = currentData.copy(hasSponsorBlockSegments = command.hasSegments))
                        }
                    }
                    is YousifyViewModel.PlaybackCommand.RequestPause -> {
                        if (isServiceBound && audioPlayer != null) {
                            audioPlayer?.pause()
                        } else {
                            Log.w(TAG, "RequestPause: Service not bound or player not available.")
                        }
                    }
                    is YousifyViewModel.PlaybackCommand.RequestResume -> {
                        if (isServiceBound && audioPlayer != null) {
                            // Убедимся, что есть что возобновлять
                            if (trackForPlayerServiceWithYtId != null) {
                                audioPlayer?.play()
                            } else {
                                // Если нет trackForPlayerServiceWithYtId, но есть currentTrack в ViewModel,
                                // значит, нужно перезапустить его.
                                viewModel.currentTrack.value?.let { trackToResume ->
                                    Log.w(TAG, "RequestResume: No YT track loaded, re-initiating for ${trackToResume.title}")
                                    val contextPlaylist = viewModel.currentPlaylist.value
                                    playTrackInternal(trackToResume, contextPlaylist, true)
                                }
                            }
                        } else {
                            Log.w(TAG, "RequestResume: Service not bound or player not available.")
                            // Попытка перезапуска, если есть трек в ViewModel
                            viewModel.currentTrack.value?.let { trackToResume ->
                                val contextPlaylist = viewModel.currentPlaylist.value
                                playTrackInternal(trackToResume, contextPlaylist, true)
                            }
                        }
                    }
                }
            }
        }

        coroutineScope.launch {
            YtAudioPlayer.playerEvents.collectLatest { event ->
                Log.d(TAG, "Received PlayerEvent: $event from YtAudioPlayer")
                // currentPlayingSpotifyId - Spotify ID трека, который мы ожидаем
                // trackForPlayerServiceWithYtId?.id - YouTube ID трека, который мы ожидаем

                when (event) {
                    is YtAudioPlayer.PlayerEvent.Error -> {
                        if (event.videoId == trackForPlayerServiceWithYtId?.id) {
                            Log.e(TAG, "Error from YtAudioPlayer for video ${event.videoId}: ${event.message}")
                            uiState.errorPlaying(event.message ?: "Unknown player error")
                        }
                    }
                    is YtAudioPlayer.PlayerEvent.StateChanged -> {
                        if (event.videoId == trackForPlayerServiceWithYtId?.id) {
                            Log.d(TAG, "StateChanged for current YT ID ${event.videoId}: ${event.playbackState}, isPlaying: ${event.isPlaying}")
                            when (event.playbackState) {
                                PlaybackStateCompat.STATE_PLAYING -> uiState.update(newState = MiniPlayerState.PLAYING)
                                PlaybackStateCompat.STATE_PAUSED -> uiState.update(newState = MiniPlayerState.PAUSED)
                                PlaybackStateCompat.STATE_BUFFERING -> uiState.update(newState = MiniPlayerState.LOADING)
                                PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.STATE_NONE -> {
                                    if (uiState.getStateFlow().value != MiniPlayerState.HIDDEN) {
                                        // Если сервис остановился не по команде onClose, показываем как паузу
                                        // Если это STATE_ENDED, ViewModel должна была инициировать следующий трек.
                                        // Если просто STOPPED/NONE и не конец трека, это может быть ошибка или внешняя остановка.
                                        // Для простоты покажем PAUSED, если не HIDDEN.
                                        if (event.playbackState == PlaybackStateCompat.STATE_STOPPED && !event.isPlaying && viewModel.currentTrack.value != null){
                                            // Это может быть конец трека, ViewModel обработает skip.
                                        } else if (viewModel.currentTrack.value != null) {
                                            uiState.update(newState = MiniPlayerState.PAUSED)
                                        }
                                    }
                                }
                                PlaybackStateCompat.STATE_ERROR -> uiState.errorPlaying("Playback error")
                            }
                        } else {
                            Log.d(TAG, "StateChanged event for non-current/expected video: ${event.videoId} (current expected YT ID: ${trackForPlayerServiceWithYtId?.id})")
                        }
                    }
                    is YtAudioPlayer.PlayerEvent.MediaCommand -> {
                        if (event.videoId == trackForPlayerServiceWithYtId?.id) {
                            when (event.command) {
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT -> {
                                    Log.d(TAG, "MediaCommand: Skip to Next received for YT ID ${event.videoId}")
                                    viewModel.skipToNextTrack()
                                }
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS -> {
                                    Log.d(TAG, "MediaCommand: Skip to Previous received for YT ID ${event.videoId}")
                                    viewModel.skipToPreviousTrack()
                                }
                            }
                        } else {
                            Log.d(TAG, "MediaCommand event for non-current/expected video: ${event.videoId}, expected: ${trackForPlayerServiceWithYtId?.id}")
                        }
                    }
                    is YtAudioPlayer.PlayerEvent.SponsorSegmentsFetched -> {
                        val currentData = uiState.getDataFlow().value
                        if (currentData != null && currentData.trackId == currentPlayingSpotifyId && event.videoId == trackForPlayerServiceWithYtId?.id) {
                            uiState.update(newData = currentData.copy(hasSponsorBlockSegments = event.hasSegments))
                            viewModel.updateSponsorBlockInfo(event.videoId, event.hasSegments) // Сообщаем ViewModel
                        }
                    }
                }
            }
        }
    }

    // trackWithYouTubeId: TrackEntity, где id - это YouTube ID
    // originalSpotifyId: Spotify ID этого трека
    private fun ensureServiceBoundAndPlay(trackWithYouTubeId: TrackEntity, originalSpotifyId: String) {
        Log.d(TAG, "ensureServiceBoundAndPlay for YouTube ID: ${trackWithYouTubeId.id}, Spotify ID: $originalSpotifyId")
        this.trackForPlayerServiceWithYtId = trackWithYouTubeId
        this.currentPlayingSpotifyId = originalSpotifyId

        if (!checkAndRequestPermissions()) {
            Log.w(TAG, "Required permissions not granted. Playback aborted.")
            uiState.errorPlaying("Permissions required.")
            return
        }

        if (isServiceBound && audioPlayer != null) {
            Log.d(TAG, "Service already bound. Playing track (YT ID): ${trackWithYouTubeId.id}, title: ${trackWithYouTubeId.title}")
            audioPlayer?.playYoutubeAudio(trackWithYouTubeId.id, trackWithYouTubeId.title, trackWithYouTubeId.artist, null)
        } else if (!isServiceBound) {
            val intent = Intent(context, YtAudioPlayer::class.java)
            Log.d(TAG, "Attempting to start and bind YtAudioPlayer service.")
            try {
                context.startService(intent) // Можно использовать ContextCompat.startForegroundService для API 26+
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start YtAudioPlayer service: ${e.message}", e)
                uiState.errorPlaying("Cannot start player service.")
                return
            }

            serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    Log.d(TAG, "YtAudioPlayer service connected.")
                    audioPlayer = (binder as YtAudioPlayer.LocalBinder).getService()
                    isServiceBound = true

                    val pendingTrack = this@MiniPlayerController.trackForPlayerServiceWithYtId
                    if (pendingTrack != null) {
                        Log.d(TAG, "Service connected. Playing pending track (YT ID): ${pendingTrack.id}, title: ${pendingTrack.title}")
                        audioPlayer?.playYoutubeAudio(pendingTrack.id, pendingTrack.title, pendingTrack.artist, null)
                    } else {
                        Log.w(TAG, "Service connected, but no pending track to play (trackForPlayerServiceWithYtId is null).")
                    }
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    Log.w(TAG, "YtAudioPlayer service disconnected unexpectedly.")
                    isServiceBound = false
                    audioPlayer = null
                    if (viewModel.currentTrack.value != null && uiState.getStateFlow().value != MiniPlayerState.HIDDEN) {
                        uiState.update(newState = MiniPlayerState.ERROR, newData = uiState.getDataFlow().value?.copy(errorMessage = "Player service disconnected"))
                    } else if (uiState.getStateFlow().value != MiniPlayerState.HIDDEN) {
                        // uiState.hide() // Не скрываем, если не было команды
                    }
                }
            }
            try {
                val bindSuccess = context.bindService(intent, serviceConnection!!, Context.BIND_AUTO_CREATE)
                if (!bindSuccess) {
                    Log.e(TAG, "context.bindService for YtAudioPlayer returned false.")
                    uiState.errorPlaying("Could not connect to player service.")
                    this@MiniPlayerController.trackForPlayerServiceWithYtId = null
                    this@MiniPlayerController.currentPlayingSpotifyId = null
                } else {
                    Log.d(TAG, "Successfully initiated YtAudioPlayer service binding.")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException on bindService for YtAudioPlayer: ${e.message}", e)
                uiState.errorPlaying("Permission error connecting to player.")
                this@MiniPlayerController.trackForPlayerServiceWithYtId = null
                this@MiniPlayerController.currentPlayingSpotifyId = null
            }
        }
    }

    // trackFromContext: TrackEntity с Spotify ID в поле 'id'
    // playlistContext: полный список треков плейлиста (TrackEntity с Spotify ID)
    private fun playTrackInternal(trackFromContext: TrackEntity, playlistContext: List<TrackEntity>?, isFromViewModel: Boolean) {
        Log.d(TAG, "Internal play: Spotify ID ${trackFromContext.id}, Title: ${trackFromContext.title}, FromViewModel: $isFromViewModel, PlaylistContext size: ${playlistContext?.size}")

        val spotifyId = trackFromContext.id

        val trackDataForUi = MiniPlayerData(
            trackId = spotifyId,
            title = trackFromContext.title,
            artist = trackFromContext.artist,
            imageUrl = null,
            hasSponsorBlockSegments = false
        )
        uiState.startLoading(trackDataForUi) // Показываем загрузку

        // Очищаем предыдущий трек для плеера, новый будет установлен после поиска
        this.trackForPlayerServiceWithYtId = null
        this.currentPlayingSpotifyId = spotifyId // Устанавливаем Spotify ID трека, который пытаемся играть

        coroutineScope.launch {
            try {
                val ytResult = withContext(Dispatchers.IO) { findYouTubeVideo(trackFromContext) }

                if (ytResult == null || ytResult.videoId.isBlank()) {
                    Log.e(TAG, "Track not found on YouTube for Spotify ID: ${trackFromContext.id}")
                    uiState.errorPlaying("Track not found on YouTube")
                    this@MiniPlayerController.currentPlayingSpotifyId = null // Сброс
                    return@launch
                }

                Log.d(TAG, "YouTube video found: ${ytResult.videoId} for Spotify track: ${trackFromContext.title}")

                val trackForService = TrackEntity( // Это TrackEntity для сервиса, где id = YouTube ID
                    id = ytResult.videoId,
                    playlistId = trackFromContext.playlistId,
                    title = trackFromContext.title,
                    artist = trackFromContext.artist,
                    isrc = trackFromContext.isrc,
                    durationMs = trackFromContext.durationMs
                )
                // Передаем trackForService (с YT ID) и оригинальный spotifyId
                ensureServiceBoundAndPlay(trackForService, spotifyId)

            } catch (e: SecurityException) {
                Log.e(TAG, "Missing permissions during playback setup", e)
                uiState.errorPlaying("Missing permissions")
                onRequirePermissions(requiredPermissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray())
                this@MiniPlayerController.currentPlayingSpotifyId = null
            } catch (e: IOException) {
                Log.e(TAG, "Network error during playback setup", e)
                uiState.errorPlaying("Network error: ${e.localizedMessage ?: "Unknown network error"}")
                this@MiniPlayerController.currentPlayingSpotifyId = null
            } catch (e: Exception) {
                Log.e(TAG, "General error during playback setup for ${trackFromContext.title}", e)
                uiState.errorPlaying("Error: ${e.localizedMessage ?: "Unknown error"}")
                this@MiniPlayerController.currentPlayingSpotifyId = null
            }
        }
    }

    private suspend fun findYouTubeVideo(trackWithSpotifyId: TrackEntity): YtTrackResult? {
        val db = YousifyDatabase.getInstance(context)
        val cacheDao = db.ytTrackCacheDao()

        val cachedEntry = cacheDao.getBySpotifyId(trackWithSpotifyId.id) // id здесь Spotify ID
        if (cachedEntry != null && cachedEntry.videoId.isNotBlank()) {
            Log.d(TAG, "Found cached YouTube videoId: ${cachedEntry.videoId} for Spotify ID: ${trackWithSpotifyId.id}")
            return YtTrackResult(cachedEntry.videoId, cachedEntry.score)
        }

        Log.d(TAG, "Searching YouTube for Spotify track: ${trackWithSpotifyId.title} (Spotify ID: ${trackWithSpotifyId.id})")
        val searchResultEntity = SearchEngine.findBestYoutube(trackWithSpotifyId, context)

        return if (searchResultEntity != null && searchResultEntity.videoId.isNotBlank()) {
            Log.d(TAG, "SearchEngine found YouTube videoId: ${searchResultEntity.videoId} for Spotify ID: ${trackWithSpotifyId.id}")
            // searchResultEntity.spotifyId должен быть trackWithSpotifyId.id
            val entityToCache = searchResultEntity.copy(spotifyId = trackWithSpotifyId.id)
            cacheDao.insert(entityToCache)
            Log.d(TAG, "Cached search result in YousifyDatabase for Spotify ID: ${trackWithSpotifyId.id} -> YT ID: ${entityToCache.videoId}")
            YtTrackResult(entityToCache.videoId, entityToCache.score)
        } else {
            Log.w(TAG, "SearchEngine found no YouTube video for Spotify track ${trackWithSpotifyId.title}")
            null
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            Log.w(TAG, "Requesting missing permissions: ${permissionsToRequest.joinToString()}")
            onRequirePermissions(permissionsToRequest)
            return false
        }
        return true
    }

    fun release() {
        Log.d(TAG, "Releasing MiniPlayerController resources")
        job.cancel()
        serviceConnection?.let {
            try {
                if (isServiceBound) {
                    context.unbindService(it)
                    Log.d(TAG, "YtAudioPlayer service unbound.")
                }
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Service not registered or already unbound on release: ${e.message}")
            }
        }
        audioPlayer = null
        isServiceBound = false
        trackForPlayerServiceWithYtId = null
        currentPlayingSpotifyId = null
        Log.d(TAG, "MiniPlayerController released.")
    }

    // --- MiniPlayerEvents Implementation ---
    override fun onPlayPause() {
        val currentState = uiState.getStateFlow().value
        Log.d(TAG, "onPlayPause called by UI. Current MiniPlayerState: $currentState")

        if (currentState == MiniPlayerState.PLAYING) {
            viewModel.pauseCurrentTrack()
        } else { // PAUSED, ERROR, LOADING (если был трек), etc.
            // Если есть текущий трек в ViewModel, пытаемся его возобновить/запустить
            viewModel.currentTrack.value?.let {
                Log.d(TAG, "Requesting RESUME for track via ViewModel: ${it.title}")
                viewModel.resumeCurrentTrack() // ViewModel пошлет команду RequestResume
            } ?: run {
                Log.w(TAG, "onPlayPause: No current track in ViewModel to resume/play.")
                uiState.errorPlaying("No track to play.")
            }
        }
    }

    override fun onSkipNext() {
        Log.d(TAG, "onSkipNext called by UI. Requesting next track from ViewModel.")
        viewModel.skipToNextTrack()
    }

    override fun onSkipPrevious() {
        Log.d(TAG, "onSkipPrevious called by UI. Requesting previous track from ViewModel.")
        viewModel.skipToPreviousTrack()
    }

    override fun onClose() {
        Log.d(TAG, "onClose called by UI. Stopping service and hiding UI.")
        if (isServiceBound && audioPlayer != null) {
            context.startService(Intent(context, YtAudioPlayer::class.java).setAction(YtAudioPlayer.ACTION_STOP))
        } else {
            // Если сервис не был связан, просто скрываем UI
            Log.w(TAG, "onClose: Service not bound, just hiding UI and clearing state.")
        }
        uiState.hide()
        viewModel.clearPlaybackState()
        trackForPlayerServiceWithYtId = null
        currentPlayingSpotifyId = null
    }

    override fun onExpand() {
        uiState.toggleExpanded()
    }

    override fun onRetry() {
        Log.d(TAG, "onRetry called by UI.")
        viewModel.currentTrack.value?.let { trackToRetry ->
            Log.d(TAG, "Retrying track via ViewModel: ${trackToRetry.title}")
            val currentPlaylistContext = viewModel.currentPlaylist.value
            // Передаем команду на воспроизведение в ViewModel
            viewModel.playTrackInContext(trackToRetry, currentPlaylistContext.ifEmpty { listOf(trackToRetry) })
        } ?: run {
            Log.w(TAG, "Retry called but no current track in ViewModel to retry.")
            uiState.errorPlaying("No track to retry.")
        }
    }

    private data class YtTrackResult(val videoId: String, val score: Float)
}