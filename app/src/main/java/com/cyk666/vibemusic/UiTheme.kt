package com.cyk666.vibemusic

// 主题与 UI 模型（从 MainActivity 拆出；round6 T5）

import androidx.compose.foundation.background
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val ObsidianBg = Color(0xFF0B0B0C)
internal val ObsidianSurface = Color(0xFF151516)
internal val Champagne = Color(0xFFF5E6C8)
internal val InkOnDark = Color(0xFFEDEDF2)
internal val GrayMuted = Color(0xFF9CA3AF)

internal val ObsidianScheme = darkColorScheme(
    background = ObsidianBg,
    surface = ObsidianSurface,
    surfaceVariant = ObsidianSurface,
    primary = UiAccent,
    secondary = UiAccentSoft,
    tertiary = Champagne,
    onBackground = InkOnDark,
    onSurface = InkOnDark,
    onSurfaceVariant = GrayMuted,
    // 暖金实底上的前景用近黑：白字对 #D4A24E 只有 2.3:1，深色≈8:1。
    onPrimary = ObsidianBg
)

sealed interface LyricUiState {
    data object Loading : LyricUiState
    data class Ok(val lines: List<LyricLine>) : LyricUiState
    data object Failed : LyricUiState
}
