package com.veshikov.yousify.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.veshikov.yousify.data.model.Playlist
import com.veshikov.yousify.data.model.TrackEntity
import com.veshikov.yousify.data.model.TrackItem
import com.veshikov.yousify.ui.adapters.PlaylistAdapter
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import android.content.Intent
import com.veshikov.yousify.player.YtAudioService
import android.app.PictureInPictureParams
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.veshikov.yousify.data.SpotifyApiWrapper
import com.veshikov.yousify.auth.SpotifyAuthManager
import java.net.URL
import java.net.URLEncoder
import java.net.HttpURLConnection
import org.json.JSONObject
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon

@Composable
fun AuthScreen(onTokenReceived: (String) -> Unit) {
    var token by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Войти через Spotify", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Введите OAuth токен Spotify") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (token.isNotBlank()) {
                Log.i("Yousify", "[Auth] Token submitted: ${token.take(8)}... (len=${token.length})")
                onTokenReceived(token)
            }
        }) {
            Text("Синхронизировать")
        }
    }
}

@Composable
fun MainScreen(viewModel: YousifyViewModel? = null) {
    val actualViewModel = viewModel ?: androidx.lifecycle.viewmodel.compose.viewModel<YousifyViewModel>()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val apiWrapper = remember { SpotifyApiWrapper.getInstance() }
    var authed by remember { mutableStateOf(apiWrapper.getAccessToken() != null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember {
        SpotifyAuthManager(context) { code ->
            Log.i("YousifyAuth", "AUTH_CODE: $code")
            coroutineScope.launch {
                val prefs = context.getSharedPreferences(SpotifyAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
                val codeVerifier = prefs.getString(SpotifyAuthManager.CODE_VERIFIER_KEY, null)
                Log.i("YousifyAuth", "CODE_VERIFIER: $codeVerifier")
                if (codeVerifier != null) {
                    loading = true
                    error = null
                    val token = exchangeCodeForToken(code, codeVerifier)
                    Log.i("YousifyAuth", "TOKEN_EXCHANGE: $token")
                    if (token != null) {
                        coroutineScope.launch {
                            val ok = apiWrapper.initializeApiWithToken(token)
                            Log.i("YousifyAuth", "TOKEN_OK: $ok")
                            if (ok) {
                                authed = true
                                loading = false
                                error = null
                                // Триггерим синхронизацию после успешного входа
                                actualViewModel.sync()
                                actualViewModel.loadPlaylists()
                            } else {
                                loading = false
                                error = "Ошибка инициализации Spotify API"
                            }
                        }
                    } else {
                        loading = false
                        error = "Ошибка получения accessToken"
                        Log.e("YousifyAuth", "TOKEN_ERROR: token is null")
                    }
                } else {
                    loading = false
                    error = "Ошибка: отсутствует code_verifier"
                    Log.e("YousifyAuth", "TOKEN_ERROR: code_verifier is null")
                }
            }
        }
    }

    if (!authed) {
        Column(modifier = Modifier.padding(32.dp)) {
            Text("Войти через Spotify", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            if (!loading) {
                Button(
                    onClick = {
                        error = null
                        loading = true
                        activity?.let { authManager.startAuth(it) }
                    },
                    enabled = !loading
                ) {
                    Text("Войти через Spotify")
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Ожидание авторизации...")
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(error!!, color = Color.Red)
            }
        }
        return
    }

    // Основной UI приложения
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { YousifyBottomBar(navController) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            YousifyNavHost(navController, actualViewModel)
        }
    }
}

// Теперь suspend-функция, выполняющаяся в IO-потоке
suspend fun exchangeCodeForToken(code: String, codeVerifier: String): String? {
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
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            Log.i("YousifyAuth", "TOKEN_RESPONSE: code=$responseCode body=$response")
            if (responseCode == 200) {
                val token = JSONObject(response).optString("access_token", null)
                Log.i("YousifyAuth", "TOKEN_PARSED: $token")
                token
            } else {
                Log.e("YousifyAuth", "TOKEN_ERROR: $responseCode $response")
                null
            }
        } catch (e: Exception) {
            Log.e("YousifyAuth", "TOKEN_EXCEPTION: ${e.message}", e)
            null
        }
    }
}

@Composable
fun YousifyNavHost(navController: NavHostController, viewModel: YousifyViewModel) {
    NavHost(navController, startDestination = "playlists") {
        composable("playlists") {
            PlaylistsScreen(viewModel, navController)
        }
        composable("tracks/{playlistId}") { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId")
            if (playlistId != null) {
                TracksScreen(viewModel, playlistId, onTrackClick = { track ->
                    navController.navigate("track/${track.id}")
                })
            } else {
                Text("Не выбран плейлист")
            }
        }
        composable("liked_tracks") {
            LikedTracksScreen(viewModel)
        }
        composable("track/{trackId}") { backStackEntry ->
            val trackId = backStackEntry.arguments?.getString("trackId")
            val track = viewModel.tracks.collectAsState().value.find { it.id == trackId }
            if (track != null) {
                TrackDetailScreen(track, viewModel)
            } else {
                Text("Трек не найден")
            }
        }
        composable("search") {
            SearchScreen(viewModel)
        }
        composable("settings") {
            SettingsScreen(viewModel)
        }
    }
}

sealed class BottomNavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Playlists : BottomNavItem("playlists", "Playlists", Icons.Filled.List)
    object Search : BottomNavItem("search", "Search", Icons.Filled.Search)
    object Settings : BottomNavItem("settings", "Settings", Icons.Filled.Settings)
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
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                label = { Text(item.label) },
                icon = { Icon(item.icon, contentDescription = item.label) }
            )
        }
    }
}

