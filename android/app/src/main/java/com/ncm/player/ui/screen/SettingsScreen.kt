package com.ncm.player.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
    fadeDuration: Float,
    onFadeChange: (Float) -> Unit,
    onBackPressed: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text("Sound Quality", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            val qualities = listOf("standard", "higher", "exhigh", "lossless", "hires")
            qualities.forEach { quality ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (quality == currentQuality),
                        onClick = { onQualityChange(quality) }
                    )
                    Text(quality.replaceFirstChar { it.uppercase() })
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("Fade In/Out Duration (${fadeDuration.toInt()}s)", style = MaterialTheme.typography.titleLarge)
            Slider(
                value = fadeDuration,
                onValueChange = onFadeChange,
                valueRange = 0f..10f,
                steps = 10
            )
        }
    }
}
