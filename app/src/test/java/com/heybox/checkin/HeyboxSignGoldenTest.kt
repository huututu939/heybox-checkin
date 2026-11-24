package com.heybox.checkin

import com.heybox.checkin.core.HeyboxSign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用上游 test/fixtures/native-v14.json 的 24 组黄金向量校验签名移植。
 * 只依赖 hkey / _rnd / _time 三个可复现值，nonce 是随机的，只校验形状。
 * 故意用轻量正则解析而不是 org.json：Android 单元测试里 android.jar 的 org.json 是 stub。
 */
class HeyboxSignGoldenTest {

    private val fixture: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("native-v14.json")!!
            .readBytes()
            .toString(Charsets.UTF_8)
    }

    @Test
    fun nativeSignatureMatchesGoldenVectors() {
        val blocks = fixture.split(Regex("\"id\": ?\""))
        var checked = 0
        for (block in blocks.drop(1)) {
            val path = field(block, "path") ?: continue
            val debugInfo = field(block, "debugInfo") ?: error("缺少 debugInfo")
            val time = field(block, "time") ?: error("缺少 time")
            val osVersion = field(block, "osVersion") ?: error("缺少 osVersion")
            val version = field(block, "version") ?: HeyboxSign.APP_VERSION
            val expectedTime = field(block, "_time") ?: error("缺少 _time")
            val expectedRnd = field(block, "_rnd") ?: error("缺少 _rnd")
            val expectedHkey = field(block, "hkey") ?: error("缺少 hkey")

            // fixture 的 path 是直接喂给 signNative 的原始路径，不做归一化（上游分层如此）
            val signature = HeyboxSign.nativeSignature(
                path = path,
                timeSeconds = time.toLong(),
                debugInfo = debugInfo,
                osVersion = osVersion,
                version = version,
            )

            assertEquals("_time @${path}", expectedTime, signature.time)
            assertEquals("hkey @${path}", expectedHkey, signature.hkey)
            assertEquals("_rnd @${path}", expectedRnd, signature.rnd)
            checked++
        }
        assertEquals("fixture 记录数", 24, checked)
    }

    @Test
    fun nonceLooksLikeUpstream() {
        val signature = HeyboxSign.nativeSignature(
            path = "/account/login_code/",
            timeSeconds = 1793040000L,
            debugInfo = "offline-recovery-probe-1",
            osVersion = "17",
        )
        assertEquals(32, signature.nonce.length)
        assertTrue(signature.nonce.all { it.isLetterOrDigit() })
    }

    private fun field(block: String, name: String): String? {
        val quoted = Regex("\"$name\": ?\"([^\"]*)\"").find(block)?.groupValues?.get(1)
        return quoted
    }
}
