package com.veshikov.yousify.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.veshikov.yousify.data.model.YousifyDatabase
import com.veshikov.yousify.data.model.YtTrackCacheDao
import com.veshikov.yousify.data.model.YtTrackCacheEntity
import com.veshikov.yousify.youtube.NewPipeHelper
import kotlinx.coroutines.*

@androidx.media3.common.util.UnstableApi
class YtAudioService : Service() {
    private lateinit var player: ExoPlayer
    private var segments: List<Pair<Float, Float>> = emptyList()
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        val trackSelector = DefaultTrackSelector(this)
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(reason: Int) {
                skipSponsorSegments()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    skipSponsorSegments()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("YtAudioService", "[YtAudioService] ExoPlayer error: ${error.errorCodeName} msg=${error.message}", error)
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoId = intent?.getStringExtra("videoId")
        val audioUrl = intent?.getStringExtra("audioUrl")
        val spotifyId = intent?.getStringExtra("spotifyId")

        // Останавливаем текущий трек перед запуском нового
        player.stop()
        player.clearMediaItems()
        job?.cancel()

        // Для отладки: логируем параметры
        android.util.Log.i("YtAudioService", "[YtAudioService] onStartCommand: spotifyId=$spotifyId, videoId=$videoId, audioUrl=$audioUrl")

        if (spotifyId != null) {
            CoroutineScope(Dispatchers.Main).launch {
                val dao = YousifyDatabase.getInstance(applicationContext).ytTrackCacheDao()
                val cached = dao.getBySpotifyId(spotifyId)
                if (cached != null && cached.audioUrl != null) {
                    android.util.Log.i("YtAudioService", "[YtAudioService] Cache hit: $spotifyId => videoId=${cached.videoId}, audioUrl=${cached.audioUrl}")
                    segments = SponsorBlockApi.getOrFetch(applicationContext, cached.videoId)
                    player.setMediaItem(MediaItem.fromUri(cached.audioUrl))
                    player.prepare()
                    player.play()
                    startForeground(1, buildNotification())
                    return@launch
                }
                // Если кэша нет — ищем и кэшируем только для этого spotifyId
                if (videoId != null && audioUrl == null) {
                    // sanitize: всегда извлекай только 11-символьный id
                    val cleanVideoId = Regex("([a-zA-Z0-9_-]{11})").find(videoId)?.value ?: videoId
                    val ytUrl = "https://www.youtube.com/watch?v=$cleanVideoId"
                    withContext(Dispatchers.IO) {
                        val dashAudioUrl = com.veshikov.yousify.youtube.NewPipeHelper.getBestAudioUrl(ytUrl)
                        if (dashAudioUrl != null) {
                            val entity = com.veshikov.yousify.data.model.YtTrackCacheEntity(
                                spotifyId = spotifyId,
                                videoId = cleanVideoId,
                                score = 1f,
                                audioUrl = dashAudioUrl
                            )
                            dao.insert(entity)
                            val newIntent = Intent(this@YtAudioService, YtAudioService::class.java)
                            newIntent.putExtra("videoId", cleanVideoId)
                            newIntent.putExtra("audioUrl", dashAudioUrl)
                            newIntent.putExtra("spotifyId", spotifyId)
                            startService(newIntent)
                        }
                    }
                    return@launch
                }
                if (audioUrl != null) {
                    android.util.Log.i("YtAudioService", "[YtAudioService] Direct audioUrl for $spotifyId: $audioUrl")
                    segments = SponsorBlockApi.getOrFetch(applicationContext, videoId ?: return@launch)
                    player.setMediaItem(MediaItem.fromUri(audioUrl))
                    player.prepare()
                    player.play()
                    startForeground(1, buildNotification())
                    return@launch
                }
            }
            return START_STICKY
        }
        if (videoId != null && audioUrl == null) {
            // sanitize: всегда извлекай только 11-символьный id
            val cleanVideoId = Regex("([a-zA-Z0-9_-]{11})").find(videoId)?.value ?: videoId
            val ytUrl = "https://www.youtube.com/watch?v=$cleanVideoId"
            Thread {
                try {
                    val dashAudioUrl = NewPipeHelper.getBestAudioUrl(ytUrl)
                    if (dashAudioUrl != null) {
                        val newIntent = Intent(this, YtAudioService::class.java)
                        newIntent.putExtra("videoId", cleanVideoId)
                        newIntent.putExtra("audioUrl", dashAudioUrl)
                        startService(newIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
            return START_NOT_STICKY
        }
        if (audioUrl != null) {
            android.util.Log.i("YtAudioService", "[YtAudioService] prepare audio: $audioUrl")
            job = CoroutineScope(Dispatchers.Main).launch {
                try {
                    segments = SponsorBlockApi.getOrFetch(applicationContext, videoId ?: return@launch)
                    player.setMediaItem(MediaItem.fromUri(audioUrl))
                    player.prepare()
                    player.play()
                    startForeground(1, buildNotification())
                } catch (e: Exception) {
                    android.util.Log.e("YtAudioService", "[YtAudioService] error in playback: ${e.message}", e)
                }
            }
            return START_STICKY
        }
        return START_NOT_STICKY
    }

    private fun skipSponsorSegments() {
        val pos = player.currentPosition / 1000f
        val seg = segments.firstOrNull { pos >= it.first && pos < it.second }
        if (seg != null) {
            player.seekTo(((seg.second + 0.5f) * 1000).toLong())
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "yt_audio"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(NotificationChannel(channelId, "YT Audio", NotificationManager.IMPORTANCE_LOW))
            }
        }
        val pi = PendingIntent.getActivity(this, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("YouTube Audio")
            .setContentText("Playing...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        job?.cancel()
        player.release()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
