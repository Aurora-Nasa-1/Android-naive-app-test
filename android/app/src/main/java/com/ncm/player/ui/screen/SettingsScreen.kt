package com.ncm.player.ui.screen

import com.ncm.player.util.LogManager
import android.content.ClipData
import android.content.ClipboardManager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ncm.player.R

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
    pureBlackMode: Boolean,
    onPureBlackModeChange: (Boolean) -> Unit,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    followCoverApp: Boolean,
    onFollowCoverAppChange: (Boolean) -> Unit,
    followCoverMini: Boolean,
    onFollowCoverMiniChange: (Boolean) -> Unit,
    followCoverPlayer: Boolean,
    onFollowCoverPlayerChange: (Boolean) -> Unit,
    useFluidBackground: Boolean,
    onUseFluidBackgroundChange: (Boolean) -> Unit,
    downloadDir: String?,
    onDownloadDirChange: (String) -> Unit,
    onClearCache: () -> Unit,
    onBackPressed: () -> Unit,
    bottomContentPadding: PaddingValues = PaddingValues(0.dp)
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
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            SettingsCategory(stringResource(R.string.appearance)) {
                ThemeModeSettingItem(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )

                if (themeMode == 1) { // Follow Cover
                    Column(modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp)) {
                        Text(
                            text = stringResource(R.string.follow_cover_granular),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onFollowCoverAppChange(!followCoverApp) }.padding(vertical = 4.dp)) {
                            Checkbox(checked = followCoverApp, onCheckedChange = onFollowCoverAppChange)
                            Text(stringResource(R.string.follow_cover_app), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onFollowCoverMiniChange(!followCoverMini) }.padding(vertical = 4.dp)) {
                            Checkbox(checked = followCoverMini, onCheckedChange = onFollowCoverMiniChange)
                            Text(stringResource(R.string.follow_cover_mini), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onFollowCoverPlayerChange(!followCoverPlayer) }.padding(vertical = 4.dp)) {
                            Checkbox(checked = followCoverPlayer, onCheckedChange = onFollowCoverPlayerChange)
                            Text(stringResource(R.string.follow_cover_player), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onUseFluidBackgroundChange(!useFluidBackground) }.padding(vertical = 4.dp)) {
                            Checkbox(checked = useFluidBackground, onCheckedChange = onUseFluidBackgroundChange)
                            Text("Use Fluid Background", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                SwitchSettingItem(
                    icon = Icons.Default.Brightness4,
                    title = stringResource(R.string.pure_black_mode),
                    subtitle = stringResource(R.string.pure_black_desc),
                    checked = pureBlackMode,
                    onCheckedChange = onPureBlackModeChange
                )
            }

            SettingsCategory(stringResource(R.string.playback_quality_cat)) {
                QualitySettingItem(
                    icon = Icons.Default.Wifi,
                    label = stringResource(R.string.wifi_quality_label),
                    selectedQuality = currentQualityWifi,
                    onQualityChange = onQualityWifiChange
                )
                QualitySettingItem(
                    icon = Icons.Default.CellTower,
                    label = stringResource(R.string.cellular_quality_label),
                    selectedQuality = currentQualityCellular,
                    onQualityChange = onQualityCellularChange
                )
            }

            SettingsCategory(stringResource(R.string.download_settings)) {
                QualitySettingItem(
                    icon = Icons.Default.HighQuality,
                    label = stringResource(R.string.download_quality_label),
                    selectedQuality = downloadQuality,
                    onQualityChange = onDownloadQualityChange
                )

                SwitchSettingItem(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.allow_cellular_download),
                    subtitle = stringResource(R.string.allow_cellular_download_desc),
                    checked = allowCellularDownload,
                    onCheckedChange = onAllowCellularDownloadChange
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.download_dir)) },
                    supportingContent = { Text(downloadDir?.substringAfterLast("%2F") ?: "System Music folder") },
                    leadingContent = { Icon(Icons.Default.Folder, null) },
                    modifier = Modifier.clickable { dirPicker.launch(null) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }

            SettingsCategory(stringResource(R.string.audio_effects)) {
                Text(
                    stringResource(R.string.crossfade_duration, fadeDuration.toInt()),
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

            SettingsCategory(stringResource(R.string.storage_cache)) {
                SwitchSettingItem(
                    icon = Icons.Default.Cached,
                    title = stringResource(R.string.cellular_caching),
                    subtitle = stringResource(R.string.cellular_caching_desc),
                    checked = useCellularCache,
                    onCheckedChange = onUseCellularCacheChange
                )

                Text(
                    stringResource(R.string.max_cache_size, cacheSize),
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
                    Text(stringResource(R.string.clear_cache))
                }
            }


            SettingsCategory(stringResource(R.string.debug)) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val logsCopiedMsg = stringResource(R.string.logs_copied)
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("NCM Player Logs", LogManager.getAllLogsString())
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, logsCopiedMsg, android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.copy_debug_logs))
                }
            }

            Spacer(modifier = Modifier.height(32.dp + bottomContentPadding.calculateBottomPadding()))
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
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun ThemeModeSettingItem(
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val modes = listOf(
        stringResource(R.string.theme_mode_system),
        stringResource(R.string.theme_mode_cover),
        stringResource(R.string.theme_mode_fixed)
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.theme_mode)) },
        supportingContent = { Text(modes.getOrElse(themeMode) { modes[0] }) },
        leadingContent = { Icon(Icons.Default.Brightness4, null) },
        modifier = Modifier.clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.theme_mode)) },
            text = {
                Column {
                    modes.forEachIndexed { index, mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeModeChange(index)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = index == themeMode, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(mode)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) }
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
