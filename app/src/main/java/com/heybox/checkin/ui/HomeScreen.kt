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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.heybox.checkin.core.HeyboxConfig
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.ui.theme.Ink
import com.heybox.checkin.ui.theme.NumberSmall
import com.heybox.checkin.ui.theme.NumberStyle
import com.heybox.checkin.ui.theme.NumberTiny
import com.heybox.checkin.ui.theme.Glass
import com.heybox.checkin.ui.theme.glass
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
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
import kotlinx.coroutines.delay

private enum class HomeTab { Today, Settings }

private data class Permissions(val notify: Boolean, val battery: Boolean, val exactAlarm: Boolean) {
    val readyCount: Int get() = listOf(notify, battery, exactAlarm).count { it }
}

private const val SHARE_POST = "1"
private const val SHARE_GAME = "19"
private const val SHARE_REVIEW = "31"
private val SHARE_IDS = listOf(SHARE_POST, SHARE_GAME, SHARE_REVIEW)
private val TAB_BOTTOM = 190.dp

/**
 * 首页拆成「今天 / 设置」两个页签：
 * 今天 = 状态 + 到账数字 + 下次时间 + 四个任务 + 七天记录，一屏放得下；
 * 设置 = 自动签到、值守条件、最近任务隐藏、外观、关于与退出。
 */
