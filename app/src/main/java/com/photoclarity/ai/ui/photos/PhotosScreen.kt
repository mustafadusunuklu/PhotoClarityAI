package com.photoclarity.ai.ui.photos

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
import com.photoclarity.ai.ui.components.BottomNavBar
import com.photoclarity.ai.ui.navigation.Screen
import com.photoclarity.ai.ui.scan.ScanResultHolder
import com.photoclarity.ai.ui.theme.GradientEnd
import com.photoclarity.ai.ui.theme.GradientStart
import com.photoclarity.ai.ui.theme.Secondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    onOpenDrawer: () -> Unit = {},
    onStartScan: () -> Unit,
    onOpenResults: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val groups        = ScanResultHolder.groups
    val hasScanResults = groups.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Fotoğraflar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    // ── Hamburger ──────────────────────────────────────────
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menüyü Aç"
                        )
                    }
                },
                actions = {
                    // ── Profile avatar ─────────────────────────────────────
                    IconButton(onClick = { onNavigate(Screen.Profile.route) }) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(GradientStart, GradientEnd))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profil",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            BottomNavBar(
                currentRoute = Screen.Photos.route,
                onNavigate   = { route ->
                    if (route != Screen.Photos.route) {
                        onNavigate(route)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Hero banner ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(36.dp))
                    Text(
                        "Galeri Analizi",
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White
                    )
                    Text(
                        text  = if (hasScanResults)
                                    "${groups.sumOf { it.photoCount }} fotoğraf analiz edildi, ${groups.size} grup bulundu"
                                else
                                    "Galerinizi tarayarak yinelenen ve bulanık fotoğrafları keşfedin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // ── Action cards ─────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoActionCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Default.Search,
                    title    = "Tara",
                    subtitle = "Tüm galeri",
                    tint     = GradientStart,
                    onClick  = onStartScan
                )
                PhotoActionCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Default.ContentCopy,
                    title    = "Kopyalar",
                    subtitle = "${groups.count { it.groupType == DuplicateGroup.GroupType.EXACT_DUPLICATE }} grup",
                    tint     = Secondary,
                    onClick  = onOpenResults
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoActionCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Default.AutoAwesome,
                    title    = "Benzerler",
                    subtitle = "${groups.count { it.groupType == DuplicateGroup.GroupType.VISUAL_SIMILAR }} grup",
                    tint     = Color(0xFF9C27B0),
                    onClick  = onOpenResults
                )
                PhotoActionCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Default.BlurOn,
                    title    = "Bulanık",
                    subtitle = "${groups.count { it.groupType == DuplicateGroup.GroupType.LOW_QUALITY }} fotoğraf",
                    tint     = Color(0xFFF44336),
                    onClick  = onOpenResults
                )
            }

            // ── Coming soon ───────────────────────────────────────────────────
            Card(
                shape  = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.GridView, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tam Galeri Görünümü", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Tüm fotoğraflarınızı göz atın — yakında eklenecek.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = GradientStart.copy(alpha = 0.12f)) {
                        Text(
                            "Yakında",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = GradientStart,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PhotoActionCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(18.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        onClick  = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
