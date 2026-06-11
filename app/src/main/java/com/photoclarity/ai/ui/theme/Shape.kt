package com.photoclarity.ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    // 4dp – Small chips, badges
    extraSmall = RoundedCornerShape(4.dp),
    // 8dp – Small cards
    small = RoundedCornerShape(8.dp),
    // 12dp – Buttons, dialogs
    medium = RoundedCornerShape(12.dp),
    // 16dp – Standard cards
    large = RoundedCornerShape(16.dp),
    // 24dp – Bottom sheets, modals, prominent cards
    extraLarge = RoundedCornerShape(24.dp)
)
