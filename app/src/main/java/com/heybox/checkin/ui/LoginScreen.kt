package com.heybox.checkin.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heybox.checkin.core.HeyboxConfig
import com.heybox.checkin.data.LoginFlow
import com.heybox.checkin.data.SessionStore
import com.heybox.checkin.data.UpdateCheck
import com.heybox.checkin.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 登录页：先说明「这台机器只发码、你回主手机取码」，再按 01 / 02 两步走。
 * 视觉上只用一处强调色（品牌小方块、主按钮与状态点），其余靠字重与留白分层。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    context: Context,
    store: SessionStore,
    onLoggedIn: (HeyboxConfig) -> Unit,
    previewCode: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val flow = remember { LoginFlow(context, store) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var codeSent by remember { mutableStateOf(previewCode) }
    val scroll = rememberScrollState()
    val codeRequester = remember { BringIntoViewRequester() }
    var codeFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val submitLogin: () -> Unit = submit@{
        if (busy || code.isEmpty() || !codeSent) return@submit
        keyboard?.hide()
        busy = true
        message = "登录中…"
        scope.launch {
            runCatching { flow.verify(code) }
                .onSuccess {
                    message = ""
                    onLoggedIn(it)
                }
                .onFailure { message = "登录失败：${describe(it)}" }
            busy = false
        }
    }

    // 键盘弹起来时把验证码框顶进可视区：只加 imePadding 不够，
    // TextField 自己不会滚动外层 Column，这里显式滚一次。
    LaunchedEffect(codeFocused) {
        if (codeFocused) {
            delay(120)
            codeRequester.bringIntoView()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(scroll)
            .padding(horizontal = 22.dp, vertical = 24.dp),
    ) {
        BrandMark()
        Spacer(Modifier.height(26.dp))
        Text("盒签", style = MaterialTheme.typography.displayLarge, color = Ink.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "小黑盒每日签到，交给这台备用机。\n设好一次，之后只在通知里看结果。",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.muted,
        )

        Spacer(Modifier.height(38.dp))
        StepHeader("01", "手机号")
        Spacer(Modifier.height(12.dp))
        DarkField(
            value = phone,
            onValueChange = { phone = it.filter { ch -> ch.isDigit() || ch == '+' } },
            label = "接收短信的手机号",
            enabled = !busy,
            keyboardType = KeyboardType.Phone,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "这台机器没有卡。点获取验证码后，用主手机收短信，再把 6 位数字填到第二步。",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
        )
        Spacer(Modifier.height(14.dp))
        AccentButton(
            text = "获取验证码",
            enabled = !busy && phone.length >= 6,
            onClick = {
                busy = true
                message = "正在获取设备令牌并请求短信…"
                scope.launch {
                    runCatching { flow.requestCode(phone) }
                        .onSuccess {
                            message = it
                            codeSent = true
                        }
                        .onFailure { message = "发送失败：${describe(it)}" }
                    busy = false
                }
            },
        )

        Spacer(Modifier.height(30.dp))
        StepHeader("02", "短信验证码")
        Spacer(Modifier.height(12.dp))
        Box(Modifier.bringIntoViewRequester(codeRequester)) {
            DarkField(
                value = code,
                onValueChange = { code = it.filter { ch -> ch.isDigit() }.take(8) },
                label = "6 位数字",
                enabled = !busy && codeSent,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                onImeDone = submitLogin,
                onFocusChanged = { codeFocused = it },
            )
        }
        Spacer(Modifier.height(14.dp))
        AccentButton(
            text = "登录这台设备",
            enabled = !busy && code.isNotEmpty() && codeSent,
            onClick = submitLogin,
        )

        if (busy) {
            Spacer(Modifier.height(22.dp))
            LinearProgressIndicator(
                color = Ink.accent,
                trackColor = Ink.line,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text("首次登录要跑一次官方设备指纹，十几秒内别退出", style = MaterialTheme.typography.bodySmall, color = Ink.muted)
        }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            StatusLine(message)
        }
        // 设备令牌 / 版本类失败在 App 内无法自救，给一条能直接走到下载页的路。
        if (UpdateCheck.looksOutdated(message)) {
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = { openUrl(context, UpdateCheck.RELEASES_URL) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    "大概率是包太旧了，去下载页看看 →",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink.accent,
                )
            }
        }

        Spacer(Modifier.height(40.dp))
        Text(
            "登录会把手机端小黑盒挤下线一次，重新登录手机端即可。",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
        )
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Ink.accent, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("盒", style = MaterialTheme.typography.titleLarge, color = Ink.onAccent)
        }
        Column {
            Text("HEYBOX DAILY", style = MaterialTheme.typography.labelSmall, color = Ink.muted, fontSize = 10.sp)
            Spacer(Modifier.height(3.dp))
            Text("备用机值守 · 自建", style = MaterialTheme.typography.bodySmall, color = Ink.ink)
        }
    }
}

@Composable
private fun StepHeader(index: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(index, style = MaterialTheme.typography.labelSmall, color = Ink.accent, fontSize = 11.sp)
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(1.dp)
                .background(Ink.line),
        )
        Text(title, style = MaterialTheme.typography.labelSmall, color = Ink.muted)
    }
}

@Composable
private fun AccentButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
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
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusLine(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .background(Ink.accent, RoundedCornerShape(100.dp)),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink.ink)
    }
}

@Composable
private fun DarkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    imeAction: ImeAction = ImeAction.Default,
    onImeDone: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeDone() }),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Ink.surface,
            unfocusedContainerColor = Ink.surface,
            disabledContainerColor = Ink.surface,
            focusedBorderColor = Ink.accent,
            unfocusedBorderColor = Ink.line,
            disabledBorderColor = Ink.line,
            focusedTextColor = Ink.ink,
            unfocusedTextColor = Ink.ink,
            disabledTextColor = Ink.muted,
            focusedLabelColor = Ink.accent,
            unfocusedLabelColor = Ink.muted,
            disabledLabelColor = Ink.muted,
            cursorColor = Ink.accent,
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 1.sp),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChanged(it.isFocused) },
    )
}
