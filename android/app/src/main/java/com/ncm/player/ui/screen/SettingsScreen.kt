package com.ncm.player.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentQualityWifi: String,
    onQualityWifiChange: (String) -> Unit,
    currentQualityCellular: String,
    onQualityCellularChange: (String) -> Unit,
    downloadQuality: String,
    onDownloadQualityChange: (String) -> Unit,
    fadeDuration: Float,
    onFadeChange: (Float) -> Unit,
    cacheSize: Int,
    onCacheSizeChange: (Int) -> Unit,
    useCellularCache: Boolean,
    onUseCellularCacheChange: (Boolean) -> Unit,
    allowCellularDownload: Boolean,
    onAllowCellularDownloadChange: (Boolean) -> Unit,
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
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCategory("Playback Quality") {
                QualitySettingItem(
                    icon = Icons.Default.Wifi,
                    label = "WiFi Quality",
                    selectedQuality = currentQualityWifi,
                    onQualityChange = onQualityWifiChange
                )
                QualitySettingItem(
                    icon = Icons.Default.CellTower,
                    label = "Cellular Quality",
                    selectedQuality = currentQualityCellular,
                    onQualityChange = onQualityCellularChange
                )
            }

            SettingsCategory("Download Settings") {
                QualitySettingItem(
                    icon = Icons.Default.HighQuality,
                    label = "Download Quality",
                    selectedQuality = downloadQuality,
                    onQualityChange = onDownloadQualityChange
                )

                SwitchSettingItem(
                    icon = Icons.Default.Download,
                    title = "Allow Cellular Download",
                    subtitle = "Allow downloading songs over mobile data",
                    checked = allowCellularDownload,
                    onCheckedChange = onAllowCellularDownloadChange
                )

                ListItem(
                    headlineContent = { Text("Download Directory") },
                    supportingContent = { Text(downloadDir?.substringAfterLast("%2F") ?: "System Music folder") },
                    leadingContent = { Icon(Icons.Default.Folder, null) },
                    modifier = Modifier.clickable { dirPicker.launch(null) }
                )
            }

            SettingsCategory("Audio Effects") {
                Text(
                    "Crossfade Duration (${fadeDuration.toInt()}s)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 56.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, null, modifier = Modifier.padding(start = 16.dp, end = 24.dp))
                    Slider(
                        value = fadeDuration,
                        onValueChange = onFadeChange,
                        valueRange = 0f..10f,
                        steps = 10,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SettingsCategory("Storage & Cache") {
                SwitchSettingItem(
                    icon = Icons.Default.Cached,
                    title = "Cellular Data Caching",
                    subtitle = "Cache songs during cellular playback",
                    checked = useCellularCache,
                    onCheckedChange = onUseCellularCacheChange
                )

                Text(
                    "Max Cache Size: ${cacheSize}MB",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 56.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cached, null, modifier = Modifier.padding(start = 16.dp, end = 24.dp))
                    Slider(
                        value = cacheSize.toFloat(),
                        onValueChange = { onCacheSizeChange(it.toInt()) },
                        valueRange = 100f..2048f,
                        steps = 19,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = onClearCache,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Text("Clear Playback Cache")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsCategory(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                content = content
            )
        }
    }
}

@Composable
fun QualitySettingItem(
    icon: ImageVector,
    label: String,
    selectedQuality: String,
    onQualityChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val qualities = listOf("standard", "higher", "exhigh", "lossless", "hires")

    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(selectedQuality.replaceFirstChar { it.uppercase() }) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Column {
                    qualities.forEach { quality ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onQualityChange(quality)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = quality == selectedQuality, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(quality.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SwitchSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}
