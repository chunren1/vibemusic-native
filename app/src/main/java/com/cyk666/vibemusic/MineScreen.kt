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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MineScreen(
    modifier: Modifier = Modifier,
    user: LoggedInUser?,
    authChecked: Boolean,
    playlists: List<Playlist>,
    playlistsLoading: Boolean,
    onLoginClick: () -> Unit,
    onRetryPlaylists: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String, String) -> Unit = { _, _ -> },
    onRenamePlaylist: (Playlist, String) -> Unit = { _, _ -> },
    onUpdateDescription: (Playlist, String) -> Unit = { _, _ -> },
    onDeletePlaylist: (Playlist) -> Unit = {},
    onBatchDelete: (List<String>) -> Unit = {},
    onMovePlaylist: (Playlist, Int) -> Unit = { _, _ -> },
    onImportPlaylist: (String, String) -> Unit = { _, _ -> },
    offlineCount: Int = 0,
    onOpenOffline: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    historyCount: Int = 0,
    onOpenHistory: () -> Unit = {},
    favCount: Int = 0,
    onOpenFavorites: () -> Unit = {},
    onGoSearch: () -> Unit = {},
    onBrowse: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
) {
    var showProfileView by remember { mutableStateOf(false) }
    val overviewScroll = rememberScrollState()
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
            .verticalScroll(overviewScroll)
    ) {
        if (!authChecked) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(8.dp))
            Text("正在恢复登录态…", style = MaterialTheme.typography.bodySmall)
            return
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "我的",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = InkOnDark,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(48.dp)
            ) {
                AppIcon(AppIconKind.SETTINGS, GrayMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (user == null) {
            MineUserCard(
                displayName = "未登录",
                sub = "登录后看我的歌单；访客可继续搜歌",
                avatarUrl = "",
                bgUrl = "",
                onClick = onLoginClick
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
                Text("去登录")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRegisterClick, modifier = Modifier.fillMaxWidth()) {
                Text("注册新账号")
            }
            TextButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                Text("先逛逛")
            }
            Spacer(Modifier.height(12.dp))
            MineTripleRow(
                items = buildMineTriple(
                    favCount = 0,
                    historyCount = 0,
                    offlineCount = offlineCount,
                    loggedIn = false
                ),
                onCell = { id ->
                    when (id) {
                        "favorites" -> onOpenFavorites()
                        "history" -> onLoginClick()
                        else -> onOpenOffline()
                    }
                }
            )
            return
        }
        val displayName = user.nickname.ifBlank { user.username }
        val profileSub = "@${user.username}" +
            listOf(user.gender, user.birthday)
                .filter { it.isNotBlank() }
                .joinToString(" · ", prefix = " · ")
                .takeIf { user.gender.isNotBlank() || user.birthday.isNotBlank() }
                .orEmpty()
        MineUserCard(
            displayName = displayName,
            sub = profileSub,
            avatarUrl = user.avatar,
            bgUrl = user.bgImage,
            onClick = { showProfileView = true }
        )
        Spacer(Modifier.height(8.dp))
        if (showProfileView) {
            ProfileViewDialog(
                user = user,
                onOpenSettings = {
                    showProfileView = false
                    onOpenSettings()
                },
                onDismiss = { showProfileView = false }
            )
        }
        MineTripleRow(
            items = buildMineTriple(
                favCount = favCount,
                historyCount = historyCount,
                offlineCount = offlineCount,
                loggedIn = true
            ),
            onCell = { id ->
                when (id) {
                    "favorites" -> onOpenFavorites()
                    "history" -> onOpenHistory()
                    else -> onOpenOffline()
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "自建歌单 (${playlists.size})",
                style = MaterialTheme.typography.titleSmall,
                color = InkOnDark,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenPlaylists) {
                Text("管理")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (playlistsLoading) {
            SearchSkeleton()
        } else if (playlists.isEmpty()) {
            EmptyStateLine(
                text = "还没有歌单，新建一个开始收藏吧",
                actionLabel = "去搜索",
                onAction = onGoSearch
            )
        } else {
            MineSectionCard {
                playlists.forEachIndexed { index, pl ->
                    if (index > 0) MineDivider()
                    EntryRow(
                        modifier = Modifier.heightIn(min = 56.dp),
                        title = pl.name.ifBlank { "(untitled)" },
                        subtitle = "${pl.songCount} 首",
                        coverUrl = pl.coverUrl,
                        onClick = { onSelectPlaylist(pl) },
                        trailing = { AppIcon(AppIconKind.CHEVRON_RIGHT, GrayMuted) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MineAvatar(avatarUrl: String, displayName: String) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(NeonViolet, NeonCyan)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = avatarInitial(displayName),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(absImgUrl(avatarUrl).ifBlank { null })
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
        )
    }
}

@Composable
private fun MineUserCard(
    displayName: String,
    sub: String,
    avatarUrl: String,
    bgUrl: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(ObsidianSurface)
    ) {
        if (shouldShowMineBg(bgUrl)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(absImgUrl(bgUrl))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x99000000),
                                Color(0x66000000),
                                Color(0xCC000000)
                            )
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MineAvatar(avatarUrl = avatarUrl, displayName = displayName)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = InkOnDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MineTripleRow(
    items: List<MineTripleItem>,
    onCell: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurface)
            .padding(vertical = 8.dp)
    ) {
        items.forEach { e ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .clickable { onCell(e.id) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = e.count,
                    style = MaterialTheme.typography.titleLarge,
                    color = InkOnDark,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = e.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun absImgUrl(path: String): String {
    val t = path.trim()
    if (t.isBlank()) return ""
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    return VibeApi.BASE_URL.trimEnd('/') + (if (t.startsWith("/")) t else "/$t")
}

fun avatarInitial(name: String): String {
    for (ch in name.trim()) {
        if (ch.isLetterOrDigit()) return ch.uppercaseChar().toString()
    }
    return "♪"
}

fun shouldShowMineBg(bgImage: String): Boolean = bgImage.isNotBlank()
