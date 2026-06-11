package com.photoclarity.ai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photoclarity.ai.domain.model.ScanStepStatus
import com.photoclarity.ai.ui.theme.Secondary

@Composable
fun LiveStatusItem(
    stepStatus: ScanStepStatus,
    modifier: Modifier = Modifier
) {
    val (icon, tint, textAlpha) = when (stepStatus.status) {
        ScanStepStatus.StepStatus.DONE -> Triple(
            Icons.Outlined.CheckCircle,
            Secondary,
            1f
        )
        ScanStepStatus.StepStatus.IN_PROGRESS -> Triple(
            Icons.Outlined.Sync,
            MaterialTheme.colorScheme.primary,
            1f
        )
        ScanStepStatus.StepStatus.PENDING -> Triple(
            Icons.Outlined.HourglassEmpty,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            0.5f
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stepStatus.step.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
        )
    }
}
