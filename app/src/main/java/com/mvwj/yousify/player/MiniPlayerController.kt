package com.mvwj.yousify.player

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
import com.mvwj.yousify.data.model.TrackEntity
import com.mvwj.yousify.data.model.YousifyDatabase
import com.mvwj.yousify.ui.components.MiniPlayerData
import com.mvwj.yousify.ui.components.MiniPlayerEvents
import com.mvwj.yousify.ui.components.MiniPlayerState
import com.mvwj.yousify.ui.components.MiniPlayerUiState
import com.mvwj.yousify.youtube.SearchEngine
import com.mvwj.yousify.ui.YousifyViewModel
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
    internal val uiState = MiniPlayerUiState() // Ð¡Ð´ÐµÐ»Ð°ÐµÐ¼ internal, Ñ‡Ñ‚Ð¾Ð±Ñ‹ ViewModel Ð½Ðµ Ð¸Ð¼ÐµÐ» Ð¿Ñ€ÑÐ¼Ð¾Ð³Ð¾ Ð´Ð¾ÑÑ‚ÑƒÐ¿Ð°

    private var audioPlayer: YtAudioPlayer? = null
    private var serviceConnection: ServiceConnection? = null
    private var isServiceBound = false

    private var trackForPlayerServiceWithYtId: TrackEntity? = null // Ð¥Ñ€Ð°Ð½Ð¸Ñ‚ TrackEntity, Ð³Ð´Ðµ id - ÑÑ‚Ð¾ YouTube ID
    private var currentPlayingSpotifyId: String? = null // Spotify ID Ñ‚Ñ€ÐµÐºÐ°, ÐºÐ¾Ñ‚Ð¾Ñ€Ñ‹Ð¹ ÑÐµÐ¹Ñ‡Ð°Ñ Ð´Ð¾Ð»Ð¶ÐµÐ½ Ð¸Ð³Ñ€Ð°Ñ‚ÑŒ

    // Ð­Ñ‚Ð¸ StateFlows Ð½Ð°Ð±Ð»ÑŽÐ´Ð°ÑŽÑ‚ÑÑ Ð¸Ð· MainScreen
    val miniPlayerState get() = uiState.getStateFlow()
    val miniPlayerData get() = uiState.getDataFlow()
    val isExpanded get() = uiState.getExpandedFlow()

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    init {
        lifecycleOwner.lifecycleScope.launch {
            viewModel.playbackCommand.collectLatest { command ->
                Log.d(TAG, "Received PlaybackCommand from ViewModel: $command")
                when (command) {
                    is YousifyViewModel.PlaybackCommand.PlayTrack -> {
                        // ViewModel ÑƒÐ¶Ðµ ÑƒÑÑ‚Ð°Ð½Ð¾Ð²Ð¸Ð» ÐºÐ¾Ð½Ñ‚ÐµÐºÑÑ‚ Ð¿Ð»ÐµÐ¹Ð»Ð¸ÑÑ‚Ð° Ð¸ Ñ‚ÐµÐºÑƒÑ‰Ð¸Ð¹ Ñ‚Ñ€ÐµÐº (_currentTrack)
                        // command.track - ÑÑ‚Ð¾ Ñ‚Ñ€ÐµÐº, ÐºÐ¾Ñ‚Ð¾Ñ€Ñ‹Ð¹ Ð½ÑƒÐ¶Ð½Ð¾ Ð¸Ð³Ñ€Ð°Ñ‚ÑŒ (Ñ Spotify ID)
                        // command.playlistContext - ÐºÐ¾Ð½Ñ‚ÐµÐºÑÑ‚ Ð¿Ð»ÐµÐ¹Ð»Ð¸ÑÑ‚Ð°
                        playTrackInternal(command.track, command.playlistContext, true)
                    }
                    is YousifyViewModel.PlaybackCommand.UpdateSponsorBlockStatus -> {
                        val currentData = uiState.getDataFlow().value
                        // currentPlayingSpotifyId - ÑÑ‚Ð¾ Spotify ID Ñ‚ÐµÐºÑƒÑ‰ÐµÐ³Ð¾ Ñ‚Ñ€ÐµÐºÐ°
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
                            // Ð£Ð±ÐµÐ´Ð¸Ð¼ÑÑ, Ñ‡Ñ‚Ð¾ ÐµÑÑ‚ÑŒ Ñ‡Ñ‚Ð¾ Ð²Ð¾Ð·Ð¾Ð±Ð½Ð¾Ð²Ð»ÑÑ‚ÑŒ
                            if (trackForPlayerServiceWithYtId != null) {
                                audioPlayer?.play()
                            } else {
                                // Ð•ÑÐ»Ð¸ Ð½ÐµÑ‚ trackForPlayerServiceWithYtId, Ð½Ð¾ ÐµÑÑ‚ÑŒ currentTrack Ð² ViewModel,
                                // Ð·Ð½Ð°Ñ‡Ð¸Ñ‚, Ð½ÑƒÐ¶Ð½Ð¾ Ð¿ÐµÑ€ÐµÐ·Ð°Ð¿ÑƒÑÑ‚Ð¸Ñ‚ÑŒ ÐµÐ³Ð¾.
                                viewModel.currentTrack.value?.let { trackToResume ->
                                    Log.w(TAG, "RequestResume: No YT track loaded, re-initiating for ${trackToResume.title}")
                                    val contextPlaylist = viewModel.currentPlaylist.value
                                    playTrackInternal(trackToResume, contextPlaylist, true)
                                }
                            }
                        } else {
                            Log.w(TAG, "RequestResume: Service not bound or player not available.")
                            // ÐŸÐ¾Ð¿Ñ‹Ñ‚ÐºÐ° Ð¿ÐµÑ€ÐµÐ·Ð°Ð¿ÑƒÑÐºÐ°, ÐµÑÐ»Ð¸ ÐµÑÑ‚ÑŒ Ñ‚Ñ€ÐµÐº Ð² ViewModel
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
                // currentPlayingSpotifyId - Spotify ID Ñ‚Ñ€ÐµÐºÐ°, ÐºÐ¾Ñ‚Ð¾Ñ€Ñ‹Ð¹ Ð¼Ñ‹ Ð¾Ð¶Ð¸Ð´Ð°ÐµÐ¼
                // trackForPlayerServiceWithYtId?.id - YouTube ID Ñ‚Ñ€ÐµÐºÐ°, ÐºÐ¾Ñ‚Ð¾Ñ€Ñ‹Ð¹ Ð¼Ñ‹ Ð¾Ð¶Ð¸Ð´Ð°ÐµÐ¼

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
                                        // Ð•ÑÐ»Ð¸ ÑÐµÑ€Ð²Ð¸Ñ Ð¾ÑÑ‚Ð°Ð½Ð¾Ð²Ð¸Ð»ÑÑ Ð½Ðµ Ð¿Ð¾ ÐºÐ¾Ð¼Ð°Ð½Ð´Ðµ onClose, Ð¿Ð¾ÐºÐ°Ð·Ñ‹Ð²Ð°ÐµÐ¼ ÐºÐ°Ðº Ð¿Ð°ÑƒÐ·Ñƒ
                                        // Ð•ÑÐ»Ð¸ ÑÑ‚Ð¾ STATE_ENDED, ViewModel Ð´Ð¾Ð»Ð¶Ð½Ð° Ð±Ñ‹Ð»Ð° Ð¸Ð½Ð¸Ñ†Ð¸Ð¸Ñ€Ð¾Ð²Ð°Ñ‚ÑŒ ÑÐ»ÐµÐ´ÑƒÑŽÑ‰Ð¸Ð¹ Ñ‚Ñ€ÐµÐº.
                                        // Ð•ÑÐ»Ð¸ Ð¿Ñ€Ð¾ÑÑ‚Ð¾ STOPPED/NONE Ð¸ Ð½Ðµ ÐºÐ¾Ð½ÐµÑ† Ñ‚Ñ€ÐµÐºÐ°, ÑÑ‚Ð¾ Ð¼Ð¾Ð¶ÐµÑ‚ Ð±Ñ‹Ñ‚ÑŒ Ð¾ÑˆÐ¸Ð±ÐºÐ° Ð¸Ð»Ð¸ Ð²Ð½ÐµÑˆÐ½ÑÑ Ð¾ÑÑ‚Ð°Ð½Ð¾Ð²ÐºÐ°.
                                        // Ð”Ð»Ñ Ð¿Ñ€Ð¾ÑÑ‚Ð¾Ñ‚Ñ‹ Ð¿Ð¾ÐºÐ°Ð¶ÐµÐ¼ PAUSED, ÐµÑÐ»Ð¸ Ð½Ðµ HIDDEN.
                                        if (event.playbackState == PlaybackStateCompat.STATE_STOPPED && !event.isPlaying && viewModel.currentTrack.value != null){
                                            // Ð­Ñ‚Ð¾ Ð¼Ð¾Ð¶ÐµÑ‚ Ð±Ñ‹Ñ‚ÑŒ ÐºÐ¾Ð½ÐµÑ† Ñ‚Ñ€ÐµÐºÐ°, ViewModel Ð¾Ð±Ñ€Ð°Ð±Ð¾Ñ‚Ð°ÐµÑ‚ skip.
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
                    is YtAudioPlayer.PlayerEvent.Progress -> {
                        if (event.videoId == trackForPlayerServiceWithYtId?.id) {
                            val currentData = uiState.getDataFlow().value ?: return@collectLatest
                            uiState.update(
                                newData = currentData.copy(
                                    currentPositionMs = event.positionMs.coerceAtLeast(0L),
                                    durationMs = event.durationMs.takeIf { it > 0 } ?: currentData.durationMs
                                )
                            )
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
                            viewModel.updateSponsorBlockInfo(event.videoId, event.hasSegments) // Ð¡Ð¾Ð¾Ð±Ñ‰Ð°ÐµÐ¼ ViewModel
                        }
                    }
                }
            }
        }
    }

    // trackWithYouTubeId: TrackEntity, Ð³Ð´Ðµ id - ÑÑ‚Ð¾ YouTube ID
    // originalSpotifyId: Spotify ID ÑÑ‚Ð¾Ð³Ð¾ Ñ‚Ñ€ÐµÐºÐ°
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
            audioPlayer?.playYoutubeAudio(
                youtubeVideoId = trackWithYouTubeId.id,
                title = trackWithYouTubeId.title,
                artist = trackWithYouTubeId.artist,
                thumbnailUrl = null,
                spotifyId = originalSpotifyId
            )
        } else if (!isServiceBound) {
            val intent = Intent(context, YtAudioPlayer::class.java)
            Log.d(TAG, "Attempting to start and bind YtAudioPlayer service.")
            try {
                context.startService(intent) // ÐœÐ¾Ð¶Ð½Ð¾ Ð¸ÑÐ¿Ð¾Ð»ÑŒÐ·Ð¾Ð²Ð°Ñ‚ÑŒ ContextCompat.startForegroundService Ð´Ð»Ñ API 26+
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
                        audioPlayer?.playYoutubeAudio(
                            youtubeVideoId = pendingTrack.id,
                            title = pendingTrack.title,
                            artist = pendingTrack.artist,
                            thumbnailUrl = null,
                            spotifyId = this@MiniPlayerController.currentPlayingSpotifyId
                        )
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
                        // uiState.hide() // ÐÐµ ÑÐºÑ€Ñ‹Ð²Ð°ÐµÐ¼, ÐµÑÐ»Ð¸ Ð½Ðµ Ð±Ñ‹Ð»Ð¾ ÐºÐ¾Ð¼Ð°Ð½Ð´Ñ‹
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

    // trackFromContext: TrackEntity Ñ Spotify ID Ð² Ð¿Ð¾Ð»Ðµ 'id'
    // playlistContext: Ð¿Ð¾Ð»Ð½Ñ‹Ð¹ ÑÐ¿Ð¸ÑÐ¾Ðº Ñ‚Ñ€ÐµÐºÐ¾Ð² Ð¿Ð»ÐµÐ¹Ð»Ð¸ÑÑ‚Ð° (TrackEntity Ñ Spotify ID)
    private fun playTrackInternal(trackFromContext: TrackEntity, playlistContext: List<TrackEntity>?, isFromViewModel: Boolean) {
        Log.d(TAG, "Internal play: Spotify ID ${trackFromContext.id}, Title: ${trackFromContext.title}, FromViewModel: $isFromViewModel, PlaylistContext size: ${playlistContext?.size}")

        val spotifyId = trackFromContext.id

        val trackDataForUi = MiniPlayerData(
            trackId = spotifyId,
            title = trackFromContext.title,
            artist = trackFromContext.artist,
            imageUrl = trackFromContext.imageUrl,
            currentPositionMs = 0L,
            durationMs = trackFromContext.durationMs,
            hasSponsorBlockSegments = false
        )
        uiState.startLoading(trackDataForUi) // ÐŸÐ¾ÐºÐ°Ð·Ñ‹Ð²Ð°ÐµÐ¼ Ð·Ð°Ð³Ñ€ÑƒÐ·ÐºÑƒ

        // ÐžÑ‡Ð¸Ñ‰Ð°ÐµÐ¼ Ð¿Ñ€ÐµÐ´Ñ‹Ð´ÑƒÑ‰Ð¸Ð¹ Ñ‚Ñ€ÐµÐº Ð´Ð»Ñ Ð¿Ð»ÐµÐµÑ€Ð°, Ð½Ð¾Ð²Ñ‹Ð¹ Ð±ÑƒÐ´ÐµÑ‚ ÑƒÑÑ‚Ð°Ð½Ð¾Ð²Ð»ÐµÐ½ Ð¿Ð¾ÑÐ»Ðµ Ð¿Ð¾Ð¸ÑÐºÐ°
        this.trackForPlayerServiceWithYtId = null
        this.currentPlayingSpotifyId = spotifyId // Ð£ÑÑ‚Ð°Ð½Ð°Ð²Ð»Ð¸Ð²Ð°ÐµÐ¼ Spotify ID Ñ‚Ñ€ÐµÐºÐ°, ÐºÐ¾Ñ‚Ð¾Ñ€Ñ‹Ð¹ Ð¿Ñ‹Ñ‚Ð°ÐµÐ¼ÑÑ Ð¸Ð³Ñ€Ð°Ñ‚ÑŒ

        coroutineScope.launch {
            try {
                val ytResult = withContext(Dispatchers.IO) { findYouTubeVideo(trackFromContext) }

                if (ytResult == null || ytResult.videoId.isBlank()) {
                    Log.e(TAG, "Track not found on YouTube for Spotify ID: ${trackFromContext.id}")
                    uiState.errorPlaying("Track not found on YouTube")
                    this@MiniPlayerController.currentPlayingSpotifyId = null // Ð¡Ð±Ñ€Ð¾Ñ
                    return@launch
                }

                Log.d(TAG, "YouTube video found: ${ytResult.videoId} for Spotify track: ${trackFromContext.title}")

                val trackForService = TrackEntity( // Ð­Ñ‚Ð¾ TrackEntity Ð´Ð»Ñ ÑÐµÑ€Ð²Ð¸ÑÐ°, Ð³Ð´Ðµ id = YouTube ID
                    playlistId = trackFromContext.playlistId,
                    position = trackFromContext.position,
                    id = ytResult.videoId,
                    title = trackFromContext.title,
                    artist = trackFromContext.artist,
                    imageUrl = trackFromContext.imageUrl,
                    isrc = trackFromContext.isrc,
                    durationMs = trackFromContext.durationMs
                )
                // ÐŸÐµÑ€ÐµÐ´Ð°ÐµÐ¼ trackForService (Ñ YT ID) Ð¸ Ð¾Ñ€Ð¸Ð³Ð¸Ð½Ð°Ð»ÑŒÐ½Ñ‹Ð¹ spotifyId
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

        val cachedEntry = cacheDao.getBySpotifyId(trackWithSpotifyId.id) // id Ð·Ð´ÐµÑÑŒ Spotify ID
        if (cachedEntry != null && cachedEntry.videoId.isNotBlank()) {
            Log.d(TAG, "Found cached YouTube videoId: ${cachedEntry.videoId} for Spotify ID: ${trackWithSpotifyId.id}")
            return YtTrackResult(cachedEntry.videoId, cachedEntry.score)
        }

        Log.d(TAG, "Searching YouTube for Spotify track: ${trackWithSpotifyId.title} (Spotify ID: ${trackWithSpotifyId.id})")
        val searchResultEntity = SearchEngine.findBestYoutube(trackWithSpotifyId, context)

        return if (searchResultEntity != null && searchResultEntity.videoId.isNotBlank()) {
            Log.d(TAG, "SearchEngine found YouTube videoId: ${searchResultEntity.videoId} for Spotify ID: ${trackWithSpotifyId.id}")
            // searchResultEntity.spotifyId Ð´Ð¾Ð»Ð¶ÐµÐ½ Ð±Ñ‹Ñ‚ÑŒ trackWithSpotifyId.id
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
        } else { // PAUSED, ERROR, LOADING (ÐµÑÐ»Ð¸ Ð±Ñ‹Ð» Ñ‚Ñ€ÐµÐº), etc.
            // Ð•ÑÐ»Ð¸ ÐµÑÑ‚ÑŒ Ñ‚ÐµÐºÑƒÑ‰Ð¸Ð¹ Ñ‚Ñ€ÐµÐº Ð² ViewModel, Ð¿Ñ‹Ñ‚Ð°ÐµÐ¼ÑÑ ÐµÐ³Ð¾ Ð²Ð¾Ð·Ð¾Ð±Ð½Ð¾Ð²Ð¸Ñ‚ÑŒ/Ð·Ð°Ð¿ÑƒÑÑ‚Ð¸Ñ‚ÑŒ
            viewModel.currentTrack.value?.let {
                Log.d(TAG, "Requesting RESUME for track via ViewModel: ${it.title}")
                viewModel.resumeCurrentTrack() // ViewModel Ð¿Ð¾ÑˆÐ»ÐµÑ‚ ÐºÐ¾Ð¼Ð°Ð½Ð´Ñƒ RequestResume
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

    override fun onSeekTo(positionMs: Long) {
        if (isServiceBound && audioPlayer != null) {
            audioPlayer?.seekTo(positionMs)
            uiState.getDataFlow().value?.let { currentData ->
                uiState.update(newData = currentData.copy(currentPositionMs = positionMs.coerceAtLeast(0L)))
            }
        } else {
            Log.w(TAG, "onSeekTo: Service not bound or player not available.")
        }
    }

    override fun onClose() {
        Log.d(TAG, "onClose called by UI. Stopping service and hiding UI.")
        if (isServiceBound && audioPlayer != null) {
            context.startService(Intent(context, YtAudioPlayer::class.java).setAction(YtAudioPlayer.ACTION_STOP))
        } else {
            // Ð•ÑÐ»Ð¸ ÑÐµÑ€Ð²Ð¸Ñ Ð½Ðµ Ð±Ñ‹Ð» ÑÐ²ÑÐ·Ð°Ð½, Ð¿Ñ€Ð¾ÑÑ‚Ð¾ ÑÐºÑ€Ñ‹Ð²Ð°ÐµÐ¼ UI
            Log.w(TAG, "onClose: Service not bound, just hiding UI and clearing state.")
        }
        uiState.hide()
        viewModel.clearPlaybackState()
        trackForPlayerServiceWithYtId = null
        currentPlayingSpotifyId = null
    }

    override fun onExpand() {
        Log.d(TAG, "onExpand ignored: mini player is fixed in compact mode.")
    }

    override fun onRetry() {
        Log.d(TAG, "onRetry called by UI.")
        viewModel.currentTrack.value?.let { trackToRetry ->
            Log.d(TAG, "Retrying track via ViewModel: ${trackToRetry.title}")
            val currentPlaylistContext = viewModel.currentPlaylist.value
            // ÐŸÐµÑ€ÐµÐ´Ð°ÐµÐ¼ ÐºÐ¾Ð¼Ð°Ð½Ð´Ñƒ Ð½Ð° Ð²Ð¾ÑÐ¿Ñ€Ð¾Ð¸Ð·Ð²ÐµÐ´ÐµÐ½Ð¸Ðµ Ð² ViewModel
            viewModel.playTrackInContext(trackToRetry, currentPlaylistContext.ifEmpty { listOf(trackToRetry) })
        } ?: run {
            Log.w(TAG, "Retry called but no current track in ViewModel to retry.")
            uiState.errorPlaying("No track to retry.")
        }
    }

    private data class YtTrackResult(val videoId: String, val score: Float)
}
