package com.photoclarity.ai.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photoclarity.ai.domain.model.SessionStatus
import com.photoclarity.ai.domain.model.SessionSnapshot
import com.photoclarity.ai.ui.session.SessionViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanHistoryScreen(onBack: () -> Unit, viewModel: SessionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Tarama Geçmişi", fontWeight = FontWeight.Bold) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Geri") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(if (!state.ready) "Tarama kayıtları yükleniyor…" else if (state.history.isEmpty()) "Henüz tarama kaydı yok" else "Son ${state.history.size} taramanın özeti. Fotoğraf sonuçları son taramaya aittir.")
                state.storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            items(state.history, key = { it.id }) { s ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.startedAt)), fontWeight = FontWeight.Bold)
                        Text(when (s.status) {
                            SessionStatus.RUNNING -> "Tarama sürüyor"
                            SessionStatus.COMPLETED -> "Tamamlandı"
                            SessionStatus.CANCELLED -> "Kullanıcı iptal etti"
                            SessionStatus.INTERRUPTED -> "Kesildi — yeniden başlatılabilir"
                            SessionStatus.FAILED -> "Başarısız"
                            SessionStatus.STALE -> "Sonuçlar güncel değil — yeniden tarama gerekiyor"
                        })
                        Text("Kapsam: ${if (s.scopeKey == "FULL") "tam fotoğraf erişimi" else "seçilen fotoğraflar"}")
                        Text("Listelenen: ${s.discovered} • Analiz denenen: ${s.attempted} • Analiz hatası: ${if (s.failedKnown) s.failed.toString() else "henüz kesinleşmedi"}")
                        Text("Tarama anında gruplarda bulunan: ${s.matched} • Kaydedilen süre: ${s.durationMillis / 1000} sn")
                        SessionSnapshot(session = s).errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
