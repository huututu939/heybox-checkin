package com.heybox.checkin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.heybox.checkin.ui.theme.Ink
import com.heybox.checkin.ui.theme.dialogSurface

/**
 * 和 App 其他卡片同一套语言的弹窗：圆角玻璃卡、渐变描边、主按钮是墨色实心块。
 * 弹窗在独立窗口里，拿不到页面后景，所以只保留表面色相和高光（见 [dialogSurface]）。
 */
@Composable
internal fun GlassDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 遮罩单独一层，点它才关；卡片自己吃掉点击，别穿透
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 26.dp)
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .dialogSurface()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {}
                    .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun DialogTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Ink.ink,
        lineHeight = 24.sp,
    )
}

@Composable
internal fun DialogBody(
    text: String,
    color: Color = Ink.muted,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        lineHeight = 22.sp,
        modifier = modifier,
    )
}

/**
 * 弹窗底部按钮：右边一个墨色主按钮，左边一个次要文字按钮。
 * [primaryEnabled] 关掉时主按钮变成灰底（下载中之类的状态）。
 */
@Composable
internal fun DialogActions(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = "取消",
    onSecondary: () -> Unit = {},
    danger: Boolean = false,
    primaryEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondary != null) {
            TextButton(
                onClick = onSecondary,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(secondary, style = MaterialTheme.typography.labelLarge, color = Ink.muted)
            }
        }
        Spacer(Modifier.height(0.dp))
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (danger) MaterialTheme.colorScheme.error else Ink.ink,
                contentColor = if (danger) MaterialTheme.colorScheme.onError else Ink.onInk,
                disabledContainerColor = Ink.raised,
                disabledContentColor = Ink.muted,
            ),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.height(42.dp),
        ) {
            Text(primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}
