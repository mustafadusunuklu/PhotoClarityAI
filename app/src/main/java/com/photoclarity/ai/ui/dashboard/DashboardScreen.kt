package com.photoclarity.ai.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photoclarity.ai.ui.components.BottomNavBar
import com.photoclarity.ai.ui.components.DonutChart
import com.photoclarity.ai.ui.components.GradientButton
import com.photoclarity.ai.ui.navigation.Screen
import com.photoclarity.ai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenDrawer: () -> Unit,
    onStartScan: () -> Unit,
    onOpenResults: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PhotoClarity AI",
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
                currentRoute = Screen.Dashboard.route,
                onNavigate   = { route ->
                    if (route != Screen.Dashboard.route) {
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

            // ── Storage Card ──────────────────────────────────────────────────
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(24.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier              = Modifier.padding(20.dp),
                    horizontalAlignment   = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier                = Modifier.fillMaxWidth(),
                        horizontalArrangement   = Arrangement.SpaceBetween,
                        verticalAlignment       = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Depolama Analizi",
                                style      = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Dahili Depolama",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val isHealthy = uiState.storageInfo?.isHealthy ?: true
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isHealthy) Secondary.copy(alpha = 0.15f)
                                    else DeleteRed.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier              = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector     = if (isHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint            = if (isHealthy) Secondary else DeleteRed,
                                    modifier        = Modifier.size(14.dp)
                                )
                                Text(
                                    text       = if (isHealthy) "Sağlıklı" else "Dolu",
                                    style      = MaterialTheme.typography.labelSmall,
                                    color      = if (isHealthy) Secondary else DeleteRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color    = Secondary,
                            modifier = Modifier.size(180.dp)
                        )
                    } else {
                        DonutChart(
                            usedFraction = uiState.storageInfo?.usedFraction ?: 0f,
                            totalGb      = uiState.storageInfo?.totalGb ?: 0f,
                            usedGb       = uiState.storageInfo?.usedGb ?: 0f
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LegendItem(color = Secondary, label = "Sistem ve Medya")
                        LegendItem(color = MaterialTheme.colorScheme.outlineVariant, label = "Kazanılabilir Alan")
                    }

                    Spacer(Modifier.height(20.dp))

                    GradientButton(
                        text      = "Hızlı Tarama",
                        onClick   = onStartScan,
                        modifier  = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                Icons.Default.RadioButtonChecked,
                                contentDescription = null,
                                tint     = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            // ── Bu Ay Temizlenen ──────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(20.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null, tint = Secondary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "BU AY TEMİZLENEN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text       = "%.1f".format(uiState.storageInfo?.cleanedGb ?: 0f),
                            style      = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text     = " GB",
                            style    = MaterialTheme.typography.titleMedium,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        text  = "Toplam ${uiState.storageInfo?.totalPhotoCount ?: 0} fotoğraf analiz hazır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Hızlı Erişim Grid ─────────────────────────────────────────────
            Text(
                "Hızlı Erişim",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Default.ContentCopy,
                    label    = "Kopyalar",
                    sub      = if (uiState.hasPreviousScanResults) "${uiState.lastScanGroupCount} grup" else "Tara",
                    tint     = GradientStart,
                    onClick  = onOpenResults
                )
                QuickCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Default.AutoAwesome,
                    label    = "Benzerler",
                    sub      = if (uiState.hasPreviousScanResults) "Görüntüle" else "Tara",
                    tint     = Secondary,
                    onClick  = onOpenResults
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Default.PhotoLibrary,
                    label    = "Fotoğraflar",
                    sub      = "Galeri",
                    tint     = Color(0xFF9C27B0),
                    onClick  = { onNavigate(Screen.Photos.route) }
                )
                QuickCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Default.History,
                    label    = "Geçmiş",
                    sub      = "Tarama özeti",
                    tint     = Color(0xFFFF9800),
                    onClick  = { onNavigate(Screen.ScanHistory.route) }
                )
            }

            // ── Similar Photos Card ───────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenResults() },
                shape  = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Benzer Fotoğraflar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Yapay zeka potansiyel kopyaları analiz ediyor. Tarama başlatın.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick   = onOpenResults,
                        modifier  = Modifier.fillMaxWidth(),
                        shape     = RoundedCornerShape(12.dp),
                        colors    = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text("Şimdi İncele") }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun QuickCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    sub: String,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier  = modifier,
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        onClick   = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier         = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
