package com.ncm.player.ui.component
import com.ncm.player.ui.component.WavyCircularProgressIndicator
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBackPressed: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    isLoading: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBackPressed != null) {
                        IconButton(onClick = onBackPressed) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            content(PaddingValues(0.dp))
            if (isLoading) {
                WavyCircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
