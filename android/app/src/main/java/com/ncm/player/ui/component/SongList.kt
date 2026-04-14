package com.ncm.player.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    ListItem(
        headlineContent = {
            Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = {
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
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() }
    )
}

@Composable
fun SongList(
    songs: List<Song>,
    favoriteSongs: List<String> = emptyList(),
    completedSongs: Set<String> = emptySet(),
    onSongClick: (Song) -> Unit,
    onLikeClick: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: (@Composable () -> Unit)? = null
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        if (header != null) {
            item { header() }
        }
        items(songs, key = { it.id }) { song ->
            SongItem(
                song = song,
                isFavorite = favoriteSongs.contains(song.id),
                isDownloaded = completedSongs.contains(song.id),
                onLikeClick = onLikeClick?.let { { it(song) } },
                onClick = { onSongClick(song) }
            )
        }
    }
}
