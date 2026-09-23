package com.cyk666.vibemusic

// 主题与 UI 模型（从 MainActivity 拆出；round6 T5）

import androidx.compose.foundation.background
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val ObsidianBg = Color(0xFF0A0A0F)
internal val ObsidianSurface = Color(0xFF14141C)
internal val NeonViolet = Color(0xFF8B5CF6)
internal val NeonCyan = Color(0xFF06B6D4)
internal val Champagne = Color(0xFFF5E6C8)
internal val InkOnDark = Color(0xFFEDEDF2)
internal val GrayMuted = Color(0xFF9CA3AF)

internal val ObsidianScheme = darkColorScheme(
    background = ObsidianBg,
    surface = ObsidianSurface,
    surfaceVariant = ObsidianSurface,
    primary = NeonViolet,
    secondary = NeonCyan,
    tertiary = Champagne,
    onBackground = InkOnDark,
    onSurface = InkOnDark,
    onSurfaceVariant = GrayMuted,
    onPrimary = InkOnDark
)

sealed interface LyricUiState {
    data object Loading : LyricUiState
    data class Ok(val lines: List<LyricLine>) : LyricUiState
    data object Failed : LyricUiState
}