@Composable
fun PlaylistsScreen(viewModel: YousifyViewModel, navController: NavHostController) {
    val playlistsEntities = viewModel.playlists.collectAsState().value
    val likedTracks = viewModel.likedTracks.collectAsState().value
    val playlists = playlistsEntities.map {
        Playlist(
            id = it.id,
            name = it.name,
            description = null, // Нет поля description
            uri = "", // Нет поля uri
            href = null,
            images = emptyList(),
            owner = null,
            tracks = null
        )
    }
    val adapter = remember { PlaylistAdapter { playlist ->
        if (playlist.id == PlaylistAdapter.LIKED_SONGS_ID) {
            navController.navigate("liked_tracks")
        } else {
            navController.navigate("tracks/${playlist.id}")
        }
    } }
    LaunchedEffect(Unit) {
        viewModel.loadLikedTracks()
    }
    val likedPlaylist = if (likedTracks.isNotEmpty()) Playlist(
        id = PlaylistAdapter.LIKED_SONGS_ID,
        name = "Liked Songs",
        description = null, // Нет поля description
        uri = "", // Нет поля uri
        href = null,
        images = emptyList(),
        owner = null,
        tracks = null
    ) else null
    LaunchedEffect(playlists, likedTracks) {
        adapter.submitPlaylistsWithLiked(playlists, likedPlaylist)
    }
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Ваши плейлисты", fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(12.dp))
        if (playlists.isEmpty() && likedPlaylist == null) {
            Text("Нет плейлистов. Синхронизируйте с Spotify.", color = Color.Gray)
        } else {
            AndroidView(factory = { context ->
                RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    this.adapter = adapter
                }
            })
        }
    }
}