@Composable
fun HomeScreen(
    context: Context,
    store: SessionStore,
    config: HeyboxConfig,
    appearance: String,
    darkTheme: Boolean,
    resumeTick: Int,
    previewExpired: Boolean = false,
    previewHistory: List<RunRecord>? = null,
    onAppearanceChange: (String) -> Unit,
    onRelogin: () -> Unit,
    onLogout: () -> Unit,
) {
    var record by remember { mutableStateOf(previewHistory?.lastOrNull() ?: store.loadRun()) }
    var history by remember { mutableStateOf(previewHistory ?: recentHistory(store)) }
    var tab by remember { mutableStateOf(HomeTab.Today) }
    var showAbout by remember { mutableStateOf(false) }
    var showRecord by remember { mutableStateOf(false) }
    var startupHelp by remember { mutableStateOf(false) }
    var askHideRecent by remember { mutableStateOf(false) }
    var scheduled by remember { mutableStateOf(previewHistory != null || store.scheduledEnabled()) }
    var nextAt by remember { mutableStateOf(if (previewHistory != null) previewNextRun() else store.nextTrigger()) }
    var startHour by remember { mutableStateOf(store.windowStart()) }
    var endHour by remember { mutableStateOf(store.windowEnd()) }
    var expired by remember { mutableStateOf(store.sessionExpired() || previewExpired) }
    var hideRecent by remember { mutableStateOf(store.hideFromRecents()) }
    var enabledShares by remember {
        mutableStateOf(
            if (previewHistory != null) setOf(SHARE_POST, SHARE_GAME, SHARE_REVIEW) else store.enabledShares(),
        )
    }
    var permissions by remember { mutableStateOf(readPermissions(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var launching by remember { mutableStateOf(false) }
    var sawRunning by remember { mutableStateOf(false) }
    val running by RunState.running.collectAsState()
    val stage by RunState.stage.collectAsState()
    val todayScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }
    if (showRecord) {
        RecordScreen(history = history, onBack = { showRecord = false })
        return
    }

    val now = System.currentTimeMillis()
    // 只有同一天的记录才能当「今天」用，否则跨天之后首页会挂着昨天的完成态和增量。
    val todayRecord = record.todayOrNull(now)
    val ranToday = todayRecord != null
    val shareDone = todayRecord?.let { run ->
        enabledShares.all { run.taskStates[it] == "finish" }
    } == true
    val status = when {
        running -> "执行中"
        error != null -> "未完成"
        ranToday && todayRecord?.signed == true && shareDone -> "今日已完成"
        ranToday && todayRecord?.signed == true -> "今日已签到"
        else -> "今日待执行"
    }
    val cells = weekCells(history, now)

    LaunchedEffect(resumeTick) {
        // 预览模式不碰真实状态：不重排闹钟、不覆盖假数据。
        if (previewHistory != null) return@LaunchedEffect
        permissions = readPermissions(context)
        scheduled = store.scheduledEnabled()
        val stored = store.nextTrigger()
        nextAt = if (scheduled) {
            when {
                // 正在执行的那次会自己排下一次，这里别抢。
                RunLock.isBusy() || RunState.running.value -> stored
                // 未来的时间不要重新抽签，但要重新落一次闹钟：
                // force-stop / 系统清理会取消 AlarmManager 里的闹钟，只留下 prefs 里的时间。
                stored > System.currentTimeMillis() -> if (Scheduler.rearm(context, stored)) stored else 0L
                else -> Scheduler.scheduleNext(context)
            }
        } else {
            0L
        }
        expired = store.sessionExpired()
        history = recentHistory(store)
        record = store.loadRun()
        Widgets.refresh(context)
    }
    LaunchedEffect(running) {
        if (previewHistory != null) return@LaunchedEffect
        launching = false
        if (running) {
            sawRunning = true
            return@LaunchedEffect
        }
        scheduled = store.scheduledEnabled()
        nextAt = store.nextTrigger()
        expired = store.sessionExpired()
        history = recentHistory(store)
        record = store.loadRun()
        // 只在「这次界面亲眼看到过一次执行结束」后回填错误，
        // 避免冷启动时把昨天留下的失败文案又顶到横幅里。
        if (sawRunning) {
            val result = store.lastResult()
            error = if (result != null && (result.startsWith("失败") || result.startsWith("登录已过期"))) {
                result
            } else {
                null
            }
        }
        Widgets.refresh(context)
    }
    // 服务启动到 RunState 置位之间有个很短的窗口，按钮先锁住，避免连点拉起两次。
    LaunchedEffect(launching) {
        if (launching) {
            delay(2500)
            if (!RunState.running.value) launching = false
        }
    }

    val runNow: () -> Unit = {
        if (RunLock.isBusy()) {
            error = "已有一次任务在执行，本次忽略"
        } else {
            error = null
            launching = true
            runCatching { CheckInService.start(context, force = true) }
                .onFailure {
                    launching = false
                    error = "无法启动执行服务：${describe(it)}"
                }
        }
    }

    val barBackdrop = rememberLayerBackdrop()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(barBackdrop),
        ) {
        when (tab) {
            HomeTab.Today -> TodayTab(
                config = config,
                status = status,
                running = running,
                expired = expired,
                scheduled = scheduled,
                nextAt = nextAt,
                startHour = startHour,
                endHour = endHour,
                today = todayRecord,
                cells = cells,
                enabledShares = enabledShares,
                hideRecent = hideRecent,
                error = error,
                launching = launching,
                stage = stage,
                scroll = todayScroll,
                onRelogin = onRelogin,
                onRun = runNow,
                onShowRecord = { showRecord = true },
                onToggleShare = { id ->
                    val enabled = store.shareTaskEnabled(id)
                    store.setShareTaskEnabled(id, !enabled)
                    enabledShares = store.enabledShares()
                },
            )

            HomeTab.Settings -> SettingsTab(
                permissions = permissions,
                scheduled = scheduled,
                nextAt = nextAt,
                startHour = startHour,
                endHour = endHour,
                hideRecent = hideRecent,
                appearance = appearance,
                error = error,
                scroll = settingsScroll,
                onToggleScheduled = { enabled ->
                    scheduled = enabled
                    store.setScheduled(enabled)
                    nextAt = if (enabled) Scheduler.scheduleNext(context) else {
                        Scheduler.cancel(context)
                        0L
                    }
                },
                onWindow = { newStart, newEnd ->
                    startHour = newStart
                    endHour = newEnd
                    store.setWindow(newStart, newEnd)
                    if (scheduled) nextAt = Scheduler.scheduleNext(context)
                },
                onToggleHideRecent = { enabled ->
                    if (enabled) {
                        askHideRecent = true
                    } else {
                        store.setHideFromRecents(false)
                        hideRecent = false
                    }
                },
                onFix = { target ->
                    when (target) {
                        "notify" -> openAppSettings(context)
                        "battery" -> requestIgnoreBattery(context)
                        "alarm" -> requestExactAlarm(context)
                    }
                },
                onAutostart = { startupHelp = true },
                onAppearanceChange = onAppearanceChange,
                onAbout = { showAbout = true },
                onLogout = onLogout,
                onExpiredRelogin = onRelogin,
                onFixPermissions = { permissions = readPermissions(context) },
            )
        }
        }

        BottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            backdrop = barBackdrop,
            tab = tab,
            onTab = { tab = it },
        )

        if (askHideRecent) {
            AlertDialog(
                onDismissRequest = { askHideRecent = false },
                containerColor = Ink.surface,
                titleContentColor = Ink.ink,
                textContentColor = Ink.muted,
                title = { Text("先在最近任务里锁定盒签", style = MaterialTheme.typography.titleMedium) },
                text = {
                    Text(
                        "开启后，离开 App 时会结束任务，多任务里不再留下盒签卡片。\n\n" +
                            "请先在最近任务里长按盒签卡片并锁定，再回来开启：没锁定时卡片直接消失，" +
                            "系统可能顺手把进程一起回收，定时闹钟也会失效，只能重新打开 App 补排。\n\n" +
                            "锁定后点下面的按钮。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        store.setHideFromRecents(true)
                        hideRecent = true
                        askHideRecent = false
                    }) { Text("已锁定，开启", color = Ink.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { askHideRecent = false }) { Text("取消", color = Ink.muted) }
                },
            )
        }

        if (startupHelp) {
            AlertDialog(
                onDismissRequest = { startupHelp = false },
                containerColor = Ink.surface,
                titleContentColor = Ink.ink,
                textContentColor = Ink.muted,
                title = { Text("把盒签加进自启动", style = MaterialTheme.typography.titleMedium) },
                text = {
                    Text(
                        "各家系统的入口不一样，App 无权直接跳过去，按机型手动开一次即可：\n\n" +
                            "华为：设置 → 应用 → 应用启动管理 → 盒签 → 关掉「自动管理」，勾选自启动、后台活动\n" +
                            "小米：设置 → 应用设置 → 授权管理 → 自启动 → 盒签\n" +
                            "OPPO / vivo：设置 → 应用管理 → 盒签 → 自启动 / 后台运行\n\n" +
                            "另外在最近任务里把盒签卡片锁定，能少被系统清一次。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        startupHelp = false
                        openStartupSettings(context)
                    }) { Text("打开系统设置", color = Ink.accent) }
                },
                dismissButton = {
                    TextButton(onClick = { startupHelp = false }) { Text("知道了", color = Ink.muted) }
                },
            )
        }
    }
}

