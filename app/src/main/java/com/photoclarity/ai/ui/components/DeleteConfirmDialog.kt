package com.photoclarity.ai.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.photoclarity.ai.ui.theme.DeleteRed

@Composable
fun DeleteConfirmDialog(
    photoCount: Int,
    totalSizeLabel: String,
    useSystemTrash: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = DeleteRed
            )
        },
        title = {
            Text(
                text = if (useSystemTrash) "Sistem Çöp Kutusuna Taşı" else "Fotoğrafları Kalıcı Sil",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = if (useSystemTrash) "$photoCount fotoğraf ($totalSizeLabel) cihazın sistem çöp kutusuna taşınacak. " +
                        "Sonraki ekranda sistem onayı gerekir. " +
                        (if (photoCount > com.photoclarity.ai.core.media.RemovalBatchPolicy.MAX_TRASH_URIS)
                            "İşlem ${com.photoclarity.ai.core.media.RemovalBatchPolicy.count(photoCount)} bölüme ayrılır; her bölüm ayrı sistem onayı ister. İptal, tamamlanan bölümleri geri almaz. " else "") +
                        "Geri yükleme, saklama süresi ve kalıcı silme sistem veya galeri uygulaması tarafından yönetilir. PhotoClarityAI süre veya geri yükleme garantisi vermez."
                    else "$photoCount fotoğraf ($totalSizeLabel) kalıcı olarak silinecek. Bu cihazda sistem çöp kutusu kullanılamıyor. Bu işlem geri alınamaz. Fotoğrafları kontrol ederek onaylayın.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DeleteRed
                )
            ) {
                Text(if (useSystemTrash) "Sistem Onayına Geç" else "Evet, Kalıcı Sil")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
