package com.heybox.checkin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heybox.checkin.BuildConfig
import com.heybox.checkin.R
import com.heybox.checkin.data.Diagnostics
import com.heybox.checkin.data.UpdateCheck
import com.heybox.checkin.data.UpdateInfo
import com.heybox.checkin.ui.theme.Ink
import com.heybox.checkin.ui.theme.glass
import com.heybox.checkin.ui.theme.NumberSmall
import kotlinx.coroutines.launch

private const val URL_DAILY = "https://github.com/Cansiny0320/heybox-daily"
private const val URL_CORE = "https://github.com/XiaHouSheng/heybox-core"

/**
 * 「关于」页：只放三件事——这是什么、参考了谁、别拿它干什么。
 * 视觉与首页同一套色板，不引入新的组件风格。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) {
        LicensesScreen(onBack = { showLicenses = false })
        return
    }
    // 关于页是首页里的条件分支，不注册返回回调的话系统返回会直接 finish Activity。
    BackHandler { onBack() }

    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onBack,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text("← 返回", style = MaterialTheme.typography.bodySmall, color = Ink.muted)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "关于",
            style = MaterialTheme.typography.displayLarge,
            color = Ink.ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "盒签 · 小黑盒每日自动签到",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.muted,
        )

        Spacer(Modifier.height(20.dp))
        AboutCard(title = "版本") {
            Text(
                "v${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）",
                style = NumberSmall,
                color = Ink.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "协议层直调官方接口，不跑 Docker、不做无障碍点击；签到数据不经过任何第三方托管，" +
                    "登录时仅加载官方设备指纹组件。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.muted,
            )
            Spacer(Modifier.height(6.dp))
            UpdateRow()
        }

        Spacer(Modifier.height(14.dp))
        AboutCard(title = "参考项目") {
            Text(
                "heybox-daily",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.ink,
                fontWeight = FontWeight.SemiBold,
            )
            LinkLine("github.com/Cansiny0320/heybox-daily", URL_DAILY)
            Text("MIT License", style = MaterialTheme.typography.bodySmall, color = Ink.muted)
            Spacer(Modifier.height(10.dp))
            Text(
                "heybox-core",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.ink,
                fontWeight = FontWeight.SemiBold,
            )
            LinkLine("github.com/XiaHouSheng/heybox-core", URL_CORE)
            Text("MIT License", style = MaterialTheme.typography.bodySmall, color = Ink.muted)
            Spacer(Modifier.height(8.dp))
            Text(
                "本项目的签到 / 分享流程、请求签名与设备指纹实现参考并移植自这两个项目。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.muted,
            )
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = { showLicenses = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    "查看完整开源许可 →",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.accent,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        AboutCard(title = "免责声明") {
            Disclaimer("本工具为非官方客户端，与小黑盒官方无任何关联或授权。")
            Disclaimer("仅供个人学习，请勿用于代签、售卖等商业行为。")
            Disclaimer("因使用本工具产生的账号风险或数据异常，由使用者自行承担。")
        }

        Spacer(Modifier.height(14.dp))
        AboutCard(title = "遇到问题") {
            Text(
                "复制下面这段环境信息发给我，比截图更好定位问题。不含账号凭据。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.muted,
            )
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = {
                    copyToClipboard(context, Diagnostics.report(context))
                    copied = true
                    Toast.makeText(context, "诊断信息已复制", Toast.LENGTH_SHORT).show()
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    if (copied) "已复制 · 再复制一次" else "复制诊断信息",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.accent,
                )
            }
        }

        Spacer(Modifier.height(30.dp))
        Text(
            "By 胡图图",
            style = MaterialTheme.typography.headlineSmall,
            color = Ink.ink,
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun LicensesScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val text = remember {
        runCatching {
            context.resources.openRawResource(R.raw.third_party_notices)
                .bufferedReader()
                .use { it.readText() }
        }.getOrDefault("许可文本读取失败")
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onBack,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text("← 返回", style = MaterialTheme.typography.bodySmall, color = Ink.muted)
        }
        Spacer(Modifier.height(12.dp))
        Text("开源许可", style = MaterialTheme.typography.displayLarge, color = Ink.ink, fontSize = 34.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun LinkLine(label: String, url: String) {
    val context = LocalContext.current
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = Ink.accent,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, url) },
    )
}

/** 「检查更新」的四种结果，避免用一堆布尔量互相打架。 */
private sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Newer(val info: UpdateInfo) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * 版本自检入口（手动）。启动时也会自动查一次，见 HomeScreen；不做后台轮询。
 */
@Composable
private fun UpdateRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    // 有新版本时不直接跳浏览器，先给「应用内更新 / 浏览器」两个选择
    var dialog by remember { mutableStateOf<UpdateInfo?>(null) }

    val check: () -> Unit = {
        scope.launch {
            state = UpdateState.Checking
            state = UpdateCheck.latest().fold(
                onSuccess = { info ->
                    if (UpdateCheck.isNewer(info.tag, BuildConfig.VERSION_NAME)) {
                        UpdateState.Newer(info)
                    } else {
                        UpdateState.UpToDate
                    }
                },
                onFailure = { UpdateState.Failed(describe(it)) },
            )
        }
    }

    // 失败文案可能很长（例如 DNS 报错），单独占一行，别把后面的下载链接挤出屏幕。
    Column {
        when (val s = state) {
            UpdateState.Idle -> LinkAction("检查更新", action = check)
            UpdateState.Checking -> Text(
                "正在检查…",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.muted,
            )

            UpdateState.UpToDate -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("已是最新版本", style = MaterialTheme.typography.bodySmall, color = Ink.muted)
                LinkAction("再查一次", action = check)
            }

            is UpdateState.Newer -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("有新版本 ${s.info.tag}", style = MaterialTheme.typography.bodySmall, color = Ink.accent)
                LinkAction("更新 →") { dialog = s.info }
            }

            is UpdateState.Failed -> {
                Text(
                    "检查失败：${s.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.muted,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LinkAction("重试", action = check)
                    LinkAction("打开下载页 →") { openUrl(context, UpdateCheck.RELEASES_URL) }
                }
            }
        }

        dialog?.let { info ->
            UpdateDialog(info = info, onDismiss = { dialog = null })
        }
    }
}

@Composable
private fun LinkAction(text: String, action: () -> Unit) {
    TextButton(
        onClick = action,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Ink.accent)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("盒签诊断信息", text))
}

@Composable
private fun AboutCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Disclaimer(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("·", color = Ink.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.padding(horizontal = 3.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
        )
    }
}
