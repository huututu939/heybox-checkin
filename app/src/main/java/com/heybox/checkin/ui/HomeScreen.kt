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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.heybox.checkin.core.HeyboxClient
import com.heybox.checkin.core.HeyboxConfig
import com.heybox.checkin.core.SessionExpiredException
import com.heybox.checkin.core.TaskParser
import com.heybox.checkin.core.TaskRunner
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.ui.theme.Ink
import com.heybox.checkin.ui.theme.NumberSmall
import com.heybox.checkin.ui.theme.NumberStyle
import com.heybox.checkin.work.RunLock
import com.heybox.checkin.work.Scheduler
import com.heybox.checkin.widget.Widgets
import kotlinx.coroutines.launch

private data class Permissions(val notify: Boolean, val battery: Boolean, val exactAlarm: Boolean) {
    val readyCount: Int get() = listOf(notify, battery, exactAlarm).count { it }
}

@Composable
fun HomeScreen(
    context: Context,
    store: SessionStore,
    config: HeyboxConfig,
    appearance: String,
    darkTheme: Boolean,
    resumeTick: Int,
    previewExpired: Boolean = false,
    onAppearanceChange: (String) -> Unit,
    onRelogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var record by remember { mutableStateOf(store.loadRun()) }
    var history by remember { mutableStateOf(recentHistory(store)) }
    var historyOpen by remember { mutableStateOf(false) }
    var startupHelp by remember { mutableStateOf(false) }
    var scheduled by remember { mutableStateOf(store.scheduledEnabled()) }
    var nextAt by remember { mutableStateOf(store.nextTrigger()) }
    var startHour by remember { mutableStateOf(store.windowStart()) }
    var endHour by remember { mutableStateOf(store.windowEnd()) }
    var expired by remember { mutableStateOf(store.sessionExpired()) }
    var running by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var permissions by remember { mutableStateOf(readPermissions(context)) }
    var showAbout by remember { mutableStateOf(false) }
    var enabledShares by remember { mutableStateOf(store.enabledShares()) }
    // 提到分支外面：进关于页再返回时，首页还停在原来的位置，不会跳回顶部。
    val homeScroll = rememberScrollState()

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    LaunchedEffect(Unit) {
        if (scheduled) nextAt = Scheduler.scheduleNext(context)
    }
    LaunchedEffect(resumeTick) {
        permissions = readPermissions(context)
        nextAt = store.nextTrigger()
        expired = store.sessionExpired()
        history = recentHistory(store)
        record = store.loadRun()
        Widgets.refresh(context)
    }

    val now = System.currentTimeMillis()
    val ranToday = record?.let { isSameLocalDay(it.at, now) } == true
    val shareDone = record?.let { run ->
        TaskParser.SHARE_IDS.all { run.taskStates[it] == "finish" }
    } == true
    val status = when {
        running -> "执行中"
        error != null -> "未完成"
        ranToday && record?.signed == true && shareDone -> "今日已完成"
        ranToday && record?.signed == true -> "今日已签到"
        else -> "今日待执行"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(homeScroll)
            .padding(horizontal = 22.dp, vertical = 24.dp),
    ) {
        // 顶部身份
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("盒签", style = MaterialTheme.typography.headlineMedium, color = Ink.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "heybox_id ${config.session.heyboxId}",
                    style = NumberSmall,
                    color = Ink.muted,
                )
            }
            StatusChip(status, running)
        }

        Spacer(Modifier.height(22.dp))
        if (expired || previewExpired) {
            ExpiredBanner(onRelogin)
            Spacer(Modifier.height(12.dp))
        }
        if (!scheduled) {
            SchedulePromptBanner(
                onEnable = {
                    scheduled = true
                    store.setScheduled(true)
                    nextAt = Scheduler.scheduleNext(context)
                },
            )
            Spacer(Modifier.height(12.dp))
        }
        HeroCard(record, ranToday, nextAt, scheduled, status, running, startHour, endHour)
        Spacer(Modifier.height(14.dp))
        TasksCard(
            record = record,
            ranToday = ranToday,
            enabledShares = enabledShares,
            onToggleShare = { id, enabled ->
                store.setShareTaskEnabled(id, enabled)
                enabledShares = store.enabledShares()
            },
        )
        Spacer(Modifier.height(14.dp))
        ScheduleCard(
            scheduled = scheduled,
            nextAt = nextAt,
            startHour = startHour,
            endHour = endHour,
            onToggle = { enabled ->
                scheduled = enabled
                store.setScheduled(enabled)
                if (enabled) {
                    nextAt = Scheduler.scheduleNext(context)
                } else {
                    Scheduler.cancel(context)
                    nextAt = 0L
                }
            },
            onWindow = { newStart, newEnd ->
                startHour = newStart
                endHour = newEnd
                store.setWindow(newStart, newEnd)
                if (scheduled) nextAt = Scheduler.scheduleNext(context)
            },
        )
        Spacer(Modifier.height(14.dp))
        PermissionsCard(
            permissions = permissions,
            onFix = { target ->
                when (target) {
                    "notify" -> openAppSettings(context)
                    "battery" -> requestIgnoreBattery(context)
                    "alarm" -> requestExactAlarm(context)
                }
            },
            onAutostart = { startupHelp = true },
        )
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
        Spacer(Modifier.height(14.dp))
        AppearanceCard(
            appearance = appearance,
            darkTheme = darkTheme,
            onChange = onAppearanceChange,
        )
        Spacer(Modifier.height(14.dp))
        HistoryCard(
            history = history,
            expanded = historyOpen,
            onToggle = { historyOpen = !historyOpen },
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (!RunLock.tryAcquire()) {
                    error = "已有一次任务在执行，本次忽略"
                    return@Button
                }
                error = null
                running = true
                stage = "检查今日状态"
                scope.launch {
                    try {
                        val runner = TaskRunner(
                            client = HeyboxClient(config),
                            account = config.session.heyboxId,
                            onStage = { stage = stageText(it) },
                            enabledShares = enabledShares,
                        )
                        runCatching { runner.runDaily() }
                            .onSuccess { report ->
                                // 只有真的打了签到/分享请求才算「本次执行」，否则只是刷新了余额。
                                val didExecute = report.execution.values.any { it == "executed" }
                                val fresh = RunRecord.from(
                                    snapshot = report.snapshot,
                                    signed = true,
                                    report = if (didExecute) report else null,
                                )
                                val prev = store.loadRun()
                                    ?.takeIf { isSameLocalDay(it.at, System.currentTimeMillis()) }
                                val merged = when {
                                    didExecute -> prev?.withAnotherRun(fresh) ?: fresh
                                    prev != null -> prev.withBalanceOnly(fresh)
                                    else -> fresh
                                }
                                store.saveRun(merged)
                                store.markRanToday()
                                store.clearSessionExpired()
                                store.setLastResult(
                                    "经验 +${report.expDelta?.toString() ?: "?"} · H币 +${report.coinDelta}",
                                )
                                record = merged
                                history = recentHistory(store)
                                expired = false
                                Widgets.refresh(context)
                                stage = ""
                            }
                            .onFailure {
                                if (it is SessionExpiredException) {
                                    store.markSessionExpired()
                                    expired = true
                                }
                                error = describe(it)
                                store.setLastResult("失败：${describe(it)}")
                                stage = ""
                            }
                        if (scheduled) nextAt = Scheduler.scheduleNext(context, skipToday = true)
                    } finally {
                        RunLock.release()
                        running = false
                    }
                }
            },
            enabled = !running,
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Ink.accent,
                contentColor = Ink.onAccent,
                disabledContainerColor = Ink.raised,
                disabledContentColor = Ink.muted,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(if (running) stage.ifEmpty { "执行中" } else "立即执行一次", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        if (running) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                color = Ink.accent,
                trackColor = Ink.line,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
        error?.let {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("×", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(30.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onLogout) {
                    Text("退出登录", color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { showAbout = true }) {
                    Text("关于", color = Ink.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "${permissions.readyCount}/3 权限就绪",
                style = MaterialTheme.typography.labelSmall,
                color = if (permissions.readyCount == 3) Ink.muted else Ink.accent,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun StatusChip(status: String, running: Boolean) {
    val color = when {
        running -> Ink.accent
        status == "今日已完成" -> Ink.accent
        status == "未完成" -> MaterialTheme.colorScheme.error
        else -> Ink.muted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .border(1.dp, Ink.line, RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(if (running) PulseAlpha() else 1f)
                .background(color, RoundedCornerShape(100.dp)),
        )
        Text(status, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 11.sp)
    }
}

/** 执行中状态点的呼吸，唯一一处动效，只为说明「还在跑」。 */
@Composable
private fun PulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val value by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "alpha",
    )
    return value
}

@Composable
private fun HeroCard(
    record: RunRecord?,
    ranToday: Boolean,
    nextAt: Long,
    scheduled: Boolean,
    status: String,
    running: Boolean,
    startHour: Int,
    endHour: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.surface, RoundedCornerShape(28.dp))
            .border(1.dp, Ink.line, RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("今日到账", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            Text(formatDay(System.currentTimeMillis()), style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            StatCell("经验", deltaText(record, record?.expDelta), Modifier.weight(1f))
            VerticalRule()
            StatCell("H 币", deltaText(record, record?.coinDelta), Modifier.weight(1f))
            VerticalRule()
            StatCell("电池", deltaText(record, record?.batteryDelta), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                record == null -> "尚未同步 · 点下方按钮跑一次"
                !record.executed -> "今天已由其它入口完成，本次没有重复执行"
                else -> "上面是本次执行带来的增量，下一行是账户当前余额"
            },
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("账户余额", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            Text(balanceText(record), style = MaterialTheme.typography.bodySmall, color = Ink.muted)
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Ink.line, thickness = 1.dp)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text("下一次", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
                Spacer(Modifier.height(6.dp))
                if (scheduled && nextAt > 0L) {
                    Text(formatTime(nextAt), style = NumberSmall, color = Ink.ink, fontSize = 17.sp)
                } else {
                    Text("定时未开启", style = NumberSmall, color = Ink.muted, fontSize = 17.sp)
                }
            }
            Text(
                if (scheduled) "随机窗口 ${hourLabel(startHour)}–${hourLabel(endHour)}" else "打开下方开关即可托管",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.muted,
            )
        }
    }
}

/** 增量只在「本次真的执行过」时才有意义；余额那组数字不能当收益看。 */
private fun deltaText(record: RunRecord?, value: Long?): String {
    if (record == null || !record.executed || value == null) return "—"
    return if (value >= 0) "+$value" else value.toString()
}

private fun balanceText(record: RunRecord?): String {
    if (record == null) return "H 币 — · 电池 — · 经验 —"
    return buildString {
        append("H 币 ${record.coin}")
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

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = NumberStyle, color = Ink.ink)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.muted)
    }
}

@Composable
private fun VerticalRule() {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .width(1.dp)
            .height(44.dp)
            .background(Ink.line),
    )
}

@Composable
private fun TasksCard(
    record: RunRecord?,
    ranToday: Boolean,
    enabledShares: Set<String>,
    onToggleShare: (String, Boolean) -> Unit,
) {
    val rows = listOf("sign" to "签到", "1" to "分享帖子", "19" to "分享游戏", "31" to "分享游戏评价")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .border(1.dp, Ink.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("每日四件", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            Text(
                when {
                    record == null -> "尚未同步"
                    ranToday -> "今天"
                    else -> formatTime(record.at)
                },
                style = MaterialTheme.typography.labelSmall,
                color = Ink.muted,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "签到始终执行；分享任务默认关闭，想要就自己打开",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(4.dp))
        rows.forEachIndexed { index, (id, name) ->
            if (index > 0) HorizontalDivider(color = Ink.line, thickness = 1.dp)
            val state = when (id) {
                "sign" -> if (record?.signed == true) "finish" else record?.taskStates?.get(id)
                else -> record?.taskStates?.get(id)
            }
            if (id == "sign") {
                TaskRow(name, state)
            } else {
                val enabled = id in enabledShares
                TaskRow(
                    name = name,
                    state = state,
                    toggleable = true,
                    enabled = enabled,
                    onToggle = { onToggleShare(id, !enabled) },
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    name: String,
    state: String?,
    toggleable: Boolean = false,
    enabled: Boolean = true,
    onToggle: () -> Unit = {},
) {
    val done = enabled && state == "finish"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = if (done) Ink.ink else Ink.muted,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (toggleable) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .border(
                            1.dp,
                            if (enabled) Ink.accent else Ink.line,
                            RoundedCornerShape(100.dp),
                        )
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        if (enabled) "开" else "关",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) Ink.accent else Ink.muted,
                        fontSize = 11.sp,
                    )
                }
            }
            if (done) {
                Text("✓", style = MaterialTheme.typography.labelSmall, color = Ink.accent, fontSize = 11.sp)
            }
            Text(
                when {
                    !enabled -> "已关闭"
                    state == "finish" -> "已完成"
                    state == "waiting" -> "待执行"
                    state == null -> "—"
                    else -> state
                },
                style = NumberSmall,
                color = Ink.muted,
            )
        }
    }
}

