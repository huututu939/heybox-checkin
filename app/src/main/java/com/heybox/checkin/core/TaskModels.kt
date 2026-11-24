package com.heybox.checkin.core

import org.json.JSONArray
import org.json.JSONObject

data class DailyTask(val id: String, val title: String, val state: String)

data class TaskSnapshot(
    val tasks: List<DailyTask>,
    val exp: Long,
    val coin: Long,
    val battery: Long?,
    val level: Long?,
    val maxExp: Long?,
) {
    fun stateOf(id: String): String? = tasks.firstOrNull { it.id == id }?.state
}

data class SignState(val state: String, val signedDate: Long?)

data class Inspection(val snapshot: TaskSnapshot, val signed: Boolean)

/** 分享素材，对应上游三种 src。 */
sealed class ShareContent {
    abstract val src: String

    data class Link(val linkId: String, val hSrc: String) : ShareContent() {
        override val src = "link"
    }

    data class GameDetail(val appId: String, val hSrc: String) : ShareContent() {
        override val src = "game_detail"
    }

    data class GameComment(val linkId: String) : ShareContent() {
        override val src = "game_comment"
    }
}

data class GameCandidate(val appId: String, val hSrc: String)

/**
 * 上游 src/tasks.ts 的解析规则，逐条对齐：
 * - 账号必须与本地 heybox_id 一致，否则视为登录过期
 * - 只取 title 为「每日任务」的分组
 * - 分享任务 1/19/31 必须存在且状态为 waiting 或 finish
 */
object TaskParser {

    val SHARE_IDS = listOf("1", "19", "31")
    val SHARE_NAMES = mapOf("1" to "分享帖子", "19" to "分享游戏", "31" to "分享游戏评价")

    fun snapshot(root: JSONObject, account: String): TaskSnapshot {
        val user = root.optJSONObject("user") ?: throw IllegalStateException("任务列表缺少 user 字段")
        val userid = user.opt("userid")?.toString().orEmpty()
        if (userid != account) throw SessionExpiredException()

        val groups = root.optJSONArray("task_list") ?: throw IllegalStateException("任务列表为空")
        var daily: JSONArray? = null
        for (i in 0 until groups.length()) {
            val group = groups.optJSONObject(i) ?: continue
            if (group.optString("title") == "每日任务") {
                daily = group.optJSONArray("tasks")
                break
            }
        }
        if (daily == null || daily.length() == 0) throw IllegalStateException("每日任务列表为空")

        val tasks = ArrayList<DailyTask>(daily.length())
        for (i in 0 until daily.length()) {
            val item = daily.optJSONObject(i) ?: continue
            val id = item.optJSONObject("report_extra")?.opt("task_id")?.toString() ?: "sign"
            tasks += DailyTask(id = id, title = item.optString("title"), state = item.optString("state"))
        }
        for (id in SHARE_IDS) {
            val task = tasks.firstOrNull { it.id == id }
                ?: throw IllegalStateException("APP 任务 $id 缺失，请检查接口版本")
            if (task.state !in setOf("waiting", "finish")) {
                throw IllegalStateException("APP 任务 $id 状态异常：${task.state}")
            }
        }

        val level = user.optJSONObject("level_info") ?: throw IllegalStateException("任务列表缺少经验或 H 币数据")
        val exp = level.opt("exp").asLong() ?: throw IllegalStateException("任务列表缺少经验数据")
        val coin = level.opt("coin").asLong() ?: throw IllegalStateException("任务列表缺少 H 币数据")

        return TaskSnapshot(
            tasks = tasks,
            exp = exp,
            coin = coin,
            battery = user.opt("battery").nonNegativeLong(),
            level = level.opt("level").nonNegativeLong(),
            maxExp = level.opt("max_exp").nonNegativeLong(minimum = 1),
        )
    }

    fun signState(root: JSONObject): SignState = SignState(
        state = root.optString("state"),
        signedDate = root.opt("signed_date").asLong(),
    )

