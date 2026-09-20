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
fun CacheManageRow(
    cacheSizeLabel: String,
    showConfirm: Boolean,
    onAskClear: () -> Unit,
    onConfirmClear: () -> Unit,
    onDismissClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "播放缓存 (rolling)",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "已用 $cacheSizeLabel · 播放缓存 rolling，满150MB自动清理",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onAskClear) {
            Text("清理缓存")
        }
    }
    if (showConfirm) {
        DangerConfirmDialog(
            title = "清理缓存",
            text = "确定清除播放缓存吗？本地下载不受影响； rolling 缓存离线将无法播放，需联网重新缓存。",
            confirmText = "清除",
            onConfirm = onConfirmClear,
            onDismiss = onDismissClear
        )
    }
}

@Composable
fun UpdateChannelRow(
    channel: UpdateChannel,
    onSelect: (UpdateChannel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "更新通道",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = updateChannelSubtitle(channel),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = { onSelect(UpdateChannel.STABLE) },
            enabled = channel != UpdateChannel.STABLE
        ) {
            Text(if (channel == UpdateChannel.STABLE) "✓正式版" else "正式版")
        }
        TextButton(
            onClick = { onSelect(UpdateChannel.BETA) },
            enabled = channel != UpdateChannel.BETA
        ) {
            Text(if (channel == UpdateChannel.BETA) "✓测试版" else "测试版")
        }
    }
}

@Composable
fun VersionRow(
    versionLabel: String,
    checking: Boolean,
    onCheck: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = versionLabel.ifBlank { formatVersionLabel("") },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onCheck, enabled = !checking) {
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(6.dp))
            }
            Text("检查更新")
        }
    }
}

