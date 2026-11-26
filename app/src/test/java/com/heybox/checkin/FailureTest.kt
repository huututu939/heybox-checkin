package com.heybox.checkin

import com.heybox.checkin.core.HeyboxApiException
import com.heybox.checkin.core.HeyboxHttpException
import com.heybox.checkin.core.SessionExpiredException
import com.heybox.checkin.data.FailureKind
import com.heybox.checkin.data.classifyFailure
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureTest {

    @Test
    fun `登录过期算登录类，提示去重登`() {
        val failure = classifyFailure(SessionExpiredException())
        assertEquals(FailureKind.SESSION, failure.kind)
        assertTrue(failure.hint.contains("重新登录"))
    }

    @Test
    fun `断网和超时都算网络类`() {
        assertEquals(FailureKind.NETWORK, classifyFailure(UnknownHostException("api.xiaoheihe.cn")).kind)
        assertEquals(FailureKind.NETWORK, classifyFailure(SocketTimeoutException("timeout")).kind)
    }

    @Test
    fun `人机验证单独一类，且不提示重试`() {
        val failure = classifyFailure(HeyboxApiException("/task/sign_v3/sign", "show_captcha", ""))
        assertEquals(FailureKind.CAPTCHA, failure.kind)
        assertTrue(failure.hint.contains("手动"))
    }

    @Test
    fun `接口返回太频繁算限流`() {
        val failure = classifyFailure(HeyboxApiException("/task/list_v2/", "failed", "请求过于频繁"))
        assertEquals(FailureKind.RATE_LIMIT, failure.kind)
    }

    @Test
    fun `HTTP 5xx 算上游异常，401 算登录过期`() {
        assertEquals(FailureKind.SERVER, classifyFailure(HeyboxHttpException(500, "")).kind)
        assertEquals(FailureKind.SESSION, classifyFailure(HeyboxHttpException(401, "")).kind)
        assertEquals(FailureKind.RATE_LIMIT, classifyFailure(HeyboxHttpException(429, "")).kind)
    }

    @Test
    fun `没核对到账算核对类`() {
        val failure = classifyFailure(IllegalStateException("签到未确认到账，任务已停止"))
        assertEquals(FailureKind.VERIFY, failure.kind)
    }

    @Test
    fun `找不到可分享内容的普通错误不冒充网络问题`() {
        val failure = classifyFailure(IllegalStateException("没有找到可分享的帖子"))
        assertEquals(FailureKind.OTHER, failure.kind)
    }

    @Test
    fun `未知异常也不会丢掉原始信息`() {
        val failure = classifyFailure(RuntimeException("boom"))
        assertEquals(FailureKind.OTHER, failure.kind)
        assertEquals("boom", failure.message)
    }

    @Test
    fun `分类 key 能反解回枚举，旧记录缺失时落到其它`() {
        assertEquals(FailureKind.NETWORK, FailureKind.from("network"))
        assertEquals(FailureKind.OTHER, FailureKind.from(null))
        assertEquals(FailureKind.OTHER, FailureKind.from("不存在的分类"))
    }
}
