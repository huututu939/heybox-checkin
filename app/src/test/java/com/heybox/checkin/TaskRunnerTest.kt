package com.heybox.checkin

import com.heybox.checkin.core.AppInfo
import com.heybox.checkin.core.DeviceInfo
import com.heybox.checkin.core.HeyboxClient
import com.heybox.checkin.core.HeyboxConfig
import com.heybox.checkin.core.PreparedRequest
import com.heybox.checkin.core.SessionExpiredException
import com.heybox.checkin.core.SessionInfo
import com.heybox.checkin.core.TaskParser
import com.heybox.checkin.core.TaskRunner
import com.heybox.checkin.core.Transport
import kotlin.math.floor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编排层的端到端单测：用假 Transport 把「已签 / 完整跑一遍 / 签到不确认 / 分享不结算 /
 * 账号不匹配」都走一遍。真实停顿通过 `pause` 注入空实现，测试不会真的等几十秒。
 */
class TaskRunnerTest {

    private val account = "100000000"
    private val today = floor((System.currentTimeMillis() / 1000.0 + 8 * 3600) / 86400).toLong()

    private fun client(api: Transport) = HeyboxClient(
        HeyboxConfig(
            session = SessionInfo(heyboxId = account, pkey = "pkey", deviceId = "device"),
            device = DeviceInfo(model = "test", osVersion = "17"),
            app = AppInfo(),
        ),
        api,
    )

    private fun ok(result: String) = """{"status":"ok","result":$result}"""

    /** 有状态的假接口：签到后余额上涨，分享上报三次后对应任务变成 finish。 */
    private inner class FakeApi(
        private val signNeverConfirms: Boolean = false,
        private val shareNeverSettles: Boolean = false,
        private val wrongUser: Boolean = false,
    ) : Transport {
        var signed = false
        var signCalls = 0
        var reportCalls = 0
        private val finishedShares = mutableSetOf<String>()
        private var shareIndex = 0
        private var reportsForShare = 0

        override fun execute(request: PreparedRequest): String {
            val url = request.url
            return when {
                "sign_v4/get_sign_state" in url -> ok(
                    """{"state":"${if (signed && !signNeverConfirms) "ok" else "no"}","signed_date":$today}""",
                )

                "sign_v3/sign" in url -> {
                    signCalls++
                    if (!signNeverConfirms) signed = true
                    ok("{}")
                }

                "/task/list_v2/" in url -> ok(tasks())

                "/account/data_report/" in url -> {
                    reportCalls++
                    reportsForShare++
                    // 每个分享是 show / tap / success 三次上报
                    if (reportsForShare == 3) {
                        if (!shareNeverSettles) finishedShares += TaskParser.SHARE_IDS[shareIndex]
                        shareIndex++
                        reportsForShare = 0
                    }
                    ok("{}")
                }

                "/bbs/app/feeds" in url -> ok("""{"links":[{"linkid":"11","h_src":"post"}]}""")
                "/game/all_recommend/v2" in url -> ok("""{"list":[{"appid":"22","h_src":"game"}]}""")
                "/bbs/app/link/game/comments" in url -> ok("""{"links":[{"linkid":"33","userid":"44"}]}""")
                "/game/comment/share_data/" in url -> ok("""{"comment_detail":{"linkid":"55"}}""")
                else -> throw IllegalStateException("unexpected url: $url")
            }
        }

        private fun tasks(): String {
            fun state(id: String) = when {
                id == "sign" -> if (signed) "finish" else "waiting"
                id in finishedShares -> "finish"
                else -> "waiting"
            }
            val userid = if (wrongUser) "999" else account
            val exp = if (signed) 100 else 10
            val coin = if (signed) 300 else 100
            return """{
                "user":{"userid":"$userid","battery":5,"level_info":{"exp":$exp,"coin":$coin,"level":1,"max_exp":100}},
                "task_list":[{"title":"每日任务","tasks":[
                    {"report_extra":{"task_id":"sign"},"state":"${state("sign")}"},
                    {"report_extra":{"task_id":"1"},"state":"${state("1")}"},
                    {"report_extra":{"task_id":"19"},"state":"${state("19")}"},
                    {"report_extra":{"task_id":"31"},"state":"${state("31")}"}
                ]}]
            }"""
        }
    }

    private fun runner(api: FakeApi, shares: Set<String> = TaskParser.SHARE_IDS.toSet()) =
        TaskRunner(client(api), account, enabledShares = shares, pause = {})

    @Test
    fun `已签到且分享全关时只做核对`() {
        val api = FakeApi().apply { signed = true }
        val report = runBlocking { runner(api, shares = emptySet()).runDaily() }

        assertEquals("skipped", report.execution["sign"])
        assertEquals(0, api.signCalls)
        assertEquals(0, api.reportCalls)
        assertEquals(0L, report.expDelta)
    }

    @Test
    fun `完整跑一遍签到和三个分享`() {
        val api = FakeApi()
        val report = runBlocking { runner(api).runDaily() }

        assertEquals(1, api.signCalls)
        // 三个分享各 show / tap / success 三次上报
        assertEquals(9, api.reportCalls)
        assertEquals("executed", report.execution["sign"])
        assertEquals("executed", report.execution["1"])
        assertEquals("executed", report.execution["19"])
        assertEquals("executed", report.execution["31"])
        assertEquals(90L, report.expDelta)
        assertEquals(200L, report.coinDelta)
        assertTrue(report.snapshot.tasks.all { it.state == "finish" })
    }

    @Test
    fun `签到未确认到账就停止`() {
        val api = FakeApi(signNeverConfirms = true)
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { runner(api, shares = emptySet()).runDaily() }
        }
        assertTrue(error.message.orEmpty().contains("签到未确认到账"))
        assertEquals(0, api.reportCalls)
    }

    @Test
    fun `分享上报后未结算就停止`() {
        val api = FakeApi(shareNeverSettles = true)
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { runner(api).runDaily() }
        }
        assertTrue(error.message.orEmpty().contains("未结算"))
    }

    @Test
    fun `账号不匹配按登录过期处理`() {
        val api = FakeApi(wrongUser = true)
        assertThrows(SessionExpiredException::class.java) {
            runBlocking { runner(api).inspect() }
        }
    }
}
