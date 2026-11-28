package com.heybox.checkin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heybox.checkin.data.RunRecord
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.data.SettingsExport
import com.heybox.checkin.data.SharedSettings
import com.heybox.checkin.data.recordsFromJson
import com.heybox.checkin.ui.theme.Ink
import com.heybox.checkin.ui.theme.NumberTiny
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember



/**
 * 设置页：自动、值守、通知、显示、其他五组。
 * 行组件与对话框在 [SettingsWidgets.kt]，这里只负责组织和业务文案。
 */
@Composable
internal fun SettingsTab(
    plan: AutoPlan,
    permissions: Permissions,
    hideRecent: Boolean,
    appearance: String,
    successNotify: Boolean,
    nightQuiet: Boolean,
    error: String?,
    scroll: ScrollState,
    onToggleScheduled: (Boolean) -> Unit,
    onWindow: (Int, Int) -> Unit,
    onToggleHideRecent: (Boolean) -> Unit,
    onSuccessNotify: (Boolean) -> Unit,
    onNightQuiet: (Boolean) -> Unit,
    onFix: (String) -> Unit,
    onAutostart: () -> Unit,
    onAppearanceChange: (String) -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
    store: SessionStore,
    onImport: (SharedSettings) -> Unit,
    onHistoryChanged: () -> Unit,
) {
    val context = LocalContext.current
    // 退出会清掉登录态、历史和定时，先问一句，免得误触
    var askLogout by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    var selfCheckOpen by remember { mutableStateOf(false) }
    // 备份导入：选完文件先确认再写入；选错了给一句能行动的提示
    var pendingImport by remember { mutableStateOf<List<RunRecord>?>(null) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
        val records = text?.let { recordsFromJson(it) }
        if (records.isNullOrEmpty()) {
            importMessage = "没有读到盒签备份。选「导出备份」生成的 JSON 文件。"
        } else {
            pendingImport = records
        }
    }

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
                            plan.scheduled && plan.nextAt > 0L -> "下次 ${formatTime(plan.nextAt)} · 每天重新随机"
                            plan.scheduled -> "已开启，但系统暂未允许排定 · 检查精确闹钟权限"
                            else -> "关闭中 · 不会自动执行"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.muted,
                        fontSize = 11.5.sp,
                    )
                }
                Switch(
                    checked = plan.scheduled,
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
                    hour = plan.startHour,
                    canDec = plan.startHour > 0,
                    canInc = plan.startHour + 2 <= plan.endHour,
                    modifier = Modifier.weight(1f),
                    onChange = { onWindow(it, plan.endHour) },
                )
                HourStepper(
                    label = "结束",
                    hour = plan.endHour,
                    canDec = plan.endHour - 2 >= plan.startHour,
                    canInc = plan.endHour < 24,
                    modifier = Modifier.weight(1f),
                    onChange = { onWindow(plan.startHour, it) },
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

        GroupLabel("通知")
        Card {
            ToggleRow(
                title = "签到成功后通知",
                note = "关闭后只提醒失败和连续漏签",
                checked = successNotify,
                onChange = onSuccessNotify,
            )
            HorizontalDivider(color = Ink.line, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
            ToggleRow(
                title = "夜间静音",
                note = "${SessionStore.QUIET_START_HOUR}:00 – 次日 ${SessionStore.QUIET_END_HOUR}:00 的成功通知不弹，失败照常",
                checked = nightQuiet,
                onChange = onNightQuiet,
            )
        }

        Spacer(Modifier.height(10.dp))
        Card {
            ToggleRow(title = "最近任务隐藏", note = null, checked = hideRecent, onChange = onToggleHideRecent)
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
            ChevronRow("导出备份", RowGlyph.Export, onClick = {
                shareHistoryJson(context, store.history())
            })
            HorizontalDivider(color = Ink.line, thickness = 1.dp)
            ChevronRow("导入备份", RowGlyph.Import, onClick = {
                pickBackup.launch(arrayOf("*/*"))
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
                        "成功通知 ${if (decoded.successNotify) "开启" else "关闭"} · 夜间静音 ${if (decoded.nightQuiet) "开启" else "关闭"}\n" +
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

    pendingImport?.let { records ->
        GlassDialog(onDismissRequest = { pendingImport = null }) {
            DialogTitle("导入备份")
            Spacer(Modifier.height(8.dp))
            DialogBody(
                "备份里有 ${records.size} 天签到记录。\n\n" +
                    "同一天已有记录会被备份里的版本覆盖，其余不动。",
            )
            DialogActions(
                primary = "导入",
                onPrimary = {
                    store.mergeHistory(records)
                    pendingImport = null
                    onHistoryChanged()
                },
                secondary = "取消",
                onSecondary = { pendingImport = null },
            )
        }
    }

    importMessage?.let { message ->
        GlassDialog(onDismissRequest = { importMessage = null }) {
            DialogTitle("导入备份")
            Spacer(Modifier.height(8.dp))
            DialogBody(message)
            DialogActions(
                primary = "知道了",
                onPrimary = { importMessage = null },
                secondary = null,
            )
        }
    }

    if (selfCheckOpen) {
        SelfCheckDialog(context, onClose = { selfCheckOpen = false })
    }
}
