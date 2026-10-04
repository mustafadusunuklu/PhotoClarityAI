package com.photoclarity.ai.ui.results

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photoclarity.ai.core.analysis.QualityScorer
import com.photoclarity.ai.ui.components.*
import com.photoclarity.ai.ui.theme.DeleteRed
import com.photoclarity.ai.ui.theme.GradientEnd
import com.photoclarity.ai.ui.theme.GradientStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    onGroupClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.selectionLocked) { /* Finish/cancel consent before leaving. */ }
    val context = LocalContext.current
    val openPermissionSettings: () -> Unit = {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}")))
    }
    val legacyWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onLegacyWritePermissionResult(it)
    }

    var launchedConsentId by rememberSaveable { mutableStateOf<String?>(null) }
    // Only a matching request/batch callback can affect the frozen transaction.
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteResult(result.resultCode == Activity.RESULT_OK, launchedConsentId)
        launchedConsentId = null
    }

    LaunchedEffect(uiState.pendingDeleteIntentSender) {
        uiState.pendingDeleteIntentSender?.let { sender ->
            launchedConsentId = uiState.pendingConsentId
            viewModel.onDeletePromptLaunched()
            try {
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (e: Exception) { viewModel.onDeletePromptFailed() }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Sonuçlar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !uiState.selectionLocked) {
                        Icon(Icons.Default.ArrowBack, "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Sticky delete bar
            AnimatedVisibility(
                visible = uiState.selectedPhotoCount > 0,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                DeleteBottomBar(
                    selectedCount = uiState.selectedPhotoCount,
                    totalSizeLabel = uiState.selectedSizeLabel,
                    onDelete = { viewModel.requestDeleteConfirmation() },
                    isBusy = uiState.isLoading,
                    useSystemTrash = uiState.removalMode == com.photoclarity.ai.domain.repository.PhotoRepository.RemovalMode.SYSTEM_TRASH
                )
            }
        }
    ) { padding ->
        if (uiState.restoring) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Tarama kaydı ve fotoğraf erişimi doğrulanıyor.")
                }
            }
        } else if (uiState.groups.isEmpty() && !uiState.isLoading) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                if (uiState.recoveryRequired) TextButton(onClick = { viewModel.retryRecovery() }) { Text("İşlem kaydını yeniden kontrol et") }
                if (uiState.error?.contains("Silme izni verilmedi") == true) {
                    TextButton(onClick = openPermissionSettings) { Text("Silme izin ayarlarını aç") }
                }
                EmptyStateView(
                    title = if (uiState.error != null) "Sonuçlar doğrulanamadı" else if (uiState.sessionId == null) "Henüz tamamlanmış tarama yok" else "Bu taramada grup kalmadı",
                    subtitle = if (uiState.error != null) "İzni kontrol ederek yeniden tarayın; bu ekran galerinizin temiz olduğunu göstermez."
                        else if (uiState.scopeKey?.startsWith("LIMITED:") == true)
                            "Yalnız erişim verdiğiniz fotoğraflar incelendi. Tüm galeri taranmadı."
                        else "Bu taramanın kapsamındaki kopya veya benzer fotoğraf grupları gösterilir.",
                    modifier = Modifier.weight(1f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    if (uiState.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Fotoğraf işlemi sürüyor; sistem onayını tamamlayın.")
                        if (uiState.consentBatchCount > 1) Text("Sistem onayı: ${uiState.consentBatch} / ${uiState.consentBatchCount} bölüm. İptal, tamamlanan bölümleri geri almaz.")
                    }
                    uiState.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    if (uiState.recoveryRequired) TextButton(onClick = { viewModel.retryRecovery() }) { Text("İşlem kaydını yeniden kontrol et") }
                    if (uiState.error?.contains("Silme izni verilmedi") == true) {
                        Text("Kalıcı silme için depolama yazma izni gerekir. İzni cihaz ayarlarından değiştirebilirsiniz.")
                        TextButton(onClick = openPermissionSettings) { Text("Silme izin ayarlarını aç") }
                    }
                    // Header
                    Column {
                        Text(
                            text = "${uiState.groups.count { it.groupType != com.photoclarity.ai.domain.model.DuplicateGroup.GroupType.LOW_QUALITY }} Fotoğraf Grubu",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Korunacak fotoğraf seçilemez. Diğer fotoğrafları işlemden önce tek tek inceleyin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        // Smart select button
                        GradientButton(
                            text = "Korunacaklar Dışındakileri Seç",
                            enabled = !uiState.selectionLocked,
                            onClick = { viewModel.smartSelectAll() },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                }

                itemsIndexed(
                    items = uiState.groups,
                    key = { _, group -> group.id }
                ) { index, group ->
                    // Group header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (group.groupType == com.photoclarity.ai.domain.model.DuplicateGroup.GroupType.LOW_QUALITY) "Tekil kalite önerisi" else "Grup ${index + 1} / ${uiState.groups.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "${group.photoCount} Fotoğraf",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    PhotoGroupCard(
                        group = group,
                        selectedPhotoIds = uiState.selectedPhotoIds,
                        onPhotoSelectionChanged = { photoId, selected ->
                            viewModel.togglePhotoSelection(photoId, selected)
                        },
                        qualityScorer = viewModel.qualityScorer,
                        selectionEnabled = !uiState.selectionLocked
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (uiState.confirmationPhotos != null && !uiState.waitingForLegacyWritePermission) {
        DeleteConfirmDialog(
            photoCount = uiState.confirmationPhotos.orEmpty().size,
            totalSizeLabel = uiState.confirmationSizeLabel,
            useSystemTrash = uiState.removalMode == com.photoclarity.ai.domain.repository.PhotoRepository.RemovalMode.SYSTEM_TRASH,
            onConfirm = {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    viewModel.onLegacyWritePermissionRequested()
                    try { legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                    catch (e: Exception) { viewModel.onLegacyWritePermissionResult(false) }
                } else viewModel.deleteSelectedPhotos()
            },
            onDismiss = { viewModel.dismissDeleteConfirmation() }
        )
    }
}

@Composable
private fun DeleteBottomBar(
    selectedCount: Int,
    totalSizeLabel: String,
    onDelete: () -> Unit,
    isBusy: Boolean,
    useSystemTrash: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "İşlem için seçildi (dosya boyutu)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = totalSizeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DeleteRed
                )
            }

            GradientButton(
                text = if (useSystemTrash) "Çöp Kutusu ($selectedCount)" else "Kalıcı Sil ($selectedCount)",
                enabled = !isBusy,
                onClick = onDelete,
                gradientStart = DeleteRed,
                gradientEnd = DeleteRed.copy(red = 0.8f),
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}