/* ------------------------------------------------------------------ 今天 */

@Composable
private fun TodayTab(
    config: HeyboxConfig,
    status: String,
    running: Boolean,
    expired: Boolean,
    scheduled: Boolean,
    nextAt: Long,
    startHour: Int,
    endHour: Int,
    today: RunRecord?,
    cells: List<WeekCell>,
    enabledShares: Set<String>,
    hideRecent: Boolean,
    error: String?,
    launching: Boolean,
    stage: String,
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

        Spacer(Modifier.height(16.dp))
        StatusRow(status = status, running = running)

        Spacer(Modifier.height(18.dp))
        Text("今日到账", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        Spacer(Modifier.height(6.dp))
        Figures(today)

        Spacer(Modifier.height(8.dp))
        Text(balanceText(today), style = NumberTiny, color = Ink.muted)

        Spacer(Modifier.height(18.dp))
        WindowBlock(
            scheduled = scheduled,
            nextAt = nextAt,
            startHour = startHour,
            endHour = endHour,
        )

        Spacer(Modifier.height(20.dp))
        SectionHead(title = "每日四件", hint = if (today == null) "今天 · 尚未执行" else "今天")
        Spacer(Modifier.height(10.dp))
        TaskTiles(
            today = today,
            enabledShares = enabledShares,
            onToggleShare = onToggleShare,
        )

        Spacer(Modifier.height(20.dp))
        SectionHead(
            title = "最近七天",
            hint = "全部记录",
            onHint = onShowRecord,
        )
        Spacer(Modifier.height(10.dp))
        WeekRow(cells)

        if (hideRecent) {
            Spacer(Modifier.height(14.dp))
            Text(
                "最近任务隐藏已开启：离开 App 会结束任务，多任务里不留卡片。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.muted,
                fontSize = 11.sp,
            )
        }

        error?.let {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(18.dp))
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
private fun StatusRow(status: String, running: Boolean) {
    val color = when {
        running -> Ink.accent
        status == "今日已完成" || status == "今日已签到" -> Ink.success
        status == "未完成" -> MaterialTheme.colorScheme.error
        else -> Ink.muted
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
            status,
            style = MaterialTheme.typography.headlineMedium,
            color = Ink.ink,
        )
        Spacer(Modifier.weight(1f))
        Text(formatDay(System.currentTimeMillis()), style = MaterialTheme.typography.bodySmall, color = Ink.muted, fontSize = 12.sp)
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
        Text("随机窗口", style = NumberTiny, color = Ink.muted, fontSize = 10.sp)
        Text(hourLabel(endHour), style = NumberTiny, color = Ink.muted, fontSize = 10.sp)
    }
}

private fun previewNextRun(): Long = java.util.Calendar.getInstance().apply {
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
private fun SectionHead(title: String, hint: String, onHint: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        Text(
            hint,
            style = if (onHint != null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
            color = if (onHint != null) Ink.accent else Ink.muted,
            fontSize = if (onHint != null) 12.sp else 11.sp,
            modifier = if (onHint != null) Modifier.clickable(onClick = onHint) else Modifier,
        )
    }
}

@Composable
private fun TaskTiles(today: RunRecord?, enabledShares: Set<String>, onToggleShare: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SignTile(state = taskState(today, "sign", today?.signed == true), modifier = Modifier.weight(1f))
            ShareTile(
                name = "分享帖子",
                state = taskState(today, SHARE_POST, false),
                enabled = SHARE_POST in enabledShares,
                onToggle = { onToggleShare(SHARE_POST) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShareTile(
                name = "分享游戏",
                state = taskState(today, SHARE_GAME, false),
                enabled = SHARE_GAME in enabledShares,
                onToggle = { onToggleShare(SHARE_GAME) },
                modifier = Modifier.weight(1f),
            )
            ShareTile(
                name = "分享游戏评价",
                state = taskState(today, SHARE_REVIEW, false),
                enabled = SHARE_REVIEW in enabledShares,
                onToggle = { onToggleShare(SHARE_REVIEW) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Tile(name: String, state: String, modifier: Modifier, trailing: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .height(86.dp)
            .glass(RoundedCornerShape(20.dp), raised = false)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = Ink.ink, fontSize = 14.sp)
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
private fun SettingsTab(
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
    onExpiredRelogin: () -> Unit,
    onFixPermissions: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 150.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("设置", style = MaterialTheme.typography.headlineMedium, color = Ink.ink)
            Text(
                "${permissions.readyCount}/3 权限就绪",
                style = MaterialTheme.typography.labelSmall,
                color = if (permissions.readyCount == 3) Ink.muted else Ink.accent,
                modifier = Modifier.clickable(onClick = onFixPermissions),
            )
        }

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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("执行时段", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
                Text(
                    "${hourLabel(startHour)} → ${hourLabel(endHour)}",
                    style = NumberSmall,
                    color = Ink.ink,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(8.dp))
            Text(
                "每天在时段内随机挑一刻，不会固定在同一个时间；改完立即重排。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.muted,
                fontSize = 11.sp,
            )
        }

        GroupLabel("值守")
        Card {
            Text("值守条件", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            Spacer(Modifier.height(4.dp))
            PermissionRow("通知权限", permissions.notify, "开启", onClick = { onFix("notify") })
            PermissionRow("忽略电池优化", permissions.battery, "去设置", onClick = { onFix("battery") })
            PermissionRow("精确闹钟", permissions.exactAlarm, "去设置", onClick = { onFix("alarm") })
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .padding(top = 7.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Ink.accent),
                    )
                    Column {
                        Text("自启动 / 后台运行", style = MaterialTheme.typography.bodyMedium, color = Ink.ink)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "系统读不到这个开关，只能自己去确认",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink.muted,
                            fontSize = 11.5.sp,
                        )
                    }
                }
                Text(
                    "去设置",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.accent,
                    modifier = Modifier
                        .clickable(onClick = onAutostart)
                        .padding(start = 12.dp, top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Card {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("最近任务隐藏", style = MaterialTheme.typography.titleMedium, color = Ink.ink)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "离开时结束任务，多任务里不留卡片",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.muted,
                        fontSize = 11.5.sp,
                    )
                }
                Switch(
                    checked = hideRecent,
                    onCheckedChange = onToggleHideRecent,
                    colors = switchColors(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "开启前先锁定卡片，否则卡片消失时系统可能连带回收进程，定时闹钟也会失效。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.muted,
                fontSize = 11.sp,
            )
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
            ChevronRow("关于盒签", onClick = onAbout)
            HorizontalDivider(color = Ink.line, thickness = 1.dp)
            ChevronRow("退出登录", onClick = onLogout)
            Spacer(Modifier.height(6.dp))
            Text(
                "盒签 ${com.heybox.checkin.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.muted,
                fontSize = 11.sp,
            )
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (permissions.readyCount < 3) {
            Spacer(Modifier.height(12.dp))
            Text(
                "有 ${3 - permissions.readyCount} 项权限未就绪，夜间可能被系统清掉。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.accent,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "重登只在登录过期时使用",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
            fontSize = 11.sp,
            modifier = Modifier.clickable(onClick = onExpiredRelogin),
        )
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

@Composable
private fun PermissionRow(name: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (ok) Ink.success else Ink.accent),
            )
            Text(name, style = MaterialTheme.typography.bodyMedium, color = Ink.ink)
        }
        if (ok) {
            Text("已就绪", style = MaterialTheme.typography.bodySmall, color = Ink.muted, fontSize = 12.sp)
        } else {
            Text(
                action,
                style = MaterialTheme.typography.bodySmall,
                color = Ink.accent,
                modifier = Modifier.clickable(onClick = onClick),
            )
        }
    }
}

@Composable
private fun ChevronRow(text: String, onClick: () -> Unit) {
    val muted = Ink.muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Ink.ink)
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

/* ------------------------------------------------------------- 底部导航 */

@Composable
private fun BottomBar(
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop,
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
) {
    val highlight = Glass.palette.highlight
    // 胶囊宽度包住内容，不再拉满整行——空玻璃看起来最像「一块白板」。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(30.dp) },
                    effects = {
                        vibrancy()
                        blur(18.dp.toPx())
                        lens(30.dp.toPx(), 72.dp.toPx())
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.85f) },
                    shadow = { Shadow.Default },
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Ink.surface.copy(alpha = 0.18f),
                            Ink.surface.copy(alpha = 0.05f),
                        ),
                    ),
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(Glass.palette.borderTop, Glass.palette.borderBottom),
                    ),
                    RoundedCornerShape(30.dp),
                )
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(highlight, Color.Transparent),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.8f, size.height),
                        ),
                    )
                }
                .padding(horizontal = 26.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem("今天", tab == HomeTab.Today, TabIcon.Today) { onTab(HomeTab.Today) }
            TabItem("设置", tab == HomeTab.Settings, TabIcon.Settings) { onTab(HomeTab.Settings) }
        }
    }
}