    /** signed_date 是「北京时间第几天」，与上游一致。 */
    fun signedToday(state: SignState?, nowMs: Long = System.currentTimeMillis()): Boolean =
        state != null &&
            state.state in setOf("ok", "ignore") &&
            state.signedDate == kotlin.math.floor((nowMs / 1000.0 + 8 * 3600) / 86400).toLong()

    fun posts(root: JSONObject): List<Pair<String, String>> {
        val links = root.optJSONArray("links") ?: return emptyList()
        val result = ArrayList<Pair<String, String>>()
        for (i in 0 until links.length()) {
            val item = links.optJSONObject(i) ?: continue
            val linkId = item.opt("linkid").toIdString() ?: item.opt("link_id").toIdString()
            val hSrc = item.optString("h_src")
            if (linkId != null && hSrc.isNotEmpty()) result += linkId to hSrc
        }
        return result
    }

    fun games(root: JSONObject): List<GameCandidate> {
        val output = ArrayList<GameCandidate>()
        collectGames(root, output)
        return output
    }

    private fun collectGames(node: Any?, output: MutableList<GameCandidate>) {
        when (node) {
            is JSONObject -> {
                val appId = node.opt("appid").toIdString()
                val hSrc = node.optString("h_src")
                if (appId != null && hSrc.isNotEmpty() && output.none { it.appId == appId }) {
                    output += GameCandidate(appId, hSrc)
                }
                for (key in node.keys()) collectGames(node.opt(key), output)
            }
            is JSONArray -> for (i in 0 until node.length()) collectGames(node.opt(i), output)
        }
    }

    fun reviews(root: JSONObject): List<Pair<String, String>> {
        val links = root.optJSONArray("links") ?: return emptyList()
        val result = ArrayList<Pair<String, String>>()
        for (i in 0 until links.length()) {
            val item = links.optJSONObject(i) ?: continue
            val linkId = item.opt("linkid").toIdString() ?: item.opt("link_id").toIdString()
            val userid = item.opt("userid").toIdString()
            if (linkId != null && userid != null) result += userid to linkId
        }
        return result
    }

    fun reviewLinkId(root: JSONObject): String? =
        root.optJSONObject("comment_detail")?.opt("linkid").toIdString()

    /** 与上游 shareEvent 一致：tap 用 type 4，其余 3；非 show 事件额外带 plat 与 h_session_id。 */
    fun shareEventJson(action: String, content: ShareContent, sessionId: String, timeSec: Long): String {
        require(action in setOf("show", "tap", "success")) { "未知分享事件" }
        val addition = JSONObject().apply {
            put("src", content.src)
            when (content) {
                is ShareContent.Link -> {
                    put("link_id", content.linkId)
                    put("h_src", content.hSrc)
                }
                is ShareContent.GameDetail -> {
                    put("app_id", content.appId)
                    put("h_src", content.hSrc)
                }
                is ShareContent.GameComment -> put("link_id", content.linkId)
            }
            if (action != "show") {
                put("plat", "WechatSession")
                put("h_session_id", sessionId)
            }
        }
        val event = JSONObject().apply {
            put("type", if (action == "tap") "4" else "3")
            put("path", "/share/behavior/$action")
            put("time", timeSec.toString())
            put("addition", addition)
        }
        return JSONObject().put("events", JSONArray().put(event)).toString()
    }

    /**
     * 注意：不能写成 `text.toLongOrNull()`。这是 Any? 的成员扩展，会优先于 stdlib 的 String.toLongOrNull
     * 被选中，自己调自己导致无限递归（StackOverflowError，message 为 null）。
     */
    private fun Any?.asLong(): Long? {
        val text = when (this) {
            null -> return null
            is Number -> toString()
            else -> this.toString().trim()
        }
        return try {
            java.lang.Long.parseLong(text)
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun Any?.nonNegativeLong(minimum: Long = 0): Long? {
        val value = asLong() ?: return null
        return if (value >= minimum) value else null
    }

    private fun Any?.toIdString(): String? {
        val text = when (this) {
            null -> return null
            is Number -> toString()
            else -> this.toString().trim()
        }
        return text.ifEmpty { null }
    }
}
