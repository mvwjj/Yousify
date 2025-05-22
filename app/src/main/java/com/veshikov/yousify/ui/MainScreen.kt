package com.veshikov.yousify.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator // Material 2
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.* // Material 3 components
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
// import androidx.compose.ui.res.painterResource // Не используется в этом файле напрямую
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
// import com.veshikov.yousify.R // Не используется в этом файле напрямую
import com.veshikov.yousify.auth.SecurePrefs // ИСПРАВЛЕНО: импорт SecurePrefs
import com.veshikov.yousify.auth.SpotifyAuthManager
import com.veshikov.yousify.data.SpotifyApiWrapper
import com.veshikov.yousify.data.model.PlaylistEntity
import com.veshikov.yousify.data.model.TrackEntity
import com.veshikov.yousify.player.MiniPlayerController
// import com.veshikov.yousify.player.YtAudioService // Не используется в этом файле напрямую
import com.veshikov.yousify.ui.components.MiniPlayer
import com.veshikov.yousify.ui.components.MiniPlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import android.app.PictureInPictureParams

// suspend функция для обмена кода на токен
suspend fun exchangeCodeForToken(code: String, codeVerifier: String, context: Context): String? { // context уже есть
    Log.i("YousifyAuth", "EXCHANGE_TOKEN_START: code=$code codeVerifier=$codeVerifier")
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://accounts.spotify.com/api/token")
            val postData = "grant_type=authorization_code" +
                    "&code=" + URLEncoder.encode(code, "UTF-8") +
                    "&redirect_uri=" + URLEncoder.encode(SpotifyAuthManager.REDIRECT_URI, "UTF-8") +
                    "&client_id=" + URLEncoder.encode(SpotifyAuthManager.CLIENT_ID, "UTF-8") +
                    "&code_verifier=" + URLEncoder.encode(codeVerifier, "UTF-8")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(postData.toByteArray()) }
            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }
            Log.i("YousifyAuth", "TOKEN_RESPONSE: code=$responseCode body=$responseBody")
            if (responseCode == 200) {
                val jsonResponse = JSONObject(responseBody)
                val accessToken = jsonResponse.optString("access_token", null)
                val refreshToken = jsonResponse.optString("refresh_token", null)
                val expiresIn = jsonResponse.optLong("expires_in", 3600)

                Log.i("YousifyAuth", "TOKEN_PARSED: access=${accessToken?.take(10)}..., refresh=${refreshToken?.take(5)}..., expires=$expiresIn")

                if (accessToken != null && refreshToken != null) {
                    SecurePrefs.save(accessToken, refreshToken, expiresIn, context) // ИСПРАВЛЕНО
                    Log.i("YousifyAuth", "TOKENS_SAVED_TO_SECURE_PREFS")
                }
                accessToken
            } else {
                Log.e("YousifyAuth", "TOKEN_ERROR: $responseCode $responseBody")
                null
            }
        } catch (e: Exception) {
            Log.e("YousifyAuth", "TOKEN_EXCEPTION: ${e.message}", e)
            null
        }
    }
}

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Playlists : BottomNavItem("playlists", "Playlists", Icons.Filled.LibraryMusic)
    object Search : BottomNavItem("search", "Search", Icons.Filled.Search)
    object Settings : BottomNavItem("settings", "Settings", Icons.Filled.Settings)
}

