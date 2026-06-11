package com.photoclarity.ai.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photoclarity.ai.domain.model.HashAlgorithm
import com.photoclarity.ai.ui.theme.GradientStart
import com.photoclarity.ai.ui.theme.Secondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Hash Algorithm ───────────────────────────────────────────────
            SettingsSection(title = "Hash Algoritması", icon = Icons.Default.Code) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HashAlgorithm.values().forEach { algo ->
                        AlgorithmRadioItem(
                            algorithm = algo,
                            selected = settings.hashAlgorithm == algo,
                            onSelect = { viewModel.updateHashAlgorithm(algo) }
                        )
                    }
                }
            }

            // ─── Similarity Threshold ─────────────────────────────────────────
            SettingsSection(title = "Benzerlik Eşiği", icon = Icons.Default.Tune) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Minimum Benzerlik",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "%${settings.similarityPercent}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = GradientStart
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = settings.similarityThreshold,
                        onValueChange = { viewModel.updateSimilarityThreshold(it) },
                        valueRange = 0.70f..0.99f,
                        colors = SliderDefaults.colors(
                            thumbColor = GradientStart,
                            activeTrackColor = GradientStart
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("%70", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%99", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ─── Scan Options ─────────────────────────────────────────────────
            SettingsSection(title = "Tarama Seçenekleri", icon = Icons.Default.Search) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsToggleRow(
                        label = "Birebir Aynı Fotoğraflar",
                        description = "MD5/SHA-256 ile tam eşleşme",
                        checked = settings.exactMatchEnabled,
                        onCheckedChange = { viewModel.updateExactMatch(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Görsel Benzerlik (AI)",
                        description = "pHash/aHash/dHash ile analiz",
                        checked = settings.visualSimilarityEnabled,
                        onCheckedChange = { viewModel.updateVisualSimilarity(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Aynı Klasörü Dahil Et",
                        description = "Aynı albümdeki fotoğrafları karşılaştır",
                        checked = settings.includeSameFolderPhotos,
                        onCheckedChange = { viewModel.updateSameFolder(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Seri Çekim Tespiti",
                        description = "Ardışık hızlı çekim gruplarını bul",
                        checked = settings.detectBurstShots,
                        onCheckedChange = { viewModel.updateBurstDetection(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Düşük Kalite Tespiti",
                        description = "Bulanık fotoğrafları işaretle",
                        checked = settings.detectLowQuality,
                        onCheckedChange = { viewModel.updateLowQuality(it) }
                    )
                }
            }

            // ─── Smart Selection ──────────────────────────────────────────────
            SettingsSection(title = "Akıllı Özellikler", icon = Icons.Default.AutoAwesome) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SettingsToggleRow(
                        label = "Akıllı Seçim",
                        description = "En iyi fotoğrafı otomatik sakla",
                        checked = settings.smartSelectionEnabled,
                        onCheckedChange = { viewModel.updateSmartSelection(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "Metadata Kullan",
                        description = "Tarih ve kamera bilgisini dahil et",
                        checked = settings.useMetadata,
                        onCheckedChange = { viewModel.updateMetadata(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        label = "GPS Konumu Kullan",
                        description = "Çekim yeri benzerliğini hesaba kat",
                        checked = settings.useGpsMetadata,
                        onCheckedChange = { viewModel.updateGps(it) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GradientStart,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
private fun AlgorithmRadioItem(
    algorithm: HashAlgorithm,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = GradientStart)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = algorithm.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) GradientStart else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = algorithm.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = GradientStart
            )
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        thickness = 0.5.dp
    )
}
