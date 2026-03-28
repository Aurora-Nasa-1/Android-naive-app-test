package com.ncm.player.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ncm.player.viewmodel.LoginViewModel

@Composable
fun LoginScreen(viewModel: LoginViewModel, onLoginSuccess: () -> Unit) {
    LaunchedEffect(viewModel.isLogged) {
        if (viewModel.isLogged) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(Unit) {
        if (!viewModel.isLogged && viewModel.qrCodeBitmap == null) {
            viewModel.fetchQrCode()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Scan QR Code to Login", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        when {
            viewModel.qrCodeBitmap != null -> {
                Image(
                    bitmap = viewModel.qrCodeBitmap!!.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.size(256.dp)
                )
            }
            viewModel.qrUrl != null -> {
                AsyncImage(
                    model = viewModel.qrUrl,
                    contentDescription = "QR Code",
                    modifier = Modifier.size(256.dp)
                )
            }
            else -> {
                CircularProgressIndicator()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(viewModel.loginStatus)

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.fetchQrCode() }) {
            Text("Refresh QR Code")
        }
    }
}
