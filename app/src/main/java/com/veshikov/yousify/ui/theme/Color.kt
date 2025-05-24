package com.veshikov.yousify.ui.theme

import androidx.compose.ui.graphics.Color

// Основные цвета приложения для Material 3
val YousifyGreen = Color(0xFF1DB954) // Основной зеленый Spotify
val YousifyDarkCharcoal = Color(0xFF191414) // Темный фон Spotify
val YousifyLightGray = Color(0xFFB3B3B3) // Светло-серый для текста/иконок на темном фоне
val YousifyWhite = Color.White
val YousifyBlack = Color.Black

// Дополнительные цвета для светлой/темной темы Material 3
// Светлая тема
val md_theme_light_primary = YousifyGreen
val md_theme_light_onPrimary = YousifyWhite
val md_theme_light_primaryContainer = Color(0xFF9FFBAD) // Пример светлого контейнера для primary
val md_theme_light_onPrimaryContainer = Color(0xFF002108)
val md_theme_light_secondary = Color(0xFF506351) // Пример вторичного цвета
val md_theme_light_onSecondary = Color.White
val md_theme_light_secondaryContainer = Color(0xFFD3E8D1)
val md_theme_light_onSecondaryContainer = Color(0xFF0E1F11)
val md_theme_light_tertiary = Color(0xFF3A656F) // Пример третичного цвета
val md_theme_light_onTertiary = Color.White
val md_theme_light_tertiaryContainer = Color(0xFFBEEAF6)
val md_theme_light_onTertiaryContainer = Color(0xFF001F26)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color.White
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFDFDF7)
val md_theme_light_onBackground = Color(0xFF1A1C19)
val md_theme_light_surface = Color(0xFFFDFDF7)
val md_theme_light_onSurface = Color(0xFF1A1C19)
val md_theme_light_surfaceVariant = Color(0xFFDDE5D9)
val md_theme_light_onSurfaceVariant = Color(0xFF414941)
val md_theme_light_outline = Color(0xFF717971)
val md_theme_light_inverseOnSurface = Color(0xFFF0F1EB)
val md_theme_light_inverseSurface = Color(0xFF2F312D)
val md_theme_light_inversePrimary = Color(0xFF7BDA8F) // Инвертированный primary
val md_theme_light_surfaceTint = md_theme_light_primary
val md_theme_light_outlineVariant = Color(0xFFC1C9BF)
val md_theme_light_scrim = Color.Black

// Темная тема
val md_theme_dark_primary = YousifyGreen // Оставляем тот же яркий зеленый
val md_theme_dark_onPrimary = YousifyDarkCharcoal // Контрастный текст на primary
val md_theme_dark_primaryContainer = Color(0xFF00522A) // Пример темного контейнера для primary
val md_theme_dark_onPrimaryContainer = Color(0xFF9FFBAD)
val md_theme_dark_secondary = Color(0xFFB7CCB6) // Пример вторичного цвета для темной темы
val md_theme_dark_onSecondary = Color(0xFF233425)
val md_theme_dark_secondaryContainer = Color(0xFF394B3A)
val md_theme_dark_onSecondaryContainer = Color(0xFFD3E8D1)
val md_theme_dark_tertiary = Color(0xFFA3CED9) // Пример третичного цвета для темной темы
val md_theme_dark_onTertiary = Color(0xFF013640)
val md_theme_dark_tertiaryContainer = Color(0xFF1F4D57)
val md_theme_dark_onTertiaryContainer = Color(0xFFBEEAF6)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = YousifyDarkCharcoal // Используем фирменный темный фон
val md_theme_dark_onBackground = YousifyLightGray // Светлый текст на темном фоне
val md_theme_dark_surface = YousifyDarkCharcoal // Поверхность совпадает с фоном
val md_theme_dark_onSurface = YousifyLightGray
val md_theme_dark_surfaceVariant = Color(0xFF414941) // Вариант поверхности для карточек и т.п.
val md_theme_dark_onSurfaceVariant = Color(0xFFC1C9BF)
val md_theme_dark_outline = Color(0xFF8B938A)
val md_theme_dark_inverseOnSurface = Color(0xFF1A1C19)
val md_theme_dark_inverseSurface = Color(0xFFE3E3DE)
val md_theme_dark_inversePrimary = YousifyGreen // primary такой же, инверсия не нужна сильно
val md_theme_dark_surfaceTint = md_theme_dark_primary
val md_theme_dark_outlineVariant = Color(0xFF414941)
val md_theme_dark_scrim = Color.Black