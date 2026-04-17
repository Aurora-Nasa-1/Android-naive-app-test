package com.ncm.player.ui.component

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary
) {
    // Official MD3 Expressive LoadingIndicator
    if (progress != null) {
        LoadingIndicator(
            progress = progress,
            modifier = modifier,
            color = color
        )
    } else {
        LoadingIndicator(
            modifier = modifier,
            color = color
        )
    }
}
