package com.cyk666.vibemusic

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 气泡切角暗示对话方向：用户右对齐切右下，助手左对齐切左下。
private val BubbleShapeUser = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
private val BubbleShapeAi = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

/** 气泡宽度上限：屏宽 82%，平板封顶 340dp（再宽一行字会长得读不动）。 */
private fun bubbleMaxWidth(screenWidth: Dp): Dp = (screenWidth * 0.82f).coerceAtMost(340.dp)

/** 空态建议问题：点击即发，顺序即展示顺序。 */
private val ASSISTANT_STARTERS = listOf(
    "推荐点适合现在的歌",
    "我最近在听什么",
    "帮我建一个跑步歌单"
)

/** 气泡里最多铺几首；多出的收成一行"还有 N 首"，不把对话拉成歌单页。 */
private const val MAX_SONG_ROWS = 5

/**
 * AI 助手对话页（纯展示层：只收 [AssistantState] 与回调，不自己发请求）。
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
        if (text.isEmpty() || state.sending) return
        draft = ""
        onSend(text)
    }

    // 新消息 / 进入思考态都贴到底：条目数 = messages 数（+1 思考气泡）。
    LaunchedEffect(state.messages.size, state.sending) {
        if (state.messages.isEmpty() && !state.sending) return@LaunchedEffect
        val target = if (state.sending) state.messages.size else state.messages.lastIndex
        if (target >= 0) listState.animateScrollToItem(target)
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).imePadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "助手", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = UiInk
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "AI 生成", style = MaterialTheme.typography.labelSmall, color = UiChampagne,
                modifier = Modifier.clip(RoundedCornerShape(22.dp))
                    .background(UiChampagne.copy(alpha = 0.10f))
                    .border(1.dp, UiChampagne.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(Modifier.weight(1f))
            if (state.messages.isNotEmpty()) {
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("清空", style = MaterialTheme.typography.bodySmall, color = UiMuted)
                }
            }
        }
        Text(
            "告诉我心情或场景，我来点歌",
            style = MaterialTheme.typography.bodySmall, color = UiMuted
        )

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val bubbleMax = bubbleMaxWidth(maxWidth)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.messages.isEmpty() && !state.sending) {
                    item(key = "empty") { AssistantIntro(loggedIn, onSend, onLogin) }
                }
                itemsIndexed(state.messages, key = { i, m -> "$i-${m.role}" }) { _, msg ->
                    MessageBubble(msg, bubbleMax, onPlaySongs)
                }
                if (state.sending) item(key = "thinking") { ThinkingBubble(bubbleMax) }
            }
        }

        InputBar(draft, { draft = it }, !state.sending && draft.isNotBlank()) { submit() }
    }
}

// ---- 消息 ----

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    maxBubble: Dp,
    onPlaySongs: (List<Song>, Int) -> Unit
) {
    if (msg.role == ChatRole.USER) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                msg.text, style = MaterialTheme.typography.bodyMedium, color = UiInk,
                modifier = Modifier.widthIn(max = maxBubble).clip(BubbleShapeUser)
                    .background(UiViolet).padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            msg.text, style = MaterialTheme.typography.bodyMedium,
            // 失败消息只降饱和、不刷红：整页暗色，红字像报警而不是"没连上"。
            color = if (msg.failed) UiMuted else UiInk,
            modifier = Modifier.widthIn(max = maxBubble).clip(BubbleShapeAi)
                .background(UiSurface).padding(horizontal = 14.dp, vertical = 10.dp)
        )
        if (msg.songs.isNotEmpty()) SongResultCard(msg.songs, maxBubble, onPlaySongs)
    }
}

@Composable
private fun SongResultCard(
    songs: List<Song>,
    maxBubble: Dp,
    onPlaySongs: (List<Song>, Int) -> Unit
) {
    val shown = songs.take(MAX_SONG_ROWS)
    Column(
        Modifier.padding(top = 6.dp).widthIn(max = maxBubble).clip(RoundedCornerShape(12.dp))
            .background(UiSurface.copy(alpha = 0.72f))
            .border(1.dp, UiInk.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        shown.forEachIndexed { index, song ->
            SongRow(
                buildSongRowModel(song), meta = null,
                onClick = { onPlaySongs(songs, index) }, onOverflow = {}
            )
        }
        if (songs.size > shown.size) {
            Text(
                "还有 ${songs.size - shown.size} 首", style = MaterialTheme.typography.bodySmall,
                color = UiMuted, modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun ThinkingBubble(maxBubble: Dp) {
    Row(
        Modifier.widthIn(max = maxBubble).clip(BubbleShapeAi).background(UiSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("正在想", style = MaterialTheme.typography.bodyMedium, color = UiMuted)
        Spacer(Modifier.width(6.dp))
        val phase by rememberInfiniteTransition(label = "thinking").animateFloat(
            0f, 3f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1050, easing = LinearEasing)),
            label = "thinkingDots"
        )
        val active = phase.toInt() % 3
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(if (i == active) UiCyan else UiMuted.copy(alpha = 0.35f))
                )
            }
        }
    }
}

// ---- 空态 ----

@Composable
private fun AssistantIntro(
    loggedIn: Boolean,
    onSend: (String) -> Unit,
    onLogin: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(UiViolet, UiCyan))),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(AppIconKind.MESSAGE, UiInk, size = 22.dp, contentDescription = null)
        }
        Text(
            "我是 vibe 音乐精灵，想听点什么？", style = MaterialTheme.typography.titleMedium,
            color = UiInk, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            "说个心情、场景或歌手，我来挑歌", style = MaterialTheme.typography.bodySmall,
            color = UiMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ASSISTANT_STARTERS.forEach { prompt -> StarterPill(prompt) { onSend(prompt) } }
        }
        if (!loggedIn) {
            Text(
                "现在是游客模式", style = MaterialTheme.typography.bodySmall,
                color = UiMuted, modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                "登录后我能看你的收听历史", style = MaterialTheme.typography.bodySmall,
                color = UiCyan, textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onLogin).padding(horizontal = 12.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun StarterPill(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
            .background(UiSurface)
            .border(1.dp, UiInk.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = UiInk,
            modifier = Modifier.weight(1f)
        )
        AppIcon(AppIconKind.CHEVRON_RIGHT, UiMuted, size = 16.dp, contentDescription = null)
    }
}

// ---- 输入 ----

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    canSend: Boolean,
    onSubmit: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    "和音乐精灵聊聊…", style = MaterialTheme.typography.bodyMedium, color = UiMuted
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(28.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = UiSurface, unfocusedContainerColor = UiSurface,
                focusedBorderColor = UiViolet.copy(alpha = 0.55f),
                unfocusedBorderColor = UiInk.copy(alpha = 0.10f),
                focusedTextColor = UiInk, unfocusedTextColor = UiInk, cursorColor = UiCyan
            )
        )
        Spacer(Modifier.width(8.dp))
        SendButton(canSend, onSubmit)
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .background(if (enabled) UiViolet else UiSurface)
            .clickable(enabled = enabled, onClickLabel = "发送", onClick = onClick)
            .semantics { contentDescription = "发送" },
        contentAlignment = Alignment.Center
    ) {
        AppIcon(
            AppIconKind.MESSAGE, if (enabled) UiInk else UiMuted,
            size = 20.dp, contentDescription = null
        )
    }
}
