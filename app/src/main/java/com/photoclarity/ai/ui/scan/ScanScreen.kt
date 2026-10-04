package com.photoclarity.ai.ui.scan

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photoclarity.ai.ui.components.LiveStatusItem
import com.photoclarity.ai.ui.components.ScanProgressIndicator
import com.photoclarity.ai.ui.theme.GradientStart

@Composable
fun ScanScreen(
    onScanComplete: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate when scan finishes (with or without results)
    LaunchedEffect(uiState.isCompleted, uiState.isCancelled) {
        when {
            uiState.isCancelled              -> onCancel()
            uiState.error != null            -> { /* stay – error shown below */ }
            uiState.isCompleted              -> onScanComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Spacer(Modifier.height(32.dp))

            // Animated circular progress
            ScanProgressIndicator(
                progress = uiState.progressFraction,
                size = 220.dp,
                strokeWidth = 6.dp
            )

            Spacer(Modifier.height(32.dp))

            // Title and progress text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (uiState.isScanning) "Kütüphane Analiz Ediliyor" else "Tarama Durumu",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                AnimatedContent(
                    targetState = "${uiState.currentProgress} / ${uiState.totalPhotos} fotoğraf analiz edildi...",
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "progress_text"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Text("Tarama uygulama görünürken çalışır. Arka plana geçince durur; yeniden başlatabilirsiniz.",
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                if (uiState.failedPhotos > 0) Text("${uiState.failedPhotos} fotoğraf analiz edilemedi.")
                // Show error if any
                uiState.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Live status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CANLI AKIŞ",
                        style = MaterialTheme.typography.labelSmall,
                        color = GradientStart,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    uiState.steps.forEach { stepStatus ->
                        LiveStatusItem(stepStatus = stepStatus)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Cancel button
            OutlinedButton(
                onClick = { if (uiState.isScanning) viewModel.cancelScan() else viewModel.startScan() },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(48.dp)
            ) {
                Text(
                    text = if (uiState.isScanning) "Taramayı İptal Et" else "Taramayı Yeniden Başlat",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
