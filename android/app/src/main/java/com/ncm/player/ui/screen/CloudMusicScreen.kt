package com.ncm.player.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ncm.player.R
import com.ncm.player.model.Song
import com.ncm.player.ui.component.SongItem
import com.ncm.player.ui.component.WavyCircularProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudMusicScreen(
    songs: List<Song>,
    favoriteSongs: List<String>,
    isLoading: Boolean,
    onSongClick: (Song) -> Unit,
    onLikeClick: (Song) -> Unit,
    onBackPressed: () -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cloud_music)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isLoading && songs.isEmpty()) {
                WavyCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (songs.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_cloud_songs),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
                    )
                ) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                        SongItem(
                            song = song,
                            isFavorite = favoriteSongs.contains(song.id),
                            onClick = { onSongClick(song) },
                            onLikeClick = { onLikeClick(song) },
                            showDivider = index < songs.size - 1
                        )
                    }
                }
            }
        }
    }
}
