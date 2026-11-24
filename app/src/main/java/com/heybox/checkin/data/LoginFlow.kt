package com.heybox.checkin.data

import android.content.Context
import com.heybox.checkin.core.HeyboxClient
import com.heybox.checkin.core.HeyboxConfig
import com.heybox.checkin.core.LoginApi
import com.heybox.checkin.core.TaskParser
import com.heybox.checkin.device.DeviceTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 短信验证码登录。备用机不插卡，所以流程是：这台机发码 → 主手机收码 → 回来填。
 * 全程只需要验证码，不处理人机验证；真被要求了就把上游那句提示直接抛给界面。
 */
class LoginFlow(private val context: Context, private val store: SessionStore) {

    private var pending: HeyboxConfig? = null
    private var pendingPhone: String? = null

    suspend fun requestCode(rawPhone: String): String = withContext(Dispatchers.IO) {
        val phone = LoginApi.normalizePhone(rawPhone)
        val token = store.load()?.session?.tokenId?.takeIf { TOKEN_PATTERN.matches(it) }
            ?: DeviceTokenProvider(context).fetch()
        val config = LoginApi.pendingConfig(
            deviceModel = android.os.Build.MODEL ?: "unknown",
            osVersion = android.os.Build.VERSION.RELEASE ?: "unknown",
            token = token,
            deviceId = java.util.UUID.randomUUID().toString(),
        )
        LoginApi.sendCode(HeyboxClient(config), phone)
        pending = config
        pendingPhone = phone
        "验证码已发送至尾号 ${phone.takeLast(4)}"
    }

    suspend fun verify(code: String): HeyboxConfig = withContext(Dispatchers.IO) {
        val config = pending ?: throw IllegalStateException("请先获取验证码")
        val phone = pendingPhone ?: throw IllegalStateException("请先获取验证码")
        val client = HeyboxClient(config)
        val result = client.unwrap("/account/login_code/", LoginApi.verifyCode(client, phone, code))

        val account = result.optJSONObject("profile")?.opt("heybox_id")?.toString().orEmpty()
        val userid = result.optJSONObject("account_detail")?.opt("userid")?.toString().orEmpty()
        val pkey = result.optString("pkey")
        if (!account.all { it.isDigit() } || account.isEmpty() || account != userid || pkey.isEmpty()) {
            throw IllegalStateException("登录响应中的账号或凭据无效，原配置已保留")
        }

        val authenticated = config.copy(session = config.session.copy(heyboxId = account, pkey = pkey))
        // 登录时只校验账号与基础结构，不要求分享任务存在：分享默认关闭，不能挡住纯签到。
        TaskParser.snapshot(HeyboxClient(authenticated).tasksResult(), account, emptySet())
        store.save(authenticated)
        pending = null
        pendingPhone = null
        authenticated
    }

    fun cancel() {
        pending = null
        pendingPhone = null
    }

    private companion object {
        val TOKEN_PATTERN = Regex("^B[A-Za-z0-9+/=_-]{88}$")
    }
}
