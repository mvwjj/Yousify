package com.veshikov.yousify.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColors(
    primary = androidx.compose.ui.graphics.Color(0xFF1DB954),
    primaryVariant = androidx.compose.ui.graphics.Color(0xFF1AA34A),
    secondary = androidx.compose.ui.graphics.Color(0xFF191414)
)

private val LightColorPalette = lightColors(
    primary = androidx.compose.ui.graphics.Color(0xFF1DB954),
    primaryVariant = androidx.compose.ui.graphics.Color(0xFF1AA34A),
    secondary = androidx.compose.ui.graphics.Color(0xFF191414)
)

@Composable
fun YouSifyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColorPalette else LightColorPalette
    MaterialTheme(
        colors = colors,
        typography = androidx.compose.material.Typography(),
        shapes = androidx.compose.material.Shapes(),
        content = content
    )
}
