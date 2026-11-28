package com.heybox.checkin.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heybox.checkin.data.CheckItem
import com.heybox.checkin.data.runSelfCheck
import com.heybox.checkin.data.selfCheckText
import com.heybox.checkin.ui.theme.Ink
import com.heybox.checkin.ui.theme.NumberSmall
import com.heybox.checkin.ui.theme.glass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember



/* ------------------------------------------------------------------ 通用组件 */

@Composable
internal fun GroupLabel(text: String, first: Boolean = false) {
    Spacer(Modifier.height(if (first) 18.dp else 20.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Ink.muted,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
    )
}

@Composable
internal fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

@Composable
internal fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Ink.onAccent,
    checkedTrackColor = Ink.accent,
    uncheckedThumbColor = Ink.muted,
    uncheckedTrackColor = Ink.raised,
    uncheckedBorderColor = Ink.line,
)

/** 一个开关行：标题 + 一句可省的说明，说明写「关掉会怎样」而不重复标题。 */
@Composable
internal fun ToggleRow(
    title: String,
    note: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink.ink)
            if (note != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.muted,
                    fontSize = 11.5.sp,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = switchColors())
    }
}

/**
 * 一行一个权限/开关：图标同时表达「是哪一项」和「当前状态」，
 * 没就绪时整行可点，就绪了就不再挂一句「已就绪」。
 */
@Composable
internal fun PermissionRow(
    name: String,
    ok: Boolean?,
    glyph: PermGlyph,
    action: String = "去设置",
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = ok != true, onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PermIcon(glyph, ok)
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.ink,
            modifier = Modifier.weight(1f),
        )
        if (ok != true) {
            Text(action, style = MaterialTheme.typography.bodySmall, color = Ink.accent)
        }
    }
}

@Composable
internal fun ChevronRow(text: String, glyph: RowGlyph? = null, onClick: () -> Unit) {
    val muted = Ink.muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                RowIcon(glyph)
                Spacer(Modifier.width(12.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = Ink.ink)
        }
        Canvas(Modifier.size(8.dp, 13.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.12f)
                lineTo(size.width * 0.8f, size.height * 0.5f)
                lineTo(size.width * 0.2f, size.height * 0.88f)
            }
            drawPath(
                path,
                color = muted,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
internal fun SegmentedOption(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Ink.surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Ink.ink else Ink.muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
internal fun HourStepper(
    label: String,
    hour: Int,
    canDec: Boolean,
    canInc: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = modifier
            .background(Ink.raised, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepButton("−", canDec) { onChange(hour - 1) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hourLabel(hour), style = NumberSmall, color = Ink.ink, fontSize = 14.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.muted, fontSize = 9.sp)
        }
        StepButton("+", canInc) { onChange(hour + 1) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) Ink.surface else Ink.raised)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = NumberSmall,
            color = if (enabled) Ink.ink else Ink.muted.copy(alpha = 0.5f),
        )
    }
}

/* ------------------------------------------------------------------ 图标 */

internal enum class PermGlyph { Bell, Battery, Alarm, Power }

internal enum class RowGlyph { Info, Export, Import, Table, Check, Exit }

