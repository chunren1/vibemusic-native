package com.cyk666.vibemusic

import androidx.compose.runtime.saveable.Saver

/**
 * Top-level navigation destinations of the single-Activity app.
 *
 * Extracted from `MainActivity.kt` (refactor step C1) — declaration only, no
 * behavior change. Keeping it in its own file makes the navigation surface
 * greppable without opening the 3.9k-line Activity.
 */
sealed interface Screen {
    data object Discover : Screen
    data object Search : Screen
    data object Player : Screen
    data object Queue : Screen
    data object Login : Screen
    data object Mine : Screen
    data object Offline : Screen
    data object History : Screen
    data object Favorites : Screen
    data object Settings : Screen
    data object Playlists : Screen
    data class PlaylistDetail(val playlist: Playlist) : Screen
}

// ---- Navigation persistence (CODE-REVIEW-2026-10-28 UI-5) ----
//
// 设置里可以选「跟随系统」方向，此时旋转屏幕会重建 Activity：`screen` 原本是普通
// `remember`，重建后一律回到首页 —— 歌还在响、界面没了（UpdateCheck 的副标题自己
// 承认了这件事）。这里给 `screen` 一个可存盘的编码，交给 `rememberSaveable`。
//
// 编码用「route|字段|字段…」而不是 Parcelable：新增一个 Screen 变体时，忘记同步
// 编码会在 `decodeScreen` 里落到 Discover（安全降级，不崩），而 Parcelable 的
// `writeToParcel` 漏字段是静默丢数据。字段里的分隔符与 % 用百分号转义。

private const val SCREEN_SEP = "|"
private const val SCREEN_SEP_ESC = "%7C"
private const val SCREEN_PCT_ESC = "%25"

/** 稳定路由串。**新增 Screen 变体必须同时改这里与 [decodeScreen]。** */
fun screenRoute(screen: Screen): String = when (screen) {
    Screen.Discover -> "discover"
    Screen.Search -> "search"
    Screen.Player -> "player"
    Screen.Queue -> "queue"
    Screen.Login -> "login"
    Screen.Mine -> "mine"
    Screen.Offline -> "offline"
    Screen.History -> "history"
    Screen.Favorites -> "favorites"
    Screen.Settings -> "settings"
    Screen.Playlists -> "playlists"
    is Screen.PlaylistDetail -> "playlistDetail"
}

private fun esc(s: String): String =
    s.replace("%", SCREEN_PCT_ESC).replace(SCREEN_SEP, SCREEN_SEP_ESC)

private fun unesc(s: String): String =
    s.replace(SCREEN_SEP_ESC, SCREEN_SEP).replace(SCREEN_PCT_ESC, "%")

/** 把当前导航态编码成一个可存进 Bundle 的字符串。 */
fun encodeScreen(screen: Screen): String {
    val route = screenRoute(screen)
    if (screen !is Screen.PlaylistDetail) return route
    val p = screen.playlist
    return listOf(
        route,
        esc(p.id),
        esc(p.name),
        esc(p.coverUrl),
        p.songCount.toString(),
        esc(p.description),
        esc(p.creator)
    ).joinToString(SCREEN_SEP)
}

/**
 * 解码导航态。**任何异常/未知 route/字段不足都降级成 [Screen.Discover]**：
 * 存盘状态可能是上一个版本写下的（改了 route 名、删了字段），崩溃比回首页更糟。
 */
fun decodeScreen(raw: String?): Screen {
    if (raw.isNullOrBlank()) return Screen.Discover
    val f = raw.split(SCREEN_SEP)
    val route = f.first()
    val simple = when (route) {
        "discover" -> Screen.Discover
        "search" -> Screen.Search
        "player" -> Screen.Player
        "queue" -> Screen.Queue
        "login" -> Screen.Login
        "mine" -> Screen.Mine
        "offline" -> Screen.Offline
        "history" -> Screen.History
        "favorites" -> Screen.Favorites
        "settings" -> Screen.Settings
        "playlists" -> Screen.Playlists
        else -> null
    }
    if (simple != null) return simple
    if (route != "playlistDetail" || f.size < 7) return Screen.Discover
    return Screen.PlaylistDetail(
        Playlist(
            id = unesc(f[1]),
            name = unesc(f[2]),
            coverUrl = unesc(f[3]),
            songCount = f[4].toIntOrNull() ?: 0,
            description = unesc(f[5]),
            creator = unesc(f[6])
        )
    )
}

/** `rememberSaveable(stateSaver = ScreenSaver)` 用的 Saver。 */
val ScreenSaver: Saver<Screen, String> = Saver(
    save = { encodeScreen(it) },
    restore = { decodeScreen(it) }
)
