package com.cyk666.vibemusic

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
                    tint = if (selectedTab == 0) NeonViolet else GrayMuted,
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
                    tint = if (selectedTab == 1) NeonViolet else GrayMuted,
                    filled = selectedTab == 1
                )
            }
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onSelect(Screen.Assistant) },
            label = { Text("助手") },
            icon = {
                // 鲸鱼图标保留 Twemoji 原色（多色扁平设计），选中/未选中用大小+透明度区分：
                // 单色 tint 会把这个图形最关键的蓝色抹掉，反而失去辨识度。
                Icon(
                    painter = painterResource(R.drawable.ic_whale),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .size(if (selectedTab == 2) 26.dp else 23.dp)
                        .alpha(if (selectedTab == 2) 1f else 0.62f)
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
                    tint = if (selectedTab == 3) NeonViolet else GrayMuted,
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
                    tint = if (selectedTab == 4) NeonViolet else GrayMuted,
                    filled = selectedTab == 4
                )
            }
        )
    }
}
