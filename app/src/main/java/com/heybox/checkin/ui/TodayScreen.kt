package com.heybox.checkin.ui

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.heybox.checkin.core.HeyboxConfig
import com.heybox.checkin.BuildConfig
import com.heybox.checkin.data.FailureKind
import com.heybox.checkin.data.CheckItem
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.SettingsExport
import com.heybox.checkin.data.SharedSettings
import com.heybox.checkin.data.UpdateCheck
import com.heybox.checkin.data.UpdateInfo
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.data.missStreak
import com.heybox.checkin.data.runSelfCheck
import com.heybox.checkin.data.selfCheckText
import com.heybox.checkin.ui.theme.Ink
import com.heybox.checkin.ui.theme.NumberSmall
import com.heybox.checkin.ui.theme.NumberStyle
import com.heybox.checkin.ui.theme.NumberTiny
import com.heybox.checkin.ui.theme.Glass
import com.heybox.checkin.ui.theme.glass
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.heybox.checkin.ui.theme.LocalBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.heybox.checkin.work.CheckInService
import com.heybox.checkin.work.RunLock
import com.heybox.checkin.work.RunState
import com.heybox.checkin.work.Scheduler
import com.heybox.checkin.widget.Widgets
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** 首页状态。用枚举而不是中文串：文案和判断色只在这里改，不会散到三个文件。 */
internal enum class TodayStatus(val label: String) {
    RUNNING("执行中"),
    INCOMPLETE("未完成"),
    DONE("今日已完成"),
    SIGNED("今日已签到"),
    FAILED("今日失败"),
    PENDING("今日待执行"),
}

@Composable
internal fun TodayTab(
    config: HeyboxConfig,
    status: TodayStatus,
    running: Boolean,
    expired: Boolean,
    scheduled: Boolean,
    nextAt: Long,
    startHour: Int,
    endHour: Int,
    today: RunRecord?,
    cells: List<WeekCell>,
    enabledShares: Set<String>,
    error: String?,
    launching: Boolean,
    stage: String,
    missed: Int,
    lastReason: String?,
    needsRelogin: Boolean,
    scroll: androidx.compose.foundation.ScrollState,
    onRelogin: () -> Unit,
    onRun: () -> Unit,
    onShowRecord: () -> Unit,
    onToggleShare: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = TAB_BOTTOM),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    "盒签",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Ink.ink,
                    fontSize = 22.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "heybox_id ${config.session.heyboxId}",
                    style = NumberTiny,
                    color = Ink.muted,
                )
            }
        }

        if (expired) {
            Spacer(Modifier.height(14.dp))
            ExpiredBanner(onRelogin)
        }
        // 登录过期时上面的横幅已经说明原因了，不再叠一条
        if (!expired && missed >= 2) {
            Spacer(Modifier.height(14.dp))
            MissBanner(
                days = missed,
                reason = lastReason,
                action = if (needsRelogin) "重新登录" else "看记录",
                onClick = if (needsRelogin) onRelogin else onShowRecord,
            )
        }

        Spacer(Modifier.height(14.dp))
        StatusRow(status = status, running = running)

        Spacer(Modifier.height(16.dp))
        Text("今日到账", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        Spacer(Modifier.height(6.dp))
        Figures(today)

        Spacer(Modifier.height(8.dp))
        Text(balanceText(today), style = NumberTiny, color = Ink.muted)

        Spacer(Modifier.height(14.dp))
        WindowBlock(
            scheduled = scheduled,
            nextAt = nextAt,
            startHour = startHour,
            endHour = endHour,
        )

        Spacer(Modifier.height(16.dp))
        SectionHead(title = "每日四件")
        Spacer(Modifier.height(10.dp))
        TaskTiles(
            today = today,
            enabledShares = enabledShares,
            onToggleShare = onToggleShare,
        )

        Spacer(Modifier.height(16.dp))
        SectionHead(
            title = "最近七天",
            hint = "全部记录",
            onHint = onShowRecord,
        )
        Spacer(Modifier.height(10.dp))
        WeekRow(cells)

        error?.let {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            // 登录过期时先修凭据：直接跑只会再失败一次。
            onClick = if (expired) onRelogin else onRun,
            enabled = !running && !launching,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Ink.ink,
                contentColor = Ink.onInk,
                disabledContainerColor = Ink.raised,
                disabledContentColor = Ink.muted,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                when {
                    expired -> "重新登录"
                    running -> stage.ifEmpty { "执行中" }
                    else -> "立即执行一次"
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
        }
        if (running) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                color = Ink.accent,
                trackColor = Ink.line,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}

@Composable
private fun StatusRow(status: TodayStatus, running: Boolean) {
    val color = when (status) {
        TodayStatus.DONE, TodayStatus.SIGNED -> Ink.success
        TodayStatus.INCOMPLETE, TodayStatus.FAILED -> MaterialTheme.colorScheme.error
        TodayStatus.RUNNING -> Ink.accent
        TodayStatus.PENDING -> Ink.muted
    }
    val alpha = if (running) {
        val transition = rememberInfiniteTransition(label = "dot")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "dotAlpha",
        ).value
    } else {
        1f
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha)),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            status.label,
            style = MaterialTheme.typography.headlineMedium,
            color = Ink.ink,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            formatDay(System.currentTimeMillis()),
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Figures(today: RunRecord?) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Figure("经验", deltaText(today, today?.expDelta), Modifier.weight(1f), first = true)
        Figure("H 币", deltaText(today, today?.coinDelta), Modifier.weight(1f), first = false)
        Figure("电池", deltaText(today, today?.batteryDelta), Modifier.weight(1f), first = false)
    }
}

