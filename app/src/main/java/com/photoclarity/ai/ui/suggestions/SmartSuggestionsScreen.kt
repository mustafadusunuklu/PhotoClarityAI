package com.photoclarity.ai.ui.suggestions

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photoclarity.ai.domain.model.DuplicateGroup
import com.photoclarity.ai.ui.scan.ScanResultHolder
import com.photoclarity.ai.ui.theme.GradientEnd
import com.photoclarity.ai.ui.theme.GradientStart
import com.photoclarity.ai.ui.theme.Secondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartSuggestionsScreen(
    onStartScan: () -> Unit,
    onBack: () -> Unit
) {
    val groups = ScanResultHolder.groups
    val totalWasteMb = groups.sumOf { it.totalWasteBytes } / (1024f * 1024f)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Akıllı Öneriler", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Header hero
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoFixHigh, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(32.dp))
                    Text(
                        "Akıllı Temizlik Önerileri",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    if (groups.isEmpty()) {
                        Text(
                            "Öneriler oluşturmak için önce bir tarama yapın.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    } else {
                        Text(
                            "${groups.size} grup analiz edildi — %.1f MB kazanılabilir".format(totalWasteMb),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            if (groups.isEmpty()) {
                // No scan yet — prompt to scan
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Text("Henüz tarama yapılmadı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(
                            "Akıllı temizlik önerileri görmek için önce galerinizi tarayın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onStartScan,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GradientStart)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Taramayı Başlat")
                        }
                    }
                }
            } else {
                // Show suggestion cards based on actual scan data
                Text("Öneriler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                val exactCount   = groups.count { it.groupType == DuplicateGroup.GroupType.EXACT_DUPLICATE }
                val similarCount = groups.count { it.groupType == DuplicateGroup.GroupType.VISUAL_SIMILAR }
                val burstCount   = groups.count { it.groupType == DuplicateGroup.GroupType.BURST_SHOT }
                val blurCount    = groups.count { it.groupType == DuplicateGroup.GroupType.LOW_QUALITY }

                if (exactCount > 0) {
                    SuggestionCard(
                        icon      = Icons.Default.ContentCopy,
                        tint      = GradientStart,
                        title     = "$exactCount grup tam kopya tespit edildi",
                        subtitle  = "Kopyaları silerek en yüksek alanı kazanabilirsiniz.",
                        priority  = "Yüksek Öncelik"
                    )
                }
                if (similarCount > 0) {
                    SuggestionCard(
                        icon      = Icons.Default.AutoAwesome,
                        tint      = Secondary,
                        title     = "$similarCount grup görsel benzer fotoğraf bulundu",
                        subtitle  = "En iyi kaliteli olanı tutup diğerlerini silebilirsiniz.",
                        priority  = "Orta Öncelik"
                    )
                }
                if (burstCount > 0) {
                    SuggestionCard(
                        icon      = Icons.Default.BurstMode,
                        tint      = Color(0xFF9C27B0),
                        title     = "$burstCount seri çekim grubu tespit edildi",
                        subtitle  = "En net kareyi koruyup diğer seri kareleri silebilirsiniz.",
                        priority  = "Orta Öncelik"
                    )
                }
                if (blurCount > 0) {
                    SuggestionCard(
                        icon      = Icons.Default.BlurOn,
                        tint      = Color(0xFFF44336),
                        title     = "$blurCount düşük kaliteli / bulanık fotoğraf",
                        subtitle  = "Bu fotoğraflar netlik analizi sonucunda işaretlendi.",
                        priority  = "Düşük Öncelik"
                    )
                }
            }

            // Coming soon feature
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, null, tint = GradientStart, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Otomatik Temizlik Planı", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Haftalık otomatik tarama ve akıllı temizlik — yakında eklenecek.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SuggestionCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    priority: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(6.dp), color = tint.copy(alpha = 0.12f)) {
                    Text(
                        priority,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
