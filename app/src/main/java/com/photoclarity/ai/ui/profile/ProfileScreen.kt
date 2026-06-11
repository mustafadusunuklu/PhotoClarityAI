package com.photoclarity.ai.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.ui.scan.ScanResultHolder
import com.photoclarity.ai.ui.theme.GradientEnd
import com.photoclarity.ai.ui.theme.GradientStart
import com.photoclarity.ai.ui.theme.Secondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    // Read data directly — no ViewModel needed
    val groups         = ScanResultHolder.groups
    val exactCount     = groups.count { it.groupType == DuplicateGroup.GroupType.EXACT_DUPLICATE }
    val similarCount   = groups.count { it.groupType == DuplicateGroup.GroupType.VISUAL_SIMILAR }
    val burstCount     = groups.count { it.groupType == DuplicateGroup.GroupType.BURST_SHOT }
    val lowCount       = groups.count { it.groupType == DuplicateGroup.GroupType.LOW_QUALITY }
    val totalPhotos    = groups.sumOf { it.photoCount }
    val wasteMb        = groups.sumOf { it.totalWasteBytes } / (1024f * 1024f)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Profil", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector     = Icons.Default.Person,
                    contentDescription = null,
                    tint            = Color.White,
                    modifier        = Modifier.size(52.dp)
                )
            }

            Text(
                text       = "Kullanıcı",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Secondary.copy(alpha = 0.15f)
            ) {
                Text(
                    text       = "PhotoClarity Üyesi",
                    style      = MaterialTheme.typography.labelMedium,
                    color      = Secondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            // Stats card
            Card(
                shape  = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier              = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement   = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Özet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBox(label = "Kazanılabilir", value = "%.0f MB".format(wasteMb), icon = Icons.Default.DeleteSweep)
                        StatBox(label = "Bulunan Fotoğraf", value = "$totalPhotos", icon = Icons.Default.PhotoLibrary)
                    }
                }
            }

            // Scan summary card
            Card(
                shape  = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier              = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Tarama Özeti", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (groups.isEmpty()) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Text(
                                "Henüz tarama yapılmadı.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        SummaryRow(Icons.Default.ContentCopy,  "Tam Kopya",   exactCount,   GradientStart)
                        SummaryRow(Icons.Default.AutoAwesome,  "Görsel Benzer", similarCount, Secondary)
                        SummaryRow(Icons.Default.Photo,        "Seri Çekim",  burstCount,   Color(0xFF9C27B0))
                        SummaryRow(Icons.Default.BlurOn,       "Düşük Kalite", lowCount,    MaterialTheme.colorScheme.error)
                        HorizontalDivider()
                        SummaryRow(Icons.Default.PhotoLibrary, "Toplam Fotoğraf", totalPhotos, MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = Secondary, modifier = Modifier.size(24.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryRow(
    icon: ImageVector,
    label: String,
    count: Int,
    tint: Color
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
