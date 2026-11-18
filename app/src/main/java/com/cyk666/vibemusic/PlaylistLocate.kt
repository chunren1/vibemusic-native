package com.cyk666.vibemusic

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 歌单详情顶栏的「定位到正在播放」。
 *
 * <p>只做一件事：算出正在播的那首在**当前可见列表**里的下标，然后动画滚过去。
 * 用 visibleSongs 而不是全量 songs —— 开了筛选时，定位必须落在看得见的那份列表上，
 * 否则会滚动到一个被筛掉的空位置。</p>
 *
 * <p>LazyColumn 前面还有一个 header item，所以目标下标 +1。</p>
 */
@Composable
fun PlaylistLocateButton(
    listState: LazyListState,
    visibleSongs: List<Song>,
    currentPlayingKey: String?
) {
    val index = remember(visibleSongs, currentPlayingKey) {
        if (currentPlayingKey.isNullOrBlank()) -1
        else visibleSongs.indexOfFirst { it.platform + ":" + it.sourceId == currentPlayingKey }
    }
    if (index < 0) return
    val scope = rememberCoroutineScope()
    IconButton(
        onClick = { scope.launch { listState.animateScrollToItem(index + 1) } },
        modifier = Modifier.size(48.dp)
    ) {
        AppIcon(AppIconKind.MUSIC_NOTE, UiAccent)
    }
}

/** 便捷重载：自己持有滚动状态（调用方不需要 rememberLazyListState 时用）。 */
@Composable
fun rememberPlaylistListState(): LazyListState = rememberLazyListState()
