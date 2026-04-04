package com.ncm.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ncm.player.model.Playlist
import com.ncm.player.model.Song
import com.ncm.player.model.UserProfile
import com.ncm.player.util.ImageUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userProfile: UserProfile?,
    playlists: List<Playlist>,
    albums: List<Playlist> = emptyList(),
    songs: List<Song>,
    isArtist: Boolean = false,
    isLoading: Boolean = false,
    onPlaylistClick: (Playlist) -> Unit,
    onSongClick: (Song) -> Unit,
    onMessageClick: (Long, String) -> Unit,
    onBackPressed: () -> Unit
) {
    if (userProfile != null && userProfile.userId == 0L) {
         Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
             CircularProgressIndicator()
         }
         return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(userProfile?.nickname ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    userProfile?.let {
                        IconButton(onClick = { onMessageClick(it.userId, it.nickname) }) {
                            Icon(Icons.Default.Email, contentDescription = "Message")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading || userProfile == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            var isSongsExpanded by remember(userProfile.userId) { mutableStateOf(false) }
            var isAlbumsExpanded by remember(userProfile.userId) { mutableStateOf(false) }
            var isPlaylistsExpanded by remember(userProfile.userId) { mutableStateOf(false) }

            val currentUid = userProfile.userId

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 16.dp
                )
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = ImageUtils.getResizedImageUrl(userProfile.avatarUrl, 300),
                            contentDescription = null,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            userProfile.nickname,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        userProfile.signature?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (isArtist) {
                                UserStatItem(count = userProfile.eventCount, label = "Songs")
                                UserStatItem(count = userProfile.follows, label = "Albums")
                                UserStatItem(count = userProfile.followeds, label = "MVs")
                            } else {
                                UserStatItem(count = userProfile.follows, label = "Follows")
                                UserStatItem(count = userProfile.followeds, label = "Followers")
                                UserStatItem(count = userProfile.eventCount, label = "Events")
                            }
                        }
                    }
                }

                if (songs.isNotEmpty()) {
                    val displaySongs = if (isSongsExpanded) songs else songs.take(5)

                    item {
                        Text(
                            "Songs (${songs.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                    items(
                        items = displaySongs,
                        key = { "user_${userProfile.userId}_song_${it.id}" },
                        contentType = { "song" }
                    ) { song ->
                        SongItem(
                            song = song,
                            onClick = { onSongClick(song) }
                        )
                    }

                    if (songs.size > 5) {
                        item {
                            TextButton(
                                onClick = { isSongsExpanded = !isSongsExpanded },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Text(if (isSongsExpanded) "Show Less" else "Show All")
                            }
                        }
                    }
                }

                if (albums.isNotEmpty()) {
                    val displayAlbums = if (isAlbumsExpanded) albums else albums.take(5)

                    item {
                        Text(
                            "Albums (${albums.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                    items(
                        items = displayAlbums,
                        key = { "user_${userProfile.userId}_album_${it.id}" },
                        contentType = { "playlist" }
                    ) { album ->
                        PlaylistItem(
                            playlist = album,
                            onClick = { onPlaylistClick(album) }
                        )
                    }

                    if (albums.size > 5) {
                        item {
                            TextButton(
                                onClick = { isAlbumsExpanded = !isAlbumsExpanded },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Text(if (isAlbumsExpanded) "Show Less" else "Show All")
                            }
                        }
                    }
                }

                if (playlists.isNotEmpty()) {
                    val displayPlaylists = if (isPlaylistsExpanded) playlists else playlists.take(5)

                    item {
                        Text(
                            "Playlists (${playlists.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                    items(
                        items = displayPlaylists,
                        key = { "user_${userProfile.userId}_playlist_${it.id}" },
                        contentType = { "playlist" }
                    ) { playlist ->
                        PlaylistItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }

                    if (playlists.size > 5) {
                        item {
                            TextButton(
                                onClick = { isPlaylistsExpanded = !isPlaylistsExpanded },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Text(if (isPlaylistsExpanded) "Show Less" else "Show All")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserStatItem(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
