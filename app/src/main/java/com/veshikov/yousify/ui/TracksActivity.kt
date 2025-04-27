package com.veshikov.yousify.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarDuration
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.veshikov.yousify.R
import com.veshikov.yousify.auth.AuthManager
import com.veshikov.yousify.data.model.TrackEntity
import com.veshikov.yousify.data.model.TrackItem
import com.veshikov.yousify.player.MiniPlayerController
import com.veshikov.yousify.player.YtAudioPlayer
import com.veshikov.yousify.ui.adapters.TrackAdapter
import com.veshikov.yousify.ui.components.MiniPlayer
import com.veshikov.yousify.ui.components.MiniPlayerState
import com.veshikov.yousify.ui.theme.YouSifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TracksActivity : ComponentActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var noTracksText: TextView
    
    // Мини-плеер компоненты (View)
    private var miniPlayerView: View? = null
    private var miniPlayerCard: MaterialCardView? = null
    private var trackImage: ImageView? = null
    private var trackTitle: TextView? = null
    private var artistName: TextView? = null
    private var sponsorBlockIndicator: LinearLayout? = null
    private var loadingProgress: ProgressBar? = null
    private var playPauseButton: FloatingActionButton? = null
    private var errorView: LinearLayout? = null
    private var playbackControls: LinearLayout? = null
    private var extendedInfo: LinearLayout? = null
    private var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>? = null
    
    // Контроллер для мини-плеера
    private lateinit var miniPlayerController: MiniPlayerController
    
    // Проверка API уровня для выбора режима UI
    private val useComposeUI = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S // API 31+
    
    // Запрос разрешений для воспроизведения
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // Повторяем воспроизведение, если все разрешения получены
            miniPlayerController.onRetry()
        } else {
            showSnackbar("Permissions required for playback")
        }
    }
    
    // Перечень необходимых разрешений
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.FOREGROUND_SERVICE,
            android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
        )
    } else {
        arrayOf(
            android.Manifest.permission.FOREGROUND_SERVICE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализация контроллера мини-плеера
        miniPlayerController = MiniPlayerController(
            context = this,
            lifecycleOwner = this,
            onRequirePermissions = { requestPlaybackPermissions() }
        )
        
        lifecycleScope.launch {
            try {
                AuthManager.getApi(this@TracksActivity)
                
                // Выбираем между Compose и XML в зависимости от API уровня
                if (useComposeUI) {
                    setComposeContent()
                } else {
                    setContentView(R.layout.activity_tracks)
                    initializeXmlViews()
                    observeMiniPlayerStateForXml()
                }
                
                // Загружаем треки
                loadTracks()
                
            } catch (e: IllegalStateException) {
                launchSpotifyLogin()
            }
        }
    }
    
    private fun initializeXmlViews() {
        // Инициализация основных компонентов
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        noTracksText = findViewById(R.id.noTracksText)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = TrackAdapter { trackItem ->
            onTrackClick(trackItem)
        }
        
        // Инициализация мини-плеера
        miniPlayerView = findViewById(R.id.miniPlayerView)
        miniPlayerCard = miniPlayerView?.findViewById(R.id.miniPlayerCard)
        trackImage = miniPlayerView?.findViewById(R.id.trackImage)
        trackTitle = miniPlayerView?.findViewById(R.id.trackTitle)
        artistName = miniPlayerView?.findViewById(R.id.artistName)
        sponsorBlockIndicator = miniPlayerView?.findViewById(R.id.sponsorBlockIndicator)
        loadingProgress = miniPlayerView?.findViewById(R.id.loadingProgress)
        playPauseButton = miniPlayerView?.findViewById(R.id.playPauseButton)
        errorView = miniPlayerView?.findViewById(R.id.errorView)
        playbackControls = miniPlayerView?.findViewById(R.id.playbackControls)
        extendedInfo = miniPlayerView?.findViewById(R.id.extendedInfo)
        
        // Инициализация BottomSheet
        miniPlayerCard?.let {
            bottomSheetBehavior = BottomSheetBehavior.from(it)
            bottomSheetBehavior?.isHideable = true
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
            
            bottomSheetBehavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    // Обновляем состояние развернутости в контроллере
                    val isExpanded = newState == BottomSheetBehavior.STATE_EXPANDED
                    miniPlayerController.onExpand()
                }
                
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // Можно добавить анимацию при перетаскивании
                }
            })
        }
        
        // Настройка обработчиков для кнопок
        miniPlayerView?.findViewById<View>(R.id.dragHandle)?.setOnClickListener {
            toggleBottomSheetState()
        }
        
        miniPlayerView?.findViewById<View>(R.id.closeButton)?.setOnClickListener {
            miniPlayerController.onClose()
        }
        
        playPauseButton?.setOnClickListener {
            miniPlayerController.onPlayPause()
        }
        
        miniPlayerView?.findViewById<View>(R.id.skipPreviousButton)?.setOnClickListener {
            miniPlayerController.onSkipPrevious()
        }
        
        miniPlayerView?.findViewById<View>(R.id.skipNextButton)?.setOnClickListener {
            miniPlayerController.onSkipNext()
        }
        
        miniPlayerView?.findViewById<View>(R.id.retryButton)?.setOnClickListener {
            miniPlayerController.onRetry()
        }
    }
    
    private fun toggleBottomSheetState() {
        bottomSheetBehavior?.let {
            it.state = if (it.state == BottomSheetBehavior.STATE_EXPANDED) {
                BottomSheetBehavior.STATE_COLLAPSED
            } else {
                BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }
    
    private fun observeMiniPlayerStateForXml() {
        // Наблюдаем за состоянием из контроллера
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
                bottomSheetBehavior?.state = if (isExpanded) {
                    BottomSheetBehavior.STATE_EXPANDED
                } else {
                    BottomSheetBehavior.STATE_COLLAPSED
                }
                
                // Показываем/скрываем дополнительную информацию
                extendedInfo?.visibility = if (isExpanded) View.VISIBLE else View.GONE
            }
        }
    }
    
    private fun updateMiniPlayerViewState(state: MiniPlayerState) {
        when (state) {
            MiniPlayerState.HIDDEN -> {
                miniPlayerCard?.visibility = View.GONE
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
            }
            MiniPlayerState.LOADING -> {
                miniPlayerCard?.visibility = View.VISIBLE
                loadingProgress?.visibility = View.VISIBLE
                playbackControls?.visibility = View.GONE
                errorView?.visibility = View.GONE
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            MiniPlayerState.PLAYING -> {
                miniPlayerCard?.visibility = View.VISIBLE
                loadingProgress?.visibility = View.GONE
                playbackControls?.visibility = View.VISIBLE
                errorView?.visibility = View.GONE
                playPauseButton?.setImageResource(R.drawable.ic_pause)
            }
            MiniPlayerState.PAUSED -> {
                miniPlayerCard?.visibility = View.VISIBLE
                loadingProgress?.visibility = View.GONE
                playbackControls?.visibility = View.VISIBLE
                errorView?.visibility = View.GONE
                playPauseButton?.setImageResource(R.drawable.ic_play)
            }
            MiniPlayerState.ERROR -> {
                miniPlayerCard?.visibility = View.VISIBLE
                loadingProgress?.visibility = View.GONE
                playbackControls?.visibility = View.GONE
                errorView?.visibility = View.VISIBLE
            }
        }
    }
    
    private fun updateMiniPlayerViewData(data: com.veshikov.yousify.ui.components.MiniPlayerData?) {
        if (data == null) return
        
        trackTitle?.text = data.title
        artistName?.text = data.artist
        sponsorBlockIndicator?.visibility = if (data.hasSponsorBlockSegments) View.VISIBLE else View.GONE
        
        // Загрузка обложки с помощью Coil
        data.imageUrl?.let { url ->
            trackImage?.let { imageView ->
                val drawable = ContextCompat.getDrawable(this, R.drawable.ic_track_placeholder)
                imageView.setImageDrawable(drawable)
            }
        } ?: run {
            trackImage?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_track_placeholder))
        }
        
        // Обновляем текст ошибки, если есть
        miniPlayerView?.findViewById<TextView>(R.id.errorText)?.text = 
            data.errorMessage ?: getString(R.string.playback_error)
    }
    
    private fun setComposeContent() {
        setContent {
            YouSifyTheme {
                TracksScreen()
            }
        }
    }

    @Composable
    fun TracksScreen() {
        val scaffoldState = rememberScaffoldState()
        val state by miniPlayerController.miniPlayerState.collectAsState(initial = MiniPlayerState.HIDDEN)
        val data by miniPlayerController.miniPlayerData.collectAsState(initial = null)
        val isExpanded by miniPlayerController.isExpanded.collectAsState(initial = false)
        
        Scaffold(
            scaffoldState = scaffoldState,
            bottomBar = {
                MiniPlayer(
                    state = state,
                    data = data,
                    isExpanded = isExpanded,
                    events = miniPlayerController,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Используем AndroidView для встраивания существующего XML-layout
                AndroidView(
                    factory = { context ->
                        // Создаем View из XML
                        View.inflate(context, R.layout.content_tracks, null).apply {
                            // Инициализация View компонентов
                            recyclerView = findViewById(R.id.recyclerView)
                            progressBar = findViewById(R.id.progressBar)
                            noTracksText = findViewById(R.id.noTracksText)
                            
                            recyclerView.layoutManager = LinearLayoutManager(context)
                            recyclerView.adapter = TrackAdapter { trackItem ->
                                onTrackClick(trackItem)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    private fun onTrackClick(trackItem: TrackItem) {
        val track = trackItem.track ?: return
        
        val playlistId = intent.getStringExtra("playlist_id") ?: ""
        val isrc = track.externalIds?.get("isrc") ?: ""
        
        // Преобразуем в TrackEntity для передачи в мини-плеер
        val trackEntity = TrackEntity(
            id = track.id.toString(),
            playlistId = playlistId,
            title = track.name ?: "Unknown",
            artist = track.artists?.joinToString(", ") { it.name ?: "" } ?: "Unknown",
            isrc = isrc,
            durationMs = track.durationMs ?: 0L,
        )
        
        // Воспроизведение трека через контроллер
        miniPlayerController.playTrack(trackEntity)
    }
    
    private fun loadTracks() {
        val playlistName = intent.getStringExtra("playlist_name")
        
        playlistName?.let {
            title = it
        } ?: run {
            title = getString(R.string.tracks)
        }
        
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        noTracksText.visibility = View.GONE
        
        // Удалены обращения к playlists
        // playlistId?.let { id ->
        //     lifecycleScope.launch {
        //         try {
        //             val api = AuthManager.getApi(this@TracksActivity)
        //             
        //             // Получаем треки через API
        //             val tracks = withContext(Dispatchers.IO) {
        //                 api.playlists.getPlaylistTracks(id).items
        //             }
        //             
        //             withContext(Dispatchers.Main) {
        //                 if (tracks.isNotEmpty()) {
        //                     (recyclerView.adapter as? TrackAdapter)?.submitList(tracks)
        //                     recyclerView.visibility = View.VISIBLE
        //                 } else {
        //                     noTracksText.visibility = View.VISIBLE
        //                 }
        //                 progressBar.visibility = View.GONE
        //             }
        //         } catch (e: Exception) {
        //             Log.e("TracksActivity", "Failed to load tracks", e)
        //             withContext(Dispatchers.Main) {
        //                 showSnackbar("Failed to load tracks: ${e.localizedMessage}")
        //                 progressBar.visibility = View.GONE
        //             }
        //         }
        //     }
        // }
    }
    
    private fun requestPlaybackPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest)
        }
    }
    
    private fun showSnackbar(message: String) {
        Snackbar.make(
            findViewById(R.id.snackbarContainer),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun launchSpotifyLogin() {
        val verifier = java.util.UUID.randomUUID().toString()
        val authUri = "https://accounts.spotify.com/authorize?client_id=${AuthManager.CLIENT_ID}&response_type=code&redirect_uri=${AuthManager.REDIRECT}&code_challenge=$verifier&code_challenge_method=plain&scope=playlist-read-private%20user-read-email"
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(authUri))
        startActivity(intent)
        getSharedPreferences("auth", Context.MODE_PRIVATE).edit().putString("verifier", verifier).apply()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val uri = intent?.data ?: return
        if (uri.toString().startsWith(AuthManager.REDIRECT)) {
            val code = uri.getQueryParameter("code") ?: return
            val verifier = getSharedPreferences("auth", Context.MODE_PRIVATE).getString("verifier", null) ?: return
            lifecycleScope.launch {
                try {
                    AuthManager.exchangeCode(code, verifier, this@TracksActivity)
                    // Перезапуск активити с Compose UI если необходимо
                    if (useComposeUI) {
                        setComposeContent()
                    }
                    loadTracks()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showSnackbar("Login failed: ${e.localizedMessage}")
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        miniPlayerController.release()
    }

    companion object {
        fun newIntent(ctx: Context, id: String, name: String? = null): Intent {
            return Intent(ctx, TracksActivity::class.java)
                .putExtra("playlist_id", id)
                .putExtra("playlist_name", name)
        }
    }
}