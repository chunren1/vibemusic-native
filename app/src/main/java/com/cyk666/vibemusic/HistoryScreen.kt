package com.cyk666.vibemusic

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.activity.SystemBarStyle
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cyk666.vibemusic.MediaCache.toCachedMediaItem
import com.cyk666.vibemusic.OfflineStore.toLocalMediaItem
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    items: List<VibeApi.HistoryItem>,
    loading: Boolean,
    favIds: Set<String> = emptySet(),
    onPlayAt: (Int) -> Unit,
    onToggleFav: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onDeleteOne: (VibeApi.HistoryItem) -> Unit = {},
    onClearAll: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<VibeApi.HistoryItem?>(null) }
    var sheetFor by remember { mutableStateOf<VibeApi.HistoryItem?>(null) }
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                AppIcon(AppIconKind.CHEVRON_LEFT, GrayMuted, size = 20.dp)
                Text("我的")
            }
            Text(
                text = "最近播放",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (items.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) {
                    Text("清空")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (loading) {
            SearchSkeleton()
            return
        }
        if (items.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("还没有播放记录，去搜一首播吧")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) {
                Text("刷新")
            }
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(items, key = { idx, h -> h.song.sourceId + h.song.platform + idx }) { index, item ->
                val song = item.song
                SongRow(
                    model = buildSongRowModel(
                        song,
                        subtitleOverride = if (item.playedAt.isNotBlank()) {
                            item.playedAt.replace("T", " ").take(16) + " · " + song.artist
                        } else {
                            "第${index + 1}首 · " + song.artist
                        }
                    ),
                    onClick = { onPlayAt(index) },
                    onOverflow = { sheetFor = item }
                )
            }
        }
    }
    sheetFor?.let { target ->
        val faved = target.song.sourceId.isNotBlank() && target.song.sourceId in favIds
        SongMenuSheet(
            title = target.song.name.ifBlank { "(untitled)" },
            actions = listOf(
                SongMenuAction("fav", if (faved) "取消收藏" else "收藏"),
                SongMenuAction("add", "加入歌单"),
                SongMenuAction("delete", "删除记录", danger = true)
            ),
            onAction = { id ->
                when (id) {
                    "fav" -> onToggleFav(target.song)
                    "add" -> onAddToPlaylist(target.song)
                    "delete" -> confirmDelete = target
                }
                sheetFor = null
            },
            onDismiss = { sheetFor = null }
        )
    }
    if (showClearConfirm) {
        DangerConfirmDialog(
            title = "清空历史",
            text = "确定清空全部 ${items.size} 条播放记录吗？不可恢复。",
            confirmText = "清空",
            onConfirm = {
                showClearConfirm = false
                onClearAll()
            },
            onDismiss = { showClearConfirm = false }
        )
    }
    confirmDelete?.let { target ->
        DangerConfirmDialog(
            title = "删除记录",
            text = "确定删除《${target.song.name.ifBlank { "(untitled)" }}》的播放记录吗？",
            confirmText = "删除",
            onConfirm = {
                confirmDelete = null
                onDeleteOne(target)
            },
            onDismiss = { confirmDelete = null }
        )
    }
}
