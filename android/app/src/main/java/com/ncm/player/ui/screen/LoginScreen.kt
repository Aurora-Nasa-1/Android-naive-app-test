package com.ncm.player.ui.screen

import com.ncm.player.ui.component.WavyCircularProgressIndicator
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ncm.player.R
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
        Text(stringResource(R.string.scan_qr_login), style = MaterialTheme.typography.headlineMedium)
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

                WavyCircularProgressIndicator()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(viewModel.loginStatus)

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { viewModel.fetchQrCode() }) {
            Text(stringResource(R.string.refresh_qr))
        }
    }
}
