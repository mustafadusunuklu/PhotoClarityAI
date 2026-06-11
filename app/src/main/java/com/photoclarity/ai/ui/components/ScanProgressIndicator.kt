package com.photoclarity.ai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photoclarity.ai.ui.theme.GradientEnd
import com.photoclarity.ai.ui.theme.GradientStart
import com.photoclarity.ai.ui.theme.OutlineDark

@Composable
fun ScanProgressIndicator(
    progress: Float,  // 0.0 – 1.0, or negative for indeterminate
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_anim")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val iconRotation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_rotation"
    )

    val glow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "progress"
    )

    val arcBrush = Brush.sweepGradient(
        colors = listOf(
            GradientStart.copy(alpha = 0f),
            GradientStart,
            GradientEnd
        )
    )
    val trackColor = OutlineDark.copy(alpha = 0.2f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val outerStroke = (strokePx * 2.5f)
            drawArc(
                color = GradientStart.copy(alpha = glow * 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(outerStroke / 2, outerStroke / 2),
                size = androidx.compose.ui.geometry.Size(
                    this.size.width - outerStroke,
                    this.size.height - outerStroke
                ),
                style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )
        }

        // Main progress arc
        Canvas(modifier = Modifier
            .fillMaxSize()
            .rotate(rotation)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(
                this.size.width - strokePx * 2,
                this.size.height - strokePx * 2
            )
            val topLeft = androidx.compose.ui.geometry.Offset(strokePx, strokePx)

            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Progress sweep
            val sweep = if (progress < 0) 270f else 360f * animatedProgress
            drawArc(
                brush = arcBrush,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        // Center icon
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = "Scanning",
            tint = GradientStart.copy(alpha = 0.7f + glow * 0.3f),
            modifier = Modifier
                .size(48.dp)
                .rotate(iconRotation)
        )
    }
}
