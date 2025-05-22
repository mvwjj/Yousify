package com.veshikov.yousify.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.veshikov.yousify.R
import com.veshikov.yousify.auth.SecurePrefs // ИСПРАВЛЕНО: импорт SecurePrefs
import com.veshikov.yousify.data.SpotifyApiWrapper
import com.veshikov.yousify.data.model.TrackEntity
import com.veshikov.yousify.data.model.TrackItem
import com.veshikov.yousify.player.MiniPlayerController
import com.veshikov.yousify.ui.adapters.TrackAdapter
import com.veshikov.yousify.ui.components.MiniPlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.load

@androidx.media3.common.util.UnstableApi
class TracksActivity : ComponentActivity() {
    private val TAG = "TracksActivity"
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var noTracksText: TextView

    private var miniPlayerView: View? = null
    private var miniPlayerCard: MaterialCardView? = null
    private var trackImage: ImageView? = null
    private var trackTitleTextView: TextView? = null
    private var artistNameTextView: TextView? = null
    private var sponsorBlockIndicator: LinearLayout? = null
    private var loadingProgress: ProgressBar? = null
    private var playPauseButton: FloatingActionButton? = null
    private var errorView: LinearLayout? = null
    private var playbackControls: LinearLayout? = null
    private var extendedInfo: LinearLayout? = null
    private var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>? = null

    private lateinit var miniPlayerController: MiniPlayerController
    private val yousifyViewModel: YousifyViewModel by viewModels()

