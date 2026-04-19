package com.ncm.player.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.foundation.isSystemInDarkTheme
import com.ncm.player.util.ImageUtils
import com.ncm.player.model.Song
import com.ncm.player.ui.theme.createCustomColorScheme
import androidx.compose.foundation.shape.CircleShape

@Composable
fun BottomPlaybackBar(
    song: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useCoverColor: Boolean = false,
    coverColor: Int? = null
) {
    if (song == null) return

    val barColorScheme = if (useCoverColor && coverColor != null) {
        createCustomColorScheme(coverColor, isSystemInDarkTheme())
    } else {
        MaterialTheme.colorScheme
    }

    MaterialTheme(colorScheme = barColorScheme) {
        // MD3 Expressive Floating Pill-shaped Mini Player
        Surface(
            modifier = modifier
                .height(72.dp)
                .fillMaxWidth(0.9f)
                .padding(bottom = 8.dp) // Lift it up slightly
                .clickable { onClick() },
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            shape = CircleShape,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Avatar for Expressive feel
                AsyncImage(
                    model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 120),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Play/Pause Button
                    FilledIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        if (isBuffering) {
                            WavyCircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Skip Next Button (Optional in mini player, but keeping for utility)
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
