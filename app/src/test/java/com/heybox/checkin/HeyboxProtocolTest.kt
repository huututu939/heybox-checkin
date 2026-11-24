package com.heybox.checkin

import com.heybox.checkin.core.AppInfo
import com.heybox.checkin.core.DeviceInfo
import com.heybox.checkin.core.HeyboxClient
import com.heybox.checkin.core.HeyboxConfig
import com.heybox.checkin.core.HeyboxCrypto
import com.heybox.checkin.core.LoginApi
import com.heybox.checkin.core.SessionInfo
import com.heybox.checkin.core.TaskParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyboxProtocolTest {

    private val config = HeyboxConfig(
        session = SessionInfo(heyboxId = "12345678", pkey = "pkey-value", deviceId = "device-uuid", tokenId = "Btoken"),
        device = DeviceInfo(model = "PKJ110", osVersion = "17"),
        app = AppInfo(),
    )

    @Test
    fun androidBase64WrapsAt76() {
        val text = HeyboxCrypto.androidBase64(ByteArray(57) { 7 })
        val lines = text.split("\n")
        assertTrue("末尾要有换行", text.endsWith("\n"))
        assertEquals("76 字符一行 + 空尾段", 2, lines.size)
        assertEquals(76, lines[0].length)
        assertEquals("", lines[1])
    }

    @Test
    fun rsaEncryptProduces128ByteCiphertext() {
        val encrypted = HeyboxCrypto.encryptPhone("+8613800138000")
        val joined = encrypted.replace("\n", "")
        assertEquals(172, joined.length) // 128 字节 → base64 172 字符
        assertEquals(128, java.util.Base64.getMimeDecoder().decode(encrypted).size)
    }

    @Test
    fun reportPayloadRoundTripsAndMatchesSid() {
        val eventsJson = """{"events":[{"type":"3","path":"/share/behavior/show","time":"1793040000","addition":{"src":"link","link_id":"1","h_src":"abc","h_session_id":"s"}}]}"""
        val payload = HeyboxCrypto.encryptReportWithKey(eventsJson, "0123456789abcdef", "1793040000")
        val decrypted = HeyboxCrypto.decryptReportForTest(payload.data, "0123456789abcdef")
        assertEquals(eventsJson, decrypted)
        assertEquals(
            HeyboxCrypto.md5Hex(payload.key + "1793040000") + HeyboxCrypto.md5Hex(payload.data),
            payload.sid,
        )
    }

    @Test
    fun nativeRequestCarriesSignatureAndCookie() {
        val request = HeyboxClient(config).prepareNative("/task/sign_v3/sign")
        assertTrue(request.url.startsWith("https://api.xiaoheihe.cn/task/sign_v3/sign?"))
        for (key in listOf("hkey=", "_time=", "nonce=", "_rnd=", "imei=", "heybox_id=")) {
            assertTrue("缺少参数 $key", request.url.contains(key))
        }
        assertEquals("pkey=pkey-value; x_xhh_tokenid=Btoken", request.headers["Cookie"])
        assertEquals("http://api.maxjia.com/", request.headers["Referer"])
        assertTrue(request.headers["User-Agent"]!!.endsWith("ApiMaxJia/1.0"))
    }

    @Test
    fun webRequestUsesWebHeadersAndHost() {
        val request = HeyboxClient(config).prepareWeb("/game/all_recommend/v2")
        assertTrue(request.url.startsWith("https://api.xiaoheihe.cn/game/all_recommend/v2?"))
        assertEquals("https://web.xiaoheihe.cn", request.headers["Origin"])
        assertEquals("com.max.xiaoheihe", request.headers["X-Requested-With"])
        assertEquals(
            "pkey=pkey-value;x_xhh_tokenid=Btoken;x_pkey=pkey-value;x_heybox_id=12345678",
            request.headers["Cookie"],
        )
    }

    @Test
    fun phoneNormalizationMatchesUpstream() {
        assertEquals("+8613800138000", LoginApi.normalizePhone("13800138000"))
        assertEquals("+8613800138000", LoginApi.normalizePhone("+8613800138000"))
        assertTrue(runCatching { LoginApi.normalizePhone("2380013800") }.isFailure)
    }

    @Test
    fun shareEventShape() {
        val json = TaskParser.shareEventJson(
            "tap",
            com.heybox.checkin.core.ShareContent.Link("123", "h_src_value"),
            "session-1",
            1793040000L,
        )
        assertTrue(json.contains("\"type\":\"4\""))
        assertTrue(json.contains("/share/behavior/tap"))
        assertTrue(json.contains("\"plat\":\"WechatSession\""))
        assertTrue(json.contains("\"h_session_id\":\"session-1\""))
    }

    @Test
    fun signedTodayUsesBeijingDay() {
        val day = kotlin.math.floor((1793040000.0 + 8 * 3600) / 86400).toLong()
        val state = com.heybox.checkin.core.SignState("ok", day)
        assertTrue(TaskParser.signedToday(state, 1793040000_000L))
        assertTrue(!TaskParser.signedToday(com.heybox.checkin.core.SignState("ok", day - 1), 1793040000_000L))
    }
}
