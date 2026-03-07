package com.mvwj.yousify.ui

import android.app.Activity
import android.app.Application
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
// import androidx.compose.ui.unit.sp // sp Ð¸Ð¼Ð¿Ð¾Ñ€Ñ‚Ð¸Ñ€ÑƒÐµÑ‚ÑÑ Ð¸Ð· MaterialTheme.typography
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mvwj.yousify.R
import com.mvwj.yousify.auth.AuthManager
import com.mvwj.yousify.auth.SecurePrefs
import com.mvwj.yousify.auth.SpotifyAuthManager
import com.mvwj.yousify.data.model.PlaylistEntity
import com.mvwj.yousify.data.model.TrackEntity
import com.mvwj.yousify.player.MiniPlayerController
import com.mvwj.yousify.ui.components.MiniPlayer
import com.mvwj.yousify.ui.components.MiniPlayerState
import com.mvwj.yousify.ui.viewmodel.YousifyViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.util.Locale

private enum class TrackSortMode {
    TitleLanguagePrimary,
    TitleLanguageSecondary
}

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
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Log.d("MainScreen", "Permission result: $result")
    }

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
                permissionLauncher.launch(permissionsArray)
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
    var spotifyClientIdInput by remember { mutableStateOf(SecurePrefs.spotifyClientId(context).orEmpty()) }
    var spotifyClientSecretInput by remember { mutableStateOf(SecurePrefs.spotifyClientSecret(context).orEmpty()) }

    val authManagerHelper = remember(context, activity) {
        SpotifyAuthManager(context) { code ->
            val scope = (activity as? ComponentActivity)?.lifecycleScope ?: lifecycleOwner.lifecycleScope
            scope.launch {
                val prefs = context.getSharedPreferences(SpotifyAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
                val codeVerifier = prefs.getString(SpotifyAuthManager.CODE_VERIFIER_KEY, null)
                if (codeVerifier != null) {
                    loadingLogin = true
                    loginError = null
                    val success = AuthManager.exchangeCodeForTokens(code, codeVerifier, context)
                    if (success) {
                        appViewModel.userJustLoggedIn()
                    } else {
                        loginError = context.getString(R.string.spotify_login_failed)
                        Log.e("YousifyAuth", "Spotify token exchange failed.")
                    }
                    loadingLogin = false
                } else {
                    loginError = context.getString(R.string.spotify_missing_verifier)
                    Log.e("YousifyAuth", "Missing PKCE code verifier.")
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
            Text(stringResource(R.string.spotify_login_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.spotify_credentials_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = spotifyClientIdInput,
                onValueChange = { spotifyClientIdInput = it },
                label = { Text(stringResource(R.string.spotify_client_id_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = spotifyClientSecretInput,
                onValueChange = { spotifyClientSecretInput = it },
                label = { Text(stringResource(R.string.spotify_client_secret_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (!loadingLogin) {
                Button(
                    onClick = {
                        loginError = null
                        val clientId = spotifyClientIdInput.trim()
                        val clientSecret = spotifyClientSecretInput.trim()
                        if (clientId.isEmpty() || clientSecret.isEmpty()) {
                            loginError = context.getString(R.string.spotify_credentials_required)
                            return@Button
                        }

                        SecurePrefs.saveSpotifyDeveloperCredentials(clientId, clientSecret, context)
                        activity?.let {
                            val started = authManagerHelper.startAuth(it)
                            if (!started) {
                                loginError = context.getString(R.string.spotify_credentials_required)
                            }
                        } ?: run {
                            Log.e("MainScreen", "Activity context is null, cannot start auth.")
                            loginError = context.getString(R.string.spotify_login_cannot_start)
                        }
                    },
                    enabled = !loadingLogin
                ) {
                    Text(stringResource(R.string.spotify_login_button))
                }
            } else {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.spotify_login_waiting))
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
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
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            val playlistName = playlists.firstOrNull { it.id == playlistId }?.name ?: stringResource(R.string.tracks)

            TracksScreen(
                playlistName = playlistName,
                tracks = tracksForPlaylist,
                onBack = { navController.popBackStack() },
                onTrackClick = { track, playlistContext ->
                    viewModel.playTrackInContext(track, playlistContext)
                },
                onQueueTrack = viewModel::enqueueTrack,
                onDisplayedTracksChanged = viewModel::updateCurrentPlaylistContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(viewModel: YousifyViewModel, navController: NavHostController) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isLoadingPlaylists by viewModel.isLoadingPlaylists.collectAsStateWithLifecycle()
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredPlaylists = playlists.filter { playlist ->
        searchQuery.isBlank() || playlist.name.contains(searchQuery, ignoreCase = true)
    }

    LaunchedEffect(Unit) {
        if (playlists.isEmpty() && viewModel.isUserLoggedIn.value) {
            viewModel.loadPlaylistsFromDb()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.playlists_title)) },
            actions = {
                IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_playlist_action))
                }
            }
        )

        if (isSearchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.search_playlists_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

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
                stringResource(R.string.no_playlists_available),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 20.dp)
            )
        } else if (filteredPlaylists.isEmpty()) {
            Text(
                stringResource(R.string.no_playlists_search_results),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPlaylists, key = { it.id }) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("tracks/${playlist.id}")
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            PlaylistCover(
                                imageUrl = playlist.imageUrl,
                                contentDescription = playlist.name,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(playlist.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(
                                        R.string.playlist_owner,
                                        playlist.owner.ifBlank { stringResource(R.string.unknown_owner) }
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCover(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .placeholder(R.drawable.ic_track_placeholder)
            .error(R.drawable.ic_track_placeholder)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    playlistName: String,
    tracks: List<TrackEntity>,
    onBack: () -> Unit,
    onTrackClick: (TrackEntity, List<TrackEntity>) -> Unit,
    onQueueTrack: (TrackEntity) -> Boolean,
    onDisplayedTracksChanged: (List<TrackEntity>) -> Unit
) {
    val context = LocalContext.current
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSortMenuVisible by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(TrackSortMode.TitleLanguagePrimary) }

    val filteredTracks = tracks.filter { track ->
        searchQuery.isBlank() ||
            track.title.contains(searchQuery, ignoreCase = true) ||
            track.artist.contains(searchQuery, ignoreCase = true)
    }

    val displayedTracks = when (sortMode) {
        TrackSortMode.TitleLanguagePrimary -> filteredTracks.sortedWith(
            compareBy<TrackEntity>(
                { detectTrackLanguageBucket(it.title) },
                { it.title.lowercase(Locale.ROOT) }
            )
        )
        TrackSortMode.TitleLanguageSecondary -> filteredTracks.sortedWith(
            compareBy<TrackEntity>(
                { -detectTrackLanguageBucket(it.title) },
                { it.title.lowercase(Locale.ROOT) }
            )
        )
    }

    LaunchedEffect(displayedTracks) {
        onDisplayedTracksChanged(displayedTracks)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                }
            },
            title = {
                Text(
                    playlistName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            actions = {
                Box {
                    IconButton(onClick = { isSortMenuVisible = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.sort_tracks_action))
                    }
                    DropdownMenu(
                        expanded = isSortMenuVisible,
                        onDismissRequest = { isSortMenuVisible = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_language_primary)) },
                            onClick = {
                                sortMode = TrackSortMode.TitleLanguagePrimary
                                isSortMenuVisible = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sort_by_language_secondary)) },
                            onClick = {
                                sortMode = TrackSortMode.TitleLanguageSecondary
                                isSortMenuVisible = false
                            }
                        )
                    }
                }
                IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_track_action))
                }
            }
        )

        if (isSearchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.search_tracks_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }
        when {
            tracks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.no_tracks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            displayedTracks.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.no_tracks_search_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedTracks, key = { "${it.playlistId}:${it.position}" }) { track ->
                        QueueableTrackCard(
                            track = track,
                            onClick = { onTrackClick(track, displayedTracks) },
                            onQueue = {
                                val added = onQueueTrack(track)
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (added) R.string.track_added_to_queue else R.string.track_already_queued
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                                added
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueableTrackCard(
    track: TrackEntity,
    onClick: () -> Unit,
    onQueue: () -> Boolean
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxDragPx = with(density) { 112.dp.toPx() }
    val queueThresholdPx = with(density) { 72.dp.toPx() }
    val offsetX = remember(track.playlistId, track.position) { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.queue_next),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelLarge
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clickable(onClick = onClick)
                .pointerInput(track.playlistId, track.position) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val nextOffset = (offsetX.value + dragAmount).coerceIn(0f, maxDragPx)
                            scope.launch {
                                offsetX.snapTo(nextOffset)
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value >= queueThresholdPx) {
                                    offsetX.animateTo(
                                        targetValue = maxDragPx,
                                        animationSpec = tween(durationMillis = 140)
                                    )
                                    onQueue()
                                }
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 220)
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 220)
                                )
                            }
                        }
                    )
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(track.imageUrl)
                            .crossfade(true)
                            .placeholder(R.drawable.ic_track_placeholder)
                            .error(R.drawable.ic_track_placeholder)
                            .build(),
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        track.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
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

