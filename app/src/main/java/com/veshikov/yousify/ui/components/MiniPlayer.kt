package com.veshikov.yousify.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Keep this for standard icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.veshikov.yousify.R // For string resources
// Import R.drawable explicitly if you use any drawable, but we aim for Icons.Filled

@androidx.media3.common.util.UnstableApi
@Composable
fun MiniPlayer(
    state: MiniPlayerState,
    data: MiniPlayerData?,
    isExpanded: Boolean,
    events: MiniPlayerEvents,
    modifier: Modifier = Modifier
) {
    if (state == MiniPlayerState.HIDDEN || data == null) return

    val bottomSheetHeight by animateDpAsState(
        targetValue = if (isExpanded) 320.dp else 200.dp, // Высота в развернутом и свернутом состоянии
        label = "BottomSheetHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bottomSheetHeight)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest) // Slightly more elevated
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -50f && !isExpanded) { // Свайп вверх для разворачивания
                        events.onExpand()
                    } else if (dragAmount > 50f && isExpanded) { // Свайп вниз для сворачивания
                        events.onExpand()
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Полоска для перетаскивания
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    )
                    .align(Alignment.CenterHorizontally)
                    .clickable { events.onExpand() } // Разворачивание/сворачивание по клику на полоску
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isExpanded) 100.dp else 80.dp), // Немного увеличим высоту в развернутом для обложки
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Обложка трека
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(data.imageUrl)
                        .crossfade(true)
                        // Используем стандартную иконку как заглушку
                        .placeholder(R.drawable.ic_track_placeholder) // Используем наш векторный drawable
                        .error(R.drawable.ic_track_placeholder)       // Используем наш векторный drawable
                        .build(),
                    contentDescription = stringResource(R.string.track_cover_description),
                    modifier = Modifier
                        .size(if (isExpanded) 80.dp else 64.dp) // Размер обложки
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center // Центрируем текст по вертикали
                ) {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isExpanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = data.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (data.hasSponsorBlockSegments) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timeline, // Стандартная иконка
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.sponsor_block_active),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                IconButton(onClick = { events.onClose() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close_player),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isExpanded) 16.dp else 8.dp))

            when (state) {
                MiniPlayerState.LOADING -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                MiniPlayerState.ERROR -> {
                    ErrorView(message = data.errorMessage ?: stringResource(R.string.playback_error_default), onRetry = events::onRetry)
                }
                else -> { // PLAYING or PAUSED
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { events.onSkipPrevious() }) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.skip_previous),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = { events.onPlayPause() },
                            modifier = Modifier
                                .size(56.dp) // Основная кнопка Play/Pause
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(28.dp)
                                )
                        ) {
                            Icon(
                                imageVector = if (state == MiniPlayerState.PLAYING)
                                    Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    id = if (state == MiniPlayerState.PLAYING)
                                        R.string.pause else R.string.play
                                ),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(onClick = { events.onSkipNext() }) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = stringResource(R.string.skip_next),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded && (state == MiniPlayerState.PLAYING || state == MiniPlayerState.PAUSED || state == MiniPlayerState.ERROR),
                enter = expandVertically(expandFrom = Alignment.Top),
                exit = shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.playback_info),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.audio_only_mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // TODO: Здесь можно добавить SeekBar, если передавать прогресс
                }
            }
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null, // Декоративно
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = stringResource(R.string.retry))
        }
    }
}

// Preview для MiniPlayer
@Preview(showBackground = true, name = "MiniPlayer Paused")
@Composable
fun MiniPlayerPausedPreview() {
    MaterialTheme { // Используем MaterialTheme для превью
        MiniPlayer(
            state = MiniPlayerState.PAUSED,
            data = MiniPlayerData("track1", "Awesome Track Title", "Cool Artist Name", null, hasSponsorBlockSegments = true),
            isExpanded = false,
            events = object : MiniPlayerEvents {
                override fun onPlayPause() {}
                override fun onSkipNext() {}
                override fun onSkipPrevious() {}
                override fun onClose() {}
                override fun onExpand() {}
                override fun onRetry() {}
            }
        )
    }
}

@Preview(showBackground = true, name = "MiniPlayer Playing Expanded")
@Composable
fun MiniPlayerPlayingExpandedPreview() {
    MaterialTheme {
        MiniPlayer(
            state = MiniPlayerState.PLAYING,
            data = MiniPlayerData("track2", "Another Great Song With A Very Long Title That Might Wrap", "The Best Artist Ever", null, hasSponsorBlockSegments = false),
            isExpanded = true,
            events = object : MiniPlayerEvents {
                override fun onPlayPause() {}
                override fun onSkipNext() {}
                override fun onSkipPrevious() {}
                override fun onClose() {}
                override fun onExpand() {}
                override fun onRetry() {}
            }
        )
    }
}

@Preview(showBackground = true, name = "MiniPlayer Error")
@Composable
fun MiniPlayerErrorPreview() {
    MaterialTheme {
        MiniPlayer(
            state = MiniPlayerState.ERROR,
            data = MiniPlayerData("track3", "Error Track", "Problematic Artist", null, errorMessage = "A critical playback error occurred."),
            isExpanded = false,
            events = object : MiniPlayerEvents {
                override fun onPlayPause() {}
                override fun onSkipNext() {}
                override fun onSkipPrevious() {}
                override fun onClose() {}
                override fun onExpand() {}
                override fun onRetry() {}
            }
        )
    }
}