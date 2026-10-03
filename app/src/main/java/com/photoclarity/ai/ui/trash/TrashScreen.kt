package com.photoclarity.ai.ui.trash

import android.os.Build
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photoclarity.ai.ui.theme.DeleteRed
import com.photoclarity.ai.ui.theme.GradientEnd
import com.photoclarity.ai.ui.theme.GradientStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Sistem Çöp Kutusu Bilgisi", fontWeight = FontWeight.Bold) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(40.dp))

            // Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(DeleteRed.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    null,
                    tint = DeleteRed,
                    modifier = Modifier.size(52.dp)
                )
            }

            Text(
                "Sistem Çöp Kutusu Bilgisi",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    "PhotoClarityAI kendi çöp kutusunu tutmaz. Onayladığınız fotoğraflar cihazın sistem çöp kutusuna taşınır."
                else "Bu Android sürümünde sistem çöp kutusu kullanılamıyor. Onayladığınız silme işlemleri kalıcıdır ve geri alınamaz.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Info note
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Şu An Geçerli Davranış", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    InfoRow(Icons.Default.Warning, "Her işlemden önce seçilen fotoğrafları kontrol edin ve açıkça onaylayın.")
                    InfoRow(Icons.Default.Android, "Android 11 ve üzerinde taşıma için ayrıca sistem onayı gerekir. Sistem isteği başarısız olursa kalıcı silmeye geçilmez.")
                    InfoRow(Icons.Default.Undo, "Geri yükleme, saklama süresi ve kalıcı silme cihazın sistem veya galeri uygulaması tarafından yönetilir.")
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DeleteRed.copy(alpha = 0.10f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, null, tint = DeleteRed, modifier = Modifier.size(16.dp))
                    Text(
                        "Saklama süresi veya geri yükleme garantisi verilmez",
                        style = MaterialTheme.typography.labelMedium,
                        color = DeleteRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
