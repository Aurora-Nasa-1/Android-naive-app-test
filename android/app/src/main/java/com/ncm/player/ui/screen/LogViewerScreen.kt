package com.ncm.player.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncm.player.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onBackPressed: () -> Unit) {
    val logs by LogManager.logs.collectAsState()
    var systemLogs by remember { mutableStateOf("") }
    var showSystemLogs by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(showSystemLogs) {
        if (showSystemLogs) {
            systemLogs = "Loading system logs..."
            withContext(Dispatchers.IO) {
                systemLogs = try {
                    val process = Runtime.getRuntime().exec("logcat -d -v time")
                    process.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    "Failed to fetch system logs: ${e.message}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Logs") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSystemLogs = !showSystemLogs
                    }) {
                        Icon(if (showSystemLogs) Icons.Default.ViewList else Icons.Default.Terminal, contentDescription = "Toggle System Logs")
                    }
                    IconButton(onClick = {
                        val text = if (showSystemLogs) systemLogs else LogManager.getAllLogsString()
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("App Logs", text)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Logs copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    IconButton(onClick = {
                        val text = if (showSystemLogs) systemLogs else LogManager.getAllLogsString()
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Logs"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { LogManager.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                }
            )
        }
    ) { innerPadding ->
        SelectionContainer {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(Color.Black)) {
                if (showSystemLogs) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = systemLogs,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(scrollState)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(logs) { entry ->
                            LogEntryItem(entry)
                            HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                        }
                        if (logs.isEmpty()) {
                            item {
                                Text("No app logs recorded yet.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(entry: LogManager.LogEntry) {
    val color = when (entry.level) {
        "E" -> Color.Red
        "W" -> Color.Yellow
        "I" -> Color.Cyan
        else -> Color.LightGray
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(
                text = entry.time,
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "[${entry.level}]",
                color = color,
                fontSize = 10.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = entry.message,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        if (entry.throwable != null) {
            Text(
                text = entry.throwable,
                color = Color.Red.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
