package com.mvwj.yousify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mvwj.yousify.R

private fun formatPlayerTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

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

    var sliderProgress by remember(data.trackId) { mutableStateOf<Float?>(null) }
    val sheetHeight = 220.dp
    val safeDurationMs = data.durationMs.coerceAtLeast(0L)
    val safePositionMs = data.currentPositionMs.coerceIn(0L, if (safeDurationMs > 0L) safeDurationMs else Long.MAX_VALUE)
    val progress = if (safeDurationMs > 0L) {
        (safePositionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayedProgress = sliderProgress ?: progress
    val displayedPositionMs = if (safeDurationMs > 0L) {
        (safeDurationMs * displayedProgress).toLong().coerceIn(0L, safeDurationMs)
    } else {
        0L
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerInput(data.trackId) {
                detectTapGestures(onTap = {})
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    )
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(data.imageUrl)
                        .crossfade(true)
                        .placeholder(R.drawable.ic_track_placeholder)
                        .error(R.drawable.ic_track_placeholder)
                        .build(),
                    contentDescription = stringResource(R.string.track_cover_description),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
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
                                imageVector = Icons.Filled.Timeline,
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

                IconButton(onClick = events::onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close_player),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (state != MiniPlayerState.ERROR) {
                Slider(
                    value = displayedProgress,
                    onValueChange = { sliderProgress = it },
                    onValueChangeFinished = {
                        val targetProgress = sliderProgress ?: progress
                        val targetPositionMs = if (safeDurationMs > 0L) {
                            (safeDurationMs * targetProgress).toLong().coerceIn(0L, safeDurationMs)
                        } else {
                            0L
                        }
                        sliderProgress = null
                        events.onSeekTo(targetPositionMs)
                    },
                    enabled = safeDurationMs > 0L,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatPlayerTime(displayedPositionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPlayerTime(safeDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            when (state) {
                MiniPlayerState.LOADING -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                MiniPlayerState.ERROR -> {
                    ErrorView(
                        message = data.errorMessage ?: stringResource(R.string.playback_error_default),
                        onRetry = events::onRetry
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            IconButton(
                                onClick = events::onSkipPrevious,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = stringResource(R.string.skip_previous),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            IconButton(
                                onClick = events::onPlayPause,
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(32.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = if (state == MiniPlayerState.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(if (state == MiniPlayerState.PLAYING) R.string.pause else R.string.play),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = events::onSkipNext,
                                modifier = Modifier.size(48.dp)
                            ) {
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
            textAlign = TextAlign.Center
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
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = stringResource(R.string.retry))
        }
    }
}

@Preview(showBackground = true, name = "MiniPlayer Paused")
@Composable
fun MiniPlayerPausedPreview() {
    MaterialTheme {
        MiniPlayer(
            state = MiniPlayerState.PAUSED,
            data = MiniPlayerData(
                trackId = "track1",
                title = "Awesome Track Title",
                artist = "Cool Artist Name",
                imageUrl = null,
                currentPositionMs = 42_000L,
                durationMs = 180_000L,
                hasSponsorBlockSegments = true
            ),
            isExpanded = false,
            events = object : MiniPlayerEvents {
                override fun onPlayPause() {}
                override fun onSkipNext() {}
                override fun onSkipPrevious() {}
                override fun onSeekTo(positionMs: Long) {}
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
            data = MiniPlayerData(
                trackId = "track2",
                title = "Another Great Song With A Very Long Title That Might Wrap",
                artist = "The Best Artist Ever",
                imageUrl = null,
                currentPositionMs = 87_000L,
                durationMs = 215_000L,
                hasSponsorBlockSegments = false
            ),
            isExpanded = true,
            events = object : MiniPlayerEvents {
                override fun onPlayPause() {}
                override fun onSkipNext() {}
                override fun onSkipPrevious() {}
                override fun onSeekTo(positionMs: Long) {}
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
            data = MiniPlayerData(
                trackId = "track3",
                title = "Error Track",
                artist = "Problematic Artist",
                imageUrl = null,
                currentPositionMs = 0L,
                durationMs = 180_000L,
                errorMessage = "A critical playback error occurred."
            ),
            isExpanded = false,
            events = object : MiniPlayerEvents {
                override fun onPlayPause() {}
                override fun onSkipNext() {}
                override fun onSkipPrevious() {}
                override fun onSeekTo(positionMs: Long) {}
                override fun onClose() {}
                override fun onExpand() {}
                override fun onRetry() {}
            }
        )
    }
}
