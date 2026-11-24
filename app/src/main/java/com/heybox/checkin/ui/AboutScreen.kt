package com.heybox.checkin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heybox.checkin.BuildConfig
import com.heybox.checkin.R
import com.heybox.checkin.data.Diagnostics
import com.heybox.checkin.ui.theme.Ink
import com.heybox.checkin.ui.theme.NumberSmall

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
        Text(
            "关于",
            style = MaterialTheme.typography.displayLarge,
            color = Ink.ink,
            fontSize = 40.sp,
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
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.onFailure {
                    Toast.makeText(context, "没有可打开链接的应用", Toast.LENGTH_SHORT).show()
                }
            },
    )
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
            .background(Ink.surface, RoundedCornerShape(18.dp))
            .border(1.dp, Ink.line, RoundedCornerShape(18.dp))
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
