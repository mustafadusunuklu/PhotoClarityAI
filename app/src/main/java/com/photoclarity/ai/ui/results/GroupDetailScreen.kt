package com.photoclarity.ai.ui.results

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.photoclarity.ai.ui.components.QualityChip
import com.photoclarity.ai.ui.theme.Secondary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val group = viewModel.getGroupById(groupId) ?: run {
        onBack()
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Grup Detayı – ${group.photoCount} Fotoğraf",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Similarity badge
            val similarityPercent = (group.similarityScore * 100).toInt()
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Secondary.copy(alpha = 0.15f),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Benzerlik: %$similarityPercent | ${group.groupType.name.replace("_", " ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Secondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Photo comparison grid
            group.photos.forEachIndexed { index, photo ->
                val isRecommended = photo.id == group.recommendedKeepId

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRecommended)
                            Secondary.copy(alpha = 0.08f)
                        else
                            MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = if (isRecommended) BorderStroke(2.dp, Secondary) else null
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (isRecommended) {
                            Text(
                                text = "⭐ Önerilen – Sakla",
                                style = MaterialTheme.typography.labelMedium,
                                color = Secondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        } else {
                            Text(
                                text = "Fotoğraf ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        AsyncImage(
                            model = photo.contentUri,
                            contentDescription = photo.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(Modifier.height(10.dp))

                        // Quality chips
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QualityChip(label = "%.1f MP".format(photo.megapixels))
                            QualityChip(label = photo.resolution)
                            if (isRecommended) {
                                QualityChip(
                                    label = viewModel.qualityScorer.qualityLabel(photo),
                                    isHighlight = true
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Metadata table
                        MetadataRow("Boyut", "%.2f MB".format(photo.sizeMb))
                        MetadataRow("Çözünürlük", photo.resolution)
                        MetadataRow("MIME", photo.mimeType)
                        MetadataRow("Klasör", photo.bucketName)
                        photo.dateTaken?.let { taken ->
                            MetadataRow(
                                "Çekim Tarihi",
                                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                                    .format(Date(taken))
                            )
                        }
                        if (photo.latitude != null && photo.longitude != null) {
                            MetadataRow("Konum", "%.4f, %.4f".format(photo.latitude, photo.longitude))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
