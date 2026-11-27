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

@Composable
internal fun SettingsTab(
    permissions: Permissions,
    scheduled: Boolean,
    nextAt: Long,
    startHour: Int,
    endHour: Int,
    hideRecent: Boolean,
    appearance: String,
    error: String?,
    scroll: androidx.compose.foundation.ScrollState,
    onToggleScheduled: (Boolean) -> Unit,
    onWindow: (Int, Int) -> Unit,
    onToggleHideRecent: (Boolean) -> Unit,
    onFix: (String) -> Unit,
    onAutostart: () -> Unit,
    onAppearanceChange: (String) -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
    store: SessionStore,
    onImport: (SharedSettings) -> Unit,
) {
    val context = LocalContext.current
    // 退出会清掉登录态、历史和定时，先问一句，免得误触
    var askLogout by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    var selfCheckOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 150.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, color = Ink.ink)

        GroupLabel("自动", first = true)
        Card {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("每天自动签到", style = MaterialTheme.typography.titleMedium, color = Ink.ink)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when {
                            scheduled && nextAt > 0L -> "下次 ${formatTime(nextAt)} · 每天重新随机"
                            scheduled -> "已开启，但系统暂未允许排定 · 检查精确闹钟权限"
                            else -> "关闭中 · 不会自动执行"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.muted,
                        fontSize = 11.5.sp,
                    )
                }
                Switch(
                    checked = scheduled,
                    onCheckedChange = onToggleScheduled,
                    colors = switchColors(),
                )
            }
            HorizontalDivider(color = Ink.line, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            Text("执行时段", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HourStepper(
                    label = "开始",
                    hour = startHour,
                    canDec = startHour > 0,
                    canInc = startHour + 2 <= endHour,
                    modifier = Modifier.weight(1f),
                    onChange = { onWindow(it, endHour) },
                )
                HourStepper(
                    label = "结束",
                    hour = endHour,
                    canDec = endHour - 2 >= startHour,
                    canInc = endHour < 24,
                    modifier = Modifier.weight(1f),
                    onChange = { onWindow(startHour, it) },
                )
            }
        }

        GroupLabel("值守")
        Card {
            PermissionRow("通知权限", permissions.notify, PermGlyph.Bell, onClick = { onFix("notify") })
            PermissionRow("忽略电池优化", permissions.battery, PermGlyph.Battery, onClick = { onFix("battery") })
            PermissionRow("精确闹钟", permissions.exactAlarm, PermGlyph.Alarm, onClick = { onFix("alarm") })
            PermissionRow("自启动 / 后台运行", null, PermGlyph.Power, onClick = onAutostart)
            HorizontalDivider(color = Ink.line, thickness = 1.dp)
            ChevronRow("运行自检", RowGlyph.Check, onClick = { selfCheckOpen = true })
        }

        Spacer(Modifier.height(10.dp))
        Card {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "最近任务隐藏",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink.ink,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = hideRecent,
                    onCheckedChange = onToggleHideRecent,
                    colors = switchColors(),
                )
            }
        }

        GroupLabel("显示")
        Card {
            Text("外观", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Ink.raised, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SegmentedOption("跟随系统", appearance == "auto", Modifier.weight(1f)) { onAppearanceChange("auto") }
                SegmentedOption("白天", appearance == "light", Modifier.weight(1f)) { onAppearanceChange("light") }
                SegmentedOption("夜间", appearance == "dark", Modifier.weight(1f)) { onAppearanceChange("dark") }
            }
        }

        GroupLabel("其他")
        Card {
            ChevronRow("关于盒签", RowGlyph.Info, onClick = onAbout)
            HorizontalDivider(color = Ink.line, thickness = 1.dp)
            ChevronRow("导出签到记录", RowGlyph.Table, onClick = {
                shareHistoryCsv(context, store.history())
            })
            HorizontalDivider(color = Ink.line, thickness = 1.dp)
            ChevronRow("导出配置", RowGlyph.Export, onClick = {
                exportText = SettingsExport.encode(SettingsExport.read(store))
            })
            HorizontalDivider(color = Ink.line, thickness = 1.dp)
            ChevronRow("导入配置", RowGlyph.Import, onClick = { importOpen = true })
            HorizontalDivider(color = Ink.line, thickness = 1.dp)
            ChevronRow("退出登录", RowGlyph.Exit, onClick = { askLogout = true })
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

    }

    if (askLogout) {
        GlassDialog(onDismissRequest = { askLogout = false }) {
            DialogTitle("退出登录？")
            Spacer(Modifier.height(10.dp))
            DialogBody("会清掉登录态、签到历史和定时设置，自动签到也会停下；重新登录后要再设一次。")
            DialogActions(
                primary = "退出",
                onPrimary = {
                    askLogout = false
                    onLogout()
                },
                onSecondary = { askLogout = false },
                danger = true,
            )
        }
    }

    exportText?.let { text ->
        GlassDialog(onDismissRequest = { exportText = null }) {
            DialogTitle("导出配置")
            Spacer(Modifier.height(8.dp))
            DialogBody("复制这段文字，换台机器后在「导入配置」里粘贴。只含定时、分享开关这些偏好，不含账号和密钥。")
            Spacer(Modifier.height(10.dp))
            SelectionContainer {
                Text(text, style = NumberTiny, color = Ink.ink, fontSize = 11.sp, lineHeight = 17.sp)
            }
            DialogActions(
                primary = "复制",
                onPrimary = {
                    copyText(context, "盒签配置", text)
                    exportText = null
                },
                secondary = "关闭",
                onSecondary = { exportText = null },
            )
        }
    }

    if (importOpen) {
        val decoded = remember { SettingsExport.decode(readClipText(context).orEmpty()) }
        GlassDialog(onDismissRequest = { importOpen = false }) {
            DialogTitle("导入配置")
            Spacer(Modifier.height(8.dp))
            if (decoded == null) {
                DialogBody("剪贴板里没有盒签配置。先在旧机器的「导出配置」里复制一段，再回来。")
                DialogActions(
                    primary = "知道了",
                    onPrimary = { importOpen = false },
                    secondary = null,
                )
            } else {
                DialogBody(
                    "将导入：\n" +
                        "每天自动签到 ${if (decoded.scheduled) "开启" else "关闭"}\n" +
                        "执行时段 ${"%02d:00".format(decoded.startHour)} – ${"%02d:00".format(decoded.endHour)}\n" +
                        "分享任务 ${decoded.shares.size} 项\n" +
                        "最近任务隐藏 ${if (decoded.hideFromRecents) "开启" else "关闭"}\n" +
                        "外观 ${when (decoded.appearance) { "light" -> "白天"; "dark" -> "夜间"; else -> "跟随系统" }}",
                )
                DialogActions(
                    primary = "导入",
                    onPrimary = {
                        onImport(decoded)
                        importOpen = false
                    },
                    secondary = "取消",
                    onSecondary = { importOpen = false },
                )
            }
        }
    }

    if (selfCheckOpen) {
        SelfCheckDialog(context, onClose = { selfCheckOpen = false })
    }
}

/** 设置页的「运行自检」：把「某天没跑」可能的原因一次查完，结果可复制给维护者。 */
@Composable
private fun SelfCheckDialog(context: Context, onClose: () -> Unit) {
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
                            if (item.ok) "✓" else "✕",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.ok) Ink.success else Ink.error,
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

@Composable
private fun GroupLabel(text: String, first: Boolean = false) {
    Spacer(Modifier.height(if (first) 18.dp else 20.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Ink.muted,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

/**
 * 一行一个权限/开关：图标同时表达「是哪一项」和「当前状态」，
 * 没就绪时整行可点，就绪了就不再挂一句「已就绪」。
 */
@Composable
private fun PermissionRow(
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

private enum class PermGlyph { Bell, Battery, Alarm, Power }

private enum class RowGlyph { Info, Export, Import, Table, Check, Exit }

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

@Composable
private fun ChevronRow(text: String, glyph: RowGlyph? = null, onClick: () -> Unit) {
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
private fun SegmentedOption(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Ink.surface else androidx.compose.ui.graphics.Color.Transparent)
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
private fun HourStepper(
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

/* ---------------------------------------------------------------- 组件 */

