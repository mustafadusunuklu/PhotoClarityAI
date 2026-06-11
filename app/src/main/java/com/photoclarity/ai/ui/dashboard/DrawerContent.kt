package com.photoclarity.ai.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.photoclarity.ai.ui.navigation.Screen
import com.photoclarity.ai.ui.theme.GradientEnd
import com.photoclarity.ai.ui.theme.GradientStart
import com.photoclarity.ai.ui.theme.Secondary

data class DrawerMenuItem(
    val icon: ImageVector,
    val label: String,
    val route: String,
    val badge: String? = null
)

private val drawerMenuItems = listOf(
    DrawerMenuItem(Icons.Default.Person,        "Profil",               Screen.Profile.route),
    DrawerMenuItem(Icons.Default.History,       "Tarama Geçmişi",       Screen.ScanHistory.route),
    DrawerMenuItem(Icons.Default.Favorite,      "Favoriler",            Screen.Favorites.route),
    DrawerMenuItem(Icons.Default.AutoFixHigh,   "Akıllı Öneriler",      Screen.SmartSuggestions.route),
    DrawerMenuItem(Icons.Default.Delete,        "Geri Dönüşüm Kutusu",  Screen.Trash.route),
    DrawerMenuItem(Icons.Default.Info,          "Hakkında",             Screen.About.route)
)

@Composable
fun DrawerContent(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ─────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Column {
                        Text(
                            "Kullanıcı",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "PhotoClarity AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Menu Items ──────────────────────────────────────────────────────
            drawerMenuItems.forEachIndexed { index, item ->
                // Add a subtle divider before "Hakkında" (last item)
                if (index == drawerMenuItems.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                val selected = currentRoute == item.route
                DrawerMenuRow(
                    item = item,
                    selected = selected,
                    onClick = {
                        onNavigate(item.route)
                    }
                )
            }

            Spacer(Modifier.weight(1f))

            // ── Footer ──────────────────────────────────────────────────────────
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, null, tint = Secondary, modifier = Modifier.size(16.dp))
                Text(
                    "Tüm işlemler cihaz üzerinde",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DrawerMenuRow(
    item: DrawerMenuItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                  else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(item.icon, null, tint = contentColor, modifier = Modifier.size(22.dp))
        Text(item.label, style = MaterialTheme.typography.bodyLarge, color = contentColor, modifier = Modifier.weight(1f))
        if (item.badge != null) {
            Surface(shape = RoundedCornerShape(8.dp), color = Secondary.copy(alpha = 0.15f)) {
                Text(item.badge, style = MaterialTheme.typography.labelSmall, color = Secondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        if (selected) {
            Box(modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