@Composable
fun TracksScreen(viewModel: YousifyViewModel, playlistId: String?, onTrackClick: (TrackEntity) -> Unit = {}) {
    if (playlistId == null) {
        Text("Не выбран плейлист")
        return
    }
    val tracks = viewModel.tracks.collectAsState().value
    LazyColumn {
        items(tracks) { track ->
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .clickable { onTrackClick(track) }, elevation = CardDefaults.cardElevation(1.dp)) {
                Row(modifier = Modifier.padding(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, fontWeight = FontWeight.Bold)
                        Text(track.artist, color = Color.Gray)
                    }
                    Text("${track.durationMs/1000}s", modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
    }
}

@Composable
fun LikedTracksScreen(viewModel: YousifyViewModel, onTrackClick: (TrackItem) -> Unit = {}) {
    val likedTracks = viewModel.likedTracks.collectAsState().value
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Liked Songs", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(8.dp))
        if (likedTracks.isEmpty()) {
            Text("Нет лайкнутых треков.", color = Color.Gray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(likedTracks) { trackItem ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onTrackClick(trackItem) },
                        elevation = CardDefaults.cardElevation(2.dp),
                        border = BorderStroke(2.dp, Color(0xFF4CAF50))
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(trackItem.track?.name ?: "Неизвестный трек", fontWeight = FontWeight.Bold)
                                Text(trackItem.track?.artists?.joinToString(", ") { it.name ?: "" } ?: "", color = Color.Gray)
                            }
                            Text("${(trackItem.track?.durationMs ?: 0L) / 1000}s", modifier = Modifier.align(Alignment.CenterVertically))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackDetailScreen(track: TrackEntity, viewModel: YousifyViewModel) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var audioUrl by remember { mutableStateOf("") }
    var videoId by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var pipSupported = Build.VERSION.SDK_INT >= 26
    var pipLaunched by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(track.title, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(track.artist, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Text("ISRC: ${track.isrc ?: "-"}")
        Text("Длительность: ${track.durationMs/1000}s")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            loading = true
            Log.i("Yousify", "[Player] Play pressed for track: ${track.title} (${track.id})")
            scope.launch(Dispatchers.IO) {
                try {
                    val ytCacheDao = com.veshikov.yousify.youtube.SearchCacheDatabase.getInstance(context).searchCacheDao()
                    // Use track ID as cache key when ISRC is null or empty
                    val cacheKey = if (track.isrc.isNullOrEmpty()) "track:${track.id}" else track.isrc
                    val cached = ytCacheDao.getByIsrc(cacheKey)
                    if (cached != null) {
                        // sanitize: всегда извлекай только 11-символьный id
                        videoId = Regex("([a-zA-Z0-9_-]{11})").find(cached.youtubeUrl)?.value ?: cached.youtubeUrl.substringAfter("v=")
                        val ytUrl = "https://www.youtube.com/watch?v=$videoId"
                        val bestAudioUrl = com.veshikov.yousify.youtube.NewPipeHelper.getBestAudioUrl(ytUrl)
                        if (bestAudioUrl != null) {
                            audioUrl = bestAudioUrl
                            Log.i("Yousify", "[Player] Cache hit for key $cacheKey, got audioUrl")
                        } else {
                            Log.w("Yousify", "[Player] Cache hit but no audio stream for key $cacheKey")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Ошибка: нет аудиопотока для видео", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        val result = com.veshikov.yousify.youtube.SearchEngine.findBestYoutube(track)
                        if (result != null) {
                            // sanitize: всегда извлекай только 11-символьный id
                            videoId = Regex("([a-zA-Z0-9_-]{11})").find(result.videoId)?.value ?: result.videoId
                            val ytUrl = "https://www.youtube.com/watch?v=$videoId"
                            val bestAudioUrl = com.veshikov.yousify.youtube.NewPipeHelper.getBestAudioUrl(ytUrl)
                            if (bestAudioUrl != null) {
                                audioUrl = bestAudioUrl
                                ytCacheDao.insert(com.veshikov.yousify.youtube.SearchCacheEntry(cacheKey, result.videoId, System.currentTimeMillis()))
                                Log.i("Yousify", "[Player] Cache miss, found and cached videoId=${result.videoId}, got audioUrl")
                            } else {
                                Log.w("Yousify", "[Player] No audio stream found for videoId=${result.videoId}")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Ошибка: нет аудиопотока для видео", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Log.w("Yousify", "[Player] No suitable YouTube video found for track: ${track.title}")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Не найдено подходящее видео на YouTube", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Yousify", "[Player] Error playing track: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Ошибка при попытке воспроизведения: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    loading = false
                }
                
                if (audioUrl.isNotEmpty()) {
                    val intent = Intent(context, YtAudioService::class.java)
                    intent.putExtra("audioUrl", audioUrl)
                    intent.putExtra("videoId", videoId)
                    intent.putExtra("spotifyId", track.id)
                    context.startService(intent)
                    isPlaying = true
                    Log.i("Yousify", "[Player] Started YtAudioService for videoId=$videoId, audioUrl=$audioUrl")
                }
            }
        }, enabled = !loading) {
            Text(if (isPlaying) "Пауза" else "Играть")
        }
        if (pipSupported) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                if (context is android.app.Activity && Build.VERSION.SDK_INT >= 26) {
                    val params = PictureInPictureParams.Builder().build()
                    context.enterPictureInPictureMode(params)
                    pipLaunched = true
                    Log.i("Yousify", "[Player] Entered Picture-in-Picture mode for track: ${track.title}")
                }
            }) {
                Text("В режим PiP")
            }
        }
    }
    if (pipLaunched) {
        BackHandler {
            if (context is android.app.Activity) context.finish()
        }
    }
}

@Composable
fun SearchScreen(viewModel: YousifyViewModel) {
    val playlists = viewModel.playlists.collectAsState().value
    val allTracks = playlists.flatMap { pl ->
        viewModel.tracks.collectAsState().value.filter { it.playlistId == pl.id }
    }
    var query by remember { mutableStateOf("") }
    val filteredPlaylists = playlists.filter { it.name.contains(query, true) || it.owner.contains(query, true) }
    val filteredTracks = allTracks.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Поиск", fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Введите запрос") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Плейлисты", fontWeight = FontWeight.SemiBold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(120.dp)) {
            items(filteredPlaylists) { playlist ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        Text(playlist.name)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("(${playlist.owner})", color = Color.Gray)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Треки", fontWeight = FontWeight.SemiBold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(180.dp)) {
            items(filteredTracks) { track ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp)) {
                        Text(track.title)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(track.artist, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: YousifyViewModel) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Настройки", fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            // Повторная синхронизация с Spotify (использует последний токен)
            // TODO: хранить и подставлять актуальный токен пользователя
            // viewModel.sync(token)
            Log.i("Yousify", "[Settings] Sync button pressed")
        }) {
            Text("Синхронизировать")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            // Очистка кэша YouTube (очистка таблицы yt_track_cache)
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                val db = com.veshikov.yousify.youtube.SearchCacheDatabase.getInstance(context)
                db.clearAllTables()
                Log.i("Yousify", "[Settings] Cleared YouTube search cache")
            }
        }) {
            Text("Очистить кэш YouTube")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("О приложении")
        Text("Yousify — неофициальный клиент Spotify с поддержкой YouTube.")
    }
}
