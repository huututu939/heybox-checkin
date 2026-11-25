package com.heybox.checkin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heybox.checkin.BuildConfig
import com.heybox.checkin.ui.theme.Ink
import com.heybox.checkin.ui.theme.glass
import com.heybox.checkin.ui.theme.NumberSmall

const val NOTICE_VERSION = 1

/**
 * 首次启动的风险与数据说明。不是可勾选的「用户协议」，只是把边界讲清楚，
 * 点「开始使用」后写入本地标记，以后不再拦。
 */
@Composable
fun WelcomeScreen(onAccept: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 24.dp),
    ) {
        Text("盒签", style = MaterialTheme.typography.labelSmall, color = Ink.muted)
        Spacer(Modifier.height(10.dp))
        Text("开始之前", style = MaterialTheme.typography.displayLarge, color = Ink.ink)
        Spacer(Modifier.height(10.dp))
        Text(
            "三件事，看完再决定要不要用。",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.muted,
        )

        Spacer(Modifier.height(26.dp))
        NoticeBlock(
            index = "01",
            title = "它替你做什么",
            body = "每天在设定的时段里挑一个随机时间，自动完成小黑盒签到。签到固定执行；" +
                "分享任务默认全部关闭，要的话到「今天」页逐个打开。",
        )
        Spacer(Modifier.height(12.dp))
        NoticeBlock(
            index = "02",
            title = "数据留在哪",
            body = "手机号、pkey 与设备标识只加密存在这台手机上；签到请求直接发往小黑盒官方接口。" +
                "登录时会加载官方设备指纹组件（static.portal101.cn，源码带 SHA-256 校验）；" +
                "没有中转服务器、没有统计 SDK，也不读取通讯录、相册与位置。",
        )
        Spacer(Modifier.height(12.dp))
        NoticeBlock(
            index = "03",
            title = "你要承担的",
            body = "这是非官方工具，与官方无任何关联或授权。账号被风控、奖励异常的风险由使用者自行承担；" +
                "请勿用于代签、售卖。若要发给别人，请自行确认平台协议与当地法规。",
        )

        Spacer(Modifier.height(26.dp))
        Text(
            "登录后建议到「设置 → 值守条件」把自启动、电池白名单和精确闹钟都打开，" +
                "否则国产系统会在后台把定时任务清掉。",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
        )

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onAccept,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Ink.accent,
                contentColor = Ink.onAccent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("我已了解，开始使用", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "继续即表示你已阅读并理解以上说明 · v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = Ink.muted,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NoticeBlock(index: String, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row {
            Text(index, style = NumberSmall, color = Ink.accent, fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.ink,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = Ink.muted)
    }
}