private enum class TabIcon { Today, Settings }

@Composable
private fun TabItem(label: String, selected: Boolean, icon: TabIcon, onClick: () -> Unit) {
    val color = if (selected) Ink.ink else Ink.muted
    val canvasColor = Ink.canvas
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val w = size.width
            when (icon) {
                TabIcon.Today -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * 0.14f, w * 0.2f),
                        size = Size(w * 0.72f, w * 0.66f),
                        cornerRadius = CornerRadius(w * 0.18f),
                        style = stroke,
                    )
                    drawLine(color, Offset(w * 0.33f, w * 0.09f), Offset(w * 0.33f, w * 0.3f), 1.5.dp.toPx(), StrokeCap.Round)
                    drawLine(color, Offset(w * 0.67f, w * 0.09f), Offset(w * 0.67f, w * 0.3f), 1.5.dp.toPx(), StrokeCap.Round)
                    val path = Path().apply {
                        moveTo(w * 0.33f, w * 0.55f)
                        lineTo(w * 0.46f, w * 0.68f)
                        lineTo(w * 0.7f, w * 0.42f)
                    }
                    drawPath(path, color, style = stroke)
                }

                TabIcon.Settings -> {
                    drawLine(color, Offset(w * 0.12f, w * 0.34f), Offset(w * 0.88f, w * 0.34f), 1.5.dp.toPx(), StrokeCap.Round)
                    drawLine(color, Offset(w * 0.12f, w * 0.68f), Offset(w * 0.88f, w * 0.68f), 1.5.dp.toPx(), StrokeCap.Round)
                    drawCircle(color = canvasColor, radius = w * 0.11f, center = Offset(w * 0.38f, w * 0.34f))
                    drawCircle(color = color, radius = w * 0.11f, center = Offset(w * 0.38f, w * 0.34f), style = stroke)
                    drawCircle(color = canvasColor, radius = w * 0.11f, center = Offset(w * 0.64f, w * 0.68f))
                    drawCircle(color = color, radius = w * 0.11f, center = Offset(w * 0.64f, w * 0.68f), style = stroke)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 10.5.sp, letterSpacing = 0.sp)
    }
}

