package com.cyk666.vibemusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 气泡宽度上限：屏宽 82%，平板封顶 340dp（再宽一行字会长得读不动）。 */
internal fun bubbleMaxWidth(screenWidth: Dp): Dp = (screenWidth * 0.82f).coerceAtMost(340.dp)

/** 空态建议问题：点击即发，顺序即展示顺序。 */
private val ASSISTANT_STARTERS = listOf(
    "推荐点适合现在的歌",
    "我最近在听什么",
    "帮我建一个跑步歌单"
)

/**
 * AI 助手对话页（纯展示层：只收 [AssistantState] 与回调，不自己发请求）。
 *
 * 本文件管"页面"本身：顶栏、消息列表编排、空态。消息气泡与歌曲卡片在
 * [AssistantBubbles]，输入区在 [AssistantComposer]——那两块各有独立的
 * 视觉规则（气泡切角/卡片行高、输入框焦点态），改一处不会牵动另一处。
 *
 * `state.notice` 由 Activity 用 Snackbar 消费，这里不显示、不弹，
 * 免得同一条错误在一屏里出现两次。
 */
@Composable
fun AssistantScreen(
    modifier: Modifier = Modifier,
    state: AssistantState,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    loggedIn: Boolean = false,
    onLogin: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    fun submit() {
        val text = draft.trim()
        if (text.isEmpty()) return
        if (state.sending) {
            // 保留草稿：用户接着要发的是下一句，不该被这一下清掉
            state.notifyBusy()
            return
        }
        draft = ""
        onSend(text)
    }

    // 新消息 / 进入思考态都贴到底：条目数 = messages 数（+1 思考气泡）。
    LaunchedEffect(state.messages.size, state.sending) {
        if (state.messages.isEmpty() && !state.sending) return@LaunchedEffect
        val target = if (state.sending) state.messages.size else state.messages.lastIndex
        if (target >= 0) listState.animateScrollToItem(target)
    }

    // 冷启动拉服务端历史恢复界面（幂等；失败静默）。恢复出的历史不含 songs/actions。
    LaunchedEffect(Unit) { state.restore() }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding()) {
        AssistantHeader(canClear = state.messages.isNotEmpty(), onClear = onClear)

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bubbleMax = bubbleMaxWidth(maxWidth)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.messages.isEmpty() && !state.sending) {
                    item(key = "empty") { AssistantEmptyState(loggedIn, onSend, onLogin) }
                }
                itemsIndexed(state.messages, key = { i, m -> "$i-${m.role}" }) { _, msg ->
                    AssistantMessageRow(msg, bubbleMax, onPlaySongs)
                }
                if (state.sending) item(key = "thinking") { AssistantThinkingRow(bubbleMax) }
            }
        }

        AssistantComposer(draft, { draft = it }, !state.sending && draft.isNotBlank()) { submit() }
    }
}

/**
 * 顶栏：头像 + 名字 + 在线态。
 *
 * 「在线 · 实时搜曲库」不是装饰——它解释了为什么回答里会挂真实可播的歌曲，
 * 比旧版的「AI 生成」徽章更有信息量（后者只提示风险，不说明能力）。
 */
@Composable
private fun AssistantHeader(canClear: Boolean, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantAvatar(34.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "音乐精灵",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = UiInk
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(UiAccentSoft))
                Spacer(Modifier.width(5.dp))
                Text(
                    "在线 · 实时搜曲库",
                    style = MaterialTheme.typography.labelSmall,
                    color = UiMuted
                )
            }
        }
        if (canClear) {
            Text(
                "清空",
                style = MaterialTheme.typography.labelMedium,
                color = UiMuted,
                modifier = Modifier.clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * 空态：品牌徽标 + 一句主张 + 三个建议。
 *
 * 建议做成整行卡片而不是居中小 chip：中文短语长短不一，chip 会排成参差的
 * 瀑布；整行左对齐、带图标与箭头，扫读路径是一条直线。
 */
@Composable
private fun AssistantEmptyState(
    loggedIn: Boolean,
    onSend: (String) -> Unit,
    onLogin: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(118.dp), contentAlignment = Alignment.Center) {
            // 中性深底 + 细金环，和消息头像同一套做法：不用发光光晕（那是 AI 味的一部分）。
            Box(
                Modifier.size(108.dp).clip(CircleShape)
                    .background(UiSurface)
                    .border(1.dp, UiAccent.copy(alpha = 0.28f), CircleShape)
            )
            Image(
                painter = painterResource(R.drawable.ic_whale),
                contentDescription = null,
                modifier = Modifier.size(88.dp)
            )
        }

        Text(
            "想听点什么？",
            style = MaterialTheme.typography.titleLarge,
            color = UiInk, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "说个心情、场景或歌手，我来挑歌",
            style = MaterialTheme.typography.bodySmall,
            color = UiMuted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )

        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            ASSISTANT_STARTERS.forEach { prompt ->
                StarterCard(prompt) { onSend(prompt) }
            }
        }

        if (!loggedIn) {
            Text(
                "现在是游客模式，登录后我能看你的收听历史",
                style = MaterialTheme.typography.labelSmall,
                color = UiMuted, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                "去登录",
                style = MaterialTheme.typography.labelMedium,
                color = UiAccentSoft, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp).heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onLogin)
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun StarterCard(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
            .background(UiSurface)
            .border(1.dp, UiInk.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(UiAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                AppIconKind.MUSIC_NOTE, UiAccent,
                size = 14.dp, contentDescription = null
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = UiInk,
            modifier = Modifier.weight(1f)
        )
        AppIcon(
            AppIconKind.CHEVRON_RIGHT, UiMuted.copy(alpha = 0.65f),
            size = 15.dp, contentDescription = null
        )
    }
}
