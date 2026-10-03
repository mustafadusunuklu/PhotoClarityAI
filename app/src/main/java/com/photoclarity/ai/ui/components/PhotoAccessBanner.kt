package com.photoclarity.ai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.photoclarity.ai.core.media.PhotoAccess
import com.photoclarity.ai.core.media.PhotoAccessSnapshot

data class PhotoAccessUi(val snapshot: PhotoAccessSnapshot, val request: () -> Unit)
val LocalPhotoAccess = staticCompositionLocalOf { PhotoAccessUi(PhotoAccessSnapshot(PhotoAccess.DENIED), {}) }

@Composable
fun PhotoAccessBanner() {
    val access = LocalPhotoAccess.current
    val label = access.snapshot.error ?: when (access.snapshot.access) {
        PhotoAccess.FULL -> "Fotoğraf erişimi: tüm fotoğraflar"
        PhotoAccess.LIMITED -> "Sınırlı erişim: yalnız seçtiğiniz fotoğraflar taranır. Tüm galeri taranmaz."
        PhotoAccess.DENIED -> "Fotoğraf erişimi yok. Tarama için izin gereklidir."
    }
    val action = if (access.snapshot.needsSettings || access.snapshot.access == PhotoAccess.FULL)
        "Fotoğraf izin ayarlarını aç" else "Fotoğraf erişimini değiştir"
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            // Keep the new permission control from consuming two rows on wide landscape windows.
            if (maxWidth >= 600.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = access.request) { Text(action) }
                }
            } else {
                Column {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = access.request) { Text(action) }
                }
            }
        }
    }
}