/* ---------------------------------------------------------------- 记录 */

@Composable
fun RecordScreen(history: List<RunRecord>, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    val cells = weekCells(history, System.currentTimeMillis()).reversed()
    val done = cells.count { it.state == DayState.DONE }
    val skipped = cells.count { it.state == DayState.SKIPPED }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onBack,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text("← 返回", style = MaterialTheme.typography.bodySmall, color = Ink.muted)
        }
        Spacer(Modifier.height(10.dp))
        Text("记录", style = MaterialTheme.typography.displayLarge, color = Ink.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "最近七天 · 完成 $done 天" + if (skipped > 0) "，跳过 $skipped 天" else "",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 2.dp),
        ) {
            cells.forEachIndexed { index, cell ->
                if (index > 0) HorizontalDivider(color = Ink.line, thickness = 1.dp)
                RecordRow(cell)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun RecordRow(cell: WeekCell) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(54.dp)) {
            Text(cell.monthDay, style = NumberTiny, color = Ink.ink, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "周${cell.weekday}",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.muted,
                fontSize = 10.sp,
                letterSpacing = 0.sp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when (cell.state) {
                    DayState.DONE -> recordDeltaText(cell)
                    DayState.SKIPPED -> "未执行（已由其它入口完成）"
                    DayState.FAILED -> "执行了，但签到没成功"
                    DayState.NONE -> "未执行"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (cell.state == DayState.DONE) Ink.ink else Ink.muted,
                fontSize = 12.5.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                when {
                    cell.state == DayState.SKIPPED -> "检测到当日已签到，没有重复执行"
                    cell.state == DayState.FAILED -> "检查当天日志或重新执行一次"
                    cell.executed && cell.time != null -> "${cell.time} 执行"
                    else -> "当天没有记录"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Ink.muted,
                fontSize = 11.sp,
                letterSpacing = 0.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        when (cell.state) {
            DayState.DONE -> MiniCheck(Ink.success)
            DayState.FAILED -> Text("–", style = NumberTiny, color = Ink.error)
            DayState.SKIPPED, DayState.NONE -> Text("–", style = NumberTiny, color = Ink.line)
        }
    }
}

private fun recordDeltaText(cell: WeekCell): String {
    val exp = cell.expDelta?.let { if (it >= 0) "+$it" else "$it" } ?: "—"
    val battery = cell.batteryDelta?.let { if (it >= 0) " · 电池 +$it" else "" } ?: ""
    return "经验 $exp · H 币 +${cell.coinDelta}$battery"
}

/* ---------------------------------------------------------------- 组件 */

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

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Ink.onAccent,
    checkedTrackColor = Ink.accent,
    uncheckedThumbColor = Ink.muted,
    uncheckedTrackColor = Ink.raised,
    uncheckedBorderColor = Ink.line,
)

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

/** 历史为空时（旧版本只存了一份最近记录）退回用那一条补位，避免「最近七天」直接空着。 */
private fun recentHistory(store: SessionStore): List<RunRecord> =
    store.history().ifEmpty { listOfNotNull(store.loadRun()) }

private fun readPermissions(context: Context): Permissions {
    val notify = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val battery = power.isIgnoringBatteryOptimizations(context.packageName)
    val alarm = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) true else {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }
    return Permissions(notify, battery, alarm)
}

private fun requestIgnoreBattery(context: Context) {
    val manager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (manager.isIgnoringBatteryOptimizations(context.packageName)) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun requestExactAlarm(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (manager.canScheduleExactAlarms()) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 各家 ROM 的自启动页都不是公开 API：按最常见的几个组件名依次尝试，全部失败就退回应用详情。 */
private fun openStartupSettings(context: Context) {
    val candidates = listOf(
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
    )
    for (component in candidates) {
        val launched = runCatching {
            context.startActivity(
                Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (launched) return
    }
    openAppSettings(context)
}