@Composable
private fun ScheduleCard(
    scheduled: Boolean,
    nextAt: Long,
    startHour: Int,
    endHour: Int,
    onToggle: (Boolean) -> Unit,
    onWindow: (Int, Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .border(1.dp, Ink.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("每天自动签到", style = MaterialTheme.typography.titleMedium, color = Ink.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (scheduled && nextAt > 0L) "下次 ${formatTime(nextAt)} · 每天重新随机" else "关闭中 · 不会自动执行",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.muted,
                )
            }
            Switch(
                checked = scheduled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink.onAccent,
                    checkedTrackColor = Ink.accent,
                    uncheckedThumbColor = Ink.muted,
                    uncheckedTrackColor = Ink.raised,
                    uncheckedBorderColor = Ink.line,
                ),
            )
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Ink.line, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("执行时段", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            Text(
                "${hourLabel(startHour)} → ${hourLabel(endHour)}",
                style = NumberSmall,
                color = Ink.ink,
            )
        }
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
        Spacer(Modifier.height(8.dp))
        Text(
            "每天在时段内随机挑一刻，不会固定在同一个时间；改完立即重排。",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
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
            .background(Ink.raised, RoundedCornerShape(10.dp))
            .border(1.dp, Ink.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StepButton("−", canDec) { onChange(hour - 1) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hourLabel(hour), style = NumberSmall, color = Ink.ink)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.muted, fontSize = 9.sp)
        }
        StepButton("+", canInc) { onChange(hour + 1) }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(if (enabled) Ink.surface else Ink.raised, RoundedCornerShape(8.dp))
            .border(1.dp, if (enabled) Ink.line else Ink.line, RoundedCornerShape(8.dp))
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

@Composable
private fun HistoryCard(history: List<RunRecord>, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .border(1.dp, Ink.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("最近七天", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            Text(
                if (expanded) "收起" else if (history.isEmpty()) "还没有记录" else "展开",
                style = MaterialTheme.typography.labelSmall,
                color = if (history.isEmpty()) Ink.muted else Ink.accent,
            )
        }
        if (expanded) {
            if (history.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("明天跑完第一笔后，这里会按天累积。", style = MaterialTheme.typography.bodySmall, color = Ink.muted)
            } else {
                history.take(7).forEachIndexed { index, run ->
                    if (index == 0) Spacer(Modifier.height(4.dp)) else HorizontalDivider(color = Ink.line, thickness = 1.dp)
                    HistoryRow(run)
                }
            }
        } else if (history.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HistoryRow(history.first())
        }
    }
}

@Composable
private fun HistoryRow(run: RunRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatMonthDay(run.at), style = NumberSmall, color = Ink.ink, fontSize = 14.sp)
        Text(
            if (run.executed) "经验 +${run.expDelta} · H 币 +${run.coinDelta}" else "未执行（已由其它入口完成）",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
        Text(
            if (run.signed) "✓" else "—",
            style = MaterialTheme.typography.labelSmall,
            color = if (run.signed) Ink.accent else Ink.muted,
        )
    }
}