    // private val useComposeUI = false // Не используется

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            miniPlayerController.onRetry()
        } else {
            showSnackbar("Permissions required for playback")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        miniPlayerController = MiniPlayerController(
            context = this,
            lifecycleOwner = this,
            viewModel = yousifyViewModel,
            onRequirePermissions = { permissionsArray -> requestPlaybackPermissions(permissionsArray) }
        )

        lifecycleScope.launch {
            // ИСПРАВЛЕНО: Передаем контекст
            val apiWrapper = SpotifyApiWrapper.getInstance(this@TracksActivity)
            if (apiWrapper.getAccessToken() == null) {
                // ИСПРАВЛЕНО: SecurePrefs.accessToken вместо SecurePrefs.access
                val savedToken = SecurePrefs.accessToken(this@TracksActivity)
                if (savedToken != null) {
                    // ИСПРАВЛЕНО: Передаем refresh token
                    apiWrapper.initializeApiWithToken(savedToken, SecurePrefs.refreshToken(this@TracksActivity))
                    setupUiAndLoadTracks()
                } else {
                    Log.w(TAG, "No Spotify token, finishing TracksActivity.")
                    Toast.makeText(this@TracksActivity, "Please login via Spotify first.", Toast.LENGTH_LONG).show()
                    finish()
                }
            } else {
                setupUiAndLoadTracks()
            }
        }
    }

    private fun setupUiAndLoadTracks() {
        setContentView(R.layout.activity_tracks)
        initializeXmlViews()
        observeMiniPlayerStateForXml()
        loadTracks()
    }

    private fun initializeXmlViews() {
        recyclerView = findViewById(R.id.recyclerViewTracksActivity)
        progressBar = findViewById(R.id.progressBarTracksActivity)
        noTracksText = findViewById(R.id.noTracksTextTracksActivity)

        recyclerView.layoutManager = LinearLayoutManager(this)

        miniPlayerView = findViewById(R.id.miniPlayerViewTracksActivity)
        if (miniPlayerView == null) {
            Log.e(TAG, "MiniPlayerView (include) not found in R.layout.activity_tracks! Ensure it has ID 'miniPlayerViewTracksActivity'.")
            return
        }

        miniPlayerCard = miniPlayerView?.findViewById(R.id.miniPlayerCard)
        trackImage = miniPlayerView?.findViewById(R.id.trackImage)
        trackTitleTextView = miniPlayerView?.findViewById(R.id.trackTitle)
        artistNameTextView = miniPlayerView?.findViewById(R.id.artistName)
        sponsorBlockIndicator = miniPlayerView?.findViewById(R.id.sponsorBlockIndicator)
        loadingProgress = miniPlayerView?.findViewById(R.id.loadingProgress)
        playPauseButton = miniPlayerView?.findViewById(R.id.playPauseButton)
        errorView = miniPlayerView?.findViewById(R.id.errorView)
        playbackControls = miniPlayerView?.findViewById(R.id.playbackControls)
        extendedInfo = miniPlayerView?.findViewById(R.id.extendedInfo)


        miniPlayerCard?.let { card ->
            bottomSheetBehavior = BottomSheetBehavior.from(card)
            bottomSheetBehavior?.isHideable = true
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN

            bottomSheetBehavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    val isExpandedNew = newState == BottomSheetBehavior.STATE_EXPANDED
                    if (miniPlayerController.isExpanded.value != isExpandedNew) {
                        miniPlayerController.uiState.setExpanded(isExpandedNew)
                    }
                }
                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })
        }

        miniPlayerView?.findViewById<View>(R.id.dragHandle)?.setOnClickListener {
            miniPlayerController.onExpand()
        }
        miniPlayerView?.findViewById<ImageButton>(R.id.closeButton)?.setOnClickListener {
            miniPlayerController.onClose()
        }
        playPauseButton?.setOnClickListener { miniPlayerController.onPlayPause() }
        miniPlayerView?.findViewById<ImageButton>(R.id.skipNextButton)?.setOnClickListener { miniPlayerController.onSkipNext() }
        miniPlayerView?.findViewById<ImageButton>(R.id.skipPreviousButton)?.setOnClickListener { miniPlayerController.onSkipPrevious() }
        miniPlayerView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.retryButton)?.setOnClickListener { miniPlayerController.onRetry() }

        updateMiniPlayerViewState(miniPlayerController.miniPlayerState.value)
        updateMiniPlayerViewData(miniPlayerController.miniPlayerData.value)
    }

    private fun observeMiniPlayerStateForXml() {
        if (miniPlayerView == null) return

        lifecycleScope.launch {
            miniPlayerController.miniPlayerState.collectLatest { state ->
                updateMiniPlayerViewState(state)
            }
        }
        lifecycleScope.launch {
            miniPlayerController.miniPlayerData.collectLatest { data ->
                updateMiniPlayerViewData(data)
            }
        }
        lifecycleScope.launch {
            miniPlayerController.isExpanded.collectLatest { isExpanded ->
                if (bottomSheetBehavior?.state != BottomSheetBehavior.STATE_DRAGGING &&
                    bottomSheetBehavior?.state != BottomSheetBehavior.STATE_SETTLING) {
                    bottomSheetBehavior?.state = if (isExpanded) BottomSheetBehavior.STATE_EXPANDED else BottomSheetBehavior.STATE_COLLAPSED
                }
                extendedInfo?.visibility = if (isExpanded) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateMiniPlayerViewState(state: MiniPlayerState) {
        if (miniPlayerView == null) return
        Log.d(TAG, "Updating MiniPlayer XML UI to state: $state")

        val isHidden = state == MiniPlayerState.HIDDEN
        miniPlayerView?.visibility = if (isHidden) View.GONE else View.VISIBLE
        miniPlayerCard?.visibility = if (isHidden) View.GONE else View.VISIBLE

        if (isHidden) {
            if (bottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
            }
        } else {
            if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_HIDDEN) {
                // Показываем свернутым, если был скрыт и сейчас не загрузка.
                // Если загрузка, то он может остаться скрытым до появления данных.
                if (state != MiniPlayerState.LOADING || miniPlayerController.miniPlayerData.value != null) {
                    bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        }


        loadingProgress?.visibility = if (state == MiniPlayerState.LOADING) View.VISIBLE else View.GONE
        playbackControls?.visibility = if (state == MiniPlayerState.PLAYING || state == MiniPlayerState.PAUSED) View.VISIBLE else View.GONE
        errorView?.visibility = if (state == MiniPlayerState.ERROR) View.VISIBLE else View.GONE

        when (state) {
            MiniPlayerState.PLAYING -> playPauseButton?.setImageResource(R.drawable.ic_pause) // Убедитесь, что такой drawable есть
            MiniPlayerState.PAUSED -> playPauseButton?.setImageResource(R.drawable.ic_play)   // Убедитесь, что такой drawable есть
            else -> { /* Default or no change */ }
        }
    }

    private fun updateMiniPlayerViewData(data: com.veshikov.yousify.ui.components.MiniPlayerData?) {
        if (miniPlayerView == null || data == null) {
            // Если данных нет, но плеер не скрыт (например, ошибка без данных),
            // то UI может оставаться видимым, но пустым.
            if (data == null && miniPlayerController.miniPlayerState.value != MiniPlayerState.HIDDEN && miniPlayerController.miniPlayerState.value != MiniPlayerState.LOADING) {
                // Можно скрыть некоторые элементы или показать заглушки
                trackTitleTextView?.text = "No track loaded"
                artistNameTextView?.text = ""
                sponsorBlockIndicator?.visibility = View.GONE
                trackImage?.setImageResource(R.drawable.ic_track_placeholder)
            }
            return
        }
        Log.d(TAG, "Updating MiniPlayer XML UI data: ${data.title}")

        trackTitleTextView?.text = data.title
        artistNameTextView?.text = data.artist
        sponsorBlockIndicator?.visibility = if (data.hasSponsorBlockSegments) View.VISIBLE else View.GONE

        trackImage?.load(data.imageUrl) {
            placeholder(R.drawable.ic_track_placeholder)
            error(R.drawable.ic_track_placeholder)
            crossfade(true)
        }

        miniPlayerView?.findViewById<TextView>(R.id.errorText)?.text =
            data.errorMessage ?: getString(R.string.playback_error_default)  // Убедитесь что R.string.playback_error_default есть
    }

    private fun onTrackClick(trackItem: TrackItem, tracksList: List<TrackItem>) {
        val track = trackItem.track ?: return
        Log.d(TAG, "Track clicked (XML): ${track.name}")

        val playlistIdFromIntent = intent.getStringExtra("playlist_id") ?: ""
        val isrc = track.externalIds?.get("isrc")

        val trackEntity = TrackEntity(
            id = track.id ?: return, // Важно проверить id на null
            playlistId = playlistIdFromIntent,
            title = track.name ?: "Unknown Title",
            artist = track.artists?.joinToString(", ") { it.name ?: "Unknown Artist" } ?: "Unknown Artist",
            isrc = isrc,
            durationMs = track.durationMs ?: 0L,
        )

        val playlistContextEntities = tracksList.mapNotNull { item ->
            item.track?.let { currentTrack -> // Переименовал, чтобы не конфликтовать с track из onTrackClick
                currentTrack.id?.let { trackId -> // Проверяем id на null
                    TrackEntity(
                        id = trackId,
                        playlistId = playlistIdFromIntent,
                        title = currentTrack.name ?: "",
                        artist = currentTrack.artists?.joinToString(", ") { art -> art.name ?: "" } ?: "",
                        isrc = currentTrack.externalIds?.get("isrc"),
                        durationMs = currentTrack.durationMs ?: 0L
                    )
                }
            }
        }
        yousifyViewModel.playTrackInContext(trackEntity, playlistContextEntities)
    }

    private fun loadTracks() {
        val playlistId = intent.getStringExtra("playlist_id")
        val playlistName = intent.getStringExtra("playlist_name")

        this.title = playlistName ?: getString(R.string.tracks) // Убедитесь, что R.string.tracks есть

        if (playlistId == null) {
            Log.e(TAG, "Playlist ID is null.")
            if (::progressBar.isInitialized) progressBar.visibility = View.GONE
            if (::noTracksText.isInitialized) {
                noTracksText.visibility = View.VISIBLE
                noTracksText.text = "Error: Playlist ID not found."
            }
            return
        }
        if (!::progressBar.isInitialized || !::recyclerView.isInitialized || !::noTracksText.isInitialized) {
            Log.e(TAG, "Views not initialized in loadTracks, cannot proceed.")
            // Попытка инициализировать снова, если это вызвано до setContentView
            if (miniPlayerView == null) { // Проверяем, был ли уже вызван setupUiAndLoadTracks
                Log.w(TAG, "Attempting re-initialization of views in loadTracks.")
                setupUiAndLoadTracks() // Это рискованно, но может помочь если порядок вызовов нарушен
                if (!::progressBar.isInitialized) return // Если все еще не инициализировано, выходим
            } else {
                return
            }
        }

        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        noTracksText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // ИСПРАВЛЕНО: Передаем контекст
                val apiWrapper = SpotifyApiWrapper.getInstance(this@TracksActivity)
                val tracks: List<TrackItem>? = apiWrapper.getPlaylistTracks(playlistId)

                withContext(Dispatchers.Main) {
                    if (tracks != null && tracks.isNotEmpty()) {
                        val adapter = TrackAdapter { clickedTrackItem ->
                            onTrackClick(clickedTrackItem, tracks)
                        }
                        recyclerView.adapter = adapter
                        adapter.submitList(tracks)
                        recyclerView.visibility = View.VISIBLE
                        noTracksText.visibility = View.GONE
                    } else if (tracks == null) {
                        noTracksText.text = "Error loading tracks."
                        noTracksText.visibility = View.VISIBLE
                    } else { // tracks is empty list
                        noTracksText.text = "No tracks in this playlist."
                        noTracksText.visibility = View.VISIBLE
                    }
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load tracks for playlist $playlistId", e)
                withContext(Dispatchers.Main) {
                    showSnackbar("Failed to load tracks: ${e.localizedMessage}")
                    progressBar.visibility = View.GONE
                    noTracksText.text = "Error: ${e.localizedMessage}"
                    noTracksText.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun requestPlaybackPermissions(permissionsToRequest: Array<String>) {
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions from Activity: ${permissionsToRequest.joinToString()}")
            requestPermissionsLauncher.launch(permissionsToRequest)
        }
    }

    private fun showSnackbar(message: String) {
        val view = findViewById<View>(android.R.id.content)
        if (view != null) {
            com.google.android.material.snackbar.Snackbar.make(view, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TracksActivity onDestroy")
        // miniPlayerController.release() // Контроллер должен быть освобожден здесь, если он не shared
    }

    companion object {
        fun newIntent(ctx: Context, id: String, name: String? = null): Intent {
            return Intent(ctx, TracksActivity::class.java)
                .putExtra("playlist_id", id)
                .putExtra("playlist_name", name)
        }
    }
}