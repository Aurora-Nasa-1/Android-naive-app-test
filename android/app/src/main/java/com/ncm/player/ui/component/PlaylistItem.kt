package com.ncm.player.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ncm.player.model.Playlist
import com.ncm.player.util.ImageUtils

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent
) {
    ListItem(
        headlineContent = { Text(playlist.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text("${playlist.trackCount} songs", style = MaterialTheme.typography.bodyMedium) },
        leadingContent = {
            Surface(
                modifier = Modifier.size(56.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                if (playlist.coverImgUrl != null) {
                    AsyncImage(
                        model = ImageUtils.getResizedImageUrl(playlist.coverImgUrl, 180),
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = containerColor)
    )
}
