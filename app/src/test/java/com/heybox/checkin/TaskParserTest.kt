package com.heybox.checkin

import com.heybox.checkin.core.SessionExpiredException
import com.heybox.checkin.core.TaskParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 解析层回归：字段来自真实 /task/list_v2/ 的形态，数字有字符串也有数字。 */
class TaskParserTest {

    private val payload = """
        {
          "user": {
            "userid": 12345678,
            "battery": "80",
            "level_info": { "exp": "1500", "coin": 200, "level": "5", "max_exp": "2000" }
          },
          "task_list": [
            { "title": "每日任务", "tasks": [
              { "title": "签到", "state": "waiting", "report_extra": { "task_id": "sign" } },
              { "title": "分享帖子", "state": "finish", "report_extra": { "task_id": 1 } },
              { "title": "分享游戏", "state": "waiting", "report_extra": { "task_id": 19 } },
              { "title": "分享游戏评价", "state": "waiting", "report_extra": { "task_id": "31" } }
            ] }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesDailyTasksAndLevel() {
        val snapshot = TaskParser.snapshot(JSONObject(payload), "12345678")
        assertEquals(1500L, snapshot.exp)
        assertEquals(200L, snapshot.coin)
        assertEquals(80L, snapshot.battery)
        assertEquals(5L, snapshot.level)
        assertEquals(2000L, snapshot.maxExp)
        assertEquals("waiting", snapshot.stateOf("sign"))
        assertEquals("finish", snapshot.stateOf("1"))
        assertEquals("waiting", snapshot.stateOf("19"))
        assertEquals("waiting", snapshot.stateOf("31"))
    }

    @Test
    fun rejectsAccountMismatch() {
        val error = runCatching { TaskParser.snapshot(JSONObject(payload), "99999999") }.exceptionOrNull()
        assertTrue(error is SessionExpiredException)
    }

    @Test
    fun signStateParsesSignedDate() {
        val state = TaskParser.signState(JSONObject("""{"state":"ok","signed_date":"20664"}"""))
        assertEquals("ok", state.state)
        assertEquals(20664L, state.signedDate)
    }
}
