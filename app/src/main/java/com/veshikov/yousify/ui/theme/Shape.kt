package com.veshikov.yousify.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // Часто используется для карточек
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)