@Composable
private fun ExpiredBanner(onRelogin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("登录已过期", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Text("自动签到会一直失败，重新登录即可恢复", style = MaterialTheme.typography.bodySmall, color = Ink.muted)
        }
        Text(
            "重新登录",
            style = MaterialTheme.typography.labelSmall,
            color = Ink.accent,
            modifier = Modifier
                .clickable(onClick = onRelogin)
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun SchedulePromptBanner(onEnable: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .border(1.dp, Ink.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("自动签到还没开启", style = MaterialTheme.typography.titleMedium, color = Ink.ink)
            Spacer(Modifier.height(4.dp))
            Text("开一次就不用再管，时间每天随机", style = MaterialTheme.typography.bodySmall, color = Ink.muted)
        }
        Text(
            "开启",
            style = MaterialTheme.typography.labelSmall,
            color = Ink.accent,
            modifier = Modifier
                .clickable(onClick = onEnable)
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun AppearanceCard(appearance: String, darkTheme: Boolean, onChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .border(1.dp, Ink.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("外观", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
            Text(
                if (darkTheme) "当前夜间" else "当前白天",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.muted,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppearanceOption("跟随系统", appearance == "auto", Modifier.weight(1f)) { onChange("auto") }
            AppearanceOption("白天", appearance == "light", Modifier.weight(1f)) { onChange("light") }
            AppearanceOption("夜间", appearance == "dark", Modifier.weight(1f)) { onChange("dark") }
        }
    }
}

@Composable
private fun AppearanceOption(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(if (selected) Ink.accent else Ink.raised, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) Ink.accent else Ink.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Ink.onAccent else Ink.muted,
        )
    }
}

@Composable
private fun PermissionsCard(permissions: Permissions, onFix: (String) -> Unit, onAutostart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .border(1.dp, Ink.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text("值守条件", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        Spacer(Modifier.height(4.dp))
        PermissionRow("通知权限", permissions.notify, "开启", onClick = { onFix("notify") })
        PermissionRow("忽略电池优化", permissions.battery, "去设置", onClick = { onFix("battery") })
        PermissionRow("精确闹钟", permissions.exactAlarm, "去设置", onClick = { onFix("alarm") })
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("!", style = NumberSmall, color = Ink.muted)
                Column {
                    Text("自启动 / 后台运行", style = MaterialTheme.typography.bodyMedium, color = Ink.muted)
                    Text(
                        "系统读不到这个开关，只能自己去确认",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.muted,
                        fontSize = 11.sp,
                    )
                }
            }
            Text(
                "去设置",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.accent,
                modifier = Modifier.clickable(onClick = onAutostart),
            )
        }
    }
}

@Composable
private fun PermissionRow(name: String, ok: Boolean, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (ok) "✓" else "!", style = NumberSmall, color = if (ok) Ink.muted else Ink.accent)
            Text(name, style = MaterialTheme.typography.bodyMedium, color = if (ok) Ink.ink else Ink.muted)
        }
        if (ok) {
            Text("已就绪", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        } else {
            Text(
                action,
                style = MaterialTheme.typography.labelSmall,
                color = Ink.accent,
                modifier = Modifier.clickable(onClick = onClick),
            )
        }
    }
}

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
