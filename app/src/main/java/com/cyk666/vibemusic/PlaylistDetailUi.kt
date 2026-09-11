package com.cyk666.vibemusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Standalone QQ-style playlist detail page (Obsidian Bloom dark).
 *
 * Top bar (back + search + overflow) → header (cover/title/creator/count/
 * 2-line description) → 分享 action → big 播放全部 row → song rows with
 * ⋯ menus (收藏/加入歌单/下载/从歌单移除). Skeleton + empty + diagnosable
 * error states mirror the Mine list.
 */
@Composable
fun PlaylistDetailScreen(
    modifier: Modifier = Modifier,
    playlist: Playlist,
    songs: List<Song>,
    songsLoading: Boolean,
    songsError: String? = null,
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
    onGoSearch: () -> Unit = {},
    onPlayAll: () -> Unit = {},
    onPlaySong: (Int) -> Unit = {},
    favIds: Set<String> = emptySet(),
    downloadingKeys: Set<String> = emptySet(),
    downloadedKeys: Set<String> = emptySet(),
    onToggleFav: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onDownload: (Song) -> Unit = {},
    onRemoveSong: (Song) -> Unit = {},
    onShare: (String) -> Unit = {},
    onRenamePlaylist: (Playlist, String) -> Unit = { _, _ -> },
    onUpdateDescription: (Playlist, String) -> Unit = { _, _ -> },
    onDeletePlaylist: (Playlist) -> Unit = {},
    onImportPlaylist: (String, String) -> Unit = { _, _ -> }
) {
    var showOverflow by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var descOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var importOpen by remember { mutableStateOf(false) }
    var songSheetFor by remember { mutableStateOf<Song?>(null) }
    var confirmRemove by remember { mutableStateOf<Song?>(null) }
    // In-playlist search: toggled by the top-bar icon, live-filters the
    // already-loaded songs (no network). Reset when switching playlists.
    var filterOpen by remember { mutableStateOf(false) }
    var filterQuery by remember(playlist.id) { mutableStateOf("") }
    val filtering = filterOpen && filterQuery.trim().isNotEmpty()
    val visibleSongs = remember(songs, filterOpen, filterQuery) {
        if (filterOpen) filterInPlaylistSongs(songs, filterQuery) else songs
    }

    val vipCount = remember(songs) { countVipSongs(songs) }
    val count = resolveDetailSongCount(songs, songsLoading, playlist.songCount)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                AppIcon(AppIconKind.CHEVRON_LEFT, UiMuted)
            }
            Text(
                text = playlist.name.ifBlank { "(untitled)" },
                style = MaterialTheme.typography.titleMedium,
                color = UiInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    filterOpen = !filterOpen
                    if (!filterOpen) filterQuery = ""
                },
                modifier = Modifier.size(48.dp)
            ) {
                AppIcon(AppIconKind.SEARCH, UiMuted)
            }
            IconButton(onClick = { showOverflow = true }, modifier = Modifier.size(48.dp)) {
                AppIcon(AppIconKind.MORE, UiMuted)
            }
        }
        if (filterOpen) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("筛选本歌单歌曲") },
                singleLine = true,
                trailingIcon = {
                    if (filterQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { filterQuery = "" },
                            modifier = Modifier.size(48.dp)
                        ) {
                            AppIcon(AppIconKind.CLOSE, UiMuted)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            if (filtering) {
                Text(
                    text = "${visibleSongs.size}/${songs.size}首",
                    style = MaterialTheme.typography.bodySmall,
                    color = UiMuted,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
        if (songsLoading && songs.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                DetailHeaderSkeleton()
                SearchSkeleton()
            }
        } else if (songsError != null && songs.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                DetailHeader(
                    playlist = playlist,
                    count = count,
                    vipCount = vipCount,
                    onPlayAll = {},
                    onShare = {},
                    playEnabled = false
                )
                Spacer(Modifier.padding(top = 8.dp))
                DiscoverRetryRow(message = songsError, onRetry = onRetry)
            }
        } else if (songs.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                DetailHeader(
                    playlist = playlist,
                    count = count,
                    vipCount = vipCount,
                    onPlayAll = {},
                    onShare = {},
                    playEnabled = false
                )
                Spacer(Modifier.padding(top = 8.dp))
                EmptyStateLine(
                    text = "歌单是空的，去搜索页把喜欢的歌加进来吧",
                    actionLabel = "去搜索",
                    onAction = onGoSearch
                )
            }
        } else if (filtering && visibleSongs.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                EmptyStateLine(
                    text = "没有匹配「${filterQuery.trim()}」的歌曲",
                    actionLabel = "清除筛选",
                    onAction = { filterQuery = "" }
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item(key = "detail-header") {
                    DetailHeader(
                        playlist = playlist,
                        count = count,
                        vipCount = vipCount,
                        onPlayAll = onPlayAll,
                        onShare = { onShare(buildShareText(playlist.name, songs)) },
                        playEnabled = true
                    )
                }
                itemsIndexed(
                    visibleSongs,
                    key = { idx, s -> s.sourceId + s.platform + idx }
                ) { _, song ->
                    SongRow(
                        model = buildSongRowModel(song),
                        meta = if (song.durationSec > 0) formatDuration(song.durationSec) else null,
                        onClick = { onPlaySong(songs.indexOf(song).takeIf { it >= 0 } ?: 0) },
                        onOverflow = { songSheetFor = song }
                    )
                }
            }
        }
    }
    if (showOverflow) {
        SongMenuSheet(
            title = playlist.name.ifBlank { "(untitled)" },
            actions = listOf(
                SongMenuAction("rename", "重命名"),
                SongMenuAction("desc", "改简介"),
                SongMenuAction("import", "导入外部歌单"),
                SongMenuAction("delete", "删除歌单", danger = true)
            ),
            onAction = { id ->
                when (id) {
                    "rename" -> renameOpen = true
                    "desc" -> descOpen = true
                    "import" -> importOpen = true
                    "delete" -> deleteOpen = true
                }
                showOverflow = false
            },
            onDismiss = { showOverflow = false }
        )
    }
    if (renameOpen) {
        PlaylistTextDialog(
            title = "重命名",
            initial = playlist.name,
            label = "新歌单名",
            confirmText = "保存",
            onConfirm = { name, _ ->
                renameOpen = false
                onRenamePlaylist(playlist, name)
            },
            onDismiss = { renameOpen = false }
        )
    }
    if (descOpen) {
        PlaylistTextDialog(
            title = "改简介",
            initial = playlist.description,
            label = "简介",
            confirmText = "保存",
            onConfirm = { desc, _ ->
                descOpen = false
                onUpdateDescription(playlist, desc)
            },
            onDismiss = { descOpen = false }
        )
    }
    if (deleteOpen) {
        DangerConfirmDialog(
            title = "删除歌单",
            text = "确定删除「${playlist.name}」吗？组内歌曲一并移除，不可恢复。",
            confirmText = "删除",
            onConfirm = {
                deleteOpen = false
                onDeletePlaylist(playlist)
            },
            onDismiss = { deleteOpen = false }
        )
    }
    if (importOpen) {
        ImportDialog(
            onConfirm = { source, id ->
                importOpen = false
                onImportPlaylist(source, id)
            },
            onDismiss = { importOpen = false }
        )
    }
    songSheetFor?.let { target ->
        val dlKey = offlineBaseName(target)
        val faved = target.sourceId.isNotBlank() && target.sourceId in favIds
        SongMenuSheet(
            title = target.name.ifBlank { "(untitled)" },
            actions = listOf(
                SongMenuAction("fav", if (faved) "取消收藏" else "收藏"),
                SongMenuAction(
                    "download",
                    when {
                        dlKey in downloadingKeys -> "下载中…"
                        dlKey in downloadedKeys -> "已下载"
                        else -> "下载"
                    },
                    enabled = dlKey !in downloadingKeys
                ),
                SongMenuAction("add", "加入歌单"),
                SongMenuAction("remove", "从歌单移除", danger = true)
            ),
            onAction = { id ->
                when (id) {
                    "fav" -> onToggleFav(target)
                    "download" -> onDownload(target)
                    "add" -> onAddToPlaylist(target)
                    "remove" -> confirmRemove = target
                }
                songSheetFor = null
            },
            onDismiss = { songSheetFor = null }
        )
    }
    confirmRemove?.let { target ->
        DangerConfirmDialog(
            title = "从歌单删除",
            text = "确定把《${target.name}》从「${playlist.name}」移除吗？",
            confirmText = "删除",
            onConfirm = {
                confirmRemove = null
                onRemoveSong(target)
            },
            onDismiss = { confirmRemove = null }
        )
    }
}

