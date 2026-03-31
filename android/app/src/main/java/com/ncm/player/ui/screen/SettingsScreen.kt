package com.ncm.player.ui.screen

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentQuality: String,
    onQualityChange: (String) -> Unit,
    downloadQuality: String,
    onDownloadQualityChange: (String) -> Unit,
    fadeDuration: Float,
    onFadeChange: (Float) -> Unit,
    cacheSize: Int,
    onCacheSizeChange: (Int) -> Unit,
    useCellularCache: Boolean,
    onUseCellularCacheChange: (Boolean) -> Unit,
    downloadDir: String?,
    onDownloadDirChange: (String) -> Unit,
    onClearCache: () -> Unit,
    onBackPressed: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dirPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onDownloadDirChange(it.toString())
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets.statusBars.add(WindowInsets(top = 8.dp))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Playback Quality", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            val qualities = listOf("standard", "higher", "exhigh", "lossless", "hires")
            qualities.forEach { quality ->
                Surface(
                    onClick = { onQualityChange(quality) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (quality == currentQuality) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (quality == currentQuality),
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(quality.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Download Quality", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            qualities.forEach { quality ->
                Surface(
                    onClick = { onDownloadQualityChange(quality) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (quality == downloadQuality) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (quality == downloadQuality),
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(quality.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("Audio Effects", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Fade In/Out Duration (${fadeDuration.toInt()}s)", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = fadeDuration,
                onValueChange = onFadeChange,
                valueRange = 0f..10f,
                steps = 10
            )

            Spacer(modifier = Modifier.height(32.dp))
            Text("Storage", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Allow Cellular Data Caching")
                Switch(
                    checked = useCellularCache,
                    onCheckedChange = onUseCellularCacheChange
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Max Cache Size: ${cacheSize}MB")
            Slider(
                value = cacheSize.toFloat(),
                onValueChange = { onCacheSizeChange(it.toInt()) },
                valueRange = 100f..2048f,
                steps = 19
            )

            Button(
                onClick = onClearCache,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear Playback Cache")
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("Download Location", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("Download Directory") },
                supportingContent = { Text(downloadDir ?: "Not set (Using system Music folder)") },
                modifier = Modifier.clickable { dirPicker.launch(null) }
            )
        }
    }
}
