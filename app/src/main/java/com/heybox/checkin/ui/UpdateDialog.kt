package com.heybox.checkin.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heybox.checkin.BuildConfig
import com.heybox.checkin.data.ApkInstaller
import com.heybox.checkin.data.UpdateInfo
import com.heybox.checkin.data.UpdateSources
import com.heybox.checkin.ui.theme.Ink
import java.io.File
import kotlinx.coroutines.launch

/** 更新弹窗的状态机：下载、等授权、失败各有各的按钮。 */
private sealed interface UpdateStage {
    data object Ready : UpdateStage
    data object Downloading : UpdateStage
    data class Downloaded(val file: File) : UpdateStage

    /** 缺「安装未知应用」权限；[file] 为空说明还没下载（先要权限再下，别白下 8 MB）。 */
    data class NeedPermission(val file: File?) : UpdateStage
    data class Failed(val reason: String) : UpdateStage
}

/**
 * 有新版时的弹窗：应用内直接下载安装，走不通再退回浏览器。
 *
 * [onSkip] 不为空时给一个「跳过这个版本」；[resumeTick] 是宿主页面的 resume 计数——
 * 用户去系统设置给完权限回到 App 时靠它继续，不用再点一次、更不会重复下载。
 */
@Composable
internal fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
    onSkip: (() -> Unit)? = null,
    resumeTick: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 之前下过同一个版本就直接当成已下载：系统重建过 App 也不用重下
    val cached = remember(info.tag) { ApkInstaller.cachedApk(context, info.tag) }
    var stage by remember(info.tag) {
        mutableStateOf<UpdateStage>(cached?.let { UpdateStage.Downloaded(it) } ?: UpdateStage.Ready)
    }
    var progress by remember { mutableStateOf<Float?>(null) }
    // 当前走的线路（直连或某个镜像的域名），探测完才会有值
    var source by remember { mutableStateOf<String?>(null) }
    val cellular = remember { onCellular(context) }

    fun install(file: File) {
        if (!ApkInstaller.canInstall(context)) {
            stage = UpdateStage.NeedPermission(file)
            ApkInstaller.requestPermission(context)
            return
        }
        runCatching { ApkInstaller.install(context, file) }
            .onSuccess { onDismiss() }
            .onFailure { stage = UpdateStage.Failed(describe(it)) }
    }

    fun download() {
        val url = info.apkUrl
        if (url == null) {
            // Release 里没有 APK 附件（比如手动建的 release），只能走浏览器
            openUrl(context, info.url)
            onDismiss()
            return
        }
        stage = UpdateStage.Downloading
        progress = 0f
        source = null
        scope.launch {
            runCatching {
                ApkInstaller.download(
                    context = context,
                    apkUrl = url,
                    shaUrl = info.shaUrl,
                    onSource = { line -> scope.launch { source = UpdateSources.label(line) } },
                    onProgress = { p -> scope.launch { progress = p } },
                )
            }
                .onSuccess { file ->
                    stage = UpdateStage.Downloaded(file)
                    install(file)
                }
                .onFailure { stage = UpdateStage.Failed(describe(it)) }
        }
    }

    /** 点「应用内更新」：先确认能装，再决定要不要下载。 */
    fun start() {
        if (!ApkInstaller.canInstall(context)) {
            stage = UpdateStage.NeedPermission(null)
            ApkInstaller.requestPermission(context)
            return
        }
        val ready = ApkInstaller.cachedApk(context, info.tag)
        if (ready != null) {
            stage = UpdateStage.Downloaded(ready)
            install(ready)
            return
        }
        download()
    }

    // 从「安装未知应用」设置回来：权限有了就接着装/下，不再让用户多点一次
    LaunchedEffect(resumeTick, stage) {
        val waiting = stage as? UpdateStage.NeedPermission ?: return@LaunchedEffect
        if (!ApkInstaller.canInstall(context)) return@LaunchedEffect
        val file = waiting.file
        if (file != null && file.exists()) install(file) else start()
    }

    GlassDialog(onDismissRequest = onDismiss) {
        DialogTitle("有新版 ${info.tag}")
        Spacer(Modifier.height(8.dp))
        DialogBody(
            buildString {
                append("当前版本 ${BuildConfig.VERSION_NAME}")
                info.apkSize?.let { append("，更新包约 ${formatSize(it)}") }
            },
        )

        // 按「新增 / 优化 / 修复」逐条显示；弹窗里最多两条，其余去 Release 页面看
        val shown = remember(info.tag) { info.changes.take(2) }
        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            shown.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        item.section,
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink.accent,
                        fontSize = 10.sp,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        modifier = Modifier
                            // 大字体下让标签自己变宽，别把「新增」压成两行
                            .widthIn(min = 30.dp)
                            .padding(top = 2.dp, end = 8.dp),
                    )
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.muted,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(5.dp))
            }
            if (info.changes.size > shown.size) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "全部 ${info.changes.size} 条改动 →",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.accent,
                    modifier = Modifier.clickable { openUrl(context, info.url) },
                )
            }
        } else {
            // 正文没有分节的旧版日志：退回一段摘要
            info.notes?.let { notes ->
                Spacer(Modifier.height(8.dp))
                Text(
                    notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.muted,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when (val s = stage) {
            UpdateStage.Ready -> {
                DialogBody("可以直接在应用内下载安装，覆盖后登录态和历史都还在。")
                if (cellular) {
                    Spacer(Modifier.height(6.dp))
                    DialogBody("现在用的是移动网络，建议连 WiFi 再下。", color = Ink.accent)
                }
            }

            UpdateStage.Downloading -> {
                val p = progress
                val line = source
                DialogBody(
                    when {
                        line == null -> "正在选择最快线路…"
                        p != null -> "正在从 $line 下载 ${(p * 100).toInt()}%"
                        else -> "正在从 $line 下载…"
                    },
                )
                Spacer(Modifier.height(10.dp))
                if (p != null) {
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth(),
                        color = Ink.accent,
                        trackColor = Ink.raised,
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Ink.accent,
                        trackColor = Ink.raised,
                    )
                }
            }

            is UpdateStage.Downloaded -> DialogBody("已经下好了，点「安装」交给系统安装器。")

            is UpdateStage.NeedPermission -> DialogBody(
                if (s.file == null) {
                    "系统需要你允许「盒签」安装应用。先打开开关，回到 App 会自动接着下载并安装。"
                } else {
                    "已经下好了。打开开关后回到 App 会自动弹出安装，不用重新下载。"
                },
            )

            is UpdateStage.Failed -> DialogBody(
                "更新失败：${s.reason}",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row {
            Text(
                "浏览器下载",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.accent,
                modifier = Modifier.clickable {
                    openUrl(context, info.url)
                    onDismiss()
                },
            )
            if (onSkip != null) {
                Text(
                    " · ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.line,
                )
                Text(
                    "跳过这个版本",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.muted,
                    modifier = Modifier.clickable {
                        onSkip()
                        onDismiss()
                    },
                )
            }
        }

        val primary = when (stage) {
            UpdateStage.Ready -> "应用内更新"
            UpdateStage.Downloading -> "下载中…"
            is UpdateStage.Downloaded -> "安装"
            is UpdateStage.NeedPermission -> "去允许安装"
            is UpdateStage.Failed -> "重试"
        }
        DialogActions(
            primary = primary,
            onPrimary = {
                when (val s = stage) {
                    UpdateStage.Ready, is UpdateStage.Failed -> start()
                    is UpdateStage.Downloaded -> install(s.file)
                    is UpdateStage.NeedPermission -> ApkInstaller.requestPermission(context)
                    UpdateStage.Downloading -> Unit
                }
            },
            secondary = "稍后",
            onSecondary = onDismiss,
            primaryEnabled = stage != UpdateStage.Downloading,
        )
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1024 * 1024) "%.1f MB".format(bytes / 1024.0 / 1024.0) else "${bytes / 1024} KB"

/** 移动网络下给个提醒：8 MB 对流量党不算小。 */
private fun onCellular(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
}