@Composable
fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = GrayMuted,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    user: LoggedInUser?,
    cacheSizeLabel: String,
    storageLabel: String,
    versionLabel: String,
    checkingUpdate: Boolean,
    onCheckUpdate: () -> Unit,
    updateChannel: UpdateChannel = UpdateChannel.STABLE,
    onSelectChannel: (UpdateChannel) -> Unit = {},
    onClearCache: () -> Unit,
    onBack: () -> Unit,
    keepAliveIgnoring: Boolean = false,
    onOpenBatterySettings: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onChangePassword: (String, String) -> Unit = { _, _ -> },
    onUpdateProfile: (String?, String?, String?) -> Unit = { _, _, _ -> },
    onPickAvatar: () -> Unit = {},
    onPickBg: () -> Unit = {},
    authDiagCode: String = "",
    authDiagTimeMs: Long = 0L,
    positionSaveTimeMs: Long = 0L
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var showChangePwd by remember(user?.username) { mutableStateOf(false) }
    var showProfile by remember(user?.username) { mutableStateOf(false) }
    var showCookiePaste by remember { mutableStateOf(false) }
    var cookieStatus by remember { mutableStateOf<VibeApi.CookieStatus?>(null) }
    var cookieLoading by remember { mutableStateOf(false) }
    var cookieMsg by remember { mutableStateOf("") }
    val cookieScope = rememberCoroutineScope()
    fun loadCookieStatus() {
        if (user == null) {
            cookieStatus = null
            cookieMsg = ""
            cookieLoading = false
            return
        }
        cookieLoading = true
        cookieScope.launch {
            try {
                cookieStatus = VibeApi.cookieStatus()
            } catch (e: AuthException) {
                cookieStatus = null
                cookieMsg = e.message ?: "登录过期，请重登"
            } catch (e: Exception) {
                cookieStatus = null
                cookieMsg = friendlyNetworkMessage(e)
            } finally {
                cookieLoading = false
            }
        }
    }
    LaunchedEffect(user?.userId) { loadCookieStatus() }
    val cookieState = cookieStatus
    val cookieSub = when {
        user == null -> "登录后绑定"
        cookieLoading && cookieState == null -> "加载中…"
        cookieState != null -> cookieRowSubtitle(cookieState.has, cookieState.valid, cookieState.needsRebind)
        cookieMsg.isNotBlank() -> cookieMsg
        else -> "未绑定"
    }
    val rows = remember(cacheSizeLabel, storageLabel, versionLabel, cookieSub) {
        buildSettingsRows(
            cacheLabel = "已用 $cacheSizeLabel · 满150MB自动清理",
            storageLabel = storageLabel,
            versionLabel = versionLabel.ifBlank { formatVersionLabel("") },
            cookieLabel = cookieSub
        )
    }
    val themeRow = rows.first { it.id == "theme" }
    val cookieRow = rows.first { it.id == "cookie" }
    val storageRow = rows.first { it.id == "storage" }
    val aboutRow = rows.first { it.id == "about" }
    val cookieRebind = user != null && cookieState?.needsRebind == true
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                AppIcon(AppIconKind.CHEVRON_LEFT, GrayMuted, size = 20.dp)
                Text("我的")
            }
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }
        if (user != null) {
            SettingsSectionTitle("账号资料")
            MineSectionCard {
                SettingsRowShell(
                    title = "@${user.username}",
                    subtitle = user.nickname.ifBlank { "昵称未设置" },
                    showChevron = false
                )
                MineDivider()
                SettingsRowShell(
                    title = "改资料",
                    subtitle = "昵称/性别/生日",
                    onClick = { showProfile = true }
                )
                MineDivider()
                SettingsRowShell(
                    title = "改密码",
                    subtitle = "至少8位",
                    onClick = { showChangePwd = true }
                )
                MineDivider()
                SettingsRowShell(
                    title = "换头像",
                    subtitle = "最大 2MB",
                    onClick = onPickAvatar
                )
                MineDivider()
                SettingsRowShell(
                    title = "换背景",
                    subtitle = "我的页头图",
                    onClick = onPickBg
                )
            }
            if (showChangePwd) {
                ChangePasswordDialog(
                    onConfirm = { old, new ->
                        showChangePwd = false
                        onChangePassword(old, new)
                    },
                    onDismiss = { showChangePwd = false }
                )
            }
            if (showProfile) {
                ProfileDialog(
                    initialNickname = user.nickname,
                    initialGender = user.gender,
                    initialBirthday = user.birthday,
                    onConfirm = { nickname, gender, birthday ->
                        showProfile = false
                        onUpdateProfile(nickname, gender, birthday)
                    },
                    onDismiss = { showProfile = false }
                )
            }
        } else {
            SettingsSectionTitle("账号")
            Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
                Text("去登录")
            }
        }
        SettingsSectionTitle("音乐服务")
        MineSectionCard {
            SettingsRowShell(
                title = cookieRow.title,
                subtitle = cookieRow.subtitle,
                onClick = {
                    if (user == null) onLoginClick()
                    else showCookiePaste = true
                }
            )
            if (cookieRebind) {
                MineDivider()
                SettingsRowShell(
                    title = "重新绑定",
                    subtitle = "网易 Cookie 已失效，请粘贴新的",
                    onClick = { showCookiePaste = true }
                )
            }
            if (user != null && cookieMsg.isNotBlank()) {
                MineDivider()
                SettingsRowShell(
                    title = "状态",
                    subtitle = cookieMsg,
                    showChevron = false
                )
            }
        }
        if (showCookiePaste) {
            CookiePasteDialog(
                showUnbind = cookieState?.has == true,
                onConfirm = { pasted ->
                    showCookiePaste = false
                    cookieScope.launch {
                        try {
                            VibeApi.saveNeteaseCookie(pasted)
                            cookieStatus = VibeApi.cookieStatus()
                            cookieMsg = "绑定成功"
                        } catch (e: AuthException) {
                            cookieMsg = e.message ?: "登录过期，请重登"
                        } catch (e: Exception) {
                            cookieMsg = "绑定失败: ${friendlyNetworkMessage(e)}"
                        }
                    }
                },
                onDismiss = { showCookiePaste = false },
                onDelete = {
                    showCookiePaste = false
                    cookieScope.launch {
                        try {
                            VibeApi.deleteNeteaseCookie()
                            cookieStatus = VibeApi.cookieStatus()
                            cookieMsg = "已解绑"
                        } catch (e: AuthException) {
                            cookieMsg = e.message ?: "登录过期，请重登"
                        } catch (e: Exception) {
                            cookieMsg = "解绑失败: ${friendlyNetworkMessage(e)}"
                        }
                    }
                }
            )
        }
        SettingsSectionTitle("存储")
        MineSectionCard {
            CacheManageRow(
                cacheSizeLabel = cacheSizeLabel,
                showConfirm = showClearConfirm,
                onAskClear = { showClearConfirm = true },
                onConfirmClear = {
                    showClearConfirm = false
                    onClearCache()
                },
                onDismissClear = { showClearConfirm = false }
            )
            MineDivider()
            SettingsRowShell(title = storageRow.title, subtitle = storageRow.subtitle)
            MineDivider()
            // 后台保活：暂停后 Media3 必然退前台，vivo 约 2-3 分钟清进程 → 通知播放键失灵。
            // 框架内无解，白名单是现实解（详见 PlaybackService 与 BeautyPhase10 注释）。
            val keepAlive = keepAliveRow(keepAliveIgnoring)
            SettingsRowShell(
                title = keepAlive.title,
                subtitle = keepAlive.subtitle,
                showChevron = false,
                onClick = if (keepAliveIgnoring) null else onOpenBatterySettings,
                trailing = {
                    if (keepAliveIgnoring) {
                        Text(text = "✓", color = NeonViolet)
                    } else {
                        TextButton(onClick = onOpenBatterySettings) { Text("去开启") }
                    }
                }
            )
            if (!keepAliveIgnoring) {
                Text(
                    text = KEEP_ALIVE_VENDOR_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayMuted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                )
            }
        }
        SettingsSectionTitle("关于与退出")
        MineSectionCard {
            SettingsRowShell(
                title = themeRow.title,
                subtitle = themeRow.subtitle,
                trailing = { Text(text = "✓", color = NeonViolet) }
            )
            MineDivider()
            SettingsRowShell(
                title = "登录诊断",
                subtitle = remember(authDiagCode, authDiagTimeMs, positionSaveTimeMs) {
                    formatAuthDiag(authDiagCode, authDiagTimeMs, positionSaveTimeMs)
                },
                showChevron = false
            )
            MineDivider()
            SettingsRowShell(
                title = aboutRow.title,
                subtitle = "${aboutRow.subtitle}\n$SETTINGS_GITHUB_URL\n开源致谢：感谢每一位贡献者"
            )
        }
        Spacer(Modifier.height(4.dp))
        UpdateChannelRow(
            channel = updateChannel,
            onSelect = onSelectChannel
        )
        VersionRow(
            versionLabel = versionLabel,
            checking = checkingUpdate,
            onCheck = onCheckUpdate
        )
        if (user != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("退出登录")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
