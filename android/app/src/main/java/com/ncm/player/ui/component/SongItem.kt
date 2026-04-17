package com.ncm.player.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ncm.player.model.Song
import com.ncm.player.util.ImageUtils

@Composable
fun SongItem(
    song: Song,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    onLikeClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    showDivider: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    Surface(
        modifier = modifier,
        color = containerColor
    ) {
        Column {
            ListItem(
                headlineContent = {
                    Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                },
                supportingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDownloaded) {
                            Icon(
                                Icons.Default.DownloadDone,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).padding(end = 4.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                leadingContent = leadingContent ?: {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        if (song.albumArtUrl != null) {
                            AsyncImage(
                                model = ImageUtils.getResizedImageUrl(song.albumArtUrl, 180),
                                contentDescription = null,
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.padding(12.dp))
                        }
                    }
                },
                trailingContent = trailingContent ?: if (onLikeClick != null) {
                    {
                        IconButton(onClick = onLikeClick) {
                            Icon(
                                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                } else null,
                modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            if (showDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
