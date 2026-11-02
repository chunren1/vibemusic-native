package com.cyk666.vibemusic

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 各页头部「‹ 我的 / ‹ 返回播放」返回键的唯一实现（设置 / 收藏 / 最近播放 /
 * 本地下载 / 我的歌单 / 播放队列 六处共用）。
 *
 * 此前六处各写一遍 `TextButton { AppIcon(CHEVRON_LEFT, 灰, 20.dp); Text("我的") }`，
 * 于是同一行出现三种样式 —— 因为 M3 的 `TextButton` 默认把 `contentColor` 设成
 * `colorScheme.primary`、文字默认 `labelLarge`（14sp / Medium）：
 * - 箭头 = 灰（GrayMuted / UiMuted，其实是同一个 `0xFF9CA3AF` 的两个 token）；
 * - 「我的」= 主题主色 + labelLarge；
 * - 旁边的标题 = `titleMedium`（16sp）+ 默认 ink 色。
 *
 * 用户看到的就是「返回像一颗彩色按钮，字号还跟标题不一样」。这里定死一条口径：
 * **图标与文字同 token（[UiMuted]）、同字号（titleMedium 16sp，与旁边标题同尺寸）**，
 * 层级靠颜色而不靠字号表达；颜色 token 统一到 [UiMuted]，不再用 [GrayMuted]。
 * 图标不给 contentDescription（默认也是 null）：文字已是标签，TalkBack 别念两遍。
 */
@Composable
fun BackNavButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = UiMuted),
        // 箭头贴住行首：默认 24dp 起始内边距会让「‹」离页面左边缘过远，
        // 和下方卡片的 16dp 对齐线错开。
        contentPadding = PaddingValues(start = 0.dp, end = 8.dp)
    ) {
        AppIcon(AppIconKind.CHEVRON_LEFT, UiMuted, size = 20.dp, contentDescription = null)
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = UiMuted
        )
    }
}
