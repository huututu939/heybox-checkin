package com.heybox.checkin.ui

import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heybox.checkin.data.PermissionState
import com.heybox.checkin.core.HeyboxConfig
import com.heybox.checkin.BuildConfig
import com.heybox.checkin.data.FailureKind
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.SettingsExport
import com.heybox.checkin.data.UpdateCheck
import com.heybox.checkin.data.UpdateInfo
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.data.missStreak
import com.kyant.backdrop.backdrops.layerBackdrop
import com.heybox.checkin.ui.theme.LocalBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.heybox.checkin.work.CheckInService
import com.heybox.checkin.work.RunLock
import com.heybox.checkin.work.RunState
import com.heybox.checkin.work.Scheduler
import com.heybox.checkin.widget.Widgets
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue



internal enum class HomeTab { Today, Settings }

internal data class Permissions(val notify: Boolean, val battery: Boolean, val exactAlarm: Boolean) {
    val readyCount: Int get() = listOf(notify, battery, exactAlarm).count { it }
}

/**
 * 自动签到的四个相关值总是成对出现（排程开关、下次触发、窗口起止），
 * 收成一份数据传递，避免页签函数一路挂八个散参数。
 */
internal data class AutoPlan(
    val scheduled: Boolean,
    val nextAt: Long,
    val startHour: Int,
    val endHour: Int,
)

