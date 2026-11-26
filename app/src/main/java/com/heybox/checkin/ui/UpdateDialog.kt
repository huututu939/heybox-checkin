package com.heybox.checkin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.heybox.checkin.BuildConfig
import com.heybox.checkin.data.ApkInstaller
import com.heybox.checkin.data.UpdateInfo
import com.heybox.checkin.data.UpdateSources
import com.heybox.checkin.ui.theme.Ink
import java.io.File
import kotlinx.coroutines.launch

/** 更新弹窗的状态机：下载、等待授权、失败都各有各的按钮。 */
private sealed interface UpdateStage {
    data object Ready : UpdateStage
    data object Downloading : UpdateStage
    data class Downloaded(val file: File) : UpdateStage
    data class NeedPermission(val file: File) : UpdateStage
    data class Failed(val reason: String) : UpdateStage
}

/**
 * 有新版时的弹窗：应用内直接下载安装，走不通再退回浏览器。
 *
 * 自动检查只在打开 App 时跑一次（见 HomeScreen），也复用在这里给「关于 → 检查更新」。
 */
@Composable
internal fun UpdateDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf<UpdateStage>(UpdateStage.Ready) }
    var progress by remember { mutableStateOf<Float?>(null) }
    // 当前走的线路（直连或某个镜像的域名），探测完才会有值
    var source by remember { mutableStateOf<String?>(null) }

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

    GlassDialog(onDismissRequest = onDismiss) {
        DialogTitle("有新版 ${info.tag}")
        Spacer(Modifier.height(8.dp))
        DialogBody("当前版本 ${BuildConfig.VERSION_NAME}")
        Spacer(Modifier.height(12.dp))

        when (val s = stage) {
            UpdateStage.Ready -> DialogBody("可以直接在应用内下载安装，覆盖后登录态和历史都还在。")

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

            is UpdateStage.Downloaded -> DialogBody("已下载，点「安装」交给系统安装器。")

            is UpdateStage.NeedPermission -> DialogBody(
                "已经下好了。系统需要你允许「盒签」安装应用：在设置里打开开关后回来点「继续安装」。",
            )

            is UpdateStage.Failed -> DialogBody(
                "更新失败：${s.reason}",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "或者用浏览器下载 →",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.accent,
            modifier = Modifier.clickable {
                openUrl(context, info.url)
                onDismiss()
            },
        )

        val primary = when (stage) {
            UpdateStage.Ready -> "应用内更新"
            UpdateStage.Downloading -> "下载中…"
            is UpdateStage.Downloaded -> "安装"
            is UpdateStage.NeedPermission -> "继续安装"
            is UpdateStage.Failed -> "重试"
        }
        DialogActions(
            primary = primary,
            onPrimary = {
                when (val s = stage) {
                    UpdateStage.Ready, is UpdateStage.Failed -> download()
                    is UpdateStage.Downloaded -> install(s.file)
                    is UpdateStage.NeedPermission -> install(s.file)
                    UpdateStage.Downloading -> Unit
                }
            },
            secondary = "稍后",
            onSecondary = onDismiss,
            primaryEnabled = stage != UpdateStage.Downloading,
        )
    }
}
