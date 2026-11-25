package com.cyk666.vibemusic

// 主题与 UI 模型（从 MainActivity 拆出；round6 T5）

import androidx.compose.foundation.background
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 应用主题：三套深色共享同一套前景与暖金强调，只换背景/表面。
 *
 * <p>只做深色变体是刻意的：前景、分割线、半透明遮罩都不动，迁移面最小；
 * 浅色主题要重算全部对比度，不在这里顺带做。</p>
 */
enum class AppTheme(
    val label: String,
    val bg: Color,
    val surface: Color,
    val accent: Color,
    val accentSoft: Color,
    val ink: Color,
    val muted: Color
) {
    /** 黑金：原有的样子。 */
    OBSIDIAN(
        "曜石·金", Color(0xFF0B0B0C), Color(0xFF151516),
        Color(0xFFD4A24E), Color(0xFFE8C98A), Color(0xFFEDEDF2), Color(0xFF9CA3AF)
    ),

    /** 纯黑 + 冷银：去金色，黑白极简，OLED 上最省电。 */
    OLED(
        "纯黑·银", Color(0xFF000000), Color(0xFF0C0C0D),
        Color(0xFFD7DBE0), Color(0xFFF2F4F7), Color(0xFFFFFFFF), Color(0xFF9AA0A6)
    ),

    /** 暖灰 + 铜橙：像旧纸与铜器，长时间看不刺眼。 */
    WARM_GRAY(
        "暖灰·铜", Color(0xFF1A1917), Color(0xFF262320),
        Color(0xFFC97B4A), Color(0xFFE0A57E), Color(0xFFF2EDE4), Color(0xFFA69C8F)
    );

    companion object {
        fun parse(name: String?): AppTheme = entries.firstOrNull { it.name == name } ?: OBSIDIAN
    }
}

/**
 * 当前主题的单一来源。
 *
 * <p>用 snapshot state 而不是 CompositionLocal：颜色定义分散在 16 个文件的 130 多处，
 * 换成 CompositionLocal 要在根节点多包一层、逐个调整嵌套；snapshot state 的读取同样
 * 参与重组跟踪，切换主题时所有读取方会自动重绘，改动面最小。</p>
 */
object ThemeState {
    var current by mutableStateOf(AppTheme.OBSIDIAN)
        private set

    fun set(theme: AppTheme) {
        current = theme
    }
}

// 名字保持不变，130 多处引用一行都不用改；在 @Composable 之外读取会编译报错，
// 这是有意的约束——颜色只能来自当前主题，不能来自某个对象的初始化。
val AppBg: Color @Composable get() = ThemeState.current.bg
val AppSurface: Color @Composable get() = ThemeState.current.surface
val InkOnDark: Color @Composable get() = ThemeState.current.ink
val GrayMuted: Color @Composable get() = ThemeState.current.muted
internal val Champagne = Color(0xFFF5E6C8)

// 封面遮罩：半透明黑，与主题无关（任何主题下都要压暗底图），但必须有名字
val UiScrimTop = Color(0x99000000)
val UiScrimMid = Color(0x66000000)
val UiScrimBottom = Color(0xCC000000)

@Composable
internal fun appColorScheme(theme: AppTheme) = darkColorScheme(
    background = theme.bg,
    surface = theme.surface,
    surfaceVariant = theme.surface,
    primary = theme.accent,
    secondary = theme.accentSoft,
    tertiary = Champagne,
    onBackground = theme.ink,
    onSurface = theme.ink,
    onSurfaceVariant = theme.muted,
    // 暖金实底上的前景用近黑：白字对 #D4A24E 只有 2.3:1，深色≈8:1。
    onPrimary = AppBg
)

sealed interface LyricUiState {
    data object Loading : LyricUiState
    data class Ok(val lines: List<LyricLine>) : LyricUiState
    data object Failed : LyricUiState
}
