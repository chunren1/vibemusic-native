package com.cyk666.vibemusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航态存盘契约（CODE-REVIEW-2026-10-28 UI-5）。
 *
 * 为什么值得单独钉：`screen` 改成 `rememberSaveable` 之后，存进 Bundle 的字符串
 * 会跨「旋转」甚至跨「进程死亡后恢复」使用 —— 如果哪天改了 route 名、加了 Screen
 * 变体却忘了同步 [decodeScreen]，用户旋转后会被扔回首页（旧行为）或直接崩溃（更糟）。
 * 这里把「未知输入一律降级 Discover」钉成断言。
 */
class ScreenTest {

    @Test
    fun simpleRoutesRoundTrip() {
        val screens = listOf(
            Screen.Discover, Screen.Search, Screen.Assistant, Screen.Player, Screen.Queue,
            Screen.Login, Screen.Mine, Screen.Offline, Screen.History,
            Screen.Favorites, Screen.Settings, Screen.Playlists
        )
        for (s in screens) {
            assertEquals(s, decodeScreen(encodeScreen(s)))
        }
    }

    @Test
    fun playlistDetailRoundTripsEveryField() {
        val pl = Playlist(
            id = "pl-42",
            name = "深夜｜雨声与钢琴",
            coverUrl = "https://vibe.cyk666.top/cover/42.jpg?a=1&b=2",
            songCount = 37,
            description = "给自己听的",
            creator = "cyk"
        )
        assertEquals(Screen.PlaylistDetail(pl), decodeScreen(encodeScreen(Screen.PlaylistDetail(pl))))
    }

    @Test
    fun separatorsAndPercentInFieldsSurvive() {
        // 分隔符与转义字符同时出现在字段里：这是编码唯一的失败面。
        val pl = Playlist(
            id = "a|b",
            name = "100% 纯音乐|合集",
            coverUrl = "https://x/y%7Cz.jpg",
            songCount = 0,
            description = "50% off | 限时",
            creator = "%"
        )
        assertEquals(Screen.PlaylistDetail(pl), decodeScreen(encodeScreen(Screen.PlaylistDetail(pl))))
    }

    @Test
    fun unknownOrTruncatedInputFallsBackToDiscover() {
        // 版本漂移：老版本写的 route / 被截断的字段 / 垃圾值，都不许崩。
        assertEquals(Screen.Discover, decodeScreen(null))
        assertEquals(Screen.Discover, decodeScreen(""))
        assertEquals(Screen.Discover, decodeScreen("   "))
        assertEquals(Screen.Discover, decodeScreen("legacyRouteFromV1"))
        assertEquals(Screen.Discover, decodeScreen("playlistDetail|only-id"))
        // 字段齐但 songCount 非法 → 0，不抛。
        assertEquals(
            Screen.PlaylistDetail(Playlist("id", "n", "", 0, "d", "c")),
            decodeScreen("playlistDetail|id|n||oops|d|c")
        )
    }

    @Test
    fun routeIdsAreStable() {
        // 这些字符串一旦进过用户的 Bundle，就不能再改（改了 = 老状态降级回首页）。
        assertEquals("discover", screenRoute(Screen.Discover))
        assertEquals("playlistDetail", screenRoute(Screen.PlaylistDetail(Playlist("i", "n", "", 0))))
    }

    @Test
    fun loginReturnTarget_keepsTabPages() {
        // 从这 5 个 tab 被引导去登录，登录完必须回到原页（2.0.31 之前固定跳 Mine）。
        assertEquals(Screen.Assistant, loginReturnTarget(Screen.Assistant))
        assertEquals(Screen.Search, loginReturnTarget(Screen.Search))
        assertEquals(Screen.Player, loginReturnTarget(Screen.Player))
        assertEquals(Screen.Discover, loginReturnTarget(Screen.Discover))
        assertEquals(Screen.Mine, loginReturnTarget(Screen.Mine))
    }

    @Test
    fun loginReturnTarget_degradesVolatilePagesToMine() {
        // 详情/队列依赖 selectedPlaylist/queue，登录回来已不可靠 → 降级 Mine。
        val pl = Playlist("pl-1", "歌单", "", 3)
        assertEquals(Screen.Mine, loginReturnTarget(Screen.PlaylistDetail(pl)))
        assertEquals(Screen.Mine, loginReturnTarget(Screen.Queue))
        // 登录页自己不能再回登录页（否则返回键原地打转）。
        assertEquals(Screen.Mine, loginReturnTarget(Screen.Login))
    }

    @Test
    fun loginReturnTarget_neverReturnsLogin() {
        // 回归守卫：2.0.33 的 goLogin 把「进登录页」误写成自递归调用，
        // 线上一点登录就 StackOverflow。这里遍历所有入口，顺带钉住
        // 「任何来处都不会把用户再送回登录页」。
        val all = listOf(
            Screen.Discover, Screen.Search, Screen.Assistant, Screen.Player, Screen.Queue,
            Screen.Login, Screen.Mine, Screen.Offline, Screen.History, Screen.Favorites,
            Screen.Settings, Screen.Playlists,
            Screen.PlaylistDetail(Playlist("p", "n", "", 0))
        )
        for (s in all) {
            assertTrue("loginReturnTarget($s) 不能是 Login", loginReturnTarget(s) != Screen.Login)
        }
    }
}
