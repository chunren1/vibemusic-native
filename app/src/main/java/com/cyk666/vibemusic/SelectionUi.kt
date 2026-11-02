package com.cyk666.vibemusic

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ---- 1. 纯逻辑：三个多选页面（下载 / 我的歌单 / 歌单详情）共用 ----

/** 纯函数：多选集合的增删。 */
fun toggleSelected(selected: Set<String>, key: String): Set<String> =
    if (key in selected) selected - key else selected + key

/** 纯函数：列表里每一项都被勾选才算「全选」（空列表不算，否则会显示「全不选」）。 */
fun allSelected(keys: List<String>, selected: Set<String>): Boolean =
    keys.isNotEmpty() && keys.all { it in selected }

/** 纯函数：点「全选」应写入的集合；已经全选就清空，对应「全不选」。 */
fun toggleAllSelected(keys: List<String>, selected: Set<String>): Set<String> =
    if (allSelected(keys, selected)) emptySet() else keys.toSet()

/** 纯函数：列表被删空/刷新成空后，多选态该自动退出（否则留一个没有行的工具栏）。 */
fun shouldExitSelection(selecting: Boolean, rowCount: Int): Boolean =
    selecting && rowCount == 0

// ---- 2. 共享组件 ----

/**
 * 多选态顶部工具栏：取消 / 已选 N 项 / 全选 / 破坏性动作。
 *
 * 三个页面共用同一套，避免各写一份后视觉漂移（`actionLabel` 只换文案：
 * 下载页「删除」、歌单详情「移除」）。退出多选的另外两条路径在调用方：
 * 左上返回与系统返回键（BackHandler）。
 */
@Composable
fun SelectionToolbar(
    count: Int,
    allSelected: Boolean,
    actionLabel: String,
    onCancel: () -> Unit,
    onToggleAll: () -> Unit,
    onAction: () -> Unit,
    actionEnabled: Boolean = count > 0,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_DP.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCancel) { Text("取消") }
        Text(
            text = "已选 $count 项",
            style = MaterialTheme.typography.titleSmall,
            color = UiInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onToggleAll) { Text(if (allSelected) "全不选" else "全选") }
        TextButton(onClick = onAction, enabled = actionEnabled) {
            Text(actionLabel, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * 可多选的歌曲行：共享 [EntryRow] 没有 leading 槽，这里在它左边挂一个勾选圈；
 * 多选时顺手收掉右侧溢出按钮（那是单曲菜单，多选态下没有意义）。
 */
@Composable
fun SelectableSongRow(
    model: SongRowModel,
    meta: String?,
    checked: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOverflow: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (selecting) SelectionDot(checked)
        EntryRow(
            title = model.title,
            subtitle = model.subtitle,
            coverUrl = model.coverUrl,
            coverSize = 56.dp,
            meta = meta,
            onClick = onClick,
            onLongClick = onLongClick,
            badge = if (isBilibiliPlatform(model.platform)) {
                { BiliBadge() }
            } else {
                null
            },
            trailing = if (selecting) {
                null
            } else {
                {
                    IconButton(onClick = onOverflow, modifier = Modifier.size(48.dp)) {
                        AppIcon(AppIconKind.MORE, UiMuted)
                    }
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 勾选圈：未选空心描边，选中填充主题主色 + 对勾。
 *
 * `internal` 是因为「我的歌单」页的行是 `EntryRow`（不是歌曲），没有走
 * [SelectableSongRow]，但三页的多选视觉必须一致，故单独复用这个圆圈。
 */
@Composable
internal fun SelectionDot(checked: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(
        targetValue = if (checked) accent else Color.Transparent,
        label = "selectionDotFill"
    )
    Box(
        modifier = Modifier
            .padding(start = 2.dp, end = 8.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = 1.5.dp,
                color = if (checked) accent else UiMuted.copy(alpha = 0.55f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            AppIcon(
                kind = AppIconKind.CHECK,
                tint = MaterialTheme.colorScheme.onPrimary,
                size = 13.dp,
                contentDescription = null
            )
        }
    }
}
