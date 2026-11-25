package com.cyk666.vibemusic

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * 主题选择的持久化与组合期状态。
 *
 * <p>存进既有的 playback DataStore：同一个文件只能有一个 DataStore 实例，
 * 另起一个会在启动时崩。键是 app_theme，读取失败一律回落到曜石。</p>
 */
object ThemeStore {

    private val KEY = stringPreferencesKey("app_theme")

    suspend fun save(context: Context, theme: AppTheme) {
        try {
            context.playbackDataStore.edit { it[KEY] = theme.name }
        } catch (_: Exception) {
            SilentFail.note("ThemeStore.save")
        }
    }

    suspend fun load(context: Context): AppTheme = try {
        AppTheme.parse(context.playbackDataStore.data.first()[KEY])
    } catch (_: Exception) {
        AppTheme.OBSIDIAN
    }
}

/** 组合期持有的主题状态：value 参与重组，set 负责切换。 */
class AppThemeHandle(val value: AppTheme, val set: (AppTheme) -> Unit)

@Composable
fun rememberAppTheme(context: Context): AppThemeHandle {
    // 初值取 ThemeState.current：配置变更或重进页面时不会先闪回曜石
    var theme by remember { mutableStateOf(ThemeState.current) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect("app-theme") {
        theme = ThemeStore.load(context)
        ThemeState.set(theme)
        loaded = true
    }
    // 首次加载不算用户操作；之后每次切换都落盘（调用方不必再管持久化）
    LaunchedEffect(theme, loaded) {
        if (loaded) ThemeStore.save(context, theme)
    }
    return AppThemeHandle(theme) { picked ->
        theme = picked
        ThemeState.set(picked)
    }
}
