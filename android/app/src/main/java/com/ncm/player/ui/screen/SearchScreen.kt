package com.ncm.player.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ncm.player.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchResults: List<Song>,
    favoriteSongs: List<String>,
    isLoading: Boolean,
    onSearch: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    onLikeClick: (Song) -> Unit
) {
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onSearch(it)
                        },
                        placeholder = { Text("Search songs") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        singleLine = true
                    )
                },
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(innerPadding))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(searchResults) { song ->
                SongItem(
                    song = song,
                    isFavorite = favoriteSongs.contains(song.id),
                    onLikeClick = { onLikeClick(song) },
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}
