package com.cyk666.vibemusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * 主题选择弹层：三套深色，只换背景与表面。
 *
 * <p>每个选项左侧放一个「背景 + 表面」的双色块，让差异在选中之前就能看出来——
 * 三套深色差别很细，只写名字用户分不清。</p>
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerSheet(
    current: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("选择主题", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "三套均为深色，区别在背景与表面明度；强调色保持一致。",
                style = MaterialTheme.typography.bodySmall,
                color = GrayMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            AppTheme.entries.forEach { theme ->
                ThemeOptionRow(
                    theme = theme,
                    selected = theme == current,
                    onClick = { onSelect(theme) }
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ThemeOptionRow(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemeSwatch(theme)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(theme.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (theme == AppTheme.OBSIDIAN) "默认 · 强调色 #" + hexOf(theme.accent)
                else "强调色 #" + hexOf(theme.accent),
                style = MaterialTheme.typography.bodySmall,
                color = GrayMuted
            )
        }
        if (selected) {
            Text("✓", color = UiAccent, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ThemeSwatch(theme: AppTheme) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(theme.bg)
            .border(2.dp, theme.accent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(theme.accent)
        )
    }
}

private fun hexOf(color: Color): String = String.format("%06X", color.toArgb() and 0xFFFFFF)

/** 设置页里的「主题」行：只负责展示当前主题并打开弹层。 */
@Composable
fun ThemeSettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    SettingsRowShell(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = { Text(text = "✓", color = UiAccent) }
    )
}

/** 弹层的显隐收在这里，设置页只留一行调用。 */
@Composable
fun ThemeSheetHost(
    visible: Boolean,
    current: AppTheme,
    onSelect: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    ThemePickerSheet(current = current, onSelect = onSelect, onDismiss = onDismiss)
}

