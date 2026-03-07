package com.mvwj.yousify.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Ð—Ð°Ð¼ÐµÐ½Ð¸Ñ‚Ðµ ÑÑ‚Ð¾ Ð½Ð° ÑÐ²Ð¾Ð¸ ÑˆÑ€Ð¸Ñ„Ñ‚Ñ‹, ÐµÑÐ»Ð¸ Ð¾Ð½Ð¸ Ñƒ Ð²Ð°Ñ ÐµÑÑ‚ÑŒ
// import com.mvwj.yousify.R (ÐµÑÐ»Ð¸ ÑˆÑ€Ð¸Ñ„Ñ‚Ñ‹ Ð² res/font)

// val SansSerifFontFamily = FontFamily(
//    Font(R.font.your_sans_regular, FontWeight.Normal),
//    Font(R.font.your_sans_medium, FontWeight.Medium),
//    Font(R.font.your_sans_bold, FontWeight.Bold)
// )

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default, // Ð—Ð°Ð¼ÐµÐ½Ð¸Ñ‚Ðµ Ð½Ð° SansSerifFontFamily, ÐµÑÐ»Ð¸ ÐµÑÑ‚ÑŒ
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold, // Ð¡Ð´ÐµÐ»Ð°ÐµÐ¼ Ñ‡ÑƒÑ‚ÑŒ Ð¶Ð¸Ñ€Ð½ÐµÐµ Ð´Ð»Ñ Ð·Ð°Ð³Ð¾Ð»Ð¾Ð²ÐºÐ¾Ð² Ð¿Ð¾Ð¼ÐµÐ½ÑŒÑˆÐµ
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium, // Medium Ð´Ð»Ñ Ð¿Ð¾Ð´Ð·Ð°Ð³Ð¾Ð»Ð¾Ð²ÐºÐ¾Ð²
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)