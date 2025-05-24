package com.veshikov.yousify.ui

import android.app.Activity
import android.app.Application
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
// import androidx.compose.ui.unit.sp // sp импортируется из MaterialTheme.typography
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.veshikov.yousify.R
import com.veshikov.yousify.auth.AuthManager
import com.veshikov.yousify.auth.SecurePrefs
import com.veshikov.yousify.auth.SpotifyAuthManager
import com.veshikov.yousify.data.model.PlaylistEntity
import com.veshikov.yousify.data.model.TrackEntity
import com.veshikov.yousify.player.MiniPlayerController
import com.veshikov.yousify.ui.components.MiniPlayer
import com.veshikov.yousify.ui.components.MiniPlayerState
import com.veshikov.yousify.ui.viewmodel.YousifyViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Playlists : BottomNavItem("playlists", "Playlists", Icons.Filled.LibraryMusic)
    object Search : BottomNavItem("search", "Search", Icons.Filled.Search)
    object Settings : BottomNavItem("settings", "Settings", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.media3.common.util.UnstableApi
@Composable
fun MainScreen(
    appViewModel: YousifyViewModel = viewModel(
        factory = YousifyViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current as? Activity

    var authed by remember { mutableStateOf(SecurePrefs.accessToken(context) != null) }

    LaunchedEffect(Unit) {
        appViewModel.isUserLoggedIn.collect { isLoggedIn ->
            authed = isLoggedIn
            if (isLoggedIn && appViewModel.playlists.value.isEmpty()) {
                appViewModel.loadPlaylistsFromDb()
            }
        }
    }

    val miniPlayerController = remember(context, lifecycleOwner, appViewModel) {
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
    DisposableEffect(Unit) {
        onDispose {
            miniPlayerController.release()
        }
    }

    var loadingLogin by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    val authManagerHelper = remember(context, activity) {
        SpotifyAuthManager(context) { code ->
            Log.i("YousifyAuth", "AUTH_CODE_RECEIVED: $code")
            val scope = (activity as? ComponentActivity)?.lifecycleScope ?: lifecycleOwner.lifecycleScope
            scope.launch {
                val prefs = context.getSharedPreferences(SpotifyAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
                val codeVerifier = prefs.getString(SpotifyAuthManager.CODE_VERIFIER_KEY, null)
                Log.i("YousifyAuth", "STORED_CODE_VERIFIER: $codeVerifier")
                if (codeVerifier != null) {
                    loadingLogin = true
                    loginError = null
                    val success = AuthManager.exchangeCodeForTokens(code, codeVerifier, context)
                    if (success) {
                        Log.i("YousifyAuth", "TOKEN_EXCHANGE_SUCCESS. Notifying ViewModel.")
                        appViewModel.userJustLoggedIn()
                    } else {
                        loginError = "Failed to get access token from Spotify."
                        Log.e("YousifyAuth", "TOKEN_EXCHANGE_FAILED.")
                    }
                    loadingLogin = false
                } else {
                    loginError = "Error: Auth session issue (missing verifier)."
                    Log.e("YousifyAuth", "CODE_VERIFIER_IS_NULL.")
                    loadingLogin = false
                }
            }
        }
    }

    if (!authed) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Login via Spotify", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))
            if (!loadingLogin) {
                Button(
                    onClick = {
                        loginError = null
                        activity?.let {
                            Log.i("MainScreen", "Starting Spotify Auth Flow")
                            authManagerHelper.startAuth(it)
                        } ?: run {
                            Log.e("MainScreen", "Activity context is null, cannot start auth.")
                            loginError = "Cannot start login process."
                        }
                    },
                    enabled = !loadingLogin
                ) {
                    Text("Login with Spotify")
                }
            } else {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Waiting for authorization...")
            }
            loginError?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
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
            YousifyNavHost(navController, appViewModel)

            val miniPlayerCurrentState by miniPlayerController.miniPlayerState.collectAsStateWithLifecycle()
            val miniPlayerData by miniPlayerController.miniPlayerData.collectAsStateWithLifecycle()
            val isMiniPlayerExpanded by miniPlayerController.isExpanded.collectAsStateWithLifecycle()

            AnimatedVisibility(
                visible = miniPlayerCurrentState != MiniPlayerState.HIDDEN && miniPlayerData != null,
                enter = slideInVertically(initialOffsetY = { it / 2 }),
                exit = slideOutVertically(targetOffsetY = { it / 2 }),
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
fun YousifyNavHost(
    navController: NavHostController,
    viewModel: YousifyViewModel
) {
    NavHost(navController, startDestination = BottomNavItem.Playlists.route) {
        composable(BottomNavItem.Playlists.route) {
            PlaylistsScreen(viewModel, navController)
        }
        composable("tracks/{playlistId}") { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId")
            LaunchedEffect(playlistId) {
                playlistId?.let { viewModel.selectPlaylistForViewing(it) }
            }
            val tracksForPlaylist by viewModel.tracksForSelectedPlaylist.collectAsStateWithLifecycle()

            TracksScreen(
                tracks = tracksForPlaylist,
                onTrackClick = { track ->
                    viewModel.playTrackInContext(track, tracksForPlaylist)
                }
            )
        }
        composable("track_detail/{trackId}") { backStackEntry ->
            val trackId = backStackEntry.arguments?.getString("trackId")
            val tracksFromVm by viewModel.tracksForSelectedPlaylist.collectAsStateWithLifecycle()
            val track = tracksFromVm.find { it.id == trackId }

            if (track != null) {
                TrackDetailScreen(track = track, viewModel = viewModel)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Track not found (ID: $trackId)")
                }
            }
        }
        composable(BottomNavItem.Search.route) {
            SearchScreen(viewModel = viewModel, navController = navController)
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
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isLoadingPlaylists by viewModel.isLoadingPlaylists.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (playlists.isEmpty() && viewModel.isUserLoggedIn.value) {
            viewModel.loadPlaylistsFromDb()
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.playlist), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { viewModel.syncSpotifyData() },
                enabled = !isLoadingPlaylists
            ) {
                if (isLoadingPlaylists && playlists.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.sync))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoadingPlaylists && playlists.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (playlists.isEmpty()) {
            Text(
                "No playlists found. Try syncing or create playlists in Spotify.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 20.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("tracks/${playlist.id}")
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.QueueMusic,
                                contentDescription = playlist.name,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(playlist.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Owner: ${playlist.owner.ifEmpty { "Unknown" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.no_tracks), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tracks, key = { it.id }) { track ->
            Card(modifier = Modifier
                .fillMaxWidth()
                .clickable { onTrackClick(track) },
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = track.title,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${track.durationMs / 1000 / 60}:${(track.durationMs / 1000 % 60).toString().padStart(2, '0')}",
                        modifier = Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TrackDetailScreen(track: TrackEntity, viewModel: YousifyViewModel) {
    val context = LocalContext.current
    val activity = LocalContext.current as? Activity
    var pipLaunched by remember { mutableStateOf(false) }

    val currentMiniPlayerTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val miniPlayerActualState by viewModel.miniPlayerState.collectAsStateWithLifecycle()

    val isPlayingThisTrackInMiniPlayer = currentMiniPlayerTrack?.id == track.id && miniPlayerActualState == MiniPlayerState.PLAYING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(track.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
        Text(track.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Text("ISRC: ${track.isrc ?: "-"}", style = MaterialTheme.typography.bodyMedium)
        Text("Duration: ${track.durationMs / 1000}s", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (currentMiniPlayerTrack?.id == track.id) {
                    if (miniPlayerActualState == MiniPlayerState.PLAYING) {
                        viewModel.pauseCurrentTrack()
                    } else {
                        viewModel.resumeCurrentTrack()
                    }
                } else {
                    viewModel.playTrackInContext(track, listOf(track))
                }
            }
        ) {
            Text(if (isPlayingThisTrackInMiniPlayer) stringResource(R.string.pause) else stringResource(R.string.play))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null && activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        if (!activity.isInPictureInPictureMode) {
                            val params = PictureInPictureParams.Builder().build()
                            val entered = activity.enterPictureInPictureMode(params)
                            if(entered) pipLaunched = true
                            else Log.w("TrackDetailScreen", "Failed to enter PiP mode via activity method.")
                        }
                    } catch (e: Exception) {
                        Log.e("TrackDetailScreen", "Error entering PiP mode: ${e.message}", e)
                        Toast.makeText(context, "Error entering PiP mode", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text("Enter Picture-in-Picture")
            }
        }
    }

    if (pipLaunched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
        BackHandler {
            pipLaunched = false
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun SearchScreen(
    viewModel: YousifyViewModel,
    navController: NavHostController
) {
    val allPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    // ВЫНОСИМ СЮДА: tracksOfCurrentContext должен быть получен в Composable контексте SearchScreen
    val tracksOfCurrentContext by viewModel.tracksForSelectedPlaylist.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.search), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search playlists, tracks, artists") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search Icon") }
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (query.isNotBlank()) {
                val filteredPlaylists = allPlaylists.filter {
                    it.name.contains(query, ignoreCase = true) || it.owner.contains(query, ignoreCase = true)
                }
                item {
                    Text("Found Playlists (${filteredPlaylists.size})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (filteredPlaylists.isNotEmpty()) {
                    items(filteredPlaylists, key = { "playlist-${it.id}" }) { playlist ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { navController.navigate("tracks/${playlist.id}") },
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.QueueMusic,
                                    contentDescription = playlist.name,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(end = 8.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                                    Text(playlist.owner, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text("No playlists found.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }

                // Используем tracksOfCurrentContext, который уже получен
                val filteredTracks = tracksOfCurrentContext.filter {
                    it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
                }
                item {
                    Text("Found Tracks in current context (${filteredTracks.size})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (filteredTracks.isNotEmpty()) {
                    items(filteredTracks, key = { "track-${it.id}" }) { track ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.playTrackInContext(track, tracksOfCurrentContext) },
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = track.title,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(end = 8.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = "${track.durationMs / 1000 / 60}:${(track.durationMs / 1000 % 60).toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text("No tracks found in the current context.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            } else {
                item {
                    Text("Enter a query to search.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: YousifyViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                Log.i("YousifySettings", "[Settings] Sync button pressed")
                viewModel.syncSpotifyData()
            },
            enabled = !isSyncing
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing...")
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync Icon", modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.sync))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            scope.launch {
                try {
                    viewModel.clearYouTubeCaches()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "YouTube related caches cleared", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e("YousifySettings", "[Settings] Error clearing caches", e)
                        Toast.makeText(context, "Error clearing cache", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear Cache Icon", modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.clear_cache))
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    viewModel.logout()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Icon(Icons.Filled.Logout, contentDescription = "Logout Icon", modifier = Modifier.size(ButtonDefaults.IconSize), tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Logout from Spotify", color = MaterialTheme.colorScheme.onErrorContainer)
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        // Убедитесь, что R.string.app_info определен в strings.xml.
        // Это была строка 580 в старом логе ошибки, убедимся что MaterialTheme.typography теперь корректно из M3
        Text(stringResource(R.string.app_info), style = MaterialTheme.typography.bodyMedium)
    }
}