@Composable
private fun RowScope.Figure(name: String, value: String, modifier: Modifier, first: Boolean) {
    Row(modifier = modifier) {
        if (!first) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(38.dp)
                    .background(Ink.line),
            )
        }
        Column(modifier = Modifier.padding(start = if (first) 0.dp else 14.dp)) {
            Text(value, style = NumberStyle, color = Ink.ink)
            Spacer(Modifier.height(3.dp))
            Text(name, style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        }
    }
}

@Composable
private fun WindowBlock(scheduled: Boolean, nextAt: Long, startHour: Int, endHour: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text("下次执行", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        Text(
            when {
                !scheduled -> "定时未开启"
                nextAt > 0L -> formatTime(nextAt)
                else -> "尚未排定"
            },
            style = NumberSmall,
            color = if (scheduled && nextAt > 0L) Ink.ink else Ink.muted,
            fontSize = 15.sp,
        )
    }
    Spacer(Modifier.height(10.dp))
    val fraction = trackFraction(nextAt, scheduled, startHour, endHour)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Ink.accent.copy(alpha = 0.18f)),
        )
        if (fraction != null) {
            val x = maxWidth * fraction
            Box(
                Modifier
                    .offset(x = x - 5.5.dp, y = (-2.5).dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Ink.accent),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(hourLabel(startHour), style = NumberTiny, color = Ink.muted, fontSize = 10.sp)
        Text(hourLabel(endHour), style = NumberTiny, color = Ink.muted, fontSize = 10.sp)
    }
}

internal fun previewNextRun(): Long = java.util.Calendar.getInstance().apply {
    add(java.util.Calendar.DAY_OF_YEAR, 1)
    set(java.util.Calendar.HOUR_OF_DAY, 17)
    set(java.util.Calendar.MINUTE, 14)
    set(java.util.Calendar.SECOND, 0)
    set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

private fun trackFraction(nextAt: Long, scheduled: Boolean, startHour: Int, endHour: Int): Float? {
    if (!scheduled || nextAt <= 0L) return null
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = nextAt }
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY) + calendar.get(java.util.Calendar.MINUTE) / 60f
    val span = (endHour - startHour).toFloat()
    if (span <= 0f) return null
    return ((hour - startHour) / span).coerceIn(0.02f, 0.98f)
}

@Composable
private fun SectionHead(title: String, hint: String? = null, onHint: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        if (hint != null) {
            Text(
                hint,
                style = if (onHint != null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                color = if (onHint != null) Ink.accent else Ink.muted,
                fontSize = if (onHint != null) 12.sp else 11.sp,
                modifier = if (onHint != null) Modifier.clickable(onClick = onHint) else Modifier,
            )
        }
    }
}

