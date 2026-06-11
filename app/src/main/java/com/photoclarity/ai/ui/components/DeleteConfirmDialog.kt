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
                text = "Fotoğrafları Sil",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = "$photoCount fotoğraf ($totalSizeLabel) kalıcı olarak silinecek. " +
                        "Bu işlem geri alınamaz.",
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
                Text("Evet, Sil")
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
