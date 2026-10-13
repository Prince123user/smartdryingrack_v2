package com.example.smartdryingrack.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalDarkTheme = staticCompositionLocalOf { true }

object AppColors {
    val Background: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.Background else LightAppColors.Background
    val BackgroundAlt: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.BackgroundAlt else LightAppColors.BackgroundAlt
    val Card: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.Card else LightAppColors.Card
    val CardAlt: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.CardAlt else LightAppColors.CardAlt
    val ElevatedCard: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.ElevatedCard else LightAppColors.ElevatedCard
    val Primary: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.Primary else LightAppColors.Primary
    val PrimaryVariant: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.PrimaryVariant else LightAppColors.PrimaryVariant
    val Success: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.Success else LightAppColors.Success
    val Warning: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.Warning else LightAppColors.Warning
    val Danger: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.Danger else LightAppColors.Danger
    val Cyan: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.Cyan else LightAppColors.Cyan
    val TextPrimary: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.TextPrimary else LightAppColors.TextPrimary
    val TextSecondary: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.TextSecondary else LightAppColors.TextSecondary
    val TextTertiary: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.TextTertiary else LightAppColors.TextTertiary
    val Border: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.Border else LightAppColors.Border
    val BorderSubtle: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.BorderSubtle else LightAppColors.BorderSubtle
    val GlassSurface: Color
        @Composable get() = if (LocalDarkTheme.current) DarkAppColors.GlassSurface else LightAppColors.GlassSurface
}

object DarkAppColors {
    // Dark Theme Palette (Primary Futuristic Smart-Home)
    val Background = Color(0xFF001020)
    val BackgroundAlt = Color(0xFF001326)
    val Card = Color(0xFF031C2D)
    val CardAlt = Color(0xFF08283A)
    val ElevatedCard = Color(0xFF19314A)
    val Primary = Color(0xFF16E3F4) // Futuristic Electric Blue
    val PrimaryVariant = Color(0xFF00A9C8)
    val Success = Color(0xFF2AEAD0) // Modern Green
    val Warning = Color(0xFFFFB020) // Amber / Orange
    val Danger = Color(0xFFFF4D5E)  // Red / Rain / Error
    val Cyan = Color(0xFF00E5FF)    // Secondary Accent
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB2C6E3)
    val TextTertiary = Color(0xFF829AB8)
    val Border = Color(0xFF264869)
    val BorderSubtle = Color(0xFF18334C)
    val GlassSurface = Color(0xE6102438)
}

object LightAppColors {
    // Light Theme Palette
    val Background = Color(0xFFEDF4FC)
    val BackgroundAlt = Color(0xFFFFFFFF)
    val Card = Color(0xFFFFFFFF)
    val CardAlt = Color(0xFFF8FAFC)
    val ElevatedCard = Color(0xFFEEF2F7)
    val Primary = Color(0xFF007F99)
    val PrimaryVariant = Color(0xFF006476)
    val Success = Color(0xFF007F99)
    val Warning = Color(0xFF945600)
    val Danger = Color(0xFFDC2626)
    val Cyan = Color(0xFF007F99)
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF475569)
    val TextTertiary = Color(0xFF64746A)
    val Border = Color(0xFFCBD5E1)
    val BorderSubtle = Color(0xFFE2E8F0)
    val GlassSurface = Color(0xCCFFFFFF)
}
