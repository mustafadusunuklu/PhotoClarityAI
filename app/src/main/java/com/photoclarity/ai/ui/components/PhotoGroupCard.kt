package com.photoclarity.ai.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.domain.model.Photo
import com.photoclarity.ai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PhotoGroupCard(
    group: DuplicateGroup,
    selectedPhotoIds: Set<Long>,
    onPhotoSelectionChanged: (Long, Boolean) -> Unit,
    qualityScorer: QualityScorer,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Group photos
            group.photos.forEachIndexed { index, photo ->
                val isSelected = photo.id in selectedPhotoIds
                val isRecommended = photo.id == group.recommendedKeepId

                PhotoCard(
                    photo = photo,
                    isSelected = isSelected,
                    isRecommended = isRecommended,
                    qualityScorer = qualityScorer,
                    onSelectionChanged = { selected ->
                        onPhotoSelectionChanged(photo.id, selected)
                    }
                )

                if (index < group.photos.size - 1) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 0.5.dp
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun PhotoCard(
    photo: Photo,
    isSelected: Boolean,
    isRecommended: Boolean,
    qualityScorer: QualityScorer,
    onSelectionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isSelected && isRecommended -> Secondary
        isSelected -> GradientStart
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelectionChanged(!isSelected) }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column {
            // Photo thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                AsyncImage(
                    model = photo.contentUri,
                    contentDescription = photo.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )

                // Recommended badge
                if (isRecommended) {
                    Surface(
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart),
                        shape = RoundedCornerShape(20.dp),
                        color = Secondary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                "Önerilen",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Checkbox
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd)
                ) {
                    AnimatedCheckbox(
                        checked = isSelected,
                        onCheckedChange = onSelectionChanged
                    )
                }

                // Quality chips at bottom
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    QualityChip(label = qualityScorer.megapixelLabel(photo))
                    if (isRecommended) {
                        QualityChip(
                            label = qualityScorer.qualityLabel(photo),
                            isHighlight = true
                        )
                    }
                }
            }

            // Metadata row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Boyut",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.1f MB".format(photo.sizeMb),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Tarih",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val date = photo.dateTaken?.let { taken ->
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(taken))
                    } ?: SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                        .format(Date(photo.dateAdded * 1000))
                    Text(
                        text = date.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