@androidx.media3.common.util.UnstableApi
@Composable
fun MainScreen(appViewModel: YousifyViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val miniPlayerController = remember {
        MiniPlayerController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            viewModel = appViewModel,
            onRequirePermissions = { permissionsArray ->
                Log.w("MainScreen", "Permissions required by MiniPlayerController: ${permissionsArray.joinToString()}")
                Toast.makeText(context, "Player requires permissions: ${permissionsArray.joinToString()}", Toast.LENGTH_LONG).show()
            }
        )
    }
    val activity = context as? Activity
    // ИСПРАВЛЕНО: Передаем контекст
    val apiWrapper = remember { SpotifyApiWrapper.getInstance(context) }

    // ИСПРАВЛЕНО: SecurePrefs.accessToken вместо SecurePrefs.access
    val initialAccessToken = remember { SecurePrefs.accessToken(context) }
    var authed by remember { mutableStateOf(apiWrapper.getAccessToken() != null || initialAccessToken != null) }

    LaunchedEffect(initialAccessToken, apiWrapper.getAccessToken(), authed) { // Добавил authed в key, чтобы перезапускать при его изменении
        val currentApiToken = apiWrapper.getAccessToken()
        if (currentApiToken != null && !authed) { // Если токен уже в wrapper, но authed еще false
            authed = true
            appViewModel.syncSpotifyData()
            appViewModel.loadPlaylistsFromDb()
        } else if (initialAccessToken != null && currentApiToken == null) { // Если есть сохраненный, но не в wrapper
            Log.i("MainScreen", "Initializing API with saved token from SecurePrefs")
            // ИСПРАВЛЕНО: передаем refresh token из SecurePrefs
            val success = apiWrapper.initializeApiWithToken(initialAccessToken, SecurePrefs.refreshToken(context))
            if (success) {
                Log.i("MainScreen", "API initialized successfully with saved token")
                authed = true // Устанавливаем authed
                appViewModel.syncSpotifyData()
                appViewModel.loadPlaylistsFromDb()
            } else {
                Log.e("MainScreen", "Failed to initialize API with saved token")
                SecurePrefs.clear(context) // ИСПРАВЛЕНО
                authed = false
            }
        } else if (currentApiToken != null && authed) { // Если токен в wrapper и мы авторизованы
            if (appViewModel.playlists.value.isEmpty()) {
                appViewModel.loadPlaylistsFromDb() // Загружаем, если плейлисты пусты
            }
        } else if (initialAccessToken == null && currentApiToken == null) { // Если токенов нет нигде
            authed = false
        }
    }


    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val authManager = remember {
        SpotifyAuthManager(context) { code ->
            Log.i("YousifyAuth", "AUTH_CODE: $code")
            (activity as? ComponentActivity)?.lifecycleScope?.launch {
                val prefs = context.getSharedPreferences(SpotifyAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
                val codeVerifier = prefs.getString(SpotifyAuthManager.CODE_VERIFIER_KEY, null)
                Log.i("YousifyAuth", "CODE_VERIFIER: $codeVerifier")
                if (codeVerifier != null) {
                    loading = true
                    error = null
                    val token = exchangeCodeForToken(code, codeVerifier, context) // context уже передается
                    Log.i("YousifyAuth", "TOKEN_EXCHANGE: ${token?.take(10)}...")
                    if (token != null) {
                        // ИСПРАВЛЕНО: передаем refresh token (который должен был сохраниться в exchangeCodeForToken)
                        val success = apiWrapper.initializeApiWithToken(token, SecurePrefs.refreshToken(context))
                        Log.i("YousifyAuth", "API_INIT_OK: $success")
                        if (success) {
                            loading = false
                            error = null
                            authed = true // Устанавливаем authed здесь
                            // appViewModel.syncSpotifyData() // Запуск синхронизации после успешной авторизации
                            // appViewModel.loadPlaylistsFromDb()
                        } else {
                            loading = false
                            error = "Error initializing Spotify API after token exchange"
                            authed = false
                        }
                    } else {
                        loading = false
                        error = "Failed to get accessToken from exchange"
                        Log.e("YousifyAuth", "TOKEN_ERROR: token is null after exchange")
                        authed = false
                    }
                } else {
                    loading = false
                    error = "Error: missing code_verifier"
                    Log.e("YousifyAuth", "TOKEN_ERROR: code_verifier is null")
                    authed = false
                }
            }
        }
    }

    if (!authed) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Login via Spotify", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(24.dp))
            if (!loading) {
                Button(
                    onClick = {
                        error = null
                        // loading = true // loading будет установлено в authManager callback
                        activity?.let { authManager.startAuth(it) }
                    },
                    enabled = !loading
                ) {
                    Text("Login")
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Waiting for authorization...")
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(error!!, color = Color.Red)
            }
        }
        return
    }

    val navController = rememberNavController()
    Scaffold(
        bottomBar = { YousifyBottomBar(navController) }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            YousifyNavHost(navController, appViewModel, miniPlayerController)

            val miniPlayerCurrentState by miniPlayerController.miniPlayerState.collectAsState()
            val miniPlayerData by miniPlayerController.miniPlayerData.collectAsState()
            val isMiniPlayerExpanded by miniPlayerController.isExpanded.collectAsState()

            AnimatedVisibility(
                visible = miniPlayerCurrentState != MiniPlayerState.HIDDEN && miniPlayerData != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                miniPlayerData?.let { data ->
                    MiniPlayer(
                        state = miniPlayerCurrentState,
                        data = data,
                        isExpanded = isMiniPlayerExpanded,
                        events = miniPlayerController,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}


@androidx.media3.common.util.UnstableApi
@Composable
fun YousifyNavHost(navController: NavHostController, viewModel: YousifyViewModel, miniPlayerController: MiniPlayerController) {
    NavHost(navController, startDestination = BottomNavItem.Playlists.route) {
        composable(BottomNavItem.Playlists.route) {
            PlaylistsScreen(viewModel, navController)
        }
        composable("tracks/{playlistId}") { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId")
            LaunchedEffect(playlistId) {
                playlistId?.let { viewModel.selectPlaylistForViewing(it) }
            }
            val tracksForPlaylist by viewModel.tracksForSelectedPlaylist.collectAsState()

            TracksScreen(
                tracks = tracksForPlaylist,
                onTrackClick = { track ->
                    viewModel.playTrackInContext(track, tracksForPlaylist)
                }
            )
        }
        composable("track_detail/{trackId}") { backStackEntry ->
            val trackId = backStackEntry.arguments?.getString("trackId")
            val tracksFromVm by viewModel.tracksForSelectedPlaylist.collectAsState()
            val track = tracksFromVm.find { it.id == trackId }

            if (track != null) {
                TrackDetailScreen(track = track, viewModel = viewModel)
            } else {
                Text("Track not found (ID: $trackId)")
            }
        }
        composable(BottomNavItem.Search.route) {
            SearchScreen(viewModel = viewModel, navController = navController, miniPlayerController = miniPlayerController)
        }
        composable(BottomNavItem.Settings.route) {
            SettingsScreen(viewModel = viewModel)
        }
    }
}

@Composable
fun YousifyBottomBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem.Playlists,
        BottomNavItem.Search,
        BottomNavItem.Settings
    )
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun PlaylistsScreen(viewModel: YousifyViewModel, navController: NavHostController) {
    val playlists by viewModel.playlists.collectAsState()
    val context = LocalContext.current // Для SpotifyApiWrapper

    LaunchedEffect(Unit) {
        // ИСПРАВЛЕНО: Передаем контекст
        if (playlists.isEmpty() && SpotifyApiWrapper.getInstance(context).getAccessToken() != null) {
            viewModel.loadPlaylistsFromDb() // Сначала грузим из БД
            viewModel.syncSpotifyData()    // Потом синхронизируем
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your Playlists", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            Button(onClick = { viewModel.syncSpotifyData() }) {
                Text("Sync")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        // ИСПРАВЛЕНО: Передаем контекст
        val isLoading = playlists.isEmpty() && SpotifyApiWrapper.getInstance(context).getAccessToken() != null
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                CircularProgressIndicator() // Material 2
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading playlists...", color = Color.Gray)
            }
        } else if (playlists.isEmpty() && SpotifyApiWrapper.getInstance(context).getAccessToken() != null) {
            Text("No playlists. Try syncing.", color = Color.Gray)
        } else if (playlists.isNotEmpty()){
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("tracks/${playlist.id}")
                            },
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = "https://placehold.co/64x64?text=${playlist.name.firstOrNull()?.uppercaseChar() ?: 'P'}",
                                contentDescription = playlist.name,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(playlist.name, fontWeight = FontWeight.Bold)
                                Text("Owner: ${playlist.owner.ifEmpty { "Unknown" }}", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TracksScreen(tracks: List<TrackEntity>, onTrackClick: (TrackEntity) -> Unit) {
    if (tracks.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("This playlist has no tracks or they are loading.", color = Color.Gray)
        }
        return
    }

    LazyColumn(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        items(tracks) { track ->
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onTrackClick(track) },
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(track.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${track.durationMs / 1000 / 60}:${(track.durationMs / 1000 % 60).toString().padStart(2, '0')}",
                        modifier = Modifier.align(Alignment.CenterVertically),
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}

@Composable
fun TrackDetailScreen(track: TrackEntity, viewModel: YousifyViewModel) {
    val context = LocalContext.current
    // val loading by remember { mutableStateOf(false) } // loading не используется
    // val scope = rememberCoroutineScope() // scope не используется
    val pipSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    var pipLaunched by remember { mutableStateOf(false) }

    val currentMiniPlayerTrack by viewModel.currentTrack.collectAsState()
    val isPlayingThisTrackInMiniPlayer = currentMiniPlayerTrack?.id == track.id

    Column(modifier = Modifier.padding(16.dp)) {
        Text(track.title, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(track.artist, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Text("ISRC: ${track.isrc ?: "-"}")
        Text("Duration: ${track.durationMs / 1000}s")
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // Логика play/pause должна учитывать текущее состояние плеера, а не только isPlayingThisTrackInMiniPlayer
                // Эта логика уже есть в ViewModel и MiniPlayerController
                if (currentMiniPlayerTrack?.id == track.id) { // Если этот трек уже текущий в плеере
                    // viewModel.togglePlayPause() // Нужен такой метод в ViewModel/Controller или используем существующие
                    val currentPlayerData = viewModel.playbackCommand // Это SharedFlow, нужна актуализация состояния плеера
                    // Для простоты, если трек текущий, то либо пауза, либо возобновление
                    // Правильнее будет получать актуальное состояние (PLAYING/PAUSED) из MiniPlayerController
                    // Но здесь мы отправляем команды в ViewModel
                    if (/* MiniPlayerController.uiState.getStateFlow().value == MiniPlayerState.PLAYING */ false) { // Заглушка, т.к. нет прямого доступа
                        viewModel.pauseCurrentTrack()
                    } else {
                        viewModel.resumeCurrentTrack()
                    }
                } else { // Если это другой трек или плеер пуст
                    viewModel.playTrackInContext(track, listOf(track))
                }
            }
            // enabled = !loading // loading не используется
        ) {
            // Текст кнопки должен зависеть от реального состояния плеера для этого трека
            Text(if (isPlayingThisTrackInMiniPlayer /* && MiniPlayerController.isPlaying */) "Pause" else "Play")
        }

        if (pipSupported) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                if (context is Activity) {
                    try {
                        if (!context.isInPictureInPictureMode) {
                            val params = PictureInPictureParams.Builder().build()
                            context.enterPictureInPictureMode(params)
                            pipLaunched = true
                        }
                    } catch (e: Exception) {
                        Log.e("Yousify", "[TrackDetailScreen] Error entering PiP mode: ${e.message}", e)
                        Toast.makeText(context, "Error entering PiP mode", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text("Enter PiP Mode")
            }
        }
    }
    if (pipLaunched) { BackHandler { /* ... */ } }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun SearchScreen(viewModel: YousifyViewModel, navController: NavHostController, miniPlayerController: MiniPlayerController) {
    val allPlaylists by viewModel.playlists.collectAsState()
    var query by remember { mutableStateOf("") }

    val filteredPlaylists = allPlaylists.filter {
        it.name.contains(query, ignoreCase = true) || it.owner.contains(query, ignoreCase = true)
    }

    val tracksOfCurrentPlaylist by viewModel.tracksForSelectedPlaylist.collectAsState() // Это треки последнего просмотренного плейлиста
    val filteredTracks = tracksOfCurrentPlaylist.filter { // Фильтруем треки из этого контекста
        it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Search", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search playlists, tracks, or artists") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (query.isNotBlank()) {
            Text("Found Playlists", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            if (filteredPlaylists.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 200.dp) // Ограничиваем высоту списка
                ) {
                    items(filteredPlaylists) { playlist ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("tracks/${playlist.id}") },
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = "https://placehold.co/48x48?text=${playlist.name.firstOrNull()?.uppercaseChar() ?: 'P'}",
                                    contentDescription = playlist.name,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(playlist.name, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.weight(1f))
                                Text("(${playlist.owner})", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                Text("No playlists found.", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Found Tracks (in current/last playlist)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            if (filteredTracks.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 300.dp) // Ограничиваем высоту списка
                ) {
                    items(filteredTracks) { track ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.playTrackInContext(track, tracksOfCurrentPlaylist) }, // Передаем контекст текущего плейлиста
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, fontWeight = FontWeight.Medium)
                                    Text(track.artist, color = Color.Gray, fontSize = 12.sp)
                                }
                                Text(
                                    text = "${track.durationMs / 1000 / 60}:${(track.durationMs / 1000 % 60).toString().padStart(2, '0')}",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            } else {
                Text("No tracks found in the current context.", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            }
        } else {
            Text("Enter a query to search.", color = Color.Gray, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
fun SettingsScreen(viewModel: YousifyViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            Log.i("Yousify", "[Settings] Sync button pressed")
            viewModel.syncSpotifyData()
            Toast.makeText(context, "Synchronization started...", Toast.LENGTH_SHORT).show()
        }) {
            Text("Sync with Spotify")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            scope.launch(Dispatchers.IO) {
                try {
                    val ytCacheDb = com.veshikov.yousify.data.model.YousifyDatabase.getInstance(context)
                    ytCacheDb.ytTrackCacheDao().clearAll()

                    val searchCacheDb = com.veshikov.yousify.youtube.SearchCacheDatabase.getInstance(context)
                    searchCacheDb.searchCacheDao().deleteOlderThan(System.currentTimeMillis()) // Очищает весь кэш

                    val sbCacheDb = com.veshikov.yousify.player.SponsorBlockDatabase.getInstance(context)
                    // sbCacheDb.sponsorBlockDao().clearAll() // TODO: Реализовать clearAll если нужно, или удалять по времени
                    val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
                    // sbCacheDb.sponsorBlockDao().deleteOlderThan(oneWeekAgo) // Если есть такой метод
                    // Пока нет метода clearAll() в SponsorBlockDao

                    withContext(Dispatchers.Main) {
                        Log.i("Yousify", "[Settings] Cleared YouTube related caches (SponsorBlock partially or not cleared)")
                        Toast.makeText(context, "YouTube caches cleared (SponsorBlock might require manual clear or has no full clear method)", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e("Yousify", "[Settings] Error clearing caches", e)
                        Toast.makeText(context, "Error clearing cache", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }) {
            Text("Clear YouTube Caches")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("About App", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Yousify — your music companion for Spotify and YouTube.", style = MaterialTheme.typography.bodyMedium)
    }
}