@Composable
private fun RowIcon(glyph: RowGlyph) {
    val color = Ink.muted
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (glyph) {
            RowGlyph.Info -> {
                drawCircle(color, radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                drawCircle(color, radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.36f))
                drawLine(color, Offset(w * 0.5f, h * 0.48f), Offset(w * 0.5f, h * 0.66f), 1.5.dp.toPx(), StrokeCap.Round)
            }

            RowGlyph.Export -> {
                drawLine(color, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.26f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.4f), Offset(w * 0.5f, h * 0.26f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.64f, h * 0.4f), Offset(w * 0.5f, h * 0.26f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.24f, h * 0.82f), Offset(w * 0.76f, h * 0.82f), 1.5.dp.toPx(), StrokeCap.Round)
            }

            RowGlyph.Import -> {
                drawLine(color, Offset(w * 0.5f, h * 0.26f), Offset(w * 0.5f, h * 0.72f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.58f), Offset(w * 0.5f, h * 0.72f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.64f, h * 0.58f), Offset(w * 0.5f, h * 0.72f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.24f, h * 0.86f), Offset(w * 0.76f, h * 0.86f), 1.5.dp.toPx(), StrokeCap.Round)
            }

            RowGlyph.Table -> {
                // 表格：外框 + 两条横线，和「导出配置」的箭头区分开
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.18f, h * 0.24f),
                    size = Size(w * 0.64f, h * 0.52f),
                    cornerRadius = CornerRadius(w * 0.06f),
                    style = stroke,
                )
                drawLine(color, Offset(w * 0.18f, h * 0.42f), Offset(w * 0.82f, h * 0.42f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.18f, h * 0.58f), Offset(w * 0.82f, h * 0.58f), 1.5.dp.toPx(), StrokeCap.Round)
            }

            RowGlyph.Check -> {
                drawLine(color, Offset(w * 0.22f, h * 0.52f), Offset(w * 0.42f, h * 0.7f), 1.8.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.42f, h * 0.7f), Offset(w * 0.78f, h * 0.3f), 1.8.dp.toPx(), StrokeCap.Round)
            }

            RowGlyph.Exit -> {
                // 门 + 向外的箭头，和「自启动」的电源符号区分开
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.16f, h * 0.22f),
                    size = Size(w * 0.36f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.06f),
                    style = stroke,
                )
                drawLine(color, Offset(w * 0.46f, h * 0.5f), Offset(w * 0.84f, h * 0.5f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.72f, h * 0.36f), Offset(w * 0.86f, h * 0.5f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.72f, h * 0.64f), Offset(w * 0.86f, h * 0.5f), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun PermIcon(glyph: PermGlyph, ok: Boolean?) {
    val color = when (ok) {
        true -> Ink.success
        false -> Ink.accent
        null -> Ink.muted
    }
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (glyph) {
            PermGlyph.Bell -> {
                val bell = Path().apply {
                    moveTo(w * 0.2f, h * 0.68f)
                    cubicTo(w * 0.3f, h * 0.6f, w * 0.3f, h * 0.5f, w * 0.3f, h * 0.4f)
                    cubicTo(w * 0.3f, h * 0.2f, w * 0.7f, h * 0.2f, w * 0.7f, h * 0.4f)
                    cubicTo(w * 0.7f, h * 0.5f, w * 0.7f, h * 0.6f, w * 0.8f, h * 0.68f)
                    close()
                }
                drawPath(bell, color, style = stroke)
                drawCircle(color, radius = w * 0.055f, center = Offset(w * 0.5f, h * 0.8f))
            }

            PermGlyph.Battery -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.12f, h * 0.3f),
                    size = Size(w * 0.66f, h * 0.4f),
                    cornerRadius = CornerRadius(w * 0.09f),
                    style = stroke,
                )
                drawLine(color, Offset(w * 0.84f, h * 0.42f), Offset(w * 0.84f, h * 0.58f), 2.dp.toPx(), StrokeCap.Round)
                val bolt = Path().apply {
                    moveTo(w * 0.5f, h * 0.34f)
                    lineTo(w * 0.4f, h * 0.5f)
                    lineTo(w * 0.5f, h * 0.5f)
                    lineTo(w * 0.44f, h * 0.66f)
                }
                drawPath(bolt, color, style = stroke)
            }

            PermGlyph.Alarm -> {
                drawCircle(color, radius = w * 0.31f, center = Offset(w * 0.5f, h * 0.55f), style = stroke)
                drawLine(color, Offset(w * 0.5f, h * 0.4f), Offset(w * 0.5f, h * 0.55f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.55f), Offset(w * 0.6f, h * 0.61f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.28f, h * 0.16f), Offset(w * 0.37f, h * 0.25f), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(w * 0.72f, h * 0.16f), Offset(w * 0.63f, h * 0.25f), 1.5.dp.toPx(), StrokeCap.Round)
            }

            PermGlyph.Power -> {
                drawArc(
                    color = color,
                    startAngle = -60f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.24f),
                    size = Size(w * 0.64f, h * 0.64f),
                    style = stroke,
                )
                drawLine(color, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.46f), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

/* ------------------------------------------------------------------ 对话框 */

/** 设置页的「运行自检」：把「某天没跑」可能的原因一次查完，结果可复制给维护者。 */
@Composable
internal fun SelfCheckDialog(context: Context, onClose: () -> Unit) {
    var items by remember { mutableStateOf<List<CheckItem>?>(null) }
    LaunchedEffect(Unit) { items = runSelfCheck(context) }

    GlassDialog(onDismissRequest = onClose) {
        DialogTitle("运行自检")
        Spacer(Modifier.height(10.dp))
        val current = items
        if (current == null) {
            DialogBody("正在检查权限、闹钟与网络…")
        } else {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                current.forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            when {
                                !item.verifiable -> "!"
                                item.ok -> "✓"
                                else -> "✕"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                !item.verifiable -> Ink.accent
                                item.ok -> Ink.success
                                else -> Ink.error
                            },
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(22.dp),
                        )
                        Column {
                            Text(item.label, style = MaterialTheme.typography.bodyMedium, color = Ink.ink)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = Ink.muted,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }
        DialogActions(
            primary = "复制结果",
            onPrimary = {
                items?.let { copyText(context, "盒签自检", selfCheckText(it)) }
                onClose()
            },
            secondary = "关闭",
            onSecondary = onClose,
            primaryEnabled = items != null,
        )
    }
}

/** 首次进首页时的权限提醒：缺哪项、为什么缺了会出问题。 */
@Composable
internal fun PermissionHelpDialog(
    permissions: Permissions,
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val missing = buildList {
        if (!permissions.notify) add("通知权限：签到结果发不出通知")
        if (!permissions.battery) add("忽略电池优化：后台容易被系统清掉")
        if (!permissions.exactAlarm) add("精确闹钟：定时会漂到不准的点")
    }
    // 用户在设置里补齐后（回到 App 会重新读权限），这个提示自己消失
    if (missing.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    GlassDialog(onDismissRequest = onDismiss) {
        DialogTitle("还有 ${missing.size} 项没开")
        Spacer(Modifier.height(10.dp))
        DialogBody(
            "缺这些的话，夜里定时容易被系统掐掉，签到会漏。\n\n" +
                missing.joinToString("\n") { "· $it" },
        )
        Spacer(Modifier.height(8.dp))
        DialogBody("自启动要按机型手动开，设置页里有各家入口。")
        DialogActions(primary = "去设置", onPrimary = onGoSettings, secondary = "稍后", onSecondary = onDismiss)
    }
}