internal const val SHARE_POST = "1"
internal const val SHARE_GAME = "19"
internal const val SHARE_REVIEW = "31"
internal val SHARE_IDS = listOf(SHARE_POST, SHARE_GAME, SHARE_REVIEW)
internal val TAB_BOTTOM = 190.dp

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
    previewUpdate: UpdateInfo? = null,
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
    var plan by remember {
        mutableStateOf(
            AutoPlan(
                scheduled = previewHistory != null || store.scheduledEnabled(),
                nextAt = if (previewHistory != null) previewNextRun() else store.nextTrigger(),
                startHour = store.windowStart(),
                endHour = store.windowEnd(),
            ),
        )
    }
    var expired by remember { mutableStateOf(store.sessionExpired() || previewExpired) }
    var hideRecent by remember { mutableStateOf(store.hideFromRecents()) }
    var successNotify by remember { mutableStateOf(store.successNotify()) }
    var nightQuiet by remember { mutableStateOf(store.nightQuiet()) }
    var enabledShares by remember {
        mutableStateOf(
            if (previewHistory != null) setOf(SHARE_POST, SHARE_GAME, SHARE_REVIEW) else store.enabledShares(),
        )
    }
    var permissions by remember { mutableStateOf(readPermissions(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var launching by remember { mutableStateOf(false) }
    var sawRunning by remember { mutableStateOf(false) }
    // 启动时自动查一次更新；失败就静默，别拿网络问题烦人
    var update by remember { mutableStateOf(previewUpdate) }
    var showPermissionHelp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (previewHistory != null || previewUpdate != null) return@LaunchedEffect
        // 12 小时内查过就不再打 GitHub：未认证接口 60 次/小时/IP，开关几次 App 就会撞 403
        if (!UpdateCheck.shouldAutoCheck(store.updateCheckedAt(), store.updateCheckOk())) return@LaunchedEffect
        val result = UpdateCheck.latest()
        store.markUpdateChecked(ok = result.isSuccess)
        result.onSuccess { info ->
            val skipped = info.tag == store.skippedVersion()
            if (!skipped && UpdateCheck.isNewer(info.tag, BuildConfig.VERSION_NAME)) update = info
        }
    }
    val running by RunState.running.collectAsState()
    val stage by RunState.stage.collectAsState()
    val todayScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()

    // 首次进首页时，如果还有权限没开，提醒一次（之后不再打扰）
    LaunchedEffect(Unit) {
        if (previewHistory == null && !store.permissionHelpSeen() && permissions.readyCount < 3) {
            showPermissionHelp = true
        }
    }

    update?.let { info ->
        UpdateDialog(
            info = info,
            onDismiss = { update = null },
            onSkip = { store.skipVersion(info.tag) },
            resumeTick = resumeTick,
        )
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false }, resumeTick = resumeTick)
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
        running -> TodayStatus.RUNNING
        error != null -> TodayStatus.INCOMPLETE
        ranToday && todayRecord.signed == true && shareDone -> TodayStatus.DONE
        ranToday && todayRecord.signed == true -> TodayStatus.SIGNED
        ranToday && todayRecord.error != null -> TodayStatus.FAILED
        else -> TodayStatus.PENDING
    }
    val cells = weekCells(history, now)
    // 连续没签上也属于「今天该知道的事」：闹钟被 ROM 清掉不会留下失败记录，
    // 只靠记录页等用户自己翻是发现不了的
    val missed = missStreak(history, now)
    val lastFailure = history.firstOrNull { !it.signed && it.error != null }
    val lastReason = lastFailure?.error
    // 登录过期时横幅直接给「重新登录」，比让用户点进记录页自己判断有用
    val needsRelogin = FailureKind.from(lastFailure?.errorKind) == FailureKind.SESSION

    LaunchedEffect(resumeTick) {
        // 预览模式不碰真实状态：不重排闹钟、不覆盖假数据。
        if (previewHistory != null) return@LaunchedEffect
        permissions = readPermissions(context)
        val scheduledNow = store.scheduledEnabled()
        val stored = store.nextTrigger()
        val nextAtNow = if (scheduledNow) {
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
        plan = AutoPlan(scheduledNow, nextAtNow, store.windowStart(), store.windowEnd())
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
        plan = plan.copy(
            scheduled = store.scheduledEnabled(),
            nextAt = store.nextTrigger(),
            startHour = store.windowStart(),
            endHour = store.windowEnd(),
        )
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

    val pageBackdrop = rememberLayerBackdrop()
    // 底栏要同时折射「页面内容」和「背景光斑」：只取页面层的话，空白处是透明的，玻璃会发灰
    val rootBackdrop = LocalBackdrop.current
    val barBackdrop = if (rootBackdrop != null) {
        rememberCombinedBackdrop(rootBackdrop, pageBackdrop)
    } else {
        pageBackdrop
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop),
        ) {
        when (tab) {
            HomeTab.Today -> TodayTab(
                config = config,
                status = status,
                running = running,
                expired = expired,
                plan = plan,
                today = todayRecord,
                cells = cells,
                enabledShares = enabledShares,
                error = error,
                launching = launching,
                stage = stage,
                missed = missed,
                lastReason = lastReason,
                needsRelogin = needsRelogin,
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
                plan = plan,
                permissions = permissions,
                hideRecent = hideRecent,
                appearance = appearance,
                successNotify = successNotify,
                nightQuiet = nightQuiet,
                error = error,
                scroll = settingsScroll,
                onToggleScheduled = { enabled ->
                    store.setScheduled(enabled)
                    plan = plan.copy(
                        scheduled = enabled,
                        nextAt = if (enabled) Scheduler.scheduleNext(context) else 0L,
                    )
                },
                onWindow = { newStart, newEnd ->
                    store.setWindow(newStart, newEnd)
                    plan = plan.copy(
                        startHour = newStart,
                        endHour = newEnd,
                        nextAt = if (plan.scheduled) Scheduler.scheduleNext(context) else plan.nextAt,
                    )
                },
                onToggleHideRecent = { enabled ->
                    if (enabled) {
                        askHideRecent = true
                    } else {
                        store.setHideFromRecents(false)
                        hideRecent = false
                    }
                },
                onSuccessNotify = { enabled ->
                    store.setSuccessNotify(enabled)
                    successNotify = enabled
                },
                onNightQuiet = { enabled ->
                    store.setNightQuiet(enabled)
                    nightQuiet = enabled
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
                store = store,
                onImport = { cfg ->
                    SettingsExport.save(store, cfg)
                    successNotify = cfg.successNotify
                    nightQuiet = cfg.nightQuiet
                    enabledShares = store.enabledShares()
                    hideRecent = cfg.hideFromRecents
                    onAppearanceChange(cfg.appearance)
                    plan = plan.copy(
                        scheduled = cfg.scheduled,
                        startHour = cfg.startHour,
                        endHour = cfg.endHour,
                        nextAt = if (cfg.scheduled) Scheduler.scheduleNext(context) else 0L,
                    )
                },
                onHistoryChanged = {
                    history = recentHistory(store)
                },
            )
        }
        }

        LiquidBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            backdrop = barBackdrop,
            tab = tab,
            darkTheme = darkTheme,
            onTab = { tab = it },
        )

        if (showPermissionHelp) {
        PermissionHelpDialog(
            permissions = permissions,
            onGoSettings = {
                showPermissionHelp = false
                store.markPermissionHelpSeen()
                tab = HomeTab.Settings
            },
            onDismiss = {
                showPermissionHelp = false
                store.markPermissionHelpSeen()
            },
        )
    }

    if (askHideRecent) {
            GlassDialog(onDismissRequest = { askHideRecent = false }) {
                DialogTitle("先在最近任务里锁定盒签")
                Spacer(Modifier.height(10.dp))
                DialogBody(
                    "开启后，离开 App 时会结束任务，多任务里不再留下盒签卡片。\n\n" +
                        "请先在最近任务里长按盒签卡片并锁定，再回来开启：没锁定时卡片直接消失，" +
                        "系统可能顺手把进程一起回收，定时闹钟也会失效，只能重新打开 App 补排。\n\n" +
                        "锁定后点下面的按钮。",
                )
                DialogActions(
                    primary = "已锁定，开启",
                    onPrimary = {
                        store.setHideFromRecents(true)
                        hideRecent = true
                        askHideRecent = false
                    },
                    onSecondary = { askHideRecent = false },
                )
            }
        }

        if (startupHelp) {
            GlassDialog(onDismissRequest = { startupHelp = false }) {
                DialogTitle("把盒签加进自启动")
                Spacer(Modifier.height(10.dp))
                DialogBody(
                    "各家系统的入口不一样，App 无权直接跳过去，按机型手动开一次即可：\n\n" +
                        "华为：设置 → 应用 → 应用启动管理 → 盒签 → 关掉「自动管理」，勾选自启动、后台活动\n" +
                        "小米：设置 → 应用设置 → 授权管理 → 自启动 → 盒签\n" +
                        "OPPO / vivo：设置 → 应用管理 → 盒签 → 自启动 / 后台运行\n\n" +
                        "另外在最近任务里把盒签卡片锁定，能少被系统清一次。",
                )
                DialogActions(
                    primary = "打开系统设置",
                    onPrimary = {
                        startupHelp = false
                        openStartupSettings(context)
                    },
                    secondary = "知道了",
                    onSecondary = { startupHelp = false },
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ 今天 */

/** 历史为空时（旧版本只存了一份最近记录）退回用那一条补位，避免「最近七天」直接空着。 */
private fun recentHistory(store: SessionStore): List<RunRecord> =
    store.history().ifEmpty { listOfNotNull(store.loadRun()) }

private fun readPermissions(context: Context): Permissions = Permissions(
    notify = PermissionState.notificationsAllowed(context),
    battery = PermissionState.batteryAllowed(context),
    exactAlarm = PermissionState.exactAlarmAllowed(context),
)

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