@Composable
private fun DetailHeader(
    playlist: Playlist,
    count: Int,
    vipCount: Int,
    onPlayAll: () -> Unit,
    onShare: () -> Unit,
    playEnabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playlist.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                        .background(UiSurface),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(AppIconKind.MUSIC_NOTE, UiMuted, size = 40.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name.ifBlank { "(untitled)" },
                    style = MaterialTheme.typography.titleLarge,
                    color = UiInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.padding(top = 4.dp))
                if (playlist.creator.isNotBlank()) {
                    Text(
                        text = playlist.creator,
                        style = MaterialTheme.typography.bodySmall,
                        color = UiMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "共${count.coerceAtLeast(0)}首",
                    style = MaterialTheme.typography.bodySmall,
                    color = UiMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (playlist.description.isNotBlank()) {
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                text = playlist.description,
                style = MaterialTheme.typography.bodySmall,
                color = UiMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.padding(top = 12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onShare,
                enabled = playEnabled,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            ) {
                Text("分享")
            }
        }
        Spacer(Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(UiSurface)
                .clickable(enabled = playEnabled) { onPlayAll() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                kind = AppIconKind.PLAY_CIRCLE,
                tint = if (playEnabled) UiViolet else UiMuted,
                filled = true,
                size = 32.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "播放全部",
                    style = MaterialTheme.typography.titleMedium,
                    color = UiInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildPlayAllLabel(count, vipCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = UiMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun DetailHeaderSkeleton() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ShimmerBox(modifier = Modifier.size(96.dp), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp))
            Spacer(Modifier.padding(top = 8.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).heightIn(min = 16.dp))
        }
    }
    Spacer(Modifier.padding(top = 12.dp))
}
