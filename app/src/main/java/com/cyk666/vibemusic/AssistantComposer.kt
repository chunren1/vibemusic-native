package com.cyk666.vibemusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 输入区（本文件只负责"把话发出去"这一件事）。
 *
 * 空态与建议问题不在这里：它们属于页面的内容展示，跟输入焦点、IME、
 * 发送按钮没有共享状态，放在一起只会让"改输入框样式"时误触空态布局。
 * 见 [AssistantScreen] 里的 [AssistantEmptyState]。
 */
@Composable
internal fun AssistantComposer(
    value: String,
    onValueChange: (String) -> Unit,
    canSend: Boolean,
    onSubmit: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    "和音乐精灵聊聊…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = UiMuted
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(26.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            // 不在这里判 canSend：sending 时按回车要给出「还在回答」的提示，
            // 而不是让按键毫无反应（按钮本身仍是禁用态）。
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = UiSurface,
                unfocusedContainerColor = UiSurface,
                focusedBorderColor = UiViolet.copy(alpha = 0.45f),
                unfocusedBorderColor = UiInk.copy(alpha = 0.08f),
                focusedTextColor = UiInk,
                unfocusedTextColor = UiInk,
                cursorColor = UiCyan
            )
        )
        Spacer(Modifier.width(8.dp))
        SendButton(canSend, onSubmit)
    }
}

/** 发送键：启用时紫→青渐变，禁用时退回面板色（不靠降透明度，避免看着像"半坏"）。 */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val brush = if (enabled) {
        Brush.linearGradient(listOf(UiViolet, UiCyan))
    } else {
        Brush.linearGradient(listOf(UiSurface, UiSurface))
    }
    androidx.compose.foundation.layout.Box(
        Modifier.size(46.dp).clip(CircleShape).background(brush)
            .clickable(enabled = enabled, onClickLabel = "发送", onClick = onClick)
            .semantics { contentDescription = "发送" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "↑",
            color = if (enabled) Color.White else UiMuted,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