@Composable
private fun TaskTiles(today: RunRecord?, enabledShares: Set<String>, onToggleShare: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SignTile(state = taskState(today, "sign", today?.signed == true), modifier = Modifier.weight(1f).fillMaxHeight())
            ShareTile(
                name = "分享帖子",
                state = taskState(today, SHARE_POST, false),
                enabled = SHARE_POST in enabledShares,
                onToggle = { onToggleShare(SHARE_POST) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShareTile(
                name = "分享游戏",
                state = taskState(today, SHARE_GAME, false),
                enabled = SHARE_GAME in enabledShares,
                onToggle = { onToggleShare(SHARE_GAME) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ShareTile(
                name = "分享游戏评价",
                state = taskState(today, SHARE_REVIEW, false),
                enabled = SHARE_REVIEW in enabledShares,
                onToggle = { onToggleShare(SHARE_REVIEW) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun Tile(name: String, state: String, modifier: Modifier, trailing: @Composable () -> Unit) {
    Column(
        modifier = modifier
            // 用最小高度而不是固定高度：系统字体放大时卡片跟着长高，别把状态行裁掉
            .heightIn(min = 80.dp)
            .glass(RoundedCornerShape(20.dp), raised = false)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = Ink.ink,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state,
                style = MaterialTheme.typography.bodySmall,
                color = if (state == "已完成") Ink.success else Ink.muted,
                fontSize = 11.5.sp,
                maxLines = 1,
            )
            trailing()
        }
    }
}

@Composable
private fun SignTile(state: String, modifier: Modifier) {
    Tile(name = "签到", state = state, modifier = modifier) {
        if (state == "已完成") CheckBadge()
    }
}

@Composable
private fun ShareTile(name: String, state: String, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    Tile(name = name, state = state, modifier = modifier) {
        SmallSwitch(checked = enabled, onToggle = onToggle)
    }
}

@Composable
private fun CheckBadge() {
    val success = Ink.success
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(success.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(11.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.15f, size.height * 0.56f)
                lineTo(size.width * 0.38f, size.height * 0.8f)
                lineTo(size.width * 0.85f, size.height * 0.24f)
            }
            drawPath(
                path,
                color = success,
                style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
private fun SmallSwitch(checked: Boolean, onToggle: () -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = { onToggle() },
        colors = switchColors(),
        modifier = Modifier.size(width = 40.dp, height = 24.dp),
    )
}

@Composable
private fun WeekRow(cells: List<WeekCell>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        cells.forEach { cell -> DayCell(cell) }
    }
}

@Composable
private fun DayCell(cell: WeekCell) {
    val accent = when (cell.state) {
        DayState.DONE -> Ink.success
        DayState.FAILED -> Ink.error
        DayState.SKIPPED, DayState.NONE -> Ink.muted
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(38.dp)) {
        Text(cell.weekday, style = MaterialTheme.typography.labelSmall, color = Ink.muted, fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (cell.today) Ink.success.copy(alpha = 0.14f)
                    else Ink.surface.copy(alpha = 0.5f),
                )
                .border(1.dp, if (cell.today) Ink.success.copy(alpha = 0.14f) else Ink.line, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (cell.state) {
                DayState.DONE -> MiniCheck(accent)
                DayState.SKIPPED, DayState.FAILED, DayState.NONE ->
                    Text("–", style = NumberTiny, color = accent.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(cell.monthDay, style = NumberTiny, color = Ink.muted, fontSize = 10.sp)
    }
}

@Composable
private fun MiniCheck(color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(12.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.53f)
            lineTo(size.width * 0.42f, size.height * 0.8f)
            lineTo(size.width * 0.86f, size.height * 0.22f)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/* ------------------------------------------------------------------ 设置 */

@Composable
private fun ExpiredBanner(onRelogin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.error.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("登录已过期", style = MaterialTheme.typography.bodyMedium, color = Ink.error, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(
                "自动签到会一直失败，重新登录即可恢复",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.muted,
                fontSize = 11.5.sp,
            )
        }
        Text(
            "重新登录",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.accent,
            modifier = Modifier
                .clickable(onClick = onRelogin)
                .padding(start = 12.dp),
        )
    }
}

/**
 * 连续没签上时的提示条。
 *
 * 存在的理由不是「多一条提示」，而是这类失败最常见的成因（闹钟没响、后台被冻）
 * 根本不会留下失败记录——不主动说，用户只会以为一直在正常签到。
 */
@Composable
private fun MissBanner(days: Int, reason: String?, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.error.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 一行说清两件事：几天没签上、最近一次为什么；原因太长就省略，不换行把页面撑高
        Text(
            "连续 $days 天没签上",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.error,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            if (reason != null) " · $reason" else "",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            action,
            style = MaterialTheme.typography.bodySmall,
            color = Ink.accent,
            maxLines = 1,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

private fun taskState(record: RunRecord?, id: String, signed: Boolean): String {
    if (record == null) return "—"
    if (signed || record.taskStates[id] == "finish") return "已完成"
    return when (record.taskStates[id]) {
        "waiting" -> "待执行"
        null -> "—"
        else -> record.taskStates[id] ?: "—"
    }
}

private fun deltaText(record: RunRecord?, value: Long?): String {
    if (record == null || !record.executed || value == null) return "—"
    return if (value >= 0) "+$value" else value.toString()
}

private fun balanceText(record: RunRecord?): String {
    if (record == null) return "余额 H 币 — · 电池 — · 经验 —"
    return buildString {
        append("余额 H 币 ${record.coin}")
        append(" · 电池 ${record.battery?.toString() ?: "—"}")
        val level = record.level
        if (level != null) {
            append(" · Lv $level ${record.exp}")
            record.maxExp?.let { append("/$it") }
        } else {
            append(" · 经验 ${record.exp}")
        }
    }
}

/* -------------------------------------------------------------- 系统侧 */

