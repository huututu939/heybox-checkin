package com.heybox.checkin.data

import com.heybox.checkin.core.DeviceTokenInvalidException
import com.heybox.checkin.core.HeyboxApiException
import com.heybox.checkin.core.HeyboxHttpException
import com.heybox.checkin.core.SessionExpiredException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 失败按「用户接下来能做什么」分类，而不是按异常类型分。
 *
 * 签到失败最怕的是「不知道怎么回事、也不知道要不要管」：网络问题等下一次就好，
 * 登录过期必须自己去重登，人机验证是自动签到永远过不去的一道墙。这三件事在通知和
 * 记录页里必须长得不一样，否则用户只能猜。
 */
enum class FailureKind(val key: String, val label: String) {
    /** 断网、DNS、超时、连接被重置：多半是环境问题，等自动重试。 */
    NETWORK("network", "网络"),

    /** 小黑盒 5xx 或接口返回失败：上游问题，等重试。 */
    SERVER("server", "服务"),

    /** 登录态失效：不重登会一直失败。 */
    SESSION("session", "登录"),

    /** 命中人机验证：自动签到过不去，只能人工处理。 */
    CAPTCHA("captcha", "验证"),

    /** 请求太频繁：继续重试只会更糟。 */
    RATE_LIMIT("rate_limit", "限流"),

    /** 请求发出去了，但没核对到账：状态不明，稍后复查。 */
    VERIFY("verify", "核对"),

    OTHER("other", "其它");

    companion object {
        fun from(key: String?): FailureKind = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** [message] 进记录与通知正文；[hint] 是给用户看的下一步。 */
data class Failure(
    val kind: FailureKind,
    val message: String,
    val hint: String,
)

/**
 * 把签到过程中的异常翻译成失败分类。
 *
 * 注意顺序：`SSLException` / `SocketTimeoutException` 都是 `IOException`，具体类型必须排在
 * `IOException` 前面；`SessionExpiredException` 也要先于通用分支，否则会被当成普通失败。
 */
fun classifyFailure(error: Throwable): Failure = when (error) {
    is SessionExpiredException, is DeviceTokenInvalidException -> Failure(
        FailureKind.SESSION,
        error.message ?: "登录已过期",
        "打开 App 重新登录，之前的记录不会丢",
    )

    is UnknownHostException -> Failure(FailureKind.NETWORK, "域名解析失败", "检查手机网络，等下次自动重试")
    is SocketTimeoutException -> Failure(FailureKind.NETWORK, "连接超时", "网络太慢或被拦截，等下次自动重试")
    is ConnectException -> Failure(FailureKind.NETWORK, "连不上服务器", "检查手机网络，等下次自动重试")
    is NoRouteToHostException -> Failure(FailureKind.NETWORK, "网络不可达", "检查手机网络，等下次自动重试")

    is HeyboxApiException -> when {
        error.captchaRequired -> Failure(
            FailureKind.CAPTCHA,
            "要求人机验证",
            "自动签到过不去这一关，需要打开小黑盒手动签一次",
        )

        error.rateLimited -> Failure(
            FailureKind.RATE_LIMIT,
            "请求太频繁",
            "今天先别手动重试，明天会自动继续",
        )

        else -> Failure(FailureKind.SERVER, error.message ?: "接口未成功", "稍后会自动重试")
    }

    is HeyboxHttpException -> when {
        error.status == 401 -> Failure(FailureKind.SESSION, "登录已过期", "打开 App 重新登录")
        error.status == 429 -> Failure(FailureKind.RATE_LIMIT, "请求太频繁", "今天先别手动重试，明天会自动继续")
        error.status in 500..599 -> Failure(FailureKind.SERVER, "小黑盒服务器 ${error.status}", "上游异常，稍后会自动重试")
        error.status == 403 -> Failure(FailureKind.SERVER, "请求被拒绝", "可能被风控挡了，先停一天再试")
        else -> Failure(FailureKind.SERVER, "HTTP ${error.status}", "稍后会自动重试")
    }

    is IllegalStateException -> {
        val text = error.message ?: ""
        if (text.contains("未确认") || text.contains("未结算") || text.contains("状态")) {
            Failure(FailureKind.VERIFY, text, "请求发出去了但没核对到账，10 分钟后会自动复查")
        } else {
            Failure(FailureKind.OTHER, text.ifEmpty { "执行中断" }, "可以点「立即重试」再跑一次")
        }
    }

    is IOException -> Failure(FailureKind.NETWORK, "网络中断", "检查手机网络，等下次自动重试")

    else -> Failure(
        FailureKind.OTHER,
        error.message ?: error.javaClass.simpleName,
        "可以点「立即重试」再跑一次",
    )
}
