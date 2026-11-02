package com.cyk666.vibemusic

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 「新建歌单」的两条入口（从 PlaylistsUi.kt 抽出：那个文件靠棘轮卡 313 纯 LOC，
 * 而且这两条入口是同一件事，本来就该待在一起）。
 *
 * 改版理由：原来是一条横跨整行的主色 `Button("新建歌单")`，紧贴「全部歌单」卡片
 * 上方 —— 两个区块抢同一层级，看着像「新建」比列表还重要。现在降级成两个入口：
 * 1. 标题行最右的「＋」图标：常驻，空态 / 失败态 / 加载态都能新建
 *    （原来是空态只剩「去搜索」，根本没有新建入口）；
 * 2. 「全部歌单」卡片里列表末尾的「＋ 新建歌单」：跟着列表走，文案与
 *    AddToPlaylistSheet 里的同款入口保持一致（同一个字符串），颜色走 UiMuted
 *    不抢主色。
 *
 * 两条入口在多选态都由调用方隐藏（多选时不该能新建）。
 */

/** 入口一：标题行右端的「＋」。显式给 contentDescription（默认标签是「添加」，语义太泛）。 */
@Composable
fun PlaylistsCreateIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        AppIcon(AppIconKind.ADD, UiInk, contentDescription = "新建歌单")
    }
}

/** 入口二：「全部歌单」卡片末行。48dp 触摸区，文字不抢主色。 */
@Composable
fun PlaylistsCreateFooterRow(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(
            text = "＋ 新建歌单",
            style = MaterialTheme.typography.bodyMedium,
            color = UiMuted
        )
    }
}