private fun detectTrackLanguageBucket(title: String): Int {
    val cyrillicCount = title.count { it in '\u0400'..'\u04FF' }
    val latinCount = title.count { it.isLetter() && it.code in 65..122 }
    return when {
        cyrillicCount > latinCount && cyrillicCount > 0 -> 0
        latinCount > cyrillicCount && latinCount > 0 -> 1
        cyrillicCount > 0 && latinCount > 0 -> 2
        else -> 3
    }
}

@Composable
fun TrackDetailScreen(track: TrackEntity, viewModel: YousifyViewModel) {
    val context = LocalContext.current
    val activity = LocalContext.current as? Activity
    var pipLaunched by remember { mutableStateOf(false) }
    val pipErrorMessage = stringResource(R.string.pip_error)

    val currentMiniPlayerTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val miniPlayerActualState by viewModel.miniPlayerState.collectAsStateWithLifecycle()

    val isPlayingThisTrackInMiniPlayer =
        currentMiniPlayerTrack?.playlistId == track.playlistId &&
            currentMiniPlayerTrack?.position == track.position &&
            miniPlayerActualState == MiniPlayerState.PLAYING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(track.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
        Text(track.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.isrc_label, track.isrc ?: "-"), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.duration_seconds, track.durationMs / 1000), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (
                    currentMiniPlayerTrack?.playlistId == track.playlistId &&
                    currentMiniPlayerTrack?.position == track.position
                ) {
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
                        Toast.makeText(context, pipErrorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text(stringResource(R.string.enter_pip))
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

    // Ð’Ð«ÐÐžÐ¡Ð˜Ðœ Ð¡Ð®Ð”Ð: tracksOfCurrentContext Ð´Ð¾Ð»Ð¶ÐµÐ½ Ð±Ñ‹Ñ‚ÑŒ Ð¿Ð¾Ð»ÑƒÑ‡ÐµÐ½ Ð² Composable ÐºÐ¾Ð½Ñ‚ÐµÐºÑÑ‚Ðµ SearchScreen
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
            label = { Text(stringResource(R.string.search_all_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (query.isNotBlank()) {
                val filteredPlaylists = allPlaylists.filter {
                    it.name.contains(query, ignoreCase = true) || it.owner.contains(query, ignoreCase = true)
                }
                item {
                    Text(
                        stringResource(R.string.found_playlists, filteredPlaylists.size),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
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
                                PlaylistCover(
                                    imageUrl = playlist.imageUrl,
                                    contentDescription = playlist.name,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(end = 8.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(playlist.name, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        playlist.owner.ifBlank { stringResource(R.string.unknown_owner) },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            stringResource(R.string.no_playlists_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }

                // Ð˜ÑÐ¿Ð¾Ð»ÑŒÐ·ÑƒÐµÐ¼ tracksOfCurrentContext, ÐºÐ¾Ñ‚Ð¾Ñ€Ñ‹Ð¹ ÑƒÐ¶Ðµ Ð¿Ð¾Ð»ÑƒÑ‡ÐµÐ½
                val filteredTracks = tracksOfCurrentContext.filter {
                    it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
                }
                item {
                    Text(
                        stringResource(R.string.found_tracks_in_context, filteredTracks.size),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (filteredTracks.isNotEmpty()) {
                    items(filteredTracks, key = { "track-${it.playlistId}-${it.position}" }) { track ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.playTrackInContext(track, tracksOfCurrentContext) },
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = track.imageUrl,
                                    contentDescription = track.title,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(end = 8.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
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
                        Text(
                            stringResource(R.string.no_tracks_found_in_context),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.enter_search_query),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
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
    val cacheClearedMessage = stringResource(R.string.cache_cleared)
    val cacheClearErrorMessage = stringResource(R.string.cache_clear_error)
    var spotifyClientIdInput by remember { mutableStateOf(SecurePrefs.spotifyClientId(context).orEmpty()) }
    var spotifyClientSecretInput by remember { mutableStateOf(SecurePrefs.spotifyClientSecret(context).orEmpty()) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Text(stringResource(R.string.spotify_credentials_section_title), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.spotify_credentials_settings_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = spotifyClientIdInput,
            onValueChange = { spotifyClientIdInput = it },
            label = { Text(stringResource(R.string.spotify_client_id_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = spotifyClientSecretInput,
            onValueChange = { spotifyClientSecretInput = it },
            label = { Text(stringResource(R.string.spotify_client_secret_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                val clientId = spotifyClientIdInput.trim()
                val clientSecret = spotifyClientSecretInput.trim()
                if (clientId.isEmpty() || clientSecret.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.spotify_credentials_required), Toast.LENGTH_SHORT).show()
                } else {
                    SecurePrefs.saveSpotifyDeveloperCredentials(clientId, clientSecret, context)
                    Toast.makeText(context, context.getString(R.string.spotify_credentials_saved), Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Text(stringResource(R.string.spotify_save_credentials))
        }
        Spacer(modifier = Modifier.height(24.dp))

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
                    Text(stringResource(R.string.syncing))
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
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
                        Toast.makeText(context, cacheClearedMessage, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e("YousifySettings", "[Settings] Error clearing caches", e)
                        Toast.makeText(context, cacheClearErrorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
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
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize), tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.logout_from_spotify), color = MaterialTheme.colorScheme.onErrorContainer)
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        // Ð£Ð±ÐµÐ´Ð¸Ñ‚ÐµÑÑŒ, Ñ‡Ñ‚Ð¾ R.string.app_info Ð¾Ð¿Ñ€ÐµÐ´ÐµÐ»ÐµÐ½ Ð² strings.xml.
        // Ð­Ñ‚Ð¾ Ð±Ñ‹Ð»Ð° ÑÑ‚Ñ€Ð¾ÐºÐ° 580 Ð² ÑÑ‚Ð°Ñ€Ð¾Ð¼ Ð»Ð¾Ð³Ðµ Ð¾ÑˆÐ¸Ð±ÐºÐ¸, ÑƒÐ±ÐµÐ´Ð¸Ð¼ÑÑ Ñ‡Ñ‚Ð¾ MaterialTheme.typography Ñ‚ÐµÐ¿ÐµÑ€ÑŒ ÐºÐ¾Ñ€Ñ€ÐµÐºÑ‚Ð½Ð¾ Ð¸Ð· M3
        Text(stringResource(R.string.app_info), style = MaterialTheme.typography.bodyMedium)
    }
}
