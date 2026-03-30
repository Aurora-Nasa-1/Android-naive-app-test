package com.ncm.player.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ncm.player.model.Song
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackPressed: () -> Unit,
    onPlayLocalSong: (Song, android.net.Uri) -> Unit
) {
    val context = LocalContext.current
    var localSongs by remember { mutableStateOf(listLocalSongs(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (localSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("No downloaded songs found.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        items(localSongs) { (song, uri) ->
                    ListItem(
                        headlineContent = { Text(song.name) },
                        supportingContent = { Text(song.artist) },
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.padding(8.dp))
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                context.contentResolver.delete(uri, null, null)
                                localSongs = listLocalSongs(context)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        },
                        modifier = Modifier.clickable { onPlayLocalSong(song, uri) }
                    )
                }
            }
        }
    }
}

private fun listLocalSongs(context: android.content.Context): List<Pair<Song, android.net.Uri>> {
    val userDirUri = com.ncm.player.util.UserPreferences.getDownloadDir(context)
    val files = mutableListOf<Pair<Song, android.net.Uri>>()

    if (userDirUri != null) {
        val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(userDirUri))
        tree?.listFiles()?.forEach { file ->
            if (file.name?.endsWith(".mp3") == true) {
                files.add(Song(
                    id = "local_${file.name}",
                    name = file.name?.removeSuffix(".mp3") ?: "Unknown",
                    artist = "Local",
                    album = "Downloads"
                ) to file.uri)
            }
        }
    }

    // Always check system Music folder as well
    val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
    musicDir.listFiles { _, name -> name.endsWith(".mp3") }?.forEach { file ->
        files.add(Song(
            id = "local_${file.name}",
            name = file.nameWithoutExtension,
            artist = "Local File",
            album = "Downloads"
        ) to android.net.Uri.fromFile(file))
    }

    return files.distinctBy { it.second }
}
