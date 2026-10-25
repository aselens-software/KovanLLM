package com.aselens.kovanllm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark Palette
val DarkBg = Color(0xFF090A0F)
val DarkSurface = Color(0xFF13151D)
val DarkCard = Color(0xFF1B1E29)
val DarkPrimary = Color(0xFFD97706)
val DarkPrimaryLight = Color(0xFFF59E0B)
val DarkSuccess = Color(0xFF10B981)
val DarkText = Color(0xFFF3F4F6)
val DarkTextMuted = Color(0xFF9CA3AF)
val DarkBorder = Color(0xFF222634)
val DarkUserBubble = Color(0xFFD97706)
val DarkAssistantBubble = Color(0xFF1A1D26)
val DarkCodeBg = Color(0xFF0D0F14)

// Light Palette
val LightBg = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFF1F5F9)
val LightPrimary = Color(0xFFD97706)
val LightPrimaryLight = Color(0xFFF59E0B)
val LightSuccess = Color(0xFF059669)
val LightText = Color(0xFF0F172A)
val LightTextMuted = Color(0xFF64748B)
val LightBorder = Color(0xFFE2E8F0)
val LightUserBubble = Color(0xFFD97706)
val LightAssistantBubble = Color(0xFFF1F5F9)
val LightCodeBg = Color(0xFFE2E8F0)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.White,
    primaryContainer = DarkCard,
    onPrimaryContainer = DarkText,
    secondary = DarkSuccess,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onBackground = DarkText,
    onSurface = DarkText,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightCard,
    onPrimaryContainer = LightText,
    secondary = LightSuccess,
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = LightCard,
    onBackground = LightText,
    onSurface = LightText,
    outline = LightBorder
)

@Composable
fun KovanLLMTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        content = content
    )
}
