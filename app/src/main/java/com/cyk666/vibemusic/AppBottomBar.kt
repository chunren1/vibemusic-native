package com.cyk666.vibemusic

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 底部导航栏（从 `MainActivity.kt` 抽出，2026-11-09）。
 *
 * 抽出的原因：加「助手」tab 时 `MainActivity.kt` 纯 LOC 已顶到 3408/3409 的体量棘轮上限
 * （`scripts/check-file-size.sh` 只降不升），而每个 tab 是 11 行手写 `NavigationBarItem`。
 * 把整块搬进独立文件后，Activity 那边只剩一次调用，且以后加 tab 不必再动 3900 行的文件。
 *
 * 选中态仍是外部传进来的下标：`screen` → tab 的映射属于导航语义，留在 Activity
 * （那里能一眼看全 `Screen` 的所有分支）。
 */
@Composable
internal fun AppBottomBar(
    selectedTab: Int,
    onSelect: (Screen) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onSelect(Screen.Discover) },
            label = { Text("首页") },
            icon = {
                AppIcon(
                    kind = AppIconKind.EXPLORE,
                    tint = if (selectedTab == 0) UiAccent else GrayMuted,
                    filled = selectedTab == 0
                )
            }
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onSelect(Screen.Search) },
            label = { Text("搜索") },
            icon = {
                AppIcon(
                    kind = AppIconKind.SEARCH,
                    tint = if (selectedTab == 1) UiAccent else GrayMuted,
                    filled = selectedTab == 1
                )
            }
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onSelect(Screen.Assistant) },
            label = { Text("助手") },
            icon = {
                // 星芒 ✦：与其它四个 tab 同族的单色矢量符号，选中金/未选中灰。
                // 不用卡通 IP：多色 emoji 混在单色图标组里必然违和（2026-11-11 反馈）。
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = if (selectedTab == 2) UiAccent else GrayMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
        )
        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onSelect(Screen.Player) },
            label = { Text("播放") },
            icon = {
                AppIcon(
                    kind = AppIconKind.PLAY_CIRCLE,
                    tint = if (selectedTab == 3) UiAccent else GrayMuted,
                    filled = selectedTab == 3
                )
            }
        )
        NavigationBarItem(
            selected = selectedTab == 4,
            onClick = { onSelect(Screen.Mine) },
            label = { Text("我的") },
            icon = {
                AppIcon(
                    kind = AppIconKind.PERSON,
                    tint = if (selectedTab == 4) UiAccent else GrayMuted,
                    filled = selectedTab == 4
                )
            }
        )
    }
}
